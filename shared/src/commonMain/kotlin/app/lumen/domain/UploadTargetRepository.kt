package app.lumen.domain

import app.lumen.db.LumenDb
import app.lumen.db.epochMillis
import app.lumen.security.SecretRef
import kotlin.random.Random

data class SavedTarget(val id: String, val config: UploadTarget)

class UploadTargetRepository(private val db: LumenDb) {

    fun list(): List<SavedTarget> = db.lumenQueries.selectUploadTargets().executeAsList().map {
        SavedTarget(
            id = it.id,
            config = UploadTarget(
                label = it.label,
                kind = it.kind,
                host = it.host,
                port = it.port.toInt(),
                username = it.username,
                password = SecretRef.resolve(it.password),
                remoteDir = it.remoteDir,
            ),
        )
    }

    fun byId(id: String): UploadTarget? = list().firstOrNull { it.id == id }?.config

    fun add(config: UploadTarget): SavedTarget {
        val id = buildString { repeat(10) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        db.lumenQueries.insertUploadTarget(
            id, config.label, config.kind, config.host, config.port.toLong(), config.username,
            SecretRef.store(id, "upload.pass", config.password), config.remoteDir, epochMillis(),
        )
        return SavedTarget(id, config)
    }

    fun remove(id: String) {
        db.lumenQueries.selectUploadTargets().executeAsList().firstOrNull { it.id == id }
            ?.let { SecretRef.forget(it.password) }
        db.lumenQueries.deleteUploadTarget(id)
    }
}
