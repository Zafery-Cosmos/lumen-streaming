package app.lumen

import android.os.Build

actual fun platformDeviceName(): String =
    "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }

/** TODO(L9 hors-ligne) : DownloadManager Android. */
actual fun platformDownload(url: String, fileName: String): Boolean = false
