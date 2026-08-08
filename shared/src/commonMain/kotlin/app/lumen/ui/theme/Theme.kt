package app.lumen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette Lumen — fond noir profond, accent rouge chaud.
// Le thème clair et le noir pur OLED viendront avec l'écran Paramètres (L10b).
object LumenColors {
    val Background = Color(0xFF0B0B0F)      // noir profond, légèrement bleuté
    val Surface = Color(0xFF14141A)         // cartes, feuilles
    val SurfaceHigh = Color(0xFF1D1D25)     // éléments survolés / focus
    val Accent = Color(0xFFE8443A)          // rouge Lumen (personnalisable plus tard)
    val OnBackground = Color(0xFFF2F2F5)
    val Muted = Color(0xFF9A9AA6)           // textes secondaires
}

private val DarkScheme = darkColorScheme(
    primary = LumenColors.Accent,
    onPrimary = Color.White,
    background = LumenColors.Background,
    onBackground = LumenColors.OnBackground,
    surface = LumenColors.Surface,
    onSurface = LumenColors.OnBackground,
    surfaceVariant = LumenColors.SurfaceHigh,
    onSurfaceVariant = LumenColors.Muted,
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    // Sombre par défaut : c'est l'identité de l'app, le clair sera un choix explicite.
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
