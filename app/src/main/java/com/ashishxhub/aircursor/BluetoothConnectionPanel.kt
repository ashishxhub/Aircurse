package com.ashishxhub.aircursor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.UUID

private val AIRCURSOR_SPP_UUID: UUID =
    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class BluetoothSppClient private constructor(
    private val socket: BluetoothSocket
) {
    @Synchronized
    fun sendLine(message: String) {
        val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
        writer.write(message)
        writer.newLine()
        writer.flush()
    }

    fun close() {
        try { socket.close() } catch (_: Exception) { }
    }

    companion object {
        suspend fun connect(device: BluetoothDevice): BluetoothSppClient =
            withContext(Dispatchers.IO) {
                val socket = device.createRfcommSocketToServiceRecord(AIRCURSOR_SPP_UUID)
                try {
                    socket.connect()
                    BluetoothSppClient(socket)
                } catch (error: Exception) {
                    try { socket.close() } catch (_: Exception) { }
                    throw error
                }
            }
    }
}

@Composable
fun BluetoothConnectionPanel(
    onConnected: (BluetoothSppClient, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bluetoothManager = remember {
        context.getSystemService(BluetoothManager::class.java)
    }
    val adapter: BluetoothAdapter? = remember { bluetoothManager?.adapter }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var message by remember { mutableStateOf("Pair your Windows PC in Android Bluetooth settings first.") }
    var loading by remember { mutableStateOf(false) }
    var connectedName by remember { mutableStateOf<String?>(null) }
    var activeClient by remember { mutableStateOf<BluetoothSppClient?>(null) }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    fun loadPairedDevices() {
        if (adapter == null) {
            message = "This phone does not support Bluetooth."
            return
        }
        if (!hasConnectPermission()) {
            message = "Allow Bluetooth permission to see paired devices."
            return
        }
        try {
            if (!adapter.isEnabled) {
                message = "Turn on Bluetooth, then load paired devices."
                devices = emptyList()
                return
            }
            devices = adapter.bondedDevices.sortedBy { it.name ?: it.address }
            message = if (devices.isEmpty()) {
                "No paired devices. Pair your Windows PC in phone settings first."
            } else {
                "Choose your paired Windows PC below."
            }
        } catch (_: SecurityException) {
            message = "Bluetooth permission is required."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadPairedDevices()
        else message = "Bluetooth permission denied. Enable it in app settings."
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BLUETOOTH CLASSIC • WINDOWS", color = Cyan, style = MaterialTheme.typography.titleSmall)
        Text(message, color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasConnectPermission()) {
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    loadPairedDevices()
                }
            }) {
                Text("LOAD PAIRED DEVICES")
            }
            if (connectedName != null) {
                OutlinedButton(onClick = {
                    activeClient?.close()
                    activeClient = null
                    connectedName = null
                    message = "Bluetooth disconnected."
                }) { Text("DISCONNECT") }
            }
        }
        devices.forEach { device ->
            val deviceName = try {
                device.name ?: device.address
            } catch (_: SecurityException) {
                "Bluetooth device"
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !loading && connectedName == null) {
                    if (!hasConnectPermission()) {
                        message = "Bluetooth permission is required. Load devices again."
                        return@clickable
                    }
                    scope.launch {
                        loading = true
                        message = "Connecting to $deviceName…"
                        try {
                            val client = BluetoothSppClient.connect(device)
                            activeClient = client
                            connectedName = deviceName
                            message = "Bluetooth connected to $deviceName."
                            onConnected(client, deviceName)
                        } catch (error: Exception) {
                            message = "Connection failed. Keep Windows paired and its SPP server running."
                        } finally {
                            loading = false
                        }
                    }
                },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (connectedName == deviceName) "CONNECTED • $deviceName" else "CONNECT • $deviceName",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
