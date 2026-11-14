package com.ashishxhub.aircursor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    var pairingToken by remember { mutableStateOf("") }
    var keyboardText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Not connected • Enter your desktop server address") }
    var connected by remember { mutableStateOf(false) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var sentEvents by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun performRequest(path: String, body: String?): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim()
            val cleanPort = port.trim().toIntOrNull()
            if (cleanHost.isBlank() || cleanPort == null || cleanPort !in 1..65535) {
                return@withContext false to "Enter a valid desktop IP and port"
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL("http://$cleanHost:$cleanPort$path").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = if (body == null) "GET" else "POST"
                    setRequestProperty("Content-Type", "application/json")
                    if (path != "/health") setRequestProperty("Authorization", "Bearer ${pairingToken.trim()}")
                    if (body != null) {
                        doOutput = true
                        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    true to "Server replied • HTTP $code"
                } else {
                    false to "Server replied HTTP $code"
                }
            } catch (_: Exception) {
                false to "Can't reach desktop. Check same Wi-Fi and server."
            } finally {
                connection?.disconnect()
            }
        }

    fun request(path: String, body: String? = null, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val result = performRequest(path, body)
            onResult(result.first, result.second)
        }
    }

    fun sendEvent(path: String, body: String) {
        if (!connected) {
            status = "Demo mode • Connect to a desktop server first"
            return
        }
        request(path, body) { ok, message ->
            if (!ok) connected = false
            status = message
        }
        sentEvents++
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF7C8CFF),
        background = Color(0xFF10131B),
        surface = Color(0xFF1B2030)
    )) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF10131B)).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("AirCursor", fontSize = 30.sp, color = Color.White)
            Text("Wireless trackpad + keyboard • v0.6", color = Color.LightGray)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; connected = false },
                    modifier = Modifier.weight(1f),
                    label = { Text("Desktop IP") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it; connected = false },
                    modifier = Modifier.width(105.dp),
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            OutlinedTextField(
                value = pairingToken,
                onValueChange = { pairingToken = it; connected = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pairing token") },
                singleLine = true
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    status = "Checking desktop server…"
                    request("/health") { ok, message ->
                        connected = ok
                        busy = false
                        status = if (ok) "Connected • Desktop server ready" else message
                    }
                }) {
                    Text(if (busy) "Checking…" else "Test connection")
                }
                Text(
                    status,
                    color = if (connected) Color(0xFF91D9B0) else Color(0xFFFFC978),
                    fontSize = 12.sp
                )
            }

            Box(
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF20283A), RoundedCornerShape(24.dp))
                    .pointerInput(connected, host, port) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            x += drag.x
                            y += drag.y
                            sendEvent(
                                "/move",
                                JSONObject().put("dx", drag.x).put("dy", drag.y).toString()
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("TOUCHPAD", color = Color.White, fontSize = 18.sp)
                    Text(
                        if (connected) "Drag to move desktop cursor"
                        else "Drag here to test • Connect for desktop control",
                        color = Color.LightGray
                    )
                    Text("ΔX ${x.toInt()}   ΔY ${y.toInt()}", color = Color(0xFF91D9B0))
                    Text("Events sent: $sentEvents", color = Color.LightGray)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { sendEvent("/click", """{"button":"left"}""") },
                    modifier = Modifier.weight(1f)
                ) { Text("Left click") }
                Button(
                    onClick = { sendEvent("/click", """{"button":"right"}""") },
                    modifier = Modifier.weight(1f)
                ) { Text("Right click") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { sendEvent("/scroll", """{"amount":-3}""") },
                    modifier = Modifier.weight(1f)
                ) { Text("▲ Scroll") }
                OutlinedButton(
                    onClick = { sendEvent("/scroll", """{"amount":3}""") },
                    modifier = Modifier.weight(1f)
                ) { Text("▼ Scroll") }
                OutlinedButton(
                    onClick = {
                        x = 0f
                        y = 0f
                        sentEvents = 0
                        status = "Display reset"
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }
            }

            Text(
                "Server API: GET /health • POST /move, /click, /scroll, /type",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}
