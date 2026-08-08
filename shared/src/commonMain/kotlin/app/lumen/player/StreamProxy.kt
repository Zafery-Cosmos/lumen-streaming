package app.lumen.player

/**
 * Proxy de flux local.
 *
 * Toutes les sources — Jellyfin, addons, torrents — sont réexposées derrière
 * une adresse locale propre : `http://127.0.0.1:PORT/api/proxyv2/<id>/stream.mkv`.
 *
 * Ce n'est pas qu'une question d'allure : l'URL Jellyfin porte le jeton
 * d'accès en clair (`api_key=…`), donc la copier ou la partager revient à
 * donner sa session. Ici le jeton reste dans l'app, et l'URL locale ne vaut
 * rien en dehors de la machine. En prime, les en-têtes exigés par certains
 * addons (Referer, User-Agent) sont réinjectés par le proxy, ce qui rend le
 * lien lisible par un lecteur externe comme VLC.
 */
expect object StreamProxy {
    /** Démarre le proxy si besoin ; renvoie false s'il est indisponible. */
    suspend fun ensureRunning(): Boolean

    /**
     * Enregistre une source et renvoie son URL locale.
     * [extension] sert uniquement à ce que les lecteurs devinent le format.
     */
    fun register(
        upstreamUrl: String,
        headers: Map<String, String> = emptyMap(),
        extension: String = "mkv",
    ): String

    /** Adresse de base du proxy, ou null s'il ne tourne pas. */
    fun baseUrl(): String?
}

/** Devine l'extension d'un flux à partir de son URL. */
fun guessStreamExtension(url: String): String {
    val path = url.substringBefore('?').substringAfterLast('/')
    return when {
        path.endsWith(".m3u8") -> "m3u8"
        path.endsWith(".mp4") -> "mp4"
        path.endsWith(".mkv") -> "mkv"
        path.endsWith(".webm") -> "webm"
        path.endsWith(".avi") -> "avi"
        url.contains("main.m3u8") || url.contains("master.m3u8") -> "m3u8"
        else -> "mkv"
    }
}
