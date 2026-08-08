package app.lumen.player

import app.lumen.domain.MediaProbe
import app.lumen.domain.TranscodePlan
import app.lumen.domain.TranscodeProgress
import kotlinx.coroutines.flow.Flow

/** true si ffmpeg ET ffprobe sont installés et exécutables. */
expect fun ffmpegAvailable(): Boolean

/** Version de ffmpeg, affichée dans les réglages ; null s'il est absent. */
expect fun ffmpegVersion(): String?

/** Inspecte un fichier vidéo ; null si illisible ou si ffprobe manque. */
expect suspend fun probeMedia(path: String): MediaProbe?

/**
 * Segmente un fichier en dossier HLS, en émettant la progression au fil de
 * l'eau. Le flux se termine par [TranscodeProgress.Done] ou
 * [TranscodeProgress.Failed] — jamais silencieusement.
 */
expect fun transcodeToHls(
    input: String,
    outputDir: String,
    plan: TranscodePlan,
    durationSeconds: Double,
): Flow<TranscodeProgress>

/** Sélecteur de fichier vidéo natif ; null si annulé ou indisponible. */
expect suspend fun pickVideoFile(title: String): String?

/** Crée le dossier de sortie et renvoie son chemin ; null si impossible. */
expect fun prepareOutputDir(parent: String, name: String): String?

/** Dossier proposé par défaut pour ranger les conversions. */
expect fun defaultHlsOutputParent(): String
