package probe

import app.lumen.update.ReleaseArtifact
import app.lumen.update.UpdateClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest

/**
 * Vérifie le téléchargement réel contre le serveur du NAS : le manifeste
 * publié, la taille annoncée, et le sha256 du fichier reçu.
 */
fun main(): Unit = runBlocking {
    val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json() }
    }
    val updates = UpdateClient(http)

    val manifest = updates.latest() ?: error("manifeste injoignable")
    println("Version publiée : ${manifest.version}")
    val artifact = manifest.platforms["linux"] ?: error("pas d'artefact linux")
    println("Fichier : ${artifact.file} (${artifact.size} octets attendus)")

    var lastProgress = 0L
    val path = updates.download(artifact) { state ->
        if (state.downloaded - lastProgress > 10_000_000) {
            println("  ${state.downloaded}/${state.total} octets, ${state.bytesPerSecond} o/s")
            lastProgress = state.downloaded
        }
    } ?: error("ÉCHEC : download() a renvoyé null")

    val file = File(path)
    check(file.exists()) { "fichier absent après téléchargement : $path" }
    check(file.length() == artifact.size) { "taille ${file.length()} != attendue ${artifact.size}" }

    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    val sha = digest.digest().joinToString("") { "%02x".format(it) }
    check(sha == artifact.sha256) { "sha256 $sha != attendu ${artifact.sha256}" }

    println("OK : ${file.length()} octets, sha256 vérifié")
    http.close()
}
