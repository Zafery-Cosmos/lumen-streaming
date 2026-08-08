package app.lumen.player

// TODO(Android) : moteur torrent embarqué (libtorrent) — desktop d'abord.
actual suspend fun ensureTorrentEngine(): Boolean = false
actual fun torrentStreamUrl(infoHash: String, title: String): String = ""
actual suspend fun torrentStats(infoHash: String): TorrentStats? = null

actual suspend fun torrentEngineStatus(): TorrentEngineStatus =
    TorrentEngineStatus(running = false, endpoint = "", cacheBytes = 0, cacheDir = "")

actual suspend fun purgeTorrentCache(): Long = 0

actual suspend fun restartTorrentEngine(): Boolean = false
