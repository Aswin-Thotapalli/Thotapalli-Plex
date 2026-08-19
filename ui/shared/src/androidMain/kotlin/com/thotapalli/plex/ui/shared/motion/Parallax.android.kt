package com.thotapalli.plex.ui.shared.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Android gyroscopic parallax.
 *
 * Tilt is read from `TYPE_GAME_ROTATION_VECTOR` — drift-free and not affected by magnetic
 * interference — falling back to `TYPE_ROTATION_VECTOR`, then the raw accelerometer's gravity
 * vector, whichever the device actually provides. The reading is turned into a small fraction of
 * tilt in each axis, low-pass smoothed so it glides rather than jitters, mapped to a bounded pixel
 * offset, and applied on a single graphics layer. Reading the offset state inside the
 * `graphicsLayer` block keeps updates on the layer alone, so the decorated content never
 * recomposes or relayouts as the phone moves.
 *
 * If no sensor exists, or the platform hands back nothing usable, the listener is simply never
 * registered and the element sits still. Nothing here throws.
 */
actual fun Modifier.gyroParallax(
    depthX: Float,
    depthY: Float,
    enabled: Boolean,
): Modifier {
    if (!enabled) return this
    return composed {
        val context = LocalContext.current
        val density = LocalDensity.current
        val offsetX = remember { mutableFloatStateOf(0f) }
        val offsetY = remember { mutableFloatStateOf(0f) }

        DisposableEffect(context, density, depthX, depthY) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = sensorManager?.let {
                it.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                    ?: it.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                    ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            }

            if (sensorManager == null || sensor == null) {
                // No usable sensor: leave the element still and register nothing.
                onDispose { }
            } else {
                val maxPxX = with(density) { depthX.dp.toPx() }
                val maxPxY = with(density) { depthY.dp.toPx() }

                val listener = object : SensorEventListener {
                    private var smoothX = 0f
                    private var smoothY = 0f
                    private val rotation = FloatArray(9)
                    private val orientation = FloatArray(3)

                    override fun onSensorChanged(event: SensorEvent) {
                        val fraction = runCatching { readTilt(event) }.getOrNull() ?: return
                        // Low-pass filter: ease toward the new reading so motion is smooth.
                        smoothX += SMOOTHING * (fraction.first - smoothX)
                        smoothY += SMOOTHING * (fraction.second - smoothY)
                        offsetX.floatValue = smoothX * maxPxX
                        offsetY.floatValue = smoothY * maxPxY
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

                    /**
                     * Normalised tilt in each axis, each in roughly -1..1, negated so the picture
                     * slides against the tilt the way a real recessed surface would.
                     */
                    private fun readTilt(event: SensorEvent): Pair<Float, Float> =
                        when (event.sensor.type) {
                            Sensor.TYPE_ACCELEROMETER -> {
                                val gx = (event.values[0] / SensorManager.GRAVITY_EARTH)
                                    .coerceIn(-1f, 1f)
                                val gy = (event.values[1] / SensorManager.GRAVITY_EARTH)
                                    .coerceIn(-1f, 1f)
                                -gx to gy
                            }

                            else -> {
                                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                                SensorManager.getOrientation(rotation, orientation)
                                val roll = orientation[2]
                                val pitch = orientation[1]
                                val fx = (roll / REFERENCE_TILT_RADIANS).coerceIn(-1f, 1f)
                                val fy = (pitch / REFERENCE_TILT_RADIANS).coerceIn(-1f, 1f)
                                -fx to fy
                            }
                        }
                }

                sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
                onDispose { sensorManager.unregisterListener(listener) }
            }
        }

        graphicsLayer {
            translationX = offsetX.floatValue
            translationY = offsetY.floatValue
        }
    }
}

// A comfortable full-tilt reference of about 30 degrees. Reaching this much roll or pitch drives
// the offset to its clamped maximum; ordinary handling stays well inside it.
private const val REFERENCE_TILT_RADIANS = 0.5236f

// Per-event easing toward the newest reading. Low enough that the shift feels like an object with
// a little weight, high enough that it keeps up with a deliberate tilt.
private const val SMOOTHING = 0.15f
