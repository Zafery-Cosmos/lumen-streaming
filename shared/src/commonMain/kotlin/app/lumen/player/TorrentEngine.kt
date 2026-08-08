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

/** État du moteur : joignable ou non, et poids réel du cache sur le disque. */
data class TorrentEngineStatus(
    val running: Boolean,
    val endpoint: String,
    val cacheBytes: Long,
    val cacheDir: String,
)

expect suspend fun torrentEngineStatus(): TorrentEngineStatus

/** Vide le cache du moteur ; renvoie le nombre d'octets libérés. */
expect suspend fun purgeTorrentCache(): Long
