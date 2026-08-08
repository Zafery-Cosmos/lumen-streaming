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
data class QuickConnectState(
    @SerialName("Secret") val secret: String = "",
    @SerialName("Code") val code: String = "",
    @SerialName("Authenticated") val authenticated: Boolean = false,
)
