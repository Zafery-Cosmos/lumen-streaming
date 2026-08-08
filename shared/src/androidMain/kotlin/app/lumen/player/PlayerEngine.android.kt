package app.lumen.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Moteur Android : Media3/ExoPlayer, en-têtes par flux via la DataSource (plan §4). */
class Media3Engine(private val context: Context) : PlayerEngine {
    override val name: String = "ExoPlayer"
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var player: ExoPlayer = ExoPlayer.Builder(context).build()
        private set

    // Listener partagé : ré-attaché à chaque nouveau player (cf. play()).
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(playing = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(
                buffering = playbackState == Player.STATE_BUFFERING,
                ended = playbackState == Player.STATE_ENDED,
                durationMs = player.duration.coerceAtLeast(0),
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                playing = false, buffering = false,
                error = error.errorCodeName,
            )
        }
    }

    init {
        player.addListener(listener)
        // ExoPlayer n'émet pas la position en continu : on la publie nous-mêmes.
        scope.launch {
            while (true) {
                _state.value = _state.value.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = player.duration.coerceAtLeast(0),
                )
                delay(500)
            }
        }
    }

    override fun play(url: String, headers: Map<String, String>, startMs: Long) {
        val httpFactory = DefaultHttpDataSource.Factory()
        if (headers.isNotEmpty()) httpFactory.setDefaultRequestProperties(headers)
        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
        // On remplace le player pour appliquer la factory, en migrant le listener.
        val old = player
        player = newPlayer
        newPlayer.addListener(listener)
        old.release()
        newPlayer.setMediaItem(MediaItem.fromUri(url), startMs)
        newPlayer.prepare()
        newPlayer.play()
    }

    override fun pause() { player.pause() }
    override fun resume() { player.play() }
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs) }
    override fun setRate(rate: Float) { player.setPlaybackSpeed(rate) }
    override fun setVolume(volume: Int) { player.volume = (volume / 100f).coerceIn(0f, 1f) }
    // TODO(L4b) : sélection de pistes via TrackSelectionParameters.
    override fun release() {
        scope.cancel()
        player.release()
    }
}

@Composable
actual fun rememberPlayerEngine(): PlayerEngine {
    val context = LocalContext.current
    val engine = remember { Media3Engine(context) }
    DisposableEffect(Unit) {
        onDispose { engine.release() }
    }
    return engine
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier, fill: Boolean) {
    val media3 = engine as Media3Engine
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false   // l'UI est à nous, pas au moteur
                player = media3.player
            }
        },
        update = { view ->
            view.player = media3.player
            view.resizeMode = if (fill) {
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier,
    )
}

// Android decode en materiel par defaut via MediaCodec : rien a choisir.
actual fun availableTranscodeProfiles(): List<String> = emptyList()
