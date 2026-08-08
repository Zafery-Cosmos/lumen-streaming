package app.lumen.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.player.VideoSurface
import app.lumen.player.rememberPlayerEngine
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lecteur plein écran, UI 100% maison (plan §3/§4) : overlay auto-masqué,
 * timeline, ±10 s, reprise serveur. Les pistes/sous-titres arrivent au L4b.
 */
@Composable
fun PlayerScreen(
    client: JellyfinClient,
    session: StoredSession,
    itemId: String,
    onBack: () -> Unit,
) {
    val engine = rememberPlayerEngine()
    val state by engine.state.collectAsState()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var playSessionId by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Démarrage : détail (titre + reprise) → PlaybackInfo → lecture → session.
    LaunchedEffect(itemId) {
        try {
            val item = client.item(session.baseUrl, session.userId, itemId)
            title = if (item.type == "Episode") {
                "${item.seriesName} — S${item.parentIndexNumber}E${item.indexNumber} · ${item.name}"
            } else item.name
            val startMs = (item.userData?.playbackPositionTicks ?: 0L) / 10_000
            val info = client.playbackInfo(session.baseUrl, session.userId, itemId)
            val source = info.mediaSources.firstOrNull() ?: error("Aucune source de lecture")
            playSessionId = info.playSessionId
            engine.play(client.streamUrl(session.baseUrl, itemId, source), startMs = startMs)
            client.reportPlaybackStart(session.baseUrl, itemId, info.playSessionId)
        } catch (e: Exception) {
            loadError = "Impossible de lancer la lecture"
        }
    }

    // Remontée de progression toutes les 10 s (plan §4) — reprise partagée.
    LaunchedEffect(playSessionId) {
        if (playSessionId == null) return@LaunchedEffect
        while (true) {
            delay(10_000)
            val pos = engine.state.value.positionMs * 10_000
            runCatching {
                client.reportPlaybackProgress(
                    session.baseUrl, itemId, pos,
                    paused = !engine.state.value.playing, playSessionId = playSessionId,
                )
            }
        }
    }

    // Auto-masquage des contrôles après 3 s de lecture sans interaction.
    LaunchedEffect(controlsVisible, state.playing) {
        if (controlsVisible && state.playing) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // À la sortie : on fige la position côté serveur (fire-and-forget).
    fun leave() {
        val pos = engine.state.value.positionMs * 10_000
        val psid = playSessionId
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { client.reportPlaybackStopped(session.baseUrl, itemId, pos, psid) }
        }
        onBack()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            },
    ) {
        VideoSurface(engine, Modifier.fillMaxSize())

        if (state.buffering && loadError == null) {
            CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }
        (loadError ?: state.error)?.let {
            Text(it, color = LumenColors.OnBackground, modifier = Modifier.align(Alignment.Center))
        }

        // Fin de lecture → retour automatique (le « épisode suivant » viendra plus tard).
        LaunchedEffect(state.ended) {
            if (state.ended) leave()
        }

        AnimatedVisibility(
            visible = controlsVisible || !state.playing,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
        ) {
            ControlsOverlay(
                title = title,
                playing = state.playing,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onBack = ::leave,
                onTogglePlay = { if (state.playing) engine.pause() else engine.resume() },
                onSeek = { engine.seekTo(it) },
            )
        }
    }
}

@Composable
private fun ControlsOverlay(
    title: String,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.3f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.75f),
            ),
        ),
    ) {
        // Barre du haut : retour + titre.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
        ) {
            RoundControl(Icons.AutoMirrored.Filled.ArrowBack, "Retour", 22.dp, onClick = onBack)
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        // Contrôles centraux : -10 s, lecture/pause, +10 s.
        Row(
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            RoundControl(Icons.Filled.Replay10, "Reculer de 10 s", 28.dp) {
                onSeek((positionMs - 10_000).coerceAtLeast(0))
            }
            RoundControl(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "Pause" else "Lecture",
                40.dp,
                big = true,
                onClick = onTogglePlay,
            )
            RoundControl(Icons.Filled.Forward10, "Avancer de 10 s", 28.dp) {
                onSeek((positionMs + 10_000).coerceAtMost(durationMs))
            }
        }

        // Timeline + temps.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
        ) {
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { onSeek((it * durationMs).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = LumenColors.Accent,
                    activeTrackColor = LumenColors.Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(positionMs), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconSize: androidx.compose.ui.unit.Dp,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(if (big) 72.dp else 52.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}
