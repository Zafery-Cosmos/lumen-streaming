package app.lumen.domain

import app.lumen.db.LumenDb
import app.lumen.db.epochMillis
import app.lumen.security.SecretRef
import kotlin.random.Random

data class WebDavSource(
    val id: String,
    val config: WebDavConfig,
)

class WebDavSourceRepository(private val db: LumenDb) {

    fun list(): List<WebDavSource> = db.lumenQueries.selectWebDavSources().executeAsList().map {
        WebDavSource(
            id = it.id,
            config = WebDavConfig(
                label = it.label,
                baseUrl = it.baseUrl,
                username = it.username,
                password = SecretRef.resolve(it.password),
            ),
        )
    }

    fun add(config: WebDavConfig): WebDavSource {
        val id = buildString { repeat(10) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        db.lumenQueries.insertWebDavSource(
            id, config.label, config.baseUrl, config.username,
            SecretRef.store(id, "webdav.pass", config.password), epochMillis(),
        )
        return WebDavSource(id, config)
    }

    fun remove(id: String) {
        db.lumenQueries.selectWebDavSources().executeAsList().firstOrNull { it.id == id }
            ?.let { SecretRef.forget(it.password) }
        db.lumenQueries.deleteWebDavSource(id)
    }
}
