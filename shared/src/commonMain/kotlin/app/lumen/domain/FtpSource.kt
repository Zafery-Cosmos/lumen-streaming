package app.lumen.domain

import kotlinx.serialization.Serializable

/** Connexion à un serveur FTP perso. */
@Serializable
data class FtpConfig(
    val label: String,
    val host: String,
    val port: Int = 21,
    val username: String,
    val password: String,
)

data class FtpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

/**
 * Navigation FTP (commons-net) — JVM et Android seulement, comme le parsing
 * WebDAV. La lecture, elle, passe par [app.lumen.player.StreamProxy] : FTP ne
 * se donne pas tel quel à un lecteur vidéo.
 */
expect class FtpClient() {
    suspend fun list(config: FtpConfig, path: String): Result<List<FtpEntry>>
}
