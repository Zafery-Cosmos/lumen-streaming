package app.lumen.player

import app.lumen.domain.HlsTranscode
import app.lumen.domain.MediaProbe
import app.lumen.domain.ProbeTrack
import app.lumen.domain.TranscodePlan
import app.lumen.domain.TranscodeProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

private fun run(vararg command: String, timeoutSeconds: Long = 20): String? = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(false).start()
    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return null
    }
    if (process.exitValue() != 0) null else output
}.getOrNull()

actual fun ffmpegAvailable(): Boolean = ffmpegVersion() != null && run("ffprobe", "-version") != null

actual fun ffmpegVersion(): String? =
    run("ffmpeg", "-version")?.lineSequence()?.firstOrNull()
        ?.removePrefix("ffmpeg version ")?.substringBefore(' ')

actual suspend fun probeMedia(path: String): MediaProbe? = withContext(Dispatchers.IO) {
    val output = run(
        "ffprobe", "-v", "quiet", "-print_format", "json",
        "-show_format", "-show_streams", path,
        timeoutSeconds = 60,
    ) ?: return@withContext null

    runCatching {
        val root = JSON.parseToJsonElement(output) as JsonObject
        val streams = (root["streams"] as? JsonArray).orEmpty()

        fun JsonObject.text(key: String): String? =
            (this[key])?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

        fun JsonObject.tag(key: String): String? =
            (this["tags"] as? JsonObject)?.text(key)

        val video = streams.map { it as JsonObject }.firstOrNull { it.text("codec_type") == "video" }
        val audio = streams.map { it as JsonObject }
            .filter { it.text("codec_type") == "audio" }
            .mapIndexed { i, s ->
                ProbeTrack(
                    index = i,
                    codec = s.text("codec_name") ?: "?",
                    language = s.tag("language"),
                    title = s.tag("title"),
                    channels = s.text("channels")?.toIntOrNull(),
                )
            }
        val subtitles = streams.map { it as JsonObject }
            .filter { it.text("codec_type") == "subtitle" }
            .mapIndexed { i, s ->
                ProbeTrack(i, s.text("codec_name") ?: "?", s.tag("language"), s.tag("title"))
            }
        val duration = (root["format"] as? JsonObject)?.text("duration")?.toDoubleOrNull()
            ?: video?.text("duration")?.toDoubleOrNull()
            ?: 0.0

        MediaProbe(
            videoCodec = video?.text("codec_name"),
            width = video?.text("width")?.toIntOrNull(),
            height = video?.text("height")?.toIntOrNull(),
            durationSeconds = duration,
            audioTracks = audio,
            subtitleTracks = subtitles,
        )
    }.getOrNull()
}

actual fun transcodeToHls(
    input: String,
    outputDir: String,
    plan: TranscodePlan,
    durationSeconds: Double,
): Flow<TranscodeProgress> = flow {
    val args = HlsTranscode.ffmpegArgs(input, outputDir, plan)
    val process = ProcessBuilder(listOf("ffmpeg") + args)
        // La sortie d'erreur de ffmpeg est verbeuse mais c'est elle qui porte
        // la vraie cause d'un échec : on la garde pour le message final.
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    val errors = StringBuilder()
    val errorPump = Thread {
        process.errorStream.bufferedReader().forEachLine { line ->
            synchronized(errors) {
                errors.appendLine(line)
                // On ne garde que la fin : un échec s'explique dans ses
                // dernières lignes, pas dans le dump des métadonnées.
                if (errors.length > 4000) errors.delete(0, errors.length - 4000)
            }
        }
    }.apply { isDaemon = true; start() }

    // Lecture ligne à ligne DANS la coroutine du flow : `forEachLine` prend
    // une lambda non suspendue, d'où il serait impossible d'émettre.
    val block = mutableListOf<String>()
    val reader = process.inputStream.bufferedReader()
    while (true) {
        val line = reader.readLine() ?: break
        block += line
        if (line.startsWith("progress=")) {
            HlsTranscode.parseProgress(block, durationSeconds)?.let { emit(it) }
            block.clear()
        }
    }

    process.waitFor()
    errorPump.join(2_000)

    val master = File(outputDir, "master.m3u8")
    if (process.exitValue() == 0 && master.isFile) {
        emit(TranscodeProgress.Done(master.absolutePath))
    } else {
        val tail = synchronized(errors) { errors.toString() }
            .lineSequence().filter { it.isNotBlank() }.toList().takeLast(3)
            .joinToString(" — ")
        emit(TranscodeProgress.Failed(tail.ifBlank { "ffmpeg a échoué sans message" }))
    }
}.flowOn(Dispatchers.IO)

actual suspend fun pickVideoFile(title: String): String? =
    withContext(Dispatchers.Main) {
        runCatching {
            val chooser = javax.swing.JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
                fileFilter = object : javax.swing.filechooser.FileFilter() {
                    override fun accept(f: File) = f.isDirectory ||
                        f.extension.lowercase() in setOf(
                            "mkv", "mp4", "avi", "mov", "m4v", "ts", "wmv", "flv", "webm", "mpg", "mpeg",
                        )

                    override fun getDescription() = "Fichiers vidéo"
                }
            }
            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else null
        }.getOrNull()
    }

actual fun prepareOutputDir(parent: String, name: String): String? = runCatching {
    val dir = File(parent, name)
    if (dir.exists() && (dir.list()?.isNotEmpty() == true)) {
        // On ne mélange pas deux conversions dans le même dossier : les
        // segments d'un ancien film survivraient au nouveau manifeste.
        dir.listFiles()?.forEach { it.delete() }
    }
    if (!dir.isDirectory && !dir.mkdirs()) return null
    dir.absolutePath
}.getOrNull()

actual fun defaultHlsOutputParent(): String {
    val configured = app.lumen.domain.AppSettings.hlsOutputDir.value
    if (configured.isNotBlank()) return configured
    val home = System.getProperty("user.home")
    return File(home, "HLS").apply { mkdirs() }.absolutePath
}
