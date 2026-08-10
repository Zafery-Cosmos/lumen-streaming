package probe

import app.lumen.domain.AppSettings
import app.lumen.i18n.Lang
import app.lumen.i18n.T

/**
 * Vérifie le moteur de traduction sur le vrai code : bascule de langue,
 * repli du manquant, substitution, et surtout que TOUTE clé employée dans
 * les sources existe dans les DEUX langues.
 */
fun main() {
    // La sonde écrit dans les VRAIS réglages persistés (mêmes préférences que
    // l'app installée) : sans restauration, tester l'anglais changerait la
    // langue de l'utilisateur pour de bon.
    val original = AppSettings.language.value
    try {
        run(original)
    } finally {
        AppSettings.language.set(original)
        println("langue restaurée : $original")
    }
}

private fun run(@Suppress("UNUSED_PARAMETER") original: String) {
    var fail = 0
    fun check(name: String, ok: Boolean, detail: String = "") {
        if (ok) println("  ok   $name") else { fail++; println("  FAIL $name $detail") }
    }

    println("Bascule de langue")
    AppSettings.language.set("fr")
    check("français actif", T.lang == Lang.FR, T.lang.toString())
    check("nav.home = Accueil", T["nav.home"] == "Accueil", T["nav.home"])
    AppSettings.language.set("en")
    check("anglais actif", T.lang == Lang.EN, T.lang.toString())
    check("nav.home = Home", T["nav.home"] == "Home", T["nav.home"])

    println("Substitution")
    check("format", T.format("update.installed", "1.2.3") == "Installed version: 1.2.3", T.format("update.installed", "1.2.3"))

    println("Repli")
    check("clé inconnue renvoie la clé", T["zz.inconnu"] == "zz.inconnu", T["zz.inconnu"])

    println("Couverture des clés employées dans les sources")
    val srcRoot = java.io.File("shared/src/commonMain/kotlin/app/lumen")
    val used = mutableMapOf<String, MutableSet<String>>()
    val rx = Regex("""T(?:\.format)?\s*(?:\[|\()\s*"([a-zA-Z0-9_.]+)"""")
    srcRoot.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/i18n/") }.forEach { f ->
        rx.findAll(f.readText()).forEach { m ->
            used.getOrPut(m.groupValues[1]) { mutableSetOf() }.add(f.name)
        }
    }
    println("  ${used.size} clés employées")
    val fr = T.keys(Lang.FR)
    val en = T.keys(Lang.EN)
    val missingFr = used.keys.filter { it !in fr }
    val missingEn = used.keys.filter { it !in en }
    missingFr.forEach { println("  FAIL clé absente du FR : $it (${used[it]})") }
    missingEn.forEach { println("  FAIL clé absente de l'EN : $it (${used[it]})") }
    fail += missingFr.size + missingEn.size

    val unused = fr.filter { it !in used.keys }
    if (unused.isNotEmpty()) println("  (info) ${unused.size} clés définies mais pas encore employées")

    println()
    println(if (fail == 0) "TOUT PASSE" else "$fail ÉCHEC(S)")
    if (fail != 0) throw IllegalStateException("$fail échec(s)")
}
