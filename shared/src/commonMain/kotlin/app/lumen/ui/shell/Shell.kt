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
    profileStore: app.lumen.domain.ProfileStore,
    onLogout: () -> Unit,
    onSwitchProfile: () -> Unit,
    onProfilesChanged: () -> Unit,
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
    var playingId by remember { mutableStateOf<String?>(null) }
    playingId?.let { id ->
        app.lumen.ui.player.PlayerScreen(
            client, session,
            itemId = id,
            onBack = { playingId = null; refreshKey++ },  // refresh → « Reprendre » à jour
        )
        return
    }

    // La barre est TRANSPARENTE et flotte au-dessus du contenu (style Netflix) :
    // le hero passe dessous, un dégradé assure la lisibilité des onglets.
    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
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
                    onPlay = { id -> playingId = id },
                    onOpen = openDetail,
                )
                state.startsWith("person:") -> app.lumen.ui.person.PersonScreen(
                    tmdb,
                    personName = state.removePrefix("person:"),
                    onOpen = openDetail,
                )
                state == "search" -> SearchScreen(client, session, profile, searchQuery, onOpen = openDetail)
                state == ShellTab.Home.name -> HomeScreen(
                    client, tmdb, session, profile, refreshKey,
                    onOpen = openDetail,
                    onPlay = { id -> playingId = id },
                )
                state == ShellTab.Movies.name -> BrowseScreen(
                    client, session, profile, includeTypes = "Movie", title = "Films",
                    onOpen = openDetail, onPlay = { id -> playingId = id },
                )
                state == ShellTab.Series.name -> BrowseScreen(
                    client, session, profile, includeTypes = "Series", title = "Séries",
                    onOpen = openDetail, onPlay = { id -> playingId = id },
                )
                else -> when (settingsSub) {
                    "profiles" -> app.lumen.ui.profiles.ProfileSettingsScreen(
                        profileStore,
                        onBack = { settingsSub = null },
                        onProfilesChanged = onProfilesChanged,
                    )
                    else -> SettingsScreen(
                        session,
                        profileName = profile?.name,
                        onOpenProfiles = { settingsSub = "profiles" },
                        onSwitchProfile = onSwitchProfile,
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
        listOf(ShellTab.Home, ShellTab.Movies, ShellTab.Series).forEach { t ->
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
