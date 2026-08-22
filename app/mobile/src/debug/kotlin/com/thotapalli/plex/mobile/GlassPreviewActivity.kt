package com.thotapalli.plex.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.ThotapalliTheme
import com.thotapalli.plex.ui.shared.liquidBackdropSource
import com.thotapalli.plex.ui.shared.liquidGlassPanel
import com.thotapalli.plex.ui.shared.rememberLiquidBackdrop

/**
 * Debug-only preview of the Kyant liquid-glass navigation over a vivid test backdrop, so the
 * refraction and Fresnel edge can be verified on a device/emulator without signing in.
 */
class GlassPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThotapalliTheme(darkTheme = true) {
                val backdrop = rememberLiquidBackdrop()
                Box(Modifier.fillMaxSize()) {
                    // A vivid field of colour bands and bright thin lines — refraction bends the
                    // lines near the glass edge and the colours show through the pane.
                    Canvas(Modifier.fillMaxSize().liquidBackdropSource(backdrop)) {
                        val bands = listOf(
                            Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF1E88E5),
                            Color(0xFF00ACC1), Color(0xFF43A047), Color(0xFFFDD835),
                            Color(0xFFFB8C00),
                        )
                        val bh = size.height / bands.size
                        bands.forEachIndexed { i, c ->
                            drawRect(c, topLeft = Offset(0f, i * bh), size = androidx.compose.ui.geometry.Size(size.width, bh))
                        }
                        // Bright vertical lines to make refraction obvious.
                        var x = 0f
                        while (x < size.width) {
                            drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(3f, size.height))
                            x += 60f
                        }
                    }

                    // The glass rail.
                    Column(
                        modifier = Modifier
                            .width(236.dp)
                            .fillMaxHeight()
                            .liquidGlassPanel(
                                backdrop,
                                RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                                PlexTheme.colours.surface.copy(alpha = 0.20f),
                            )
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        listOf("Home", "Search", "Downloads", "Settings", "Libraries").forEach {
                            PlexText(text = it, style = PlexTheme.type.title, colour = PlexTheme.colours.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
