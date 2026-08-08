package app.lumen.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.api.TmdbClient
import app.lumen.auth.StoredSession
import app.lumen.domain.HomeContent
import app.lumen.domain.HomeRepository
import app.lumen.domain.Rail
import app.lumen.ui.components.HeroCarousel
import app.lumen.ui.components.MediaCard
import app.lumen.ui.theme.LumenColors

/**
 * Accueil éditorial : carrousel hero (films/séries Jellyfin qui défilent),
 * puis rangées Reprendre / À suivre / Nouveautés / Top 10 / genres TMDB.
 * `refreshKey` change quand l'utilisateur presse « Synchroniser » → tout recharge.
 */
@Composable
fun HomeScreen(
    client: JellyfinClient,
    tmdb: TmdbClient,
    session: StoredSession,
    profile: app.lumen.domain.LocalProfile?,
    watchRepo: app.lumen.domain.WatchStateRepository,
    refreshKey: Int,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val repo = remember { HomeRepository(client, tmdb, session, watchRepo) }
    val content by produceState<HomeContent?>(initialValue = null, refreshKey, profile) {
        value = null
        value = repo.load(profile)
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        when (val c = content) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            else -> {
                val ctx = remember { app.lumen.ui.components.CardContext(client, session, onPlay) }
                HomeBody(c, onOpen, onPlay, ctx)
            }
        }
    }
}

@Composable
private fun HomeBody(
    content: HomeContent,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    ctx: app.lumen.ui.components.CardContext,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (content.heroes.isNotEmpty()) {
            item(key = "hero") { HeroCarousel(content.heroes, onOpen, onPlay) }
        }
        // Pas d'apparition différée ici : les rangées doivent TOUJOURS être là
        // quand on scrolle — aucun trou, aucun arrêt de glisse.
        items(content.rails.size, key = { content.rails[it].id }) { index ->
            RailRow(content.rails[index], onOpen, ctx)
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RailRow(rail: Rail, onOpen: (String) -> Unit, ctx: app.lumen.ui.components.CardContext) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            rail.title,
            color = LumenColors.OnBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            items(rail.items, key = { it.id }) { card ->
                MediaCard(card, wide = rail.wide, onClick = { onOpen(card.id) }, ctx = ctx)
            }
        }
    }
}
