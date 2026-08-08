package app.lumen.auth

import com.russhwolf.settings.Settings
import kotlin.random.Random

/** Session persistée : de quoi se reconnecter silencieusement au lancement. */
data class StoredSession(
    val baseUrl: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val accessToken: String,
)

/**
 * Persistance de la session et de l'identité de l'appareil.
 *
 * TODO(L10b) : chiffrer le token au repos (Keystore Android / keyring desktop).
 * Pour l'instant multiplatform-settings suffit à faire tourner L1.
 */
class SessionStore(private val settings: Settings = Settings()) {

    /** DeviceId stable exigé par l'en-tête MediaBrowser — généré une seule fois. */
    val deviceId: String
        get() = settings.getStringOrNull(KEY_DEVICE_ID) ?: buildString {
            repeat(32) { append("0123456789abcdef"[Random.nextInt(16)]) }
        }.also { settings.putString(KEY_DEVICE_ID, it) }

    fun save(session: StoredSession) {
        settings.putString(KEY_BASE_URL, session.baseUrl)
        settings.putString(KEY_SERVER_NAME, session.serverName)
        settings.putString(KEY_USER_ID, session.userId)
        settings.putString(KEY_USER_NAME, session.userName)
        settings.putString(KEY_TOKEN, session.accessToken)
    }

    fun load(): StoredSession? {
        val baseUrl = settings.getStringOrNull(KEY_BASE_URL) ?: return null
        val token = settings.getStringOrNull(KEY_TOKEN) ?: return null
        return StoredSession(
            baseUrl = baseUrl,
            serverName = settings.getString(KEY_SERVER_NAME, ""),
            userId = settings.getString(KEY_USER_ID, ""),
            userName = settings.getString(KEY_USER_NAME, ""),
            accessToken = token,
        )
    }

    /** Déconnexion : on oublie la session mais jamais le deviceId. */
    fun clear() {
        listOf(KEY_BASE_URL, KEY_SERVER_NAME, KEY_USER_ID, KEY_USER_NAME, KEY_TOKEN)
            .forEach(settings::remove)
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_BASE_URL = "session.base_url"
        const val KEY_SERVER_NAME = "session.server_name"
        const val KEY_USER_ID = "session.user_id"
        const val KEY_USER_NAME = "session.user_name"
        const val KEY_TOKEN = "session.token"
    }
}
