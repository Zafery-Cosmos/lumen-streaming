package app.lumen.update

actual val updatePlatformKey: String = "android"

// TODO(Android) : écrire dans le cache externe puis Intent d'installation APK
// (nécessite un Context + REQUEST_INSTALL_PACKAGES). Desktop d'abord.
actual fun saveUpdateFile(fileName: String, bytes: ByteArray): String? = null

actual fun applyUpdate(path: String): Boolean = false

actual fun nowMillis(): Long = System.currentTimeMillis()
