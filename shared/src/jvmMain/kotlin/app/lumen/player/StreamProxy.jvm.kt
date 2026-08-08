package app.lumen.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.random.Random

private data class ProxiedStream(val url: String, val headers: Map<String, String>)

actual object StreamProxy {
    private const val PREFIX = "/api/proxyv2/"
    private val streams = ConcurrentHashMap<String, ProxiedStream>()
    private var server: HttpServer? = null
    private var port: Int = 0

    actual suspend fun ensureRunning(): Boolean = withContext(Dispatchers.IO) {
        if (server != null) return@withContext true
        runCatching {
            // Port 0 = le système en attribue un libre ; on n'écoute QUE sur
            // la boucle locale, le proxy n'est jamais joignable de l'extérieur.
            val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            http.executor = Executors.newCachedThreadPool { r ->
                Thread(r, "lumen-proxy").apply { isDaemon = true }
            }
            http.createContext(PREFIX) { exchange -> handle(exchange) }
            http.start()
            server = http
            port = http.address.port
            true
        }.getOrDefault(false)
    }

    actual fun register(upstreamUrl: String, headers: Map<String, String>, extension: String): String {
        val id = buildString { repeat(16) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        streams[id] = ProxiedStream(upstreamUrl, headers)
        // On borne le nombre d'entrées : une session peut enchaîner les sources.
        if (streams.size > 64) {
            streams.keys.take(streams.size - 64).forEach(streams::remove)
        }
        return "http://127.0.0.1:$port$PREFIX$id/stream.$extension"
    }

    actual fun baseUrl(): String? = server?.let { "http://127.0.0.1:$port" }

    private fun handle(exchange: HttpExchange) {
        val id = exchange.requestURI.path
            .removePrefix(PREFIX)
            .substringBefore('/')
        val stream = streams[id]
        if (stream == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URI(stream.url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = exchange.requestMethod
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                // Les en-têtes exigés par la source (Referer, User-Agent…).
                stream.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                // Le Range du lecteur doit passer : sans lui, pas de navigation
                // dans la vidéo — le lecteur redémarrerait au début à chaque saut.
                exchange.requestHeaders["Range"]?.firstOrNull()?.let {
                    setRequestProperty("Range", it)
                }
            }

            val status = connection.responseCode
            listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges").forEach { h ->
                connection.getHeaderField(h)?.let { exchange.responseHeaders.add(h, it) }
            }
            val length = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L
            exchange.sendResponseHeaders(status, if (length > 0) length else 0L)

            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            input?.use { source ->
                exchange.responseBody.use { sink -> source.copyTo(sink, 128 * 1024) }
            }
        } catch (_: Exception) {
            // Le lecteur ferme la connexion à chaque saut : c'est normal.
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally {
            connection?.disconnect()
            exchange.close()
        }
    }
}
