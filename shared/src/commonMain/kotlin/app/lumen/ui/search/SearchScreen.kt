package app.lumen.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.api.StremioClient
import app.lumen.api.StremioManifest
import app.lumen.api.StremioMeta
import app.lumen.api.TmdbClient
import app.lumen.auth.StoredSession
import app.lumen.domain.AddonEntry
import app.lumen.domain.AddonStore
import app.lumen.domain.CardItem
import app.lumen.domain.allows
import app.lumen.domain.toCard
import app.lumen.ui.components.MediaCard
import app.lumen.ui.detail.SourcesOverlay
import app.lumen.ui.detail.SourcesTarget
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recherche unifiée : la médiathèque Jellyfin ET tous les addons Stremio
 * activés (leurs catalogues qui savent chercher), en parallèle.
 */
@Composable
fun SearchScreen(
    client: JellyfinClient,
    tmdb: TmdbClient,
    session: StoredSession,
    profile: app.lumen.domain.LocalProfile?,
    query: String,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    onPlayExternal: (url: String, title: String, headers: Map<String, String>) -> Unit,
    onPlayTorrent: (infoHash: String, title: String) -> Unit,
) {
    val stremio = remember { StremioClient(client.http) }
    val store = remember { AddonStore() }
    val scope = rememberCoroutineScope()
    var sourcesTarget by remember { mutableStateOf<SourcesTarget?>(null) }

    // Manifestes des addons (une seule fois) — pour connaître leurs catalogues.
    val manifests by produceState<List<Pair<AddonEntry, StremioManifest?>>>(initialValue = emptyList()) {
        val enabled = store.list().filter { it.enabled }
        value = coroutineScope {
            enabled.map { addon ->
                async { addon to runCatching { stremio.manifest(addon.manifestUrl) }.getOrNull() }
            }.awaitAll()
        }
    }

    // Résultats Jellyfin.
    val results by produceState<List<BaseItem>?>(initialValue = null, query, profile) {
        value = null           // relance l'indicateur pendant la frappe
        delay(350)             // debounce : on attend que la frappe se calme
        value = runCatching {
            client.search(session.baseUrl, session.userId, query).items
                .filter { profile.allows(it) }
        }.getOrDefault(emptyList())
    }

    // Résultats des ADDONS : chaque catalogue interrogé en parallèle.
    val addonResults by produceState<List<StremioMeta>>(initialValue = emptyList(), query, manifests) {
        value = emptyList()
        delay(400)
        if (manifests.isEmpty()) return@produceState
        value = coroutineScope {
            manifests.flatMap { (addon, manifest) ->
                manifest?.catalogs.orEmpty().map { catalog ->
                    async {
                        runCatching {
                            stremio.searchCatalog(addon.manifestUrl, catalog.type, catalog.id, query)
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten().distinctBy { it.id }.take(24)
        }
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        when (val list = results) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                // Le haut compense la barre de navigation transparente qui flotte.
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 88.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (list.isEmpty()) "Aucun résultat dans la médiathèque pour « $query »"
                        else "Dans votre médiathèque",
                        color = if (list.isEmpty()) LumenColors.Muted else LumenColors.OnBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(list, key = { it.id }) { item ->
                    val card = item.toCard(client, session)
                    MediaCard(
                        card,
                        onClick = { onOpen(card.id) },
                        ctx = androidx.compose.runtime.remember {
                            app.lumen.ui.components.CardContext(client, session, onPlay)
                        },
                    )
                }

                if (addonResults.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "addons-header") {
                        Text(
                            "Depuis les addons",
                            color = LumenColors.OnBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                        )
                    }
                    items(addonResults, key = { "strem-${it.type}-${it.id}" }) { meta ->
                        MediaCard(
                            CardItem(
                                id = "strem:${meta.type}:${meta.id}",
                                title = meta.name,
                                posterUrl = meta.poster,
                                inLibrary = false,
                            ),
                            onClick = {
                                if (meta.id.startsWith("tt")) {
                                    scope.launch {
                                        val found = runCatching { tmdb.findByImdb(meta.id) }.getOrNull()
                                        if (found != null) {
                                            onOpen("tmdb:${found.first}:${found.second}")
                                        } else {
                                            sourcesTarget = SourcesTarget(meta.type, meta.id, meta.name)
                                        }
                                    }
                                } else {
                                    sourcesTarget = SourcesTarget(meta.type, meta.id, meta.name)
                                }
                            },
                        )
                    }
                }
            }
        }

        sourcesTarget?.let { target ->
            SourcesOverlay(
                stremio = stremio,
                addons = store.list(),
                target = target,
                onDismiss = { sourcesTarget = null },
                onPlay = { url, headers ->
                    sourcesTarget = null
                    onPlayExternal(url, target.title, headers)
                },
                onPlayTorrent = { hash ->
                    sourcesTarget = null
                    onPlayTorrent(hash, target.title)
                },
            )
        }
    }
}
