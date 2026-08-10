package app.lumen.i18n

import app.lumen.domain.AppSettings

/** Les langues proposées dans les réglages. */
enum class Lang(val code: String, val label: String) {
    FR("fr", "Français"),
    EN("en", "English"),
}

/**
 * Traduction de l'interface.
 *
 * Volontairement construit comme [app.lumen.ui.theme.LumenColors] : un objet
 * dont les valeurs sont lues à travers un getter qui consulte l'état
 * [AppSettings.language]. Compose observe cette lecture pendant la
 * composition, donc changer de langue retraduit l'écran immédiatement — sans
 * redémarrage, et sans avoir à propager un paramètre dans toute l'app.
 *
 * L'accès non composable fonctionne aussi (messages d'erreur construits dans
 * le domaine, par exemple), ce qu'un `stringResource()` ne permettrait pas.
 *
 * Une clé absente retombe sur le français, puis sur la clé elle-même : une
 * traduction manquante n'a jamais pu faire planter l'app. Le filet réel est
 * ailleurs — une sonde vérifie que toute clé employée dans le code existe
 * dans les deux langues.
 */
object T {

    /** La langue effective : le réglage, ou celle du système si « auto ». */
    val lang: Lang
        get() = when (AppSettings.language.value) {
            "fr" -> Lang.FR
            "en" -> Lang.EN
            else -> if (systemLanguageCode().startsWith("fr")) Lang.FR else Lang.EN
        }

    // Les tables sont scindées en deux fichiers (migration en deux temps) ;
    // le second lot prime sur le premier en cas de doublon de clé.
    private val frAll: Map<String, String> by lazy { FR + FR2 }
    private val enAll: Map<String, String> by lazy { EN + EN2 }

    private val table: Map<String, String>
        get() = if (lang == Lang.EN) enAll else frAll

    /** Le texte pour cette clé, dans la langue courante. */
    operator fun get(key: String): String = table[key] ?: frAll[key] ?: key

    /** Idem, avec substitution des repères {0}, {1}… dans l'ordre donné. */
    fun format(key: String, vararg args: Any?): String {
        var out = get(key)
        args.forEachIndexed { i, a -> out = out.replace("{$i}", a.toString()) }
        return out
    }

    /** Les clés connues — utilisé par la sonde de couverture. */
    fun keys(l: Lang): Set<String> = if (l == Lang.EN) enAll.keys else frAll.keys
}

/** Code langue du système (« fr », « fr-FR », « en »…), selon la plateforme. */
expect fun systemLanguageCode(): String
