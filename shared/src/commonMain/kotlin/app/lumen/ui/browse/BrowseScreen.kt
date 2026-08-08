package app.lumen.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.ui.components.MediaCard
import app.lumen.ui.theme.LumenColors

/**
 * Grille d'une catégorie (Films, Séries). Les filtres avancés et la pagination
 * infinie arrivent au L7 — ici : tri alphabétique, 200 premiers items.
 */
@Composable
fun BrowseScreen(
    client: JellyfinClient,
    session: StoredSession,
    includeTypes: String,
    title: String,
) {
    val items by produceState<List<BaseItem>?>(initialValue = null, includeTypes) {
        value = runCatching {
            client.items(
                session.baseUrl, session.userId,
                includeTypes = includeTypes,
                limit = 200,
            ).items
        }.getOrDefault(emptyList())
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        when (val list = items) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        title,
                        color = LumenColors.OnBackground,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(list, key = { it.id }) { item ->
                    MediaCard(client, session, item)
                }
                if (list.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text("Rien ici pour l'instant.", color = LumenColors.Muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
