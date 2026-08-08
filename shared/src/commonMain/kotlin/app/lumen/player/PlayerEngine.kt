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
interface PlayerEngine {
    val state: StateFlow<PlayerState>

    /** Charge et lance une URL (flux direct ou HLS), avec en-têtes optionnels. */
    fun play(url: String, headers: Map<String, String> = emptyMap(), startMs: Long = 0)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun release()
}

/** Fabrique le moteur natif de la plateforme, lié au cycle de vie de l'écran. */
@Composable
expect fun rememberPlayerEngine(): PlayerEngine

/** Surface vidéo native du moteur — remplit l'espace donné, fond noir. */
@Composable
expect fun VideoSurface(engine: PlayerEngine, modifier: Modifier)
