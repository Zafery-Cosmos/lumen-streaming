package app.lumen.player

// TODO(Android) : moteur torrent embarqué (libtorrent) — desktop d'abord.
actual suspend fun ensureTorrentEngine(): Boolean = false
actual fun torrentStreamUrl(infoHash: String, title: String): String = ""
actual suspend fun torrentStats(infoHash: String): TorrentStats? = null
