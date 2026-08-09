package app.lumen.domain

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

actual class Uploader actual constructor() {

    private fun sftp(target: UploadTarget): SSHClient = SSHClient().apply {
        // L'empreinte de l'hôte n'est pas vérifiée : ces serveurs sont des NAS
        // domestiques sans certificat, et refuser la connexion rendrait la
        // fonction inutilisable. Le mot de passe reste chiffré côté client.
        addHostKeyVerifier(PromiscuousVerifier())
        connectTimeout = 10_000
        connect(target.host, target.port)
        authPassword(target.username, target.password)
    }

    actual suspend fun test(target: UploadTarget): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            when (target.kind) {
                "ftp" -> testFtp(target)
                else -> testSftp(target)
            }
        }
    }

    private fun testSftp(target: UploadTarget): String {
        val ssh = sftp(target)
        try {
            ssh.newSFTPClient().use { sf ->
                val dir = target.remoteDir.ifBlank { "." }
                val entries = sf.ls(dir)
                // Écriture réelle : lister ne prouve pas qu'on peut déposer.
                val probe = "$dir/.lumen-write-test"
                val tmp = File.createTempFile("lumen", ".probe").apply { writeText("ok"); deleteOnExit() }
                sf.put(tmp.absolutePath, probe)
                sf.rm(probe)
                tmp.delete()
                return "${entries.size} élément${if (entries.size > 1) "s" else ""} — écriture autorisée"
            }
        } finally {
            runCatching { ssh.disconnect() }
        }
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
        val ssh = sftp(target)
        try {
            ssh.newSFTPClient().use { sf ->
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
        } finally {
            runCatching { ssh.disconnect() }
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
