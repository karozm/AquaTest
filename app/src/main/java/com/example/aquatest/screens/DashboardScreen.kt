package com.example.aquatest.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aquatest.R
import com.example.aquatest.api.ProvisionData
import com.example.aquatest.api.RebindRequest
import com.example.aquatest.api.RetrofitClient
import com.example.aquatest.bluetooth.BLEManager
import com.example.aquatest.data.DeviceStore
import com.example.aquatest.data.SavedDevice
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun DashboardScreen(
        bleManager: BLEManager,
        userId: String,
        onDeviceClick: (SavedDevice) -> Unit,
        onLogout: () -> Unit
) {
        val devices by bleManager.foundDevices.collectAsState()
        val deviceNames by bleManager.deviceNames.collectAsState()
        val connectedDevice by bleManager.connectedDevice.collectAsState()
        val isScanning by bleManager.isScanning.collectAsState()
        val connectionState by bleManager.connectionState.collectAsState()
        val deviceMac by bleManager.deviceMac.collectAsState()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val deviceStore = remember { DeviceStore(context) }
        var savedDevices by remember { mutableStateOf<List<SavedDevice>>(emptyList()) }
        var isLoadingDevices by remember { mutableStateOf(false) }

        var isProvisioning by remember { mutableStateOf(false) }

        // Fetch devices from API
        fun loadDevices() {
                if (userId.isNotBlank()) {
                        isLoadingDevices = true
                        scope.launch {
                                try {
                                        val response = RetrofitClient.instance.getDevices(userId)
                                        if (response.isSuccessful && response.body() != null) {
                                                val deviceInfos =
                                                        response.body()!!.devices ?: emptyList()

                                                // Convert device infos to SavedDevice objects
                                                savedDevices =
                                                        deviceInfos.map { deviceInfo ->
                                                                val deviceId = deviceInfo.id
                                                                val deviceName = deviceInfo.name

                                                                // Try to get BLE MAC from mapping
                                                                val bleMac =
                                                                        deviceStore
                                                                                .getBleMacForDeviceMac(
                                                                                        deviceId
                                                                                )

                                                                // Try to get display name and
                                                                // topicId from local storage
                                                                val localDevice =
                                                                        deviceStore
                                                                                .getSavedDevices()
                                                                                .find {
                                                                                        it.topicId ==
                                                                                                deviceId ||
                                                                                                it.mac ==
                                                                                                        deviceId ||
                                                                                                (bleMac !=
                                                                                                        null &&
                                                                                                        it.mac ==
                                                                                                                bleMac)
                                                                                }

                                                                // Use BLE MAC from mapping if
                                                                // available, otherwise use deviceId
                                                                val macToUse =
                                                                        bleMac
                                                                                ?: localDevice?.mac
                                                                                        ?: deviceId

                                                                SavedDevice(
                                                                        macToUse,
                                                                        localDevice?.displayName
                                                                                ?: deviceName,
                                                                        deviceId // topicId is the
                                                                        // device MAC from
                                                                        // API
                                                                        )
                                                        }

                                        } else {
                                                savedDevices = emptyList()
                                        }
                                } catch (e: Exception) {
                                        Log.e("Dashboard", "Error loading devices", e)
                                        savedDevices = emptyList()
                                } finally {
                                        isLoadingDevices = false
                                }
                        }
                }
        }

        // Load devices when screen is displayed
        LaunchedEffect(userId) { loadDevices() }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        text = "Dashboard",
                                        style = MaterialTheme.typography.headlineMedium
                                )
                                TextButton(onClick = onLogout) {
                                        Text("Wyloguj", color = MaterialTheme.colorScheme.error)
                                }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                        Text(
                                text = "Twoje Urządzenia:",
                                style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoadingDevices) {
                        item {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                ) { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
                        }
                } else if (savedDevices.isNotEmpty()) {
                        items(savedDevices) { device ->
                                // Check if device is found during scanning
                                val isFound = devices.any { it.address == device.mac }
                                SavedDeviceItem(
                                        device,
                                        isFound = isFound,
                                        onClick = {
                                                // Try to connect if device is found and not already connected to this device
                                                val isCurrentlyConnected = connectedDevice?.address == device.mac && 
                                                        connectionState == BluetoothProfile.STATE_CONNECTED
                                                if (isFound && !isCurrentlyConnected) {
                                                        val foundDevice = devices.find { it.address == device.mac }
                                                        foundDevice?.let {
                                                                Log.d("Dashboard", "Connecting to device: ${device.mac}")
                                                                bleManager.connect(it)
                                                        }
                                                }
                                                // Navigate to device details screen
                                                onDeviceClick(device)
                                        }
                                )
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                } else {
                        item {
                                Text(
                                        text = "Brak urządzeń",
                                        color = Color.Gray,
                                        modifier = Modifier.padding(16.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                        }
                }

                item {
                        Text(
                                text = "Podłączone urządzenia:",
                                style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                }

                if (connectedDevice != null && connectionState == BluetoothProfile.STATE_CONNECTED
                ) {
                        item {
                                val currentConnectedName =
                                        deviceNames[connectedDevice!!.address]
                                                ?: connectedDevice!!.name
                                ConnectedDeviceItem(
                                        device = connectedDevice!!,
                                        deviceName = currentConnectedName,
                                        onDisconnect = { bleManager.disconnect() }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                        text = "Parowanie Nowego Urządzenia",
                                                        style = MaterialTheme.typography.titleSmall
                                                )
                                                if (deviceMac.isNotBlank()) {
                                                        Text(
                                                                text = "MAC Urządzenia: $deviceMac",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color = Color.Gray
                                                        )
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))

                                                Button(
                                                        onClick = {
                                                                Log.d(
                                                                        "Provisioning",
                                                                        "=== Rozpoczynam proces parowania ==="
                                                                )
                                                                isProvisioning = true

                                                                scope.launch {
                                                                        try {
                                                                                if (deviceMac
                                                                                                .isBlank()
                                                                                ) {
                                                                                        bleManager
                                                                                                .readDeviceMac()
                                                                                        var waitCount =
                                                                                                0
                                                                                        while (deviceMac
                                                                                                .isBlank() &&
                                                                                                waitCount <
                                                                                                        20) {
                                                                                                kotlinx.coroutines
                                                                                                        .delay(
                                                                                                                100
                                                                                                        )
                                                                                                waitCount++
                                                                                        }
                                                                                }

                                                                                // If MAC reading
                                                                                // failed, abort
                                                                                // provisioning
                                                                                if (deviceMac
                                                                                                .isBlank()
                                                                                ) {
                                                                                        Log.e(
                                                                                                "Provisioning",
                                                                                                "Nie można odczytać MAC z charakterystyki - przerywam provisioning"
                                                                                        )
                                                                                        isProvisioning =
                                                                                                false
                                                                                        android.widget
                                                                                                .Toast
                                                                                                .makeText(
                                                                                                        context,
                                                                                                        "Błąd: Nie można odczytać MAC urządzenia",
                                                                                                        android.widget
                                                                                                                .Toast
                                                                                                                .LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                        return@launch
                                                                                }

                                                                                val macToSend =
                                                                                        deviceMac
                                                                                val defaultName =
                                                                                        deviceNames[
                                                                                                connectedDevice!!
                                                                                                        .address]
                                                                                                ?: connectedDevice!!
                                                                                                        .name
                                                                                                        ?: "AquaTest Device"

                                                                                val provisionData =
                                                                                        ProvisionData(
                                                                                                macToSend
                                                                                        )
                                                                                val response =
                                                                                        RetrofitClient
                                                                                                .instance
                                                                                                .provision(
                                                                                                        provisionData
                                                                                                )

                                                                                if (response.isSuccessful &&
                                                                                                response.body() !=
                                                                                                        null
                                                                                ) {
                                                                                        val data =
                                                                                                response.body()!!
                                                                                        val rootCa =
                                                                                                try {
                                                                                                        context.resources
                                                                                                                .openRawResource(
                                                                                                                        R.raw.amazon_root_ca1
                                                                                                                )
                                                                                                                .use {
                                                                                                                        it.bufferedReader()
                                                                                                                                .use {
                                                                                                                                        r
                                                                                                                                        ->
                                                                                                                                        r.readText()
                                                                                                                                }
                                                                                                                }
                                                                                                } catch (
                                                                                                        e:
                                                                                                                Exception) {
                                                                                                        ""
                                                                                                }

                                                                                        bleManager
                                                                                                .sendProvisioningCerts(
                                                                                                        rootCa,
                                                                                                        data
                                                                                                ) {
                                                                                                        bleManager
                                                                                                                .setupTelemetryNotifications()
                                                                                                        val bleAddress =
                                                                                                                connectedDevice!!
                                                                                                                        .address

                                                                                                        // Save MAC mapping: device MAC (from characteristic) -> BLE MAC (that app sees)
                                                                                                        deviceStore
                                                                                                                .saveMacMapping(
                                                                                                                        macToSend,
                                                                                                                        bleAddress
                                                                                                                )

                                                                                                        val newDevice =
                                                                                                                SavedDevice(
                                                                                                                        bleAddress,
                                                                                                                        defaultName,
                                                                                                                        macToSend
                                                                                                                )
                                                                                                        deviceStore
                                                                                                                .saveDevice(
                                                                                                                        newDevice
                                                                                                                )

                                                                                                        scope
                                                                                                                .launch {
                                                                                                                        // Send rebind request after pairing
                                                                                                                        try {
                                                                                                                                RetrofitClient
                                                                                                                                        .instance
                                                                                                                                        .rebind(
                                                                                                                                                RebindRequest(
                                                                                                                                                        macToSend,
                                                                                                                                                        userId
                                                                                                                                                )
                                                                                                                                        )
                                                                                                                        } catch (
                                                                                                                                e:
                                                                                                                                        Exception) {
                                                                                                                                Log.e(
                                                                                                                                        "Provisioning",
                                                                                                                                        "Błąd podczas rebind",
                                                                                                                                        e
                                                                                                                                )
                                                                                                                        }
                                                                                                                        loadDevices()
                                                                                                                        isProvisioning =
                                                                                                                                false
                                                                                                                        onDeviceClick(
                                                                                                                                newDevice
                                                                                                                        )
                                                                                                                }
                                                                                                }
                                                                                } else if (response.code() ==
                                                                                                400
                                                                                ) {
                                                                                        val bleAddress =
                                                                                                connectedDevice!!
                                                                                                        .address
                                                                                        val newDevice =
                                                                                                SavedDevice(
                                                                                                        bleAddress,
                                                                                                        defaultName,
                                                                                                        macToSend
                                                                                                )
                                                                                        deviceStore
                                                                                                .saveDevice(
                                                                                                        newDevice
                                                                                                )
                                                                                        scope
                                                                                                .launch {
                                                                                                        // Send rebind request after pairing (even if provision returned 400)
                                                                                                        try {
                                                                                                                RetrofitClient
                                                                                                                        .instance
                                                                                                                        .rebind(
                                                                                                                                RebindRequest(
                                                                                                                                        macToSend,
                                                                                                                                        userId
                                                                                                                                )
                                                                                                                        )
                                                                                                        } catch (
                                                                                                                e:
                                                                                                                        Exception) {
                                                                                                                Log.e(
                                                                                                                        "Provisioning",
                                                                                                                        "Błąd podczas rebind",
                                                                                                                        e
                                                                                                                )
                                                                                                        }
                                                                                                        loadDevices()
                                                                                                        isProvisioning =
                                                                                                                false
                                                                                                        onDeviceClick(
                                                                                                                newDevice
                                                                                                        )
                                                                                                }
                                                                                } else {
                                                                                        isProvisioning =
                                                                                                false
                                                                                }
                                                                        } catch (e: Exception) {
                                                                                isProvisioning =
                                                                                        false
                                                                        }
                                                                }
                                                        },
                                                        enabled = !isProvisioning,
                                                        modifier = Modifier.fillMaxWidth()
                                                ) {
                                                        if (isProvisioning)
                                                                CircularProgressIndicator(
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        24.dp
                                                                                ),
                                                                        color = Color.White,
                                                                        strokeWidth = 2.dp
                                                                )
                                                        else Text("Paruj Urządzenie")
                                                }
                                        }
                                }
                        }
                } else {
                        item { Text(text = "Brak podłączonych urządzeń", color = Color.Gray) }
                }

                item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                                onClick = { bleManager.startScanning() },
                                enabled = !isScanning,
                                modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isScanning) "Skanowanie..." else "Szukaj nowych urządzeń") }
                        Spacer(modifier = Modifier.height(16.dp))
                }

                items(
                        devices.filter { device ->
                                val name = deviceNames[device.address] ?: device.name
                                name?.contains("aquatest", ignoreCase = true) == true
                        }
                ) { device ->
                        val isPaired = savedDevices.any { it.mac == device.address }
                        if (device.address != connectedDevice?.address && !isPaired) {
                                DeviceItem(
                                        device = device,
                                        deviceName = deviceNames[device.address]
                                ) { bleManager.connect(device) }
                        }
                }
        }
}

