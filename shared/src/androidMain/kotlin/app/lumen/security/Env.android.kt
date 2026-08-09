package app.lumen.security

import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import app.lumen.AndroidCtx
import java.io.File
import java.security.MessageDigest

private val markers = listOf(
    "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su",
    "/data/local/bin/su", "/system/sd/xbin/su", "/system/app/Superuser.apk",
    "/su/bin/su", "/magisk",
)

actual object Env {
    actual fun probe(): Set<Signal> = buildSet {
        if (markers.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
            runCatching { Build.TAGS?.contains("test-keys") == true }.getOrDefault(false)
        ) {
            add(Signal.ELEVATED)
        }
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) add(Signal.INSPECTED)
        if (looksVirtual()) add(Signal.VIRTUAL)
        if (!fingerprintMatches()) add(Signal.REPACKAGED)
    }

    private fun looksVirtual(): Boolean = runCatching {
        val f = Build.FINGERPRINT.orEmpty()
        f.startsWith("generic") || f.contains("vbox") || f.contains("emulator") ||
            Build.MODEL.orEmpty().contains("Emulator") ||
            Build.HARDWARE.orEmpty().let { it == "goldfish" || it == "ranchu" }
    }.getOrDefault(false)

    /**
     * Empreinte du certificat de signature, comparée à celle enregistrée au
     * premier lancement. Un paquet réassemblé porte forcément une autre
     * signature — l'écart devient visible pour l'utilisateur.
     */
    private fun fingerprintMatches(): Boolean = runCatching {
        val ctx = AndroidCtx.app
        val sigs = ctx.packageManager
            .getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.apkContentsSigners
            ?: return true
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sigs.first().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val ref = File(ctx.filesDir, ".pkg")
        if (!ref.exists()) {
            ref.writeText(digest)
            true
        } else {
            steadyEquals(ref.readText().trim(), digest)
        }
    }.getOrDefault(true)
}
