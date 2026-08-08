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

    /** Vérifie qu'un token stocké est encore valable (reconnexion silencieuse). */
    suspend fun tokenIsValid(baseUrl: String): Boolean = try {
        http.get("${baseUrl.trimEnd('/')}/System/Info") {
            header("Authorization", authorizationHeader())
        }.status.isSuccess()
    } catch (_: Exception) {
        false
    }
}
