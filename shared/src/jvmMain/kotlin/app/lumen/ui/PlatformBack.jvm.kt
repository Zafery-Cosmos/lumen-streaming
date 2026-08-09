package app.lumen.ui

import androidx.compose.runtime.Composable

// Pas de geste « retour » système sur desktop : rien à intercepter.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
