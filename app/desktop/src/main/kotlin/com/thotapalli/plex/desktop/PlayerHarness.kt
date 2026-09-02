package com.thotapalli.plex.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.thotapalli.plex.core.playback.PlaybackMode
import com.thotapalli.plex.core.playback.PlaybackSource
import com.thotapalli.plex.core.playback.PlayerEngine
import com.thotapalli.plex.player.mpv.LibMpv
import com.thotapalli.plex.ui.shared.player.VideoSurface
import kotlinx.coroutines.delay
import java.nio.file.Path

/**
 * Runtime harness for the SHIPPED [VideoSurface] composable: a real Compose Desktop window hosting the
 * single-window GL player through the same SwingPanel + AWTGLCanvas the app uses, playing mpv's test
 * pattern, with a Compose overlay composited over it. The counter in the overlay is driven by a
 * LaunchedEffect + delay running on the render-thread dispatcher, so a rising number in a screenshot
 * proves the overlay scene recomposes there. Move the mouse over the window to confirm pointer events
 * reach the scene (the button reacts to hover in the app's real overlay).
 *
 *   gradlew :app:desktop:playerHarness
 */
fun main() {
    LibMpv.load(listOf(Path.of("app", "desktop", "native", "windows-x64"), Path.of("native", "windows-x64")))
    application {
        Window(onCloseRequest = ::exitApplication, title = "Thotapalli player harness") {
            var engine by remember { mutableStateOf<PlayerEngine?>(null) }
            LaunchedEffect(engine) {
                val e = engine ?: return@LaunchedEffect
                e.load(
                    PlaybackSource(
                        uri = "av://lavfi:testsrc=size=1280x720:rate=30:duration=600",
                        mode = PlaybackMode.DIRECT,
                        headers = emptyMap(),
                        frameRate = null,
                    ),
                    startAtMs = 0,
                )
                e.play()
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoSurface(
                    bind = { engine = it },
                    onPointerActivity = {},
                    modifier = Modifier.fillMaxSize(),
                    overlay = {
                        var tick by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(1000)
                                tick++
                            }
                        }
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                                    .background(Color(0xCC101418)),
                            )
                            Box(
                                Modifier.align(Alignment.BottomStart).padding(32.dp)
                                    .clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFD54F))
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            ) {
                                Text("overlay alive: ${tick}s", color = Color(0xFF101418), fontSize = 20.sp)
                            }
                        }
                    },
                )
            }
        }
    }
}
