package app.lumen.domain

import java.io.OutputStream
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

/**
 * Lit un fichier posé sur le serveur de médias, à l'octet près.
 *
 * C'est ce que le proxy local appelle pour servir un dossier HLS distant : le
 * lecteur voit une simple URL HTTP, les octets viennent du serveur.
 */
class RemoteReader {

    /** Taille du fichier distant, nécessaire pour répondre un Content-Range juste. */
    fun size(target: UploadTarget, path: String): Long? = runCatching {
        when (target.kind) {
            "ftp" -> withFtp(target) { it.listFiles(path).firstOrNull()?.size }
            else -> withSftp(target) { it.stat(path).size }
        }
    }.getOrNull()

    /** Recopie le fichier depuis [start] vers [sink]. */
    fun copyTo(target: UploadTarget, path: String, start: Long, sink: OutputStream) {
        when (target.kind) {
            "ftp" -> withFtp(target) { client ->
                if (start > 0) client.restartOffset = start
                client.retrieveFileStream(path)?.use { it.copyTo(sink, 128 * 1024) }
                client.completePendingCommand()
            }
            else -> withSftp(target) { sf ->
                sf.open(path).use { file ->
                    // Lecture par blocs à partir de l'offset demandé : c'est ce
                    // qui rend le saut dans la vidéo possible sans tout relire.
                    val buffer = ByteArray(128 * 1024)
                    var offset = start
                    while (true) {
                        val read = file.read(offset, buffer, 0, buffer.size)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                        offset += read
                    }
                }
            }
        }
    }

    private fun <T> withSftp(target: UploadTarget, block: (net.schmizz.sshj.sftp.SFTPClient) -> T): T {
        val ssh = SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier())
            connectTimeout = 10_000
            connect(target.host, target.port)
            authPassword(target.username, target.password)
        }
        try {
            return ssh.newSFTPClient().use(block)
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    private fun <T> withFtp(target: UploadTarget, block: (FTPClient) -> T): T {
        val client = FTPClient()
        try {
            client.connect(target.host, target.port)
            check(client.login(target.username, target.password)) { "Authentification refusée" }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            return block(client)
        } finally {
            runCatching { client.logout() }
            runCatching { client.disconnect() }
        }
    }
}
