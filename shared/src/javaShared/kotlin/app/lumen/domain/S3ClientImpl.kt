package app.lumen.domain

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element

// SimpleDateFormat plutôt que java.time : minSdk 24, java.time exige l'API 26.
private fun utcFormat(pattern: String): SimpleDateFormat =
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun hmac(key: ByteArray, data: String): ByteArray =
    Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        .doFinal(data.toByteArray(Charsets.UTF_8))

/**
 * Encodage URI STRICT d'AWS (RFC 3986) : tout est encodé sauf les non-réservés.
 * C'est LE piège classique de SigV4 — URLEncoder de Java encode l'espace en
 * « + » et laisse passer « * », ce qui invalide la signature.
 */
private fun awsEncode(value: String, keepSlash: Boolean = false): String = buildString {
    value.toByteArray(Charsets.UTF_8).forEach { b ->
        val c = b.toInt().toChar()
        when {
            c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~" -> append(c)
            c == '/' && keepSlash -> append(c)
            else -> append("%%%02X".format(b.toInt() and 0xFF))
        }
    }
}

actual class S3Client actual constructor() {

    /** Présigne une requête GET (objet OU liste) — cœur commun SigV4. */
    private fun presign(
        config: PrivateStorageConfig,
        path: String,                       // chemin path-style, déjà encodé
        extraParams: Map<String, String>,
        expiresSeconds: Int,
    ): String {
        val endpoint = config.endpoint.trim().trimEnd('/')
        val uri = URI(endpoint)
        val host = uri.host + if (uri.port != -1) ":${uri.port}" else ""
        val region = config.region?.takeIf { it.isNotBlank() } ?: "us-east-1"

        val now = Date()
        val amzDate = utcFormat("yyyyMMdd'T'HHmmss'Z'").format(now)
        val day = utcFormat("yyyyMMdd").format(now)
        val scope = "$day/$region/s3/aws4_request"

        val params = (extraParams + mapOf(
            "X-Amz-Algorithm" to "AWS4-HMAC-SHA256",
            "X-Amz-Credential" to "${config.accessKey}/$scope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to expiresSeconds.toString(),
            "X-Amz-SignedHeaders" to "host",
        )).toSortedMap()
        val canonicalQuery = params.entries.joinToString("&") { (k, v) -> "${awsEncode(k)}=${awsEncode(v)}" }

        val canonicalRequest =
            "GET\n$path\n$canonicalQuery\nhost:$host\n\nhost\nUNSIGNED-PAYLOAD"
        val stringToSign =
            "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest)}"

        var key = hmac("AWS4${config.secretKey}".toByteArray(Charsets.UTF_8), day)
        key = hmac(key, region)
        key = hmac(key, "s3")
        key = hmac(key, "aws4_request")
        val signature = hmac(key, stringToSign).joinToString("") { "%02x".format(it) }

        return "$endpoint$path?$canonicalQuery&X-Amz-Signature=$signature"
    }

    actual fun presignGet(config: PrivateStorageConfig, key: String, expiresSeconds: Int): String =
        presign(
            config,
            "/${awsEncode(config.bucket)}/${awsEncode(key, keepSlash = true)}",
            emptyMap(),
            expiresSeconds,
        )

    actual suspend fun list(config: PrivateStorageConfig, prefix: String): Result<List<S3Entry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = presign(
                    config,
                    "/${awsEncode(config.bucket)}",
                    mapOf("list-type" to "2", "delimiter" to "/", "prefix" to prefix),
                    300,
                )
                val connection = URI(url).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                val xml = try {
                    if (connection.responseCode !in 200..299) {
                        val err = connection.errorStream?.readBytes()?.decodeToString().orEmpty()
                        error("HTTP ${connection.responseCode}${extractS3Error(err)?.let { " — $it" } ?: ""}")
                    }
                    connection.inputStream.readBytes().decodeToString()
                } finally {
                    connection.disconnect()
                }
                parseListing(xml, prefix)
            }
        }

    actual suspend fun listAllKeys(config: PrivateStorageConfig, prefix: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val keys = mutableListOf<String>()
                var token: String? = null
                do {
                    val params = buildMap {
                        put("list-type", "2")
                        if (prefix.isNotBlank()) put("prefix", prefix)
                        token?.let { put("continuation-token", it) }
                    }
                    val url = presign(config, "/${awsEncode(config.bucket)}", params, 300)
                    val connection = URI(url).toURL().openConnection() as HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 30_000
                    val xml = try {
                        if (connection.responseCode !in 200..299) {
                            val err = connection.errorStream?.readBytes()?.decodeToString().orEmpty()
                            error("HTTP ${connection.responseCode}${extractS3Error(err)?.let { " — $it" } ?: ""}")
                        }
                        connection.inputStream.readBytes().decodeToString()
                    } finally {
                        connection.disconnect()
                    }
                    val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                        .newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
                    val contents = doc.getElementsByTagNameNS("*", "Contents")
                    for (i in 0 until contents.length) {
                        (contents.item(i) as? Element)
                            ?.getElementsByTagNameNS("*", "Key")?.item(0)?.textContent
                            ?.let { keys += it }
                    }
                    val truncated = doc.getElementsByTagNameNS("*", "IsTruncated")
                        .item(0)?.textContent == "true"
                    token = if (truncated) {
                        doc.getElementsByTagNameNS("*", "NextContinuationToken").item(0)?.textContent
                    } else {
                        null
                    }
                } while (token != null)
                keys
            }
        }

    private fun parseListing(xml: String, prefix: String): List<S3Entry> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val entries = mutableListOf<S3Entry>()

        val prefixes = doc.getElementsByTagNameNS("*", "CommonPrefixes")
        for (i in 0 until prefixes.length) {
            val p = (prefixes.item(i) as? Element)
                ?.getElementsByTagNameNS("*", "Prefix")?.item(0)?.textContent ?: continue
            entries += S3Entry(
                name = p.trimEnd('/').substringAfterLast('/'),
                key = p,
                isDirectory = true,
                sizeBytes = null,
            )
        }

        val contents = doc.getElementsByTagNameNS("*", "Contents")
        for (i in 0 until contents.length) {
            val el = contents.item(i) as? Element ?: continue
            val key = el.getElementsByTagNameNS("*", "Key").item(0)?.textContent ?: continue
            if (key == prefix) continue   // marqueur du « dossier » lui-même
            entries += S3Entry(
                name = key.removePrefix(prefix),
                key = key,
                isDirectory = false,
                sizeBytes = el.getElementsByTagNameNS("*", "Size").item(0)?.textContent?.toLongOrNull(),
            )
        }
        return entries.sortedWith(compareByDescending<S3Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /** Le <Message> d'une erreur S3, bien plus parlant que le code HTTP seul. */
    private fun extractS3Error(xml: String): String? = runCatching {
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            .getElementsByTagName("Message").item(0)?.textContent
    }.getOrNull()
}
