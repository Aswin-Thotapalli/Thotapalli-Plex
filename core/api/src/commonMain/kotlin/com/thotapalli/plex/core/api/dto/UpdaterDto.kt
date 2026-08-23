package com.thotapalli.plex.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape for `GET /updater/status`, the server's own update state.
 *
 * A pending update arrives as one or more `Release` entries; an up-to-date server reports
 * none. As with every Plex shape, the API carries no compatibility guarantee, so each field
 * is nullable with a defensive default rather than failing the whole request. See CLAUDE.md
 * section 18 point 2.
 */
@Serializable
data class UpdaterStatusContainer(
    val size: Int = 0,
    /** Whether the server can install an update in place. */
    val canInstall: Boolean = false,
    val downloadURL: String? = null,
    val status: String? = null,
    /** Some server builds report the available version at the container level. */
    val version: String? = null,
    @SerialName("Release") val release: List<ReleaseDto> = emptyList(),
)

@Serializable
data class ReleaseDto(
    val key: String? = null,
    val version: String? = null,
    val added: String? = null,
    /** The change notes for the release; mapped onto [ServerUpdate.notes]. */
    val fixed: String? = null,
    val downloadURL: String? = null,
    val state: String? = null,
)
