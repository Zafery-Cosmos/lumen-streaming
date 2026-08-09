package app.lumen.domain

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.decodeURLPart
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable

/** Connexion à un partage WebDAV (Nextcloud, Synology, Apache mod_dav…). */
@Serializable
data class WebDavConfig(
    val label: String,
    val baseUrl: String,
    val username: String,
    val password: String,
)

/** Une entrée résolue (dossier ou fichier), chemin ABSOLU sur le serveur. */
data class WebDavEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

/** Une ligne brute de réponse PROPFIND, avant résolution en WebDavEntry. */
data class WebDavRawEntry(
    val href: String,
    val isCollection: Boolean,
    val contentLength: Long?,
    val displayName: String?,
)

/** Parsing XML `multistatus` — DOM (javax.xml) : JVM et Android seulement. */
expect fun parseWebDavMultistatus(xml: String): List<WebDavRawEntry>

private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:displayname/>
  </d:prop>
</d:propfind>"""

class WebDavClient {
    private val http = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 6_000
        }
    }

    /** Origine "https://hôte[:port]" de la config, sans le chemin. */
    private fun originOf(config: WebDavConfig): String =
        Regex("^(https?://[^/]+)").find(config.baseUrl.trim())?.groupValues?.get(1)
            ?: config.baseUrl.trimEnd('/')

    /** Le chemin absolu d'où partir la navigation : celui de baseUrl lui-même. */
    fun rootPath(config: WebDavConfig): String {
        val rest = config.baseUrl.trim().removePrefix(originOf(config))
        return rest.ifBlank { "/" }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun basicAuthHeader(config: WebDavConfig): String =
        "Basic " + Base64.encode("${config.username}:${config.password}".encodeToByteArray())

    /** URL complète pour un chemin ABSOLU (pas relatif à baseUrl). */
    fun streamUrl(config: WebDavConfig, absolutePath: String): String = originOf(config) + absolutePath

    /** Liste le contenu d'un dossier — [path] est un chemin ABSOLU sur le serveur. */
    suspend fun list(config: WebDavConfig, path: String): Result<List<WebDavEntry>> = runCatching {
        val resp = http.request(streamUrl(config, path)) {
            method = HttpMethod("PROPFIND")
            header("Depth", "1")
            header("Authorization", basicAuthHeader(config))
            header("Content-Type", "application/xml; charset=utf-8")
            setBody(PROPFIND_BODY)
        }
        val status = resp.status.value
        if (status !in 200..299 && status != 207) error("HTTP $status")
        val selfNormalized = path.trimEnd('/')
        parseWebDavMultistatus(resp.bodyAsText())
            .mapNotNull { raw ->
                val decoded = runCatching { raw.href.decodeURLPart() }.getOrDefault(raw.href)
                val hrefPath = Regex("^https?://[^/]+").replace(decoded, "")
                if (hrefPath.trimEnd('/') == selfNormalized) return@mapNotNull null
                WebDavEntry(
                    name = raw.displayName?.ifBlank { null }
                        ?: hrefPath.trimEnd('/').substringAfterLast('/'),
                    path = hrefPath,
                    isDirectory = raw.isCollection,
                    sizeBytes = raw.contentLength,
                )
            }
            .sortedWith(compareByDescending<WebDavEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }
}
