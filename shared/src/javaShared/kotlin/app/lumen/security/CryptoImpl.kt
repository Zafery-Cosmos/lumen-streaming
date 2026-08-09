package app.lumen.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val rng = SecureRandom()

actual object Crypto {

    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    actual fun encrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // 128 bits de marque d'authentification : le maximum prévu par GCM.
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plain)
    }

    actual fun decrypt(key: ByteArray, nonce: ByteArray, cipher: ByteArray, aad: ByteArray): ByteArray? =
        runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            c.updateAAD(aad)
            c.doFinal(cipher)
        }.getOrNull()   // AEADBadTagException = clé fausse ou contenu modifié

    actual fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // platformDeviceKey() est fourni séparément par jvmMain et androidMain :
    // ce fichier est compilé dans les deux, chacun apporte sa version. Pas
    // d'expect/actual ici — ce dossier n'est pas un source set commun.
    actual fun deviceKey(): ByteArray? = platformDeviceKey()
}
