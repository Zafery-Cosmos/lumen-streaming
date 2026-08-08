package app.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Apparition décalée : glissement + fondu, avec un retard croissant par index. */
@Composable
fun StaggeredReveal(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
    ) {
        content()
    }
}

/**
 * Carte média commune (accueil, grilles, recherche) : scale animé au survol,
 * badge lecture vectoriel, barre de progression si entamé.
 */
@Composable
fun MediaCard(
    client: JellyfinClient,
    session: StoredSession,
    item: BaseItem,
    wide: Boolean = false,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )

    val (width, height, imageType) = if (wide) {
        Triple(248.dp, 140.dp, if (item.imageTags.containsKey("Thumb")) "Thumb" else "Primary")
    } else {
        Triple(138.dp, 207.dp, "Primary")
    }

    Column(
        modifier = Modifier
            .width(width)
            .hoverable(interaction)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.height(height).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(LumenColors.Surface)) {
            AsyncImage(
                model = client.imageUrl(
                    session.baseUrl, item.id, imageType, item.imageTags[imageType], maxWidth = 500,
                ),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val progress = item.userData?.playedPercentage
            if (progress != null && progress > 0) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((progress / 100).toFloat())
                            .background(LumenColors.Accent),
                    )
                }
            }
            if (hovered) {
                Box(
                    Modifier.align(Alignment.Center).size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Lire",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        val label = if (item.type == "Episode") {
            "${item.seriesName ?: item.name} — S${item.parentIndexNumber ?: "?"}E${item.indexNumber ?: "?"}"
        } else item.name
        Text(
            label,
            color = LumenColors.Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
