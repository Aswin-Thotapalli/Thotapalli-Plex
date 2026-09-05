package com.thotapalli.plex.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.thotapalli.plex.core.data.db.PlexDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver {
        val app = context.applicationContext

        // The cache is disposable. If the schema epoch this build expects differs from the one the
        // on-disk database was built with, delete it and start fresh rather than opening an older
        // schema and failing on a missing column. See SCHEMA_EPOCH and CLAUDE.md section 7.
        //
        // The epoch is stored in a SharedPreferences, NOT in the database's user_version. That pragma
        // is owned by SQLDelight's AndroidSqliteDriver, which uses it as the SQLite schema version:
        // writing our own value there makes the next open see a "newer" database than the driver
        // expects and crash with "Can't downgrade database from version N to 1" through
        // SQLiteOpenHelper.onDowngrade. Keeping the epoch out of the database entirely avoids that
        // collision. The JVM driver has no such helper, so it can keep using user_version there.
        val meta = app.getSharedPreferences(EPOCH_PREFS, Context.MODE_PRIVATE)
        val dbFile = app.getDatabasePath(PLEX_DATABASE_NAME)
        if (dbFile.exists() && meta.getLong(EPOCH_KEY, -1L) != SCHEMA_EPOCH) {
            com.thotapalli.plex.core.model.Diagnostics.record(
                com.thotapalli.plex.core.model.DiagnosticCategory.CACHE,
                "Cache schema changed; rebuilding the local database",
            )
            runCatching { SQLiteDatabase.deleteDatabase(dbFile) }
        }
        meta.edit().putLong(EPOCH_KEY, SCHEMA_EPOCH).apply()

        return AndroidSqliteDriver(
            schema = PlexDatabase.Schema,
            context = app,
            name = PLEX_DATABASE_NAME,
            callback = object : AndroidSqliteDriver.Callback(PlexDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Framework SQLite enables WAL by default. Add busy_timeout and foreign keys.
                    // Deliberately does NOT touch user_version — that belongs to the driver.
                    runCatching { db.execSQL("PRAGMA busy_timeout = 3000") }
                    runCatching { db.setForeignKeyConstraintsEnabled(true) }
                }
            },
        )
    }

    private companion object {
        const val EPOCH_PREFS = "thotapalli_plex_db_meta"
        const val EPOCH_KEY = "schema_epoch"
    }
}
