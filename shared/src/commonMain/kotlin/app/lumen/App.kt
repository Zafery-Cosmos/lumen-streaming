package app.lumen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.ui.theme.LumenColors
import app.lumen.ui.theme.LumenTheme

/**
 * Racine de l'UI partagée. Pour l'instant (L0) : simple écran de marque,
 * qui prouve que le pipeline Compose fonctionne sur Android et desktop.
 * L1 le remplacera par l'écran de connexion au serveur.
 */
@Composable
fun App() {
    LumenTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(LumenColors.Background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "LUMEN",
                    color = LumenColors.OnBackground,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 14.sp,
                )
                Text(
                    text = "votre médiathèque, en mieux",
                    color = LumenColors.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 14.dp), // compense le letterSpacing du titre
                )
            }
        }
    }
}
