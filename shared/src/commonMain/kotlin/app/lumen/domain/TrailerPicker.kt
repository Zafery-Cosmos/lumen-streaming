package app.lumen.domain

import app.lumen.api.YouTubeVideo

/** Une bande-annonce retenue, avec la raison du choix (affichée dans l'UI). */
data class TrailerPick(
    val video: YouTubeVideo,
    val score: Int,
    val reason: String,
)

/**
 * Choisit une bande-annonce **en français doublé** parmi des résultats YouTube.
 *
 * TMDB et IMDb ne référencent presque que des bandes-annonces en VO
 * sous-titrées : leur champ `iso_639_1 = fr` désigne la langue des
 * sous-titres, pas celle de la piste audio. On cherche donc directement sur
 * YouTube, où les distributeurs français publient la VF, et on tranche sur le
 * titre de la vidéo — seul indice fiable et universel.
 *
 * Le tri est volontairement sévère : mieux vaut ne rien proposer qu'imposer
 * une VOSTFR à quelqu'un qui a demandé de la VF.
 */
object TrailerPicker {

    private val VOST = Regex("""(?i)(\bvost\b|\bvostfr\b|\bvo\b|sous[- ]?titr|subtitle|legendad|subtitulad)""")
    private val VF = Regex("""(?i)(\bvf\b|\bvff\b|version fran[cç]aise|en fran[cç]ais|\bfrench\b|\btruefrench\b)""")
    private val TRAILER =
        Regex("""(?i)(bande[- ]?annonce|film[- ]?annonce|\btrailer\b|\bteaser\b)""")

    /** Année citée dans un titre : « Le Roi Lion (1994) ». */
    private val YEAR = Regex("""(19|20)\d{2}""")

    /** Ce qui n'est pas une bande-annonce mais lui ressemble dans les résultats. */
    private val NOISE = Regex(
        """(?i)(r[ée]action|\breaction\b|analyse|explication|d[ée]cryptage|\breview\b|critique|""" +
            """\btop \d|r[ée]sum[ée]|breakdown|easter[- ]?egg|fan[- ]?made|concept trailer|""" +
            """\bamv\b|mashup|parodie|making[- ]?of|interview|\bvlog\b|\bedit\b)""",
    )

    /** Distributeurs français : la source de la VF, à privilégier. */
    private val OFFICIAL_CHANNELS = listOf(
        "sonypictures", "warnerbros", "universalpictures", "paramountpictures",
        "waltdisney", "disneyfr", "disneypixar", "20thcentury", "netflixfrance",
        "primevideofrance", "appletv", "canal", "studiocanal", "path[ée]", "gaumont",
        "metropolitanfilm", "lepacte", "wildbunch", "ugcdistribution", "sndfilms",
        "diaphana", "arps[ée]lection", "bacfilms", "kmbo", "marsfilms", "leschroniques",
    )

    /** Relais qui republient les VF : fiables, mais après le distributeur. */
    private val RELAY_CHANNELS = listOf(
        "filmsactu", "aucin", "allocin", "imineo", "cinetrailer", "enserie",
        "cinemaclic", "bandesannonces",
    )

    /**
     * @param wantedTitle titre de l'œuvre, tel que connu de TMDB/Jellyfin
     * @param year année de sortie, si connue (départage les remakes)
     */
    fun best(videos: List<YouTubeVideo>, wantedTitle: String, year: Int?): TrailerPick? =
        videos.asSequence()
            .mapIndexedNotNull { index, v -> evaluate(v, wantedTitle, year, index) }
            .maxByOrNull { it.score }

