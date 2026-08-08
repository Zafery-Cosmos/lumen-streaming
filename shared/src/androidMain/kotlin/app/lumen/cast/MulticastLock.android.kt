package app.lumen.cast

import android.content.Context
import android.net.wifi.WifiManager
import app.lumen.AndroidCtx

actual suspend fun <T> withMulticastLock(block: suspend () -> T): T {
    val wifi = runCatching {
        AndroidCtx.app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }.getOrNull()
    val lock = wifi?.createMulticastLock("lumen-cast")?.apply { acquire() }
    return try {
        block()
    } finally {
        runCatching { lock?.release() }
    }
}
