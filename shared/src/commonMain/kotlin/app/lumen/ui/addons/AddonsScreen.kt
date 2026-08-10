package app.lumen.ui.addons

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.ui.theme.LocalSidePadding
import app.lumen.i18n.T
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/**
 * Onglet Addons — un onglet de navigation à part entière plutôt qu'un
 * réglage enfoui : c'est une source de contenu au même titre que Films ou
 * Séries, pas une préférence qu'on configure une fois et qu'on oublie.
 */
@Composable
fun AddonsScreen(client: JellyfinClient) {
    val scope = rememberCoroutineScope()
    val stremio = remember { app.lumen.api.StremioClient(client.http) }
    val store = remember { app.lumen.domain.AddonStore() }
    var addons by remember { mutableStateOf(store.list()) }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(LumenColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = LocalSidePadding.current, end = LocalSidePadding.current, top = 96.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            T["addons.title"],
            color = LumenColors.OnBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth(),
        )
        Text(
            T["addons.intro"],
            color = LumenColors.Muted, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                label = { Text(T["addons.manifestUrl"], color = LumenColors.Muted) },
                singleLine = true,
                colors = app.lumen.ui.settings.fieldColors(),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val installed = store.install(stremio, url)
                        busy = false
                        if (installed != null) {
                            addons = store.list()
                            url = ""
                        } else {
                            error = T["addons.invalid"]
                        }
                    }
                },
                enabled = url.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (busy) T["addons.checking"] else T["addons.install"], fontWeight = FontWeight.SemiBold)
            }
        }
        error?.let { Text(it, color = LumenColors.Accent, fontSize = 13.sp) }

        addons.forEach { addon ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(addon.name, color = LumenColors.OnBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        addon.manifestUrl,
                        color = LumenColors.Muted, fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                // Ouvre la page /configure de l'addon dans le navigateur.
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = T["addons.configure"],
                    tint = LumenColors.Muted,
                    modifier = Modifier.size(18.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        app.lumen.platformOpenUrl(
                            addon.manifestUrl.removeSuffix("/manifest.json") + "/configure",
                        )
                    },
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = addon.enabled,
                    onCheckedChange = { store.toggle(addon.manifestUrl); addons = store.list() },
                    colors = SwitchDefaults.colors(checkedTrackColor = LumenColors.Accent),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = T["addons.remove"],
                    tint = LumenColors.Muted,
                    modifier = Modifier.size(18.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { store.remove(addon.manifestUrl); addons = store.list() },
                )
            }
        }
        if (addons.isEmpty()) {
            Text(T["addons.empty"], color = LumenColors.Muted, fontSize = 13.sp)
        }
    }
}
