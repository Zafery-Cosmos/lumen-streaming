package app.lumen

import java.net.InetAddress

actual fun platformDeviceName(): String = try {
    InetAddress.getLocalHost().hostName
} catch (_: Exception) {
    System.getProperty("os.name") ?: "PC"
}
