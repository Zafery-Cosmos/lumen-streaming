package app.lumen.player

import app.lumen.domain.FtpConfig
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
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

private sealed class ProxiedSource {
    data class Http(val url: String, val headers: Map<String, String>) : ProxiedSource()
    data class Ftp(val config: FtpConfig, val path: String, val sizeBytes: Long?) : ProxiedSource()
    /** Dossier HLS d'un bucket : [dirPrefix] est le dossier du master ("" ou "…/"). */
    data class S3Hls(val config: app.lumen.domain.PrivateStorageConfig, val dirPrefix: String) : ProxiedSource()
    /** Dossier HLS posé sur un serveur SFTP/FTP. */
    data class RemoteHls(val target: app.lumen.domain.UploadTarget, val dir: String) : ProxiedSource()
}

actual object StreamProxy {
    private const val PREFIX = "/api/proxyv2/"
    private val streams = ConcurrentHashMap<String, ProxiedSource>()
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

    private fun store(id: String, source: ProxiedSource): String {
        streams[id] = source
        // On borne le nombre d'entrées : une session peut enchaîner les sources.
        if (streams.size > 64) {
            streams.keys.take(streams.size - 64).forEach(streams::remove)
        }
        return id
    }

    private fun newId(): String = buildString { repeat(16) { append("0123456789abcdef"[Random.nextInt(16)]) } }

    actual fun register(upstreamUrl: String, headers: Map<String, String>, extension: String): String {
        val id = store(newId(), ProxiedSource.Http(upstreamUrl, headers))
        return "http://127.0.0.1:$port$PREFIX$id/stream.$extension"
    }

    actual fun registerFtp(config: FtpConfig, path: String, sizeBytes: Long?, extension: String): String {
        val id = store(newId(), ProxiedSource.Ftp(config, path, sizeBytes))
        return "http://127.0.0.1:$port$PREFIX$id/stream.$extension"
    }

    actual fun registerS3Hls(config: app.lumen.domain.PrivateStorageConfig, masterKey: String): String {
        val dir = masterKey.substringBeforeLast('/', "")
        val id = store(newId(), ProxiedSource.S3Hls(config, if (dir.isEmpty()) "" else "$dir/"))
        return "http://127.0.0.1:$port$PREFIX$id/${masterKey.substringAfterLast('/')}"
    }

    actual fun registerRemoteHls(target: app.lumen.domain.UploadTarget, masterPath: String): String {
        val dir = masterPath.substringBeforeLast('/', "")
        val id = store(newId(), ProxiedSource.RemoteHls(target, dir))
        return "http://127.0.0.1:$port$PREFIX$id/${masterPath.substringAfterLast('/')}"
    }

    actual fun baseUrl(): String? = server?.let { "http://127.0.0.1:$port" }

    private fun handle(exchange: HttpExchange) {
        val id = exchange.requestURI.path
            .removePrefix(PREFIX)
            .substringBefore('/')
        when (val stream = streams[id]) {
            null -> {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            is ProxiedSource.Http -> handleHttp(exchange, stream)
            is ProxiedSource.Ftp -> handleFtp(exchange, stream)
            is ProxiedSource.S3Hls -> handleS3Hls(exchange, stream)
            is ProxiedSource.RemoteHls -> handleRemoteHls(exchange, stream)
        }
    }

    /**
     * Sert un fichier du dossier HLS distant. Le lecteur demande les segments
     * en relatif : ils sont résolus dans le même dossier, sur le serveur.
     */
    private fun handleRemoteHls(exchange: HttpExchange, stream: ProxiedSource.RemoteHls) {
        try {
            val rest = exchange.requestURI.path.removePrefix(PREFIX).substringAfter('/')
            val path = stream.dir + "/" + java.net.URLDecoder.decode(rest, "UTF-8")
            val start = exchange.requestHeaders["Range"]?.firstOrNull()
                ?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L

            val reader = app.lumen.domain.RemoteReader()
            val size = reader.size(stream.target, path)
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            val remaining = size?.let { it - start } ?: 0L
            val status = if (start > 0 && size != null) 206 else 200
            if (status == 206 && size != null) {
                exchange.responseHeaders.add("Content-Range", "bytes $start-${size - 1}/$size")
            }
            exchange.sendResponseHeaders(status, if (remaining > 0) remaining else 0L)
            exchange.responseBody.use { sink -> reader.copyTo(stream.target, path, start, sink) }
        } catch (_: Exception) {
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally {
            exchange.close()
        }
    }

    /**
     * Sert n'importe quel fichier du dossier HLS : le chemin après l'id est la
     * clé relative au dossier du master (playlist, init.mp4, seg_XXXX.m4s…),
     * présignée puis relayée telle quelle — Range compris, pour le seek.
     */
    private fun handleS3Hls(exchange: HttpExchange, stream: ProxiedSource.S3Hls) {
        var connection: HttpURLConnection? = null
        try {
            val rest = exchange.requestURI.path
                .removePrefix(PREFIX)
                .substringAfter('/')
            val key = stream.dirPrefix + java.net.URLDecoder.decode(rest, "UTF-8")
            val signed = app.lumen.domain.S3Client().presignGet(stream.config, key, 3600)

            connection = (URI(signed).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 60_000
                exchange.requestHeaders["Range"]?.firstOrNull()?.let { setRequestProperty("Range", it) }
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
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally {
            connection?.disconnect()
            exchange.close()
        }
    }

    private fun handleHttp(exchange: HttpExchange, stream: ProxiedSource.Http) {
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

    private fun handleFtp(exchange: HttpExchange, stream: ProxiedSource.Ftp) {
        val client = FTPClient()
        try {
            client.connect(stream.config.host, stream.config.port)
            if (!client.login(stream.config.username, stream.config.password)) {
                runCatching { exchange.sendResponseHeaders(502, -1) }
                return
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            // Le Range demandé par le lecteur devient un REST FTP (reprise à
            // l'octet) — et sa borne de FIN doit être respectée : sans ça, une
            // requête "bytes=0-1023" (sondage de format) renvoie tout le fichier.
            val rangeHeader = exchange.requestHeaders["Range"]?.firstOrNull()
            val total = stream.sizeBytes
            var start = 0L
            var end: Long? = null
            if (rangeHeader != null) {
                val spec = rangeHeader.removePrefix("bytes=")
                start = spec.substringBefore('-').toLongOrNull() ?: 0L
                end = spec.substringAfter('-', "").toLongOrNull()
            }
            if (start > 0) client.setRestartOffset(start)

            val input = client.retrieveFileStream(stream.path)
            if (input == null) {
                runCatching { exchange.sendResponseHeaders(502, -1) }
                return
            }

            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            val limit = when {
                end != null -> end - start + 1
                total != null -> total - start
                else -> null
            }
            val status = if (rangeHeader != null) 206 else 200
            if (status == 206 && total != null) {
                exchange.responseHeaders.add("Content-Range", "bytes $start-${end ?: (total - 1)}/$total")
            }
            exchange.sendResponseHeaders(status, if (limit != null && limit > 0) limit else 0L)

            input.use { source ->
                exchange.responseBody.use { sink ->
                    if (limit != null) source.copyToLimited(sink, limit, 128 * 1024) else source.copyTo(sink, 128 * 1024)
                }
            }
            client.completePendingCommand()
        } catch (_: Exception) {
            // Le lecteur ferme la connexion à chaque saut : c'est normal.
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally {
            runCatching { client.disconnect() }
            exchange.close()
        }
    }
}

/** Comme [java.io.InputStream.copyTo], mais s'arrête après [limit] octets. */
private fun java.io.InputStream.copyToLimited(out: java.io.OutputStream, limit: Long, bufferSize: Int) {
    val buffer = ByteArray(bufferSize)
    var remaining = limit
    while (remaining > 0) {
        val n = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (n < 0) break
        out.write(buffer, 0, n)
        remaining -= n
    }
}
