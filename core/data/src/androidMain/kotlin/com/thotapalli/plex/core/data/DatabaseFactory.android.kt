package com.thotapalli.plex.core.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.thotapalli.plex.core.data.db.PlexDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(
        schema = PlexDatabase.Schema,
        context = context.applicationContext,
        name = PLEX_DATABASE_NAME,
    )
}
