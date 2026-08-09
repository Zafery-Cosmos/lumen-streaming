package app.lumen

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Dernier recours : si une exception remonte jusqu'en haut d'un thread, on
 * l'écrit sur disque avant de disparaître. Sans ça, un plantage lancé depuis
 * une icône de bureau ne laisse aucune trace — la sortie d'erreur n'est
 * rattachée à rien.
 */
object CrashLog {
    private val file: File
        get() = File(System.getProperty("user.home"), ".config/lumen").apply { mkdirs() }
            .let { File(it, "crash.log") }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                file.appendText(
                    buildString {
                        appendLine("=".repeat(60))
                        appendLine("Lumen ${app.lumen.update.LUMEN_VERSION} — thread « ${thread.name} »")
                        appendLine(trace.toString())
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun path(): String = file.absolutePath
}
