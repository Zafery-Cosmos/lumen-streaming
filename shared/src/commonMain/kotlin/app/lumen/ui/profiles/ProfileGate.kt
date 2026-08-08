package app.lumen.ui.profiles

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
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
import app.lumen.ui.components.ProfileAvatar
import app.lumen.ui.theme.LumenColors

/**
 * « Qui regarde ? » — sélection du profil local au lancement, avec saisie du
 * code PIN quand le profil est verrouillé. La vérification passe par la base
 * (hash), jamais par une comparaison en clair.
 */
@Composable
fun ProfileGate(
    profiles: List<LocalProfile>,
    verifyPin: (LocalProfile, String) -> Boolean,
    onSelect: (LocalProfile) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
) {
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
                            if (profile.hasPin) pinFor = profile else onSelect(profile)
                        }
                    }
                    // Pastille « + » : créer un profil directement depuis le gate.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAdd,
                        ),
                    ) {
                        Box(
                            Modifier.size(104.dp).background(
                                LumenColors.SurfaceHigh,
                                androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Ajouter un profil",
                                tint = LumenColors.Muted,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        Text("Ajouter", color = LumenColors.Muted, fontSize = 15.sp)
                    }
                }
                Text(
                    "Gérer les profils",
                    color = LumenColors.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onManage,
                        )
                        .padding(8.dp),
                )
            }
            else -> PinPad(
                title = "Code de ${p.name}",
                onCancel = { pinFor = null },
                onSubmit = { entered ->
                    if (verifyPin(p, entered)) {
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
        Box {
            ProfileAvatar(profile.name, profile.avatar, profile.colorIndex, size)
            if (profile.hasPin) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Verrouillé",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(18.dp),
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

/** Pavé numérique de saisie de PIN. */
@Composable
fun PinPad(title: String, onCancel: () -> Unit, onSubmit: (String) -> Boolean) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(title, color = LumenColors.OnBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)

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

        // "DEL" est un sentinel interne — rendu en icône vectorielle, jamais en texte.
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "DEL")).forEach { row ->
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
                                "DEL" -> entered = entered.dropLast(1)
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
                        if (key == "DEL") {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
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
