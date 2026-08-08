package app.lumen.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.api.StremioClient
import app.lumen.api.StremioManifest
import app.lumen.api.StremioMeta
import app.lumen.api.TmdbClient
import app.lumen.domain.AddonEntry
import app.lumen.domain.AddonStore
import app.lumen.domain.CardItem
import app.lumen.ui.components.MediaCard
import app.lumen.ui.detail.SourcesOverlay
import app.lumen.ui.detail.SourcesTarget
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Les catégories de l'onglet Découvrir. */
private val DISCOVER_TYPES = listOf(
    "Films" to listOf("movie"),
    "Séries" to listOf("series"),
    "Chaînes TV" to listOf("tv", "channel"),
)

/**
 * Découvrir : les CATALOGUES des addons Stremio, groupés par catégorie
 * (Films / Séries / Chaînes TV). Un film/série s'ouvre en fiche TMDB ;
 * une chaîne TV ouvre directement ses flux.
 */
@Composable
fun DiscoverScreen(
    client: JellyfinClient,
    tmdb: TmdbClient,
    onOpen: (String) -> Unit,
    onPlayExternal: (url: String, title: String, headers: Map<String, String>) -> Unit,
    onPlayTorrent: (infoHash: String, title: String) -> Unit,
) {
    val stremio = remember { StremioClient(client.http) }
    val store = remember { AddonStore() }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(0) }
    var sourcesTarget by remember { mutableStateOf<SourcesTarget?>(null) }

    // Les manifestes complets (avec catalogues) des addons activés.
    val manifests by produceState<List<Pair<AddonEntry, StremioManifest?>>?>(initialValue = null) {
        val enabled = store.list().filter { it.enabled }
        value = coroutineScope {
            enabled.map { addon ->
                async { addon to runCatching { stremio.manifest(addon.manifestUrl) }.getOrNull() }
            }.awaitAll()
        }
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 96.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "chips") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 48.dp),
                ) {
                    DISCOVER_TYPES.forEachIndexed { i, (label, _) ->
                        val isSel = i == selected
                        Text(
                            label,
                            color = if (isSel) Color.Black else LumenColors.OnBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(
                                    if (isSel) LumenColors.OnBackground else LumenColors.Surface,
                                    RoundedCornerShape(18.dp),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { selected = i }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                        )
                    }
                }
            }

            val types = DISCOVER_TYPES[selected].second
            when (val list = manifests) {
                null -> item(key = "loading") {
                    CircularProgressIndicator(
                        color = LumenColors.Accent,
                        modifier = Modifier.padding(48.dp).size(32.dp),
                    )
                }
                else -> {
                    val rails = list.flatMap { (addon, manifest) ->
                        manifest?.catalogs.orEmpty()
                            .filter { it.type in types }
                            .map { Triple(addon, manifest!!, it) }
                    }
                    if (rails.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                if (list.isEmpty()) {
                                    "Aucun addon installé — ajoute-en dans Paramètres → Addons Stremio."
                                } else {
                                    "Aucun catalogue de ce type dans tes addons."
                                },
                                color = LumenColors.Muted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 48.dp),
                            )
                        }
                    }
                    items(rails.size, key = { "rail-${rails[it].third.type}-${rails[it].third.id}-${rails[it].first.manifestUrl}" }) { i ->
                        val (addon, _, catalog) = rails[i]
                        CatalogRail(
                            stremio = stremio,
                            addon = addon,
                            type = catalog.type,
                            catalogId = catalog.id,
                            title = "${catalog.name.ifBlank { catalog.id }} — ${addon.name}",
                            grid = catalog.type in listOf("tv", "channel"),
                            onClick = { meta ->
                                when {
                                    // Chaîne TV : lecture DIRECTE — c'est l'addon
                                    // de la chaîne, pas besoin de choisir la source.
                                    catalog.type in listOf("tv", "channel") -> scope.launch {
                                        val streams = runCatching {
                                            stremio.streams(addon.manifestUrl, catalog.type, meta.id)
                                        }.getOrDefault(emptyList())
                                        val direct = streams.firstOrNull { it.playable }
                                        when {
                                            direct?.url != null ->
                                                onPlayExternal(direct.url, meta.name, direct.requestHeaders)
                                            streams.firstOrNull()?.infoHash != null ->
                                                onPlayTorrent(streams.first().infoHash!!, meta.name)
                                            else -> sourcesTarget = SourcesTarget(catalog.type, meta.id, meta.name)
                                        }
                                    }
                                    // Film/série IMDb → fiche TMDB complète.
                                    meta.id.startsWith("tt") -> scope.launch {
                                        val found = runCatching { tmdb.findByImdb(meta.id) }.getOrNull()
                                        if (found != null) {
                                            onOpen("tmdb:${found.first}:${found.second}")
                                        } else {
                                            sourcesTarget = SourcesTarget(catalog.type, meta.id, meta.name)
                                        }
                                    }
                                    // Identifiant propriétaire de l'addon → ses flux.
                                    else -> sourcesTarget = SourcesTarget(catalog.type, meta.id, meta.name)
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

@Composable
private fun CatalogRail(
    stremio: StremioClient,
    addon: AddonEntry,
    type: String,
    catalogId: String,
    title: String,
    grid: Boolean = false,
    onClick: (StremioMeta) -> Unit,
) {
    val metas by produceState<List<StremioMeta>?>(initialValue = null, addon.manifestUrl, type, catalogId) {
        value = runCatching { stremio.catalog(addon.manifestUrl, type, catalogId) }.getOrDefault(emptyList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            color = LumenColors.OnBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        when (val list = metas) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.padding(horizontal = 48.dp).size(22.dp),
            )
            else -> if (grid) {
                // Chaînes TV : GRILLE verticale — tout est atteignable au scroll,
                // pas coincé au bout d'une rangée horizontale.
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 48.dp),
                ) {
                    list.chunked(7).forEach { rowMetas ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowMetas.forEach { meta ->
                                MediaCard(
                                    CardItem(
                                        id = "strem:${meta.type}:${meta.id}",
                                        title = meta.name,
                                        posterUrl = meta.poster,
                                        inLibrary = false,
                                    ),
                                    onClick = { onClick(meta) },
                                )
                            }
                        }
                    }
                }
            } else LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 48.dp),
            ) {
                items(list.take(40), key = { it.id }) { meta ->
                    MediaCard(
                        CardItem(
                            id = "strem:${meta.type}:${meta.id}",
                            title = meta.name,
                            posterUrl = meta.poster,
                            inLibrary = false,
                        ),
                        onClick = { onClick(meta) },
                    )
                }
            }
        }
    }
}
