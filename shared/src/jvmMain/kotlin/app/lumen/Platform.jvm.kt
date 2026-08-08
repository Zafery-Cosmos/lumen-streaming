package app.lumen

import java.net.InetAddress

actual fun platformDeviceName(): String = try {
    InetAddress.getLocalHost().hostName
} catch (_: Exception) {
    System.getProperty("os.name") ?: "PC"
}

/** Téléchargement direct dans ~/Téléchargements (ou ~/Downloads), en tâche de fond. */
actual fun platformDownload(url: String, fileName: String): Boolean = try {
    val home = System.getProperty("user.home")
    val dir = listOf("Téléchargements", "Downloads")
        .map { java.io.File(home, it) }
        .firstOrNull { it.isDirectory } ?: java.io.File(home)
    val target = java.io.File(dir, fileName)
    Thread {
        runCatching {
            java.net.URI(url).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }.start()
    true
} catch (_: Exception) {
    false
}
