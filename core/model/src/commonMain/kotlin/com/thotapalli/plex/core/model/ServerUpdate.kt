package com.thotapalli.plex.core.model

/**
 * A pending Plex Media Server update, as reported by the server's `/updater` endpoints.
 *
 * The client is a management surface for the owner (see the server-administration actions in
 * CLAUDE.md section 5's spirit), so it can surface and apply a server update the way the
 * official app does. This is the server product updating itself, not the client updating —
 * the client's own update flow lives in core/session (CLAUDE.md section 17).
 *
 * @property available whether the server reports a newer release than the one running.
 * @property version the available release's version string, when the server names one.
 * @property notes the release's change notes ("fixed" on the wire), when present.
 * @property canApply whether the server says the update can be installed in place.
 */
data class ServerUpdate(
    val available: Boolean,
    val version: String?,
    val notes: String?,
    val canApply: Boolean,
)
