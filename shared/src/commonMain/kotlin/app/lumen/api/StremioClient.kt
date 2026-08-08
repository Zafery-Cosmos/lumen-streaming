package app.lumen.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StremioManifest(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("types") val types: List<String> = emptyList(),
    @SerialName("catalogs") val catalogs: List<StremioCatalogRef> = emptyList(),
)

@Serializable
data class StremioCatalogRef(
    @SerialName("type") val type: String = "",   // movie | series | tv | channel…
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
)

@Serializable
data class StremioCatalogResponse(
    @SerialName("metas") val metas: List<StremioMeta> = emptyList(),
)

@Serializable
data class StremioMeta(
    @SerialName("id") val id: String = "",       // souvent un IMDb « tt… »
    @SerialName("type") val type: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("poster") val poster: String? = null,
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

    /** Recherche dans un catalogue d'addon (la plupart la supportent). */
    suspend fun searchCatalog(manifestUrl: String, type: String, id: String, query: String): List<StremioMeta> {
        val base = normalizeManifestUrl(manifestUrl).removeSuffix("/manifest.json")
        val q = query.encodeURLParameter()
        return http.get("$base/catalog/$type/$id/search=$q.json").body<StremioCatalogResponse>().metas
    }

    /** Un catalogue d'addon (rangées de l'onglet Découvrir). */
    suspend fun catalog(manifestUrl: String, type: String, id: String): List<StremioMeta> {
        val base = normalizeManifestUrl(manifestUrl).removeSuffix("/manifest.json")
        return http.get("$base/catalog/$type/$id.json").body<StremioCatalogResponse>().metas
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
