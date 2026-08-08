package app.lumen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.YouTubeClient
import app.lumen.domain.PlayRequest
import app.lumen.domain.TrailerPicker
import app.lumen.player.resolveYouTubeStream
import app.lumen.ui.theme.LumenColors
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

/**
 * Bouton « Bande-annonce », en version française doublée.
 *
 * Le flux est résolu et lu par le moteur natif : aucun lecteur YouTube
 * embarqué, donc pas de publicité, pas de suivi, et la même barre de contrôle
 * que le reste de l'app.
 *
 * Si aucune VF n'existe, on le dit plutôt que de servir une VOSTFR à sa place.
 */
@Composable
fun TrailerButton(
    http: HttpClient,
    title: String,
    year: Int?,
    isSeries: Boolean,
    onPlay: (PlayRequest) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    message = null
                    val youtube = YouTubeClient(http)
                    val results = youtube.search(TrailerPicker.query(title, year, isSeries))
                    val pick = TrailerPicker.best(results, title, year)
                    if (pick == null) {
                        busy = false
                        message = if (results.isEmpty()) {
                            "Recherche YouTube indisponible"
                        } else {
                            "Aucune bande-annonce VF trouvée (les versions " +
                                "sous-titrées sont volontairement écartées)"
                        }
                        return@launch
                    }
                    val stream = resolveYouTubeStream(pick.video.id)
                    busy = false
                    if (stream == null) {
                        message = "Lecture des bandes-annonces indisponible sur cette plateforme"
                        return@launch
                    }
                    onPlay(
                        PlayRequest(
                            url = stream.videoUrl,
                            audioSlaveUrl = stream.audioUrl,
                            title = "$title — bande-annonce VF",
                            headers = mapOf("User-Agent" to YouTubeClient.DESKTOP_UA),
                            isTrailer = true,
                        ),
                    )
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = LumenColors.SurfaceHigh.copy(alpha = 0.75f),
                disabledContainerColor = LumenColors.SurfaceHigh.copy(alpha = 0.45f),
            ),
            shape = RoundedCornerShape(6.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = LumenColors.OnBackground,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    Icons.Filled.Theaters,
                    contentDescription = null,
                    tint = LumenColors.OnBackground,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (busy) "Recherche de la VF…" else "Bande-annonce",
                color = LumenColors.OnBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                message.orEmpty(),
                color = LumenColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.width(420.dp),
            )
        }
    }
}
