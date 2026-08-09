package app.lumen.domain

import kotlinx.serialization.Serializable

/** Où Lumen dépose les dossiers HLS : le serveur qui héberge la médiathèque. */
@Serializable
data class UploadTarget(
    val label: String,
    val kind: String,          // sftp | ftp
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String,
    /** Dossier distant racine, celui que le serveur de médias surveille. */
    val remoteDir: String,
)

/** Avancement d'un envoi, pour l'afficher sans mentir sur ce qui reste. */
data class UploadProgress(
    val fileIndex: Int,
    val fileCount: Int,
    val bytesSent: Long,
    val bytesTotal: Long,
    val currentName: String,
) {
    val fraction: Float get() = if (bytesTotal > 0) bytesSent.toFloat() / bytesTotal else 0f
}

/**
 * Envoi d'un dossier vers le serveur. SFTP est le canal privilégié : il est
 * ouvert par défaut sur presque tous les NAS, chiffré, et ne demande aucune
 * installation supplémentaire — contrairement à un service maison.
 */
expect class Uploader() {
    /** Vérifie les identifiants et l'accès en écriture au dossier distant. */
    suspend fun test(target: UploadTarget): Result<String>

    /**
     * Envoie tout le contenu de [localDir] dans `remoteDir/<nom du dossier>`.
     * Renvoie le chemin distant créé.
     */
    suspend fun uploadFolder(
        target: UploadTarget,
        localDir: String,
        onProgress: (UploadProgress) -> Unit,
    ): Result<String>
}
