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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.StremioClient
import app.lumen.api.StremioStream
import app.lumen.domain.AddonEntry
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Panneau SOURCES du lecteur : liste les flux des addons pour le titre en
 * cours, et permet d'en changer SANS quitter la lecture — la position est
 * conservée par l'appelant.
 */
@Composable
fun PlayerSourcesPanel(
    stremio: StremioClient,
    addons: List<AddonEntry>,
    type: String?,
    mediaId: String?,
    title: String,
    currentLabel: String,
    onDismiss: () -> Unit,
    onPick: (url: String?, headers: Map<String, String>, hash: String?) -> Unit,
) {
    val enabled = addons.filter { it.enabled }
    val results by produceState<List<Pair<AddonEntry, List<StremioStream>?>>?>(
        initialValue = null, mediaId, type,
    ) {
        if (type == null || mediaId == null) {
            value = emptyList()
            return@produceState
        }
        value = coroutineScope {
            enabled.map { addon ->
                async {
                    addon to runCatching { stremio.streams(addon.manifestUrl, type, mediaId) }.getOrNull()
                }
            }.awaitAll()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xF00D0D12))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sources", color = LumenColors.OnBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "En cours : $currentLabel",
                    color = LumenColors.Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
        Text(
            "La lecture reprend à la même seconde.",
            color = LumenColors.Muted,
            fontSize = 11.sp,
        )

        when {
            type == null || mediaId == null -> Text(
                "Ce titre vient de votre médiathèque : aucune source d'addon à proposer.",
                color = LumenColors.Muted, fontSize = 13.sp,
            )
            enabled.isEmpty() -> Text(
                "Aucun addon installé (Paramètres → Addons Stremio).",
                color = LumenColors.Muted, fontSize = 13.sp,
            )
            results == null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(18.dp))
                Text("Interrogation des addons…", color = LumenColors.Muted, fontSize = 12.sp)
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                results.orEmpty().forEach { (addon, streams) ->
                    item(key = "h-${addon.manifestUrl}") {
                        Text(
                            addon.name,
                            color = LumenColors.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    when {
                        streams == null -> item(key = "e-${addon.manifestUrl}") {
                            Text("Erreur d'interrogation", color = LumenColors.Muted, fontSize = 11.sp)
                        }
                        streams.isEmpty() -> item(key = "v-${addon.manifestUrl}") {
                            Text("Aucune source", color = LumenColors.Muted, fontSize = 11.sp)
                        }
                        else -> items(streams.take(25).size) { i ->
                            val stream = streams[i]
                            SourceLine(stream) {
                                onPick(stream.url, stream.requestHeaders, stream.infoHash)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceLine(stream: StremioStream, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LumenColors.SurfaceHigh)
            .clickable(
                enabled = stream.playable || stream.infoHash != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = LumenColors.OnBackground,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column {
            stream.name?.let {
                Text(
                    it, color = LumenColors.OnBackground, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stream.label,
                color = LumenColors.Muted,
                fontSize = 10.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
