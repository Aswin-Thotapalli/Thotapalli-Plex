package com.thotapalli.plex.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.thotapalli.plex.core.data.db.PlexDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver {
        // The cache is disposable. If the on-disk schema epoch doesn't match this build, delete it and
        // start fresh rather than opening an older schema and failing on a missing column. Framework
        // SQLite enables WAL by default; onOpen adds busy_timeout + foreign_keys and stamps the epoch.
        val dbFile = context.applicationContext.getDatabasePath(PLEX_DATABASE_NAME)
        if (dbFile.exists() && storedEpoch(dbFile) != SCHEMA_EPOCH) {
            com.thotapalli.plex.core.model.Diagnostics.record(
                com.thotapalli.plex.core.model.DiagnosticCategory.CACHE,
                "Cache schema changed; rebuilding the local database",
            )
            runCatching { SQLiteDatabase.deleteDatabase(dbFile) }
        }

        return AndroidSqliteDriver(
            schema = PlexDatabase.Schema,
            context = context.applicationContext,
            name = PLEX_DATABASE_NAME,
            callback = object : AndroidSqliteDriver.Callback(PlexDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    runCatching { db.execSQL("PRAGMA busy_timeout = 3000") }
                    runCatching { db.setForeignKeyConstraintsEnabled(true) }
                    runCatching { db.execSQL("PRAGMA user_version = $SCHEMA_EPOCH") }
                }
            },
        )
    }

    private fun storedEpoch(dbFile: java.io.File): Long = runCatching {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }
    }.getOrDefault(-1L)
}