@Composable
fun SavedDeviceItem(device: SavedDevice, isFound: Boolean = true, onClick: () -> Unit) {
        Card(
                modifier =
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                onClick()
                        }, // Zmienione: Teraz zawsze klikalne
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isFound) MaterialTheme.colorScheme.secondaryContainer
                                        else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                        )
        ) {
                Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column {
                                Text(
                                        text = device.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color =
                                                if (isFound)
                                                        MaterialTheme.colorScheme
                                                                .onSecondaryContainer
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.5f
                                                        )
                                )
                                if (!isFound) {
                                        Text(
                                                text = "Poza zasięgiem (Offline)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.4f
                                                        )
                                        )
                                }
                        }
                }
        }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectedDeviceItem(
        device: BluetoothDevice,
        deviceName: String? = null,
        onDisconnect: () -> Unit
) {
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        ) {
                Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Column {
                                Text(
                                        text = deviceName ?: device.name ?: "AquaTest Device",
                                        style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                        text = "Gotowy do sparowania",
                                        style = MaterialTheme.typography.bodySmall
                                )
                        }
                        Button(onClick = onDisconnect) { Text("Rozłącz") }
                }
        }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, deviceName: String? = null, onClick: () -> Unit) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }) {
                Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column {
                                Text(
                                        text = deviceName ?: device.name ?: "Nieznane urządzenie",
                                        style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                        text = "MAC: ${device.address}",
                                        style = MaterialTheme.typography.bodySmall
                                )
                        }
                }
        }
}
