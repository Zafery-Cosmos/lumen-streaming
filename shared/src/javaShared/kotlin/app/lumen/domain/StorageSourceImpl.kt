package app.lumen.domain

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// Fichier partagé desktop/Android (srcDir commun, cf. shared/build.gradle.kts) :
// java.util.zip est présent sur les deux runtimes, pas besoin de dépendance.

actual fun gzipCompress(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}

actual fun gzipDecompress(bytes: ByteArray): ByteArray =
    GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
