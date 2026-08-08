package app.lumen.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.domain.toCard
import app.lumen.domain.toHero
import app.lumen.ui.components.HeroCarousel
import app.lumen.ui.components.MediaCard
import app.lumen.ui.theme.LumenColors

/**
 * Grille d'une catégorie (Films, Séries), avec le même carrousel hero que
 * l'accueil en tête (version « billboard » arrondie), filtré sur la catégorie.
 */
@Composable
fun BrowseScreen(
    client: JellyfinClient,
    session: StoredSession,
    includeTypes: String,
    title: String,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val items by produceState<List<BaseItem>?>(initialValue = null, includeTypes) {
        value = runCatching {
            client.items(
                session.baseUrl, session.userId,
                includeTypes = includeTypes,
                limit = 200,
            ).items
        }.getOrDefault(emptyList())
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        when (val list = items) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                // Le haut compense la barre de navigation transparente qui flotte.
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 88.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Le hero défilant de la catégorie, comme sur l'accueil.
                val heroes = list.mapNotNull { it.toHero(client, session) }.take(6)
                if (heroes.isNotEmpty()) {
                    item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                        HeroCarousel(
                            heroes, onOpen, onPlay,
                            modifier = Modifier.padding(bottom = 12.dp),
                            rounded = true,
                        )
                    }
                }
                item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        title,
                        color = LumenColors.OnBackground,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(list, key = { it.id }) { item ->
                    val card = item.toCard(client, session)
                    MediaCard(card, onClick = { onOpen(card.id) })
                }
                if (list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Rien ici pour l'instant.", color = LumenColors.Muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
