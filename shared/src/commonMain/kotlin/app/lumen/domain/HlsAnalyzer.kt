package app.lumen.domain

/** Une variante de qualité déclarée par le master.m3u8. */
data class HlsVariant(
    val uri: String,
    val bandwidth: Long,
    val resolution: String?,
    val codecs: String?,
) {
    /** true si le codec vidéo est lisible partout (H.264). */
    val universallyPlayable: Boolean
        get() = codecs?.contains("avc1") == true || codecs == null
}

/** Une piste audio ou de sous-titres déclarée par EXT-X-MEDIA. */
data class HlsMedia(
    val type: String,       // AUDIO | SUBTITLES
    val name: String,
    val language: String?,
    val uri: String?,
)

/** Le résultat de l'analyse d'un dossier HLS. */
data class HlsAnalysis(
    val masterPath: String,
    val variants: List<HlsVariant>,
    val media: List<HlsMedia>,
    val durationSeconds: Double,
    val segmentCount: Int,
    /** « ts » (MPEG-TS) ou « fmp4 » (fragments MP4). */
    val segmentFormat: String,
    val problems: List<String>,
) {
    val audioTracks: List<HlsMedia> get() = media.filter { it.type == "AUDIO" }
    val subtitleTracks: List<HlsMedia> get() = media.filter { it.type == "SUBTITLES" }

    /** Verdict de lisibilité SANS ré-encodage, annoncé avant l'import. */
    val directPlay: Boolean get() = problems.isEmpty() && variants.any { it.universallyPlayable }

    val bestResolution: String?
        get() = variants.maxByOrNull { it.bandwidth }?.resolution
}

/**
 * Analyse d'un dossier HLS déjà transcodé.
 *
 * On ne ré-encode rien : on lit le manifeste pour savoir ce qu'il contient et
 * si l'appareil saura le lire tel quel. Les problèmes sont signalés MAINTENANT,
 * pas au moment où l'utilisateur appuie sur Lire.
 */
object HlsAnalyzer {

    /** Attribut `KEY=VALUE` d'une ligne #EXT-X-… (valeurs entre guillemets gérées). */
    private fun attr(line: String, key: String): String? {
        val regex = Regex("""$key=("([^"]*)"|([^,]*))""")
        val m = regex.find(line) ?: return null
        return (m.groupValues[2].ifEmpty { m.groupValues[3] }).trim().ifEmpty { null }
    }

    /**
     * @param masterContent le contenu du master.m3u8
     * @param readVariant lit une playlist de variante (chemin relatif au master)
     */
    fun analyze(
        masterPath: String,
        masterContent: String,
        readVariant: (String) -> String?,
    ): HlsAnalysis {
        val problems = mutableListOf<String>()
        val lines = masterContent.lines().map { it.trim() }.filter { it.isNotEmpty() }

        if (lines.none { it.startsWith("#EXTM3U") }) {
            problems += "Ce fichier n'est pas un manifeste HLS valide"
        }

        val variants = mutableListOf<HlsVariant>()
        val media = mutableListOf<HlsMedia>()

        lines.forEachIndexed { i, line ->
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    val uri = lines.getOrNull(i + 1)?.takeIf { !it.startsWith("#") }
                    if (uri != null) {
                        variants += HlsVariant(
                            uri = uri,
                            bandwidth = attr(line, "BANDWIDTH")?.toLongOrNull() ?: 0,
                            resolution = attr(line, "RESOLUTION"),
                            codecs = attr(line, "CODECS"),
                        )
                    }
                }
                line.startsWith("#EXT-X-MEDIA") -> {
                    media += HlsMedia(
                        type = attr(line, "TYPE").orEmpty(),
                        name = attr(line, "NAME") ?: "Piste",
                        language = attr(line, "LANGUAGE"),
                        uri = attr(line, "URI"),
                    )
                }
            }
        }

        // Un manifeste sans variante EST une playlist de segments : cas courant
        // d'un transcodage simple, parfaitement valide.
        val playlists = if (variants.isEmpty()) listOf(masterPath) else variants.map { it.uri }
        var duration = 0.0
        var segments = 0
        var format = "ts"
        var encryptedWithoutKey = false
        var absolutePaths = false

        playlists.forEach { rel ->
            val content = if (variants.isEmpty()) masterContent else readVariant(rel)
            if (content == null) {
                problems += "Playlist introuvable : $rel"
                return@forEach
            }
            var localDuration = 0.0
            var localSegments = 0
            content.lines().map { it.trim() }.forEach { l ->
                when {
                    l.startsWith("#EXTINF:") ->
                        localDuration += l.removePrefix("#EXTINF:").substringBefore(',')
                            .toDoubleOrNull() ?: 0.0
                    l.startsWith("#EXT-X-MAP:") -> format = "fmp4"
                    l.startsWith("#EXT-X-KEY:") && !l.contains("METHOD=NONE") -> {
                        // Une clé hors du dossier rendrait la lecture impossible.
                        val keyUri = attr(l, "URI")
                        if (keyUri != null && keyUri.startsWith("http")) encryptedWithoutKey = true
                    }
                    l.startsWith("/") -> absolutePaths = true
                    !l.startsWith("#") && l.isNotEmpty() -> {
                        localSegments++
                        if (l.endsWith(".m4s") || l.endsWith(".mp4")) format = "fmp4"
                    }
                }
            }
            // La durée d'UNE variante suffit : elles couvrent le même contenu.
            if (localDuration > duration) duration = localDuration
            if (localSegments > segments) segments = localSegments
        }

        if (segments == 0) problems += "Aucun segment trouvé dans les playlists"
        if (encryptedWithoutKey) problems += "Playlist chiffrée dont la clé est hors du dossier"
        if (absolutePaths) {
            problems += "Chemins absolus dans les playlists : ils casseront une fois servis"
        }
        if (variants.isNotEmpty() && variants.none { it.universallyPlayable }) {
            problems += "Aucune variante H.264 : la lecture peut échouer sur certains appareils"
        }

        return HlsAnalysis(
            masterPath = masterPath,
            variants = variants.sortedByDescending { it.bandwidth },
            media = media,
            durationSeconds = duration,
            segmentCount = segments,
            segmentFormat = format,
            problems = problems,
        )
    }

    /**
     * Nettoie un nom de dossier pour la recherche TMDB :
     * « Inception.2010.1080p.BluRay.x264 » → « Inception » + 2010.
     */
    fun guessTitle(folderName: String): Pair<String, Int?> {
        val year = Regex("""(19|20)\d{2}""").find(folderName)?.value?.toIntOrNull()
        var name = folderName
        year?.let { name = name.substringBefore(it.toString()) }
        name = name.replace(Regex("""[._]"""), " ")
            .replace(
                Regex(
                    """(?i)\b(1080p|720p|2160p|4k|bluray|blu-ray|web-?dl|webrip|hdtv|x264|x265|hevc|""" +
                        """aac|ac3|dts|multi|vostfr|vf|truefrench|remux|proper|repack)\b""",
                ),
                " ",
            )
            .replace(Regex("""[\[\](){}-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return name to year
    }
}
