// REQUIRES: <uses-permission android:name="android.permission.RECORD_AUDIO"/> in app manifests
// (app/mobile and app/tv). Without it startListening reports ERROR_INSUFFICIENT_PERMISSIONS and
// onResult is never called. Manifest edits are out of this file's scope; this is the note.
package com.thotapalli.plex.ui.shared

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android voice search over the platform [SpeechRecognizer].
 *
 * A fresh recogniser is created per listen and destroyed as soon as it delivers a result or an
 * error, so nothing is held open between searches. Availability is read from the platform, so on a
 * device or emulator image without a recognition service the mic reports unavailable and the shared
 * screen never draws it.
 */
@Composable
actual fun rememberVoiceSearch(): VoiceSearchController {
    val context = LocalContext.current
    // The application context outlives any single screen; the recogniser needs nothing narrower.
    return remember(context) { AndroidVoiceSearchController(context.applicationContext) }
}

private class AndroidVoiceSearchController(private val context: Context) : VoiceSearchController {

    override val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(onResult: (String) -> Unit) {
        if (!available) return

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
