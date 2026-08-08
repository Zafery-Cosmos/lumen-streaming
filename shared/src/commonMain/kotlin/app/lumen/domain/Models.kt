package app.lumen.domain

import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.api.TmdbClient
import app.lumen.api.TmdbItem
import app.lumen.auth.StoredSession

/**
 * Carte unifiée : l'UI ne sait pas si un titre vient de Jellyfin ou de TMDB
 * (plan §5 — même principe que la future ContentSource). Elle affiche juste.
 */
data class CardItem(
    val id: String,                 // "jf:<id>" ou "tmdb:<movie|tv>:<id>"
    val title: String,
    val posterUrl: String?,
    val progressPercent: Double? = null,   // Jellyfin uniquement
    val rank: Int? = null,                 // rangée Top 10
    val inLibrary: Boolean,
)

/** Une entrée du carrousel hero : visuels précalculés, prêts à afficher. */
data class HeroItem(
    val id: String,
    val title: String,
    val backdropUrl: String,
    val logoUrl: String?,
    val overview: String?,
    val year: Int?,
    val runtimeMinutes: Int?,
    val rating: String?,
)

fun BaseItem.toCard(client: JellyfinClient, session: StoredSession, wideThumb: Boolean = false): CardItem {
    val type = if (wideThumb && imageTags.containsKey("Thumb")) "Thumb" else "Primary"
    val label = if (this.type == "Episode") {
        "${seriesName ?: name} — S${parentIndexNumber ?: "?"}E${indexNumber ?: "?"}"
    } else name
    return CardItem(
        id = "jf:$id",
        title = label,
        posterUrl = client.imageUrl(session.baseUrl, id, type, imageTags[type], maxWidth = 500),
        progressPercent = userData?.playedPercentage,
        inLibrary = true,
    )
}

fun TmdbItem.toCard(rank: Int? = null): CardItem = CardItem(
    id = "tmdb:${mediaType ?: "movie"}:$id",
    title = displayName,
    posterUrl = TmdbClient.posterUrl(posterPath),
    rank = rank,
    inLibrary = false,
)

fun BaseItem.toHero(client: JellyfinClient, session: StoredSession): HeroItem? {
    val backdrop = when {
        backdropImageTags.isNotEmpty() ->
            client.imageUrl(session.baseUrl, id, "Backdrop", backdropImageTags.first(), maxWidth = 1920)
        imageTags.containsKey("Thumb") ->
            client.imageUrl(session.baseUrl, id, "Thumb", imageTags["Thumb"], maxWidth = 1920)
        else -> return null   // pas de visuel large → pas sa place dans le hero
    }
    return HeroItem(
        id = id,
        title = name,
        backdropUrl = backdrop,
        logoUrl = imageTags["Logo"]?.let { client.imageUrl(session.baseUrl, id, "Logo", it, maxWidth = 800) },
        overview = overview,
        year = productionYear,
        runtimeMinutes = runTimeMinutes,
        rating = officialRating,
    )
}
