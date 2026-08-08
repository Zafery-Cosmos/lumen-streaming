package app.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.auth.StoredSession
import app.lumen.ui.theme.LumenColors

/**
 * Paramètres — squelette. L'arborescence complète (parité Jellyfin §6.1 + nos
 * ajouts §6.2) arrive au L10 ; pour l'instant : infos de session et déconnexion.
 * C'est LE SEUL endroit de l'app où « Se déconnecter » existe.
 */
@Composable
fun SettingsScreen(
    session: StoredSession,
    profileName: String?,
    onOpenProfiles: () -> Unit,
    onSwitchProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(LumenColors.Background)
            .padding(start = 48.dp, end = 48.dp, top = 96.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Paramètres",
            color = LumenColors.OnBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )

        SettingRow(
            icon = { Icon(Icons.Filled.Person, null, tint = LumenColors.Muted, modifier = Modifier.size(20.dp)) },
            title = "Profil",
            value = session.userName,
        )
        SettingRow(
            icon = { Icon(Icons.Filled.Dns, null, tint = LumenColors.Muted, modifier = Modifier.size(20.dp)) },
            title = "Serveur",
            value = session.serverName.ifEmpty { session.baseUrl },
        )
        SettingRow(
            icon = { Icon(Icons.Filled.Group, null, tint = LumenColors.Muted, modifier = Modifier.size(20.dp)) },
            title = "Profils du foyer",
            value = profileName ?: "Aucun profil",
            onClick = onOpenProfiles,
        )
        if (profileName != null) {
            SettingRow(
                icon = { Icon(Icons.Filled.SwapHoriz, null, tint = LumenColors.Muted, modifier = Modifier.size(20.dp)) },
                title = "Changer de profil",
                value = null,
                onClick = onSwitchProfile,
            )
        }
        SettingRow(
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Logout, null,
                    tint = LumenColors.Accent, modifier = Modifier.size(20.dp),
                )
            },
            title = "Se déconnecter",
            value = null,
            titleColor = LumenColors.Accent,
            onClick = onLogout,
        )
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String?,
    titleColor: androidx.compose.ui.graphics.Color = LumenColors.OnBackground,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        value?.let { Text(it, color = LumenColors.Muted, fontSize = 14.sp) }
    }
}
