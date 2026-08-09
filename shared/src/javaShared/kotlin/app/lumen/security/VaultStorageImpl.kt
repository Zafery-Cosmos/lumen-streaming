package app.lumen.security

import java.io.File

// Un seul fichier, extension `.lmn` : c'est lui qu'un futur import/export
// d'un compte transportera d'un appareil à l'autre.
private fun file(): File = File(vaultDir(), "lumen.lmn")

actual object VaultStorage {
    actual fun read(): ByteArray? = runCatching { file().takeIf { it.isFile }?.readBytes() }.getOrNull()

    actual fun write(blob: ByteArray) {
        val target = file()
        target.parentFile?.mkdirs()
        // Écriture par fichier temporaire puis renommage : une coupure de
        // courant ne peut pas laisser un coffre à moitié écrit, donc illisible.
        val tmp = File(target.parentFile, "lumen.lmn.tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(target)) {
            target.writeBytes(blob)
            tmp.delete()
        }
    }

    actual fun exists(): Boolean = file().isFile
    actual fun path(): String = file().absolutePath
}
