package com.example.aquatest.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aquatest.bluetooth.BLEManager

@SuppressLint("MissingPermission")
@Composable
fun DashboardScreen(bleManager: BLEManager) {
    val devices by bleManager.foundDevices.collectAsState()
    val connectedDevice by bleManager.connectedDevice.collectAsState()
    val batteryLevel by bleManager.batteryLevel.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()
    val connectionState by bleManager.connectionState.collectAsState()

    var wifiSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Sekcja podłączonych urządzeń
        Text(text = "Podłączone urządzenia:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        if (connectedDevice != null && connectionState == BluetoothProfile.STATE_CONNECTED) {
            ConnectedDeviceItem(
                device = connectedDevice!!, 
                battery = batteryLevel,
                onDisconnect = { bleManager.disconnect() }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Panel Konfiguracji WiFi
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Konfiguracja WiFi ESP32", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = { Text("SSID (Nazwa sieci)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = wifiPass,
                        onValueChange = { wifiPass = it },
                        label = { Text("Hasło WiFi") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { bleManager.configureWifi(wifiSsid, wifiPass) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Wyślij konfigurację do ESP32")
                    }
                }
            }
        } else {
            Text(
                text = "Brak podłączonych urządzeń",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Sekcja wyszukiwania
        Button(
            onClick = { bleManager.startScanning() },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "Skanowanie..." else "Szukaj nowych urządzeń")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Dostępne w pobliżu:", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(devices) { device ->
                if (device.address != connectedDevice?.address) {
                    DeviceItem(device = device) {
                        bleManager.connect(device)
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectedDeviceItem(device: BluetoothDevice, battery: Int?, onDisconnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = device.name ?: "ESP32", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (battery != null) "Bateria: $battery%" else "Łączenie z usługami...",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onDisconnect) {
                Text("Rozłącz")
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = device.name ?: "Nieznane urządzenie", style = MaterialTheme.typography.bodyLarge)
                Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
