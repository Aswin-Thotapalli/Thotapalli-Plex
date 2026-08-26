// REQUIRES: <uses-permission android:name="android.permission.RECORD_AUDIO"/> in app manifests
// (app/mobile and app/tv). Without it startListening reports ERROR_INSUFFICIENT_PERMISSIONS and
// onResult is never called. Manifest edits are out of this file's scope; this is the note.
package com.thotapalli.plex.ui.shared

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Android voice search over the platform [SpeechRecognizer].
 *
 * A fresh recogniser is created per listen and destroyed as soon as it delivers a result or an
 * error, so nothing is held open between searches. Availability is read from the platform, so on a
 * device or emulator image without a recognition service the mic reports unavailable and the shared
 * screen never draws it.
 *
 * RECORD_AUDIO is a runtime permission on Android 13+ — the manifest declaration alone is not enough,
 * and without the grant `startListening` fails silently with ERROR_INSUFFICIENT_PERMISSIONS (this was
 * the "mic doesn't work" bug). The first press requests it through the hosting Activity; once granted,
 * the next press listens.
 */
@Composable
actual fun rememberVoiceSearch(): VoiceSearchController {
    // The recogniser is driven on the main thread and permission requests need the Activity, so keep
    // the (Activity) LocalContext rather than the application context.
    val context = LocalContext.current
    return remember(context) { AndroidVoiceSearchController(context) }
}

private class AndroidVoiceSearchController(private val context: Context) : VoiceSearchController {

    override val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun start(onResult: (String) -> Unit) {
        if (!available) return

        // Runtime permission gate: request on the first press, then the viewer presses again to talk.
        if (!hasMicPermission()) {
            (context as? Activity)?.let {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), 0x5A11)
            }
            return
        }

        // SpeechRecognizer must be created and driven on the main thread. start() is called from a
        // Compose click/press handler, which is already the main thread, so this is safe.
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                if (text != null) onResult(text)
                recognizer.destroy()
            }

            override fun onError(error: Int) {
                recognizer.destroy()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }
}
