package com.thotapalli.plex.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.thotapalli.plex.core.data.db.PlexDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

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
                it.applyPragmas()
            }
        }

        Files.createDirectories(databaseFile.parent)
        // The cache is disposable. If the on-disk schema epoch doesn't match this build, delete the
        // file (and its WAL sidecars) and start fresh, rather than opening an older schema and failing
        // on a missing column. See SCHEMA_EPOCH.
        if (Files.exists(databaseFile) && storedEpoch(databaseFile) != SCHEMA_EPOCH) {
            com.thotapalli.plex.core.model.Diagnostics.record(
                com.thotapalli.plex.core.model.DiagnosticCategory.CACHE,
                "Cache schema changed; rebuilding the local database",
            )
            deleteWithSidecars(databaseFile)
        }

        val existed = Files.exists(databaseFile)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.toAbsolutePath()}")
        // Unlike the Android driver, the JDBC one does not run the schema itself.
        if (!existed) {
            PlexDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = $SCHEMA_EPOCH;", 0)
        }
        driver.applyPragmas()
        return driver
    }

    private fun SqlDriver.applyPragmas() {
        // WAL: readers don't block the writer (a browse refresh vs. a progress write). busy_timeout:
        // wait on contention instead of throwing SQLITE_BUSY. synchronous=NORMAL: safe with WAL and
        // faster. foreign_keys: enforce the (few) relations. All access is already serialised onto one
        // dispatcher, so this only hardens the desktop's single connection further.
        runCatching { execute(null, "PRAGMA journal_mode = WAL;", 0) }
        runCatching { execute(null, "PRAGMA busy_timeout = 3000;", 0) }
        runCatching { execute(null, "PRAGMA synchronous = NORMAL;", 0) }
        runCatching { execute(null, "PRAGMA foreign_keys = ON;", 0) }
    }

    private fun storedEpoch(file: Path): Long = runCatching {
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { c ->
            c.createStatement().use { st ->
                st.executeQuery("PRAGMA user_version").use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
        }
    }.getOrDefault(-1L)

    private fun deleteWithSidecars(file: Path) {
        listOf(file, Path.of("$file-wal"), Path.of("$file-shm")).forEach { runCatching { Files.deleteIfExists(it) } }
    }

    companion object {
        fun defaultFile(): Path {
            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            return Path.of(localAppData, "ThotapalliPlex", PLEX_DATABASE_NAME)
        }

        fun inMemory(): DatabaseDriverFactory = DatabaseDriverFactory(databaseFile = null)
    }
}
