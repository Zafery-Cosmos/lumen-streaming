package app.lumen.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.domain.CardItem
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage

/**
 * Carte du Top 10 façon Netflix : un chiffre géant en contour, dont l'affiche
 * vient mordre le bord droit. Le rang se lit d'un coup d'œil, de loin.
 */
@Composable
fun RankedCard(card: CardItem, ctx: CardContext? = null, onClick: () -> Unit) {
    val rank = card.rank ?: return
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )
    val measurer = rememberTextMeasurer()
    // Le « 10 » est deux fois plus large : la rangée reste régulière.
    val digitsWidth = if (rank >= 10) 132.dp else 86.dp

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .height(207.dp)
            .hoverable(interaction)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Le chiffre, dessiné en contour épais façon « stroke ».
        androidx.compose.foundation.Canvas(
            Modifier.width(digitsWidth).height(190.dp),
        ) {
            val text = rank.toString()
            val style = TextStyle(
                fontSize = 190.sp,
                fontWeight = FontWeight.Black,
                color = LumenColors.OnBackground,
            )
            val layout = measurer.measure(text, style)
            // Contour clair + intérieur sombre : lisible sur fond noir.
            drawText(
                textLayoutResult = layout,
                color = LumenColors.Background,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = (size.width - layout.size.width) / 2f,
                    y = size.height - layout.size.height * 0.92f,
                ),
            )
            drawText(
                textLayoutResult = layout,
                color = LumenColors.Muted.copy(alpha = 0.85f),
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = (size.width - layout.size.width) / 2f,
                    y = size.height - layout.size.height * 0.92f,
                ),
                drawStyle = Stroke(width = 3f),
            )
        }

        // L'affiche chevauche légèrement le chiffre.
        Box(
            Modifier
                .offset(x = (-18).dp)
                .width(138.dp)
                .height(207.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LumenColors.Surface),
        ) {
            if (card.posterUrl != null) {
                AsyncImage(
                    model = card.posterUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (hovered) {
                Box(
                    Modifier.align(Alignment.Center).size(40.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Lire",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
