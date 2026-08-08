package app.lumen.player

/** Statistiques temps réel d'un torrent en cours de lecture. */
data class TorrentStats(
    val connectedPeers: Int,
    val totalPeers: Int,
    val downloadSpeedBps: Long,
    val downloadedPercent: Double,
)

/**
 * Moteur torrent intégré (comme Stremio) : démarre TorrServer en sidecar et
 * transforme un infoHash en flux HTTP lisible par le lecteur, pendant que le
 * téléchargement avance. Desktop d'abord ; Android plus tard.
 */
expect suspend fun ensureTorrentEngine(): Boolean

/** URL de streaming HTTP locale pour cet infoHash (moteur démarré au préalable). */
expect fun torrentStreamUrl(infoHash: String, title: String): String

/** Stats du torrent en cours ; null si indisponibles. */
expect suspend fun torrentStats(infoHash: String): TorrentStats?
