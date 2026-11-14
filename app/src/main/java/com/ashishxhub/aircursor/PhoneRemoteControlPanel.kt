package com.ashishxhub.aircursor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

private val PHONE_REMOTE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

@Composable
fun PhoneRemoteControlPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { context.getSystemService(BluetoothManager::class.java) }
    val adapter: BluetoothAdapter? = remember { manager?.adapter }
    var role by remember { mutableStateOf("controller") }
    var devices by remember { mutableStateOf(emptyList<android.bluetooth.BluetoothDevice>()) }
    var status by remember { mutableStateOf("Pair both phones in Android Bluetooth settings first.") }
    var loading by remember { mutableStateOf(false) }
    var client by remember { mutableStateOf<BluetoothSppClient?>(null) }
    var connectedDevice by remember { mutableStateOf("") }
    var receiverJob by remember { mutableStateOf<Job?>(null) }
    var receiverSocket by remember { mutableStateOf<BluetoothServerSocket?>(null) }
    var receiverConnected by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun loadDevices() {
        if (adapter == null) { status = "Bluetooth is not available on this phone."; return }
        if (!hasPermission()) { status = "Allow Bluetooth permission, then try again."; return }
        try {
            devices = adapter.bondedDevices.sortedBy { it.name ?: it.address }
            status = if (devices.isEmpty()) "No paired phones found. Pair the phones in Settings > Bluetooth."
                else "Select the other paired phone."
        } catch (_: SecurityException) { status = "Bluetooth permission is required." }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadDevices() else status = "Bluetooth permission denied."
    }

    fun send(action: String, extra: JSONObject = JSONObject()) {
        val active = client
        if (active == null) { status = "Connect to the receiver phone first."; return }
        try {
            extra.put("action", action)
            active.sendLine(extra.toString())
            status = "Sent: $action"
        } catch (_: Exception) {
            status = "Bluetooth connection lost. Reconnect the phones."
            client = null
            connectedDevice = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PHONE-TO-PHONE REMOTE", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium)
        Text("Both phones must be yours or used with the owner's permission. Pair them manually in Bluetooth settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = role == "controller", onClick = { role = "controller" }, label = { Text("Controller phone") })
            FilterChip(selected = role == "receiver", onClick = { role = "receiver" }, label = { Text("Receiver phone") })
        }
        if (role == "controller") {
            Text("1. Load paired devices, then connect to the phone showing Receiver mode.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 31 && !hasPermission()) permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                else loadDevices()
            }) { Text("LOAD PAIRED PHONES") }
            devices.forEach { device ->
                val label = try { device.name ?: device.address } catch (_: Exception) { "Paired phone" }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !loading && client == null) {
                        scope.launch {
                            loading = true
                            status = "Connecting to $label…"
                            try {
                                val connected = BluetoothSppClient.connect(device)
                                client = connected
                                connectedDevice = label
                                status = "Connected to $label. Remote controls are ready."
                            } catch (_: Exception) { status = "Could not connect. Start Receiver on the other phone." }
                            finally { loading = false }
                        }
                    },
                    color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (connectedDevice == label) "CONNECTED • $label" else "CONNECT • $label",
                        modifier = Modifier.padding(12.dp))
                }
            }
            if (client != null) {
                Text("Connected: $connectedDevice", color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { send("back") }, modifier = Modifier.weight(1f)) { Text("BACK") }
                    OutlinedButton(onClick = { send("home") }, modifier = Modifier.weight(1f)) { Text("HOME") }
                    OutlinedButton(onClick = { send("recents") }, modifier = Modifier.weight(1f)) { Text("RECENTS") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { send("notifications") }, modifier = Modifier.weight(1f)) { Text("NOTIFICATIONS") }
                    OutlinedButton(onClick = { send("tap", JSONObject().put("x", .5).put("y", .5)) }, modifier = Modifier.weight(1f)) { Text("TAP CENTER") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { send("swipe", JSONObject().put("x1", .5).put("y1", .75).put("x2", .5).put("y2", .25)) }, modifier = Modifier.weight(1f)) { Text("SWIPE UP") }
                    OutlinedButton(onClick = { send("swipe", JSONObject().put("x1", .5).put("y1", .25).put("x2", .5).put("y2", .75)) }, modifier = Modifier.weight(1f)) { Text("SWIPE DOWN") }
                }
                OutlinedTextField(typedText, { typedText = it }, Modifier.fillMaxWidth(),
                    label = { Text("Text for focused input") }, singleLine = true)
                Button(onClick = { send("type", JSONObject().put("text", typedText)); typedText = "" },
                    modifier = Modifier.fillMaxWidth()) { Text("TYPE ON RECEIVER") }
                OutlinedButton(onClick = {
                    client?.close()
                    client = null
                    connectedDevice = ""
                    status = "Disconnected."
                }, modifier = Modifier.fillMaxWidth()) { Text("DISCONNECT") }
            }
        } else {
            Text("2. Enable AirCursor Accessibility below. Then start the Bluetooth receiver and keep this screen open.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, modifier = Modifier.fillMaxWidth()) { Text("OPEN ACCESSIBILITY SETTINGS") }
            Text("Find AirCursor under Downloaded apps and switch it on. Android will show its permission warning.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = receiverJob == null && !loading,
                onClick = {
                    if (adapter == null) { status = "Bluetooth is not available."; return@Button }
                    if (!hasPermission()) { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT); return@Button }
                    receiverJob = scope.launch {
                        var server: BluetoothServerSocket? = null
                        try {
                            loading = true
                            status = "Waiting for the controller phone to connect…"
                            val accepted = withContext(Dispatchers.IO) {
                                server = adapter.listenUsingRfcommWithServiceRecord("AirCursor Remote", PHONE_REMOTE_UUID)
                                receiverSocket = server
                                server!!.accept()
                            }
                            receiverConnected = true
                            status = "Controller connected. Remote commands are enabled."
                            withContext(Dispatchers.IO) {
                                BufferedReader(InputStreamReader(accepted.inputStream, Charsets.UTF_8)).use { reader ->
                                    while (true) {
                                        val line = reader.readLine() ?: break
                                        val command = try { JSONObject(line) } catch (_: Exception) { continue }
                                        withContext(Dispatchers.Main) {
                                            val enabled = AirCursorAccessibilityService.runRemoteCommand(command)
                                            status = if (enabled) "Received: ${command.optString("action")}" else
                                                "Enable AirCursor Accessibility Service to accept controls."
                                        }
                                    }
                                }
                            }
                        } catch (_: SecurityException) {
                            status = "Bluetooth permission is required."
                        } catch (_: Exception) {
                            status = "Receiver stopped or Bluetooth disconnected."
                        } finally {
                            try { server?.close() } catch (_: Exception) { }
                            receiverSocket = null
                            receiverConnected = false
                            receiverJob = null
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "WAITING / CONNECTED…" else "START RECEIVER") }
            if (receiverJob != null) {
                OutlinedButton(onClick = {
                    receiverJob?.cancel()
                    try { receiverSocket?.close() } catch (_: Exception) { }
                    receiverJob = null
                    receiverConnected = false
                    status = "Receiver stopped."
                }, modifier = Modifier.fillMaxWidth()) { Text("STOP RECEIVER") }
            }
            Text(if (receiverConnected) "● CONNECTED" else "○ NOT CONNECTED",
                color = MaterialTheme.colorScheme.primary)
        }
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
