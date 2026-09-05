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

/**
 * The cache's schema epoch. Bump this whenever the tables, columns or indices in the `.sq` files
 * change. Because the database is a disposable cache (never the source of truth), a stored epoch that
 * does not match this value means the on-disk schema is from an older build, and the driver drops and
 * rebuilds rather than crashing on a missing column — the "drop and rebuild on schema change"
 * behaviour CLAUDE.md section 7 relies on. It is stored in SQLite's `user_version`.
 */
const val SCHEMA_EPOCH = 3L
