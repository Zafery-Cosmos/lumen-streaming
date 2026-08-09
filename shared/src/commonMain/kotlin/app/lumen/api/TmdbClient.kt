package app.lumen.api

import app.lumen.config.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLParameter
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

@Serializable
data class TmdbGenre(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
)

@Serializable
data class TmdbDetail(
    @SerialName("id") val id: Long = 0,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val nameField: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("genres") val genres: List<TmdbGenre> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("seasons") val seasons: List<TmdbSeasonInfo> = emptyList(),
    @SerialName("credits") val credits: TmdbCredits? = null,
    @SerialName("similar") val similar: TmdbPaged? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
    @SerialName("images") val images: TmdbImages? = null,
) {
    val displayName: String get() = title ?: nameField ?: ""
    val year: Int? get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()

    /**
     * Le logo du titre, à afficher À LA PLACE du texte. On préfère le
     * français, puis l'anglais, puis une version sans langue ; à qualité
     * égale, le mieux noté. Null = pas de logo, on retombe sur le texte.
     */
    val logoUrl: String? get() = images?.logos.orEmpty()
        .filter { it.filePath != null }
        .minByOrNull { logo ->
            val langRank = when (logo.language) {
                "fr" -> 0
                "en" -> 1
                null -> 2
                else -> 3
            }
            langRank * 1000 - (logo.voteAverage ?: 0.0).toInt()
        }
        ?.let { TmdbClient.logoUrl(it.filePath) }
}

@Serializable
data class TmdbImages(
    @SerialName("logos") val logos: List<TmdbImage> = emptyList(),
)

@Serializable
data class TmdbImage(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
data class TmdbPersonSearch(
    @SerialName("results") val results: List<TmdbPersonRef> = emptyList(),
)

@Serializable
data class TmdbPersonRef(
    @SerialName("id") val id: Long = 0,
    @SerialName("name") val name: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class TmdbPersonDetail(
    @SerialName("id") val id: Long = 0,
    @SerialName("name") val name: String = "",
    @SerialName("biography") val biography: String? = null,
    @SerialName("birthday") val birthday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    @SerialName("combined_credits") val combinedCredits: TmdbPersonCredits? = null,
)

@Serializable
data class TmdbPersonCredits(
    @SerialName("cast") val cast: List<TmdbItem> = emptyList(),
)

@Serializable
data class TmdbFindResponse(
    @SerialName("movie_results") val movieResults: List<TmdbItem> = emptyList(),
    @SerialName("tv_results") val tvResults: List<TmdbItem> = emptyList(),
)

@Serializable
data class TmdbExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
data class TmdbCredits(
    @SerialName("cast") val cast: List<TmdbCastMember> = emptyList(),
    @SerialName("crew") val crew: List<TmdbCrewMember> = emptyList(),
)

@Serializable
data class TmdbCastMember(
    @SerialName("name") val name: String = "",
    @SerialName("character") val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class TmdbCrewMember(
    @SerialName("name") val name: String = "",
    @SerialName("job") val job: String? = null,
)

@Serializable
data class TmdbSeasonInfo(
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_count") val episodeCount: Int = 0,
)

@Serializable
data class TmdbSeason(
    @SerialName("episodes") val episodes: List<TmdbEpisode> = emptyList(),
)

@Serializable
data class TmdbEpisode(
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
)

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

    /** Une rangée séries par chemin TMDB (tv/popular, trending/tv/week…). */
    suspend fun tvRow(path: String): List<TmdbItem> =
        get(path).results.map { it.copy(mediaType = it.mediaType ?: "tv") }

    /** Recherche films + séries, pour le rapprochement d'un dossier importé. */
    suspend fun searchMulti(query: String, year: Int?): List<TmdbItem> {
        if (query.isBlank()) return emptyList()
        val extra = year?.let { "&year=$it" }.orEmpty()
        return get("search/multi", "&query=${query.encodeURLParameter()}$extra")
            .results
            .filter { it.mediaType == null || it.mediaType == "movie" || it.mediaType == "tv" }
            .take(12)
    }

    /** Nouveautés cinéma du moment. */
    suspend fun nowPlaying(): List<TmdbItem> = get("movie/now_playing").results

    /**
     * Fiche détaillée d'un film ou d'une série TMDB, avec casting, similaires
     * ET logos du titre. `include_image_language` est indispensable : sans lui
     * l'API ne renvoie que les images de la langue demandée, donc presque
     * aucun logo. On demande le français, l'anglais, puis les logos sans
     * langue (typographies internationales).
     */
    suspend fun detail(mediaType: String, id: Long): TmdbDetail =
        http.get(
            "$BASE/$mediaType/$id?api_key=${Secrets.TMDB_API_KEY}&language=fr-FR" +
                "&append_to_response=credits,similar,external_ids,images" +
                "&include_image_language=fr,en,null",
        ).body()

    /** Recherche d'une personne par nom (pont depuis les fiches Jellyfin). */
    suspend fun searchPerson(name: String): TmdbPersonSearch =
        http.get(
            "$BASE/search/person?api_key=${Secrets.TMDB_API_KEY}&language=fr-FR" +
                "&query=${name.encodeURLParameter()}",
        ).body()

    /** Fiche d'une personne : bio + toute sa filmographie. */
    suspend fun person(id: Long): TmdbPersonDetail =
        http.get(
            "$BASE/person/$id?api_key=${Secrets.TMDB_API_KEY}&language=fr-FR" +
                "&append_to_response=combined_credits",
        ).body()

    /** Retrouve un titre TMDB depuis un identifiant IMDb (pont Stremio → fiche). */
    suspend fun findByImdb(imdbId: String): Pair<String, Long>? {
        val found: TmdbFindResponse = http.get(
            "$BASE/find/$imdbId?api_key=${Secrets.TMDB_API_KEY}&language=fr-FR&external_source=imdb_id",
        ).body()
        found.movieResults.firstOrNull()?.let { return "movie" to it.id }
        found.tvResults.firstOrNull()?.let { return "tv" to it.id }
        return null
    }

    /** Une saison complète, avec tous ses épisodes (titres, résumés, vignettes). */
    suspend fun season(tvId: Long, seasonNumber: Int): TmdbSeason =
        http.get("$BASE/tv/$tvId/season/$seasonNumber?api_key=${Secrets.TMDB_API_KEY}&language=fr-FR").body()

    private suspend fun get(path: String, extra: String = ""): TmdbPaged =
        http.get("$BASE/$path?api_key=${Secrets.TMDB_API_KEY}&language=fr-FR&region=FR$extra").body()

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"

        /** Rangées éditoriales de la page Séries. */
        val TV_ROWS = listOf(
            "tv/popular" to "Séries populaires",
            "tv/top_rated" to "Séries les mieux notées",
            "tv/on_the_air" to "En cours de diffusion",
            "trending/tv/week" to "Tendances de la semaine",
        )

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
        fun stillUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w400$it" }
        fun profileUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w185$it" }
        /** Logo du titre : PNG transparent, à poser sur le visuel. */
        fun logoUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w500$it" }
    }
}
