package app.lumen.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Moteur desktop : VLCJ en RENDU PAR CALLBACK — libvlc décode en mémoire et
 * Compose dessine chaque frame lui-même. Aucun composant AWT dans la fenêtre :
 * l'overlay maison passe toujours au-dessus (le z-order Compose/AWT est
 * inutilisable sur Linux), et on évite les artefacts de la surface X11.
 */
class VlcjEngine : PlayerEngine {
    private val factory = MediaPlayerFactory()
    private val player = factory.mediaPlayers().newEmbeddedMediaPlayer()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    /** Dernière frame décodée, prête à dessiner. */
    val frames = MutableStateFlow<ImageBitmap?>(null)

    private var image: BufferedImage? = null

    private val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            image = BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_RGB)
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) = Unit
        override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
    }

    private val renderCallback = object : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) = Unit
        override fun unlock(mediaPlayer: MediaPlayer) = Unit

        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<ByteBuffer>,
            bufferFormat: BufferFormat,
            displayWidth: Int,
            displayHeight: Int,
        ) {
            val img = image ?: return
            val target = (img.raster.dataBuffer as DataBufferInt).data
            // Duplicate : ne touche ni la position ni l'ordre du buffer natif partagé.
            nativeBuffers[0].duplicate().order(ByteOrder.nativeOrder()).asIntBuffer().get(target)
            frames.value = img.toComposeImageBitmap()
        }
    }

    init {
        player.videoSurface().set(
            CallbackVideoSurface(
                bufferFormatCallback,
                renderCallback,
                true,
                VideoSurfaceAdapters.getVideoSurfaceAdapter(),
            ),
        )
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

    override fun setRate(rate: Float) {
        player.controls().setRate(rate)
    }

    override fun setVolume(volume: Int) {
        player.audio().setVolume(volume)
    }

    override fun audioTracks(): List<MediaTrack> =
        player.audio().trackDescriptions().map { MediaTrack(it.id(), it.description()) }

    override fun subtitleTracks(): List<MediaTrack> =
        player.subpictures().trackDescriptions().map { MediaTrack(it.id(), it.description()) }

    override fun selectAudioTrack(id: Int) {
        player.audio().setTrack(id)
    }

    override fun selectSubtitleTrack(id: Int) {
        player.subpictures().setTrack(id)
    }

    override fun snapshot(): Boolean {
        val dir = java.io.File(System.getProperty("user.home"), "Images")
            .takeIf { it.isDirectory } ?: java.io.File(System.getProperty("user.home"))
        val file = java.io.File(dir, "lumen-${_state.value.positionMs / 1000}s.png")
        return player.snapshots().save(file)
    }

    override fun release() {
        player.release()
        factory.release()
    }
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
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier, fill: Boolean) {
    val vlcj = engine as VlcjEngine
    val frame by vlcj.frames.collectAsState()
    Box(modifier.background(Color.Black)) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
