package app.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lumen.ui.theme.LocalSidePadding
import app.lumen.i18n.T
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/**
 * Rangée horizontale DÉFILABLE, partout dans l'app.
 *
 * Une LazyRow ne réagit pas à la molette verticale : sans flèches, tout ce qui
 * dépasse de l'écran devient inatteignable (saisons au-delà de la 4e, titres
 * similaires, filmographies, catégories d'avatars…). Ce composant règle le
 * problème une fois pour toutes.
 */
@Composable
fun ScrollableRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = LocalSidePadding.current),
    arrowWidth: Dp = 56.dp,
    iconSize: Dp = 34.dp,
    scrimColor: Color = LumenColors.Background,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()

    // Une « page » ≈ 80 % de la largeur visible : on garde un repère visuel.
    fun page(): Float =
        ((state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset) * 0.8f)
            .coerceAtLeast(240f)

    Box(modifier.fillMaxWidth().hoverable(interaction)) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = contentPadding,
            content = content,
        )

        ScrollArrow(
            visible = hovered && state.canScrollBackward,
            alignment = Alignment.CenterStart,
            icon = Icons.Filled.ChevronLeft,
            label = T["scrollableRow.precedent"],
            width = arrowWidth,
            iconSize = iconSize,
            scrimColor = scrimColor,
        ) { scope.launch { state.animateScrollBy(-page()) } }

        ScrollArrow(
            visible = hovered && state.canScrollForward,
            alignment = Alignment.CenterEnd,
            icon = Icons.Filled.ChevronRight,
            label = "Suivant",
            width = arrowWidth,
            iconSize = iconSize,
            scrimColor = scrimColor,
        ) { scope.launch { state.animateScrollBy(page()) } }
    }
}

@Composable
private fun BoxScope.ScrollArrow(
    visible: Boolean,
    alignment: Alignment,
    icon: ImageVector,
    label: String,
    width: Dp,
    iconSize: Dp,
    scrimColor: Color,
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
                .width(width)
                .background(
                    if (alignment == Alignment.CenterStart) {
                        Brush.horizontalGradient(0f to scrimColor, 1f to Color.Transparent)
                    } else {
                        Brush.horizontalGradient(0f to Color.Transparent, 1f to scrimColor)
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(iconSize))
        }
    }
}
