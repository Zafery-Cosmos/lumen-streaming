package app.lumen.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.TmdbClient
import app.lumen.api.TmdbItem
import app.lumen.domain.HlsAnalysis
import app.lumen.domain.HlsAnalyzer
import app.lumen.domain.HlsLibraryRepository
import app.lumen.findHlsMaster
import app.lumen.parentFolderName
import app.lumen.pickDirectory
import app.lumen.readLocalText
import app.lumen.resolveSibling
import app.lumen.i18n.T
import app.lumen.ui.theme.LumenColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import d'un dossier HLS déjà transcodé (master.m3u8 + segments .ts ou fMP4).
 *
 * Rien n'est ré-encodé : on analyse le manifeste, on rapproche le titre de
 * TMDB (corrigeable à la main), et on annonce AVANT l'import si le contenu
 * sera lisible tel quel.
 */
@Composable
fun HlsImportSection(
    tmdb: TmdbClient,
    repo: HlsLibraryRepository,
    targets: app.lumen.domain.UploadTargetRepository,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val destinations = targets.list()
    var targetId by remember { mutableStateOf(destinations.firstOrNull()?.id.orEmpty()) }
    var upload by remember { mutableStateOf<app.lumen.domain.UploadProgress?>(null) }
    var entries by remember { mutableStateOf(repo.list()) }
    var analysis by remember { mutableStateOf<HlsAnalysis?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Rapprochement TMDB, corrigeable.
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var chosen by remember { mutableStateOf<TmdbItem?>(null) }

    fun reset() {
        analysis = null; query = ""; matches = emptyList(); chosen = null; error = null
    }

    Text(
        T["hlsImport.importeUnDossierContenantUnMaster"],
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    // --- Étape 0 : où déposer ? -------------------------------------------
    if (destinations.isEmpty()) {
        Text(
            T["hlsImport.aucuneDestinationConfigureeAjouteDAbord"],
            color = LumenColors.Accent, fontSize = 12.sp,
        )
    } else {
        Text(T["hlsImport.deposerSur"], color = LumenColors.OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            destinations.forEach { d ->
                val selected = d.id == targetId
                Text(
                    d.config.label,
                    color = if (selected) LumenColors.Accent else LumenColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            if (selected) LumenColors.SurfaceHigh else LumenColors.Surface,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { targetId = d.id }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }

    // Progression de l'envoi — on annonce le fichier en cours ET le total.
    upload?.let { p ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { p.fraction },
                color = LumenColors.Accent,
                trackColor = LumenColors.SurfaceHigh,
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Text(
                "Envoi ${p.fileIndex}/${p.fileCount} — ${p.currentName} · " +
                    "${app.lumen.update.formatSize(p.bytesSent)} / ${app.lumen.update.formatSize(p.bytesTotal)}",
                color = LumenColors.Muted, fontSize = 11.sp,
            )
        }
    }

    // --- Étape 1 : choisir et analyser ------------------------------------
    if (analysis == null && upload == null) {
        Button(
            onClick = {
                scope.launch {
                    val dir = pickDirectory(T["hlsImport.choisirUnDossierHls"]) ?: return@launch
                    busy = true
                    error = null
                    val result = withContext(Dispatchers.Default) {
                        val master = findHlsMaster(dir)
                        if (master == null) return@withContext null
                        val content = readLocalText(master) ?: return@withContext null
                        HlsAnalyzer.analyze(master, content) { rel ->
                            readLocalText(resolveSibling(master, rel))
                        }
                    }
                    busy = false
                    if (result == null) {
                        error = T["hlsImport.aucunMasterM3u8LisibleDansCe"]
                        return@launch
                    }
                    analysis = result
                    val (guessed, year) = HlsAnalyzer.guessTitle(parentFolderName(result.masterPath))
                    query = guessed
                    matches = runCatching { tmdb.searchMulti(guessed, year) }.getOrDefault(emptyList())
                    chosen = matches.firstOrNull()
                }
            },
            enabled = !busy && destinations.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Analyse…" else T["hlsImport.choisirUnDossierHls"], fontWeight = FontWeight.SemiBold)
        }
        error?.let { Text(it, color = LumenColors.Accent, fontSize = 13.sp) }
    }

    // --- Étape 2 : analyse, rapprochement, verdict -------------------------
    analysis?.let { a ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
                .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .padding(16.dp),
        ) {
            Text(T["hlsImport.analyseDuDossier"], color = LumenColors.OnBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            InfoLine(T["hlsImport.duree"], "${(a.durationSeconds / 60).toInt()} min")
            InfoLine("Segments", "${a.segmentCount} (${if (a.segmentFormat == "fmp4") "fMP4" else "MPEG-TS"})")
            InfoLine(T["hlsImport.qualites"], a.variants.joinToString(", ") { it.resolution ?: "?" }.ifEmpty { T["hlsImport.uneSeule"] })
            if (a.audioTracks.isNotEmpty()) {
                InfoLine(T["hlsImport.pistesAudio"], a.audioTracks.joinToString(", ") { it.name })
            }
            if (a.subtitleTracks.isNotEmpty()) {
                InfoLine("Sous-titres", a.subtitleTracks.joinToString(", ") { it.name })
            }

            // Le verdict, AVANT l'import.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (a.directPlay) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (a.directPlay) Color(0xFF3ECF6B) else LumenColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (a.directPlay) T["hlsImport.lisibleEnDirectPlayAucunTranscodage"] else T["hlsImport.problemesDetectes"],
                    color = if (a.directPlay) Color(0xFF3ECF6B) else LumenColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            a.problems.forEach { Text("• $it", color = LumenColors.Accent, fontSize = 12.sp) }

            // Rapprochement TMDB, corrigeable à la main.
            Text("Titre", color = LumenColors.OnBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = LumenColors.OnBackground, fontSize = 14.sp),
                    cursorBrush = SolidColor(LumenColors.Accent),
                    modifier = Modifier.weight(1f)
                        .background(LumenColors.SurfaceHigh, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Button(
                    onClick = {
                        scope.launch {
                            matches = runCatching { tmdb.searchMulti(query, null) }.getOrDefault(emptyList())
                            chosen = matches.firstOrNull()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LumenColors.SurfaceHigh),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Rechercher", color = LumenColors.OnBackground, fontSize = 13.sp)
                }
            }

            if (matches.isNotEmpty()) {
                app.lumen.ui.components.ScrollableRow(
                    spacing = 10.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                    arrowWidth = 34.dp,
                    iconSize = 22.dp,
                    scrimColor = LumenColors.Surface,
                ) {
                    items(matches) { m ->
                        val selected = m.id == chosen?.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(92.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { chosen = m },
                        ) {
                            Box(
                                Modifier.width(84.dp).height(126.dp)
                                    .background(
                                        if (selected) LumenColors.Accent else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(if (selected) 3.dp else 0.dp),
                            ) {
                                AsyncImage(
                                    model = TmdbClient.posterUrl(m.posterPath),
                                    contentDescription = m.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(120.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LumenColors.SurfaceHigh),
                                )
                            }
                            Text(
                                "${m.displayName}${m.year?.let { " ($it)" } ?: ""}",
                                color = LumenColors.Muted,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val m = chosen
                        val dest = targets.byId(targetId) ?: return@Button
                        val localDir = a.masterPath.substringBeforeLast('/')
                        scope.launch {
                            error = null
                            val sent = app.lumen.domain.Uploader()
                                .uploadFolder(dest, localDir) { upload = it }
                            upload = null
                            sent.fold(
                                onSuccess = { remoteDir ->
                                    repo.add(
                                        title = m?.displayName ?: query.ifBlank { T["hlsImport.sansTitre"] },
                                        year = m?.year,
                                        // Le chemin enregistré est celui du SERVEUR :
                                        // la lecture passera par le proxy local.
                                        masterPath = "$remoteDir/" + a.masterPath.substringAfterLast('/'),
                                        posterUrl = TmdbClient.posterUrl(m?.posterPath),
                                        backdropUrl = TmdbClient.backdropUrl(m?.backdropPath),
                                        overview = m?.overview,
                                        analysis = a,
                                        targetId = targetId,
                                    )
                                    entries = repo.list()
                                    onChanged()
                                    reset()
                                },
                                onFailure = { error = T.format("hlsImport.envoiEchoue", it.message ?: "") },
                            )
                        }
                    },
                    enabled = query.isNotBlank() && targetId.isNotBlank() && upload == null,
                    colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(T["hlsImport.envoyerSurLeServeur"], fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Annuler",
                    color = LumenColors.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { reset() }.padding(8.dp),
                )
            }
        }
    }

    // --- Les dossiers déjà importés ---------------------------------------
    if (entries.isNotEmpty()) {
        Text(
            T["hlsImport.dossiersImportes"],
            color = LumenColors.OnBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        entries.forEach { e ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${e.title}${e.year?.let { " ($it)" } ?: ""}",
                        color = LumenColors.OnBackground, fontSize = 14.sp,
                    )
                    Text(
                        "${(e.durationSeconds / 60).toInt()} min · ${e.resolution ?: "?"} · " +
                            if (e.segmentFormat == "fmp4") "fMP4" else "MPEG-TS",
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
                    ) { repo.remove(e.id); entries = repo.list(); onChanged() },
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label : ", color = LumenColors.Muted, fontSize = 12.sp)
        Text(value, color = LumenColors.OnBackground, fontSize = 12.sp)
    }
}
