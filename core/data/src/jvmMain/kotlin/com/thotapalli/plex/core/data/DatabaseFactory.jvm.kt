package com.thotapalli.plex.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.thotapalli.plex.core.data.db.PlexDatabase
import java.nio.file.Files
import java.nio.file.Path

/**
 * A null [databaseFile] means an in-memory database, which is what the repository tests
 * use so they exercise the real schema rather than a stand-in for it.
 */
actual class DatabaseDriverFactory(
    private val databaseFile: Path? = defaultFile(),
) {

    actual fun create(): SqlDriver {
        if (databaseFile == null) {
            return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
                PlexDatabase.Schema.create(it)
            }
        }

        Files.createDirectories(databaseFile.parent)
        val existed = Files.exists(databaseFile)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.toAbsolutePath()}")
        // Unlike the Android driver, the JDBC one does not run the schema itself.
        if (!existed) PlexDatabase.Schema.create(driver)
        return driver
    }

    companion object {
        fun defaultFile(): Path {
            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            return Path.of(localAppData, "ThotapalliPlex", PLEX_DATABASE_NAME)
        }

        fun inMemory(): DatabaseDriverFactory = DatabaseDriverFactory(databaseFile = null)
    }
}
