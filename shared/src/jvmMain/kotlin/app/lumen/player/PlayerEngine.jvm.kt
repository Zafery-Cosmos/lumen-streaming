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
import androidx.compose.ui.graphics.asComposeImageBitmap
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
import java.nio.ByteBuffer

/**
 * Moteur desktop : VLCJ en RENDU PAR CALLBACK — libvlc décode en mémoire et
 * Compose dessine chaque frame lui-même. Aucun composant AWT dans la fenêtre :
 * l'overlay maison passe toujours au-dessus (le z-order Compose/AWT est
 * inutilisable sur Linux), et on évite les artefacts de la surface X11.
 */
/**
 * Emplacements où les distributions posent libvlc. On DÉSIGNE le dossier
 * nous-mêmes avant de créer la fabrique.
 *
 * Sans cela, vlcj cherche « libvlc.so » — un nom que beaucoup de distributions
 * ne fournissent pas (Fedora n'installe que `libvlc.so.5`, le lien non versionné
 * appartenant au paquet de développement). Faute de le trouver, vlcj se rabat
 * sur un balayage RÉCURSIF du répertoire courant. Lancée depuis une icône de
 * bureau, l'application démarre dans le dossier personnel : elle se met alors à
 * parcourir des dizaines de gigaoctets, sur le thread d'affichage, et se fige
 * indéfiniment à 100 % de processeur. Observé en direct sur une machine réelle.
 */
private val LIBVLC_DIRS = listOf(
    "/usr/lib64",
    "/usr/lib/x86_64-linux-gnu",
    "/usr/lib",
    "/usr/local/lib",
    "/app/lib",                       // Flatpak
    "/var/lib/snapd/lib/vlc",         // Snap
    "C:\\Program Files\\VideoLAN\\VLC",
    "/Applications/VLC.app/Contents/MacOS/lib",
)

/** Dossier contenant libvlc, ou null s'il est réellement absent de la machine. */
internal fun findLibVlcDir(): String? = LIBVLC_DIRS.firstOrNull { dir ->
    runCatching {
        java.io.File(dir).listFiles { f ->
            f.name.startsWith("libvlc.so") || f.name.equals("libvlc.dll", true) ||
                f.name.startsWith("libvlc.dylib")
        }?.isNotEmpty() == true
    }.getOrDefault(false)
}

private fun aimAtLibVlc(): Boolean {
    val dir = findLibVlcDir() ?: return false
    System.setProperty("jna.library.path", dir)
    runCatching { com.sun.jna.NativeLibrary.addSearchPath("vlc", dir) }
    // Les greffons vivent à côté ; sans eux, libvlc démarre mais ne décode rien.
    listOf("$dir/vlc/plugins", "$dir/vlc").firstOrNull { java.io.File(it).isDirectory }
        ?.let { System.setProperty("VLC_PLUGIN_PATH", it) }
    return true
}

/** Levée quand VLC n'est pas installé — message clair plutôt qu'un gel. */
class LibVlcMissing : IllegalStateException(
    "VLC est introuvable sur cet ordinateur. Installe le paquet « vlc » de ta " +
        "distribution, puis relance Lumen.",
)

class VlcjEngine : PlayerEngine {
    override val name: String = "libVLC"
    private val factory = run {
        if (!aimAtLibVlc()) throw LibVlcMissing()
        MediaPlayerFactory()
    }
    private val player = factory.mediaPlayers().newEmbeddedMediaPlayer()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    /** Dernière frame décodée, prête à dessiner. */
    val frames = MutableStateFlow<ImageBitmap?>(null)

    /**
     * Une seule image Skia et un seul tampon, réutilisés à chaque frame.
     *
     * La version précédente reconstruisait un bitmap complet par image : à
     * 3448×1440, cela fait 20 Mo alloués 24 fois par seconde, soit près de
     * 500 Mo/s jetés au ramasse-miettes. Le rendu ne suivait pas et libvlc
     * abandonnait la moitié des images — 11 affichées sur 24. Mesuré, puis
     * remesuré après correction.
     *
     * Le format natif de libvlc (RV32) correspond exactement à BGRA_8888 :
     * les octets sont recopiés tels quels, sans conversion.
     */
    private var canvas: org.jetbrains.skia.Bitmap? = null
    private var scratch: ByteArray? = null

    private val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            canvas = org.jetbrains.skia.Bitmap().apply {
                allocPixels(
                    org.jetbrains.skia.ImageInfo(
                        org.jetbrains.skia.ColorInfo(
                            org.jetbrains.skia.ColorType.BGRA_8888,
                            org.jetbrains.skia.ColorAlphaType.OPAQUE,
                            null,
                        ),
                        sourceWidth,
                        sourceHeight,
                    ),
                )
            }
            scratch = ByteArray(sourceWidth * sourceHeight * 4)
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
            val bmp = canvas ?: return
            val buf = scratch ?: return
            // Duplicate : ne touche ni la position ni l'ordre du buffer natif partagé.
            nativeBuffers[0].duplicate().get(buf)
            bmp.installPixels(buf)
            // L'enveloppe Compose est légère : elle partage les pixels, ne les copie pas.
            frames.value = bmp.asComposeImageBitmap()
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

