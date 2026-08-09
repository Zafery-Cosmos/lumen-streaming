package app.lumen.security

/**
 * Les identifiants de connexion (clés de bucket, mots de passe WebDAV et FTP)
 * ne sont pas écrits en base : la base ne garde qu'une **référence**, la valeur
 * vivant dans le coffre chiffré.
 *
 * Une base copiée ou exfiltrée ne contient donc que des renvois inertes.
 */
object SecretRef {
    private const val MARK = "lmn:"

    /** Range [value] dans le coffre et renvoie la référence à stocker en base. */
    fun store(id: String, field: String, value: String): String {
        if (value.isEmpty()) return value
        val name = "$MARK$id.$field"
        SecureStore.put(name, value)
        // Si le coffre n'a pas pu s'ouvrir, SecureStore a écrit ailleurs :
        // on renvoie quand même la référence, elle sera résolue pareil.
        return name
    }

    /** Rend la valeur réelle. Une donnée d'avant la migration est renvoyée telle quelle. */
    fun resolve(stored: String): String =
        if (stored.startsWith(MARK)) SecureStore.get(stored).orEmpty() else stored

    /** Efface la valeur associée à une source supprimée. */
    fun forget(stored: String) {
        if (stored.startsWith(MARK)) SecureStore.put(stored, null)
    }
}
