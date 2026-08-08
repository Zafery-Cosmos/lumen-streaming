package app.lumen.cast

// La diffusion Android passera par le SDK Cast de Google, qui exige une
// Activity et son propre cycle de vie : ça ne se partage pas avec le desktop.
actual suspend fun discoverCastDevices(timeoutMs: Long): List<CastDevice> = emptyList()

actual suspend fun castPlay(device: CastDevice, url: String, title: String): Boolean = false

actual suspend fun castStop(device: CastDevice): Boolean = false
