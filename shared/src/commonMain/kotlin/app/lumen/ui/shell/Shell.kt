package app.lumen.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.resources.Res
import app.lumen.resources.logo
import app.lumen.ui.browse.BrowseScreen
import app.lumen.ui.detail.DetailScreen
import app.lumen.ui.home.HomeScreen
import app.lumen.ui.search.SearchScreen
import app.lumen.ui.settings.SettingsScreen
import app.lumen.ui.theme.LumenColors
import org.jetbrains.compose.resources.painterResource

/** Les onglets de la barre du haut. */
enum class ShellTab(val label: String) {
    Home("Accueil"),
    Movies("Films"),
    Series("Séries"),
    Discover("Découvrir"),
    Settings("Paramètres"),
}

/**
 * Coquille de navigation : barre du haut (logo, onglets, recherche dynamique,
 * paramètres) + contenu animé. La recherche n'est pas un onglet : elle se déploie
 * dans la barre et prend le dessus tant qu'elle contient du texte.
 */
@Composable
fun Shell(
    client: JellyfinClient,
    session: StoredSession,
    profile: app.lumen.domain.LocalProfile?,
    profileRepo: app.lumen.domain.ProfileRepository,
    watchRepo: app.lumen.domain.WatchStateRepository,
    onLogout: () -> Unit,
    onSwitchProfile: () -> Unit,
    onProfilesChanged: () -> Unit,
    servers: List<StoredSession>,
    onSwitchServer: (StoredSession) -> Unit,
    onAddServer: () -> Unit,
    onForgetServer: (StoredSession) -> Unit,
) {
    var tab by remember { mutableStateOf(ShellTab.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var settingsSub by remember { mutableStateOf<String?>(null) }
    val tmdb = remember { app.lumen.api.TmdbClient(client.http) }

    // Pile des fiches ouvertes : on peut enchaîner fiche → fiche et revenir.
    var detailStack by remember { mutableStateOf(listOf<String>()) }
    val openDetail: (String) -> Unit = { id -> detailStack = detailStack + id }

    // Lecture en cours : le lecteur remplace TOUT l'écran, barre comprise.
    // Un PlayRequest est soit un item Jellyfin, soit un flux externe d'addon.
    var playing by remember { mutableStateOf<app.lumen.domain.PlayRequest?>(null) }
    playing?.let { req ->
        app.lumen.ui.player.PlayerScreen(
            client, session,
            request = req,
            profile = profile,
            watchRepo = watchRepo,
            onBack = { playing = null; refreshKey++ },  // refresh → « Reprendre » à jour
        )
        return
    }
    val playItem: (String) -> Unit = { id -> playing = app.lumen.domain.PlayRequest(itemId = id) }

    // Écran de veille : la moindre interaction (observée en phase initiale,
    // sans rien consommer) réarme le délai.
    var lastActivity by remember { mutableStateOf(app.lumen.db.epochMillis()) }
    var screensaverOn by remember { mutableStateOf(false) }
    LaunchedEffect(app.lumen.domain.AppSettings.screensaverEnabled.value) {
        while (app.lumen.domain.AppSettings.screensaverEnabled.value) {
            kotlinx.coroutines.delay(5_000)
            val delayMs = app.lumen.domain.AppSettings.screensaverDelayMin.value * 60_000L
            screensaverOn = app.lumen.db.epochMillis() - lastActivity > delayMs
        }
        screensaverOn = false
    }

    // La barre est TRANSPARENTE et flotte au-dessus du contenu (style Netflix) :
    // le hero passe dessous, un dégradé assure la lisibilité des onglets.
    Box(
        Modifier.fillMaxSize().background(LumenColors.Background)
            .pointerInputObserve { lastActivity = app.lumen.db.epochMillis(); screensaverOn = false },
    ) {
        val showSearch = searchOpen && searchQuery.isNotBlank()
        val target = detailStack.lastOrNull()
            ?: if (showSearch) "search" else if (tab == ShellTab.Settings && settingsSub != null) "settings-${settingsSub}" else tab.name
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 20 })
                    .togetherWith(fadeOut(tween(180)))
            },
            modifier = Modifier.fillMaxSize(),
        ) { state ->
            when {
                state.startsWith("jf:") || state.startsWith("tmdb:") -> DetailScreen(
                    client, tmdb, session,
                    mediaId = state,
                    onBack = { detailStack = detailStack.dropLast(1) },
                    onPlay = playItem,
                    onOpen = openDetail,
                    onPlayExternal = { url, title, headers ->
                        playing = app.lumen.domain.PlayRequest(url = url, title = title, headers = headers)
                    },
                )
                state.startsWith("person:") -> app.lumen.ui.person.PersonScreen(
                    tmdb,
                    personName = state.removePrefix("person:"),
                    onOpen = openDetail,
                )
                state == "search" -> SearchScreen(
                    client, session, profile, searchQuery,
                    onOpen = openDetail,
                    onPlay = playItem,
                )
                state == ShellTab.Home.name -> HomeScreen(
                    client, tmdb, session, profile, watchRepo, refreshKey,
                    onOpen = openDetail,
                    onPlay = playItem,
                )
                state == ShellTab.Movies.name -> BrowseScreen(
                    client, session, profile, includeTypes = "Movie", title = "Films",
                    onOpen = openDetail, onPlay = playItem,
                )
                state == ShellTab.Series.name -> BrowseScreen(
                    client, session, profile, includeTypes = "Series", title = "Séries",
                    onOpen = openDetail, onPlay = playItem,
                )
                state == ShellTab.Discover.name -> app.lumen.ui.discover.DiscoverScreen(
                    client, tmdb,
                    onOpen = openDetail,
                    onPlayExternal = { url, title, headers ->
                        playing = app.lumen.domain.PlayRequest(url = url, title = title, headers = headers)
                    },
                )
                else -> when (val sub = settingsSub) {
                    null -> SettingsScreen(
                        session,
                        profileName = profile?.name,
                        onOpenSub = { settingsSub = it },
                        onSwitchProfile = onSwitchProfile,
                    )
                    "profiles" -> app.lumen.ui.profiles.ProfileSettingsScreen(
                        profileRepo,
                        onBack = { settingsSub = null },
                        onProfilesChanged = onProfilesChanged,
                    )
                    else -> app.lumen.ui.settings.SettingsSectionScreen(
                        sectionKey = sub,
                        client = client,
                        session = session,
                        servers = servers,
                        onSwitchServer = onSwitchServer,
                        onAddServer = onAddServer,
                        onForgetServer = onForgetServer,
                        onBack = { settingsSub = null },
                        onLogout = onLogout,
                    )
                }
            }
        }

        TopBar(
            current = tab,
            onTab = { tab = it; searchOpen = false; searchQuery = ""; detailStack = emptyList() },
            searchOpen = searchOpen,
            searchQuery = searchQuery,
            onSearchOpen = { searchOpen = true },
            onSearchClose = { searchOpen = false; searchQuery = "" },
            onSearchChange = { searchQuery = it },
            refreshKey = refreshKey,
            onSync = { refreshKey++ },
        )

        // L'écran de veille par-dessus tout — n'existe que lorsqu'il est actif,
        // donc il ne bloque jamais les interactions en temps normal.
        androidx.compose.animation.AnimatedVisibility(
            visible = screensaverOn,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(900)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)),
        ) {
            Box(
                Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp),
                )
            }
        }
    }
}

