package app.lumen.security

import com.russhwolf.settings.Settings

/**
 * Rangement des valeurs sensibles.
 *
 * Elles vivaient jusqu'ici dans les préférences de la plateforme, en texte
 * clair : un fichier XML lisible par n'importe quel programme tournant sous le
 * même compte. Elles passent maintenant par le conteneur chiffré.
 *
 * La migration est faite au premier accès et ne se voit pas : la valeur est
 * relue depuis l'ancien emplacement, réécrite dans le conteneur, puis
 * **effacée de l'ancien**. Rien à faire pour l'utilisateur.
 */
object SecureStore {
    private val plain = Settings()

    /** Ouvre le coffre si personne ne l'a encore fait (niveau lié à l'appareil). */
    private fun ready(): Boolean {
        if (Vault.isLocked()) return false
        if (Vault.keys().isEmpty() && !VaultStorage.exists()) return Vault.openWithDevice()
        if (Vault["__probe"] == null && Vault.keys().isEmpty()) return Vault.openWithDevice()
        return true
    }

    fun get(name: String): String? {
        if (!ready()) return plain.getStringOrNull(name)
        Vault[name]?.let { return it }
        // Encore à l'ancien emplacement : on récupère, on range, on nettoie.
        val legacy = plain.getStringOrNull(name) ?: return null
        Vault.put(name, legacy)
        plain.remove(name)
        return legacy
    }

    fun put(name: String, value: String?) {
        if (!ready()) {
            if (value == null) plain.remove(name) else plain.putString(name, value)
            return
        }
        Vault.put(name, value)
        plain.remove(name)
    }
}
