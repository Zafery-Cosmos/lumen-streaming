package app.lumen.security

import java.io.File

// Journal minimal, à côté du conteneur. Le supprimer ne donne rien d'utile :
// il ne contient aucune matière secrète, seulement un compteur et une date.
private fun logFile(): File = File(vaultDir(), ".lmn.state")

actual object AttemptLog {
    private fun read(): Pair<Int, Long> = runCatching {
        val parts = logFile().readText().trim().split(":")
        parts[0].toInt() to parts[1].toLong()
    }.getOrDefault(0 to 0L)

    actual fun count(): Int = read().first
    actual fun lastAt(): Long = read().second

    actual fun record(failed: Boolean) {
        runCatching {
            val n = if (failed) read().first + 1 else 0
            logFile().writeText("$n:${System.currentTimeMillis()}")
        }
    }
}
