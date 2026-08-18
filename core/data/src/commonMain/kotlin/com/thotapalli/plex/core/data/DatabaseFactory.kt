package com.thotapalli.plex.core.data

import app.cash.sqldelight.db.SqlDriver
import com.thotapalli.plex.core.data.db.PlexDatabase

/**
 * The driver differs by platform. Android uses the framework SQLite, the desktop uses the
 * JDBC driver.
 */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

/**
 * Builds the cache.
 *
 * The database is a cache and the offline queues, nothing more. It is safe to delete and
 * is never the source of truth for anything the server also knows. See CLAUDE.md section 7.
 */
fun createPlexDatabase(factory: DatabaseDriverFactory): PlexDatabase =
    PlexDatabase(factory.create())

const val PLEX_DATABASE_NAME = "thotapalli_plex.db"
