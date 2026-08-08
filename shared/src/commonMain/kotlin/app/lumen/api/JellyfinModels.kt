package app.lumen.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Modèles minimaux de l'API Jellyfin (10.11) — on ne mappe que ce qu'on consomme.

@Serializable
data class PublicSystemInfo(
    @SerialName("ServerName") val serverName: String = "",
    @SerialName("Version") val version: String = "",
    @SerialName("Id") val id: String = "",
    @SerialName("ProductName") val productName: String? = null,
)

@Serializable
data class PublicUser(
    @SerialName("Name") val name: String = "",
    @SerialName("Id") val id: String = "",
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("HasPassword") val hasPassword: Boolean = true,
)

@Serializable
data class AuthenticationResult(
    @SerialName("User") val user: AuthenticatedUser,
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String = "",
)

@Serializable
data class AuthenticatedUser(
    @SerialName("Name") val name: String = "",
    @SerialName("Id") val id: String = "",
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
data class ItemsResult(
    @SerialName("Items") val items: List<BaseItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)

/** Item Jellyfin générique — film, série, saison, épisode ou vue de bibliothèque. */
@Serializable
data class BaseItem(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("ProviderIds") val providerIds: Map<String, String> = emptyMap(),
    @SerialName("UserData") val userData: UserData? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    // Champs spécifiques aux épisodes / saisons
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
) {
    /** Durée en minutes (les ticks Jellyfin valent 100 ns). */
    val runTimeMinutes: Int? get() = runTimeTicks?.let { (it / 600_000_000L).toInt() }
}

@Serializable
data class UserData(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
)

@Serializable
data class QuickConnectState(
    @SerialName("Secret") val secret: String = "",
    @SerialName("Code") val code: String = "",
    @SerialName("Authenticated") val authenticated: Boolean = false,
)
