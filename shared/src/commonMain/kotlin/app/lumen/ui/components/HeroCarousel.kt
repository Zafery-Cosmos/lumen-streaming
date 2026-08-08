package app.lumen.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.domain.HeroItem
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Carrousel hero partagé (accueil, Films, Séries) : défile tout seul (8 s),
 * fondu entre les titres, points de position cliquables.
 * `rounded` → version « billboard » à coins arrondis pour les pages de grille.
 */
@Composable
fun HeroCarousel(
    heroes: List<HeroItem>,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
    rounded: Boolean = false,
) {
    if (heroes.isEmpty()) return
    var index by remember { mutableStateOf(0) }

    // Rotation automatique — intervalle réglable, coupée si « animations réduites ».
    LaunchedEffect(heroes.size, app.lumen.domain.AppSettings.reducedMotion.value) {
        if (app.lumen.domain.AppSettings.reducedMotion.value) return@LaunchedEffect
        while (true) {
            delay(app.lumen.domain.AppSettings.heroIntervalSec.value.coerceAtLeast(2) * 1000L)
            index = (index + 1) % heroes.size
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 8f)
            .clip(if (rounded) RoundedCornerShape(18.dp) else RectangleShape),
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(tween(700)).togetherWith(fadeOut(tween(700))) },
        ) { i ->
            HeroSlide(heroes[i], onOpen, onPlay)
        }

    }
}

@Composable
private fun HeroSlide(hero: HeroItem, onOpen: (String) -> Unit, onPlay: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = hero.backdropUrl,
            contentDescription = hero.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to LumenColors.Background,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to LumenColors.Background.copy(alpha = 0.85f),
                    0.45f to Color.Transparent,
                ),
            ),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (hero.logoUrl != null) {
                AsyncImage(
                    model = hero.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.height(90.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                )
            } else {
                Text(
                    hero.title,
                    color = LumenColors.OnBackground,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                hero.year?.let { MetaChip(it.toString()) }
                hero.runtimeMinutes?.let { MetaChip("${it / 60}h${(it % 60).toString().padStart(2, '0')}") }
                hero.rating?.let { MetaChip(it) }
            }
            hero.overview?.let {
                Text(
                    it,
                    color = LumenColors.OnBackground.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(520.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onPlay(hero.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lire", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onOpen("jf:${hero.id}") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LumenColors.SurfaceHigh.copy(alpha = 0.7f),
                    ),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = LumenColors.OnBackground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Plus d'infos", color = LumenColors.OnBackground, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text,
        color = LumenColors.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}
