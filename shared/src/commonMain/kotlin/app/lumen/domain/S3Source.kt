package app.lumen.domain

/** Une entrée du bucket : « dossier » (préfixe) ou objet. */
data class S3Entry(
    val name: String,
    val key: String,        // clé complète (objet) ou préfixe (dossier)
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

/**
 * Client S3 minimal, UNIQUE pour S3, R2 et B2 : les trois parlent la même API
 * (signature AWS SigV4, adressage path-style). Aucune dépendance SDK — la
 * présignature tient en une centaine de lignes et les URL signées se lisent
 * ensuite en HTTPS direct, sans proxy.
 */
expect class S3Client() {
    /** Liste un « dossier » du bucket ([prefix] vide = racine). */
    suspend fun list(config: PrivateStorageConfig, prefix: String): Result<List<S3Entry>>

    /** URL de lecture signée, valable [expiresSeconds] — à donner telle quelle au lecteur. */
    fun presignGet(config: PrivateStorageConfig, key: String, expiresSeconds: Int = 6 * 3600): String

    /**
     * Toutes les clés sous [prefix] (paginé côté serveur) — pour l'indexation.
     * Préfixe vide = tout le bucket.
     */
    suspend fun listAllKeys(config: PrivateStorageConfig, prefix: String = ""): Result<List<String>>
}
