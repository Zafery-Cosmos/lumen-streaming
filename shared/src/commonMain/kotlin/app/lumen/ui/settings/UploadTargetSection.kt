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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import app.lumen.domain.UploadTarget
import app.lumen.domain.UploadTargetRepository
import app.lumen.domain.Uploader
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/**
 * Destination d'envoi : le serveur qui héberge la médiathèque. C'est le
 * préalable à l'import d'un dossier HLS — sans elle, le contenu resterait sur
 * la machine de l'utilisateur et ne serait lisible que là.
 */
@Composable
fun UploadTargetSection(repo: UploadTargetRepository) {
    val scope = rememberCoroutineScope()
    var targets by remember { mutableStateOf(repo.list()) }
    var showAdd by remember { mutableStateOf(false) }

    Text(
        "Le serveur où déposer tes dossiers HLS. Une fois envoyés, ils sont lus " +
            "depuis le serveur : ils ne dépendent plus de cet appareil.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    targets.forEach { t ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.config.label, color = LumenColors.OnBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${t.config.kind.uppercase()} · ${t.config.host}:${t.config.port} · ${t.config.remoteDir}",
                    color = LumenColors.Muted, fontSize = 11.sp,
                )
            }
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Retirer",
                tint = LumenColors.Muted,
                modifier = Modifier.size(18.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { repo.remove(t.id); targets = repo.list() },
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
        Text("Ajouter une destination", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    if (showAdd) {
        var label by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf("sftp") }
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("22") }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var remoteDir by remember { mutableStateOf("") }
        var testing by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<Result<String>?>(null) }

        fun current() = UploadTarget(
            label.ifBlank { host }, kind, host, port.toIntOrNull() ?: 22,
            username, password, remoteDir,
        )

        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = LumenColors.Surface,
            title = { Text("Destination d'envoi", color = LumenColors.OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("sftp" to 22, "ftp" to 21).forEach { (k, defaultPort) ->
                            Text(
                                k.uppercase(),
                                color = if (kind == k) LumenColors.Accent else LumenColors.Muted,
                                fontSize = 13.sp,
                                fontWeight = if (kind == k) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .background(
                                        if (kind == k) LumenColors.SurfaceHigh else LumenColors.Surface,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { kind = k; port = defaultPort.toString(); result = null }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Text(
                        "SFTP est ouvert par défaut sur la plupart des NAS et chiffre la " +
                            "connexion — à préférer quand tu as le choix.",
                        color = LumenColors.Muted, fontSize = 11.sp,
                    )
                    DialogField("Nom", label) { label = it }
                    DialogField("Hôte", host) { host = it; result = null }
                    DialogField("Port", port) { port = it.filter(Char::isDigit); result = null }
                    DialogField("Utilisateur", username) { username = it; result = null }
                    DialogField("Mot de passe", password, password = true) { password = it; result = null }
                    DialogField("Dossier distant", remoteDir) { remoteDir = it; result = null }
                    Text(
                        "Le dossier surveillé par ton serveur de médias, par exemple " +
                            "/volume1/Films.",
                        color = LumenColors.Muted, fontSize = 11.sp,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable(
                            enabled = !testing && host.isNotBlank() && remoteDir.isNotBlank(),
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            testing = true
                            result = null
                            scope.launch {
                                result = Uploader().test(current())
                                testing = false
                            }
                        }.padding(vertical = 4.dp),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(14.dp))
                        } else {
                            Text("Tester la connexion", color = LumenColors.Accent, fontSize = 13.sp)
                        }
                        result?.fold(
                            onSuccess = { Text("✓ $it", color = Color(0xFF3ECF6B), fontSize = 12.sp) },
                            onFailure = {
                                Text("✗ ${it.message ?: "échec"}", color = LumenColors.Accent, fontSize = 12.sp)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                val ok = result?.isSuccess == true
                Text(
                    "Enregistrer",
                    color = if (ok) LumenColors.Accent else LumenColors.Muted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        enabled = ok,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        repo.add(current())
                        targets = repo.list()
                        showAdd = false
                    }.padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    "Annuler", color = LumenColors.Muted,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showAdd = false }.padding(8.dp),
                )
            },
        )
    }
}
