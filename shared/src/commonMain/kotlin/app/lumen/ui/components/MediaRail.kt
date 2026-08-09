package app.lumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.domain.CardItem
import app.lumen.ui.theme.LocalSidePadding
import app.lumen.ui.theme.LumenColors

/**
 * Rangée de médias titrée, avec défilement réel (flèches au survol) —
 * la mécanique vit dans [ScrollableRow], commune à toute l'app.
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            color = LumenColors.OnBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = LocalSidePadding.current),
        )
        ScrollableRow(
            spacing = if (ranked) 26.dp else 10.dp,
            contentPadding = PaddingValues(horizontal = LocalSidePadding.current),
        ) {
            items(cards, key = { it.id }) { card ->
                if (ranked) {
                    RankedCard(card, ctx = ctx, onClick = { onOpen(card.id) })
                } else {
                    MediaCard(card, wide = wide, onClick = { onOpen(card.id) }, ctx = ctx)
                }
            }
        }
    }
}
