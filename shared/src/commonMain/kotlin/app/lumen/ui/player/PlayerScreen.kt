package app.lumen.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.domain.parseEpisodeFileName
import app.lumen.player.MediaTrack
import app.lumen.player.VideoSurface
import app.lumen.player.rememberPlayerEngine
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lecteur plein écran, UI 100% maison (plan §3/§4) : overlay auto-masqué,
 * timeline, ±10 s, panneau d'options complet (vitesse, qualité, pistes,
 * volume, rendu, capture…), carte d'info à la pause, reprise serveur.
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

    var item by remember { mutableStateOf<BaseItem?>(null) }
    var title by remember { mutableStateOf("") }
    var playSessionId by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playMethod by remember { mutableStateOf("…") }

    // Réglages du lecteur.
    var rate by remember { mutableStateOf(1f) }
    var volume by remember { mutableStateOf(100) }
    var muted by remember { mutableStateOf(false) }
    var fill by remember { mutableStateOf(false) }
    var maxBitrate by remember { mutableStateOf<Long?>(null) }
    var audioTracks by remember { mutableStateOf(listOf<MediaTrack>()) }
    var subTracks by remember { mutableStateOf(listOf<MediaTrack>()) }

    suspend fun startPlayback(startMs: Long) {
        val info = client.playbackInfo(session.baseUrl, session.userId, itemId, maxBitrate)
        val source = info.mediaSources.firstOrNull() ?: error("Aucune source de lecture")
        playSessionId = info.playSessionId
        playMethod = when {
            source.supportsDirectPlay -> "Direct Play"
            source.supportsDirectStream -> "Direct Stream"
            else -> "Transcodage"
        }
        engine.play(client.streamUrl(session.baseUrl, itemId, source), startMs = startMs)
    }

    // Démarrage : détail (titre + reprise) → PlaybackInfo → lecture → session.
    LaunchedEffect(itemId) {
        try {
            val it = client.item(session.baseUrl, session.userId, itemId)
            item = it
            // Le nom de fichier est plus fiable que les métadonnées bancales.
            val parsed = parseEpisodeFileName(it.path)
            title = when {
                it.type == "Episode" && parsed != null ->
                    "${it.seriesName ?: ""} — S${parsed.season}E${parsed.episode}" +
                        (parsed.title?.let { t -> " · $t" } ?: "")
                it.type == "Episode" ->
                    "${it.seriesName} — S${it.parentIndexNumber}E${it.indexNumber} · ${it.name}"
                else -> it.name
            }
            val startMs = (it.userData?.playbackPositionTicks ?: 0L) / 10_000
            startPlayback(startMs)
            client.reportPlaybackStart(session.baseUrl, itemId, playSessionId)
        } catch (e: Exception) {
            loadError = "Impossible de lancer la lecture"
        }
    }

    // Les pistes n'existent qu'une fois la lecture démarrée.
    LaunchedEffect(state.playing) {
        if (state.playing && audioTracks.isEmpty()) {
            delay(500)
            audioTracks = engine.audioTracks()
            subTracks = engine.subtitleTracks()
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
    LaunchedEffect(controlsVisible, state.playing, settingsOpen) {
        if (controlsVisible && state.playing && !settingsOpen) {
            delay(3_000)
            controlsVisible = false
        }
    }

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
                detectTapGestures(onTap = {
                    if (settingsOpen) settingsOpen = false else controlsVisible = !controlsVisible
                })
            },
    ) {
        VideoSurface(engine, Modifier.fillMaxSize(), fill)

        if (state.buffering && loadError == null) {
            CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }
        (loadError ?: state.error)?.let {
            Text(it, color = LumenColors.OnBackground, modifier = Modifier.align(Alignment.Center))
        }

        LaunchedEffect(state.ended) {
            if (state.ended) leave()
        }

        // Carte d'info à la pause : monte du bas, repart vers le bas à la reprise.
        AnimatedVisibility(
            visible = !state.playing && !state.buffering && loadError == null && item != null && !settingsOpen,
            enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { it / 2 },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(300)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 150.dp),
        ) {
            item?.let { PauseInfoCard(client, session, it) }
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
                onOpenSettings = { settingsOpen = !settingsOpen },
            )
        }

        // Panneau d'options — glisse depuis la droite.
        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(280)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 3 },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SettingsPanel(
                rate = rate,
                onRate = { rate = it; engine.setRate(it) },
                maxBitrate = maxBitrate,
                onMaxBitrate = {
                    maxBitrate = it
                    val pos = engine.state.value.positionMs
                    scope.launch { runCatching { startPlayback(pos) } }
                },
                audioTracks = audioTracks,
                subTracks = subTracks,
                onAudioTrack = { engine.selectAudioTrack(it) },
                onSubTrack = { engine.selectSubtitleTrack(it) },
                volume = volume,
                muted = muted,
                onVolume = { volume = it; muted = false; engine.setVolume(it) },
                onMute = { muted = !muted; engine.setVolume(if (muted) 0 else volume) },
                fill = fill,
                onFill = { fill = it },
                onSnapshot = { engine.snapshot() },
                playMethod = playMethod,
            )
        }
    }
}

