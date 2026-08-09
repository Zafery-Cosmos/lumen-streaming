package app.lumen.domain

import java.io.OutputStream
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
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
                // `skip` positionne la lecture à l'octet demandé : c'est ce qui
                // rend le saut dans la vidéo possible sans tout relire.
                sf.get(path, null, start).use { it.copyTo(sink, 128 * 1024) }
            }
        }
    }

    private fun <T> withSftp(target: UploadTarget, block: (ChannelSftp) -> T): T {
        val session = JSch().getSession(target.username, target.host, target.port).apply {
            setPassword(target.password)
            setConfig("StrictHostKeyChecking", "no")
            connect(10_000)
        }
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(10_000)
        try {
            return block(channel)
        } finally {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
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
