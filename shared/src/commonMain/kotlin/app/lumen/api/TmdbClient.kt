package app.lumen.api

import app.lumen.config.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbPaged(
    @SerialName("results") val results: List<TmdbItem> = emptyList(),
)

@Serializable
data class TmdbItem(
    @SerialName("id") val id: Long = 0,
    @SerialName("title") val title: String? = null,          // films
    @SerialName("name") val nameField: String? = null,       // séries
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("media_type") val mediaType: String? = null, // movie / tv (trending)
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
) {
    val displayName: String get() = title ?: nameField ?: ""
    val year: Int? get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
}

/**
 * Client TMDB (plan §5 ter) : rangées éditoriales de l'accueil — tendances de la
 * semaine et catalogues par genre — en français.
 */
class TmdbClient(private val http: HttpClient) {

    /** Top de la semaine, films et séries mélangés — la rangée « Top 10 ». */
    suspend fun trendingWeek(): List<TmdbItem> =
        get("trending/all/week").results.filter { it.mediaType == "movie" || it.mediaType == "tv" }

    /** Films populaires d'un genre (rangées Action, Comédie, etc.). */
    suspend fun moviesByGenre(genreId: Int): List<TmdbItem> =
        get("discover/movie", "&with_genres=$genreId&sort_by=popularity.desc").results

    /** Nouveautés cinéma du moment. */
    suspend fun nowPlaying(): List<TmdbItem> = get("movie/now_playing").results

    private suspend fun get(path: String, extra: String = ""): TmdbPaged =
        http.get("$BASE/$path?api_key=${Secrets.TMDB_API_KEY}&language=fr-FR&region=FR$extra").body()

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"

        /** Genres TMDB affichés sur l'accueil, dans cet ordre. */
        val HOME_GENRES = listOf(
            28 to "Action",
            35 to "Comédie",
            878 to "Science-Fiction",
            27 to "Horreur",
            16 to "Animation",
            53 to "Thriller",
        )

        fun posterUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w342$it" }
        fun backdropUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
    }
}
