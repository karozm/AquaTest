package com.example.aquatest.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import com.example.aquatest.api.ProvisionData
import com.example.aquatest.api.RetrofitClient
import com.example.aquatest.bluetooth.BLEManager
import com.google.gson.Gson
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun DashboardScreen(bleManager: BLEManager, userId: String, topicId: String) {
    val devices by bleManager.foundDevices.collectAsState()
    val connectedDevice by bleManager.connectedDevice.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()
    val connectionState by bleManager.connectionState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gson = remember { Gson() }

    var wifiSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var isProvisioning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Zalogowano jako: $userId", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Podłączone urządzenia:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (connectedDevice != null && connectionState == BluetoothProfile.STATE_CONNECTED) {
            ConnectedDeviceItem(device = connectedDevice!!, onDisconnect = { bleManager.disconnect() })
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Konfiguracja Urządzenia", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = wifiSsid, onValueChange = { wifiSsid = it }, label = { Text("SSID WiFi") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = wifiPass, onValueChange = { wifiPass = it }, label = { Text("Hasło WiFi") }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (wifiSsid.isBlank()) {
                                Toast.makeText(context, "Podaj SSID!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProvisioning = true

                            bleManager.configureWifi(wifiSsid, wifiPass) {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, "Konfiguracja WiFi zakończona!", Toast.LENGTH_LONG).show()
                                    isProvisioning = false
                                }
                            }

//                            scope.launch {
//                                try {
//                                    val provisionData = ProvisionData(userId, topicId, connectedDevice!!.address)
//                                    val response = RetrofitClient.instance.provision(provisionData)
//
//                                    if (response.isSuccessful && response.body() != null) {
//                                        val data = response.body()!!
//                                        Log.d("Provisioning", "Dane AWS pobrane. Rozpoczynam wysyłanie...")
//
//                                        bleManager.sendProvisioningData(topicId, data) {
//                                            bleManager.configureWifi(wifiSsid, wifiPass) {
//                                                Handler(Looper.getMainLooper()).post {
//                                                    Toast.makeText(context, "Pełna konfiguracja zakończona!", Toast.LENGTH_LONG).show()
//                                                    isProvisioning = false
//                                                }
//                                            }
//                                        }
//                                    } else {
//                                        Log.e("Provisioning", "Błąd HTTP: ${response.code()}")
//                                        isProvisioning = false
//                                    }
//                                } catch (e: Exception) {
//                                    Log.e("Provisioning", "Wyjątek", e)
//                                    isProvisioning = false
//                                } finally {
//                                    // isProvisioning = false // Można to przenieść tutaj
//                                }
//                            }
                        },
                        enabled = !isProvisioning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isProvisioning) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Wyślij WiFi")
                    }
                }
            }
        } else {
            Text(text = "Brak podłączonych urządzeń", color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { bleManager.startScanning() }, enabled = !isScanning, modifier = Modifier.fillMaxWidth()) {
            Text(if (isScanning) "Skanowanie..." else "Szukaj nowych urządzeń")
        }

        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(devices) { device ->
                if (device.address != connectedDevice?.address) {
                    DeviceItem(device = device) { bleManager.connect(device) }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectedDeviceItem(device: BluetoothDevice, onDisconnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = device.name ?: "ESP32", style = MaterialTheme.typography.titleMedium)
                Text(text = "Połączono przez BLE", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onDisconnect) { Text("Rozłącz") }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = device.name ?: "Nieznane urządzenie", style = MaterialTheme.typography.bodyLarge)
                Text(text = "MAC: ${device.address}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
