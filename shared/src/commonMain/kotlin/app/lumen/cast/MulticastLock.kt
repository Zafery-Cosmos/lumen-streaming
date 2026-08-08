package app.lumen.cast

/**
 * Exécute [block] avec la réception multicast garantie.
 *
 * Android filtre les paquets multicast par défaut pour économiser la
 * batterie : sans verrou Wi-Fi, les réponses mDNS des Chromecast n'arrivent
 * jamais. Les autres plateformes n'ont rien à faire.
 */
expect suspend fun <T> withMulticastLock(block: suspend () -> T): T