/** Observe les événements pointeur en phase initiale, sans les consommer. */
private fun Modifier.pointerInputObserve(onEvent: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onEvent()
            }
        }
    }

@Composable
private fun TopBar(
    current: ShellTab,
    onTab: (ShellTab) -> Unit,
    searchOpen: Boolean,
    searchQuery: String,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchChange: (String) -> Unit,
    refreshKey: Int,
    onSync: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to LumenColors.Background.copy(alpha = 0.85f),
                    1f to androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            .height(72.dp)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "Lumen",
            modifier = Modifier.size(38.dp),
        )
        // Onglets de navigation (Paramètres vit à droite, pas ici).
        listOf(ShellTab.Home, ShellTab.Movies, ShellTab.Series, ShellTab.Discover).forEach { t ->
            NavItem(t.label, selected = t == current) { onTab(t) }
        }
        Spacer(Modifier.weight(1f))
        SearchField(searchOpen, searchQuery, onSearchOpen, onSearchClose, onSearchChange)
        // Synchroniser : recharge l'accueil (Jellyfin + TMDB) avec un tour d'icône.
        val syncTurns by animateFloatAsState(
            targetValue = refreshKey * 360f,
            animationSpec = androidx.compose.animation.core.tween(700, easing = LinearEasing),
        )
        Icon(
            Icons.Filled.Sync,
            contentDescription = "Synchroniser",
            tint = LumenColors.Muted,
            modifier = Modifier.size(22.dp)
                .graphicsLayer { rotationZ = syncTurns }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSync,
                ),
        )
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Paramètres",
            tint = if (current == ShellTab.Settings) LumenColors.OnBackground else LumenColors.Muted,
            modifier = Modifier.size(22.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onTab(ShellTab.Settings) },
        )
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) LumenColors.OnBackground else LumenColors.Muted,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    )
}

/** Champ de recherche qui se déploie en douceur depuis l'icône loupe. */
@Composable
private fun SearchField(
    open: Boolean,
    query: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .animateContentSize(tween(280))
            .background(
                if (open) LumenColors.Surface else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = if (open) 12.dp else 0.dp, vertical = if (open) 8.dp else 0.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = "Rechercher",
            tint = LumenColors.Muted,
            modifier = Modifier.size(20.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (!open) onOpen() },
        )
        if (open) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = LumenColors.OnBackground, fontSize = 14.sp),
                cursorBrush = SolidColor(LumenColors.Accent),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Titres, films, séries…", color = LumenColors.Muted, fontSize = 14.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier.width(240.dp).focusRequester(focusRequester),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "Fermer la recherche",
                tint = LumenColors.Muted,
                modifier = Modifier.size(18.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
            )
        }
    }
}
