package app.lumen.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** La version actuellement installée — comparée à celle publiée sur le NAS. */
const val LUMEN_VERSION = "0.1.0"

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
     */
    fun events(): Flow<ReleaseManifest> = flow {
        http.prepareGet("$baseUrl/api/events").execute { response ->
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
     */
    suspend fun download(
        artifact: ReleaseArtifact,
        onProgress: (DownloadState) -> Unit,
    ): String? = runCatching {
        var startedAt = 0L
        val bytes: ByteArray = http.get("$baseUrl/files/${artifact.file}") {
            onDownload { received, contentLength ->
                val now = nowMillis()
                if (startedAt == 0L) startedAt = now
                val elapsed = (now - startedAt).coerceAtLeast(1)
                onProgress(
                    DownloadState(
                        downloaded = received,
                        total = contentLength ?: artifact.size,
                        bytesPerSecond = received * 1000 / elapsed,
                    ),
                )
            }
        }.body()
        saveUpdateFile(artifact.file, bytes)
    }.getOrNull()
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
