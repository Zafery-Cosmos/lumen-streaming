package app.lumen.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import app.lumen.i18n.T
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Ce que le panneau Sources doit chercher. */
data class SourcesTarget(
    val type: String,     // movie | series
    val mediaId: String,  // tt1375666 ou tt0903747:2:5
    val title: String,
)

/**
 * Panneau « Sources » (plan §5) : interroge TOUS les addons activés en
 * parallèle et liste leurs flux, groupés par addon (Torrentio, Frenchio…).
 * Un flux à URL directe se lit d'un clic ; un torrent nu attend un debrid.
 */
@Composable
fun SourcesOverlay(
    stremio: StremioClient,
    addons: List<AddonEntry>,
    target: SourcesTarget,
    onDismiss: () -> Unit,
    onPlay: (url: String, headers: Map<String, String>) -> Unit,
    onPlayTorrent: (infoHash: String) -> Unit,
) {
    val enabled = addons.filter { it.enabled }
    val results by produceState<List<Pair<AddonEntry, List<StremioStream>?>>?>(
        initialValue = null, target,
    ) {
        value = coroutineScope {
            enabled.map { addon ->
                async {
                    addon to runCatching {
                        stremio.streams(addon.manifestUrl, target.type, target.mediaId)
                    }.getOrNull()
                }
            }.awaitAll()
        }
    }

    // Scrim plein écran — clic à côté = fermer.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LumenColors.Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* absorbe le clic — ne ferme pas */ }
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sources", color = LumenColors.OnBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(target.title, color = LumenColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
            ) {
                when {
                    enabled.isEmpty() -> Text(
                        T["sources.aucunAddonInstalleAjouteTorrentioFrenchio"],
                        color = LumenColors.Muted, fontSize = 14.sp,
                    )
                    results == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(20.dp))
                        Text(T["sources.interrogationDesAddons"], color = LumenColors.Muted, fontSize = 13.sp)
                    }
                    else -> results.orEmpty().forEach { (addon, streams) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                addon.name,
                                color = LumenColors.Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            when {
                                streams == null -> Text(T["sources.erreurDInterrogation"], color = LumenColors.Muted, fontSize = 12.sp)
                                streams.isEmpty() -> Text(T["sources.aucuneSource"], color = LumenColors.Muted, fontSize = 12.sp)
                                else -> streams.take(25).forEach { stream ->
                                    StreamRow(
                                        stream,
                                        onPlay = { url -> onPlay(url, stream.requestHeaders) },
                                        onPlayTorrent = onPlayTorrent,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(
    stream: StremioStream,
    onPlay: (String) -> Unit,
    onPlayTorrent: (String) -> Unit,
) {
    val playable = stream.playable
    val infoHash = stream.infoHash
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LumenColors.SurfaceHigh)
            .clickable(
                enabled = playable || infoHash != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                when {
                    // Flux direct → lecture dans Lumen.
                    playable -> stream.url?.let(onPlay)
                    // Torrent → lecture dans Lumen via le moteur intégré,
                    // exactement comme Stremio le fait sans debrid.
                    infoHash != null -> onPlayTorrent(infoHash)
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = LumenColors.OnBackground,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            stream.name?.let {
                Text(it, color = LumenColors.OnBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                stream.label,
                color = LumenColors.Muted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!playable && infoHash != null) {
                Text(
                    T["sources.torrentLuParLeMoteurIntegre"],
                    color = LumenColors.Muted.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}
