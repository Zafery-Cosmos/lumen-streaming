package app.lumen.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Modèles ----------------------------------------------------------------

/** Le code à saisir sur plex.tv/link, et l'identifiant pour venir le relire. */
@Serializable
data class PlexPin(
    @SerialName("id") val id: Long = 0,
    @SerialName("code") val code: String = "",
    @SerialName("authToken") val authToken: String? = null,
)

/** Un serveur (ou un client) rattaché au compte. */
@Serializable
data class PlexResource(
    @SerialName("name") val name: String = "",
    @SerialName("clientIdentifier") val clientIdentifier: String = "",
    @SerialName("provides") val provides: String = "",
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("owned") val owned: Boolean = false,
    @SerialName("connections") val connections: List<PlexConnection> = emptyList(),
) {
    val isServer: Boolean get() = provides.split(",").any { it.trim() == "server" }
}

@Serializable
data class PlexConnection(
    @SerialName("uri") val uri: String = "",
    @SerialName("address") val address: String = "",
    @SerialName("local") val local: Boolean = false,
    @SerialName("relay") val relay: Boolean = false,
)

/** Une bibliothèque du serveur (Films, Séries…). */
@Serializable
data class PlexSection(
    @SerialName("key") val key: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("type") val type: String = "",   // movie | show | artist | photo
)

@Serializable
internal data class PlexSectionsResponse(
    @SerialName("MediaContainer") val container: PlexSectionsContainer = PlexSectionsContainer(),
)

@Serializable
internal data class PlexSectionsContainer(
    @SerialName("Directory") val directory: List<PlexSection> = emptyList(),
)

/** Un titre d'une bibliothèque. */
@Serializable
data class PlexItem(
    @SerialName("ratingKey") val ratingKey: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("year") val year: Int? = null,
    @SerialName("type") val type: String = "",
    @SerialName("thumb") val thumb: String? = null,
    @SerialName("art") val art: String? = null,
    @SerialName("summary") val summary: String? = null,
    @SerialName("Media") val media: List<PlexMedia> = emptyList(),
) {
    /** Le premier fichier lisible, s'il y en a un. */
    val firstPartKey: String? get() = media.firstOrNull()?.parts?.firstOrNull()?.key
}

@Serializable
data class PlexMedia(
    @SerialName("Part") val parts: List<PlexPart> = emptyList(),
)

@Serializable
data class PlexPart(
    @SerialName("key") val key: String = "",
    @SerialName("file") val file: String? = null,
)

@Serializable
internal data class PlexItemsResponse(
    @SerialName("MediaContainer") val container: PlexItemsContainer = PlexItemsContainer(),
)

@Serializable
internal data class PlexItemsContainer(
    @SerialName("Metadata") val metadata: List<PlexItem> = emptyList(),
)

/**
 * Client Plex.
 *
 * Aucune clé d'API à demander à Plex, et aucun mot de passe à taper dans
 * Lumen : on ouvre un « PIN », l'utilisateur valide le code à quatre
 * caractères sur plex.tv/link, et on récupère un jeton lié à SON compte.
 * C'est le mécanisme prévu pour les appareils (téléviseurs, consoles).
 *
 * L'identifiant client est propre à cette installation : Plex s'en sert pour
 * reconnaître l'appareil et lister les autorisations dans le compte.
 */
class PlexClient(
    private val http: HttpClient,
    private val clientId: String,
    private val product: String = "Lumen",
) {
    private fun io.ktor.client.request.HttpRequestBuilder.plexHeaders(token: String? = null) {
        header("Accept", "application/json")
        header("X-Plex-Product", product)
        header("X-Plex-Version", "1.0")
        header("X-Plex-Client-Identifier", clientId)
        header("X-Plex-Platform", "Kotlin")
        header("X-Plex-Device-Name", product)
        token?.let { header("X-Plex-Token", it) }
    }

    /** Ouvre un code à saisir sur plex.tv/link. */
    suspend fun createPin(): PlexPin =
        http.post("$PLEX_TV/api/v2/pins") { plexHeaders() }.body()

    /**
     * Relit le PIN : `authToken` reste null tant que l'utilisateur n'a pas
     * validé le code. À interroger périodiquement, sans harceler.
     */
    suspend fun pollPin(id: Long): PlexPin =
        http.get("$PLEX_TV/api/v2/pins/$id") { plexHeaders() }.body()

    /** Les serveurs du compte, avec leurs adresses possibles. */
    suspend fun servers(token: String): List<PlexResource> =
        http.get("$PLEX_TV/api/v2/resources?includeHttps=1&includeRelay=1") {
            plexHeaders(token)
        }.body<List<PlexResource>>().filter { it.isServer }

    /**
     * L'adresse à utiliser pour joindre ce serveur.
     *
     * On préfère une adresse locale : elle est directe, rapide, et ne fait pas
     * transiter la vidéo par les relais de Plex (limités en débit). Le relais
     * ne sert que de dernier recours.
     */
    fun bestConnection(resource: PlexResource): String? =
        resource.connections.sortedBy { c ->
            when {
                c.local && !c.relay -> 0
                !c.relay -> 1
                else -> 2
            }
        }.firstOrNull()?.uri

    /** Les bibliothèques du serveur. */
    suspend fun sections(baseUrl: String, token: String): List<PlexSection> =
        http.get("${baseUrl.trimEnd('/')}/library/sections") { plexHeaders(token) }
            .body<PlexSectionsResponse>().container.directory

    /** Le contenu d'une bibliothèque. */
    suspend fun sectionItems(baseUrl: String, token: String, sectionKey: String): List<PlexItem> =
        http.get("${baseUrl.trimEnd('/')}/library/sections/$sectionKey/all") { plexHeaders(token) }
            .body<PlexItemsResponse>().container.metadata

    /**
     * URL d'une image du serveur. Le jeton voyage dans l'URL : les chargeurs
     * d'images ne savent pas poser d'en-tête personnalisé.
     */
    fun imageUrl(baseUrl: String, path: String?, token: String): String? {
        if (path.isNullOrBlank()) return null
        return "${baseUrl.trimEnd('/')}$path?X-Plex-Token=${token.encodeURLParameter()}"
    }

    /**
     * URL de lecture directe du fichier — pas de transcodage : Plex sert
     * l'original, et le lecteur de Lumen s'en débrouille comme pour toute
     * autre source.
     */
    fun streamUrl(baseUrl: String, partKey: String, token: String): String =
        "${baseUrl.trimEnd('/')}$partKey?X-Plex-Token=${token.encodeURLParameter()}"

    companion object {
        private const val PLEX_TV = "https://plex.tv"

        /** La page où saisir le code affiché. */
        const val LINK_PAGE = "https://plex.tv/link"
    }
}