    private fun evaluate(
        video: YouTubeVideo,
        wantedTitle: String,
        year: Int?,
        index: Int,
    ): TrailerPick? {
        val title = video.title
        val channel = video.channel

        // Éliminations sèches : ce n'est pas la bonne œuvre, ou pas une BA.
        if (!titleMatches(wantedTitle, title)) return null
        if (NOISE.containsMatchIn(title)) return null
        // Une bande-annonce dure entre ~20 s et ~5 min. Au-delà c'est un extrait,
        // une featurette ou le film entier reposté.
        if (video.durationSeconds !in 20..330) return null
        if (!TRAILER.containsMatchIn(title)) return null

        var score = 0
        val reasons = mutableListOf<String>()

        val saysVf = VF.containsMatchIn(title)
        val saysVost = VOST.containsMatchIn(title)

        when {
            // « VOSTFR » contient « VF » : l'exclusion doit primer sur la détection.
            saysVost -> {
                score -= 80
                reasons += "sous-titrée"
            }
            saysVf -> {
                score += 60
                reasons += "VF annoncée"
            }
        }

        // Les noms de chaînes s'écrivent « Warner Bros. France » ou
        // « WarnerBrosFrance » : on compare les deux graphies.
        val normChannel = normalize(channel)
        val tightChannel = normChannel.replace(" ", "")
        fun channelIn(list: List<String>) = list.any {
            Regex(it).containsMatchIn(normChannel) || Regex(it).containsMatchIn(tightChannel)
        }
        when {
            channelIn(OFFICIAL_CHANNELS) -> {
                score += 40
                reasons += "distributeur français"
            }
            channelIn(RELAY_CHANNELS) -> {
                score += 20
                reasons += "chaîne française"
            }
        }

        if (Regex("""(?i)officielle""").containsMatchIn(title)) {
            score += 12
            reasons += "officielle"
        }
        // Une chaîne française qui n'annonce NI VF ni VOST publie en pratique la
        // VF : c'est son marché. On l'accepte, mais derrière une VF explicite.
        if (!saysVf && !saysVost && score > 0) reasons += "présumée VF"

        // Une année contradictoire dans le titre désigne une AUTRE œuvre : sans
        // ce filtre, « Le Roi Lion » (1994) remonte le remake de 2019. On tolère
        // un an d'écart, une bande-annonce sortant souvent avant le film.
        val citedYears = YEAR.findAll(title).mapNotNull { it.value.toIntOrNull() }.toList()
        if (year != null && citedYears.isNotEmpty()) {
            if (citedYears.none { it in (year - 1)..(year + 1) }) return null
            score += 8
            reasons += "année concordante"
        }

        // À égalité, l'ordre de pertinence de YouTube tranche.
        score += (10 - index).coerceAtLeast(0)

        // En dessous de zéro, on préfère ne rien proposer : c'est une VOSTFR ou
        // une vidéo hors sujet.
        if (score <= 0) return null
        return TrailerPick(video, score, reasons.joinToString(", ").ifEmpty { "correspondance du titre" })
    }

    /**
     * Le titre de la vidéo doit contenir l'essentiel du titre de l'œuvre.
     * Sans ce garde-fou, la recherche « Spider-Man » renvoie les bandes-annonces
     * de toute la franchise.
     */
    private fun titleMatches(wanted: String, videoTitle: String): Boolean {
        val needle = normalize(wanted)
        val haystack = normalize(videoTitle)
        val words = needle.split(' ').filter { it.length >= 3 }
        if (words.isEmpty()) return haystack.contains(needle)
        val hits = words.count { haystack.contains(it) }
        // Tolérance d'un mot par tranche de quatre. Plus laxiste, « Spider-Man
        // No Way Home » accepterait « Spider-Man Far From Home » : trois mots
        // sur quatre en commun suffisent à confondre deux films d'une même
        // franchise.
        return hits >= words.size - words.size / 4
    }

    /** Minuscules, sans accents ni ponctuation : « Spider-Man » ≡ « spider man ». */
    fun normalize(text: String): String = buildString {
        text.lowercase().forEach { c ->
            append(
                when (c) {
                    in "àâäá" -> 'a'
                    in "éèêë" -> 'e'
                    in "îïí" -> 'i'
                    in "ôöó" -> 'o'
                    in "ûüùú" -> 'u'
                    'ç' -> 'c'
                    in "'\"’-_:.,!?()[]{}/&" -> ' '
                    else -> c
                },
            )
        }
    }.replace(Regex("""\s+"""), " ").trim()

    /** Requête envoyée à YouTube : cadrée pour remonter la VF en tête. */
    fun query(title: String, year: Int?, isSeries: Boolean): String = buildString {
        append(title)
        year?.let { append(" ").append(it) }
        append(if (isSeries) " série bande annonce VF" else " bande annonce VF")
    }
}
