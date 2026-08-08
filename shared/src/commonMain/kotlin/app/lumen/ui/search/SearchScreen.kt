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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.domain.allows
import app.lumen.domain.toCard
import app.lumen.ui.components.MediaCard
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.delay

/**
 * Résultats de recherche dynamiques : la requête est débouncée (350 ms) pour ne
 * pas mitrailler le serveur à chaque frappe. La tolérance aux fautes viendra
 * avec l'index local du L7 — ici c'est la recherche serveur.
 */
@Composable
fun SearchScreen(
    client: JellyfinClient,
    session: StoredSession,
    profile: app.lumen.domain.LocalProfile?,
    query: String,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val results by produceState<List<BaseItem>?>(initialValue = null, query, profile) {
        value = null           // relance l'indicateur pendant la frappe
        delay(350)             // debounce : on attend que la frappe se calme
        value = runCatching {
            client.search(session.baseUrl, session.userId, query).items
                .filter { profile.allows(it) }
        }.getOrDefault(emptyList())
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
                        if (list.isEmpty()) "Aucun résultat pour « $query »"
                        else "Résultats pour « $query »",
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
            }
        }
    }
}
