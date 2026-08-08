package app.lumen.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.lumen.App
import app.lumen.resources.Res
import app.lumen.resources.logo
import org.jetbrains.compose.resources.painterResource

/**
 * Aligne le WM_CLASS de la fenetre sur le nom du raccourci installe.
 *
 * Sous X11 et XWayland, AWT derive WM_CLASS du nom de la classe principale :
 * la fenetre s annoncait « app-lumen-desktop-MainKt ». Le bureau ne pouvait
 * donc pas la rapprocher de « lumen.desktop », et affichait l icone generique
 * de Java dans la barre des taches — alors que le logo etait bien installe.
 *
 * Il n existe aucune propriete systeme pour cela ; le champ interne du toolkit
 * est le seul point d entree. L echec est sans consequence : on perd juste
 * l icone, l application fonctionne.
 */
private fun alignWindowClass() {
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, "Lumen")
    }
}

fun main() {
    // Doit preceder la creation de la fenetre : WM_CLASS est fige a l ouverture.
    alignWindowClass()
    // Declare le raccourci et les icones de theme, sans quoi le bureau ne sait
    // pas a quelle application rattacher la fenetre.
    DesktopEntry.installIfNeeded()
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lumen",
        icon = painterResource(Res.drawable.logo),
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App()
    }
    }
}
