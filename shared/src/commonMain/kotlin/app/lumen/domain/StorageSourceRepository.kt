package app.lumen.domain

import app.lumen.db.LumenDb
import app.lumen.db.epochMillis
import kotlin.random.Random

/** Une source de stockage perso enregistrée localement. */
data class StorageSource(
    val id: String,
    val config: PrivateStorageConfig,
)

/**
 * Persistance des sources de stockage perso — table locale, propre à
 * l'appareil. Rien ici ne synchronise ou n'annonce quoi que ce soit à
 * distance : l'utilisateur importe une config, elle reste chez lui.
 */
class StorageSourceRepository(private val db: LumenDb) {

    fun list(): List<StorageSource> = db.lumenQueries.selectStorageSources().executeAsList().map {
        StorageSource(
            id = it.id,
            config = PrivateStorageConfig(
                label = it.label,
                kind = it.kind,
                endpoint = it.endpoint,
                region = it.region,
                bucket = it.bucket,
                accessKey = it.accessKey,
                secretKey = it.secretKey,
                folders = it.folders.lines().filter(String::isNotBlank),
            ),
        )
    }

    /** Enregistre une config fraîchement importée (QR code ou saisie manuelle). */
    fun add(config: PrivateStorageConfig): StorageSource {
        val id = buildString { repeat(10) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        db.lumenQueries.insertStorageSource(
            id, config.label, config.kind, config.endpoint, config.region,
            config.bucket, config.accessKey, config.secretKey, epochMillis(),
            config.folders.joinToString("\n"),
        )
        return StorageSource(id, config)
    }

    fun remove(id: String) {
        db.lumenQueries.deleteStorageSource(id)
    }
}
