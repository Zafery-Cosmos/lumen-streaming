package app.lumen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

/**
 * Moteur desktop : VLCJ sur le libvlc du système (déjà présent sur Fedora).
 * mpv pourra remplacer ce moteur plus tard derrière la même interface.
 */
class VlcjEngine : PlayerEngine {
    val component = EmbeddedMediaPlayerComponent()
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private val player get() = component.mediaPlayer()

    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(playing = true, buffering = false, ended = false)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(playing = false)
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _state.value = _state.value.copy(buffering = newCache < 100f)
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _state.value = _state.value.copy(positionMs = newTime)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _state.value = _state.value.copy(durationMs = newLength)
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(playing = false, ended = true)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(
                    playing = false, buffering = false,
                    error = "Lecture impossible (libvlc)",
                )
            }
        })
    }

    override fun play(url: String, headers: Map<String, String>, startMs: Long) {
        // libvlc prend les en-têtes via des options de média « :http-… » (plan §4).
        val options = buildList {
            if (startMs > 0) add(":start-time=${startMs / 1000}")
            headers["Referer"]?.let { add(":http-referrer=$it") }
            headers["User-Agent"]?.let { add(":http-user-agent=$it") }
        }.toTypedArray()
        player.media().play(url, *options)
    }

    override fun pause() = player.controls().setPause(true)
    override fun resume() = player.controls().setPause(false)
    override fun seekTo(positionMs: Long) = player.controls().setTime(positionMs)
    override fun release() = component.release()
}

@Composable
actual fun rememberPlayerEngine(): PlayerEngine {
    val engine = remember { VlcjEngine() }
    DisposableEffect(Unit) {
        onDispose { engine.release() }
    }
    return engine
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    val vlcj = engine as VlcjEngine
    SwingPanel(
        factory = { vlcj.component },
        modifier = modifier,
    )
}
