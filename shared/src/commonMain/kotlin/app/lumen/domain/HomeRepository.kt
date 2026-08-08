package app.lumen.domain

import app.lumen.api.JellyfinClient
import app.lumen.api.TmdbClient
import app.lumen.auth.StoredSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Une rangée de l'accueil, prête à afficher. */
data class Rail(
    val id: String,
    val title: String,
    val items: List<CardItem>,
    /** true → cartes 16:9 (reprise/épisodes), false → affiches 2:3. */
    val wide: Boolean = false,
)

data class HomeContent(
    val heroes: List<HeroItem>,
    val rails: List<Rail>,
)

/**
 * Accueil éditorial (exigence utilisateur) : PAS de rangées par dossier
 * (« Downloads »…). À la place : Reprendre / À suivre (Jellyfin), Nouveautés
 * (Jellyfin fusionné), puis Top 10 de la semaine et rangées par genre via TMDB.
 * Le hero est un carrousel des films/séries Jellyfin avec un vrai backdrop.
 */
class HomeRepository(
    private val client: JellyfinClient,
    private val tmdb: TmdbClient,
    private val session: StoredSession,
) {

    suspend fun load(profile: LocalProfile? = null): HomeContent = coroutineScope {
        val base = session.baseUrl
        val uid = session.userId

        // --- Jellyfin -------------------------------------------------------
        val resume = async { runCatching { client.resumeItems(base, uid).items }.getOrDefault(emptyList()) }
        val nextUp = async { runCatching { client.nextUp(base, uid).items }.getOrDefault(emptyList()) }
        val recent = async {
            runCatching {
                client.items(
                    base, uid,
                    includeTypes = "Movie,Series",
                    sortBy = "DateCreated",
                    limit = 24,
                ).items
            }.getOrDefault(emptyList())
        }

        // --- TMDB (éditorial) ----------------------------------------------
        // Profil enfant : pas de Top 10 tout-venant, et uniquement les genres
        // adaptés (Animation, Familial). Le contenu Jellyfin est filtré par âge.
        val isChild = profile?.child == true
        val trending = async {
            if (isChild) emptyList() else runCatching { tmdb.trendingWeek() }.getOrDefault(emptyList())
        }
        val genres = if (isChild) listOf(16 to "Animation", 10751 to "Familial") else TmdbClient.HOME_GENRES
        val genreRails = genres.map { (genreId, label) ->
            async {
                Triple(genreId, label, runCatching { tmdb.moviesByGenre(genreId) }.getOrDefault(emptyList()))
            }
        }

        val recentItems = recent.await().filter { profile.allows(it) }

        val rails = buildList {
            resume.await().filter { profile.allows(it) }.takeIf { it.isNotEmpty() }?.let { list ->
                add(Rail("resume", "Reprendre la lecture", list.map { it.toCard(client, session, wideThumb = true) }, wide = true))
            }
            nextUp.await().filter { profile.allows(it) }.takeIf { it.isNotEmpty() }?.let { list ->
                add(Rail("nextup", "À suivre", list.map { it.toCard(client, session, wideThumb = true) }, wide = true))
            }
            recentItems.takeIf { it.isNotEmpty() }?.let { list ->
                add(Rail("recent", "Nouveautés", list.map { it.toCard(client, session) }))
            }
            trending.await().take(10).takeIf { it.isNotEmpty() }?.let { list ->
                add(Rail("top10", "Top 10 cette semaine", list.mapIndexed { i, it -> it.toCard(rank = i + 1) }))
            }
            genreRails.awaitAll().forEach { (genreId, label, items) ->
                if (items.isNotEmpty()) add(Rail("genre-$genreId", label, items.map { it.toCard() }))
            }
        }

        // Carrousel hero : films et séries Jellyfin, du plus récent au plus ancien.
        val heroes = recentItems.mapNotNull { it.toHero(client, session) }.take(8)

        HomeContent(heroes = heroes, rails = rails)
    }
}
