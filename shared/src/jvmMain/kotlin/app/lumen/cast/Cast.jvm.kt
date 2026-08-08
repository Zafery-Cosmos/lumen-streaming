package app.lumen.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

// ---------------------------------------------------------------------------
// Découverte
// ---------------------------------------------------------------------------

actual suspend fun discoverCastDevices(timeoutMs: Long): List<CastDevice> = coroutineScope {
    val both = listOf(
        async(Dispatchers.IO) { runCatching { discoverChromecasts(timeoutMs) }.getOrDefault(emptyList()) },
        async(Dispatchers.IO) { runCatching { discoverDlna(timeoutMs) }.getOrDefault(emptyList()) },
    ).awaitAll()
    both.flatten().distinctBy { it.id }
}

/**
 * mDNS : une requête PTR sur `_googlecast._tcp.local`.
 *
 * On se contente de repérer les répondeurs et de lire le nom convivial dans
 * leurs enregistrements TXT ; un vrai résolveur DNS-SD serait disproportionné
 * pour trois champs.
 */
private fun discoverChromecasts(timeoutMs: Long): List<CastDevice> {
    val query = ByteArrayOutputStream().apply {
        write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        listOf("_googlecast", "_tcp", "local").forEach { label ->
            write(label.length)
            write(label.toByteArray())
        }
        write(0)
        write(byteArrayOf(0, 12, 0, 1))   // QTYPE=PTR, QCLASS=IN
    }.toByteArray()

    val found = LinkedHashMap<String, CastDevice>()
    MulticastSocket().use { socket ->
        socket.soTimeout = 500
        // Multicast sur UDP : une requete unique se perd regulierement. On la
        // repete, ce qui ne coute rien et evite l appareil « invisible ».
        val group = InetAddress.getByName("224.0.0.251")
        repeat(3) { socket.send(DatagramPacket(query, query.size, group, 5353)) }
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(8192)
        while (System.currentTimeMillis() < deadline) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: Exception) {
                continue
            }
            val body = packet.data.copyOf(packet.length)
            if (!String(body, Charsets.ISO_8859_1).contains("googlecast")) continue
            val host = packet.address.hostAddress ?: continue
            val name = friendlyName(body) ?: "Chromecast $host"
            found[host] = CastDevice("cc:$host", name, host, 8009, CastKind.CHROMECAST)
        }
    }
    return found.values.toList()
}

/** Le champ TXT `fn=` porte le nom donné à l'appareil par son propriétaire. */
private fun friendlyName(packet: ByteArray): String? {
    val text = String(packet, Charsets.UTF_8)
    val index = text.indexOf("fn=").takeIf { it >= 0 } ?: return null
    return text.drop(index + 3)
        .takeWhile { it.code in 32..126 || it.code > 160 }
        .trim()
        .takeIf { it.isNotEmpty() }
}

/** SSDP : les téléviseurs et consoles s'annoncent comme MediaRenderer. */
private fun discoverDlna(timeoutMs: Long): List<CastDevice> {
    val search = (
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        ).toByteArray()

    val locations = LinkedHashSet<String>()
    DatagramSocket().use { socket ->
        socket.soTimeout = 500
        // Meme raison qu'en mDNS : le M-SEARCH est repete pour ne pas rater un
        // televiseur qui aurait laisse tomber le premier datagramme.
        val target = InetSocketAddress("239.255.255.250", 1900)
        repeat(3) { socket.send(DatagramPacket(search, search.size, target)) }
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(4096)
        while (System.currentTimeMillis() < deadline) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: Exception) {
                continue
            }
            String(packet.data, 0, packet.length)
                .lineSequence()
                .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                ?.substringAfter(':', "")
                ?.let { locations += it.trim().let { v -> if (v.startsWith("//")) "http:$v" else v } }
        }
    }

    return locations.mapNotNull { location ->
        runCatching {
            val xml = URI(location).toURL().readText()
            val name = xml.substringAfter("<friendlyName>", "")
                .substringBefore("</friendlyName>", "")
                .ifBlank { "Téléviseur" }
            // Le service AVTransport est celui qui accepte une URL et la joue.
            val service = xml.split("<service>").firstOrNull { it.contains("AVTransport") } ?: return@runCatching null
            val control = service.substringAfter("<controlURL>", "").substringBefore("</controlURL>", "")
            if (control.isBlank()) return@runCatching null
            val base = URI(location)
            CastDevice(
                id = "dlna:${base.host}:${base.port}",
                name = name.trim(),
                host = base.host,
                port = base.port,
                kind = CastKind.DLNA,
                controlUrl = if (control.startsWith("/")) control else "/$control",
            )
        }.getOrNull()
    }
}

// ---------------------------------------------------------------------------
// Lecture
// ---------------------------------------------------------------------------

actual suspend fun castPlay(device: CastDevice, url: String, title: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            when (device.kind) {
                CastKind.CHROMECAST -> ChromecastSessions.load(device, url, title)
                CastKind.DLNA -> dlnaPlay(device, url, title)
            }
        }.getOrDefault(false)
    }

