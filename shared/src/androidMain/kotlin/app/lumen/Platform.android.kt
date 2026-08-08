package app.lumen

import android.os.Build

actual fun platformDeviceName(): String =
    "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }
