package com.ashishxhub.aircursor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AirCursorScreen() }
    }
}

@Composable
fun AirCursorScreen() {
    var host by remember { mutableStateOf("192.168.1.10") }
    var port by remember { mutableStateOf("8765") }
    var status by remember { mutableStateOf("Demo mode • phone only") }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var scrollCount by remember { mutableIntStateOf(0) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C8CFF), background = Color(0xFF10131B), surface = Color(0xFF1B2030))) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF10131B)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AirCursor", fontSize = 30.sp, color = Color.White)
            Text("Wireless trackpad • Demo build", color = Color.LightGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(host, { host = it }, Modifier.weight(1f), label = { Text("Desktop IP") }, singleLine = true)
                OutlinedTextField(port, { port = it }, Modifier.width(105.dp), label = { Text("Port") }, singleLine = true)
            }
            Text(status, color = Color(0xFF91D9B0))
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .background(Color(0xFF20283A), RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            x += drag.x
                            y += drag.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TOUCHPAD", color = Color.White, fontSize = 18.sp)
                    Text("Drag your finger here", color = Color.LightGray)
                    Text("ΔX ${x.toInt()}   ΔY ${y.toInt()}", color = Color(0xFF91D9B0))
                    Text("Scroll test: $scrollCount", color = Color.LightGray)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { status = "Left click • demo"; }, modifier = Modifier.weight(1f)) { Text("Left click") }
                Button(onClick = { status = "Right click • demo"; }, modifier = Modifier.weight(1f)) { Text("Right click") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scrollCount++; status = "Scroll up • demo" }, modifier = Modifier.weight(1f)) { Text("▲ Scroll") }
                OutlinedButton(onClick = { scrollCount--; status = "Scroll down • demo" }, modifier = Modifier.weight(1f)) { Text("▼ Scroll") }
                OutlinedButton(onClick = { x = 0f; y = 0f; scrollCount = 0; status = "Demo reset" }, modifier = Modifier.weight(1f)) { Text("Reset") }
            }
            OutlinedButton(
                onClick = { status = "Desktop connection is the next step. A desktop server must be running on the same Wi-Fi." },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Desktop connection (coming next)") }
        }
    }
}