actual suspend fun castStop(device: CastDevice): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        when (device.kind) {
            CastKind.CHROMECAST -> ChromecastSessions.stop(device)
            CastKind.DLNA -> {
                soap(device, "Stop", "<InstanceID>0</InstanceID>")
                true
            }
        }
    }.getOrDefault(false)
}

// --- DLNA ------------------------------------------------------------------

private fun dlnaPlay(device: CastDevice, url: String, title: String): Boolean {
    // Le récepteur veut une fiche DIDL-Lite, pas seulement une URL : sans elle
    // beaucoup de téléviseurs refusent le flux sans rien dire.
    val didl = """
        <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
        <item id="0" parentID="-1" restricted="1"><dc:title>${title.xml()}</dc:title>
        <upnp:class>object.item.videoItem</upnp:class>
        <res protocolInfo="http-get:*:${guessContentType(url)}:*">${url.xml()}</res></item></DIDL-Lite>
    """.trimIndent()

    val ok = soap(
        device, "SetAVTransportURI",
        "<InstanceID>0</InstanceID><CurrentURI>${url.xml()}</CurrentURI>" +
            "<CurrentURIMetaData>${didl.xml()}</CurrentURIMetaData>",
    )
    if (!ok) return false
    return soap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
}

private fun soap(device: CastDevice, action: String, body: String): Boolean = runCatching {
    val service = "urn:schemas-upnp-org:service:AVTransport:1"
    val envelope = """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:$action xmlns:u="$service">$body</u:$action></s:Body></s:Envelope>"""

    val url = URI("http://${device.host}:${device.port}${device.controlUrl}").toURL()
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 6_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        setRequestProperty("SOAPAction", "\"$service#$action\"")
    }
    connection.outputStream.use { it.write(envelope.toByteArray()) }
    val code = connection.responseCode
    connection.disconnect()
    code in 200..299
}.getOrDefault(false)

private fun String.xml(): String = replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;")

// --- Chromecast (protocole castv2) -----------------------------------------

/**
 * Une session Chromecast doit rester ouverte : le récepteur ferme l'application
 * si plus aucun émetteur n'est connecté. On garde donc la socket et on répond
 * aux PING tant que la diffusion dure.
 */
private object ChromecastSessions {
    private val open = ConcurrentHashMap<String, ChromecastSession>()

    fun load(device: CastDevice, url: String, title: String): Boolean {
        stop(device)
        val session = ChromecastSession(device.host, device.port)
        return if (session.connectAndLoad(url, title)) {
            open[device.id] = session
            true
        } else {
            session.close()
            false
        }
    }

    fun stop(device: CastDevice): Boolean {
        open.remove(device.id)?.close()
        return true
    }
}

private class ChromecastSession(private val host: String, private val port: Int) {
    private var socket: SSLSocket? = null
    private var pump: Thread? = null
    @Volatile private var alive = true

    private val sender = "sender-lumen"
    private val nsConnection = "urn:x-cast:com.google.cast.tp.connection"
    private val nsHeartbeat = "urn:x-cast:com.google.cast.tp.heartbeat"
    private val nsReceiver = "urn:x-cast:com.google.cast.receiver"
    private val nsMedia = "urn:x-cast:com.google.cast.media"

    /** Récepteur média par défaut de Google : celui qui sait lire une URL. */
    private val MEDIA_APP_ID = "CC1AD845"

