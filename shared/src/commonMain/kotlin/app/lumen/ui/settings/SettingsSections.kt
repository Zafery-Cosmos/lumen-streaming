package app.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession
import app.lumen.domain.AppSettings
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/** Sous-écran d'un groupe de paramètres — routé par sa clé. */
@Composable
fun SettingsSectionScreen(
    sectionKey: String,
    client: JellyfinClient,
    session: StoredSession,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    SectionScaffold(
        title = when (sectionKey) {
            "display" -> "Affichage"
            "home" -> "Accueil"
            "playback" -> "Lecture"
            "quality" -> "Qualité et réseau"
            "audio" -> "Audio et sous-titres"
            "quickconnect" -> "Connexion rapide"
            "server" -> "Serveur"
            else -> "Paramètres"
        },
        onBack = onBack,
    ) {
        when (sectionKey) {
            "display" -> DisplaySection()
            "home" -> HomeSection()
            "playback" -> PlaybackSection()
            "quality" -> QualitySection()
            "audio" -> AudioSection()
            "quickconnect" -> QuickConnectSection(client, session)
            "server" -> ServerSection(session, onLogout)
        }
    }
}

// --- Sections ---------------------------------------------------------------

@Composable
private fun DisplaySection() {
    SwitchRow(
        title = "Noir pur (OLED)",
        description = "Fond entièrement noir — économise les écrans OLED. Appliqué immédiatement.",
        checked = AppSettings.oledBlack.value,
        onChecked = { AppSettings.oledBlack.set(it) },
    )
}

@Composable
private fun HomeSection() {
    Text(
        "Choisis les rangées visibles sur l'accueil.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )
    SwitchRow("Reprendre la lecture", null, AppSettings.showResume.value) { AppSettings.showResume.set(it) }
    SwitchRow("À suivre", null, AppSettings.showNextUp.value) { AppSettings.showNextUp.set(it) }
    SwitchRow("Nouveautés (votre médiathèque)", null, AppSettings.showRecent.value) { AppSettings.showRecent.set(it) }
    SwitchRow("Top 10 cette semaine (TMDB)", null, AppSettings.showTop10.value) { AppSettings.showTop10.set(it) }
    SwitchRow("Rangées par genre (TMDB)", null, AppSettings.showGenres.value) { AppSettings.showGenres.set(it) }
}

@Composable
private fun PlaybackSection() {
    SwitchRow(
        title = "Reprendre automatiquement",
        description = "Sinon, la lecture repart toujours du début.",
        checked = AppSettings.resumeAlways.value,
        onChecked = { AppSettings.resumeAlways.set(it) },
    )
    ChoiceRow(
        title = "Durée du saut avant/arrière",
        options = listOf("5 s" to 5, "10 s" to 10, "30 s" to 30, "60 s" to 60),
        selected = AppSettings.seekStepSec.value,
        onSelect = { AppSettings.seekStepSec.set(it) },
    )
    SwitchRow(
        title = "Épisode suivant automatique",
        description = "Enchaîne la lecture en fin d'épisode (arrive avec l'enchaînement, L12).",
        checked = AppSettings.autoPlayNext.value,
        onChecked = { AppSettings.autoPlayNext.set(it) },
    )
}

@Composable
private fun QualitySection() {
    ChoiceRow(
        title = "Plafond de débit par défaut",
        options = listOf(
            "Auto" to 0L,
            "20 Mbps" to 20_000_000L,
            "8 Mbps" to 8_000_000L,
            "4 Mbps" to 4_000_000L,
            "2 Mbps" to 2_000_000L,
        ),
        selected = AppSettings.defaultMaxBitrate.value,
        onSelect = { AppSettings.defaultMaxBitrate.set(it) },
    )
    Text(
        "S'applique au lancement de chaque lecture ; modifiable en cours de " +
            "lecture dans les options du lecteur.",
        color = LumenColors.Muted, fontSize = 12.sp,
    )
}

@Composable
private fun AudioSection() {
    ChoiceRow(
        title = "Langue audio préférée",
        options = listOf("Automatique" to "auto", "Français" to "fr", "Anglais (VO)" to "en"),
        selected = AppSettings.preferredAudioLang.value,
        onSelect = { AppSettings.preferredAudioLang.set(it) },
    )
    ChoiceRow(
        title = "Sous-titres préférés",
        options = listOf("Désactivés" to "off", "Français" to "fr", "Anglais" to "en"),
        selected = AppSettings.preferredSubLang.value,
        onSelect = { AppSettings.preferredSubLang.set(it) },
    )
    Text(
        "La piste correspondante est sélectionnée automatiquement au lancement " +
            "de la lecture quand elle existe.",
        color = LumenColors.Muted, fontSize = 12.sp,
    )
}

@Composable
private fun QuickConnectSection(client: JellyfinClient, session: StoredSession) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }

    Text(
        "Saisis le code affiché par un autre appareil (TV, téléphone) pour " +
            "l'autoriser sur ton compte, sans taper le mot de passe.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) { code = v; result = null } },
            label = { Text("Code à 6 chiffres", color = LumenColors.Muted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LumenColors.Accent,
                unfocusedBorderColor = LumenColors.SurfaceHigh,
                focusedTextColor = LumenColors.OnBackground,
                unfocusedTextColor = LumenColors.OnBackground,
                cursorColor = LumenColors.Accent,
            ),
            modifier = Modifier.width(220.dp),
        )
        Button(
            onClick = {
                scope.launch { result = client.quickConnectAuthorize(session.baseUrl, code) }
            },
            enabled = code.length == 6,
            colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Autoriser", fontWeight = FontWeight.SemiBold)
        }
    }
    result?.let {
        Text(
            if (it) "Appareil autorisé — il est en train de se connecter." else "Code refusé par le serveur.",
            color = if (it) LumenColors.Muted else LumenColors.Accent,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ServerSection(session: StoredSession, onLogout: () -> Unit) {
    InfoRow("Serveur", session.serverName.ifEmpty { session.baseUrl })
    InfoRow("Adresse", session.baseUrl)
    InfoRow("Compte", session.userName)
    Spacer(Modifier.size(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onLogout,
        ),
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
        Text("Se déconnecter du serveur", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// --- Composants communs ------------------------------------------------------

@Composable
private fun SectionScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(LumenColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 48.dp, end = 48.dp, top = 96.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            Text(title, color = LumenColors.OnBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 620.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LumenColors.OnBackground, fontSize = 15.sp)
            description?.let { Text(it, color = LumenColors.Muted, fontSize = 12.sp) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = LumenColors.Accent),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(title, color = LumenColors.OnBackground, fontSize = 15.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, value) ->
                val isSel = value == selected
                Text(
                    label,
                    color = if (isSel) Color.Black else LumenColors.OnBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            if (isSel) LumenColors.OnBackground else LumenColors.SurfaceHigh,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(title, color = LumenColors.OnBackground, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = LumenColors.Muted, fontSize = 13.sp)
    }
}
