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
    private val watchRepo: WatchStateRepository? = null,
    private val hlsRepo: HlsLibraryRepository? = null,
    private val bucketRepo: BucketLibraryRepository? = null,
    private val plexRepo: PlexLibraryRepository? = null,
) {

    suspend fun load(profile: LocalProfile? = null): HomeContent = coroutineScope {
        val base = session.baseUrl
        val uid = session.userId

        // --- Jellyfin -------------------------------------------------------
        // « Reprendre » est PAR PROFIL (base locale) : la bibliothèque est
        // partagée, pas la progression. Sans profil, repli sur le serveur.
        val localResume = profile?.let { p -> watchRepo?.resume(p.id) }.orEmpty()
        val resume = async {
            if (localResume.isNotEmpty()) {
                val byId = runCatching {
                    client.itemsByIds(base, uid, localResume.map { it.itemId }).items
                }.getOrDefault(emptyList()).associateBy { it.id }
                localResume.mapNotNull { byId[it.itemId] }
            } else if (profile == null) {
                runCatching { client.resumeItems(base, uid).items }.getOrDefault(emptyList())
            } else emptyList()
        }
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

        // Hero : tiré au sort dans TOUT le catalogue, pas seulement les ajouts
        // récents — et le tirage change à chaque lancement.
        val heroPool = async {
            runCatching {
                client.items(
                    base, uid,
                    includeTypes = "Movie,Series",
                    sortBy = "Random",
                    limit = 200,
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

        // La progression affichée vient de la base locale du profil.
        val localPct = localResume.associate { it.itemId to it.percent }
        // Les réglages « Accueil » (§6.2) pilotent la présence ET L'ORDRE des
        // rangées : chaque section est construite puis placée selon homeOrder.
        val railsByKey = mutableMapOf<String, List<Rail>>()
        if (AppSettings.showResume.value) {
            resume.await().filter { profile.allows(it) }.takeIf { it.isNotEmpty() }?.let { list ->
                railsByKey["resume"] = listOf(
                    Rail(
                        "resume", "Reprendre la lecture",
                        list.map { item ->
                            val card = item.toCard(client, session, wideThumb = true)
                            localPct[item.id]?.let { card.copy(progressPercent = it) } ?: card
                        },
                        wide = true,
                    ),
                )
            }
        }
        if (AppSettings.showNextUp.value) {
            nextUp.await().filter { profile.allows(it) }.takeIf { it.isNotEmpty() }?.let { list ->
                railsByKey["nextup"] = listOf(
                    Rail("nextup", "À suivre", list.map { it.toCard(client, session, wideThumb = true) }, wide = true),
                )
            }
        }
        // Les dossiers HLS importés : une rangée à part, toujours en Direct Play.
        val hlsEntries = hlsRepo?.list().orEmpty()
        if (hlsEntries.isNotEmpty()) {
            railsByKey["hls"] = listOf(
                Rail("hls", "Mes dossiers HLS", hlsEntries.map { it.toCard() }),
            )
        }
        // Les titres indexés depuis les buckets perso : même principe.
        val bucketEntries = bucketRepo?.list().orEmpty()
        if (bucketEntries.isNotEmpty()) {
            railsByKey["bucket"] = listOf(
                Rail("bucket", "Mon stockage perso", bucketEntries.map { it.toCard() }),
            )
        }
        // Les titres indexés depuis Plex : une rangée de plus, au même titre
        // que les buckets — la source est invisible à l'usage.
        val plexEntries = plexRepo?.list().orEmpty()
        if (plexEntries.isNotEmpty()) {
            railsByKey["plex"] = listOf(
                Rail("plex", app.lumen.i18n.T["plex.monPlex"], plexEntries.map { it.toCard() }),
            )
        }
        if (AppSettings.showRecent.value) {
            recentItems
                .let { l -> if (AppSettings.hidePlayedInRecent.value) l.filterNot { it.userData?.played == true } else l }
                .takeIf { it.isNotEmpty() }?.let { list ->
                    railsByKey["recent"] = listOf(Rail("recent", "Ma médiathèque", list.map { it.toCard(client, session) }))
                }
        }
        if (AppSettings.showTop10.value) {
            trending.await().take(10).takeIf { it.isNotEmpty() }?.let { list ->
                railsByKey["top10"] = listOf(
                    Rail("top10", "Top 10 cette semaine", list.mapIndexed { i, it -> it.toCard(rank = i + 1) }),
                )
            }
        }
        if (AppSettings.showGenres.value) {
            railsByKey["genres"] = genreRails.awaitAll().mapNotNull { (genreId, label, items) ->
                items.takeIf { it.isNotEmpty() }?.let { Rail("genre-$genreId", label, it.map { i -> i.toCard() }) }
            }
        }
        val order = AppSettings.homeOrder.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val orderedKeys = order + railsByKey.keys.filterNot { order.contains(it) }
        val rails = orderedKeys.flatMap { railsByKey[it].orEmpty() }

        // Carrousel hero : la médiathèque ET le catalogue TMDB mélangés — on
        // met en avant aussi ce qu'on ne possède pas encore (lisible via les
        // addons). 40 titres, tirage renouvelé à chaque lancement.
        val libraryHeroes = heroPool.await()
            .filter { profile.allows(it) }
            .mapNotNull { it.toHero(client, session) }
        val catalogHeroes = if (isChild) emptyList() else {
            (trending.await() + genreRails.awaitAll().flatMap { it.third })
                .distinctBy { it.id }
                .mapNotNull { it.toHero() }
        }
        val heroes = (libraryHeroes.shuffled().take(25) + catalogHeroes.shuffled().take(25))
            .shuffled()
            .take(40)
            .ifEmpty { recentItems.mapNotNull { it.toHero(client, session) } }

        HomeContent(heroes = heroes, rails = rails)
    }
}
