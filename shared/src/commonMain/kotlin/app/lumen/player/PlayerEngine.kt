package app.lumen.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/** État courant du lecteur, observé par l'UI maison. */
data class PlayerState(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val ended: Boolean = false,
    val error: String? = null,
)

/**
 * Abstraction du moteur de lecture (plan §1) : VLCJ/libvlc sur desktop,
 * Media3/ExoPlayer sur Android — et libmpv plus tard, sans toucher à l'UI.
 * L'UI du lecteur est 100% maison : le moteur ne dessine que la vidéo.
 */
/** Une piste (audio ou sous-titres) exposée par le moteur. */
data class MediaTrack(val id: Int, val label: String)

interface PlayerEngine {
    val state: StateFlow<PlayerState>

    /** Nom lisible du moteur (affiché dans « Session » du lecteur). */
    val name: String get() = "natif"

    /** Charge et lance une URL (flux direct ou HLS), avec en-têtes optionnels. */
    fun play(url: String, headers: Map<String, String> = emptyMap(), startMs: Long = 0)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun release()

    // Options avancées — implémentations par défaut neutres pour que chaque
    // moteur n'implémente que ce qu'il sait faire.
    fun setRate(rate: Float) {}
    fun setVolume(volume: Int) {}
    fun audioTracks(): List<MediaTrack> = emptyList()
    fun subtitleTracks(): List<MediaTrack> = emptyList()
    fun selectAudioTrack(id: Int) {}
    fun selectSubtitleTrack(id: Int) {}

    /** Capture d'écran de la frame courante ; false si non supporté. */
    fun snapshot(): Boolean = false

    /** Statistiques temps réel du flux ; null si le moteur ne les expose pas. */
    fun stats(): PlayerStats? = null
}

/** Statistiques du flux en cours (panneau « Statistiques » du lecteur). */
data class PlayerStats(
    val inputKbps: Int,          // débit d'arrivée
    val inputBytesRead: Long,    // données reçues
    val demuxKbps: Int,          // débit utile après démultiplexage
    val picturesLost: Int,       // images perdues (santé du décodage)
)

/** Fabrique le moteur natif de la plateforme, lié au cycle de vie de l'écran. */
@Composable
expect fun rememberPlayerEngine(): PlayerEngine

/** Surface vidéo native du moteur — remplit l'espace donné, fond noir.
 *  fill=true → l'image remplit l'écran (recadrée), sinon ajustée. */
@Composable
expect fun VideoSurface(engine: PlayerEngine, modifier: Modifier, fill: Boolean = false)
