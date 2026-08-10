package app.lumen.i18n

/** Desktop et Android partagent la même implémentation (java.util.Locale). */
actual fun systemLanguageCode(): String = java.util.Locale.getDefault().language.lowercase()
