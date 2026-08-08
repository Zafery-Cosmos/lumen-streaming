package app.lumen.auth

import app.lumen.api.JellyfinClient
import app.lumen.api.PublicUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Les étapes du parcours de connexion (plan §2). */
sealed interface ConnectStep {
    /** Saisie / validation de l'adresse du serveur. */
    data class Server(val error: String? = null, val busy: Boolean = false) : ConnectStep

    /** « Qui regarde ? » — profils publics du serveur. */
    data class Profiles(val users: List<PublicUser>) : ConnectStep

    /** Mot de passe pour un profil choisi (ou identifiants complets si liste masquée). */
    data class Credentials(
        val userName: String? = null,      // null → le serveur masque ses profils
        val error: String? = null,
        val busy: Boolean = false,
    ) : ConnectStep

    /** Quick Connect en attente de validation dans un autre client. */
    data class QuickConnect(val code: String, val error: String? = null) : ConnectStep

    /** Session ouverte — l'app peut basculer sur l'accueil. */
    data class Done(val session: StoredSession) : ConnectStep
}

/**
 * Machine à états du parcours de connexion. Pure logique, aucune dépendance UI :
 * les écrans Compose ne font qu'observer [step] et appeler les actions.
 */
class ConnectFlow(
    private val client: JellyfinClient,
    private val resolver: ServerResolver,
    private val store: SessionStore,
) {
    private val _step = MutableStateFlow<ConnectStep>(ConnectStep.Server())
    val step: StateFlow<ConnectStep> = _step

    /** Serveur validé à l'étape 1, utilisé par toutes les étapes suivantes. */
    var server: ResolvedServer? = null
        private set

    /** Reconnexion silencieuse au lancement : renvoie true si une session tient. */
    suspend fun tryRestore(): Boolean {
        val saved = store.load() ?: return false
        client.accessToken = saved.accessToken
        return if (client.tokenIsValid(saved.baseUrl)) {
            server = ResolvedServer(saved.baseUrl, client.publicInfo(saved.baseUrl))
            _step.value = ConnectStep.Done(saved)
            true
        } else {
            // Token révoqué côté serveur : on repart de l'écran serveur, sans purger
            // l'URL saisie pour ne pas la faire retaper.
            client.accessToken = null
            store.clear()
            false
        }
    }

    suspend fun submitServer(rawInput: String) {
        _step.value = ConnectStep.Server(busy = true)
        val resolved = resolver.resolve(rawInput)
        if (resolved == null) {
            _step.value = ConnectStep.Server(error = "Aucun serveur Jellyfin trouvé à cette adresse")
            return
        }
        server = resolved
        lastUsers = try {
            client.publicUsers(resolved.baseUrl)
        } catch (_: Exception) {
            emptyList()
        }
        _step.value = if (lastUsers.isEmpty()) ConnectStep.Credentials() else ConnectStep.Profiles(lastUsers)
    }

    private var lastUsers: List<PublicUser> = emptyList()

    fun pickProfile(user: PublicUser) {
        _step.value = ConnectStep.Credentials(userName = user.name)
    }

    /** Retour depuis l'écran mot de passe : profils s'il y en a, sinon serveur. */
    fun backToProfiles() {
        _step.value = if (lastUsers.isNotEmpty()) ConnectStep.Profiles(lastUsers) else ConnectStep.Server()
    }

    fun backToServer() {
        _step.value = ConnectStep.Server()
    }

    suspend fun login(username: String, password: String) {
        val srv = server ?: return
        val current = _step.value as? ConnectStep.Credentials ?: ConnectStep.Credentials()
        _step.value = current.copy(busy = true, error = null)
        try {
            val auth = client.authenticateByName(srv.baseUrl, username, password)
            finish(auth.user.id, auth.user.name, auth.accessToken)
        } catch (e: Exception) {
            _step.value = current.copy(
                busy = false,
                error = "Connexion refusée — vérifie le mot de passe",
            )
        }
    }

    /** Lance Quick Connect et polle jusqu'à validation dans un autre client. */
    suspend fun startQuickConnect() {
        val srv = server ?: return
        val initial = try {
            client.quickConnectInitiate(srv.baseUrl)
        } catch (_: Exception) {
            _step.update {
                (it as? ConnectStep.Credentials)?.copy(error = "Quick Connect indisponible sur ce serveur") ?: it
            }
            return
        }
        _step.value = ConnectStep.QuickConnect(code = initial.code)
        while (_step.value is ConnectStep.QuickConnect) {
            delay(2_000)
            val state = try {
                client.quickConnectState(srv.baseUrl, initial.secret)
            } catch (_: Exception) {
                continue
            }
            if (state.authenticated) {
                val auth = client.authenticateWithQuickConnect(srv.baseUrl, initial.secret)
                finish(auth.user.id, auth.user.name, auth.accessToken)
                return
            }
        }
    }

    fun cancelQuickConnect() {
        _step.value = ConnectStep.Credentials()
    }

    fun logout() {
        store.clear()
        client.accessToken = null
        _step.value = ConnectStep.Server()
    }

    private fun finish(userId: String, userName: String, token: String) {
        val srv = server ?: return
        val session = StoredSession(
            baseUrl = srv.baseUrl,
            serverName = srv.info.serverName,
            userId = userId,
            userName = userName,
            accessToken = token,
        )
        store.save(session)
        _step.value = ConnectStep.Done(session)
    }
}
