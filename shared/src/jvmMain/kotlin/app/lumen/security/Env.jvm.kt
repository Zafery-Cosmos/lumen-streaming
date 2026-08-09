package app.lumen.security

import java.lang.management.ManagementFactory

actual object Env {
    actual fun probe(): Set<Signal> = buildSet {
        // Un agent de débogage attaché peut lire la mémoire du processus,
        // donc la clé de session. C'est le seul signal réellement utile ici.
        if (inspected()) add(Signal.INSPECTED)
    }

    private fun inspected(): Boolean = runCatching {
        ManagementFactory.getRuntimeMXBean().inputArguments.any {
            it.startsWith("-agentlib:jdwp") || it.startsWith("-Xrunjdwp") || it.startsWith("-javaagent")
        }
    }.getOrDefault(false)
}
