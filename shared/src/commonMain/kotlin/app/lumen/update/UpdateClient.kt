package app.lumen.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** La version actuellement installée — comparée à celle publiée sur le NAS. */
// À BUMPER À CHAQUE PUBLICATION (publish.sh) : c'est la version que l'app
// compare au manifeste — si elle reste en arrière, l'app se repropose sa
// propre version en boucle après chaque mise à jour.
const val LUMEN_VERSION = "1.10.3"

/** Adresse du serveur de mises à jour (NAS). */
const val UPDATE_SERVER = "http://192.168.1.170:8500"

@Serializable
data class ReleaseManifest(
    @SerialName("version") val version: String = "0.0.0",
    @SerialName("notes") val notes: List<String> = emptyList(),
    @SerialName("platforms") val platforms: Map<String, ReleaseArtifact> = emptyMap(),
    @SerialName("published_at") val publishedAt: Long = 0,
) {
    /** L'artefact correspondant à CETTE plateforme, ou null s'il n'y en a pas. */
    val artifact: ReleaseArtifact? get() = platforms[updatePlatformKey]
}

@Serializable
data class ReleaseArtifact(
    @SerialName("file") val file: String = "",
    @SerialName("size") val size: Long = 0,
    @SerialName("sha256") val sha256: String = "",
)

/** Avancement d'un téléchargement — débit et temps restant calculés en direct. */
data class DownloadState(
    val downloaded: Long,
    val total: Long,
    val bytesPerSecond: Long,
) {
    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total) else 0f
    /** Secondes restantes estimées, null tant que le débit n'est pas mesurable. */
    val etaSeconds: Long?
        get() = if (bytesPerSecond > 0 && total > downloaded) {
            (total - downloaded) / bytesPerSecond
        } else null
}

/**
 * Client de mise à jour : lit le manifeste, écoute les publications EN DIRECT
 * (SSE — pas besoin de redémarrer l'app pour voir une nouvelle version), et
 * télécharge l'artefact en rapportant débit et temps restant.
 */
class UpdateClient(
    private val http: HttpClient,
    private val baseUrl: String = UPDATE_SERVER,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): ReleaseManifest? = runCatching {
        http.get("$baseUrl/api/version").body<ReleaseManifest>()
    }.getOrNull()

    /**
     * Flux des publications : le serveur pousse dès qu'une version sort.
     * Chaque élément est le manifeste fraîchement publié.
     *
     * La connexion se rétablit TOUTE SEULE : le client HTTP partagé coupe
     * chaque requête après 15 s, ce qui tuait l'abonnement en silence — le
     * « en direct » ne tenait que 15 secondes. On désactive donc le délai
     * pour cette requête-ci, et on se réabonne en boucle si le serveur
     * ferme ou devient injoignable.
     */
    fun events(): Flow<ReleaseManifest> = flow {
        while (true) {
            runCatching { listenOnce() }
            // Serveur redémarré ou réseau coupé : on retente sans harceler.
            kotlinx.coroutines.delay(15_000)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ReleaseManifest>.listenOnce() {
        http.prepareGet("$baseUrl/api/events") {
            // Un flux SSE est infini par nature : aucun délai ne doit le couper.
            timeout { requestTimeoutMillis = Long.MAX_VALUE }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            var event = ""
            while (true) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val payload = line.removePrefix("data:").trim()
                        // « hello » = état à la connexion, « update » = publication.
                        if (event == "update" || event == "hello") {
                            runCatching { json.decodeFromString<ReleaseManifest>(payload) }
                                .getOrNull()?.let { emit(it) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Télécharge l'artefact et renvoie le chemin local, ou null en cas d'échec.
     * [onProgress] est appelé en continu avec le débit et le temps restant.
     *
     * Un accroc Wi-Fi sur 28 Mo ne devrait pas coûter les 28 Mo : le serveur
     * sait déjà reprendre un téléchargement (Range), donc au lieu de tout
     * relire d'un bloc via `.body()`, on lit par blocs dans un buffer
     * préalloué, et une coupure reprend là où elle s'est arrêtée plutôt que
     * de tout recommencer — jusqu'à 3 essais avant d'abandonner pour de bon.
     */
    suspend fun download(
        artifact: ReleaseArtifact,
        onProgress: (DownloadState) -> Unit,
    ): String? {
        val buffer = ByteArray(artifact.size.toInt())
        var written = 0
        var startedAt = 0L
        repeat(4) { attempt ->
            val ok = runCatching {
                http.prepareGet("$baseUrl/files/${artifact.file}") {
                    // 100 Mo ne passent pas toujours en 15 s : le délai global
                    // du client aurait interrompu le téléchargement en vol.
                    timeout { requestTimeoutMillis = 30 * 60_000 }
                    if (written > 0) header("Range", "bytes=$written-")
                }.execute { response ->
                    if (startedAt == 0L) startedAt = nowMillis()
                    val channel = response.bodyAsChannel()
                    while (written < buffer.size) {
                        val read = channel.readAvailable(buffer, written, buffer.size - written)
                        if (read <= 0) break
                        written += read
                        val elapsed = (nowMillis() - startedAt).coerceAtLeast(1)
                        onProgress(
                            DownloadState(
                                downloaded = written.toLong(),
                                total = artifact.size,
                                bytesPerSecond = written * 1000L / elapsed,
                            ),
                        )
                    }
                }
            }.isSuccess
            if (ok && written >= buffer.size) return saveUpdateFile(artifact.file, buffer)
            // On retente à partir de ce qui a déjà été reçu — pas depuis zéro.
            if (attempt < 3) kotlinx.coroutines.delay(2_000L * (attempt + 1))
        }
        return null
    }
}

/** Compare deux versions « x.y.z » — true si [candidate] est plus récente. */
fun isNewerVersion(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.trim().split('.', '-').mapNotNull { it.toIntOrNull() }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Formate un poids en Mo/Go, pour l'afficher AVANT de télécharger. */
fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${(bytes / 100_000_000) / 10.0} Go"
    bytes >= 1_000_000 -> "${bytes / 1_000_000} Mo"
    bytes >= 1_000 -> "${bytes / 1_000} Ko"
    else -> "$bytes o"
}

/** Formate une durée restante en « 1 min 20 s ». */
fun formatEta(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    seconds >= 60 -> "${seconds / 60} min ${seconds % 60} s"
    else -> "$seconds s"
}

// --- Plateforme ------------------------------------------------------------

/** « linux », « windows », « macos » ou « android ». */
expect val updatePlatformKey: String

/** Écrit l'artefact téléchargé sur le disque ; renvoie son chemin. */
expect fun saveUpdateFile(fileName: String, bytes: ByteArray): String?

/** Installe la mise à jour (remplace et relance, ou lance l'installeur). */
expect fun applyUpdate(path: String): Boolean

/** Horloge monotone en millisecondes (pour le calcul de débit). */
expect fun nowMillis(): Long
