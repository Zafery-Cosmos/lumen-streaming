package app.lumen.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/** Une vidéo YouTube trouvée par la recherche. */
data class YouTubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$id"
}

/**
 * Recherche YouTube sans clé d'API.
 *
 * La page de résultats embarque tout son catalogue dans un bloc JSON
 * (`ytInitialData`) : on le lit directement, ce qui évite à la fois le quota
 * de l'API Data et un embed navigateur dont une app native n'a pas besoin.
 *
 * On demande explicitement la locale française : le classement renvoyé
 * privilégie alors les bandes-annonces VF, celles que TMDB ne référence pas
 * (TMDB ne connaît quasiment que les versions originales sous-titrées).
 */
class YouTubeClient(private val http: HttpClient) {

    suspend fun search(query: String): List<YouTubeVideo> = runCatching {
        val html = http.get(
            "https://www.youtube.com/results?search_query=${query.encodeURLParameter()}&hl=fr&gl=FR",
        ) {
            // Sans User-Agent de navigateur, YouTube renvoie une page dégradée
            // sans ytInitialData.
            header("User-Agent", DESKTOP_UA)
            header("Accept-Language", "fr-FR,fr;q=0.9")
        }.bodyAsText()

        val data = extractInitialData(html) ?: return emptyList()
        val renderers = mutableListOf<JsonObject>()
        collectVideoRenderers(data, renderers)
        renderers.mapNotNull { it.toVideo() }
    }.getOrDefault(emptyList())

    // --- Extraction du bloc JSON ------------------------------------------

    /**
     * Isole `ytInitialData` par équilibrage d'accolades plutôt que par regex :
     * le JSON contient des accolades dans ses chaînes, qu'une regex gloutonne
     * ou paresseuse couperait au mauvais endroit.
     */
    private fun extractInitialData(html: String): JsonObject? {
        val marker = html.indexOf("ytInitialData").takeIf { it >= 0 } ?: return null
        val open = html.indexOf('{', marker).takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        var i = open
        while (i < html.length) {
            val c = html[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching {
                            JSON.parseToJsonElement(html.substring(open, i + 1)) as? JsonObject
                        }.getOrNull()
                    }
                }
            }
            i++
        }
        return null
    }

    /** Descend l'arbre JSON et récolte chaque `videoRenderer` rencontré. */
    private fun collectVideoRenderers(element: JsonElement, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                (element["videoRenderer"] as? JsonObject)?.let(out::add)
                element.values.forEach { collectVideoRenderers(it, out) }
            }
            is JsonArray -> element.forEach { collectVideoRenderers(it, out) }
            else -> Unit
        }
    }

    private fun JsonObject.toVideo(): YouTubeVideo? {
        val id = str("videoId") ?: return null
        val title = (this["title"] as? JsonObject)?.runsText()
            ?: (this["title"] as? JsonObject)?.str("simpleText")
            ?: return null
        val channel = (this["ownerText"] as? JsonObject)?.runsText()
            ?: (this["longBylineText"] as? JsonObject)?.runsText()
            ?: ""
        val length = (this["lengthText"] as? JsonObject)?.str("simpleText")
            ?: (this["lengthText"] as? JsonObject)?.runsText()
        val thumb = ((this["thumbnail"] as? JsonObject)
            ?.get("thumbnails") as? JsonArray)
            ?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }
        return YouTubeVideo(id, title, channel, parseDuration(length), thumb)
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?: (this[key])?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    private fun JsonObject.runsText(): String? =
        (this["runs"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.str("text") }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        const val DESKTOP_UA: String =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"

        /** « 3:14 » → 194 s, « 1:02:03 » → 3723 s. 0 si inconnu (direct, court…). */
        fun parseDuration(text: String?): Int {
            if (text.isNullOrBlank()) return 0
            val parts = text.trim().split(':').mapNotNull { it.trim().toIntOrNull() }
            if (parts.isEmpty()) return 0
            return parts.fold(0) { acc, p -> acc * 60 + p }
        }
    }
}
