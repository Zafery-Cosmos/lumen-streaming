package app.lumen.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client Jellyfin minimal, écrit à la main (pas le SDK officiel — cf. plan §1).
 * Toutes les routes visent l'API actuelle de la 10.11, pas les routes dépréciées.
 */
class JellyfinClient(
    private val deviceId: String,
    private val deviceName: String,
    private val version: String = "0.1.0",
) {
    val http: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true   // l'API renvoie des dizaines de champs qu'on ne mappe pas
                explicitNulls = false
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 6_000
        }
    }

    /** Token de session — null tant qu'on n'est pas connecté. */
    var accessToken: String? = null

    /** En-tête d'identification Jellyfin, avec le token quand on l'a. */
    private fun authorizationHeader(): String {
        val base = "MediaBrowser Client=\"Lumen\", Device=\"$deviceName\", " +
            "DeviceId=\"$deviceId\", Version=\"$version\""
        return accessToken?.let { "$base, Token=\"$it\"" } ?: base
    }

    /** Valide qu'une URL est bien un serveur Jellyfin et renvoie ses infos publiques. */
    suspend fun publicInfo(baseUrl: String): PublicSystemInfo =
        http.get("${baseUrl.trimEnd('/')}/System/Info/Public") {
            header("Authorization", authorizationHeader())
        }.body()

    /** Liste des profils visibles publiquement (écran « qui regarde ? »). */
    suspend fun publicUsers(baseUrl: String): List<PublicUser> =
        http.get("${baseUrl.trimEnd('/')}/Users/Public") {
            header("Authorization", authorizationHeader())
        }.body()

    @Serializable
    private data class AuthByNameBody(val Username: String, val Pw: String)

    /** Connexion classique par identifiants. Mémorise le token en cas de succès. */
    suspend fun authenticateByName(baseUrl: String, username: String, password: String): AuthenticationResult {
        val result: AuthenticationResult = http.post("${baseUrl.trimEnd('/')}/Users/AuthenticateByName") {
            header("Authorization", authorizationHeader())
            contentType(ContentType.Application.Json)
            setBody(AuthByNameBody(username, password))
        }.body()
        accessToken = result.accessToken
        return result
    }

    /** Démarre un flux Quick Connect : renvoie le code à saisir dans le client web. */
    suspend fun quickConnectInitiate(baseUrl: String): QuickConnectState =
        http.post("${baseUrl.trimEnd('/')}/QuickConnect/Initiate") {
            header("Authorization", authorizationHeader())
        }.body()

    /** Interroge l'état d'une demande Quick Connect (à poller). */
    suspend fun quickConnectState(baseUrl: String, secret: String): QuickConnectState =
        http.get("${baseUrl.trimEnd('/')}/QuickConnect/Connect?Secret=$secret") {
            header("Authorization", authorizationHeader())
        }.body()

    @Serializable
    private data class QuickConnectAuthBody(val Secret: String)

    /** Échange un Quick Connect validé contre un vrai token de session. */
    suspend fun authenticateWithQuickConnect(baseUrl: String, secret: String): AuthenticationResult {
        val result: AuthenticationResult = http.post("${baseUrl.trimEnd('/')}/Users/AuthenticateWithQuickConnect") {
            header("Authorization", authorizationHeader())
            contentType(ContentType.Application.Json)
            setBody(QuickConnectAuthBody(secret))
        }.body()
        accessToken = result.accessToken
        return result
    }

    /** URL de l'image de profil d'un utilisateur, ou null s'il n'en a pas. */
    fun userImageUrl(baseUrl: String, userId: String, tag: String?): String? =
        tag?.let { "${baseUrl.trimEnd('/')}/UserImage?userId=$userId&tag=$it" }

    // --- Bibliothèques & items (L2) — routes actuelles de la 10.11 -----------

    /** Les vues (bibliothèques) de l'utilisateur : Films, Séries, etc. */
    suspend fun userViews(baseUrl: String, userId: String): ItemsResult =
        authedGet(baseUrl, "/UserViews?userId=$userId")

    /** Items en cours de lecture, pour la rangée « Reprendre ». */
    suspend fun resumeItems(baseUrl: String, userId: String, limit: Int = 12): ItemsResult =
        authedGet(
            baseUrl,
            "/UserItems/Resume?userId=$userId&limit=$limit&mediaTypes=Video" +
                "&fields=$DEFAULT_FIELDS",
        )

    /** « À suivre » : le prochain épisode de chaque série entamée. */
    suspend fun nextUp(baseUrl: String, userId: String, limit: Int = 12): ItemsResult =
        authedGet(baseUrl, "/Shows/NextUp?userId=$userId&limit=$limit&fields=$DEFAULT_FIELDS")

    /** Derniers ajouts d'une bibliothèque (le endpoint renvoie un tableau nu). */
    suspend fun latestItems(baseUrl: String, userId: String, parentId: String, limit: Int = 16): List<BaseItem> =
        http.get("${baseUrl.trimEnd('/')}/Items/Latest?userId=$userId&parentId=$parentId&limit=$limit&fields=$DEFAULT_FIELDS") {
            header("Authorization", authorizationHeader())
        }.body()

    /** Contenu d'une bibliothèque, paginé et trié. */
    suspend fun items(
        baseUrl: String,
        userId: String,
        parentId: String? = null,
        includeTypes: String? = null,
        sortBy: String = "SortName",
        limit: Int = 60,
        startIndex: Int = 0,
    ): ItemsResult = authedGet(
        baseUrl,
        buildString {
            append("/Items?userId=$userId&Recursive=true&limit=$limit&startIndex=$startIndex")
            append("&sortBy=$sortBy&fields=$DEFAULT_FIELDS")
            parentId?.let { append("&parentId=$it") }
            includeTypes?.let { append("&includeItemTypes=$it") }
        },
    )

    // --- Lecture (L4) --------------------------------------------------------

    /**
     * Négociation de lecture : le serveur décide Direct Play / Direct Stream /
     * Transcode. Le profil de capacités réel de l'appareil viendra au L5 ; sans
     * profil, le serveur privilégie le Direct Play — exactement ce qu'on veut.
     */
    suspend fun playbackInfo(
        baseUrl: String,
        userId: String,
        itemId: String,
        maxBitrate: Long? = null,
    ): PlaybackInfoResponse =
        http.post(
            "${baseUrl.trimEnd('/')}/Items/$itemId/PlaybackInfo?userId=$userId" +
                (maxBitrate?.let { "&maxStreamingBitrate=$it&enableDirectPlay=false&enableDirectStream=false" } ?: ""),
        ) {
            header("Authorization", authorizationHeader())
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.body()

    /** URL de lecture : flux direct si possible, sinon l'URL de transcodage HLS. */
    fun streamUrl(baseUrl: String, itemId: String, source: MediaSource): String {
        val b = baseUrl.trimEnd('/')
        return when {
            source.supportsDirectPlay || source.supportsDirectStream ->
                "$b/Videos/$itemId/stream?static=true&mediaSourceId=${source.id}&api_key=$accessToken"
            source.transcodingUrl != null -> "$b${source.transcodingUrl}"
            else -> "$b/Videos/$itemId/main.m3u8?mediaSourceId=${source.id}&api_key=$accessToken"
        }
    }

    @Serializable
    private data class ProgressBody(
        val ItemId: String,
        val PositionTicks: Long,
        val IsPaused: Boolean = false,
        val PlaySessionId: String? = null,
    )

    /** Signale le début de lecture — la session apparaît dans le tableau de bord. */
    suspend fun reportPlaybackStart(baseUrl: String, itemId: String, playSessionId: String?) {
        http.post("${baseUrl.trimEnd('/')}/Sessions/Playing") {
            header("Authorization", authorizationHeader())
            contentType(ContentType.Application.Json)
            setBody(ProgressBody(itemId, 0, PlaySessionId = playSessionId))
        }
    }

    /** Position courante (toutes les 10 s) — partage la reprise avec tous les clients. */
    suspend fun reportPlaybackProgress(
        baseUrl: String, itemId: String, positionTicks: Long, paused: Boolean, playSessionId: String?,
    ) {
        http.post("${baseUrl.trimEnd('/')}/Sessions/Playing/Progress") {
            header("Authorization", authorizationHeader())
            contentType(ContentType.Application.Json)
            setBody(ProgressBody(itemId, positionTicks, IsPaused = paused, PlaySessionId = playSessionId))
        }
    }

    /** Fin de lecture — fige la position de reprise côté serveur. */
    suspend fun reportPlaybackStopped(baseUrl: String, itemId: String, positionTicks: Long, playSessionId: String?) {
        http.post("${baseUrl.trimEnd('/')}/Sessions/Playing/Stopped") {
            header("Authorization", authorizationHeader())
            contentType(ContentType.Application.Json)
            setBody(ProgressBody(itemId, positionTicks, PlaySessionId = playSessionId))
        }
    }

    /** Détail complet d'un item (fiche film/série/épisode). */
    suspend fun item(baseUrl: String, userId: String, itemId: String): BaseItem =
        authedGet(baseUrl, "/Items/$itemId?userId=$userId&fields=$DEFAULT_FIELDS")

    /** Saisons d'une série. */
    suspend fun seasons(baseUrl: String, userId: String, seriesId: String): ItemsResult =
        authedGet(baseUrl, "/Shows/$seriesId/Seasons?userId=$userId")

    /** Épisodes d'une série — toute la série si seasonId est null. */
    suspend fun episodes(baseUrl: String, userId: String, seriesId: String, seasonId: String? = null): ItemsResult =
        authedGet(
            baseUrl,
            buildString {
                append("/Shows/$seriesId/Episodes?userId=$userId&fields=Overview,Path")
                seasonId?.let { append("&seasonId=$it") }
            },
        )

    /** Recherche dynamique — appelée à chaque frappe (débouncée côté UI). */
    suspend fun search(baseUrl: String, userId: String, term: String, limit: Int = 40): ItemsResult =
        authedGet(
            baseUrl,
            "/Items?userId=$userId&searchTerm=${term.encodeURLParameter()}" +
                "&Recursive=true&includeItemTypes=Movie,Series&limit=$limit&fields=$DEFAULT_FIELDS",
        )

    /** URL d'une image d'item (Primary, Backdrop, Logo, Thumb). */
    fun imageUrl(
        baseUrl: String,
        itemId: String,
        type: String = "Primary",
        tag: String? = null,
        maxWidth: Int? = null,
    ): String = buildString {
        append("${baseUrl.trimEnd('/')}/Items/$itemId/Images/$type")
        append("?quality=90")
        tag?.let { append("&tag=$it") }
        maxWidth?.let { append("&maxWidth=$it") }
    }

    private suspend inline fun <reified T> authedGet(baseUrl: String, path: String): T =
        http.get("${baseUrl.trimEnd('/')}$path") {
            header("Authorization", authorizationHeader())
        }.body()

    private companion object {
        /** Champs additionnels demandés partout — évite un second aller-retour par fiche. */
        const val DEFAULT_FIELDS = "ProviderIds,Overview,Genres,ParentId"
    }

    /** Vérifie qu'un token stocké est encore valable (reconnexion silencieuse). */
    suspend fun tokenIsValid(baseUrl: String): Boolean = try {
        http.get("${baseUrl.trimEnd('/')}/System/Info") {
            header("Authorization", authorizationHeader())
        }.status.isSuccess()
    } catch (_: Exception) {
        false
    }
}
