package app.lumen.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient as CommonsFtpClient

actual class FtpClient actual constructor() {
    actual suspend fun list(config: FtpConfig, path: String): Result<List<FtpEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = CommonsFtpClient()
                try {
                    client.connect(config.host, config.port)
                    check(client.login(config.username, config.password)) { "Authentification refusée" }
                    client.enterLocalPassiveMode()
                    client.setFileType(FTP.BINARY_FILE_TYPE)
                    val base = path.ifBlank { "/" }
                    client.listFiles(base)
                        .filter { it.name != "." && it.name != ".." }
                        .map { f ->
                            // Un lien symbolique n'est ni isFile ni isDirectory pour
                            // commons-net : sans ça, tout fichier symlinké (courant
                            // sur un NAS) seraient signale sans taille ni lisible.
                            val isDir = f.isDirectory
                            val isFileLike = f.isFile || (!isDir && f.isSymbolicLink)
                            FtpEntry(
                                name = f.name,
                                path = if (base.endsWith("/")) "$base${f.name}" else "$base/${f.name}",
                                isDirectory = isDir,
                                sizeBytes = if (isFileLike) f.size.takeIf { it >= 0 } else null,
                            )
                        }
                        .sortedWith(compareByDescending<FtpEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                } finally {
                    runCatching { client.logout() }
                    runCatching { client.disconnect() }
                }
            }
        }
}
