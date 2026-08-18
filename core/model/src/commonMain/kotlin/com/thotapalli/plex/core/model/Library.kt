package com.thotapalli.plex.core.model

/**
 * A library section on a server.
 *
 * Libraries are read from the server at runtime and never hardcoded, and are sorted by
 * title ascending. See CLAUDE.md section 2.
 */
data class Library(
    val key: String,
    val title: String,
    val kind: LibraryKind,
    val uuid: String,
)

/**
 * Music and photo libraries are permanently out of scope, so anything that is not a movie
 * or a show library maps to [UNSUPPORTED] and is filtered out before it reaches the
 * interface. See CLAUDE.md section 1.
 */
enum class LibraryKind {
    MOVIE,
    SHOW,
    UNSUPPORTED,
    ;

    val supported: Boolean get() = this != UNSUPPORTED
}
