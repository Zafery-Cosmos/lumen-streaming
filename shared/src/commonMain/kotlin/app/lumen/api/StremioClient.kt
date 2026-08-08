package app.lumen.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StremioManifest(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("types") val types: List<String> = emptyList(),
)

@Serializable
data class StremioStreamsResponse(
    @SerialName("streams") val streams: List<StremioStream> = emptyList(),
)

@Serializable
data class StremioStream(
    @SerialName("name") val name: String? = null,       // ex. « Torrentio 1080p »
    @SerialName("title") val title: String? = null,     // description multi-lignes
    @SerialName("description") val description: String? = null,
    @SerialName("url") val url: String? = null,         // flux direct — lisible
    @SerialName("infoHash") val infoHash: String? = null, // torrent — debrid requis
    @SerialName("behaviorHints") val behaviorHints: StremioBehaviorHints? = null,
) {
    val label: String get() = (title ?: description ?: name ?: "Flux").replace("\n", " · ")
    val playable: Boolean get() = url != null
    /** En-têtes exigés par la source (Referer/User-Agent…) — plan §4/§5. */
    val requestHeaders: Map<String, String>
        get() = behaviorHints?.proxyHeaders?.request.orEmpty()
}

@Serializable
data class StremioBehaviorHints(
    @SerialName("notWebReady") val notWebReady: Boolean = false,
    @SerialName("proxyHeaders") val proxyHeaders: StremioProxyHeaders? = null,
)

@Serializable
data class StremioProxyHeaders(
    @SerialName("request") val request: Map<String, String> = emptyMap(),
)

/**
 * Client du protocole d'addon Stremio (plan §5) : n'importe quel addon existant
 * (Torrentio, Frenchio…) fonctionne — un manifeste + des routes JSON simples.
 */
class StremioClient(private val http: HttpClient) {

    suspend fun manifest(manifestUrl: String): StremioManifest =
        http.get(normalizeManifestUrl(manifestUrl)).body()

    /**
     * Les flux d'un titre. `type` : movie | series.
     * `mediaId` : « tt1375666 » pour un film, « tt0903747:2:5 » pour S02E05.
     */
    suspend fun streams(manifestUrl: String, type: String, mediaId: String): List<StremioStream> {
        val base = normalizeManifestUrl(manifestUrl).removeSuffix("/manifest.json")
        return http.get("$base/stream/$type/$mediaId.json").body<StremioStreamsResponse>().streams
    }

    companion object {
        fun normalizeManifestUrl(input: String): String {
            var url = input.trim()
            // Les liens « stremio:// » des pages d'installation sont équivalents.
            if (url.startsWith("stremio://")) url = "https://" + url.removePrefix("stremio://")
            if (!url.endsWith("/manifest.json")) url = url.trimEnd('/') + "/manifest.json"
            return url
        }
    }
}
