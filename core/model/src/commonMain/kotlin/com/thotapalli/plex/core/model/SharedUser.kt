package com.thotapalli.plex.core.model

/**
 * An account the library owner has shared the server with, as listed by plex.tv.
 *
 * The account model is separate Plex accounts with a shared library (CLAUDE.md section 2):
 * the owner grants another account access to selected libraries. This is one such grantee.
 *
 * @property id the invited account's plex.tv id, as a string.
 * @property email the address the invitation was sent to.
 * @property username the account's username, when plex.tv reports one (an invite that has
 *   not been accepted yet may have only an email).
 */
data class SharedUser(
    val id: String,
    val email: String,
    val username: String? = null,
)
