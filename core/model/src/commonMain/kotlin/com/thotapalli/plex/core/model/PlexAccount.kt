package com.thotapalli.plex.core.model

/** The signed in Plex account. One account per device, remembered until sign out. */
data class PlexAccount(
    val id: Long,
    val uuid: String,
    val username: String,
    val title: String,
    val email: String?,
    val thumbUrl: String?,
)

/**
 * A Plex Home user.
 *
 * More than one shows a picker after sign in. Exactly one skips the picker silently.
 * See CLAUDE.md section 2.
 */
data class HomeUser(
    val id: Long,
    val uuid: String,
    val title: String,
    val admin: Boolean,
    val protected: Boolean,
    val thumbUrl: String?,
)
