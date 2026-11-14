package com.ashishxhub.aircursor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun MotionControlPanel(
    connected: Boolean,
    onMove: (Float, Float) -> Unit
) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember {
        manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }
    var active by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(900f) }
    var sensorReady by remember { mutableStateOf(sensor != null) }
    val moveCallback by rememberUpdatedState(onMove)

    DisposableEffect(active, sensor, connected) {
        if (!active || !connected || sensor == null || manager == null) {
            onDispose { }
        } else {
            var previousYaw: Float? = null
            var previousPitch: Float? = null
            var lastSentAt = 0L
            val rotation = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    SensorManager.getOrientation(rotation, orientation)
                    val yaw = orientation[0]
                    val pitch = orientation[1]
                    val oldYaw = previousYaw
                    val oldPitch = previousPitch
                    previousYaw = yaw
                    previousPitch = pitch
                    if (oldYaw == null || oldPitch == null) return

                    var deltaYaw = yaw - oldYaw
                    if (deltaYaw > Math.PI) deltaYaw -= (2.0 * Math.PI).toFloat()
                    if (deltaYaw < -Math.PI) deltaYaw += (2.0 * Math.PI).toFloat()
                    val deltaPitch = pitch - oldPitch
                    val dx = (-deltaYaw * sensitivity).coerceIn(-55f, 55f)
                    val dy = (deltaPitch * sensitivity).coerceIn(-55f, 55f)
                    val now = SystemClock.elapsedRealtime()
                    if ((abs(dx) > 1.2f || abs(dy) > 1.2f) && now - lastSentAt >= 45L) {
                        lastSentAt = now
                        moveCallback(dx, dy)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorReady = true
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { manager.unregisterListener(listener) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PHONE MOTION AIR MOUSE", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                sensor == null -> "Motion sensor not available on this phone."
                !connected -> "Connect to your desktop first. Then start motion control."
                active -> "ACTIVE • Aim your phone gently to move the desktop cursor."
                else -> "READY • Uses your phone's motion sensor. No camera required."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text("Sensitivity: ${sensitivity.toInt()}")
        Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 300f..1600f,
            enabled = !active)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { active = !active },
                enabled = sensorReady && connected,
                modifier = Modifier.weight(1f)
            ) { Text(if (active) "STOP AIR MOUSE" else "START AIR MOUSE") }
            Button(onClick = { active = false }, modifier = Modifier.weight(1f)) {
                Text("RECENTER / STOP")
            }
        }
        Text("Tip: Hold the phone steadily, tap Start, then rotate it slowly left/right/up/down.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}
