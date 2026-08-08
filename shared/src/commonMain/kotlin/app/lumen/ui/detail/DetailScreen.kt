package app.lumen.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.api.TmdbClient
import app.lumen.api.TmdbDetail
import app.lumen.auth.StoredSession
import app.lumen.domain.toCard
import app.lumen.ui.components.MediaCard
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage

/** Ce que la fiche a réussi à charger. */
private sealed interface DetailData {
    data class Jellyfin(val item: BaseItem) : DetailData
    data class Tmdb(val detail: TmdbDetail) : DetailData
    data object Failed : DetailData
}

/**
 * Fiche détail (plan §3). `mediaId` : "jf:<id>" ou "tmdb:<movie|tv>:<id>".
 * Jellyfin → fiche complète avec saisons/épisodes ; TMDB → métadonnées seules,
 * la lecture arrivera avec les addons (L8).
 */
@Composable
fun DetailScreen(
    client: JellyfinClient,
    tmdb: TmdbClient,
    session: StoredSession,
    mediaId: String,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPlayExternal: (url: String, title: String, headers: Map<String, String>) -> Unit,
    onPlayTorrent: (infoHash: String, title: String) -> Unit,
) {
    // Sources d'addons Stremio (plan §5) : état du panneau + clients.
    val stremio = remember { app.lumen.api.StremioClient(client.http) }
    val addonStore = remember { app.lumen.domain.AddonStore() }
    var sourcesTarget by remember { mutableStateOf<SourcesTarget?>(null) }
    val data by produceState<DetailData?>(initialValue = null, mediaId) {
        value = runCatching {
            when {
                mediaId.startsWith("jf:") -> {
                    val id = mediaId.removePrefix("jf:")
                    DetailData.Jellyfin(client.item(session.baseUrl, session.userId, id))
                }
                mediaId.startsWith("tmdb:") -> {
                    val (_, type, id) = mediaId.split(":")
                    DetailData.Tmdb(tmdb.detail(if (type == "tv") "tv" else "movie", id.toLong()))
                }
                else -> DetailData.Failed
            }
        }.getOrDefault(DetailData.Failed)
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        when (val d = data) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            is DetailData.Jellyfin -> JellyfinDetail(
                client, tmdb, session, d.item, onPlay, onOpen,
                onSources = { sourcesTarget = it },
            )
            is DetailData.Tmdb -> TmdbDetailBody(d.detail, onOpen, onSources = { sourcesTarget = it })
            is DetailData.Failed -> Text(
                "Impossible de charger cette fiche.",
                color = LumenColors.Muted,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Retour — sous la barre de navigation flottante, pas dessous le logo.
        Box(
            Modifier.padding(start = 24.dp, top = 84.dp).size(42.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        // Panneau Sources — par-dessus la fiche.
        sourcesTarget?.let { target ->
            SourcesOverlay(
                stremio = stremio,
                addons = addonStore.list(),
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

// --- Fiche Jellyfin (lisible) ---------------------------------------------

/** Un épisode Jellyfin replacé à sa vraie position (via TMDB si mal rangé). */
private data class OrganizedEpisode(
    val ep: BaseItem,
    val season: Int,
    val number: Int,
    val extra: app.lumen.domain.EpisodeExtra?,
)

@Composable
private fun JellyfinDetail(
    client: JellyfinClient,
    tmdb: TmdbClient,
    session: StoredSession,
    item: BaseItem,
    onPlay: (String) -> Unit,
    onOpen: (String) -> Unit,
    onSources: (SourcesTarget) -> Unit,
) {
    val seriesImdb = item.providerIds["Imdb"]
    // Titres similaires proposés par le serveur.
    val similar by produceState<List<app.lumen.domain.CardItem>>(initialValue = emptyList(), item.id) {
        value = runCatching {
            client.similar(session.baseUrl, session.userId, item.id).items
                .map { it.toCard(client, session) }
        }.getOrDefault(emptyList())
    }
    // Réorganisation virtuelle : TOUS les épisodes de la série sont chargés,
    // replacés à leur vraie saison via TMDB (numéros absolus compris), puis
    // regroupés — l'app n'affiche jamais le rangement bancal du serveur.
    val organized by produceState<Map<Int, List<OrganizedEpisode>>?>(initialValue = null, item.id) {
        if (item.type != "Series") {
            value = emptyMap(); return@produceState
        }
        val eps = runCatching {
            client.episodes(session.baseUrl, session.userId, item.id).items
        }.getOrDefault(emptyList())
        val tmdbId = item.providerIds["Tmdb"]?.toLongOrNull()
        val enricher = app.lumen.domain.EpisodeEnricher(tmdb)
        if (tmdbId != null) enricher.forSeries(tmdbId)

        value = eps.map { ep ->
            // 1) Le NOM DE FICHIER d'abord — « 053 - FL - 01-01 - Titre.mkv » dit
            //    la vérité là où les IndexNumber de Jellyfin sont faux.
            val parsed = app.lumen.domain.parseEpisodeFileName(ep.path)
            if (parsed != null) {
                val resolved = if (tmdbId != null) enricher.resolve(parsed.season, parsed.episode) else null
                OrganizedEpisode(
                    ep = ep,
                    season = parsed.season,
                    number = parsed.episode,
                    extra = resolved?.extra
                        ?: parsed.title?.let { app.lumen.domain.EpisodeExtra(it, null, null, null) },
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
            .toList().sortedBy { it.first }.toMap()   // LinkedHashMap : get(null) sans NPE, contrairement à TreeMap
    }

    var selectedSeason by remember(organized) { mutableStateOf(organized?.keys?.firstOrNull { it > 0 } ?: organized?.keys?.firstOrNull()) }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            DetailHeader(
                client, session, item, onPlay,
                onSources = if (item.type == "Movie" && seriesImdb != null) {
                    { onSources(SourcesTarget("movie", seriesImdb, item.name)) }
                } else null,
            )
        }

        val groups = organized
        if (item.type == "Series") {
            if (groups == null) {
                item(key = "loading") {
                    CircularProgressIndicator(
                        color = LumenColors.Accent,
                        modifier = Modifier.padding(48.dp).size(30.dp),
                    )
                }
            } else if (groups.isNotEmpty()) {
                item(key = "season-picker") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        items(groups.keys.toList(), key = { it }) { seasonNum ->
                            val selected = seasonNum == selectedSeason
                            Text(
                                if (seasonNum == 0) "Specials" else "Saison $seasonNum",
                                color = if (selected) Color.Black else LumenColors.OnBackground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(
                                        if (selected) LumenColors.OnBackground else LumenColors.Surface,
                                        RoundedCornerShape(20.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { selectedSeason = seasonNum }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                item(key = "episodes") {
                    // Fondu quand on change de saison — la liste ne « saute » pas.
                    AnimatedContent(
                        targetState = selectedSeason,
                        transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(150))) },
                    ) { seasonNum ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
                        ) {
                            seasonNum?.let { groups[it] }.orEmpty().forEach { org ->
                                EpisodeRow(
                                    client, session, org, onPlay,
                                    onSources = if (seriesImdb != null) {
                                        {
                                            onSources(
                                                SourcesTarget(
                                                    "series",
                                                    "$seriesImdb:${org.season}:${org.number}",
                                                    "${item.name} S${org.season}E${org.number}",
                                                ),
                                            )
                                        }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Réalisation + distribution (les données People du serveur).
        val directors = item.people.filter { it.type == "Director" }
        val cast = item.people.filter { it.type == "Actor" }.take(15)
        if (directors.isNotEmpty() || cast.isNotEmpty()) {
            item(key = "people") {
                PeopleSection(
                    directors = directors.map { it.name },
                    cast = cast.map {
                        PersonCardData(
                            name = it.name,
                            subtitle = it.role,
                            imageUrl = it.primaryImageTag?.let { tag ->
                                client.imageUrl(session.baseUrl, it.id, "Primary", tag, maxWidth = 200)
                            },
                        )
                    },
                    onPerson = { name -> onOpen("person:$name") },
                )
            }
        }
        if (similar.isNotEmpty()) {
            item(key = "similar") {
                val ctx = remember { app.lumen.ui.components.CardContext(client, session, onPlay) }
                SimilarSection(similar, onOpen, ctx)
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun DetailHeader(
    client: JellyfinClient,
    session: StoredSession,
    item: BaseItem,
    onPlay: (String) -> Unit,
    onSources: (() -> Unit)? = null,
) {
    // Fiche d'épisode : nom de fichier parsé + visuels de la SÉRIE en secours,
    // sinon on affiche « 727 - FL - 08-03 - … » sur fond noir.
    val parsed = if (item.type == "Episode") app.lumen.domain.parseEpisodeFileName(item.path) else null
    val displayTitle = parsed?.title ?: item.name
    val episodeSubtitle = when {
        parsed != null -> "${item.seriesName ?: ""} · Saison ${parsed.season} · Épisode ${parsed.episode}"
        item.type == "Episode" -> "${item.seriesName ?: ""} · S${item.parentIndexNumber ?: "?"}E${item.indexNumber ?: "?"}"
        else -> null
    }
    val backdrop = when {
        item.backdropImageTags.isNotEmpty() ->
            client.imageUrl(session.baseUrl, item.id, "Backdrop", item.backdropImageTags.first(), maxWidth = 1920)
        item.parentBackdropItemId != null && item.parentBackdropImageTags.isNotEmpty() ->
            client.imageUrl(session.baseUrl, item.parentBackdropItemId, "Backdrop", item.parentBackdropImageTags.first(), maxWidth = 1920)
        item.type == "Episode" && item.seriesId != null ->
            client.imageUrl(session.baseUrl, item.seriesId, "Backdrop", null, maxWidth = 1920)
        else -> client.imageUrl(session.baseUrl, item.id, "Primary", item.imageTags["Primary"], maxWidth = 1920)
    }

    Box(Modifier.fillMaxWidth().aspectRatio(16f / 7.2f)) {
        AsyncImage(
            model = backdrop,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 1f to LumenColors.Background),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val logoTag = item.imageTags["Logo"]
            if (logoTag != null) {
                AsyncImage(
                    model = client.imageUrl(session.baseUrl, item.id, "Logo", logoTag, maxWidth = 800),
                    contentDescription = null,
                    modifier = Modifier.height(80.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                )
            } else {
                episodeSubtitle?.let {
                    Text(it, color = LumenColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(displayTitle, color = LumenColors.OnBackground, fontSize = 38.sp, fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                item.productionYear?.let { Meta(it.toString()) }
                item.runTimeMinutes?.let { Meta("${it / 60}h${(it % 60).toString().padStart(2, '0')}") }
                item.communityRating?.let { Meta("★ ${(it * 10).toInt() / 10.0}") }
                if (item.genres.isNotEmpty()) Meta(item.genres.take(3).joinToString(" · "))
            }
            item.overview?.let {
                Text(
                    it,
                    color = LumenColors.OnBackground.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(620.dp),
                )
            }
            val resume = (item.userData?.playbackPositionTicks ?: 0L) > 0L
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    // Série : « Lire » lance le premier épisode utile via NextUp au L12 ;
                    // pour l'instant il ne s'applique qu'aux items directement lisibles.
                    onClick = { if (item.type != "Series") onPlay(item.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (resume) "Reprendre" else "Lire",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                onSources?.let { open ->
                    Button(
                        onClick = open,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LumenColors.SurfaceHigh.copy(alpha = 0.75f),
                        ),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = LumenColors.OnBackground,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sources", color = LumenColors.OnBackground, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    client: JellyfinClient,
    session: StoredSession,
    org: OrganizedEpisode,
    onPlay: (String) -> Unit,
    onSources: (() -> Unit)? = null,
) {
    val ep = org.ep
    val extra = org.extra
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPlay(ep.id) }
            .background(LumenColors.Surface)
            .padding(10.dp),
    ) {
        Box(
            Modifier.width(200.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .background(LumenColors.SurfaceHigh),
        ) {
            val imageModel = when {
                ep.imageTags.containsKey("Primary") ->
                    client.imageUrl(session.baseUrl, ep.id, "Primary", ep.imageTags["Primary"], maxWidth = 400)
                extra?.stillUrl != null -> extra.stillUrl
                else -> null
            }
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Pas de vignette côté serveur : placeholder vectoriel, pas un trou noir.
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = LumenColors.Muted.copy(alpha = 0.5f),
                    modifier = Modifier.size(34.dp).align(Alignment.Center),
                )
            }
            val progress = ep.userData?.playedPercentage
            if (progress != null && progress > 0) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((progress / 100).toFloat())
                            .background(LumenColors.Accent),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            // Numéro RÉEL dans la saison (pas l'absolu), et vrai titre TMDB en priorité.
            val n = org.number.takeIf { it > 0 }
            val label = when {
                extra?.title != null -> if (n != null) "$n. ${extra.title}" else extra.title
                n == null -> ep.name
                Regex("^[ÉE]pisode\\s*0*\\d+$", RegexOption.IGNORE_CASE).matches(ep.name.trim()) -> "Épisode $n"
                else -> "$n. ${ep.name}"
            }
            Text(
                label,
                color = LumenColors.OnBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            (ep.runTimeMinutes ?: extra?.runtimeMinutes)?.let {
                Text("$it min", color = LumenColors.Muted, fontSize = 12.sp)
            }
            (ep.overview?.takeIf { it.isNotBlank() } ?: extra?.overview)?.let {
                Text(
                    it,
                    color = LumenColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            onSources?.let { open ->
                Text(
                    "Sources (addons)",
                    color = LumenColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = open)
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

// --- Fiche TMDB (catalogue, pas encore lisible) ----------------------------

@Composable
private fun TmdbDetailBody(detail: TmdbDetail, onOpen: (String) -> Unit, onSources: (SourcesTarget) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 7.2f)) {
                AsyncImage(
                    model = TmdbClient.backdropUrl(detail.backdropPath) ?: TmdbClient.posterUrl(detail.posterPath),
                    contentDescription = detail.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to LumenColors.Background),
                    ),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        detail.displayName,
                        color = LumenColors.OnBackground,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        detail.year?.let { Meta(it.toString()) }
                        detail.runtime?.let { Meta("${it / 60}h${(it % 60).toString().padStart(2, '0')}") }
                        detail.numberOfSeasons?.let { Meta("$it saison${if (it > 1) "s" else ""}") }
                        detail.voteAverage?.let { Meta("★ ${(it * 10).toInt() / 10.0}") }
                        if (detail.genres.isNotEmpty()) Meta(detail.genres.take(3).joinToString(" · ") { it.name })
                    }
                    detail.overview?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = LumenColors.OnBackground.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(620.dp),
                        )
                    }
                    // Pas dans la médiathèque : la lecture passe par les ADDONS.
                    val imdb = detail.externalIds?.imdbId
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (imdb != null && detail.runtime != null) {
                            // Un runtime → c'est un film : sources directes possibles.
                            Button(
                                onClick = { onSources(SourcesTarget("movie", imdb, detail.displayName)) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Cloud,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sources", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = LumenColors.SurfaceHigh,
                            ),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text("Pas dans votre médiathèque", color = LumenColors.Muted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Réalisation + distribution TMDB.
        val directors = detail.credits?.crew?.filter { it.job == "Director" }?.map { it.name }.orEmpty()
        val cast = detail.credits?.cast?.take(15).orEmpty().map {
            PersonCardData(it.name, it.character, TmdbClient.profileUrl(it.profilePath))
        }
        if (directors.isNotEmpty() || cast.isNotEmpty()) {
            item(key = "people") { PeopleSection(directors, cast, onPerson = { onOpen("person:$it") }) }
        }
        val similar = detail.similar?.results.orEmpty().take(12).map { it.toCard() }
        if (similar.isNotEmpty()) {
            item(key = "similar") { SimilarSection(similar, onOpen, ctx = null) }
        }
        item(key = "bottom") { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, color = LumenColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

// --- Sections communes : distribution & similaires -------------------------

data class PersonCardData(val name: String, val subtitle: String?, val imageUrl: String?)

@Composable
private fun PeopleSection(directors: List<String>, cast: List<PersonCardData>, onPerson: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 26.dp),
    ) {
        if (directors.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 48.dp),
            ) {
                Text("Réalisation :", color = LumenColors.Muted, fontSize = 14.sp)
                Text(
                    directors.joinToString(", "),
                    color = LumenColors.OnBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (cast.isNotEmpty()) {
            Text(
                "Distribution",
                color = LumenColors.OnBackground,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 48.dp),
            ) {
                items(cast.size) { i -> PersonCard(cast[i], onClick = { onPerson(cast[i].name) }) }
            }
        }
    }
}

@Composable
private fun PersonCard(person: PersonCardData, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(92.dp).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(LumenColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (person.imageUrl != null) {
                AsyncImage(
                    model = person.imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    person.name.split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString(""),
                    color = LumenColors.Muted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            person.name,
            color = LumenColors.OnBackground,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        person.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = LumenColors.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SimilarSection(
    cards: List<app.lumen.domain.CardItem>,
    onOpen: (String) -> Unit,
    ctx: app.lumen.ui.components.CardContext?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 26.dp),
    ) {
        Text(
            "Titres similaires",
            color = LumenColors.OnBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            items(cards.size) { i ->
                MediaCard(cards[i], onClick = { onOpen(cards[i].id) }, ctx = ctx)
            }
        }
    }
}
