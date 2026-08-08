package app.lumen.domain

import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.api.TmdbClient
import app.lumen.auth.StoredSession

/** Un épisode replacé à sa vraie position (via le nom de fichier, puis TMDB). */
data class OrganizedEpisode(
    val ep: BaseItem,
    val season: Int,
    val number: Int,
    val extra: EpisodeExtra?,
)

/**
 * Réorganisation d'une série : les serveurs rangent souvent mal les fichiers
 * (numéros absolus entassés en « Saison 1 »). On se fie d'abord au NOM DE
 * FICHIER, puis à TMDB pour convertir un numéro absolu en vraie saison.
 *
 * Partagé par la fiche série et le panneau épisodes du lecteur.
 */
suspend fun organizeSeries(
    client: JellyfinClient,
    tmdb: TmdbClient,
    session: StoredSession,
    series: BaseItem,
): Map<Int, List<OrganizedEpisode>> {
    val episodes = runCatching {
        client.episodes(session.baseUrl, session.userId, series.id).items
    }.getOrDefault(emptyList())
    if (episodes.isEmpty()) return emptyMap()

    val tmdbId = series.providerIds["Tmdb"]?.toLongOrNull()
    val enricher = EpisodeEnricher(tmdb)
    if (tmdbId != null) enricher.forSeries(tmdbId)

    return episodes.map { ep ->
        // 1) Le nom de fichier dit la vérité là où les IndexNumber sont faux.
        val parsed = parseEpisodeFileName(ep.path)
        if (parsed != null) {
            val resolved = if (tmdbId != null) enricher.resolve(parsed.season, parsed.episode) else null
            OrganizedEpisode(
                ep = ep,
                season = parsed.season,
                number = parsed.episode,
                extra = resolved?.extra ?: parsed.title?.let { EpisodeExtra(it, null, null, null) },
            )
        } else {
            // 2) Sinon : S/E déclarés, réinterprétés en absolu si besoin.
            val resolved = if (tmdbId != null) enricher.resolve(ep.parentIndexNumber, ep.indexNumber) else null
            OrganizedEpisode(
                ep = ep,
                season = resolved?.season ?: ep.parentIndexNumber ?: 0,
                number = resolved?.episode ?: ep.indexNumber ?: 0,
                extra = resolved?.extra,
            )
        }
    }
        .groupBy { it.season }
        .mapValues { (_, list) -> list.sortedBy { it.number } }
        .toList()
        .sortedBy { it.first }
        .toMap(LinkedHashMap())
}

/** Libellé d'un épisode : vrai titre TMDB en priorité, sinon nom du serveur. */
fun OrganizedEpisode.label(): String {
    val n = number.takeIf { it > 0 }
    return when {
        extra?.title != null -> if (n != null) "$n. ${extra.title}" else extra.title
        n == null -> ep.name
        Regex("^[ÉE]pisode\\s*0*\\d+$", RegexOption.IGNORE_CASE).matches(ep.name.trim()) -> "Épisode $n"
        else -> "$n. ${ep.name}"
    }
}
