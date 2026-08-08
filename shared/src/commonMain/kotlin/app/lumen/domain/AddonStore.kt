package app.lumen.domain

import app.lumen.api.StremioClient
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Un addon Stremio installé dans Lumen. */
@Serializable
data class AddonEntry(
    val manifestUrl: String,
    val name: String,
    val enabled: Boolean = true,
)

/** Les addons installés, persistés localement — l'ordre est la priorité. */
class AddonStore(private val settings: Settings = Settings()) {
    private val json = Json { ignoreUnknownKeys = true }

    fun list(): List<AddonEntry> =
        settings.getStringOrNull(KEY)?.let {
            runCatching { json.decodeFromString<List<AddonEntry>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun save(addons: List<AddonEntry>) {
        settings.putString(KEY, json.encodeToString(addons))
    }

    /** Valide le manifeste puis installe — renvoie l'addon ou null si invalide. */
    suspend fun install(client: StremioClient, url: String): AddonEntry? = runCatching {
        val manifest = client.manifest(url)
        if (manifest.name.isBlank()) return null
        val entry = AddonEntry(StremioClient.normalizeManifestUrl(url), manifest.name)
        save(list().filterNot { it.manifestUrl == entry.manifestUrl } + entry)
        entry
    }.getOrNull()

    fun remove(manifestUrl: String) {
        save(list().filterNot { it.manifestUrl == manifestUrl })
    }

    fun toggle(manifestUrl: String) {
        save(list().map { if (it.manifestUrl == manifestUrl) it.copy(enabled = !it.enabled) else it })
    }

    private companion object {
        const val KEY = "addons.v1"
    }
}