/** Carte titre/note/synopsis affichée pendant la pause. */
@Composable
private fun PauseInfoCard(client: JellyfinClient, session: StoredSession, item: BaseItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .widthIn(max = 620.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(18.dp),
    ) {
        val posterTag = item.imageTags["Primary"]
        if (posterTag != null) {
            AsyncImage(
                model = client.imageUrl(session.baseUrl, item.id, "Primary", posterTag, maxWidth = 300),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(110.dp).height(160.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                parseEpisodeFileName(item.path)?.title ?: item.name,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                item.communityRating?.let {
                    Text(
                        "★ ${(it * 10).toInt() / 10.0}",
                        color = LumenColors.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item.productionYear?.let {
                    Text(it.toString(), color = LumenColors.Muted, fontSize = 13.sp)
                }
            }
            item.overview?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Le panneau latéral d'options du lecteur. */
@Composable
private fun SettingsPanel(
    rate: Float,
    onRate: (Float) -> Unit,
    maxBitrate: Long?,
    onMaxBitrate: (Long?) -> Unit,
    audioTracks: List<MediaTrack>,
    subTracks: List<MediaTrack>,
    onAudioTrack: (Int) -> Unit,
    onSubTrack: (Int) -> Unit,
    volume: Int,
    muted: Boolean,
    onVolume: (Int) -> Unit,
    onMute: () -> Unit,
    fill: Boolean,
    onFill: (Boolean) -> Unit,
    onSnapshot: () -> Unit,
    playMethod: String,
) {
    var selectedAudio by remember { mutableStateOf<Int?>(null) }
    var selectedSub by remember { mutableStateOf(-1) }

    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(Color(0xF0121218))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Options de lecture", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)

        SettingSection(Icons.Filled.Speed, "Vitesse") {
            ChipRow(
                options = listOf("0.5×" to 0.5f, "0.75×" to 0.75f, "1×" to 1f, "1.25×" to 1.25f, "1.5×" to 1.5f, "2×" to 2f),
                isSelected = { it == rate },
                onSelect = onRate,
            )
        }

        SettingSection(Icons.Filled.HighQuality, "Qualité") {
            ChipRow(
                options = listOf(
                    "Auto" to null,
                    "20 Mbps" to 20_000_000L,
                    "8 Mbps" to 8_000_000L,
                    "4 Mbps" to 4_000_000L,
                    "2 Mbps" to 2_000_000L,
                ),
                isSelected = { it == maxBitrate },
                onSelect = onMaxBitrate,
            )
        }

        SettingSection(Icons.Filled.Audiotrack, "Piste audio") {
            if (audioTracks.isEmpty()) {
                Text("Piste unique", color = LumenColors.Muted, fontSize = 13.sp)
            } else {
                ChipRow(
                    options = audioTracks.map { it.label to it.id },
                    isSelected = { it == selectedAudio },
                    onSelect = { selectedAudio = it; onAudioTrack(it) },
                )
            }
        }

        SettingSection(Icons.Filled.Subtitles, "Sous-titres") {
            ChipRow(
                options = listOf("Désactivés" to -1) + subTracks.filter { it.id >= 0 }.map { it.label to it.id },
                isSelected = { it == selectedSub },
                onSelect = { selectedSub = it; onSubTrack(it) },
            )
        }

        SettingSection(
            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            "Volume",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Muet",
                    tint = if (muted) LumenColors.Accent else Color.White,
                    modifier = Modifier.size(20.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMute,
                    ),
                )
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { onVolume(it.toInt()) },
                    valueRange = 0f..130f,
                    colors = SliderDefaults.colors(
                        thumbColor = LumenColors.Accent,
                        activeTrackColor = LumenColors.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text("$volume%", color = LumenColors.Muted, fontSize = 12.sp)
            }
        }

        SettingSection(Icons.Filled.AspectRatio, "Rendu") {
            ChipRow(
                options = listOf("Ajuster" to false, "Remplir" to true),
                isSelected = { it == fill },
                onSelect = onFill,
            )
        }

        SettingSection(Icons.Filled.PhotoCamera, "Capture d'écran") {
            Text(
                "Enregistrer l'image actuelle",
                color = LumenColors.Accent,
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSnapshot,
                ),
            )
        }

        SettingSection(Icons.Filled.Cast, "Diffuser (Cast)") {
            Text("Bientôt disponible", color = LumenColors.Muted, fontSize = 13.sp)
        }

        SettingSection(Icons.Filled.Info, "Session") {
            Text(playMethod, color = LumenColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingSection(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = LumenColors.Muted, modifier = Modifier.size(17.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (label, value) ->
            val selected = isSelected(value)
            Text(
                label,
                color = if (selected) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .background(
                        if (selected) Color.White else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
    onOpenSettings: () -> Unit,
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
        // Barre du haut : retour + titre à gauche, options à droite.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(24.dp),
        ) {
            RoundControl(Icons.AutoMirrored.Filled.ArrowBack, "Retour", 22.dp, onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            RoundControl(Icons.Filled.Tune, "Options", 22.dp, onClick = onOpenSettings)
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
    icon: ImageVector,
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
