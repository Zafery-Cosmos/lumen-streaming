package app.lumen.security

/**
 * Coffre des données sensibles (jetons Jellyfin, clés de buckets, mots de
 * passe WebDAV/FTP). Tout est écrit chiffré dans un conteneur `.lmn`.
 *
 * Trois niveaux, du plus commode au plus sûr :
 *
 *  - **L1 — aucun** : ce qui existait avant, du texte clair. Conservé
 *    uniquement pour lire les anciennes installations et les migrer.
 *  - **L2 — lié à l'appareil** : AES-256-GCM, clé détenue par le magasin de
 *    clés du système (Android Keystore). Transparent pour l'utilisateur ;
 *    copier le fichier sur une autre machine ne sert à rien.
 *  - **L3 — mot de passe maître** : AES-256-GCM, clé **dérivée à chaque
 *    ouverture et gardée en mémoire vive uniquement**. Rien sur le disque ne
 *    permet de déchiffrer. C'est le seul niveau qui résiste à quelqu'un qui
 *    aurait la main sur la machine — et le seul où un mot de passe oublié
 *    signifie des données définitivement perdues.
 *
 * Sur la lisibilité de ce code : la solidité vient de la CLÉ, jamais du secret
 * de la méthode. Un algorithme qu'on doit cacher pour être sûr n'est pas sûr —
 * et dans une app open source, il ne le resterait pas dix minutes.
 */
enum class VaultLevel(val id: Int) {
    NONE(0),
    DEVICE(1),
    PASSPHRASE(2),
    ;

    companion object {
        fun of(id: Int): VaultLevel = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** Primitives cryptographiques de la plateforme (javax.crypto sur JVM/Android). */
expect object Crypto {
    /** Aléa cryptographique (SecureRandom). */
    fun randomBytes(size: Int): ByteArray

    /** AES-256-GCM. [aad] est authentifiée mais pas chiffrée (l'en-tête). */
    fun encrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray

    /** Renvoie null si l'authentification échoue : clé fausse OU données altérées. */
    fun decrypt(key: ByteArray, nonce: ByteArray, cipher: ByteArray, aad: ByteArray): ByteArray?

    /** PBKDF2-HMAC-SHA256 → clé de 32 octets. Lent VOLONTAIREMENT. */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray

    /**
     * Clé du magasin de clés du système, créée à la première demande.
     * Null si la plateforme n'en fournit pas d'utilisable.
     */
    fun deviceKey(): ByteArray?
}

/**
 * Conteneur `.lmn` — format volontairement figé et documenté, pour qu'un futur
 * import/export puisse le relire :
 *
 * ```
 * "LMN1"  4 octets   signature du format
 * niveau  1 octet    VaultLevel
 * sel     16 octets  dérivation du mot de passe (zéros si non utilisé)
 * iter    4 octets   itérations PBKDF2 (gros-boutiste)
 * nonce   12 octets  UNIQUE par écriture — jamais réutilisé avec la même clé
 * suite   n octets   chiffré + marque d'authentification GCM
 * ```
 *
 * L'en-tête entier sert de données authentifiées : modifier le niveau ou les
 * itérations pour tenter de rétrograder le chiffrement invalide le déchiffrement.
 */
object LmnContainer {
    private val MAGIC = byteArrayOf('L'.code.toByte(), 'M'.code.toByte(), 'N'.code.toByte(), '1'.code.toByte())
    private const val SALT = 16
    private const val NONCE = 12
    const val HEADER = 4 + 1 + SALT + 4 + NONCE

    /** Itérations PBKDF2 — coût assumé au déverrouillage, pénible à forcer. */
    const val ITERATIONS = 210_000

    fun levelOf(blob: ByteArray): VaultLevel? {
        if (blob.size < HEADER || !blob.copyOfRange(0, 4).contentEquals(MAGIC)) return null
        return VaultLevel.of(blob[4].toInt())
    }

    fun saltOf(blob: ByteArray): ByteArray? =
        if (blob.size < HEADER) null else blob.copyOfRange(5, 5 + SALT)

    fun iterationsOf(blob: ByteArray): Int {
        if (blob.size < HEADER) return ITERATIONS
        val o = 5 + SALT
        return ((blob[o].toInt() and 0xFF) shl 24) or ((blob[o + 1].toInt() and 0xFF) shl 16) or
            ((blob[o + 2].toInt() and 0xFF) shl 8) or (blob[o + 3].toInt() and 0xFF)
    }

    fun seal(level: VaultLevel, key: ByteArray, salt: ByteArray, iterations: Int, plain: ByteArray): ByteArray {
        val nonce = Crypto.randomBytes(NONCE)
        val header = ByteArray(HEADER)
        MAGIC.copyInto(header, 0)
        header[4] = level.id.toByte()
        salt.copyInto(header, 5, 0, SALT)
        val o = 5 + SALT
        header[o] = (iterations ushr 24).toByte()
        header[o + 1] = (iterations ushr 16).toByte()
        header[o + 2] = (iterations ushr 8).toByte()
        header[o + 3] = iterations.toByte()
        nonce.copyInto(header, o + 4)
        return header + Crypto.encrypt(key, nonce, plain, header)
    }

    /** Null si la clé est fausse ou le fichier abîmé — jamais de demi-résultat. */
    fun open(blob: ByteArray, key: ByteArray): ByteArray? {
        if (levelOf(blob) == null) return null
        val header = blob.copyOfRange(0, HEADER)
        val nonce = blob.copyOfRange(HEADER - NONCE, HEADER)
        return Crypto.decrypt(key, nonce, blob.copyOfRange(HEADER, blob.size), header)
    }
}
