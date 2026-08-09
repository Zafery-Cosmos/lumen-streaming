package app.lumen.security

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Niveau L2 sur bureau : la clé de données est écrite dans le dossier de
 * configuration, en accès **propriétaire uniquement** (0600).
 *
 * Il faut être clair sur ce que ça vaut : cela protège d'une sauvegarde, d'un
 * disque récupéré ou d'un autre compte de la machine — PAS d'un programme qui
 * tourne déjà sous ton identité, puisqu'il peut lire le même fichier. Sur
 * bureau, il n'existe pas d'équivalent universel du Keystore d'Android.
 *
 * Le seul niveau réellement inviolable ici est le **L3** : mot de passe maître,
 * clé dérivée à chaque ouverture, jamais écrite nulle part.
 */
internal fun platformDeviceKey(): ByteArray? = runCatching {
    val dir = File(System.getProperty("user.home"), ".config/lumen").apply { mkdirs() }
    val file = File(dir, "vault.key")
    if (file.exists()) {
        file.readBytes().takeIf { it.size == 32 }
    } else {
        val key = Crypto.randomBytes(32)
        file.writeBytes(key)
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
        key
    }
}.getOrNull()

/** Dossier de configuration du bureau. */
internal fun vaultDir(): File =
    File(System.getProperty("user.home"), ".config/lumen").apply { mkdirs() }
