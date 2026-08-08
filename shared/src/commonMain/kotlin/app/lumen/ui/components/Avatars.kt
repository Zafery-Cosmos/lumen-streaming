package app.lumen.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.resources.Res
import app.lumen.resources.avatar_01
import app.lumen.resources.avatar_02
import app.lumen.resources.avatar_03
import app.lumen.resources.avatar_04
import app.lumen.resources.avatar_05
import app.lumen.resources.avatar_06
import app.lumen.resources.avatar_07
import app.lumen.resources.avatar_08
import app.lumen.resources.avatar_09
import app.lumen.resources.avatar_10
import app.lumen.resources.avatar_11
import app.lumen.resources.avatar_12
import app.lumen.resources.avatar_13
import app.lumen.resources.avatar_14
import app.lumen.resources.avatar_15
import app.lumen.resources.avatar_16
import app.lumen.resources.avatar_17
import app.lumen.resources.avatar_18
import app.lumen.resources.avatar_19
import app.lumen.resources.avatar_20
import app.lumen.resources.avatar_21
import app.lumen.resources.avatar_22
import app.lumen.resources.avatar_23
import app.lumen.resources.avatar_24
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Couleurs de secours des pastilles (profil sans avatar choisi). */
val ProfileColors = listOf(
    Color(0xFFE8443A), Color(0xFF3A7BE8), Color(0xFF2FA96B),
    Color(0xFFE89A3A), Color(0xFF9A55D6), Color(0xFF2AA6B5),
)

/** La banque d'avatars embarquée (sélection ; la banque complète viendra du NAS). */
val Avatars: List<Pair<String, DrawableResource>> = listOf(
    "avatar_01" to Res.drawable.avatar_01, "avatar_02" to Res.drawable.avatar_02,
    "avatar_03" to Res.drawable.avatar_03, "avatar_04" to Res.drawable.avatar_04,
    "avatar_05" to Res.drawable.avatar_05, "avatar_06" to Res.drawable.avatar_06,
    "avatar_07" to Res.drawable.avatar_07, "avatar_08" to Res.drawable.avatar_08,
    "avatar_09" to Res.drawable.avatar_09, "avatar_10" to Res.drawable.avatar_10,
    "avatar_11" to Res.drawable.avatar_11, "avatar_12" to Res.drawable.avatar_12,
    "avatar_13" to Res.drawable.avatar_13, "avatar_14" to Res.drawable.avatar_14,
    "avatar_15" to Res.drawable.avatar_15, "avatar_16" to Res.drawable.avatar_16,
    "avatar_17" to Res.drawable.avatar_17, "avatar_18" to Res.drawable.avatar_18,
    "avatar_19" to Res.drawable.avatar_19, "avatar_20" to Res.drawable.avatar_20,
    "avatar_21" to Res.drawable.avatar_21, "avatar_22" to Res.drawable.avatar_22,
    "avatar_23" to Res.drawable.avatar_23, "avatar_24" to Res.drawable.avatar_24,
)

fun avatarResource(name: String?): DrawableResource? =
    Avatars.firstOrNull { it.first == name }?.second

/** Pastille de profil : avatar de la banque, ou initiale colorée en secours. */
@Composable
fun ProfileAvatar(
    name: String,
    avatar: String?,
    colorIndex: Int,
    size: Int,
    cornerRadius: Int = 16,
) {
    val res = avatarResource(avatar)
    // Un avatar de la banque NAS est un chemin « /avatars/... » ; les anciens
    // profils pointent encore sur une ressource embarquée.
    val remote = avatar?.takeIf { it.startsWith("/avatars/") || it.startsWith("http") }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(cornerRadius.dp)).background(
            if (res == null && remote == null) {
                ProfileColors[colorIndex % ProfileColors.size]
            } else Color.Transparent,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (remote != null) {
            coil3.compose.AsyncImage(
                model = if (remote.startsWith("http")) remote else "${app.lumen.update.UPDATE_SERVER}$remote",
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (res != null) {
            Image(
                painter = painterResource(res),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                name.take(1).uppercase(),
                color = Color.White,
                fontSize = (size * 0.42).sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
