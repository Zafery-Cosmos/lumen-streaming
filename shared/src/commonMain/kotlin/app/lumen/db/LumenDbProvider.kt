package app.lumen.db

import androidx.compose.runtime.Composable

/** Ouvre (ou crée) la base locale Lumen — pilote SQLite natif par plateforme. */
@Composable
expect fun rememberLumenDb(): LumenDb

/** SHA-256 hexadécimal — pour ne jamais stocker un PIN en clair. */
expect fun sha256Hex(input: String): String

/** Horodatage epoch millis (pour watch_state.updatedAt). */
expect fun epochMillis(): Long
