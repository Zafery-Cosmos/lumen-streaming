package app.lumen.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumen.api.JellyfinClient
import app.lumen.api.TmdbClient
import app.lumen.auth.StoredSession
import app.lumen.domain.CardItem
import app.lumen.domain.Rail
import app.lumen.domain.allows
import app.lumen.domain.toCard
import app.lumen.domain.toHero
import app.lumen.ui.components.CardContext
import app.lumen.ui.components.HeroCarousel
import app.lumen.ui.components.MediaRail
import app.lumen.i18n.T
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Ce que la page Films/Séries a chargé. */
private data class BrowseContent(
    val heroes: List<app.lumen.domain.HeroItem>,
    val rails: List<Rail>,
)

/**
 * Page Films ou Séries : le hero défilant, puis « Ma médiathèque » (ce que ton
 * serveur possède réellement) et enfin les rangées éditoriales TMDB du même
 * type — tout est là, mais on distingue clairement ce qui t'appartient.
 */
@Composable
fun BrowseScreen(
    client: JellyfinClient,
    tmdb: TmdbClient,
    session: StoredSession,
    profile: app.lumen.domain.LocalProfile?,
    includeTypes: String,
    title: String,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val isMovies = includeTypes == "Movie"

    val content by produceState<BrowseContent?>(initialValue = null, includeTypes, profile) {
        value = coroutineScope {
            val library = async {
                runCatching {
                    client.items(
                        session.baseUrl, session.userId,
                        includeTypes = includeTypes,
                        limit = app.lumen.domain.AppSettings.browsePageSize.value,
                    ).items.filter { profile.allows(it) }
                }.getOrDefault(emptyList())
            }

            // Rangées éditoriales du bon type : genres pour les films,
            // catalogues séries (populaires / mieux notées) pour les séries.
            val editorial = if (isMovies) {
                TmdbClient.HOME_GENRES.map { (genreId, label) ->
                    async {
                        label to runCatching { tmdb.moviesByGenre(genreId) }.getOrDefault(emptyList())
                    }
                }
            } else {
                TmdbClient.TV_ROWS.map { (path, label) ->
                    async { label to runCatching { tmdb.tvRow(path) }.getOrDefault(emptyList()) }
                }
            }

            val libItems = library.await()
            val rails = buildList {
                if (libItems.isNotEmpty()) {
                    add(Rail("library", T["browse.maMediatheque"], libItems.map { it.toCard(client, session) }))
                }
                editorial.awaitAll().forEach { (label, items) ->
                    if (items.isNotEmpty()) {
                        add(Rail("tmdb-$label", label, items.map { it.toCard() }))
                    }
                }
            }
            BrowseContent(
                // Hero tiré au sort dans toute la catégorie, renouvelé à chaque
                // ouverture — pas les 6 premiers titres, toujours les mêmes.
                heroes = libItems.mapNotNull { it.toHero(client, session) }.shuffled().take(40),
                rails = rails,
            )
        }
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        when (val c = content) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            else -> {
                val ctx = remember { CardContext(client, session, onPlay) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (c.heroes.isNotEmpty()) {
                        item(key = "hero") { HeroCarousel(c.heroes, onOpen, onPlay, tmdb = tmdb) }
                    }
                    items(c.rails.size, key = { c.rails[it].id }) { index ->
                        val rail = c.rails[index]
                        MediaRail(
                            title = rail.title,
                            cards = rail.items,
                            wide = rail.wide,
                            ctx = if (rail.id == "library") ctx else null,
                            onOpen = onOpen,
                        )
                    }
                    item(key = "bottom") { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
