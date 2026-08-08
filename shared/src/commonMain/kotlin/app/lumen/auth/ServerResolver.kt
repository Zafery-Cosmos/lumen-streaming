package app.lumen.auth

import app.lumen.api.JellyfinClient
import app.lumen.api.PublicSystemInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Un serveur validé : l'URL exacte qui a répondu + ses infos publiques. */
data class ResolvedServer(val baseUrl: String, val info: PublicSystemInfo)

/**
 * Résolution tolérante de l'adresse saisie (plan §2) : l'utilisateur tape
 * « 192.168.1.170 » ou « jellyfin.example.com » et on essaie les formes usuelles.
 * Toutes les candidates sont testées en parallèle ; la première qui répond
 * dans l'ordre de préférence gagne.
 */
class ServerResolver(private val client: JellyfinClient) {

    fun candidates(rawInput: String): List<String> {
        val input = rawInput.trim().trimEnd('/')
        if (input.isEmpty()) return emptyList()

        // Une URL complète avec schéma : on la tente telle quelle, plus /jellyfin.
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return listOf(input, "$input/jellyfin")
        }

        val hasPort = Regex(""":\d+$""").containsMatchIn(input)
        return buildList {
            if (hasPort) {
                // Port explicite : le schéma reste la seule inconnue.
                add("http://$input")
                add("https://$input")
            } else {
                add("https://$input")          // hôte public en HTTPS standard
                add("http://$input:8096")      // port HTTP par défaut de Jellyfin
                add("https://$input:8920")     // port HTTPS par défaut de Jellyfin
                add("https://$input/jellyfin") // derrière un reverse-proxy avec chemin
                add("http://$input")
            }
        }
    }

    /**
     * Teste toutes les candidates en parallèle et renvoie la première (dans
     * l'ordre de préférence) qui se comporte comme un Jellyfin, ou null.
     */
    suspend fun resolve(rawInput: String): ResolvedServer? = coroutineScope {
        candidates(rawInput)
            .map { url ->
                async {
                    try {
                        val info = client.publicInfo(url)
                        // Un vrai Jellyfin renvoie toujours un Id non vide.
                        if (info.id.isNotEmpty()) ResolvedServer(url, info) else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            .awaitAll()
            .firstOrNull { it != null }
    }
}
