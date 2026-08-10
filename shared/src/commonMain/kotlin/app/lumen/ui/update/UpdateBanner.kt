package app.lumen.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.i18n.T
import app.lumen.ui.theme.LumenColors
import app.lumen.update.DownloadState
import app.lumen.update.LUMEN_VERSION
import app.lumen.update.ReleaseManifest
import app.lumen.update.UpdateClient
import app.lumen.update.applyUpdate
import app.lumen.update.formatEta
import app.lumen.update.formatSize
import app.lumen.update.isNewerVersion
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private enum class Phase { Offered, Downloading, Ready }

/**
 * Bandeau de mise à jour (bas de l'écran) : version, correctifs détaillés,
 * POIDS avant de lancer, puis débit et temps restant pendant le téléchargement.
 * Les publications arrivent EN DIRECT (SSE) — pas besoin de redémarrer l'app.
 */
@Composable
fun UpdateBanner(client: JellyfinClient, compact: Boolean = false) {
    val updates = remember { UpdateClient(client.http) }
    val scope = rememberCoroutineScope()

    val prefs = remember { com.russhwolf.settings.Settings() }
    var manifest by remember { mutableStateOf<ReleaseManifest?>(null) }
    // Persisté : une version ignorée le reste après redémarrage — sinon le
    // bandeau revient à chaque lancement, ce qui est insupportable.
    var dismissedVersion by remember {
        mutableStateOf(prefs.getStringOrNull("update.dismissed"))
    }
    fun dismiss(version: String) {
        dismissedVersion = version
        prefs.putString("update.dismissed", version)
    }
    var phase by remember { mutableStateOf(Phase.Offered) }
    var progress by remember { mutableStateOf<DownloadState?>(null) }
    var downloadedPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Vérification au lancement, puis écoute permanente des publications.
    LaunchedEffect(Unit) {
        updates.latest()?.let { if (isNewerVersion(it.version, LUMEN_VERSION)) manifest = it }
        updates.events()
            .catch { /* serveur injoignable : on retentera au prochain lancement */ }
            .collect { published ->
                if (isNewerVersion(published.version, LUMEN_VERSION)) {
                    manifest = published
                    // Une NOUVELLE version reprend la main même si on avait ignoré.
                    if (dismissedVersion != published.version) {
                        phase = Phase.Offered
                        progress = null
                        downloadedPath = null
                    }
                }
            }
    }

    val current = manifest
    val artifact = current?.artifact
    val visible = current != null && artifact != null &&
        current.platforms.isNotEmpty() && dismissedVersion != current.version

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(450)) { it },
        exit = fadeOut(tween(250)) + slideOutVertically(tween(300)) { it },
    ) {
        if (current == null || artifact == null) return@AnimatedVisibility
        Box(
            Modifier.fillMaxWidth()
                // Sur téléphone, la barre d'onglets vit sous le bandeau : sans
                // cette marge il la recouvre entièrement (rien n'était réservé
                // pour elle, alors que ce bandeau se place PAR-DESSUS tout).
                .then(if (compact) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    horizontal = if (compact) 12.dp else 24.dp,
                    vertical = if (compact) 12.dp else 24.dp,
                )
                .padding(bottom = if (compact) 68.dp else 0.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LumenColors.Surface)
                    .padding(if (compact) 14.dp else 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SystemUpdateAlt,
                        contentDescription = null,
                        tint = LumenColors.Accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            T.format("update.available", current.version),
                            color = LumenColors.OnBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            T.format("update.installed", LUMEN_VERSION),
                            color = LumenColors.Muted,
                            fontSize = 12.sp,
                        )
                    }
                }

                // Les correctifs, détaillés.
                if (current.notes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        current.notes.forEach { note ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•  ", color = LumenColors.Accent, fontSize = 13.sp)
                                Text(note, color = LumenColors.OnBackground.copy(alpha = 0.88f), fontSize = 13.sp)
                            }
                        }
                    }
                }

                when (phase) {
                    Phase.Offered -> {
                        error?.let { Text(it, color = LumenColors.Accent, fontSize = 12.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    phase = Phase.Downloading
                                    error = null
                                    scope.launch {
                                        val path = updates.download(artifact) { progress = it }
                                        if (path != null) {
                                            downloadedPath = path
                                            phase = Phase.Ready
                                        } else {
                                            error = T["update.downloadFailed"]
                                            phase = Phase.Offered
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                // Le POIDS est annoncé AVANT de lancer.
                                Text(T.format("update.update", formatSize(artifact.size)), fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                T["update.ignore"],
                                color = LumenColors.Muted,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { dismiss(current.version) }
                                    .padding(8.dp),
                            )
                        }
                    }

                    Phase.Downloading -> {
                        val p = progress
                        LinearProgressIndicator(
                            progress = { p?.fraction ?: 0f },
                            color = LumenColors.Accent,
                            trackColor = LumenColors.SurfaceHigh,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                buildString {
                                    append("${((p?.fraction ?: 0f) * 100).toInt()} %")
                                    p?.let {
                                        append(" · ${formatSize(it.downloaded)} / ${formatSize(it.total)}")
                                    }
                                },
                                color = LumenColors.Muted,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                buildString {
                                    // Débit et temps restant, en direct.
                                    p?.bytesPerSecond?.takeIf { it > 0 }?.let {
                                        append("${formatSize(it)}/s")
                                    }
                                    p?.etaSeconds?.let { append(" · " + T.format("update.remaining", formatEta(it))) }
                                },
                                color = LumenColors.Muted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Phase.Ready -> {
                        error?.let { Text(it, color = LumenColors.Accent, fontSize = 12.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    // Si tout va bien, applyUpdate FERME l'app :
                                    // on ne repasse ici qu'en cas d'échec.
                                    val ok = downloadedPath?.let { applyUpdate(it) } ?: false
                                    if (!ok) error = T["update.installFailed"]
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(T["update.installRestart"], fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                T["update.later"],
                                color = LumenColors.Muted,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { dismiss(current.version) }
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
