package app.lumen.ui.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.domain.LocalProfile
import app.lumen.ui.theme.LumenColors

/** Couleurs des pastilles de profil — vecteurs purs, pas d'images requises. */
val ProfileColors = listOf(
    Color(0xFFE8443A), Color(0xFF3A7BE8), Color(0xFF2FA96B),
    Color(0xFFE89A3A), Color(0xFF9A55D6), Color(0xFF2AA6B5),
)

/**
 * « Qui regarde ? » — sélection du profil local au lancement, avec saisie du
 * code PIN quand le profil est verrouillé (parent) ou protégé.
 */
@Composable
fun ProfileGate(profiles: List<LocalProfile>, onSelect: (LocalProfile) -> Unit) {
    var pinFor by remember { mutableStateOf<LocalProfile?>(null) }

    Box(Modifier.fillMaxSize().background(LumenColors.Background), contentAlignment = Alignment.Center) {
        when (val p = pinFor) {
            null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Text(
                    "Qui regarde ?",
                    color = LumenColors.OnBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    profiles.forEach { profile ->
                        ProfileBadge(profile) {
                            if (profile.pin != null) pinFor = profile else onSelect(profile)
                        }
                    }
                }
            }
            else -> PinPad(
                title = "Code de ${p.name}",
                onCancel = { pinFor = null },
                onSubmit = { entered ->
                    if (entered == p.pin) {
                        pinFor = null
                        onSelect(p)
                        true
                    } else false
                },
            )
        }
    }
}

@Composable
fun ProfileBadge(profile: LocalProfile, size: Int = 104, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            Modifier.size(size.dp).background(
                ProfileColors[profile.colorIndex % ProfileColors.size],
                RoundedCornerShape(16.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.name.take(1).uppercase(),
                color = Color.White,
                fontSize = (size * 0.42).sp,
                fontWeight = FontWeight.Black,
            )
            if (profile.pin != null) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Verrouillé",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(18.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(profile.name, color = LumenColors.OnBackground, fontSize = 15.sp)
            if (profile.child) {
                Text("Enfant · ${profile.maxAge}+ max", color = LumenColors.Muted, fontSize = 11.sp)
            }
        }
    }
}

/** Pavé numérique de saisie de PIN — vibre visuellement en cas d'erreur. */
@Composable
fun PinPad(title: String, onCancel: () -> Unit, onSubmit: (String) -> Boolean) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(title, color = LumenColors.OnBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        // Les 4 points du code.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(16.dp).background(
                        when {
                            error -> LumenColors.Accent
                            i < entered.length -> LumenColors.OnBackground
                            else -> LumenColors.SurfaceHigh
                        },
                        CircleShape,
                    ),
                )
            }
        }
        if (error) {
            Text("Code incorrect", color = LumenColors.Accent, fontSize = 13.sp)
        }

        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier.size(64.dp).background(
                            if (key.isEmpty()) Color.Transparent else LumenColors.Surface,
                            CircleShape,
                        ).clickable(
                            enabled = key.isNotEmpty(),
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            error = false
                            when (key) {
                                "⌫" -> entered = entered.dropLast(1)
                                else -> if (entered.length < 4) {
                                    entered += key
                                    if (entered.length == 4 && !onSubmit(entered)) {
                                        error = true
                                        entered = ""
                                    }
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") {
                            Icon(
                                Icons.Filled.Backspace,
                                contentDescription = "Effacer",
                                tint = LumenColors.Muted,
                                modifier = Modifier.size(22.dp),
                            )
                        } else if (key.isNotEmpty()) {
                            Text(key, color = LumenColors.OnBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Text(
            "Annuler",
            color = LumenColors.Muted,
            fontSize = 14.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        )
    }
}
