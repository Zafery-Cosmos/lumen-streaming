package app.lumen.domain

import app.lumen.update.UPDATE_SERVER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvatarIndex(
    @SerialName("count") val count: Int = 0,
    @SerialName("groups") val groups: List<AvatarGroup> = emptyList(),
)

/** Un groupe = une ŒUVRE (film ou série), ou les avatars neutres. */
@Serializable
data class AvatarGroup(
    @SerialName("id") val id: String = "",
    @SerialName("collection") val collection: String = "",
    @SerialName("work") val work: String = "",
    @SerialName("avatars") val avatars: List<AvatarEntry> = emptyList(),
)

@Serializable
data class AvatarEntry(
    @SerialName("file") val file: String = "",
    @SerialName("url") val url: String = "",
)

/**
 * Banque d'avatars servie par le NAS : 1757 images triées par œuvre, trop
 * lourdes pour être embarquées dans l'app — on ne charge que ce qu'on regarde.
 */
class AvatarBank(private val http: HttpClient, private val baseUrl: String = UPDATE_SERVER) {

    /**
     * L'index de la banque. On renvoie un [Result] et NON un null : sans la
     * cause, l'interface ne peut pas distinguer « ça charge » de « ça a
     * échoué », et affiche un sablier éternel.
     */
    suspend fun index(): Result<AvatarIndex> = runCatching {
        http.get("$baseUrl/avatars/index.json").body<AvatarIndex>()
    }

    /** URL absolue d'un avatar, à partir du chemin stocké dans le profil. */
    fun url(path: String): String =
        if (path.startsWith("http")) path else "$baseUrl$path"
}
