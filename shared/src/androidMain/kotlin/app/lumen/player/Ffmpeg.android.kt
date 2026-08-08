package app.lumen.player

import app.lumen.domain.MediaProbe
import app.lumen.domain.TranscodePlan
import app.lumen.domain.TranscodeProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Android n'a pas de ffmpeg système et un binaire embarqué pèserait des
// dizaines de mégaoctets pour un usage de niche. La conversion reste une
// fonction desktop ; l'UI le dit au lieu de proposer un bouton mort.
actual fun ffmpegAvailable(): Boolean = false

actual fun ffmpegVersion(): String? = null

actual suspend fun probeMedia(path: String): MediaProbe? = null

actual fun transcodeToHls(
    input: String,
    outputDir: String,
    plan: TranscodePlan,
    durationSeconds: Double,
): Flow<TranscodeProgress> =
    flowOf(TranscodeProgress.Failed("La conversion HLS n'est pas disponible sur Android"))

actual suspend fun pickVideoFile(title: String): String? = null

actual fun prepareOutputDir(parent: String, name: String): String? = null

actual fun defaultHlsOutputParent(): String = ""
