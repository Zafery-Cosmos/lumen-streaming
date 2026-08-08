package app.lumen.player

// TODO(Android) : NanoHTTPD ou ServerSocket local — desktop d'abord.
actual object StreamProxy {
    actual suspend fun ensureRunning(): Boolean = false
    actual fun register(upstreamUrl: String, headers: Map<String, String>, extension: String): String = upstreamUrl
    actual fun baseUrl(): String? = null
}
