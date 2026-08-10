package app.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.auth.StoredSession
import app.lumen.ui.theme.LocalSidePadding
import app.lumen.i18n.T
import app.lumen.ui.theme.LumenColors

/** Un groupe de paramètres, ouvert en sous-écran. */
data class SettingsGroup(val key: String, val icon: ImageVector, val title: String, val subtitle: String)

/**
 * Paramètres — écran principal : la liste des groupes (plan §6). Chaque groupe
 * s'ouvre en sous-écran, et chaque réglage est réellement branché.
 */
@Composable
fun SettingsScreen(
    session: StoredSession,
    profileName: String?,
    onOpenSub: (String) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val groups = listOf(
        SettingsGroup("display", Icons.Filled.Palette, T["settings.display"], T["settings.displaySub"]),
        SettingsGroup("playback", Icons.Filled.PlayCircle, T["settings.playback"], T["settings.playbackSub"]),
        SettingsGroup("streaming", Icons.Filled.Storage, T["settings.streaming"], T["settings.streamingSub"]),
        SettingsGroup("simkl", Icons.Filled.Sync, T["settings.simkl"], T["settings.simklSub"]),
        SettingsGroup("advanced", Icons.Filled.Tune, T["settings.advanced"], T["settings.advancedSub"]),
        SettingsGroup("quickconnect", Icons.Filled.Key, T["settings.quickconnect"], T["settings.quickconnectSub"]),
        SettingsGroup("server", Icons.Filled.Dns, T["settings.service"], session.serverName.ifEmpty { session.baseUrl }),
    )

    Column(
        // Contenu CENTRÉ dans la page, pas collé à gauche.
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(LumenColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = LocalSidePadding.current, end = LocalSidePadding.current, top = 96.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            T["settings.title"],
            color = LumenColors.OnBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        groups.forEach { group ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenSub(group.key) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Icon(group.icon, contentDescription = null, tint = LumenColors.Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.title, color = LumenColors.OnBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(group.subtitle, color = LumenColors.Muted, fontSize = 12.sp)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = LumenColors.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Raccourci : changer de profil sans passer par le groupe.
        if (profileName != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSwitchProfile,
                    ),
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
                Text(T["profile.switch"], color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
