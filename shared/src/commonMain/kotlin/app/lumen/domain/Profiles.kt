package app.lumen.domain

import app.lumen.api.BaseItem
import app.lumen.db.LumenDb
import app.lumen.db.epochMillis
import app.lumen.db.sha256Hex
import kotlin.random.Random

/**
 * Profil LOCAL du foyer (plan §6.2) : indépendant des utilisateurs Jellyfin —
 * la bibliothèque est partagée, mais chacun a son profil, sa reprise de
 * lecture, son éventuel code PIN (haché en base) et sa restriction d'âge.
 */
data class LocalProfile(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val avatar: String? = null,      // nom de ressource (avatar_01…), null → initiale
    val pinHash: String? = null,
    val child: Boolean = false,
    val maxAge: Int = 10,
) {
    val hasPin: Boolean get() = pinHash != null
}

/** Profils stockés dans la base locale SQLite. */
/**
 * Les profils appartiennent à UN serveur : ceux du NAS ne sont pas ceux
 * d'anime-sanctuary. Le dépôt est donc lié au serveur courant.
 */
class ProfileRepository(private val db: LumenDb, private val serverUrl: String) {

    /** Rattache au serveur courant les profils créés avant la séparation. */
    fun adoptOrphans() {
        db.lumenQueries.adoptOrphanProfiles(serverUrl)
    }

    fun list(): List<LocalProfile> = db.lumenQueries.selectProfiles(serverUrl).executeAsList().map {
        LocalProfile(
            id = it.id,
            name = it.name,
            colorIndex = it.colorIndex.toInt(),
            avatar = it.avatar,
            pinHash = it.pinHash,
            child = it.child != 0L,
            maxAge = it.maxAge.toInt(),
        )
    }

    fun add(name: String, avatar: String?, child: Boolean, maxAge: Int, pin: String?): LocalProfile {
        val profile = LocalProfile(
            id = buildString { repeat(8) { append("0123456789abcdef"[Random.nextInt(16)]) } },
            name = name,
            colorIndex = list().size % 6,
            avatar = avatar,
            pinHash = pin?.takeIf { it.isNotBlank() }?.let(::sha256Hex),
            child = child,
            maxAge = maxAge,
        )
        db.lumenQueries.insertProfile(
            profile.id, serverUrl, profile.name, profile.colorIndex.toLong(), profile.avatar,
            profile.pinHash, if (profile.child) 1 else 0, profile.maxAge.toLong(),
        )
        return profile
    }

    fun update(profile: LocalProfile) {
        db.lumenQueries.updateProfile(
            profile.name, profile.colorIndex.toLong(), profile.avatar, profile.pinHash,
            if (profile.child) 1 else 0, profile.maxAge.toLong(), profile.id,
        )
    }

    fun remove(id: String) {
        db.lumenQueries.deleteProfile(id)
        db.lumenQueries.deleteWatchStateForProfile(id)
    }

    /** Vérifie un PIN saisi contre le hash stocké — jamais de clair en base. */
    fun verifyPin(profile: LocalProfile, entered: String): Boolean =
        profile.pinHash != null && sha256Hex(entered) == profile.pinHash
}

/** Reprise de lecture PAR PROFIL — la bibliothèque est commune, pas la reprise. */
class WatchStateRepository(private val db: LumenDb) {

    data class Entry(val itemId: String, val positionMs: Long, val durationMs: Long) {
        val percent: Double get() = if (durationMs > 0) positionMs * 100.0 / durationMs else 0.0
    }

    fun record(profileId: String, itemId: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        db.lumenQueries.upsertWatchState(profileId, itemId, positionMs, durationMs, epochMillis())
    }

    fun resume(profileId: String): List<Entry> =
        db.lumenQueries.resumeForProfile(profileId).executeAsList().map {
            Entry(it.itemId, it.positionMs, it.durationMs)
        }

    fun position(profileId: String, itemId: String): Long? =
        db.lumenQueries.positionFor(profileId, itemId).executeAsOneOrNull()
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
