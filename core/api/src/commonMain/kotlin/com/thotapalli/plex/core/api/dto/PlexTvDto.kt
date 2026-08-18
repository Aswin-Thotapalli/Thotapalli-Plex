package com.thotapalli.plex.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shapes for https://plex.tv/api/v2. These are plain JSON, unlike the server
 * endpoints, which wrap everything in a MediaContainer.
 */

@Serializable
data class PinDto(
    val id: Long,
    val code: String,
    val product: String? = null,
    val trusted: Boolean = false,
    val clientIdentifier: String? = null,
    val expiresIn: Int? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val authToken: String? = null,
    val newRegistration: Boolean? = null,
)

@Serializable
data class UserDto(
    val id: Long = 0,
    val uuid: String = "",
    val username: String = "",
    val title: String = "",
    val email: String? = null,
    val thumb: String? = null,
)

@Serializable
data class HomeUserDto(
    val id: Long = 0,
    val uuid: String = "",
    val title: String = "",
    val admin: Boolean = false,
    val guest: Boolean = false,
    val protected: Boolean = false,
    val restricted: Boolean = false,
    val thumb: String? = null,
)

@Serializable
data class HomeUsersDto(
    val id: Long = 0,
    val name: String = "",
    val users: List<HomeUserDto> = emptyList(),
)

@Serializable
data class ResourceDto(
    val name: String = "",
    val product: String? = null,
    val productVersion: String? = null,
    val platform: String? = null,
    val device: String? = null,
    val clientIdentifier: String = "",
    val provides: String = "",
    val accessToken: String? = null,
    val owned: Boolean = false,
    val home: Boolean = false,
    val presence: Boolean = false,
    val httpsRequired: Boolean = false,
    val publicAddressMatches: Boolean = false,
    val connections: List<ResourceConnectionDto> = emptyList(),
) {
    /** Only entries whose provides list contains "server" are media servers. */
    val providesServer: Boolean
        get() = provides.split(',').any { it.trim().equals("server", ignoreCase = true) }
}

@Serializable
data class ResourceConnectionDto(
    val protocol: String = "",
    val address: String = "",
    val port: Int = 0,
    val uri: String = "",
    val local: Boolean = false,
    val relay: Boolean = false,
    @SerialName("IPv6") val ipv6: Boolean = false,
)
