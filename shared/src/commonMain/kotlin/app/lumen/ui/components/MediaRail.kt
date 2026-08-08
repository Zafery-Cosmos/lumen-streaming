package app.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.domain.CardItem
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/**
 * Rangée horizontale de médias avec DÉFILEMENT RÉEL : la molette verticale ne
 * fait pas défiler une LazyRow, donc on ajoute des flèches au survol (façon
 * Netflix) qui avancent d'un écran, plus le glisser tactile natif.
 */
@Composable
fun MediaRail(
    title: String,
    cards: List<CardItem>,
    wide: Boolean = false,
    ranked: Boolean = false,
    ctx: CardContext? = null,
    onOpen: (String) -> Unit,
) {
    if (cards.isEmpty()) return
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Une « page » ≈ la largeur visible moins une carte, pour garder un repère.
    fun page(): Int = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset)
        .let { (it * 0.8f).toInt().coerceAtLeast(300) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            color = LumenColors.OnBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        Box(Modifier.fillMaxWidth().hoverable(interaction)) {
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(if (ranked) 26.dp else 10.dp),
                contentPadding = PaddingValues(horizontal = 48.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    if (ranked) {
                        RankedCard(card, ctx = ctx, onClick = { onOpen(card.id) })
                    } else {
                        MediaCard(card, wide = wide, onClick = { onOpen(card.id) }, ctx = ctx)
                    }
                }
            }

            // Flèches : visibles au survol, seulement du côté où il reste à voir.
            ScrollArrow(
                visible = hovered && state.canScrollBackward,
                alignment = Alignment.CenterStart,
                icon = Icons.Filled.ChevronLeft,
                label = "Précédent",
            ) { scope.launch { state.animateScrollBy(-page().toFloat()) } }

            ScrollArrow(
                visible = hovered && state.canScrollForward,
                alignment = Alignment.CenterEnd,
                icon = Icons.Filled.ChevronRight,
                label = "Suivant",
            ) { scope.launch { state.animateScrollBy(page().toFloat()) } }
        }
    }
}

@Composable
private fun BoxScope.ScrollArrow(
    visible: Boolean,
    alignment: Alignment,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.align(alignment).fillMaxHeight(),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(56.dp)
                .background(
                    if (alignment == Alignment.CenterStart) {
                        Brush.horizontalGradient(
                            0f to LumenColors.Background,
                            1f to Color.Transparent,
                        )
                    } else {
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            1f to LumenColors.Background,
                        )
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}
