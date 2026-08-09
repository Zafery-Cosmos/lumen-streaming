package app.lumen.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.lumen.AndroidCtx
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

private const val ALIAS = "lumen.vault.v1"

/**
 * Niveau L2 sur Android : la clé maîtresse vit dans l'**Android Keystore** et
 * n'en sort jamais — même l'app ne peut pas la lire, elle ne peut que demander
 * au système de chiffrer/déchiffrer. On s'en sert donc pour emballer une clé de
 * données tirée au hasard, seule à être stockée (emballée) sur le disque.
 *
 * Conséquence utile : copier les fichiers de l'app sur un autre téléphone ne
 * donne rien, la clé du Keystore ne se copie pas.
 */
internal fun platformDeviceKey(): ByteArray? = runCatching {
    val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val key = (ks.getEntry(ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.secretKey
        ?: run {
            fun spec(secureElement: Boolean) = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply {
                    // Puce dédiée quand l'appareil en a une : la clé ne réside
                    // alors même plus dans la mémoire du système principal.
                    if (secureElement && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setIsStrongBoxBacked(true)
                    }
                }
                .build()

            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            runCatching { gen.init(spec(true)); gen.generateKey() }
                .getOrElse { gen.init(spec(false)); gen.generateKey() }
        }

    val wrapped = File(AndroidCtx.app.filesDir, "vault.key")
    if (wrapped.exists()) {
        val blob = wrapped.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        cipher.doFinal(blob.copyOfRange(12, blob.size))
    } else {
        val dataKey = Crypto.randomBytes(32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        wrapped.writeBytes(cipher.iv + cipher.doFinal(dataKey))
        dataKey
    }
}.getOrNull()

/** Dossier privé de l'app — inaccessible aux autres applications. */
internal fun vaultDir(): File = AndroidCtx.app.filesDir
