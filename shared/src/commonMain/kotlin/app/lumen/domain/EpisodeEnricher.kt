package app.lumen.domain

import app.lumen.api.TmdbClient
import app.lumen.api.TmdbEpisode

/** Métadonnées TMDB venues combler les trous d'un épisode Jellyfin non scrapé. */
data class EpisodeExtra(
    val title: String?,
    val overview: String?,
    val stillUrl: String?,
    val runtimeMinutes: Int?,
)

/**
 * Enrichit les épisodes sans métadonnées (exigence utilisateur) : les fichiers
 * type « FL.2014.E53 » donnent un numéro ABSOLU côté Jellyfin. On le convertit
 * en vraie saison/épisode grâce aux comptes d'épisodes TMDB, puis on récupère
 * titre, résumé et vignette. Tout est mis en cache par série.
 */
class EpisodeEnricher(private val tmdb: TmdbClient) {

    /** (season_number → nombre d'épisodes), hors saison 0 (specials). */
    private var seasonCounts: List<Pair<Int, Int>>? = null
    private val seasonCache = mutableMapOf<Int, Map<Int, TmdbEpisode>>()
    private var tvId: Long? = null

    suspend fun forSeries(tmdbTvId: Long) {
        if (tvId == tmdbTvId) return
        tvId = tmdbTvId
        seasonCache.clear()
        seasonCounts = runCatching {
            tmdb.detail("tv", tmdbTvId).seasons
                .filter { it.seasonNumber > 0 }
                .sortedBy { it.seasonNumber }
                .map { it.seasonNumber to it.episodeCount }
        }.getOrNull()
    }

    /**
     * Cherche l'épisode TMDB correspondant : d'abord tel quel (S/E déclarés par
     * Jellyfin), sinon en interprétant le numéro comme un numéro absolu.
     */
    suspend fun enrich(seasonNumber: Int?, episodeNumber: Int?): EpisodeExtra? {
        val id = tvId ?: return null
        val counts = seasonCounts ?: return null
        val ep = episodeNumber ?: return null

        // 1) Correspondance directe S/E, si plausible.
        if (seasonNumber != null && seasonNumber > 0) {
            val count = counts.firstOrNull { it.first == seasonNumber }?.second ?: 0
            if (ep <= count) {
                episodeOf(id, seasonNumber, ep)?.let { return it.toExtra() }
            }
        }

        // 2) Numéro absolu → on avance saison par saison.
        var remaining = ep
        for ((season, count) in counts) {
            if (remaining <= count) {
                return episodeOf(id, season, remaining)?.toExtra()
            }
            remaining -= count
        }
        return null
    }

    private suspend fun episodeOf(id: Long, season: Int, episode: Int): TmdbEpisode? {
        val map = seasonCache.getOrPut(season) {
            runCatching {
                tmdb.season(id, season).episodes.associateBy { it.episodeNumber }
            }.getOrDefault(emptyMap())
        }
        return map[episode]
    }

    private fun TmdbEpisode.toExtra() = EpisodeExtra(
        title = name?.takeIf { it.isNotBlank() },
        overview = overview?.takeIf { it.isNotBlank() },
        stillUrl = TmdbClient.stillUrl(stillPath),
        runtimeMinutes = runtime,
    )
}
