package app.lumen.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.domain.HomeContent
import app.lumen.domain.HomeRepository
import app.lumen.domain.Rail
import app.lumen.ui.components.MediaCard
import app.lumen.ui.components.StaggeredReveal
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage

/**
 * Accueil Netflix-like (plan §3) : hero plein écran + rangées horizontales.
 * La déconnexion n'existe QUE dans les paramètres — jamais ici.
 */
@Composable
fun HomeScreen(client: JellyfinClient, session: StoredSession) {
    val repo = remember { HomeRepository(client, session) }
    val content by produceState<HomeContent?>(initialValue = null) {
        value = repo.load()
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        when (val c = content) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            else -> HomeBody(client, session, c)
        }
    }
}

@Composable
private fun HomeBody(client: JellyfinClient, session: StoredSession, content: HomeContent) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "hero") {
            content.hero?.let { Hero(client, session, it) }
        }
        items(content.rails.size, key = { content.rails[it].id }) { index ->
            StaggeredReveal(index) {
                RailRow(client, session, content.rails[index])
            }
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Hero(client: JellyfinClient, session: StoredSession, item: BaseItem) {
    val backdropUrl = when {
        item.backdropImageTags.isNotEmpty() ->
            client.imageUrl(session.baseUrl, item.id, "Backdrop", item.backdropImageTags.first(), maxWidth = 1920)
        item.imageTags.containsKey("Thumb") ->
            client.imageUrl(session.baseUrl, item.id, "Thumb", item.imageTags["Thumb"], maxWidth = 1920)
        else -> client.imageUrl(session.baseUrl, item.id, "Primary", item.imageTags["Primary"], maxWidth = 1920)
    }
    val logoTag = item.imageTags["Logo"]

    Box(Modifier.fillMaxWidth().aspectRatio(16f / 8f)) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Double dégradé : lisibilité du texte en bas, fondu vers le fond à gauche.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to LumenColors.Background,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to LumenColors.Background.copy(alpha = 0.85f),
                    0.45f to Color.Transparent,
                ),
            ),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (logoTag != null) {
                AsyncImage(
                    model = client.imageUrl(session.baseUrl, item.id, "Logo", logoTag, maxWidth = 800),
                    contentDescription = null,
                    modifier = Modifier.height(90.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                )
            } else {
                Text(
                    item.name,
                    color = LumenColors.OnBackground,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                item.productionYear?.let { MetaChip(it.toString()) }
                item.runTimeMinutes?.let { MetaChip("${it / 60}h${(it % 60).toString().padStart(2, '0')}") }
                item.officialRating?.let { MetaChip(it) }
            }
            item.overview?.let {
                Text(
                    it,
                    color = LumenColors.OnBackground.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(520.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { /* L4 : lecture */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lire", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { /* L3 : fiche détail */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LumenColors.SurfaceHigh.copy(alpha = 0.7f),
                    ),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = LumenColors.OnBackground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Plus d'infos", color = LumenColors.OnBackground, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text,
        color = LumenColors.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun RailRow(client: JellyfinClient, session: StoredSession, rail: Rail) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            rail.title,
            color = LumenColors.OnBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            items(rail.items, key = { it.id }) { item ->
                MediaCard(client, session, item, wide = rail.wide)
            }
        }
    }
}
