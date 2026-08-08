package app.lumen.domain

import app.lumen.api.BaseItem
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Profil LOCAL (plan §6.2) : indépendant des utilisateurs Jellyfin — plusieurs
 * personnes du foyer partagent le même compte serveur mais chacun a son
 * profil, son éventuel code PIN et sa restriction d'âge (profil enfant).
 */
@Serializable
data class LocalProfile(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    /** Code PIN à 4 chiffres, null si non verrouillé. TODO(L10b) : hacher. */
    val pin: String? = null,
    val child: Boolean = false,
    /** Âge maximal autorisé pour un profil enfant (ex. 10 → rien au-dessus de 10+). */
    val maxAge: Int = 10,
)

class ProfileStore(private val settings: Settings = Settings()) {
    private val json = Json { ignoreUnknownKeys = true }

    fun list(): List<LocalProfile> =
        settings.getStringOrNull(KEY)?.let {
            runCatching { json.decodeFromString<List<LocalProfile>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun save(profiles: List<LocalProfile>) {
        settings.putString(KEY, json.encodeToString(profiles))
    }

    fun add(name: String, child: Boolean, maxAge: Int, pin: String?): LocalProfile {
        val profile = LocalProfile(
            id = buildString { repeat(8) { append("0123456789abcdef"[Random.nextInt(16)]) } },
            name = name,
            colorIndex = list().size % PROFILE_COLORS_COUNT,
            pin = pin?.takeIf { it.isNotBlank() },
            child = child,
            maxAge = maxAge,
        )
        save(list() + profile)
        return profile
    }

    fun update(profile: LocalProfile) {
        save(list().map { if (it.id == profile.id) profile else it })
    }

    fun remove(id: String) {
        save(list().filterNot { it.id == id })
    }

    companion object {
        private const val KEY = "profiles.v1"
        const val PROFILE_COLORS_COUNT = 6
    }
}

/**
 * Classification d'âge d'un item, interprétée depuis OfficialRating.
 * Couvre les systèmes français, US et les libellés Jellyfin usuels.
 */
fun ratingAge(officialRating: String?): Int? {
    val r = officialRating?.trim()?.uppercase() ?: return null
    return when {
        r in setOf("TP", "U", "G", "TV-Y", "TV-G", "0+", "ALL", "FR-U") -> 0
        r in setOf("PG", "TV-Y7", "TV-PG", "6+", "7+") -> 7
        r in setOf("FR-10", "10", "10+") -> 10
        r in setOf("PG-13", "FR-12", "12", "12+", "TV-14") -> 13
        r in setOf("FR-16", "16", "16+", "R", "TV-MA") -> 16
        r in setOf("FR-18", "18", "18+", "NC-17", "X") -> 18
        else -> null
    }
}

/** Un item est-il visible pour ce profil ? (profil adulte → tout passe). */
fun LocalProfile?.allows(item: BaseItem): Boolean {
    if (this == null || !child) return true
    // Profil enfant : ce qui n'est pas classifié est masqué — prudence par défaut.
    val age = ratingAge(item.officialRating) ?: return false
    return age <= maxAge
}
