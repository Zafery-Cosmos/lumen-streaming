package app.lumen.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Deux voies, dans cet ordre :
 *
 * 1. `yt-dlp` s'il est installé — c'est le seul moyen d'obtenir le 1080p, qui
 *    n'existe qu'en DASH (vidéo et audio séparées).
 * 2. À défaut, l'URL de la page est passée telle quelle à libvlc, dont le
 *    script `youtube.lua` sait la résoudre. On y perd la haute définition,
 *    mais la bande-annonce se lit quand même.
 */
actual suspend fun resolveYouTubeStream(videoId: String): TrailerStream? =
    withContext(Dispatchers.IO) {
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        ytDlp(watchUrl) ?: TrailerStream(watchUrl, null, "libvlc")
    }

private fun ytDlp(watchUrl: String): TrailerStream? = runCatching {
    val process = ProcessBuilder(
        "yt-dlp", "-g", "--no-warnings", "--no-playlist",
        // Google verrouille ses URLs sur l'IP du client qui les a demandées.
        // yt-dlp sortant en IPv6 et la JVM en IPv4, le lecteur récoltait un
        // 403 sur un flux pourtant valide : on aligne les deux sur IPv4.
        "--force-ipv4",
        // On force du H.264 : c'est le seul codec que toutes les cartes
        // décodent en matériel. Les replis couvrent les vidéos sans DASH.
        "-f", "bv*[height<=1080][vcodec^=avc1]+ba[ext=m4a]/b[ext=mp4]/b",
        watchUrl,
    ).redirectErrorStream(false).start()

    val urls = process.inputStream.bufferedReader().readLines()
        .map { it.trim() }
        .filter { it.startsWith("http") }

    // yt-dlp interroge le réseau : au-delà, on bascule sur le repli plutôt que
    // de laisser l'utilisateur devant un écran figé.
    if (!process.waitFor(25, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return null
    }
    if (process.exitValue() != 0 || urls.isEmpty()) return null

    TrailerStream(
        videoUrl = urls[0],
        audioUrl = urls.getOrNull(1),
        resolvedBy = "yt-dlp",
    )
}.getOrNull()
