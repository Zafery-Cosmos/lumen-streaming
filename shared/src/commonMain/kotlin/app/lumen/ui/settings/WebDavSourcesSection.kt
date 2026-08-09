package app.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.domain.PlayRequest
import app.lumen.domain.WebDavClient
import app.lumen.domain.WebDavConfig
import app.lumen.domain.WebDavEntry
import app.lumen.domain.WebDavSource
import app.lumen.domain.WebDavSourceRepository
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/** Partages WebDAV perso : dossier distant, parcouru et lu en HTTP authentifié. */
@Composable
fun WebDavSourcesSection(repo: WebDavSourceRepository, onPlay: (PlayRequest) -> Unit) {
    var sources by remember { mutableStateOf(repo.list()) }
    var showAdd by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf<WebDavSource?>(null) }

    Text(
        "Un partage WebDAV (Nextcloud, Synology, mod_dav…) : Lumen le parcourt " +
            "comme un dossier distant.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    sources.forEach { s ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { browsing = s }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.config.label, color = LumenColors.OnBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(s.config.baseUrl, color = LumenColors.Muted, fontSize = 11.sp)
            }
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Retirer",
                tint = LumenColors.Muted,
                modifier = Modifier.size(18.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    repo.remove(s.id)
                    sources = repo.list()
                    if (browsing?.id == s.id) browsing = null
                },
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { showAdd = true }.padding(vertical = 6.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
        Text("Ajouter un partage WebDAV", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    if (showAdd) {
        AddWebDavDialog(
            onDismiss = { showAdd = false },
            onSave = { cfg -> repo.add(cfg); sources = repo.list(); showAdd = false },
        )
    }

    browsing?.let { s ->
        WebDavBrowserDialog(config = s.config, onPlay = onPlay, onDismiss = { browsing = null })
    }
}

@Composable
private fun WebDavBrowserDialog(config: WebDavConfig, onPlay: (PlayRequest) -> Unit, onDismiss: () -> Unit) {
    val client = remember { WebDavClient() }
    var path by remember { mutableStateOf(client.rootPath(config)) }
    var entries by remember { mutableStateOf<List<WebDavEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        loading = true
        error = null
        client.list(config, path).fold(
            onSuccess = { entries = it },
            onFailure = { error = it.message ?: "Échec de connexion" },
        )
        loading = false
    }

    val rootPath = remember(config) { client.rootPath(config) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.Surface,
        title = { Text(config.label, color = LumenColors.OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (path.trimEnd('/') != rootPath.trimEnd('/')) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Remonter",
                            tint = LumenColors.Accent,
                            modifier = Modifier.size(18.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                path = path.trimEnd('/').substringBeforeLast('/', "") + "/"
                            },
                        )
                    }
                    Text(path, color = LumenColors.Muted, fontSize = 11.sp, maxLines = 1)
                }
                when {
                    loading -> CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(24.dp))
                    error != null -> Text(error ?: "", color = LumenColors.Accent, fontSize = 13.sp)
                    entries.isEmpty() -> Text("Dossier vide", color = LumenColors.Muted, fontSize = 13.sp)
                    else -> Column {
                        entries.forEach { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        if (entry.isDirectory) {
                                            path = entry.path
                                        } else {
                                            onPlay(
                                                PlayRequest(
                                                    url = client.streamUrl(config, entry.path),
                                                    headers = mapOf("Authorization" to client.basicAuthHeader(config)),
                                                    title = entry.name,
                                                ),
                                            )
                                            onDismiss()
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Icon(
                                    if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = LumenColors.Muted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(entry.name, color = LumenColors.OnBackground, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "Fermer", color = LumenColors.Accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ).padding(8.dp),
            )
        },
    )
}

@Composable
private fun AddWebDavDialog(onDismiss: () -> Unit, onSave: (WebDavConfig) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.Surface,
        title = { Text("Ajouter un partage WebDAV", color = LumenColors.OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogField("Nom", label) { label = it }
                DialogField("URL (https://…/dav/…)", baseUrl) { baseUrl = it; testResult = null }
                DialogField("Utilisateur", username) { username = it; testResult = null }
                DialogField("Mot de passe", password, password = true) { password = it; testResult = null }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable(
                        enabled = baseUrl.isNotBlank() && !testing,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        testing = true
                        testResult = null
                        val cfg = WebDavConfig(label.ifBlank { "Test" }, baseUrl, username, password)
                        scope.launch {
                            val client = WebDavClient()
                            testResult = client.list(cfg, client.rootPath(cfg)).isSuccess
                            testing = false
                        }
                    }.padding(vertical = 4.dp),
                ) {
                    if (testing) {
                        CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(14.dp))
                    } else {
                        Text("Tester la connexion", color = LumenColors.Accent, fontSize = 13.sp)
                    }
                    testResult?.let {
                        Text(
                            if (it) "✓ connecté" else "✗ échec",
                            color = if (it) androidx.compose.ui.graphics.Color(0xFF3ECF6B) else LumenColors.Accent,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            val valid = label.isNotBlank() && baseUrl.isNotBlank()
            Text(
                "Enregistrer",
                color = if (valid) LumenColors.Accent else LumenColors.Muted,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    enabled = valid,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSave(WebDavConfig(label, baseUrl, username, password)) }.padding(8.dp),
            )
        },
        dismissButton = {
            Text(
                "Annuler", color = LumenColors.Muted,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ).padding(8.dp),
            )
        },
    )
}
