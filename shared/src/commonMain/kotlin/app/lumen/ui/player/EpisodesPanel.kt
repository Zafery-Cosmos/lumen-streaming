package app.lumen.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.api.TmdbClient
import app.lumen.auth.StoredSession
import app.lumen.domain.OrganizedEpisode
import app.lumen.domain.label
import app.lumen.domain.organizeSeries
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage

/**
 * Panneau ÉPISODES du lecteur : saisons en puces, épisodes en liste, sur un
 * tiers de l'écran. Complètement séparé du panneau Sources — on choisit
 * « quoi regarder » ici, « avec quelle source » là-bas.
 */
@Composable
fun EpisodesPanel(
    client: JellyfinClient,
    tmdb: TmdbClient,
    session: StoredSession,
    seriesId: String,
    currentEpisodeId: String?,
    onPlayEpisode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups by produceState<Map<Int, List<OrganizedEpisode>>?>(initialValue = null, seriesId) {
        val series = runCatching { client.item(session.baseUrl, session.userId, seriesId) }.getOrNull()
        value = if (series != null) organizeSeries(client, tmdb, session, series) else emptyMap()
    }

    var season by remember(groups) {
        mutableStateOf(
            // On s'ouvre sur la saison de l'épisode en cours.
            groups?.entries?.firstOrNull { g -> g.value.any { it.ep.id == currentEpisodeId } }?.key
                ?: groups?.keys?.firstOrNull { it > 0 }
                ?: groups?.keys?.firstOrNull(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xF00D0D12))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Épisodes",
                color = LumenColors.OnBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "Fermer",
                tint = LumenColors.Muted,
                modifier = Modifier.size(22.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            )
        }

        when (val g = groups) {
            null -> CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(24.dp))
            else -> {
                // Saisons.
                app.lumen.ui.components.ScrollableRow(
                    spacing = 8.dp,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    arrowWidth = 34.dp,
                    iconSize = 22.dp,
                    scrimColor = Color(0xFF0D0D12),
                ) {
                    items(g.keys.toList()) { s ->
                        val selected = s == season
                        Text(
                            if (s == 0) "Specials" else "Saison $s",
                            color = if (selected) Color.Black else LumenColors.OnBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(
                                    if (selected) LumenColors.OnBackground else LumenColors.Surface,
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { season = s }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                        )
                    }
                }

                // Épisodes de la saison choisie.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(season?.let { g[it] }.orEmpty(), key = { it.ep.id }) { org ->
                        EpisodeLine(
                            client = client,
                            session = session,
                            org = org,
                            playing = org.ep.id == currentEpisodeId,
                            onClick = { onPlayEpisode(org.ep.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeLine(
    client: JellyfinClient,
    session: StoredSession,
    org: OrganizedEpisode,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (playing) LumenColors.SurfaceHigh else LumenColors.Surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier.width(112.dp).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(LumenColors.SurfaceHigh),
        ) {
            val model = when {
                org.ep.imageTags.containsKey("Primary") ->
                    client.imageUrl(session.baseUrl, org.ep.id, "Primary", org.ep.imageTags["Primary"], maxWidth = 300)
                else -> org.extra?.stillUrl
            }
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = org.ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (playing) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "En cours",
                        tint = LumenColors.Accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            // Progression déjà regardée.
            org.ep.userData?.playedPercentage?.takeIf { it > 0 }?.let { pct ->
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((pct / 100).toFloat())
                            .background(LumenColors.Accent),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                org.label(),
                color = if (playing) LumenColors.Accent else LumenColors.OnBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            (org.ep.runTimeMinutes ?: org.extra?.runtimeMinutes)?.let {
                Text("$it min", color = LumenColors.Muted, fontSize = 11.sp)
            }
        }
    }
}
