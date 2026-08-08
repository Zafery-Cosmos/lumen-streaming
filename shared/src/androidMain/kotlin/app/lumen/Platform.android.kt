package app.lumen

import android.os.Build

actual fun platformDeviceName(): String =
    "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }

/** TODO(L9 hors-ligne) : DownloadManager Android. */
actual fun platformDownload(url: String, fileName: String): Boolean = false

/** TODO : Intent ACTION_VIEW (nécessite un Context). */
actual fun platformOpenUrl(url: String): Boolean = false

// L import de dossiers HLS est une fonction bureau (SAF cote Android plus tard).
actual suspend fun pickDirectory(title: String): String? = null
actual fun findHlsMaster(directory: String): String? = null
actual fun readLocalText(path: String): String? = null
actual fun resolveSibling(masterPath: String, relative: String): String = relative
actual fun parentFolderName(path: String): String = ""