    fun connectAndLoad(url: String, title: String): Boolean {
        // Le certificat du Chromecast est auto-signé et propre à l'appareil :
        // le valider n'a pas de sens, la liaison reste chiffrée.
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(TrustEverything), java.security.SecureRandom())
        }
        val raw = Socket()
        raw.connect(InetSocketAddress(host, port), 8_000)
        val tls = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        tls.soTimeout = 10_000
        tls.startHandshake()
        socket = tls

        send("receiver-0", nsConnection, """{"type":"CONNECT"}""")
        send("receiver-0", nsReceiver, """{"type":"LAUNCH","appId":"$MEDIA_APP_ID","requestId":1}""")

        // On attend que le récepteur média par défaut soit prêt et annonce son
        // canal de transport : c'est lui qui acceptera la commande LOAD.
        val transportId = awaitTransportId() ?: return false

        send(transportId, nsConnection, """{"type":"CONNECT"}""")
        val media = """
            {"type":"LOAD","requestId":2,"autoplay":true,"currentTime":0,
             "media":{"contentId":"${url.json()}","streamType":"BUFFERED",
             "contentType":"${guessContentType(url)}",
             "metadata":{"type":0,"metadataType":0,"title":"${title.json()}"}}}
        """.trimIndent().replace("\n", "")
        send(transportId, nsMedia, media)

        // On ne rend « vrai » qu'une fois la lecture confirmée par l'appareil.
        // Un LOAD accepté n'est pas un LOAD joué : format refusé, URL
        // injoignable depuis le téléviseur… l'échec doit remonter.
        val playing = awaitPlayback()
        if (!playing) return false
        startHeartbeat()
        return true
    }

    /** Attend un MEDIA_STATUS annonçant la lecture, ou l'échec du chargement. */
    private fun awaitPlayback(): Boolean {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val payload = receive() ?: return false
            if (payload.contains("\"PING\"")) {
                runCatching { send("receiver-0", nsHeartbeat, """{"type":"PONG"}""") }
                continue
            }
            if (payload.contains("LOAD_FAILED") || payload.contains("LOAD_CANCELLED")) return false
            if (payload.contains("MEDIA_STATUS")) {
                val state = payload.substringAfter("\"playerState\":\"", "").substringBefore('"')
                if (state == "PLAYING" || state == "BUFFERING") return true
                // IDLE juste apres le LOAD signifie « pas encore demarre », pas
                // « refuse » : l appareil passe par cet etat avant de tamponner.
                // Seul un idleReason d echec permet de conclure.
                if (state == "IDLE") {
                    val reason = payload.substringAfter("\"idleReason\":\"", "").substringBefore('"')
                    if (reason == "ERROR" || reason == "CANCELLED") return false
                }
            }
        }
        return false
    }

    /**
     * Attend le canal du récepteur média par défaut.
     *
     * Le premier RECEIVER_STATUS décrit l'application DÉJÀ ouverte — YouTube,
     * Netflix… Prendre son transportId envoie le LOAD à une application qui
     * ignore le namespace média : la commande part, rien ne se passe. On
     * n'accepte donc que le statut portant notre propre identifiant d'app.
     */
    private fun awaitTransportId(): String? {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            val payload = receive() ?: return null
            if (payload.contains("\"PING\"")) {
                runCatching { send("receiver-0", nsHeartbeat, """{"type":"PONG"}""") }
                continue
            }
            if (!payload.contains("RECEIVER_STATUS") || !payload.contains(MEDIA_APP_ID)) continue
            val id = payload.substringAfter("\"transportId\":\"", "").substringBefore('"')
            if (id.isNotEmpty()) return id
        }
        return null
    }

    /** Sans réponse aux PING, le récepteur coupe la session au bout de ~10 s. */
    private fun startHeartbeat() {
        pump = Thread {
            while (alive) {
                val payload = receive() ?: break
                if (payload.contains("\"PING\"")) {
                    runCatching { send("receiver-0", nsHeartbeat, """{"type":"PONG"}""") }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    fun close() {
        alive = false
        runCatching { send("receiver-0", nsReceiver, """{"type":"STOP","requestId":9}""") }
        runCatching { socket?.close() }
        pump?.interrupt()
    }

    // --- Encodage du message (protobuf CastMessage, écrit à la main) --------

    private fun send(destination: String, namespace: String, payload: String) {
        val body = ByteArrayOutputStream().apply {
            writeField(1, varint(0))                                  // protocol_version
            writeField(2, lengthDelimited(sender))                    // source_id
            writeField(3, lengthDelimited(destination))               // destination_id
            writeField(4, lengthDelimited(namespace))                 // namespace
            writeField(5, varint(0))                                  // payload_type = STRING
            writeField(6, lengthDelimited(payload))                   // payload_utf8
        }.toByteArray()

        val output = socket?.outputStream ?: return
        synchronized(this) {
            output.write(
                byteArrayOf(
                    (body.size ushr 24).toByte(), (body.size ushr 16).toByte(),
                    (body.size ushr 8).toByte(), body.size.toByte(),
                ),
            )
            output.write(body)
            output.flush()
        }
    }

    /** Le corps JSON est le seul champ qui nous intéresse en réception. */
    private fun receive(): String? = runCatching {
        val input = socket?.inputStream ?: return null
        val header = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(header, read, 4 - read)
            if (n < 0) return null
            read += n
        }
        val size = ((header[0].toInt() and 0xff) shl 24) or ((header[1].toInt() and 0xff) shl 16) or
            ((header[2].toInt() and 0xff) shl 8) or (header[3].toInt() and 0xff)
        val body = ByteArray(size)
        read = 0
        while (read < size) {
            val n = input.read(body, read, size - read)
            if (n < 0) return null
            read += n
        }
        val text = String(body, Charsets.UTF_8)
        val start = text.indexOf('{')
        if (start < 0) "" else text.substring(start)
    }.getOrNull()

    private fun ByteArrayOutputStream.writeField(number: Int, payload: ByteArray) {
        // Champs 1 et 5 : varints. Champs 2, 3, 4 et 6 : chaînes précédées de
        // leur longueur. C'est tout ce que CastMessage utilise.
        val wireType = if (number == 1 || number == 5) 0 else 2
        write(varint((number shl 3) or wireType))
        write(payload)
    }

    private fun varint(value: Int): ByteArray {
        var v = value
        val out = ByteArrayOutputStream()
        while (true) {
            val b = v and 0x7f
            v = v ushr 7
            if (v == 0) { out.write(b); break }
            out.write(b or 0x80)
        }
        return out.toByteArray()
    }

    private fun lengthDelimited(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return varint(bytes.size) + bytes
    }

    private fun String.json(): String = replace("\\", "\\\\").replace("\"", "\\\"")
}

private object TrustEverything : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
}
