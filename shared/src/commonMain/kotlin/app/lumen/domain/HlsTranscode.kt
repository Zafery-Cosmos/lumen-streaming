package app.lumen.domain

/** Une piste d'un fichier source, telle que rapportée par ffprobe. */
data class ProbeTrack(
    val index: Int,
    val codec: String,
    val language: String?,
    val title: String?,
    val channels: Int? = null,
) {
    val label: String
        get() = listOfNotNull(
            title,
            language?.uppercase(),
            channels?.let { if (it > 2) "$it canaux" else "stéréo" },
            codec.uppercase(),
        ).joinToString(" · ")
}

/** Ce que contient le fichier à convertir. */
data class MediaProbe(
    val videoCodec: String?,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Double,
    val audioTracks: List<ProbeTrack>,
    val subtitleTracks: List<ProbeTrack>,
) {
    val resolution: String? get() = if (width != null && height != null) "${width}x$height" else null
}

/**
 * Décision prise avant de lancer ffmpeg, montrée à l'utilisateur.
 *
 * Le but est de recopier les flux sans les ré-encoder chaque fois que c'est
 * possible : une remultiplexion dure quelques secondes, un ré-encodage des
 * heures. C'est toute la différence entre les deux et il faut l'annoncer.
 */
data class TranscodePlan(
    val copyVideo: Boolean,
    val copyAudio: Boolean,
    val reasons: List<String>,
) {
    /** true si rien n'est ré-encodé : quasi instantané, qualité intacte. */
    val remuxOnly: Boolean get() = copyVideo && copyAudio

    val headline: String
        get() = when {
            remuxOnly -> "Remultiplexage — aucun ré-encodage, quelques secondes"
            copyVideo -> "Vidéo recopiée, audio ré-encodé — rapide"
            else -> "Ré-encodage vidéo — long, et la qualité en pâtit"
        }
}

/** Étape de la conversion, remontée à l'UI pendant que ffmpeg tourne. */
sealed interface TranscodeProgress {
    data class Running(
        val percent: Int,
        /**
         * Vitesse relative au temps réel. ffmpeg l'écrit parfois en notation
         * scientifique (« 1.14e+03x ») : on la garde en nombre et on la met
         * en forme à l'affichage.
         */
        val speedFactor: Double?,
        val etaSeconds: Int?,
    ) : TranscodeProgress {
        /** « 1140× » ou « 4,2× » — jamais « 1.14e+03x ». */
        val speedLabel: String?
            get() = speedFactor?.let {
                if (it >= 10) "${it.toInt()}×" else "${(it * 10).toInt() / 10.0}×".replace('.', ',')
            }
    }

    data class Done(val masterPath: String) : TranscodeProgress
    data class Failed(val message: String) : TranscodeProgress
}

object HlsTranscode {

    /** Codecs vidéo lisibles partout sans ré-encodage. */
    private val VIDEO_PASSTHROUGH = setOf("h264", "avc1")

    /** Codecs audio qu'un conteneur fMP4 accepte tels quels. */
    private val AUDIO_PASSTHROUGH = setOf("aac", "mp4a")

    /**
     * Décide quoi recopier et quoi ré-encoder.
     *
     * On ne ré-encode la vidéo que si son codec l'impose : HEVC, VP9 et AV1
     * passeraient techniquement en fMP4, mais pas sur tous les appareils.
     * L'audio, lui, est bon marché à convertir — AC3 et DTS y passent.
     */
    fun plan(probe: MediaProbe): TranscodePlan {
        val reasons = mutableListOf<String>()
        val video = probe.videoCodec?.lowercase()
        val copyVideo = video != null && video in VIDEO_PASSTHROUGH
        if (copyVideo) {
            reasons += "Vidéo ${video!!.uppercase()} recopiée telle quelle"
        } else {
            reasons += "Vidéo ${video?.uppercase() ?: "inconnue"} ré-encodée en H.264 " +
                "pour être lisible partout"
        }

        val audioCodecs = probe.audioTracks.map { it.codec.lowercase() }
        val copyAudio = audioCodecs.isNotEmpty() && audioCodecs.all { it in AUDIO_PASSTHROUGH }
        when {
            probe.audioTracks.isEmpty() -> reasons += "Aucune piste audio dans le fichier"
            copyAudio -> reasons += "Audio AAC recopié tel quel"
            else -> reasons += "Audio ${audioCodecs.distinct().joinToString("/") { it.uppercase() }} " +
                "converti en AAC (le fMP4 ne les accepte pas)"
        }

        if (probe.subtitleTracks.isNotEmpty()) {
            reasons += "${probe.subtitleTracks.size} piste(s) de sous-titres NON reprises : " +
                "le HLS les veut en fichiers séparés, pas encore géré"
        }
        return TranscodePlan(copyVideo, copyAudio, reasons)
    }

