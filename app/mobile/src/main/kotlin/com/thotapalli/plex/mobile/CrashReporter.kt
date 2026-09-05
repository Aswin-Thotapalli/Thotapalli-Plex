package com.thotapalli.plex.mobile

import android.content.Context
import com.thotapalli.plex.core.model.DiagnosticCategory
import com.thotapalli.plex.core.model.Diagnostics
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A last-resort, on-device crash log for an app that ships no telemetry.
 *
 * When the app dies to an uncaught exception, the stack trace is normally gone the moment the
 * process ends — and on a release build it is obfuscated in Logcat, which most people cannot reach
 * anyway. This writes the trace to a plain file the owner can retrieve, and re-surfaces it in the
 * Diagnostics section on the next launch, so a crash we cannot reproduce here becomes a stack trace
 * we can read.
 *
 * The file lives at Android/data/<package>/files/thotapalli-crash.log — reachable through the system
 * Files app or over USB. It records the previous default handler and calls it, so the OS still shows
 * its crash dialog and the process dies as it should.
 */
object CrashReporter {

    private const val FILE_NAME = "thotapalli-crash.log"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                logFile(app)?.appendText("===== $stamp  thread=${thread.name} =====\n$trace\n\n")
            }
            // Let the system handle it as usual (crash dialog, process death).
            previous?.uncaughtException(thread, throwable)
        }

        // Surface a crash from the previous run in the Diagnostics section, then clear the file so it
        // is not shown again. Best-effort: if the app crashes again before Settings is reached, the
        // file on disk still holds the trace.
        runCatching {
            val file = logFile(app) ?: return@runCatching
            if (file.exists() && file.length() > 0) {
                val text = file.readText().trim()
                if (text.isNotEmpty()) {
                    Diagnostics.record(
                        DiagnosticCategory.PLAYBACK,
                        "Recovered from a crash last launch:\n$text",
                    )
                }
                file.delete()
            }
        }
    }

    private fun logFile(context: Context): File? =
        (context.getExternalFilesDir(null) ?: context.filesDir)?.let { File(it, FILE_NAME) }
}
