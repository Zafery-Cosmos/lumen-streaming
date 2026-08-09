package app.lumen.player

// TODO(Android) : NanoHTTPD ou ServerSocket local — desktop d'abord.
actual object StreamProxy {
    actual suspend fun ensureRunning(): Boolean = false
    actual fun register(upstreamUrl: String, headers: Map<String, String>, extension: String): String = upstreamUrl
    // FTP n'a pas d'équivalent "URL directe" : sans proxy, rien n'est jouable.
    actual fun registerFtp(config: app.lumen.domain.FtpConfig, path: String, sizeBytes: Long?, extension: String): String = ""
    // Idem : le HLS d'un bucket exige la signature des segments a la volee.
    actual fun registerS3Hls(config: app.lumen.domain.PrivateStorageConfig, masterKey: String): String = ""
    actual fun baseUrl(): String? = null
}
