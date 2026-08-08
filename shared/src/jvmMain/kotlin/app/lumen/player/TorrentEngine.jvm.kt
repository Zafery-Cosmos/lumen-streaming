package app.lumen.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

private const val PORT = 8555
private const val BASE = "http://127.0.0.1:$PORT"
private const val BINARY_URL =
    "https://github.com/YouROK/TorrServer/releases/latest/download/TorrServer-linux-amd64"

private val json = Json { ignoreUnknownKeys = true }

private fun engineDir(): File =
    File(System.getProperty("user.home"), ".local/share/lumen").apply { mkdirs() }

private fun httpGet(url: String, timeoutMs: Int = 3000): String? = runCatching {
    val conn = URI(url).toURL().openConnection() as HttpURLConnection
    conn.connectTimeout = timeoutMs
    conn.readTimeout = timeoutMs
    conn.inputStream.use { it.readBytes().decodeToString() }
}.getOrNull()

private fun httpPost(url: String, body: String, timeoutMs: Int = 4000): String? = runCatching {
    val conn = URI(url).toURL().openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.connectTimeout = timeoutMs
    conn.readTimeout = timeoutMs
    conn.setRequestProperty("Content-Type", "application/json")
    conn.outputStream.use { it.write(body.encodeToByteArray()) }
    conn.inputStream.use { it.readBytes().decodeToString() }
}.getOrNull()

/**
 * Démarre TorrServer si besoin : binaire téléchargé au premier usage dans
 * ~/.local/share/lumen, lancé en sidecar, prêt quand /echo répond.
 */
actual suspend fun ensureTorrentEngine(): Boolean = withContext(Dispatchers.Default) {
    if (httpGet("$BASE/echo") != null) return@withContext true

    val binary = File(engineDir(), "torrserver")
    if (!binary.exists()) {
        val ok = runCatching {
            URI(BINARY_URL).toURL().openStream().use { input ->
                binary.outputStream().use { output -> input.copyTo(output) }
            }
            binary.setExecutable(true)
        }.isSuccess
        if (!ok) return@withContext false
    }

    runCatching {
        ProcessBuilder(
            binary.absolutePath,
            "--port", PORT.toString(),
            "--path", engineDir().absolutePath,
        )
            .redirectOutput(File(engineDir(), "torrserver.log"))
            .redirectErrorStream(true)
            .start()
    }.getOrNull() ?: return@withContext false

    // Le moteur met quelques secondes à ouvrir son port.
    repeat(30) {
        if (httpGet("$BASE/echo") != null) return@withContext true
        delay(500)
    }
    false
}

actual fun torrentStreamUrl(infoHash: String, title: String): String {
    val magnet = URLEncoder.encode("magnet:?xt=urn:btih:$infoHash", "UTF-8")
    return "$BASE/stream/video?link=$magnet&index=1&play"
}

actual suspend fun torrentStats(infoHash: String): TorrentStats? = withContext(Dispatchers.Default) {
    val body = httpPost("$BASE/torrents", """{"action":"get","hash":"${infoHash.lowercase()}"}""")
        ?: return@withContext null
    runCatching {
        val obj = json.parseToJsonElement(body).jsonObject
        fun long(vararg keys: String): Long? =
            keys.firstNotNullOfOrNull { k -> (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull }
        fun double(vararg keys: String): Double? =
            keys.firstNotNullOfOrNull { k -> (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull }

        val size = long("torrent_size", "preload_size") ?: 0L
        val loaded = long("loaded_size", "preloaded_bytes") ?: 0L
        TorrentStats(
            connectedPeers = long("connected_seeders", "active_peers")?.toInt() ?: 0,
            totalPeers = long("total_peers")?.toInt() ?: 0,
            downloadSpeedBps = double("download_speed")?.toLong() ?: 0L,
            downloadedPercent = if (size > 0) loaded * 100.0 / size else 0.0,
        )
    }.getOrNull()
}
