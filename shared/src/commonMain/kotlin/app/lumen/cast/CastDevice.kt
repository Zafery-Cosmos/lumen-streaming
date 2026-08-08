package app.lumen.cast

/** Les deux familles d'appareils que Lumen sait piloter. */
enum class CastKind { CHROMECAST, DLNA }

/** Un récepteur trouvé sur le réseau local. */
data class CastDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val kind: CastKind,
    /** DLNA uniquement : chemin SOAP du service AVTransport. */
    val controlUrl: String? = null,
) {
    val label: String get() = when (kind) {
        CastKind.CHROMECAST -> "$name · Chromecast"
        CastKind.DLNA -> "$name · DLNA"
    }
}

/**
 * Cherche les récepteurs du réseau : Chromecast par mDNS, téléviseurs et
 * consoles par SSDP. Les deux balayages tournent en parallèle.
 */
expect suspend fun discoverCastDevices(timeoutMs: Long = 4_000): List<CastDevice>

/**
 * Envoie un flux à l'appareil et lance la lecture.
 *
 * L'URL doit être joignable DEPUIS le récepteur : on transmet donc l'adresse
 * amont, jamais celle du proxy local qui n'écoute que sur la boucle.
 */
expect suspend fun castPlay(device: CastDevice, url: String, title: String): Boolean

/** Arrête la diffusion et rend la main à l'appareil. */
expect suspend fun castStop(device: CastDevice): Boolean

/** Type MIME déduit de l'URL, exigé par les deux protocoles. */
fun guessContentType(url: String): String {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u8") -> "application/x-mpegURL"
        path.endsWith(".webm") -> "video/webm"
        path.endsWith(".mkv") -> "video/x-matroska"
        path.endsWith(".mp3") -> "audio/mpeg"
        else -> "video/mp4"
    }
}
