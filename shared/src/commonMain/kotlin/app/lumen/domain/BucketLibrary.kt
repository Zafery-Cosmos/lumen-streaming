package app.lumen.domain

import app.lumen.api.TmdbClient
import app.lumen.db.LumenDb
import app.lumen.db.epochMillis
import kotlin.random.Random

/** Un titre indexé depuis un bucket perso, prêt pour l'accueil. */
data class BucketEntry(
    val id: String,
    val sourceId: String,
    val title: String,
    val year: Int?,
    val kind: String,        // file | hls
    val objectKey: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
)

class BucketLibraryRepository(private val db: LumenDb) {

    fun list(): List<BucketEntry> = db.lumenQueries.selectBucketEntries().executeAsList().map {
        BucketEntry(
            id = it.id,
            sourceId = it.sourceId,
            title = it.title,
            year = it.year?.toInt(),
            kind = it.kind,
            objectKey = it.objectKey,
            posterUrl = it.posterUrl,
            backdropUrl = it.backdropUrl,
            overview = it.overview,
        )
    }

    fun add(entry: BucketEntry) {
        db.lumenQueries.insertBucketEntry(
            entry.id, entry.sourceId, entry.title, entry.year?.toLong(), entry.kind,
            entry.objectKey, entry.posterUrl, entry.backdropUrl, entry.overview, epochMillis(),
        )
    }

    fun remove(id: String) {
        db.lumenQueries.deleteBucketEntry(id)
    }

    fun removeForSource(sourceId: String) {
        db.lumenQueries.deleteBucketEntriesForSource(sourceId)
    }
}

fun BucketEntry.toCard(): CardItem = CardItem(
    id = "bucket:$id",
    title = title,
    posterUrl = posterUrl,
    inLibrary = true,
)

/**
 * Indexe le contenu d'un bucket : repère les vidéos et les dossiers HLS,
 * rapproche chaque titre de TMDB, et remplace l'index précédent de la source.
 * Le nom de dossier/fichier sert de requête — même heuristique que l'import
 * HLS local (HlsAnalyzer.guessTitle).
 */
object BucketIndexer {

    private val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v")

    data class Candidate(val kind: String, val key: String, val rawName: String)

    /** Repère les titres présents parmi les clés du bucket. */
    fun candidates(keys: List<String>): List<Candidate> {
        val masters = keys.filter { it.substringAfterLast('/') == "master.m3u8" }
        // Un dossier HLS = celui du master ; TOUT ce qu'il contient appartient
        // au titre (segments, init.mp4…), rien n'y est indexé séparément.
        val hlsDirs = masters.map { it.substringBeforeLast('/', "") }
        fun insideHls(key: String) = hlsDirs.any { d -> d.isEmpty() || key.startsWith("$d/") }

        val hls = masters.map { master ->
            val dir = master.substringBeforeLast('/', "")
            Candidate("hls", master, dir.substringAfterLast('/').ifEmpty { "Sans titre" })
        }
        val files = keys.filter { k ->
            k.substringAfterLast('.').lowercase() in videoExtensions && !insideHls(k)
        }.map { key ->
            Candidate("file", key, key.substringAfterLast('/').substringBeforeLast('.'))
        }
        return hls + files
    }

    /**
     * Indexe [source] : liste tout le bucket, rapproche de TMDB, remplace
     * l'index. Renvoie le nombre de titres indexés, ou une erreur.
     */
    suspend fun index(
        source: StorageSource,
        s3: S3Client,
        tmdb: TmdbClient,
        repo: BucketLibraryRepository,
    ): Result<Int> = runCatching {
        // On n'explore QUE les dossiers déclarés — un bucket peut contenir tout
        // autre chose que des films. Aucun dossier choisi = tout le bucket.
        val prefixes = source.config.folders.ifEmpty { listOf("") }
        val keys = prefixes.flatMap { prefix ->
            s3.listAllKeys(source.config, prefix).getOrThrow()
        }.distinct()
        val found = candidates(keys)
        repo.removeForSource(source.id)
        found.forEach { candidate ->
            val (guessed, year) = HlsAnalyzer.guessTitle(candidate.rawName)
            val match = runCatching { tmdb.searchMulti(guessed, year) }.getOrDefault(emptyList()).firstOrNull()
            repo.add(
                BucketEntry(
                    id = buildString { repeat(10) { append("0123456789abcdef"[Random.nextInt(16)]) } },
                    sourceId = source.id,
                    title = match?.displayName ?: guessed.ifBlank { candidate.rawName },
                    year = match?.year ?: year,
                    kind = candidate.kind,
                    objectKey = candidate.key,
                    posterUrl = TmdbClient.posterUrl(match?.posterPath),
                    backdropUrl = TmdbClient.backdropUrl(match?.backdropPath),
                    overview = match?.overview,
                ),
            )
        }
        found.size
    }
}
