package app.lumen.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.domain.AvatarBank
import app.lumen.domain.AvatarIndex
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage

/**
 * Sélecteur d'avatars : 1757 images servies par le NAS, triées par ŒUVRE
 * (film ou série). On choisit d'abord la collection, puis l'œuvre, et seules
 * ses images se chargent — pas de mur de vignettes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(
    client: JellyfinClient,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val bank = remember { AvatarBank(client.http) }
    val index by produceState<AvatarIndex?>(initialValue = null) { value = bank.index() }

    val idx = index
    if (idx == null) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterStart) {
            CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(22.dp))
        }
        return
    }

    val collections = remember(idx) { idx.groups.map { it.collection }.distinct() }
    var collection by remember(idx) { mutableStateOf(collections.firstOrNull().orEmpty()) }
    val works = remember(idx, collection) { idx.groups.filter { it.collection == collection } }
    var workId by remember(collection) { mutableStateOf(works.firstOrNull()?.id.orEmpty()) }
    val group = works.firstOrNull { it.id == workId } ?: works.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Avatar — ${idx.count} images triées par film et série",
            color = LumenColors.OnBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // Collections (Général, Crunchyroll, Disney, Marvel…).
        app.lumen.ui.components.ScrollableRow(
            spacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 0.dp),
            arrowWidth = 34.dp,
            iconSize = 22.dp,
        ) {
            items(collections) { c ->
                Chip(c, c == collection) { collection = c }
            }
        }

        // Œuvres de la collection choisie.
        if (works.size > 1) {
            app.lumen.ui.components.ScrollableRow(
                spacing = 8.dp,
                contentPadding = PaddingValues(horizontal = 0.dp),
                arrowWidth = 34.dp,
                iconSize = 22.dp,
            ) {
                items(works) { w ->
                    Chip(w.work, w.id == group?.id, small = true) { workId = w.id }
                }
            }
        }

        // Les images de l'œuvre.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
        ) {
            group?.avatars?.forEach { entry ->
                val isSel = selected == entry.url
                Box(
                    Modifier
                        .size(56.dp)
                        .background(
                            if (isSel) LumenColors.Accent else Color.Transparent,
                            RoundedCornerShape(14.dp),
                        )
                        .padding(if (isSel) 3.dp else 0.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(if (isSel) null else entry.url) },
                ) {
                    AsyncImage(
                        model = bank.url(entry.url),
                        contentDescription = entry.file,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LumenColors.SurfaceHigh),
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else LumenColors.OnBackground,
        fontSize = if (small) 12.sp else 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .background(
                if (selected) LumenColors.OnBackground else LumenColors.Surface,
                RoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}
