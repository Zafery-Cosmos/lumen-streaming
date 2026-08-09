package app.lumen

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment

actual fun platformDeviceName(): String =
    "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }

/** DownloadManager : notification système, reprise, dossier Téléchargements. */
actual fun platformDownload(url: String, fileName: String): Boolean = runCatching {
    val manager = AndroidCtx.app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(
        DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED),
    )
    true
}.getOrDefault(false)

/** http comme magnet : l'application associée du système prend le relais. */
actual fun platformOpenUrl(url: String): Boolean = runCatching {
    AndroidCtx.app.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
}.getOrDefault(false)

// L import de dossiers HLS est une fonction bureau (SAF cote Android plus tard).
actual suspend fun pickDirectory(title: String): String? = null
actual fun findHlsMaster(directory: String): String? = null
actual fun readLocalText(path: String): String? = null
actual fun resolveSibling(masterPath: String, relative: String): String = relative
actual fun parentFolderName(path: String): String = ""
actual fun localFileUri(path: String): String =
    if (path.startsWith("file:")) path else "file://" + java.io.File(path).toURI().rawPath
