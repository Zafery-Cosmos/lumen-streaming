package app.lumen.update

import java.io.File
import kotlin.system.exitProcess

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
 * - Linux : l'installation d'origine (/opt, RPM/DEB) appartient à root — on ne
 *   peut PAS y écrire. La mise à jour vit donc en espace utilisateur :
 *   ~/.local/share/lumen/app/lumen.jar + un lanceur, et les entrées de menu
 *   utilisateur sont réécrites vers ce lanceur. Un script attend la VRAIE
 *   sortie du processus (pas un délai arbitraire), copie, puis relance.
 *   Dans tous les cas l'app se ferme elle-même — sans ça, rien ne redémarre.
 */
actual fun applyUpdate(path: String): Boolean = runCatching {
    val file = File(path)
    if (!file.exists()) return false

    if (updatePlatformKey == "windows") {
        ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
        exitProcess(0)
    }

    if (!file.name.endsWith(".jar")) {
        // Format non installable à chaud (AppImage…) : on l'ouvre simplement.
        ProcessBuilder("xdg-open", file.absolutePath).start()
        return true
    }

    val home = System.getProperty("user.home")
    val appDir = File(home, ".local/share/lumen/app").apply { mkdirs() }
    val stableJar = File(appDir, "lumen.jar")

    // ATTENTION : un runtime jpackage n'a PAS de bin/java (le lanceur natif
    // utilise libjvm directement) — java.home ne suffit donc pas, le lanceur
    // cherche un vrai java parmi les emplacements plausibles.
    val bundledJava = File(System.getProperty("java.home"), "bin/java").absolutePath
    val launcher = File(appDir, "lumen.sh")
    launcher.writeText(
        """
        #!/usr/bin/env bash
        J=""
        for c in "$bundledJava" "${'$'}HOME"/.local/opt/*/bin/java /usr/bin/java; do
            [ -x "${'$'}c" ] && J="${'$'}c" && break
        done
        [ -n "${'$'}J" ] || J="${'$'}(command -v java)" || exit 1
        exec "${'$'}J" --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED -jar "${stableJar.absolutePath}" "${'$'}@"
        """.trimIndent() + "\n",
    )
    launcher.setExecutable(true)

    // Les entrées de menu UTILISATEUR pointant sur Lumen basculent vers le
    // lanceur : sans ça, l'icône relancerait l'ancienne version de /opt.
    File(home, ".local/share/applications").listFiles { f -> f.extension == "desktop" }
        ?.filter { it.readText().contains("Lumen") }
        ?.forEach { entry ->
            val rewritten = entry.readLines().joinToString("\n") { line ->
                if (line.startsWith("Exec=")) "Exec=${launcher.absolutePath}" else line
            }
            entry.writeText(rewritten + "\n")
        }

    val pid = ProcessHandle.current().pid()
    val script = File(updatesDir(), "apply-update.sh")
    script.writeText(
        """
        #!/usr/bin/env bash
        # Attend la sortie REELLE de Lumen (30 s max), installe, relance.
        for i in ${'$'}(seq 1 150); do
            kill -0 $pid 2>/dev/null || break
            sleep 0.2
        done
        cp -f "${file.absolutePath}" "${stableJar.absolutePath}"
        setsid "${launcher.absolutePath}" >/dev/null 2>&1 &
        """.trimIndent() + "\n",
    )
    script.setExecutable(true)

    ProcessBuilder("bash", script.absolutePath).start()
    // C'est NOUS qui devons quitter : le script attend notre sortie.
    exitProcess(0)
}.getOrDefault(false)

actual fun nowMillis(): Long = System.currentTimeMillis()
