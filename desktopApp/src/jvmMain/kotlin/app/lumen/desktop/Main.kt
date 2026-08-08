package app.lumen.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.lumen.App
import app.lumen.resources.Res
import app.lumen.resources.logo
import org.jetbrains.compose.resources.painterResource

fun main() {
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
