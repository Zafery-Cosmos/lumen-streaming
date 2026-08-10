package app.lumen.domain

import app.lumen.api.PlexClient
import app.lumen.db.LumenDb
import app.lumen.db.epochMillis
import app.lumen.security.SecretRef
import kotlin.random.Random

/**
 * Identifiant de CETTE installation, tiré une fois puis conservé.
 *
 * Plex s'en sert pour reconnaître l'appareil : le régénérer à chaque
 * lancement créerait une nouvelle entrée d'appareil autorisé à chaque fois,
 * et encombrerait le compte de l'utilisateur.
 */
fun plexClientId(): String {
    val store = com.russhwolf.settings.Settings()
    store.getStringOrNull("plex.clientId")?.let { return it }
    val fresh = "lumen-" + buildString {
        repeat(20) { append("0123456789abcdef"[Random.nextInt(16)]) }
    }
    store.putString("plex.clientId", fresh)
    return fresh
}

/** Un compte Plex relié, et le serveur retenu. */
data class PlexSource(
    val id: String,
    val label: String,
    val token: String,
    val serverName: String,
    val baseUrl: String,
    /** Clés des bibliothèques à indexer ; vide = toutes. */
    val sections: List<String>,
)

/** Un titre indexé depuis Plex, prêt à devenir une carte de l'accueil. */
data class PlexEntry(
    val id: String,
    val sourceId: String,
    val title: String,
    val year: Int?,
    val partKey: String,
    val posterUrl: String?,
    val overview: String?,
)

class PlexSourceRepository(private val db: LumenDb) {

    fun list(): List<PlexSource> = db.lumenQueries.selectPlexSources().executeAsList().map {
        PlexSource(
            id = it.id,
            label = it.label,
            // Le jeton n'est jamais en base : seule une référence vers le coffre l'est.
            token = SecretRef.resolve(it.token),
            serverName = it.serverName,
            baseUrl = it.baseUrl,
            sections = it.sections.split(",").filter(String::isNotBlank),
        )
    }

    fun add(source: PlexSource): PlexSource {
        val id = source.id.ifBlank {
            buildString { repeat(10) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        }
        db.lumenQueries.insertPlexSource(
            id, source.label,
            SecretRef.store(id, "plex.token", source.token),
            source.serverName, source.baseUrl,
            source.sections.joinToString(","), epochMillis(),
        )
        return source.copy(id = id)
    }

    fun remove(id: String) {
        db.lumenQueries.selectPlexSources().executeAsList().firstOrNull { it.id == id }
            ?.let { SecretRef.forget(it.token) }
        db.lumenQueries.deletePlexSource(id)
        db.lumenQueries.deletePlexEntriesForSource(id)
    }
}

class PlexLibraryRepository(private val db: LumenDb) {

    fun list(): List<PlexEntry> = db.lumenQueries.selectPlexEntries().executeAsList().map {
        PlexEntry(
            id = it.id,
            sourceId = it.sourceId,
            title = it.title,
            year = it.year?.toInt(),
            partKey = it.partKey,
            posterUrl = it.posterUrl,
            overview = it.overview,
        )
    }

    fun add(entry: PlexEntry) {
        db.lumenQueries.insertPlexEntry(
            entry.id, entry.sourceId, entry.title, entry.year?.toLong(),
            entry.partKey, entry.posterUrl, entry.overview, epochMillis(),
        )
    }

    fun removeForSource(sourceId: String) {
        db.lumenQueries.deletePlexEntriesForSource(sourceId)
    }
}

fun PlexEntry.toCard(): CardItem = CardItem(
    id = "plex:$id",
    title = title,
    posterUrl = posterUrl,
    inLibrary = true,
)

/**
 * Parcourt les bibliothèques retenues et enregistre les titres lisibles.
 *
 * Seuls les films sont indexés pour l'instant : une série demande de
 * descendre saison par saison puis épisode par épisode, ce qui change la
 * forme des cartes — mieux vaut ne pas prétendre les gérer à moitié.
 */
object PlexIndexer {

    suspend fun index(
        source: PlexSource,
        client: PlexClient,
        repo: PlexLibraryRepository,
    ): Result<Int> = runCatching {
        val sections = client.sections(source.baseUrl, source.token)
            .filter { it.type == "movie" }
            .filter { source.sections.isEmpty() || it.key in source.sections }

        repo.removeForSource(source.id)
        var count = 0
        for (section in sections) {
            val items = client.sectionItems(source.baseUrl, source.token, section.key)
            for (item in items) {
                val part = item.firstPartKey ?: continue
                repo.add(
                    PlexEntry(
                        id = "${source.id}-${item.ratingKey}",
                        sourceId = source.id,
                        title = item.title,
                        year = item.year,
                        partKey = part,
                        posterUrl = client.imageUrl(source.baseUrl, item.thumb, source.token),
                        overview = item.summary,
                    ),
                )
                count++
            }
        }
        count
    }
}
