package app.lumen.domain

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

actual class Uploader actual constructor() {

    /**
     * L'empreinte de l'hôte n'est pas vérifiée : ces serveurs sont des NAS
     * domestiques sans certificat, et refuser la connexion rendrait la
     * fonction inutilisable. Le mot de passe, lui, reste chiffré côté client.
     */
    private fun open(target: UploadTarget): Pair<Session, ChannelSftp> {
        val session = JSch().getSession(target.username, target.host, target.port).apply {
            setPassword(target.password)
            setConfig("StrictHostKeyChecking", "no")
            connect(10_000)
        }
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(10_000)
        return session to channel
    }

    private inline fun <T> withSftp(target: UploadTarget, block: (ChannelSftp) -> T): T {
        val (session, channel) = open(target)
        try {
            return block(channel)
        } finally {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
        }
    }

    /** Crée un dossier distant et tous ses parents — JSch n'a pas d'équivalent. */
    private fun ChannelSftp.mkdirs(path: String) {
        var current = ""
        path.trim('/').split('/').forEach { part ->
            current += "/" + part
            runCatching { mkdir(current) }
        }
    }

    actual suspend fun test(target: UploadTarget): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            when (target.kind) {
                "ftp" -> testFtp(target)
                else -> testSftp(target)
            }
        }
    }

    private fun testSftp(target: UploadTarget): String = withSftp(target) { sf ->
        val dir = target.remoteDir.ifBlank { "." }
        val entries = sf.ls(dir)
        // Écriture réelle : lister ne prouve pas qu'on puisse déposer.
        val probe = "$dir/.lumen-write-test"
        val tmp = File.createTempFile("lumen", ".probe").apply { writeText("ok"); deleteOnExit() }
        sf.put(tmp.absolutePath, probe)
        sf.rm(probe)
        tmp.delete()
        val count = entries.size
        "$count élément${if (count > 1) "s" else ""} — écriture autorisée"
    }

    private fun testFtp(target: UploadTarget): String {
        val client = FTPClient()
        try {
            client.connect(target.host, target.port)
            check(client.login(target.username, target.password)) { "Authentification refusée" }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            val dir = target.remoteDir.ifBlank { "/" }
            val entries = client.listFiles(dir)
            val probe = "$dir/.lumen-write-test"
            check(client.storeFile(probe, "ok".byteInputStream())) { "Écriture refusée" }
            client.deleteFile(probe)
            return "${entries.size} élément${if (entries.size > 1) "s" else ""} — écriture autorisée"
        } finally {
            runCatching { client.logout() }
            runCatching { client.disconnect() }
        }
    }

    actual suspend fun uploadFolder(
        target: UploadTarget,
        localDir: String,
        onProgress: (UploadProgress) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(localDir)
            require(root.isDirectory) { "Dossier introuvable : $localDir" }
            val files = root.walkTopDown().filter { it.isFile }.toList()
            val total = files.sumOf { it.length() }
            val remoteRoot = "${target.remoteDir.trimEnd('/')}/${root.name}"

            when (target.kind) {
                "ftp" -> uploadFtp(target, root, files, total, remoteRoot, onProgress)
                else -> uploadSftp(target, root, files, total, remoteRoot, onProgress)
            }
            remoteRoot
        }
    }

    private fun uploadSftp(
        target: UploadTarget,
        root: File,
        files: List<File>,
        total: Long,
        remoteRoot: String,
        onProgress: (UploadProgress) -> Unit,
    ) {
        withSftp(target) { sf ->
            sf.mkdirs(remoteRoot)
            var sent = 0L
            files.forEachIndexed { i, f ->
                val relative = f.relativeTo(root).path.replace(File.separatorChar, '/')
                val remote = "$remoteRoot/$relative"
                remote.substringBeforeLast('/').let { if (it != remoteRoot) sf.mkdirs(it) }
                sf.put(f.absolutePath, remote)
                sent += f.length()
                onProgress(UploadProgress(i + 1, files.size, sent, total, f.name))
            }
        }
    }

    private fun uploadFtp(
        target: UploadTarget,
        root: File,
        files: List<File>,
        total: Long,
        remoteRoot: String,
        onProgress: (UploadProgress) -> Unit,
    ) {
        val client = FTPClient()
        try {
            client.connect(target.host, target.port)
            check(client.login(target.username, target.password)) { "Authentification refusée" }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            // FTP n'a pas de « mkdir -p » : on crée niveau par niveau.
            fun mkdirs(path: String) {
                var current = ""
                path.trim('/').split('/').forEach { part ->
                    current += "/$part"
                    runCatching { client.makeDirectory(current) }
                }
            }
            mkdirs(remoteRoot)
            var sent = 0L
            files.forEachIndexed { i, f ->
                val relative = f.relativeTo(root).path.replace(File.separatorChar, '/')
                val remote = "$remoteRoot/$relative"
                remote.substringBeforeLast('/').let { if (it != remoteRoot) mkdirs(it) }
                f.inputStream().use { check(client.storeFile(remote, it)) { "Envoi refusé : $relative" } }
                sent += f.length()
                onProgress(UploadProgress(i + 1, files.size, sent, total, f.name))
            }
        } finally {
            runCatching { client.logout() }
            runCatching { client.disconnect() }
        }
    }
}
