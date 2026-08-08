package app.lumen.domain

import app.lumen.db.LumenDb
import app.lumen.db.epochMillis
import kotlin.random.Random

/** Un dossier HLS importé, prêt à être lu sans ré-encodage. */
data class HlsEntry(
    val id: String,
    val title: String,
    val year: Int?,
    val masterPath: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val durationSeconds: Double,
    val resolution: String?,
    val segmentFormat: String,
)

/** La médiathèque HLS locale : des dossiers déjà transcodés, indexés. */
class HlsLibraryRepository(private val db: LumenDb) {

    fun list(): List<HlsEntry> = db.lumenQueries.selectHlsEntries().executeAsList().map {
        HlsEntry(
            id = it.id,
            title = it.title,
            year = it.year?.toInt(),
            masterPath = it.masterPath,
            posterUrl = it.posterUrl,
            backdropUrl = it.backdropUrl,
            overview = it.overview,
            durationSeconds = it.durationSeconds,
            resolution = it.resolution,
            segmentFormat = it.segmentFormat,
        )
    }

    fun add(
        title: String,
        year: Int?,
        masterPath: String,
        posterUrl: String?,
        backdropUrl: String?,
        overview: String?,
        analysis: HlsAnalysis,
    ): HlsEntry {
        val entry = HlsEntry(
            id = buildString { repeat(10) { append("0123456789abcdef"[Random.nextInt(16)]) } },
            title = title,
            year = year,
            masterPath = masterPath,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            overview = overview,
            durationSeconds = analysis.durationSeconds,
            resolution = analysis.bestResolution,
            segmentFormat = analysis.segmentFormat,
        )
        db.lumenQueries.insertHlsEntry(
            entry.id, entry.title, entry.year?.toLong(), entry.masterPath,
            entry.posterUrl, entry.backdropUrl, entry.overview,
            entry.durationSeconds, entry.resolution, entry.segmentFormat,
            epochMillis(),
        )
        return entry
    }

    fun remove(id: String) {
        db.lumenQueries.deleteHlsEntry(id)
    }
}

/** Carte d'affichage d'une entrée HLS, pour les rangées de l'accueil. */
fun HlsEntry.toCard(): CardItem = CardItem(
    id = "hls:$id",
    title = title,
    posterUrl = posterUrl,
    inLibrary = true,
)

fun HlsEntry.toHero(): HeroItem? {
    val backdrop = backdropUrl ?: return null
    return HeroItem(
        id = "hls:$id",
        title = title,
        backdropUrl = backdrop,
        logoUrl = null,
        overview = overview,
        year = year,
        runtimeMinutes = (durationSeconds / 60).toInt().takeIf { it > 0 },
        rating = null,
    )
}
