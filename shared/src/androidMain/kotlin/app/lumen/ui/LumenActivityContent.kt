package app.lumen.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.lumen.App

/**
 * Point d'entrée Compose côté Android, hébergé ici (androidMain de :shared)
 * pour que :androidApp n'ait aucun code composable : avec le Kotlin intégré
 * d'AGP 9, le module app n'a ainsi pas besoin du plugin compose-compiler.
 */
fun ComponentActivity.setLumenContent() {
    app.lumen.AndroidCtx.app = applicationContext
    setContent { App() }
}
