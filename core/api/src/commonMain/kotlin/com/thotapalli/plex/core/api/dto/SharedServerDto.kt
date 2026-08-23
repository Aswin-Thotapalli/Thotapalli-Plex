package com.thotapalli.plex.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shapes for https://plex.tv/api/v2/shared_servers, the library-sharing API.
 *
 * Sharing is how the owner grants another Plex account access to selected libraries
 * (CLAUDE.md section 2). These are plain JSON, like the rest of the plex.tv v2 surface, and
 * every field is defensively nullable because the API carries no compatibility guarantee.
 */

/** The body POSTed to share libraries with an account by email. */
@Serializable
data class ShareServerRequest(
    val machineIdentifier: String,
    val invitedEmail: String,
    /** Numeric library section ids to share. */
    val librarySectionIds: List<Int>,
    val settings: ShareServerSettings = ShareServerSettings(),
)

/**
 * Per-share settings. Downloads are in scope for every target (CLAUDE.md section 11), so
 * sync is allowed by default; the rest of Plex's toggles are left at the server default.
 */
@Serializable
data class ShareServerSettings(
    val allowSync: Boolean = true,
)

/**
 * One entry from `GET /api/v2/shared_servers`. The invited account may be nested under
 * `invited`, or only present as the flat `invitedEmail` when the invite is still pending.
 */
@Serializable
data class SharedServerDto(
    val id: Long = 0,
    val name: String? = null,
    val invitedId: Long? = null,
    val invitedEmail: String? = null,
    @SerialName("invited") val invited: InvitedAccountDto? = null,
)

@Serializable
data class InvitedAccountDto(
    val id: Long = 0,
    val uuid: String? = null,
    val username: String? = null,
    val title: String? = null,
    val email: String? = null,
)
