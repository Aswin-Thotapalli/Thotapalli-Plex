package com.thotapalli.plex.core.api.mapper

import com.thotapalli.plex.core.api.dto.HomeUsersDto
import com.thotapalli.plex.core.api.dto.ResourceConnectionDto
import com.thotapalli.plex.core.api.dto.ResourceDto
import com.thotapalli.plex.core.api.dto.UserDto
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.model.PlexAccount
import com.thotapalli.plex.core.model.PlexServer
import com.thotapalli.plex.core.model.ServerConnection

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
