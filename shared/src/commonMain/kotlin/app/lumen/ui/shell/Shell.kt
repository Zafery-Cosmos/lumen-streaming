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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
fun Shell(client: JellyfinClient, session: StoredSession, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(ShellTab.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(LumenColors.Background)) {
        TopBar(
            current = tab,
            onTab = { tab = it; searchOpen = false; searchQuery = "" },
            searchOpen = searchOpen,
            searchQuery = searchQuery,
            onSearchOpen = { searchOpen = true },
            onSearchClose = { searchOpen = false; searchQuery = "" },
            onSearchChange = { searchQuery = it },
        )

        val showSearch = searchOpen && searchQuery.isNotBlank()
        AnimatedContent(
            targetState = if (showSearch) "search" else tab.name,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 20 })
                    .togetherWith(fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f),
        ) { state ->
            when {
                state == "search" -> SearchScreen(client, session, searchQuery)
                state == ShellTab.Home.name -> HomeScreen(client, session)
                state == ShellTab.Movies.name -> BrowseScreen(client, session, includeTypes = "Movie", title = "Films")
                state == ShellTab.Series.name -> BrowseScreen(client, session, includeTypes = "Series", title = "Séries")
                else -> SettingsScreen(session, onLogout)
            }
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 28.dp),
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
