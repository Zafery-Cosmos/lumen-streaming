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
private const val BINARY_URL =
    "https://github.com/YouROK/TorrServer/releases/latest/download/TorrServer-linux-amd64"

/**
 * Adresse du moteur — un sidecar local par défaut, ou un serveur choisi par
 * l'utilisateur (Paramètres → Streaming). Dans ce second cas, on ne télécharge
 * ni ne lance rien : on ne fait que s'y connecter, il est géré ailleurs.
 */
private fun base(): String {
    val custom = app.lumen.domain.AppSettings.torrentServerUrl.value.trim()
    return if (custom.isNotEmpty()) custom.trimEnd('/') else "http://127.0.0.1:$PORT"
}

private fun usesCustomServer() = app.lumen.domain.AppSettings.torrentServerUrl.value.isNotBlank()

private val json = Json { ignoreUnknownKeys = true }

// Le binaire, son log et sa base de config vivent dans engineDir() À CÔTÉ du
// cache réel — les compter comme « cache occupé » gonflerait l'affichage de
// ~60 Mo en permanence, et les effacer au « vider le cache » détruirait la
// configuration du moteur au lieu de son cache.
private val NON_CACHE_FILES = setOf("torrserver", "torrserver.log", "config.db", "settings.json")

private fun cacheBytesIn(dir: File): Long = runCatching {
    dir.walkTopDown().filter { it.isFile && it.name !in NON_CACHE_FILES }.sumOf { it.length() }
}.getOrDefault(0L)

/**
 * INCIDENT réel : quand le dossier par défaut était ~/.local/share/lumen tout
 * court, purgeTorrentCache() a supprimé app/ (le lanceur ET le jar de l'app
 * installée) parce que ce n'était pas dans NON_CACHE_FILES — le dossier était
 * PARTAGÉ avec les propres fichiers de l'app. Un sous-dossier DÉDIÉ rend une
 * suppression accidentelle de fichiers étrangers au moteur impossible, quelle
 * que soit la liste blanche présente ou future.
 */
private fun engineDir(): File {
    val custom = app.lumen.domain.AppSettings.torrentCacheDir.value
    val dir = if (custom.isNotBlank()) File(custom) else {
        File(System.getProperty("user.home"), ".local/share/lumen/torrent-engine")
    }
    return dir.apply { mkdirs() }
}

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

/** Pousse le profil de cache choisi via l'API — PAS de CLI pour ça (voir plus bas). */
private fun applyCacheSettings() {
    val bytes = app.lumen.domain.AppSettings.torrentCacheGib.value.toLong() * 1024 * 1024 * 1024
    // « Mémoire vive » = rien sur le disque ; le profil « par défaut » écrit
    // sur le disque, comme un client torrent classique.
    val useDisk = app.lumen.domain.AppSettings.torrentProfile.value != "ram"
    httpPost(
        "${base()}/settings",
        """{"action":"set","sets":{"CacheSize":$bytes,"UseDisk":$useDisk}}""",
    )
}

/**
 * Démarre TorrServer si besoin : binaire téléchargé au premier usage dans
 * ~/.local/share/lumen, lancé en sidecar, prêt quand /echo répond.
 *
 * Cette version de TorrServer (MatriX) n'a NI --cache-size NI --ram-cache —
 * ce sont des options d'un autre fork/une autre version. Les passer fait
 * échouer le lancement avec « unknown argument » à CHAQUE tentative : c'est
 * la cause du statut « hors ligne » permanent. Le cache se règle après coup,
 * par l'API HTTP du moteur (POST /settings), la seule voie qu'il expose.
 */
actual suspend fun ensureTorrentEngine(): Boolean = withContext(Dispatchers.Default) {
    if (httpGet("${base()}/echo") != null) {
        if (!usesCustomServer()) applyCacheSettings()
        return@withContext true
    }
    // Un serveur choisi par l'utilisateur est SON affaire : s'il ne répond
    // pas, ce n'est pas à nous de télécharger un binaire pour le remplacer.
    if (usesCustomServer()) return@withContext false

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
        ProcessBuilder(binary.absolutePath, "--port", PORT.toString(), "--path", engineDir().absolutePath)
            .redirectOutput(File(engineDir(), "torrserver.log"))
            .redirectErrorStream(true)
            .start()
    }.getOrNull() ?: return@withContext false

    // Le moteur met quelques secondes à ouvrir son port.
    repeat(30) {
        if (httpGet("${base()}/echo") != null) {
            applyCacheSettings()
            return@withContext true
        }
        delay(500)
    }
    false
}

actual fun torrentStreamUrl(infoHash: String, title: String): String {
    val magnet = URLEncoder.encode("magnet:?xt=urn:btih:$infoHash", "UTF-8")
    return "${base()}/stream/video?link=$magnet&index=1&play"
}

actual suspend fun torrentStats(infoHash: String): TorrentStats? = withContext(Dispatchers.Default) {
    val body = httpPost("${base()}/torrents", """{"action":"get","hash":"${infoHash.lowercase()}"}""")
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

actual suspend fun torrentEngineStatus(): TorrentEngineStatus = withContext(Dispatchers.Default) {
    TorrentEngineStatus(
        running = httpGet("${base()}/echo", timeoutMs = 1200) != null,
        endpoint = base(),
        cacheBytes = cacheBytesIn(engineDir()),
        cacheDir = engineDir().absolutePath,
    )
}

/**
 * Vide le cache — via l'API du moteur, PAS en devinant des noms de dossiers.
 *
 * L'ancien code effaçait des dossiers « cache »/« torrents » qui n'existent
 * pas dans cette version de TorrServer : il ne libérait jamais rien. « wipe »
 * est l'action documentée par le moteur lui-même pour tout retirer (torrents
 * ET cache), sans exiger le hash qu'exigent « drop »/« rem ».
 */
actual suspend fun purgeTorrentCache(): Long = withContext(Dispatchers.Default) {
    val dir = engineDir()
    val before = cacheBytesIn(dir)
    httpPost("${base()}/torrents", """{"action":"wipe"}""")
    // Filet de sécurité si le moteur ne tournait pas : on efface nous-mêmes
    // tout ce qui n'est pas sa configuration.
    dir.listFiles()?.forEach { f ->
        if (f.name !in NON_CACHE_FILES) runCatching { f.deleteRecursively() }
    }
    val after = cacheBytesIn(dir)
    (before - after).coerceAtLeast(0L)
}

actual suspend fun restartTorrentEngine(): Boolean = withContext(Dispatchers.Default) {
    if (usesCustomServer()) return@withContext ensureTorrentEngine()
    // Le moteur relit sa configuration au démarrage : on le coupe proprement
    // puis ensureTorrentEngine() le relance avec les nouveaux réglages.
    httpGet("${base()}/shutdown", timeoutMs = 2000)
    runCatching { ProcessBuilder("pkill", "-f", "torrserver").start().waitFor() }
    delay(1200)
    ensureTorrentEngine()
}
