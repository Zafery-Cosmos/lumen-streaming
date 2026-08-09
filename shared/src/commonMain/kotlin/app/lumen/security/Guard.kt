package app.lumen.security

import app.lumen.db.epochMillis

/**
 * Contrôles d'environnement d'exécution.
 *
 * Ces vérifications ne remplacent aucune protection : elles renseignent
 * l'utilisateur sur la confiance qu'il peut accorder à l'appareil. Un appareil
 * signalé ici ne bloque pas l'application — bloquer se contourne, et punirait
 * surtout les gens qui maîtrisent leur propre matériel.
 */
enum class Signal { ELEVATED, INSPECTED, VIRTUAL, REPACKAGED }

expect object Env {
    fun probe(): Set<Signal>
}

/** Comparaison à durée constante : la durée ne dépend pas de l'endroit où ça diffère. */
fun steadyEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var acc = 0
    for (i in a.indices) acc = acc or (a[i].toInt() xor b[i].toInt())
    return acc == 0
}

fun steadyEquals(a: String, b: String): Boolean =
    steadyEquals(a.encodeToByteArray(), b.encodeToByteArray())

/**
 * Cadence des tentatives d'ouverture. Le compteur survit au redémarrage :
 * relancer l'application ne remet pas le délai à zéro.
 */
expect object AttemptLog {
    fun count(): Int
    fun lastAt(): Long
    fun record(failed: Boolean)
}

object Pace {
    /** Millisecondes à patienter avant un nouvel essai ; 0 = libre. */
    fun waitMs(): Long {
        val n = AttemptLog.count()
        if (n < 3) return 0
        // 2 s, 4 s, 8 s… plafonné à 5 minutes : une recherche exhaustive
        // devient impraticable, l'usage normal reste indolore.
        val backoff = (2_000L shl minOf(n - 3, 8)).coerceAtMost(300_000L)
        val since = epochMillis() - AttemptLog.lastAt()
        return (backoff - since).coerceAtLeast(0)
    }
}
