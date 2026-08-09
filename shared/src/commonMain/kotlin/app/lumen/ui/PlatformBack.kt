package app.lumen.ui

import androidx.compose.runtime.Composable

/**
 * Bouton/geste « retour » du système.
 *
 * Sur Android c'est LE geste de navigation : sans l'intercepter, un retour
 * quitte purement et simplement l'application au lieu de remonter d'un écran.
 * Sur desktop il n'existe pas — l'implémentation y est volontairement vide.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
