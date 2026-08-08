package app.lumen.domain

import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Une rangée de l'accueil : un titre et ses items, prêts à afficher. */
data class Rail(
    val id: String,
    val title: String,
    val items: List<BaseItem>,
    /** true → cartes 16:9 (reprise/épisodes), false → affiches 2:3. */
    val wide: Boolean = false,
)

data class HomeContent(
    val hero: BaseItem?,
    val rails: List<Rail>,
)

/**
 * Construit le contenu de l'accueil (plan §3) : Reprendre, À suivre, puis les
 * derniers ajouts de chaque bibliothèque. Tout part en parallèle — l'accueil
 * arrive d'un bloc plutôt qu'en cascade.
 */
class HomeRepository(private val client: JellyfinClient, private val session: StoredSession) {

    suspend fun load(): HomeContent = coroutineScope {
        val base = session.baseUrl
        val uid = session.userId

        val resume = async { runCatching { client.resumeItems(base, uid).items }.getOrDefault(emptyList()) }
        val nextUp = async { runCatching { client.nextUp(base, uid).items }.getOrDefault(emptyList()) }
        val views = async { runCatching { client.userViews(base, uid).items }.getOrDefault(emptyList()) }

        // Une rangée « Récemment ajoutés » par vraie bibliothèque — la vue
        // synthétique « Dossiers » (collectionType=folders) n'apporte que du bruit.
        val libraries = views.await().filter { it.collectionType != "folders" }
        val latestPerLibrary = libraries.map { lib ->
            async {
                lib to runCatching { client.latestItems(base, uid, lib.id) }.getOrDefault(emptyList())
            }
        }.awaitAll()

        val rails = buildList {
            resume.await().takeIf { it.isNotEmpty() }?.let {
                add(Rail("resume", "Reprendre la lecture", it, wide = true))
            }
            nextUp.await().takeIf { it.isNotEmpty() }?.let {
                add(Rail("nextup", "À suivre", it, wide = true))
            }
            latestPerLibrary.forEach { (lib, items) ->
                if (items.isNotEmpty()) add(Rail("latest-${lib.id}", "Nouveautés — ${lib.name}", items))
            }
        }

        // Le hero : de préférence un titre récent avec un backdrop à montrer.
        val hero = rails.asSequence().flatMap { it.items }
            .firstOrNull { it.backdropImageTags.isNotEmpty() || it.imageTags.containsKey("Thumb") }
            ?: rails.firstOrNull()?.items?.firstOrNull()

        HomeContent(hero = hero, rails = rails)
    }
}
