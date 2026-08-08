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
    val configured = app.lumen.domain.AppSettings.downloadDir.value
    val dir = if (configured.isNotBlank()) {
        java.io.File(configured).apply { mkdirs() }
    } else {
        listOf("Téléchargements", "Downloads")
            .map { java.io.File(home, it) }
            .firstOrNull { it.isDirectory } ?: java.io.File(home)
    }
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

/** xdg-open gère http ET magnet (client torrent système). */
actual fun platformOpenUrl(url: String): Boolean = try {
    ProcessBuilder("xdg-open", url).start()
    true
} catch (_: Exception) {
    runCatching {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        true
    }.getOrDefault(false)
}

actual suspend fun pickDirectory(title: String): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        runCatching {
            val chooser = javax.swing.JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            }
            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else null
        }.getOrNull()
    }

actual fun findHlsMaster(directory: String): String? = runCatching {
    val root = java.io.File(directory)
    if (!root.isDirectory) return null
    // On privilégie « master.m3u8 », sinon la première playlist rencontrée.
    val all = root.walkTopDown().maxDepth(3).filter { it.isFile && it.name.endsWith(".m3u8") }.toList()
    (all.firstOrNull { it.name.equals("master.m3u8", true) }
        ?: all.firstOrNull { it.name.equals("index.m3u8", true) }
        ?: all.firstOrNull())?.absolutePath
}.getOrNull()

actual fun readLocalText(path: String): String? =
    runCatching { java.io.File(path).readText() }.getOrNull()

actual fun resolveSibling(masterPath: String, relative: String): String =
    java.io.File(java.io.File(masterPath).parentFile, relative).absolutePath

actual fun parentFolderName(path: String): String =
    java.io.File(path).parentFile?.name.orEmpty()