    override fun play(
        url: String,
        headers: Map<String, String>,
        startMs: Long,
        audioSlaveUrl: String?,
    ) {
        // libvlc prend les en-têtes via des options de média « :http-… » (plan §4).
        val options = buildList {
            if (startMs > 0) add(":start-time=${startMs / 1000}")
            // Flux DASH : l'audio est un fichier à part, libvlc le recale seul.
            audioSlaveUrl?.let { add(":input-slave=$it") }
            headers["Referer"]?.let { add(":http-referrer=$it") }
            headers["User-Agent"]?.let { add(":http-user-agent=$it") }
            // Accélération matérielle — « Profil de transcodage » des réglages.
            val profile = app.lumen.domain.AppSettings.transcodeProfile.value
            if (profile.startsWith("vaapi")) {
                add(":avcodec-hw=vaapi")
                add(":vaapi-device=/dev/dri/${profile.removePrefix("vaapi-")}")
            } else {
                add(":avcodec-hw=none")
            }
            // Apparence des sous-titres — réglages « Audio et sous-titres » (§6.2).
            val scale = app.lumen.domain.AppSettings.subtitleScalePct.value
            if (scale != 100) add(":sub-text-scale=$scale")
            val margin = app.lumen.domain.AppSettings.subtitleMarginPx.value
            if (margin != 0) add(":sub-margin=$margin")
            when (app.lumen.domain.AppSettings.subtitleColor.value) {
                "yellow" -> add(":freetype-color=16776960")   // 0xFFFF00
                "cyan" -> add(":freetype-color=65535")        // 0x00FFFF
                "green" -> add(":freetype-color=65280")       // 0x00FF00
            }
            // Normalisation du volume (réglage « Audio avancé », comme Jellyfin).
            when (app.lumen.domain.AppSettings.audioNormalization.value) {
                "track" -> add(":audio-replay-gain-mode=track")
                "album" -> add(":audio-replay-gain-mode=album")
            }
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

    override fun stats(): PlayerStats? = runCatching {
        val s = player.media().info()?.statistics() ?: return null
        PlayerStats(
            // libvlc exprime les débits en kO/s → ×8 pour des kb/s.
            inputKbps = (s.inputBitrate() * 8000).toInt(),
            inputBytesRead = s.inputBytesRead().toLong(),
            demuxKbps = (s.demuxBitrate() * 8000).toInt(),
            picturesLost = s.picturesLost(),
        )
    }.getOrNull()

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

/** Moteur de repli : affiche la raison au lieu de faire tomber l'application. */
private class UnavailableEngine(reason: String) : PlayerEngine {
    override val name: String = "indisponible"
    private val _state = MutableStateFlow(PlayerState(error = reason))
    override val state: StateFlow<PlayerState> = _state
    override fun play(url: String, headers: Map<String, String>, startMs: Long, audioSlaveUrl: String?) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun release() = Unit
}

@Composable
actual fun rememberPlayerEngine(): PlayerEngine {
    val engine = remember {
        runCatching { VlcjEngine() }
            .getOrElse { UnavailableEngine(it.message ?: "Lecteur indisponible") }
    }
    DisposableEffect(Unit) {
        // Libération HORS du thread UI : release() entre dans du code natif et
        // peut prendre son temps (voire se bloquer) si libvlc est en mauvaise
        // posture. Sur le thread UI, cela figerait toute la fenêtre — y compris
        // son bouton de fermeture.
        onDispose {
            Thread({ runCatching { engine.release() } }, "lumen-player-release")
                .apply { isDaemon = true }
                .start()
        }
    }
    return engine
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier, fill: Boolean) {
    // Le moteur de repli n'a pas de surface : sans ce garde-fou, le transtypage
    // ferait tomber l'application au lieu d'afficher la raison de l'échec.
    val vlcj = engine as? VlcjEngine ?: run {
        Box(modifier.background(Color.Black))
        return
    }
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

/** Détecte les cartes de rendu DRM (/dev/dri/renderD*) exploitables par VAAPI. */
actual fun availableTranscodeProfiles(): List<String> = runCatching {
    java.io.File("/dev/dri").listFiles()
        ?.filter { it.name.startsWith("renderD") }
        ?.map { "vaapi-${it.name}" }
        ?.sorted()
        .orEmpty()
}.getOrDefault(emptyList())
