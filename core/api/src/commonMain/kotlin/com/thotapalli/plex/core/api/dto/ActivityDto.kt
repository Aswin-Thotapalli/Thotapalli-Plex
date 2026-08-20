package com.thotapalli.plex.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape for `GET /activities`, the server's list of running background jobs.
 *
 * The Plex API carries no compatibility guarantee, so every field is nullable with a
 * defensive default: a job whose shape has shifted maps to a mostly-empty record rather
 * than failing the whole request. See CLAUDE.md working rule and section 18 point 2.
 */
@Serializable
data class ActivityContainer(
    val size: Int = 0,
    @SerialName("Activity") val activity: List<ActivityDto> = emptyList(),
)

@Serializable
data class ActivityDto(
    val uuid: String? = null,
    val type: String? = null,
    val cancellable: Boolean = false,
    val title: String? = null,
    val subtitle: String? = null,
    /** 0..100 on the wire; mapped to 0f..1f in the domain. */
    val progress: Int = 0,
    /** Capitalised in Plex responses; the lower-case spelling is tolerated as a fallback. */
    @SerialName("Context") val context: ActivityContextDto? = null,
)

@Serializable
data class ActivityContextDto(
    val librarySectionID: String? = null,
)
