package app.lumen.cast

actual suspend fun <T> withMulticastLock(block: suspend () -> T): T = block()
