package app.lumen.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.domain.LocalProfile
import app.lumen.domain.ProfileRepository
import app.lumen.db.sha256Hex
import app.lumen.ui.components.Avatars
import app.lumen.ui.components.ProfileAvatar
import app.lumen.ui.theme.LocalSidePadding
import app.lumen.ui.theme.LumenColors
import org.jetbrains.compose.resources.painterResource

/** Gestion des profils du foyer — création, avatar, PIN (haché), enfant. */
@Composable
fun ProfileSettingsScreen(client: app.lumen.api.JellyfinClient, repo: ProfileRepository, onBack: () -> Unit, onProfilesChanged: () -> Unit) {
    var profiles by remember { mutableStateOf(repo.list()) }
    var editing by remember { mutableStateOf<LocalProfile?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun refresh() {
        profiles = repo.list()
        onProfilesChanged()
    }

    Column(
        // Contenu CENTRÉ, cohérent avec le reste des paramètres.
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(LumenColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = LocalSidePadding.current, end = LocalSidePadding.current, top = 96.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = LumenColors.OnBackground,
                modifier = Modifier.size(24.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            )
            Text("Profils", color = LumenColors.OnBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        when {
            creating || editing != null -> ProfileEditor(
                client = client,
                initial = editing,
                onSave = { name, avatar, child, maxAge, newPin, removePin ->
                    val current = editing
                    if (current == null) {
                        repo.add(name, avatar, child, maxAge, newPin)
                    } else {
                        repo.update(
                            current.copy(
                                name = name,
                                avatar = avatar,
                                child = child,
                                maxAge = maxAge,
                                pinHash = when {
                                    removePin -> null
                                    newPin != null -> sha256Hex(newPin)
                                    else -> current.pinHash   // inchangé
                                },
                            ),
                        )
                    }
                    creating = false; editing = null; refresh()
                },
                onDelete = editing?.let { p ->
                    {
                        repo.remove(p.id)
                        creating = false; editing = null; refresh()
                    }
                },
                onCancel = { creating = false; editing = null },
            )
            else -> {
                profiles.forEach { profile ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { editing = profile }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        ProfileAvatar(profile.name, profile.avatar, profile.colorIndex, 40, cornerRadius = 8)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(profile.name, color = LumenColors.OnBackground, fontSize = 15.sp)
                            Text(
                                buildList {
                                    if (profile.child) add("Enfant ${profile.maxAge}+ max")
                                    if (profile.hasPin) add("PIN")
                                }.joinToString(" · ").ifEmpty { "Standard" },
                                color = LumenColors.Muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { creating = true }
                        .padding(vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
                    Text("Ajouter un profil", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Éditeur de profil. Sémantique du PIN à l'édition : champ vide → PIN
 * inchangé ; 4 chiffres → nouveau PIN ; « Retirer le PIN » → déverrouillé.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditor(
    client: app.lumen.api.JellyfinClient,
    initial: LocalProfile?,
    onSave: (name: String, avatar: String?, child: Boolean, maxAge: Int, newPin: String?, removePin: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatar by remember { mutableStateOf(initial?.avatar) }
    var child by remember { mutableStateOf(initial?.child ?: false) }
    var maxAge by remember { mutableStateOf(initial?.maxAge ?: 10) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 640.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nom du profil", color = LumenColors.Muted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LumenColors.Accent,
                unfocusedBorderColor = LumenColors.SurfaceHigh,
                focusedTextColor = LumenColors.OnBackground,
                unfocusedTextColor = LumenColors.OnBackground,
                cursorColor = LumenColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        AvatarPicker(client, avatar) { avatar = it }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Profil enfant", color = LumenColors.OnBackground, fontSize = 15.sp)
                Text(
                    "Masque tout contenu au-dessus de la limite d'âge (et le non-classifié)",
                    color = LumenColors.Muted,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = child,
                onCheckedChange = { child = it },
                colors = SwitchDefaults.colors(checkedTrackColor = LumenColors.Accent),
            )
        }

        if (child) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 7, 10, 13, 16).forEach { age ->
                    val selected = age == maxAge
                    Text(
                        if (age == 0) "Tout public" else "$age+",
                        color = if (selected) Color.Black else LumenColors.OnBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(
                                if (selected) LumenColors.OnBackground else LumenColors.Surface,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { maxAge = age }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) { pin = v; removePin = false } },
                label = {
                    Text(
                        if (initial?.hasPin == true) "Nouveau PIN (vide = inchangé)" else "Code PIN (4 chiffres, vide = aucun)",
                        color = LumenColors.Muted,
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LumenColors.Accent,
                    unfocusedBorderColor = LumenColors.SurfaceHigh,
                    focusedTextColor = LumenColors.OnBackground,
                    unfocusedTextColor = LumenColors.OnBackground,
                    cursorColor = LumenColors.Accent,
                ),
                modifier = Modifier.width(300.dp),
            )
            if (initial?.hasPin == true) {
                Text(
                    if (removePin) "PIN sera retiré" else "Retirer le PIN",
                    color = if (removePin) LumenColors.Muted else LumenColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { removePin = !removePin; if (removePin) pin = "" },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onSave(name.trim(), avatar, child, maxAge, pin.takeIf { it.length == 4 }, removePin) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Enregistrer", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.SurfaceHigh),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Annuler", color = LumenColors.OnBackground)
            }
            onDelete?.let {
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Supprimer le profil",
                    tint = LumenColors.Accent,
                    modifier = Modifier.size(22.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = it,
                    ),
                )
            }
        }
    }
}
