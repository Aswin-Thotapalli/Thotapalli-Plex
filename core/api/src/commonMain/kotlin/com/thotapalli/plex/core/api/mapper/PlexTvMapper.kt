package com.thotapalli.plex.core.api.mapper

import com.thotapalli.plex.core.api.dto.HomeUsersDto
import com.thotapalli.plex.core.api.dto.ResourceConnectionDto
import com.thotapalli.plex.core.api.dto.ResourceDto
import com.thotapalli.plex.core.api.dto.SharedServerDto
import com.thotapalli.plex.core.api.dto.UserDto
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.model.PlexAccount
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.ServerConnection
import com.thotapalli.plex.core.model.SharedUser

fun UserDto.toPlexAccount() = PlexAccount(
    id = id,
    uuid = uuid,
    username = username,
    title = title.ifBlank { username },
    email = email,
    thumbUrl = thumb,
)

fun HomeUsersDto.toHomeUsers(): List<HomeUser> = users.map {
    HomeUser(
        id = it.id,
        uuid = it.uuid,
        title = it.title,
        admin = it.admin,
        protected = it.protected,
        thumbUrl = it.thumb,
    )
}

/**
 * Media servers only, and only those that handed back an access token. A resource with no
 * access token cannot be reached, so keeping it would produce a server that fails every
 * request.
 */
fun List<ResourceDto>.toPlexServers(): List<PlexServer> =
    filter { it.providesServer && !it.accessToken.isNullOrBlank() }
        .map { it.toPlexServer() }

fun ResourceDto.toPlexServer() = PlexServer(
    machineIdentifier = clientIdentifier,
    name = name,
    accessToken = accessToken.orEmpty(),
    owned = owned,
    connections = connections.map { it.toServerConnection() },
)

fun ResourceConnectionDto.toServerConnection() = ServerConnection(
    uri = uri,
    local = local,
    relay = relay,
)

/**
 * Grantees of the shared server. The invited account is preferred where plex.tv nests it;
 * a still-pending invite may carry only an email, so the flat fields are the fallback and an
 * entry with no identifier at all is dropped rather than surfaced as a blank row.
 */
fun List<SharedServerDto>.toSharedUsers(): List<SharedUser> = mapNotNull { dto ->
    val id = dto.invited?.id?.takeIf { it != 0L }?.toString()
        ?: dto.invitedId?.takeIf { it != 0L }?.toString()
        ?: dto.id.takeIf { it != 0L }?.toString()
        ?: return@mapNotNull null
    SharedUser(
        id = id,
        email = dto.invited?.email ?: dto.invitedEmail ?: "",
        username = dto.invited?.username ?: dto.invited?.title,
    )
}
