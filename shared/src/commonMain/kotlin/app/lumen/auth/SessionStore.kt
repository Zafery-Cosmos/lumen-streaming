package app.lumen.auth

import app.lumen.security.SecureStore
import com.russhwolf.settings.Settings
import kotlin.random.Random

/** Session persistée : de quoi se reconnecter silencieusement au lancement. */
@kotlinx.serialization.Serializable
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
 * Le jeton d'accès et la liste des serveurs passent par [SecureStore] : ils
 * ouvrent la médiathèque de l'utilisateur, ils n'ont rien à faire en clair.
 * Le deviceId, lui, n'est pas un secret et reste dans les préférences.
 */
class SessionStore(private val settings: Settings = Settings()) {

    /** DeviceId stable exigé par l'en-tête MediaBrowser — généré une seule fois. */
    val deviceId: String
        get() = settings.getStringOrNull(KEY_DEVICE_ID) ?: buildString {
            repeat(32) { append("0123456789abcdef"[Random.nextInt(16)]) }
        }.also { settings.putString(KEY_DEVICE_ID, it) }

    fun save(session: StoredSession) {
        SecureStore.put(KEY_BASE_URL, session.baseUrl)
        SecureStore.put(KEY_SERVER_NAME, session.serverName)
        SecureStore.put(KEY_USER_ID, session.userId)
        SecureStore.put(KEY_USER_NAME, session.userName)
        SecureStore.put(KEY_TOKEN, session.accessToken)
    }

    fun load(): StoredSession? {
        val baseUrl = SecureStore.get(KEY_BASE_URL) ?: return null
        val token = SecureStore.get(KEY_TOKEN) ?: return null
        return StoredSession(
            baseUrl = baseUrl,
            serverName = SecureStore.get(KEY_SERVER_NAME).orEmpty(),
            userId = SecureStore.get(KEY_USER_ID).orEmpty(),
            userName = SecureStore.get(KEY_USER_NAME).orEmpty(),
            accessToken = token,
        )
    }

    /** Déconnexion : on oublie la session mais jamais le deviceId. */
    fun clear() {
        listOf(KEY_BASE_URL, KEY_SERVER_NAME, KEY_USER_ID, KEY_USER_NAME, KEY_TOKEN)
            .forEach { SecureStore.put(it, null) }
    }

    // --- Multi-serveurs (plan §2) : bascule dynamique -----------------------

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Tous les serveurs connus (sessions valides mémorisées), par URL. */
    fun listServers(): List<StoredSession> =
        SecureStore.get(KEY_SERVERS)?.let {
            runCatching { json.decodeFromString<List<StoredSession>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    /** Mémorise (ou met à jour) un serveur dans la liste de bascule. */
    fun rememberServer(session: StoredSession) {
        val updated = listServers().filterNot { it.baseUrl == session.baseUrl } + session
        SecureStore.put(KEY_SERVERS, json.encodeToString(updated))
    }

    fun forgetServer(baseUrl: String) {
        SecureStore.put(
            KEY_SERVERS,
            json.encodeToString(listServers().filterNot { it.baseUrl == baseUrl }),
        )
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_BASE_URL = "session.base_url"
        const val KEY_SERVER_NAME = "session.server_name"
        const val KEY_USER_ID = "session.user_id"
        const val KEY_USER_NAME = "session.user_name"
        const val KEY_TOKEN = "session.token"
        const val KEY_SERVERS = "servers.v1"
    }
}
