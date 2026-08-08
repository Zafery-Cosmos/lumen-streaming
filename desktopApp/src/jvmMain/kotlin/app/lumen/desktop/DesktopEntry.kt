package app.lumen.desktop

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Déclare Lumen auprès du bureau Linux : raccourci et icônes de thème.
 *
 * GNOME n'utilise PAS l'icône portée par la fenêtre (`_NET_WM_ICON`) pour la
 * barre des tâches. Il rapproche la fenêtre d'un fichier `.desktop` par son
 * `StartupWMClass`, puis lit le nom d'icône déclaré là. Sans cette
 * correspondance, il affiche une icône générique — même si l'application
 * embarque parfaitement son logo.
 *
 * Or le raccourci produit par jpackage s'appelle `lumen-lumen.desktop`, ne
 * porte aucun `StartupWMClass`, et pointe l'icône par chemin absolu au lieu
 * de l'installer dans le thème. On écrit donc notre propre entrée, dans le
 * dossier utilisateur : aucun privilège requis, et elle prime sur celle du
 * paquet.
 */
object DesktopEntry {

    /** Tailles attendues par le thème hicolor, du panneau au sélecteur. */
    private val ICON_SIZES = listOf(32, 48, 64, 128, 256, 512)

    /** Doit correspondre EXACTEMENT au WM_CLASS posé dans Main.kt. */
    private const val WM_CLASS = "Lumen"

    fun installIfNeeded() {
        if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return
        runCatching { install() }
    }

    private fun install() {
        val executable = launcherPath() ?: return
        val home = File(System.getProperty("user.home"))
        val source = DesktopEntry::class.java.getResourceAsStream("/lumen-logo.png")
            ?.use { ImageIO.read(it) } ?: return

        val iconRoot = File(home, ".local/share/icons/hicolor")
        ICON_SIZES.forEach { size ->
            val target = File(iconRoot, "${size}x$size/apps/lumen.png")
            if (target.isFile) return@forEach
            target.parentFile.mkdirs()
            ImageIO.write(source.scaledTo(size), "png", target)
        }

        val entry = File(home, ".local/share/applications/lumen.desktop")
        val contents = """
            [Desktop Entry]
            Type=Application
            Name=Lumen
            GenericName=Client Jellyfin
            Comment=Lumen Streaming — client Jellyfin natif
            Exec=${'$'}EXEC
            Icon=lumen
            Terminal=false
            Categories=AudioVideo;Video;Player;
            Keywords=jellyfin;streaming;video;films;series;
            StartupWMClass=$WM_CLASS
            StartupNotify=true
        """.trimIndent().replace("${'$'}EXEC", executable) + "\n"

        // On ne réécrit que si le contenu change : inutile de toucher au
        // disque et d'invalider les caches du bureau à chaque démarrage.
        if (entry.isFile && entry.readText() == contents) return
        entry.parentFile.mkdirs()
        entry.writeText(contents)

        refreshCaches(home)
    }

    /**
     * Chemin du lanceur natif produit par jpackage.
     *
     * En développement le processus est un `java` lancé à la main : un
     * raccourci pointant dessus serait cassé, donc on s'abstient.
     */
    private fun launcherPath(): String? {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return null
        val name = File(command).name
        if (name == "java" || name == "java.exe") return null
        return command
    }

    /** Les bureaux ne relisent pas les dossiers à chaud ; on les prévient. */
    private fun refreshCaches(home: File) {
        listOf(
            listOf("update-desktop-database", File(home, ".local/share/applications").path),
            listOf("gtk-update-icon-cache", "-f", "-t", File(home, ".local/share/icons/hicolor").path),
        ).forEach { command ->
            runCatching {
                ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }
        }
    }

    private fun BufferedImage.scaledTo(size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(this, 0, 0, size, size, null)
        g.dispose()
        return out
    }
}
