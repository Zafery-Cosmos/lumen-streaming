package app.lumen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.lumen.domain.AppSettings

// Palette Lumen — fond noir profond, accent rouge chaud.
// Le fond et les surfaces dépendent du réglage « noir pur OLED » (§6.2) :
// les getters lisent l'état, donc tout bascule en direct sans redémarrage.
object LumenColors {
    val Background: Color
        get() = if (AppSettings.oledBlack.value) Color(0xFF000000) else Color(0xFF0B0B0F)
    val Surface: Color
        get() = if (AppSettings.oledBlack.value) Color(0xFF0C0C0C) else Color(0xFF14141A)
    val SurfaceHigh: Color
        get() = if (AppSettings.oledBlack.value) Color(0xFF161616) else Color(0xFF1D1D25)

    val Accent = Color(0xFFE8443A)          // rouge Lumen
    val OnBackground = Color(0xFFF2F2F5)
    val Muted = Color(0xFF9A9AA6)           // textes secondaires
}

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    // Reconstruit à chaque changement de réglage (les getters lisent l'état).
    val scheme = darkColorScheme(
        primary = LumenColors.Accent,
        onPrimary = Color.White,
        background = LumenColors.Background,
        onBackground = LumenColors.OnBackground,
        surface = LumenColors.Surface,
        onSurface = LumenColors.OnBackground,
        surfaceVariant = LumenColors.SurfaceHigh,
        onSurfaceVariant = LumenColors.Muted,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
