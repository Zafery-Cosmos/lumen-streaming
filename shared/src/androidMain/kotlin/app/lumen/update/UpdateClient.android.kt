package app.lumen.update

import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import app.lumen.AndroidCtx
import java.io.File

actual val updatePlatformKey: String = "android"

/**
 * L'APK est déposé dans le cache de l'app, puis servi à l'installateur du
 * système via un FileProvider : depuis Android 7, un `file://` pointant dans
 * nos données privées est refusé (FileUriExposedException).
 */
private fun updatesDir(): File =
    File(AndroidCtx.app.cacheDir, "updates").apply { mkdirs() }

actual fun saveUpdateFile(fileName: String, bytes: ByteArray): String? = runCatching {
    // On ne garde pas les APK précédents : ils pèsent lourd pour rien.
    updatesDir().listFiles()?.forEach { it.delete() }
    val target = File(updatesDir(), fileName)
    target.writeBytes(bytes)
    target.absolutePath
}.getOrNull()

/**
 * Lance l'installation. Android demandera l'autorisation « installer des
 * applications inconnues » la première fois — c'est le parcours normal d'une
 * app distribuée hors Play Store, et il n'y a pas de contournement possible.
 *
 * L'APK DOIT porter la même signature que l'app installée, sinon le système
 * refuse la mise à jour (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
 */
actual fun applyUpdate(path: String): Boolean = runCatching {
    val file = File(path)
    if (!file.exists()) return false
    val ctx = AndroidCtx.app
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
    true
}.getOrDefault(false)

/** True si le système autorise déjà Lumen à installer des APK. */
fun canInstallPackages(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AndroidCtx.app.packageManager.canRequestPackageInstalls()
    } else {
        true
    }

actual fun nowMillis(): Long = System.currentTimeMillis()
