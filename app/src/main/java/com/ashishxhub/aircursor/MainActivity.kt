package com.ashishxhub.aircursor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Navy = Color(0xFF090D16)
private val Panel = Color(0xFF0F172A)
private val Cyan = Color(0xFF38BDF8)
private val Muted = Color(0xFF94A3B8)
private val Green = Color(0xFF22C55E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AirCursorApp() }
    }
}

@Composable
private fun CursorMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width * .12f, size.height * .10f)
            lineTo(size.width * .86f, size.height * .57f)
            lineTo(size.width * .54f, size.height * .66f)
            lineTo(size.width * .43f, size.height * .96f)
            close()
        }
        drawPath(p, Brush.linearGradient(listOf(Color(0xFF7DD3FC), Color(0xFF0284C7))))
        drawPath(p, Color(0xFFE0F2FE), style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * .025f))
    }
}

@Composable
private fun AirCursorApp() {
    var initialized by remember { mutableStateOf(false) }
    var blast by remember { mutableStateOf(false) }
    if (!initialized) {
        SplashScreen(onInitialize = {
            if (!blast) blast = true
        }, blast = blast, onFinished = { initialized = true })
    } else {
        AirCursorDashboard()
    }
}

@Composable
private fun SplashScreen(onInitialize: () -> Unit, blast: Boolean, onFinished: () -> Unit) {
    val floatTransition = rememberInfiniteTransition(label = "cursor_float")
    val floatY by floatTransition.animateFloat(
        initialValue = 0f, targetValue = -18f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float_y"
    )
    val pulse by floatTransition.animateFloat(
        initialValue = .35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    val blastScale by animateFloatAsState(
        targetValue = if (blast) 34f else 0f,
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "blast_scale"
    )
    LaunchedEffect(blast) {
        if (blast) {
            delay(850)
            onFinished()
        }
    }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1E293B), Navy)))
            .clickable(enabled = !blast) { onInitialize() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CursorMark(Modifier.size(116.dp).offset(y = floatY.dp))
            Spacer(Modifier.height(34.dp))
            Text("TAP TO INITIALIZE AIRCURSOR", color = Cyan, fontSize = 12.sp,
                letterSpacing = 2.sp, modifier = Modifier.alpha(pulse))
        }
        if (blast) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFBAE6FD), Color(0x8838BDF8), Color.Transparent)),
                    radius = size.minDimension * blastScale / 2f,
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun AirCursorDashboard() {
    var host by remember { mutableStateOf("192.168.1.10") }
    var port by remember { mutableStateOf("8765") }
    var pairingToken by remember { mutableStateOf("") }
    var keyboardText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Not connected • Enter desktop server details") }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf("home") }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    suspend fun performRequest(path: String, body: String?): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanPort = port.trim().toIntOrNull()
        if (host.isBlank() || cleanPort == null || cleanPort !in 1..65535) {
            return@withContext false to "Enter a valid desktop IP and port"
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("http://${host.trim()}:$cleanPort$path").openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = if (body == null) "GET" else "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${pairingToken.trim()}")
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = connection.responseCode
            if (code in 200..299) true to "Server replied • HTTP $code"
            else false to "Server replied HTTP $code"
        } catch (_: Exception) {
            false to "Can't reach desktop • Check Wi-Fi and server"
        } finally {
            connection?.disconnect()
        }
    }

    fun request(path: String, body: String? = null, result: (Boolean, String) -> Unit) {
        scope.launch {
            val response = performRequest(path, body)
            result(response.first, response.second)
        }
    }

    fun sendEvent(path: String, body: String) {
        if (!connected) {
            status = "Demo mode • Connect to desktop first"
            return
        }
        request(path, body) { ok, message ->
            status = message
            if (!ok) connected = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Navy, surface = Panel)) {
        Column(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF0B1220), Navy))
            ).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("⚡ AIR_CURSOR // HUD", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("WIRELESS CONTROL SYSTEM", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                }
                Text(if (connected) "ONLINE" else "STANDBY",
                    color = if (connected) Green else Color(0xFFFFC978), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = Cyan.copy(alpha = .22f))

            if (selectedPage == "home") {
                HudCard(Modifier.fillMaxWidth()) {
                    Text("CONNECTION HUB", color = Cyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(status, color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    BluetoothConnectionPanel { client, deviceName ->
                        status = "Bluetooth connected to $deviceName"
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(host, { host = it; connected = false }, Modifier.fillMaxWidth(),
                        label = { Text("Desktop IP") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(port, { port = it; connected = false }, Modifier.weight(1f),
                            label = { Text("Port") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(pairingToken, { pairingToken = it; connected = false }, Modifier.weight(2f),
                            label = { Text("Pairing token") }, singleLine = true)
                    }
                    Button(enabled = !busy, onClick = {
                        busy = true
                        status = "Initializing secure connection…"
                        request("/pair", "{}") { ok, message ->
                            connected = ok
                            busy = false
                            status = if (ok) "CONNECTED • Desktop host ready" else message
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "CONNECTING…" else "PAIR WITH DESKTOP")
                    }
                }

                Text("CONTROL MODULES", color = Muted, fontSize = 11.sp, letterSpacing = 1.8.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HudTile("🖱️", "Mouse Control", "Trackpad & gestures", Modifier.weight(1f)) { selectedPage = "mouse" }
                    HudTile("⌨️", "Keyboard Entry", "Remote typing", Modifier.weight(1f)) { selectedPage = "keyboard" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HudTile("🎬", "Media Player", "Playback & volume", Modifier.weight(1f)) { selectedPage = "media" }
                    HudTile("📊", "Presentation", "Slide controls", Modifier.weight(1f)) { selectedPage = "presentation" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HudTile("🌀", "Motion Air Mouse", "Move cursor by tilting phone", Modifier.weight(1f)) { selectedPage = "motion" }
                    Spacer(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HudTile("📱", "Phone Remote", "Control another Android phone", Modifier.weight(1f)) { selectedPage = "phone" }
                    Spacer(Modifier.weight(1f))
                }
                Text("AIRCURSOR • FUTURISTIC REMOTE INTERFACE", color = Muted, fontSize = 10.sp,
                    letterSpacing = 1.1.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹  BACK", color = Cyan, modifier = Modifier.clickable { selectedPage = "home" }.padding(8.dp))
                    Text(selectedPage.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                when (selectedPage) {
                    "phone" -> HudCard(Modifier.fillMaxWidth()) {
                        PhoneRemoteControlPanel()
                    }
                    "motion" -> HudCard(Modifier.fillMaxWidth()) {
                        MotionControlPanel(connected = connected) { moveX, moveY ->
                            sendEvent("/move", JSONObject().put("dx", moveX).put("dy", moveY).toString())
                        }
                    }
                    "mouse" -> {
                        HudCard(Modifier.fillMaxWidth()) {
                            Text("AIR TRACKPAD", color = Cyan, fontWeight = FontWeight.Bold)
                            Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1B2A40))
                                .pointerInput(connected, host, port) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        dx += drag.x; dy += drag.y
                                        sendEvent("/move", JSONObject().put("dx", drag.x).put("dy", drag.y).toString())
                                    }
                                }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CursorMark(Modifier.size(42.dp))
                                    Text("DRAG TO MOVE CURSOR", color = Color.White, fontSize = 12.sp)
                                    Text("ΔX ${dx.toInt()}   ΔY ${dy.toInt()}", color = Cyan, fontSize = 11.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HudButton("LEFT CLICK", Modifier.weight(1f)) { sendEvent("/click", """{"button":"left"}""") }
                                HudButton("RIGHT CLICK", Modifier.weight(1f)) { sendEvent("/click", """{"button":"right"}""") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HudButton("▲ SCROLL", Modifier.weight(1f)) { sendEvent("/scroll", """{"amount":-3}""") }
                                HudButton("▼ SCROLL", Modifier.weight(1f)) { sendEvent("/scroll", """{"amount":3}""") }
                            }
                            Text("Two-finger gesture pad", color = Cyan, fontWeight = FontWeight.SemiBold)
                            Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF26334A)).pointerInput(connected) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        if (kotlin.math.abs(drag.y) > 8f) {
                                            val amount = (-drag.y * 0.0286f).toInt().coerceIn(-10, 10)
                                            if (amount != 0) sendEvent("/scroll", JSONObject().put("amount", amount).toString())
                                        }
                                    }
                                }, contentAlignment = Alignment.Center) {
                                Text("Move fingers up / down", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    "keyboard" -> HudCard(Modifier.fillMaxWidth()) {
                        Text("REMOTE KEYBOARD", color = Cyan, fontWeight = FontWeight.Bold)
                        OutlinedTextField(keyboardText, { keyboardText = it }, Modifier.fillMaxWidth(),
                            label = { Text("Type a message") })
                        Button(onClick = {
                            sendEvent("/type", JSONObject().put("text", keyboardText).toString())
                            keyboardText = ""
                        }, modifier = Modifier.fillMaxWidth()) { Text("SEND TO DESKTOP") }
                        Text("Text input is sent to the paired desktop host.", color = Muted, fontSize = 11.sp)
                    }
                    "media" -> HudCard(Modifier.fillMaxWidth()) {
                        Text("MEDIA PLAYER", color = Cyan, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton("⏮ PREVIOUS", Modifier.weight(1f)) { sendEvent("/media", """{"action":"previous"}""") }
                            HudButton("▶ / ⏸", Modifier.weight(1f)) { sendEvent("/media", """{"action":"play_pause"}""") }
                            HudButton("⏭ NEXT", Modifier.weight(1f)) { sendEvent("/media", """{"action":"next"}""") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton("VOL −", Modifier.weight(1f)) { sendEvent("/volume", """{"action":"down"}""") }
                            HudButton("MUTE", Modifier.weight(1f)) { sendEvent("/volume", """{"action":"mute"}""") }
                            HudButton("VOL +", Modifier.weight(1f)) { sendEvent("/volume", """{"action":"up"}""") }
                        }
                    }
                    "presentation" -> HudCard(Modifier.fillMaxWidth()) {
                        Text("PRESENTATION TOOLS", color = Cyan, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton("← PREVIOUS", Modifier.weight(1f)) { sendEvent("/presentation", """{"action":"previous"}""") }
                            HudButton("NEXT →", Modifier.weight(1f)) { sendEvent("/presentation", """{"action":"next"}""") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton("START", Modifier.weight(1f)) { sendEvent("/presentation", """{"action":"start"}""") }
                            HudButton("EXIT", Modifier.weight(1f)) { sendEvent("/presentation", """{"action":"exit"}""") }
                            HudButton("BLACK", Modifier.weight(1f)) { sendEvent("/presentation", """{"action":"black"}""") }
                        }
                    }
                }
                Text(status, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HudCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp))
        .background(Brush.linearGradient(listOf(Color(0xCC17243A), Color(0xCC0F172A))))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun HudTile(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp))
        .background(Panel).clickable(onClick = onClick).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(icon, fontSize = 25.sp)
        Text(title, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
        Text("OPEN  →", color = Cyan, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun HudButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
        Text(label, fontSize = 11.sp)
    }
}
