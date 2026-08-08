package app.lumen.domain

import app.lumen.api.BaseItem
import app.lumen.api.JellyfinClient
import app.lumen.auth.StoredSession

/**
 * Actions rapides des cartes (vu, favori, playlists) — mêmes effets que le
 * menu contextuel du client web Jellyfin, synchronisés côté serveur.
 */
class CardActions(private val client: JellyfinClient, private val session: StoredSession) {

    private fun raw(cardId: String) = cardId.removePrefix("jf:")

    suspend fun setPlayed(cardId: String, played: Boolean) = runCatching {
        client.setPlayed(session.baseUrl, session.userId, raw(cardId), played)
    }.isSuccess

    suspend fun setFavorite(cardId: String, favorite: Boolean) = runCatching {
        client.setFavorite(session.baseUrl, session.userId, raw(cardId), favorite)
    }.isSuccess

    suspend fun playlists(): List<BaseItem> = runCatching {
        client.playlists(session.baseUrl, session.userId).items
    }.getOrDefault(emptyList())

    suspend fun addToPlaylist(playlistId: String, cardId: String) = runCatching {
        client.addToPlaylist(session.baseUrl, session.userId, playlistId, raw(cardId))
    }.isSuccess

    suspend fun createPlaylist(name: String, cardId: String) = runCatching {
        client.createPlaylist(session.baseUrl, session.userId, name, raw(cardId))
    }.isSuccess

    /** « Regarder plus tard » : la playlist dédiée, créée au premier usage. */
    suspend fun addToWatchLater(cardId: String): Boolean {
        val existing = playlists().firstOrNull { it.name.equals(WATCH_LATER, ignoreCase = true) }
        return if (existing != null) {
            addToPlaylist(existing.id, cardId)
        } else {
            createPlaylist(WATCH_LATER, cardId)
        }
    }

    companion object {
        const val WATCH_LATER = "Regarder plus tard"
    }
}
