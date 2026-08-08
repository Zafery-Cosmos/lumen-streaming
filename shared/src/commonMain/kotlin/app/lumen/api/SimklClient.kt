package app.lumen.api

import app.lumen.config.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimklPin(
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_url") val verificationUrl: String = "",
    @SerialName("expires_in") val expiresIn: Int = 900,
    @SerialName("interval") val interval: Int = 5,
)

@Serializable
data class SimklPinResult(
    @SerialName("result") val result: String = "",
    @SerialName("access_token") val accessToken: String? = null,
)

@Serializable
data class SimklUser(
    @SerialName("user") val user: SimklUserInfo? = null,
)

@Serializable
data class SimklUserInfo(
    @SerialName("name") val name: String = "",
)

// --- Corps d'envoi de l'historique -----------------------------------------

@Serializable
private data class SimklIds(@SerialName("imdb") val imdb: String)

@Serializable
private data class SimklEpisode(@SerialName("number") val number: Int)

@Serializable
private data class SimklSeason(
    @SerialName("number") val number: Int,
    @SerialName("episodes") val episodes: List<SimklEpisode>,
)

@Serializable
private data class SimklShow(
    @SerialName("ids") val ids: SimklIds,
    @SerialName("seasons") val seasons: List<SimklSeason>,
)

@Serializable
private data class SimklMovie(@SerialName("ids") val ids: SimklIds)

@Serializable
private data class SimklHistoryBody(
    @SerialName("movies") val movies: List<SimklMovie> = emptyList(),
    @SerialName("shows") val shows: List<SimklShow> = emptyList(),
)

/**
 * Client Simkl : suit ce qui est regardé, y compris **hors médiathèque**.
 * C'est le trou que Jellyfin ne peut pas combler — il ignore tout ce qui est
 * lu via les addons Stremio.
 *
 * L'authentification passe par un code PIN (même principe que Quick Connect) :
 * pas de navigateur intégré, pas de secret client embarqué dans l'app.
 */
class SimklClient(private val http: HttpClient) {

    /** Demande un code à saisir sur simkl.com/pin. */
    suspend fun requestPin(): SimklPin? = runCatching {
        http.get("$BASE/oauth/pin?client_id=${Secrets.SIMKL_CLIENT_ID}").body<SimklPin>()
    }.getOrNull()

    /** Interroge l'état du code ; renvoie le jeton dès que l'utilisateur a validé. */
    suspend fun pollPin(userCode: String): String? = runCatching {
        http.get("$BASE/oauth/pin/$userCode?client_id=${Secrets.SIMKL_CLIENT_ID}")
            .body<SimklPinResult>()
            .takeIf { it.result == "OK" }
            ?.accessToken
    }.getOrNull()

    suspend fun userName(token: String): String? = runCatching {
        http.post("$BASE/users/settings") { auth(token) }.body<SimklUser>().user?.name
    }.getOrNull()

    /** Marque un FILM comme vu (identifié par son IMDb). */
    suspend fun markMovieWatched(token: String, imdbId: String): Boolean = runCatching {
        http.post("$BASE/sync/history") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(SimklHistoryBody(movies = listOf(SimklMovie(SimklIds(imdbId)))))
        }.status.value in 200..299
    }.getOrDefault(false)

    /** Marque un ÉPISODE comme vu (IMDb de la série + saison/épisode réels). */
    suspend fun markEpisodeWatched(
        token: String,
        seriesImdbId: String,
        season: Int,
        episode: Int,
    ): Boolean = runCatching {
        http.post("$BASE/sync/history") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(
                SimklHistoryBody(
                    shows = listOf(
                        SimklShow(
                            ids = SimklIds(seriesImdbId),
                            seasons = listOf(SimklSeason(season, listOf(SimklEpisode(episode)))),
                        ),
                    ),
                ),
            )
        }.status.value in 200..299
    }.getOrDefault(false)

    private fun io.ktor.client.request.HttpRequestBuilder.auth(token: String) {
        header("Authorization", "Bearer $token")
        header("simkl-api-key", Secrets.SIMKL_CLIENT_ID)
    }

    companion object {
        private const val BASE = "https://api.simkl.com"
        const val PIN_PAGE = "https://simkl.com/pin"
    }
}