    /**
     * La commande ffmpeg, construite ici pour être lisible et vérifiable
     * plutôt que dispersée dans le code de plateforme.
     *
     * Toutes les pistes audio sont conservées (`-map 0:a`) : un film en VF+VO
     * garde ses deux langues, sélectionnables dans le lecteur.
     */
    fun ffmpegArgs(input: String, outputDir: String, plan: TranscodePlan): List<String> = buildList {
        add("-y")
        add("-i"); add(input)
        add("-map"); add("0:v:0")
        add("-map"); add("0:a?")
        add("-sn")

        add("-c:v")
        if (plan.copyVideo) {
            add("copy")
        } else {
            add("libx264")
            add("-preset"); add("veryfast")
            add("-crf"); add("20")
            // Sans ce profil, certains décodeurs matériels refusent le flux.
            add("-profile:v"); add("high")
            add("-pix_fmt"); add("yuv420p")
        }

        add("-c:a")
        if (plan.copyAudio) add("copy") else { add("aac"); add("-b:a"); add("192k") }

        add("-f"); add("hls")
        add("-hls_time"); add("6")
        add("-hls_playlist_type"); add("vod")
        // fMP4 plutôt que MPEG-TS : pas de surcoût de conteneur, et c'est ce
        // que lisent nativement les décodeurs modernes.
        add("-hls_segment_type"); add("fmp4")
        add("-hls_fmp4_init_filename"); add("init.mp4")
        add("-hls_segment_filename"); add("$outputDir/seg_%04d.m4s")
        // Progression exploitable ligne à ligne plutôt que la barre humaine.
        add("-progress"); add("pipe:1")
        add("-nostats")
        add("$outputDir/master.m3u8")
    }

    /**
     * Lit un bloc `-progress` de ffmpeg.
     *
     * ffmpeg écrit des lignes `clé=valeur` et termine chaque bloc par
     * `progress=continue` ou `progress=end`.
     */
    fun parseProgress(lines: List<String>, durationSeconds: Double): TranscodeProgress.Running? {
        var outTimeUs: Long? = null
        var speed: Double? = null
        lines.forEach { line ->
            val key = line.substringBefore('=', "").trim()
            val value = line.substringAfter('=', "").trim()
            when (key) {
                "out_time_us", "out_time_ms" -> {
                    // out_time_ms est en réalité en microsecondes chez ffmpeg :
                    // le nom est un vieux mensonge de l'outil, pas une erreur ici.
                    value.toLongOrNull()?.let { outTimeUs = it }
                }
                "speed" -> speed = value.removeSuffix("x").toDoubleOrNull()
            }
        }
        val done = outTimeUs ?: return null
        val seconds = done / 1_000_000.0
        val percent = if (durationSeconds > 0) {
            ((seconds / durationSeconds) * 100).toInt().coerceIn(0, 100)
        } else 0
        val eta = if (speed != null && speed > 0 && durationSeconds > seconds) {
            ((durationSeconds - seconds) / speed).toInt()
        } else null
        return TranscodeProgress.Running(percent, speed, eta)
    }

    /**
     * Nom de dossier de sortie : « Le Titre (2021) », prêt pour le
     * rapprochement TMDB que fait déjà l'import.
     */
    fun outputFolderName(title: String, year: Int?): String {
        val clean = title.replace(Regex("""[/\\:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifEmpty { "Sans titre" }
        return if (year != null) "$clean ($year)" else clean
    }
}
