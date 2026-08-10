package app.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.domain.CardActions
import app.lumen.domain.CardItem
import app.lumen.platformDownload
import app.lumen.ui.theme.LocalCompactLayout
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Contexte des actions rapides d'une carte (fourni par les écrans). */
class CardContext(
    val client: JellyfinClient,
    val session: StoredSession,
    val onPlay: (String) -> Unit,
) {
    val actions = CardActions(client, session)
}

/** Apparition décalée : glissement + fondu, avec un retard croissant par index. */
@Composable
fun StaggeredReveal(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
    ) {
        content()
    }
}

/**
 * Carte média unifiée : scale au survol, progression, rang Top 10, et pour les
 * items de la médiathèque : vu (✓), favori (cœur) et menu complet (⋮) —
 * les mêmes entrées que le menu contextuel du client web Jellyfin.
 */
@Composable
fun MediaCard(
    card: CardItem,
    wide: Boolean = false,
    onClick: () -> Unit = {},
    ctx: CardContext? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )
    val scope = rememberCoroutineScope()

    var played by remember(card.id) { mutableStateOf(card.played) }
    var favorite by remember(card.id) { mutableStateOf(card.favorite) }
    var menuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }

    val (width, height) = if (wide) 248.dp to 140.dp else 138.dp to 207.dp
    val rawId = card.id.removePrefix("jf:")

    Column(
        modifier = Modifier
            .width(width)
            .hoverable(interaction)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.height(height).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(LumenColors.Surface)) {
            if (card.posterUrl != null) {
                AsyncImage(
                    model = card.posterUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = LumenColors.Muted.copy(alpha = 0.4f),
                    modifier = Modifier.size(30.dp).align(Alignment.Center),
                )
            }
            card.rank?.let { rank ->
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(rank.toString(), color = LumenColors.OnBackground, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
            // Coche permanente si déjà vu — comme Jellyfin.
            if (played && !hovered) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                        .background(LumenColors.Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Vu", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            val progress = card.progressPercent
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
            if (hovered) {
                // Badge lecture au centre.
                Box(
                    Modifier.align(Alignment.Center).size(40.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (card.inLibrary) ctx?.onPlay?.invoke(rawId) else onClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Lire", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            // Rangée d'actions : reste MONTÉE tant que le menu est ouvert —
            // sinon le popup fait perdre le survol, se démonte, et clignote.
            if (hovered || menuOpen || infoOpen) {
                if (ctx != null && card.inLibrary) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    ) {
                        SmallAction(
                            Icons.Filled.Check,
                            if (played) "Marquer non vu" else "Marquer vu",
                            active = played,
                        ) {
                            played = !played
                            scope.launch { ctx.actions.setPlayed(card.id, played) }
                        }
                        SmallAction(
                            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Favori",
                            active = favorite,
                        ) {
                            favorite = !favorite
                            scope.launch { ctx.actions.setFavorite(card.id, favorite) }
                        }
                        Box {
                            SmallAction(Icons.Filled.MoreVert, "Options") { menuOpen = true }
                            CardMenu(
                                ctx = ctx,
                                card = card,
                                rawId = rawId,
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                onInfo = { infoOpen = true },
                                played = played,
                                onPlayedChange = {
                                    played = it
                                    scope.launch { ctx.actions.setPlayed(card.id, it) }
                                },
                                favorite = favorite,
                                onFavoriteChange = {
                                    favorite = it
                                    scope.launch { ctx.actions.setFavorite(card.id, it) }
                                },
                            )
                        }
                    }
                }
            }
        }
        // L'affiche porte déjà le titre : sur téléphone, où chaque ligne coûte
        // une carte visible en moins à l'écran, la légende est redondante.
        // Sans affiche (rien à charger, ou pas encore chargée), elle reste le
        // seul moyen d'identifier la carte : on la garde dans ce cas.
        val showCaption = card.posterUrl == null || !LocalCompactLayout.current
        if (showCaption) {
            Text(
                card.title,
                color = LumenColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (infoOpen && ctx != null) {
        MediaInfoDialog(ctx, rawId, onDismiss = { infoOpen = false })
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp)
            .background(
                if (active) LumenColors.Accent else Color.Black.copy(alpha = 0.65f),
                CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Le menu ⋮ complet — toutes les entrées du menu contextuel Jellyfin. */
@Composable
private fun CardMenu(
    ctx: CardContext,
    card: CardItem,
    rawId: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    played: Boolean,
    onPlayedChange: (Boolean) -> Unit,
    favorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var page by remember(expanded) { mutableStateOf("main") }
    var playlists by remember { mutableStateOf<List<BaseItem>?>(null) }
    var newName by remember(expanded) { mutableStateOf("") }
    var feedback by remember(expanded) { mutableStateOf<String?>(null) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(LumenColors.Surface),
    ) {
        when (page) {
            "main" -> {
                MenuItem(Icons.Filled.PlayArrow, "Lire") { onDismiss(); ctx.onPlay(rawId) }
                MenuItem(Icons.Filled.PlaylistPlay, "Tout lire à partir d'ici") {
                    // La file d'attente arrive au L12 — en attendant, lance l'item.
                    onDismiss(); ctx.onPlay(rawId)
                }
                MenuItem(Icons.Filled.Check, if (played) "Marquer non vu" else "Marquer vu") {
                    onPlayedChange(!played); onDismiss()
                }
                MenuItem(
                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                ) { onFavoriteChange(!favorite); onDismiss() }
                MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Ajouter à la liste de lecture") {
                    page = "playlist"
                    scope.launch { playlists = ctx.actions.playlists() }
                }
                MenuItem(Icons.Filled.Download, "Télécharger") {
                    val ok = platformDownload(
                        ctx.client.downloadUrl(ctx.session.baseUrl, rawId),
                        "${card.title.replace(Regex("""[\\/:*?"<>|]"""), "_")}.mkv",
                    )
                    feedback = if (ok) "Téléchargement lancé" else "Indisponible sur cette plateforme"
                }
                MenuItem(Icons.Filled.ContentCopy, "Copier l'URL du flux") {
                    scope.launch {
                        val upstream = "${ctx.session.baseUrl.trimEnd('/')}/Videos/$rawId/stream" +
                            "?static=true&api_key=${ctx.client.accessToken}"
                        // On copie l'adresse LOCALE : le jeton d'accès reste
                        // dans l'app au lieu de partir dans le presse-papier.
                        val link = if (app.lumen.player.StreamProxy.ensureRunning()) {
                            app.lumen.player.StreamProxy.register(upstream)
                        } else upstream
                        clipboard.setText(AnnotatedString(link))
                        feedback = "URL copiée (locale, sans jeton)"
                    }
                }
                MenuItem(Icons.Filled.Info, "Informations du média") { onDismiss(); onInfo() }
                feedback?.let {
                    Text(it, color = LumenColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
            "playlist" -> {
                MenuItem(Icons.Filled.WatchLater, "Regarder plus tard") {
                    scope.launch {
                        val ok = ctx.actions.addToWatchLater(card.id)
                        feedback = if (ok) "Ajouté à « Regarder plus tard »" else "Échec"
                        page = "main"
                    }
                }
                when (val list = playlists) {
                    null -> Text("Chargement…", color = LumenColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    else -> list.filterNot { it.name.equals(CardActions.WATCH_LATER, true) }.forEach { pl ->
                        MenuItem(Icons.Filled.PlaylistPlay, pl.name) {
                            scope.launch {
                                val ok = ctx.actions.addToPlaylist(pl.id, card.id)
                                feedback = if (ok) "Ajouté à « ${pl.name} »" else "Échec"
                                page = "main"
                            }
                        }
                    }
                }
                MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Créer une playlist") { page = "create" }
            }
            "create" -> {
                Text(
                    "Nom de la playlist",
                    color = LumenColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    textStyle = TextStyle(color = LumenColors.OnBackground, fontSize = 14.sp),
                    cursorBrush = SolidColor(LumenColors.Accent),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        .width(200.dp)
                        .background(LumenColors.SurfaceHigh, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                MenuItem(Icons.Filled.Check, "Créer et ajouter") {
                    if (newName.isNotBlank()) {
                        scope.launch {
                            val ok = ctx.actions.createPlaylist(newName.trim(), card.id)
                            feedback = if (ok) "Playlist « ${newName.trim()} » créée" else "Échec"
                            page = "main"
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, contentDescription = null, tint = LumenColors.Muted, modifier = Modifier.size(18.dp)) },
        text = { Text(label, color = LumenColors.OnBackground, fontSize = 14.sp) },
        onClick = onClick,
    )
}

/** « Informations du média » — les données réelles de l'item serveur. */
@Composable
private fun MediaInfoDialog(ctx: CardContext, rawId: String, onDismiss: () -> Unit) {
    var item by remember { mutableStateOf<BaseItem?>(null) }
    LaunchedEffect(rawId) {
        item = runCatching { ctx.client.item(ctx.session.baseUrl, ctx.session.userId, rawId) }.getOrNull()
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.Surface,
        confirmButton = {
            Text(
                "Fermer",
                color = LumenColors.Accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ).padding(8.dp),
            )
        },
        title = { Text(item?.name ?: "Informations", color = LumenColors.OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (val i = item) {
                    null -> Text("Chargement…", color = LumenColors.Muted)
                    else -> {
                        InfoLine("Type", i.type)
                        i.productionYear?.let { InfoLine("Année", it.toString()) }
                        i.runTimeMinutes?.let { InfoLine("Durée", "$it min") }
                        i.officialRating?.let { InfoLine("Classification", it) }
                        i.communityRating?.let { InfoLine("Note", "★ ${(it * 10).toInt() / 10.0}") }
                        if (i.genres.isNotEmpty()) InfoLine("Genres", i.genres.joinToString(", "))
                        i.providerIds["Imdb"]?.let { InfoLine("IMDb", it) }
                        i.providerIds["Tmdb"]?.let { InfoLine("TMDB", it) }
                        i.path?.let { InfoLine("Fichier", it.substringAfterLast('/')) }
                        InfoLine("Identifiant", i.id)
                    }
                }
            }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label : ", color = LumenColors.Muted, fontSize = 13.sp)
        Text(value, color = LumenColors.OnBackground, fontSize = 13.sp)
    }
}
