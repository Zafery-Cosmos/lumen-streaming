package app.lumen.update

import java.io.File

actual val updatePlatformKey: String
    get() {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            os.contains("win") -> "windows"
            os.contains("mac") -> "macos"
            else -> "linux"
        }
    }

private fun updatesDir(): File =
    File(System.getProperty("user.home"), ".local/share/lumen/updates").apply { mkdirs() }

actual fun saveUpdateFile(fileName: String, bytes: ByteArray): String? = runCatching {
    val target = File(updatesDir(), fileName)
    target.writeBytes(bytes)
    target.setExecutable(true)
    target.absolutePath
}.getOrNull()

/**
 * Installe la mise à jour puis relance Lumen.
 *
 * - Windows : on lance l'installeur (.msi/.exe), l'app se ferme.
 * - Linux : le nouveau .jar remplace celui en cours d'exécution. Comme on ne
 *   peut pas s'écraser soi-même proprement, un petit script attend la sortie
 *   du processus, copie, puis relance.
 */
actual fun applyUpdate(path: String): Boolean = runCatching {
    val file = File(path)
    if (!file.exists()) return false

    if (updatePlatformKey == "windows") {
        ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
        return true
    }

    val current = currentJarPath()
    if (current == null || !file.name.endsWith(".jar")) {
        // Format non remplaçable à chaud (AppImage…) : on l'ouvre simplement.
        ProcessBuilder("xdg-open", file.absolutePath).start()
        return true
    }

    val script = File(updatesDir(), "apply-update.sh")
    script.writeText(
        """
        #!/usr/bin/env bash
        # Attend la fermeture de Lumen, remplace le binaire, puis relance.
        sleep 2
        cp -f "${file.absolutePath}" "$current"
        exec "${'$'}JAVA" -jar "$current"
        """.trimIndent(),
    )
    script.setExecutable(true)

    val java = File(System.getProperty("java.home"), "bin/java").absolutePath
    ProcessBuilder("bash", script.absolutePath)
        .apply { environment()["JAVA"] = java }
        .start()
    true
}.getOrDefault(false)

/** Chemin du .jar en cours d'exécution, ou null si lancé autrement. */
private fun currentJarPath(): String? = runCatching {
    val src = object {}.javaClass.protectionDomain?.codeSource?.location ?: return null
    val f = File(src.toURI())
    if (f.isFile && f.name.endsWith(".jar")) f.absolutePath else null
}.getOrNull()

actual fun nowMillis(): Long = System.currentTimeMillis()
