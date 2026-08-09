package app.lumen.domain

import app.lumen.db.LumenDb
import app.lumen.db.epochMillis
import app.lumen.security.SecretRef
import kotlin.random.Random

data class FtpSource(
    val id: String,
    val config: FtpConfig,
)

class FtpSourceRepository(private val db: LumenDb) {

    fun list(): List<FtpSource> = db.lumenQueries.selectFtpSources().executeAsList().map {
        FtpSource(
            id = it.id,
            config = FtpConfig(
                label = it.label,
                host = it.host,
                port = it.port.toInt(),
                username = it.username,
                password = SecretRef.resolve(it.password),
            ),
        )
    }

    fun add(config: FtpConfig): FtpSource {
        val id = buildString { repeat(10) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        db.lumenQueries.insertFtpSource(
            id, config.label, config.host, config.port.toLong(), config.username,
            SecretRef.store(id, "ftp.pass", config.password), epochMillis(),
        )
        return FtpSource(id, config)
    }

    fun remove(id: String) {
        db.lumenQueries.selectFtpSources().executeAsList().firstOrNull { it.id == id }
            ?.let { SecretRef.forget(it.password) }
        db.lumenQueries.deleteFtpSource(id)
    }
}
