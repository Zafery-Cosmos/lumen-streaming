package app.lumen.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.TmdbClient
import app.lumen.api.TmdbPersonDetail
import app.lumen.domain.toCard
import app.lumen.ui.components.MediaCard
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage

/**
 * Page acteur/réalisateur : photo, bio, infos, et toute la filmographie TMDB
 * en rangées cliquables. Ouverte par nom depuis les fiches (« person:<nom> »).
 */
@Composable
fun PersonScreen(
    tmdb: TmdbClient,
    personName: String,
    onOpen: (String) -> Unit,
) {
    val detail by produceState<TmdbPersonDetail?>(initialValue = null, personName) {
        value = runCatching {
            val ref = tmdb.searchPerson(personName).results.firstOrNull() ?: return@runCatching null
            tmdb.person(ref.id)
        }.getOrNull()
    }

    Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
        when (val d = detail) {
            null -> CircularProgressIndicator(
                color = LumenColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            else -> PersonBody(d, onOpen)
        }
    }
}

@Composable
private fun PersonBody(person: TmdbPersonDetail, onOpen: (String) -> Unit) {
    // Filmographie triée par popularité, dédupliquée, séparée films/séries.
    val credits = person.combinedCredits?.cast.orEmpty().distinctBy { it.id }
    val movies = credits.filter { it.mediaType == "movie" && it.posterPath != null }
    val shows = credits.filter { it.mediaType == "tv" && it.posterPath != null }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 96.dp, bottom = 32.dp),
    ) {
        item(key = "header") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.padding(horizontal = 48.dp),
            ) {
                Box(
                    Modifier.width(180.dp).height(270.dp).clip(RoundedCornerShape(14.dp))
                        .background(LumenColors.SurfaceHigh),
                ) {
                    TmdbClient.posterUrl(person.profilePath)?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        person.name,
                        color = LumenColors.OnBackground,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        person.birthday?.let {
                            Text("Né(e) le $it", color = LumenColors.Muted, fontSize = 13.sp)
                        }
                        person.placeOfBirth?.let {
                            Text(it, color = LumenColors.Muted, fontSize = 13.sp)
                        }
                    }
                    person.biography?.takeIf { it.isNotBlank() }?.let { bio ->
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            bio,
                            color = LumenColors.OnBackground.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                                .clickable { expanded = !expanded },
                        )
                    }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item(key = "movies") {
                FilmographyRail("Films", movies.map { it.toCard() }, onOpen)
            }
        }
        if (shows.isNotEmpty()) {
            item(key = "shows") {
                FilmographyRail("Séries", shows.map { it.toCard() }, onOpen)
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun FilmographyRail(title: String, cards: List<app.lumen.domain.CardItem>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            color = LumenColors.OnBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                MediaCard(card, onClick = { onOpen(card.id) })
            }
        }
    }
}
