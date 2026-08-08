package app.lumen.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.TmdbClient
import app.lumen.api.TmdbItem
import app.lumen.domain.HlsAnalyzer
import app.lumen.domain.HlsLibraryRepository
import app.lumen.domain.HlsTranscode
import app.lumen.domain.MediaProbe
import app.lumen.domain.TranscodePlan
import app.lumen.domain.TranscodeProgress
import app.lumen.player.defaultHlsOutputParent
import app.lumen.player.ffmpegVersion
import app.lumen.player.pickVideoFile
import app.lumen.player.prepareOutputDir
import app.lumen.player.probeMedia
import app.lumen.player.transcodeToHls
import app.lumen.readLocalText
import app.lumen.resolveSibling
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/**
 * Conversion d'un fichier vidéo en dossier HLS, par FFmpeg.
 *
 * Le principe est le même que pour l'import : ne jamais ré-encoder si le
 * fichier n'y oblige pas. Un MKV H.264/AAC est simplement redécoupé, ce qui
 * prend quelques secondes pour un film entier. On annonce la décision AVANT
 * de lancer quoi que ce soit, avec sa raison.
 */
@Composable
fun HlsConvertSection(tmdb: TmdbClient, repo: HlsLibraryRepository, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val version = remember { ffmpegVersion() }

    var input by remember { mutableStateOf<String?>(null) }
    var probe by remember { mutableStateOf<MediaProbe?>(null) }
    var plan by remember { mutableStateOf<TranscodePlan?>(null) }
    var progress by remember { mutableStateOf<TranscodeProgress?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var year by remember { mutableStateOf<Int?>(null) }
    var matches by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var chosen by remember { mutableStateOf<TmdbItem?>(null) }

    fun reset() {
        input = null; probe = null; plan = null; progress = null
        query = ""; year = null; matches = emptyList(); chosen = null; error = null
    }

    if (version == null) {
        Text(
            "FFmpeg n'est pas installé sur cet appareil : la conversion est " +
                "indisponible. L'import de dossiers DÉJÀ segmentés, lui, " +
                "fonctionne sans FFmpeg.",
            color = LumenColors.Muted, fontSize = 12.sp,
        )
        return
    }

    Text(
        "Transforme un fichier vidéo en dossier HLS lisible par Lumen. Les " +
            "flux sont recopiés tels quels dès que le codec le permet : pas " +
            "de ré-encodage, pas de perte, quelques secondes au lieu " +
            "d'heures. FFmpeg $version détecté.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    // --- Étape 1 : choisir le fichier et l'inspecter -----------------------
    if (probe == null) {
        Button(
            onClick = {
                scope.launch {
                    val file = pickVideoFile("Choisir un fichier vidéo") ?: return@launch
                    busy = true
                    error = null
                    val result = probeMedia(file)
                    busy = false
                    if (result == null || result.videoCodec == null) {
                        error = "Fichier illisible par FFmpeg (pas de piste vidéo trouvée)"
                        return@launch
                    }
                    input = file
                    probe = result
                    plan = HlsTranscode.plan(result)
                    val (guessed, guessedYear) =
                        HlsAnalyzer.guessTitle(file.substringAfterLast('/').substringBeforeLast('.'))
                    query = guessed
                    year = guessedYear
                    matches = runCatching { tmdb.searchMulti(guessed, guessedYear) }
                        .getOrDefault(emptyList())
                    chosen = matches.firstOrNull()
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = LumenColors.SurfaceHigh),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(
                Icons.Filled.Movie, contentDescription = null,
                tint = LumenColors.OnBackground, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (busy) "Analyse du fichier…" else "Convertir un fichier vidéo en HLS",
                color = LumenColors.OnBackground, fontWeight = FontWeight.SemiBold,
            )
        }
        error?.let { Text(it, color = LumenColors.Accent, fontSize = 13.sp) }
    }

    // --- Étape 2 : le plan, annoncé avant d'agir ---------------------------
    val p = probe
    val pl = plan
    if (p != null && pl != null) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
                .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .padding(16.dp),
        ) {
            Text(
                "Ce que contient le fichier",
                color = LumenColors.OnBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            ConvertLine("Durée", "${(p.durationSeconds / 60).toInt()} min")
            ConvertLine("Vidéo", "${p.videoCodec?.uppercase()} ${p.resolution ?: ""}".trim())
            ConvertLine(
                "Audio",
                p.audioTracks.joinToString(", ") { it.label }.ifEmpty { "aucune piste" },
            )
            if (p.subtitleTracks.isNotEmpty()) {
                ConvertLine("Sous-titres", p.subtitleTracks.joinToString(", ") { it.label })
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (pl.remuxOnly) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (pl.remuxOnly) Color(0xFF3ECF6B) else LumenColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    pl.headline,
                    color = if (pl.remuxOnly) Color(0xFF3ECF6B) else LumenColors.Accent,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            pl.reasons.forEach { Text("• $it", color = LumenColors.Muted, fontSize = 12.sp) }

            // Titre : pré-rempli depuis le nom de fichier, corrigeable.
            Text("Titre", color = LumenColors.OnBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    enabled = progress !is TranscodeProgress.Running,
                    textStyle = TextStyle(color = LumenColors.OnBackground, fontSize = 14.sp),
                    cursorBrush = SolidColor(LumenColors.Accent),
                    modifier = Modifier.weight(1f)
                        .background(LumenColors.SurfaceHigh, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Button(
                    onClick = {
                        scope.launch {
                            matches = runCatching { tmdb.searchMulti(query, year) }
                                .getOrDefault(emptyList())
                            chosen = matches.firstOrNull()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LumenColors.SurfaceHigh),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Rechercher", color = LumenColors.OnBackground, fontSize = 13.sp)
                }
            }
            chosen?.let {
                Text(
                    "Rapproché de « ${it.displayName}${it.year?.let { y -> " ($y)" } ?: ""} »",
                    color = LumenColors.Muted, fontSize = 12.sp,
                )
            }

            // --- Étape 3 : la progression, réelle ---------------------------
            val running = progress as? TranscodeProgress.Running
            AnimatedVisibility(
                visible = running != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val target = (running?.percent ?: 0) / 100f
                    val animated by animateFloatAsState(
                        targetValue = target,
                        animationSpec = tween(400),
                        label = "progression",
                    )
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LumenColors.SurfaceHigh),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(animated).height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(LumenColors.Accent),
                        )
                    }
                    Text(
                        buildString {
                            append("${running?.percent ?: 0} %")
                            running?.speedLabel?.let { append(" · ").append(it).append(" temps réel") }
                            running?.etaSeconds?.let {
                                append(" · reste ")
                                append(if (it >= 60) "${it / 60} min ${it % 60} s" else "$it s")
                            }
                        },
                        color = LumenColors.Muted, fontSize = 12.sp,
                    )
                }
            }
            (progress as? TranscodeProgress.Failed)?.let {
                Text("Échec : ${it.message}", color = LumenColors.Accent, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val source = input ?: return@Button
                        scope.launch {
                            val m = chosen
                            val title = m?.displayName ?: query.ifBlank { "Sans titre" }
                            val outYear = m?.year ?: year
                            val dir = prepareOutputDir(
                                defaultHlsOutputParent(),
                                HlsTranscode.outputFolderName(title, outYear),
                            )
                            if (dir == null) {
                                progress = TranscodeProgress.Failed("Dossier de sortie inaccessible")
                                return@launch
                            }
                            transcodeToHls(source, dir, pl, p.durationSeconds).collect { step ->
                                progress = step
                                if (step is TranscodeProgress.Done) {
                                    // Le dossier produit repasse par la MÊME analyse
                                    // que l'import : le verdict vient du résultat,
                                    // pas d'une promesse de la commande lancée.
                                    val content = readLocalText(step.masterPath)
                                    val analysis = content?.let {
                                        HlsAnalyzer.analyze(step.masterPath, it) { rel ->
                                            readLocalText(resolveSibling(step.masterPath, rel))
                                        }
                                    }
                                    if (analysis == null) {
                                        progress = TranscodeProgress.Failed(
                                            "Manifeste produit mais illisible",
                                        )
                                        return@collect
                                    }
                                    repo.add(
                                        title = title,
                                        year = outYear,
                                        masterPath = step.masterPath,
                                        posterUrl = TmdbClient.posterUrl(m?.posterPath),
                                        backdropUrl = TmdbClient.backdropUrl(m?.backdropPath),
                                        overview = m?.overview,
                                        analysis = analysis,
                                    )
                                    onChanged()
                                    reset()
                                }
                            }
                        }
                    },
                    enabled = progress !is TranscodeProgress.Running && query.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        if (progress is TranscodeProgress.Running) "Conversion en cours…"
                        else "Lancer la conversion",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (progress !is TranscodeProgress.Running) {
                    Text(
                        "Annuler",
                        color = LumenColors.Muted, fontSize = 14.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { reset() }.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvertLine(label: String, value: String) {
    Row {
        Text("$label : ", color = LumenColors.Muted, fontSize = 12.sp)
        Text(value, color = LumenColors.OnBackground, fontSize = 12.sp)
    }
}
