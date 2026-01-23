package com.example.aquatest.screens

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aquatest.api.RebindRequest
import com.example.aquatest.api.RetrofitClient
import com.example.aquatest.api.SetConfigRequest
import com.example.aquatest.api.SetNameRequest
import com.example.aquatest.bluetooth.BLEManager
import com.example.aquatest.data.DeviceStore
import com.example.aquatest.data.SavedDevice
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
        device: SavedDevice,
        bleManager: BLEManager,
        userId: String,
        onBack: () -> Unit,
        onDeviceUpdated: ((SavedDevice) -> Unit)? = null
) {
        val temperature by bleManager.temperature.collectAsState()
        val ph by bleManager.ph.collectAsState()
        val feedStatus by bleManager.feedStatus.collectAsState()
        val alert by bleManager.alert.collectAsState()
        val connectionState by bleManager.connectionState.collectAsState()
        val foundDevices by bleManager.foundDevices.collectAsState()
        val isScanning by bleManager.isScanning.collectAsState()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val isConnected = connectionState == android.bluetooth.BluetoothProfile.STATE_CONNECTED
        val isConnecting = connectionState == android.bluetooth.BluetoothProfile.STATE_CONNECTING

        val deviceStore = remember { DeviceStore(context) }
        val updatePrefs = remember {
                context.getSharedPreferences(
                        "device_settings",
                        android.content.Context.MODE_PRIVATE
                )
        }
        var currentDevice by remember { mutableStateOf(device) }
        var isEditingName by remember { mutableStateOf(false) }
        var editedName by remember { mutableStateOf(device.displayName) }

        // Update notifications preference (stored per device)
        var updateNotificationsEnabled by remember {
                mutableStateOf(updatePrefs.getBoolean("${device.mac}_update_notifications", true))
        }

        var wifiSsid by remember { mutableStateOf("") }
        var wifiPass by remember { mutableStateOf("") }
        var isUpdatingWifi by remember { mutableStateOf(false) }

        var isConfigurationExpanded by remember { mutableStateOf(false) }
        var autoConnectDisabled by remember { mutableStateOf(false) }

        // Threshold settings state
        var tempLower by remember { mutableStateOf("") }
        var tempUpper by remember { mutableStateOf("") }
        var phLower by remember { mutableStateOf("") }
        var phUpper by remember { mutableStateOf("") }

        // Interval state - store as seconds
        var selectedTempInterval by remember { mutableStateOf<Int?>(null) }
        var selectedFeedInterval by remember { mutableStateOf<Int?>(null) }
        var selectedPublishInterval by remember { mutableStateOf<Int?>(null) }
        
        // Interval unit and value state for UI
        var tempIntervalUnit by remember { mutableStateOf("min") }
        var tempIntervalValue by remember { mutableStateOf(1) }
        var feedIntervalUnit by remember { mutableStateOf("min") }
        var feedIntervalValue by remember { mutableStateOf(1) }
        var publishIntervalUnit by remember { mutableStateOf("min") }
        var publishIntervalValue by remember { mutableStateOf(1) }

        // Loaded config state for display
        var loadedConfig by remember { mutableStateOf<Map<String, String>?>(null) }

        // Firmware update state
        var firmwareUpdateAvailable by remember { mutableStateOf(false) }
        var firmwareUpdateUrl by remember { mutableStateOf("") }
        var firmwareVersion by remember { mutableStateOf("") }
        var isCheckingFirmware by remember { mutableStateOf(false) }
        var firmwareDownloadStarted by remember { mutableStateOf(false) }

        // Helper function to convert seconds to unit/value pair
        fun secondsToUnitValue(seconds: Int): Pair<String, Int> {
                return when {
                        seconds == 0 -> "never" to 0
                        seconds % 86400 == 0 && seconds / 86400 in 1..7 -> {
                                "days" to (seconds / 86400)
                        }
                        seconds % 3600 == 0 && seconds / 3600 in 1..24 -> {
                                "hr" to (seconds / 3600)
                        }
                        seconds % 60 == 0 && seconds / 60 in 1..60 -> {
                                "min" to (seconds / 60)
                        }
                        else -> {
                                // Default to minutes, round to nearest minute
                                val minutes = (seconds / 60).coerceIn(1, 60)
                                "min" to minutes
                        }
                }
        }

        // Helper function to convert unit/value to seconds
        fun unitValueToSeconds(unit: String, value: Int): Int {
                return when (unit) {
                        "min" -> value * 60
                        "hr" -> value * 3600
                        "days" -> value * 86400
                        else -> value * 60
                }
        }

        // Function to load device config from API
        fun loadDeviceConfig() {
                val deviceId =
                        if (currentDevice.topicId.isNotBlank()) currentDevice.topicId
                        else currentDevice.mac

                scope.launch {
                        try {
                                val response = RetrofitClient.instance.getConfig(deviceId)
                                Log.d(
                                        "DeviceDetails",
                                        "get_config API Response: code=${response.code()}, body=${response.body()}, isSuccessful=${response.isSuccessful}"
                                )

                                if (response.isSuccessful && response.body() != null) {
                                        val configResponse = response.body()!!
                                        val config = configResponse.config ?: emptyMap()
                                        Log.d("DeviceDetails", "Loaded config: $config")
                                        Log.d("DeviceDetails", "Config keys: ${config.keys}")
                                        Log.d("DeviceDetails", "Config size: ${config.size}")

                                        // Convert Map<String, String?> to Map<String, String> for
                                        // display (filtering out nulls)
                                        val configForDisplay =
                                                config.filterValues { it != null }.mapValues {
                                                        it.value!!
                                                }

                                        // Store config for display
                                        loadedConfig = configForDisplay
                                        Log.d(
                                                "DeviceDetails",
                                                "Stored config in loadedConfig: $loadedConfig"
                                        )

                                        // Parse and populate thresholds
                                        config["temp_lower"]?.let {
                                                tempLower = it?.toFloatOrNull()?.toString() ?: ""
                                        }
                                        config["temp_upper"]?.let {
                                                tempUpper = it?.toFloatOrNull()?.toString() ?: ""
                                        }
                                        config["ph_lower"]?.let {
                                                phLower = it?.toFloatOrNull()?.toString() ?: ""
                                        }
                                        config["ph_upper"]?.let {
                                                phUpper = it?.toFloatOrNull()?.toString() ?: ""
                                        }

                                        // Parse and populate intervals
                                        config["temp_frequency"]?.let {
                                                val seconds = it?.toIntOrNull()
                                                selectedTempInterval = seconds
                                                seconds?.let { sec ->
                                                        val (unit, value) = secondsToUnitValue(sec)
                                                        tempIntervalUnit = unit
                                                        tempIntervalValue = value
                                                }
                                        }
                                        config["feed_frequency"]?.let {
                                                val seconds = it?.toIntOrNull()
                                                selectedFeedInterval = seconds
                                                seconds?.let { sec ->
                                                        val (unit, value) = secondsToUnitValue(sec)
                                                        feedIntervalUnit = unit
                                                        feedIntervalValue = value
                                                }
                                        }
                                        config["wake_frequency"]?.let {
                                                val seconds = it?.toIntOrNull()
                                                selectedPublishInterval = seconds
                                                seconds?.let { sec ->
                                                        val (unit, value) = secondsToUnitValue(sec)
                                                        publishIntervalUnit = unit
                                                        publishIntervalValue = value
                                                }
                                        }
                                } else {
                                        Log.e(
                                                "DeviceDetails",
                                                "get_config failed: code=${response.code()}, message=${response.message()}, body=${response.body()}"
                                        )
                                }
                        } catch (e: Exception) {
                                Log.e("DeviceDetails", "Failed to load device config", e)
                                e.printStackTrace()
                        }
                }
        }

        // Function to check for firmware updates
        fun checkFirmwareUpdate() {
                if (!updateNotificationsEnabled) return

                val macToCheck =
                        if (currentDevice.topicId.isNotBlank()) currentDevice.topicId
                        else currentDevice.mac
                Log.d("DeviceDetails", "Firmware check triggered for MAC: $macToCheck")
                isCheckingFirmware = true

                scope.launch {
                        try {
                                val response = RetrofitClient.instance.getVersion(macToCheck)
                                Log.d(
                                        "DeviceDetails",
                                        "Firmware API Response: ${response.code()}, body: ${response.body()}"
                                )

                                if (response.code() == 200 && response.body() != null) {
                                        val versionInfo = response.body()!!
                                        firmwareVersion = versionInfo.version ?: ""
                                        val newUrl = versionInfo.url ?: ""
                                        val previousUrl = firmwareUpdateUrl
                                        firmwareUpdateUrl = newUrl
                                        firmwareUpdateAvailable = newUrl.isNotBlank()

                                        // Reset download started state if a new update URL is found
                                        if (firmwareUpdateAvailable && newUrl != previousUrl) {
                                                firmwareDownloadStarted = false
                                        }

                                        if (firmwareUpdateAvailable) {
                                                Log.d(
                                                        "DeviceDetails",
                                                        "Update found: version=$firmwareVersion, url=$firmwareUpdateUrl"
                                                )
                                        }
                                } else if (response.code() == 304) {
                                        Log.d(
                                                "DeviceDetails",
                                                "Firmware is already up to date (304)"
                                        )
                                        firmwareUpdateAvailable = false
                                        firmwareDownloadStarted = false
                                }
                        } catch (e: Exception) {
                                Log.e("DeviceDetails", "Firmware check failed", e)
                        } finally {
                                isCheckingFirmware = false
                        }
                }
        }

        // Trigger firmware check when connected and notifications are enabled
        LaunchedEffect(isConnected, updateNotificationsEnabled, currentDevice.mac) {
                if (isConnected && updateNotificationsEnabled) {
                        checkFirmwareUpdate()
                }
        }

        // Load device config when screen opens (regardless of connection state)
        LaunchedEffect(currentDevice.mac, currentDevice.topicId) { loadDeviceConfig() }

        // Also reload config when connected (in case it changed)
        LaunchedEffect(isConnected) {
                if (isConnected) {
                        loadDeviceConfig()
                }
        }

        // Start scanning automatically when screen opens if device is not connected
        LaunchedEffect(device.mac) {
                if (!isConnected && !isConnecting && !autoConnectDisabled) {
                        bleManager.startScanning()
                }
        }

        // Continue scanning if not connected and scanning stopped
        LaunchedEffect(isConnected, isConnecting, isScanning, autoConnectDisabled) {
                if (!isConnected && !isConnecting && !isScanning && !autoConnectDisabled) {
                        bleManager.startScanning()
                }
        }


        Scaffold(
                topBar = {
                        TopAppBar(
                                title = { Text(currentDevice.displayName) },
                                navigationIcon = {
                                        IconButton(onClick = onBack) {
                                                Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Wróć"
                                                )
                                        }
                                },
                                actions = {
                                        if (isConnected) {
                                                TextButton(
                                                        onClick = {
                                                                autoConnectDisabled = true
                                                                bleManager.disconnect()
                                                        }
                                                ) {
                                                        Text(
                                                                "Rozłącz",
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .error
                                                        )
                                                }
                                        } else {
                                                Button(
                                                        onClick = {
                                                                val bleDevice =
                                                                        foundDevices.find {
                                                                                it.address ==
                                                                                        currentDevice
                                                                                                .mac
                                                                        }
                                                                if (bleDevice != null) {
                                                                        bleManager.connect(
                                                                                bleDevice
                                                                        )
                                                                } else {
                                                                        bleManager.startScanning()
                                                                        Toast.makeText(
                                                                                        context,
                                                                                        "Skanowanie...",
                                                                                        Toast.LENGTH_SHORT
                                                                                )
                                                                                .show()
                                                                }
                                                        },
                                                        enabled = !isConnecting
                                                ) {
                                                        Text(
                                                                if (isConnecting) "Łączenie..."
                                                                else "Połącz"
                                                        )
                                                }
                                        }
                                }
                        )
                }
        ) { padding ->
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .padding(padding)
                                        .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        AnimatedVisibility(
                                visible = alert.isNotEmpty(),
                                enter = expandVertically(),
                                exit = shrinkVertically()
                        ) {
                                Surface(modifier = Modifier.fillMaxWidth(), color = Color.Red) {
                                        Text(
                                                text = alert,
                                                modifier = Modifier.padding(16.dp),
                                                color = Color.White
                                        )
                                }
                        }

                        Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column {
                                                Text(
                                                        text = "Status Urządzenia",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .headlineSmall
                                                )
                                                Text(
                                                        text = "MAC: ${currentDevice.mac}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.Gray
                                                )
                                        }
                                        Surface(
                                                color =
                                                        if (isConnected) Color(0xFF4CAF50)
                                                        else Color.Gray,
                                                shape = MaterialTheme.shapes.small
                                        ) {
                                                Text(
                                                        text =
                                                                if (isConnected) "POŁĄCZONO"
                                                                else "ROZŁĄCZONO",
                                                        modifier =
                                                                Modifier.padding(
                                                                        horizontal = 8.dp,
                                                                        vertical = 4.dp
                                                                ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White
                                                )
                                        }
                                }

                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                        text = "NAJNOWSZE POMIARY",
                                                        style = MaterialTheme.typography.titleMedium
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                DetailRow(
                                                        label = "Temperatura",
                                                        value = temperature
                                                )
                                                HorizontalDivider(
                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                )
                                                DetailRow(label = "pH", value = ph)
                                                HorizontalDivider(
                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                )
                                                val statusColor =
                                                        if (feedStatus
                                                                        .lowercase()
                                                                        .contains("sukces")
                                                        )
                                                                Color(0xFF4CAF50)
                                                        else if (feedStatus == "Nieznany")
                                                                MaterialTheme.colorScheme.onSurface
                                                        else Color.Red
                                                DetailRow(
                                                        label = "Status karmienia",
                                                        value = feedStatus,
                                                        valueColor = statusColor
                                                )
                                        }
                                }

                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                        Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                Text(
                                                        text = "DZIAŁANIA",
                                                        style = MaterialTheme.typography.titleMedium
                                                )
                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        Button(
                                                                onClick = {
                                                                        bleManager.sendCommand(
                                                                                bleManager
                                                                                        .FORCE_TEMP_CHR_UUID,
                                                                                byteArrayOf(0x01)
                                                                        )
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                enabled = isConnected
                                                        ) {
                                                                Text(
                                                                        "Zmierz temp.",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                )
                                                        }
                                                        Button(
                                                                onClick = {
                                                                        bleManager.sendCommand(
                                                                                bleManager
                                                                                        .FORCE_PH_CHR_UUID,
                                                                                byteArrayOf(0x01)
                                                                        )
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                enabled = isConnected
                                                        ) {
                                                                Text(
                                                                        "Zmierz pH",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                )
                                                        }
                                                        Button(
                                                                onClick = {
                                                                        bleManager.sendCommand(
                                                                                bleManager
                                                                                        .FORCE_FEED_CHR_UUID,
                                                                                byteArrayOf(0x01)
                                                                        )
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                enabled = isConnected
                                                        ) {
                                                                Text(
                                                                        "Nakarm",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                )
                                                        }
                                                }
                                        }
                                }

                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth().clickable {
                                                                        isConfigurationExpanded =
                                                                                !isConfigurationExpanded
                                                                },
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Text(
                                                                text = "KONFIGURACJA",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium
                                                        )
                                                        Icon(
                                                                imageVector =
                                                                        if (isConfigurationExpanded)
                                                                                Icons.Default
                                                                                        .ExpandLess
                                                                        else
                                                                                Icons.Default
                                                                                        .ExpandMore,
                                                                contentDescription = null
                                                        )
                                                }

                                                AnimatedVisibility(
                                                        visible = isConfigurationExpanded
                                                ) {
                                                        Column(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(16.dp)
                                                        ) {
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        8.dp
                                                                                )
                                                                )
                                                                // Device Name
                                                                if (isEditingName) {
                                                                        OutlinedTextField(
                                                                                value = editedName,
                                                                                onValueChange = {
                                                                                        editedName =
                                                                                                it
                                                                                },
                                                                                label = {
                                                                                        Text(
                                                                                                "Nazwa"
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth()
                                                                        )
                                                                        Row(
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth(),
                                                                                horizontalArrangement =
                                                                                        Arrangement
                                                                                                .spacedBy(
                                                                                                        8.dp
                                                                                                )
                                                                        ) {
                                                                                Button(
                                                                                        onClick = {
                                                                                                if (editedName
                                                                                                                .isNotBlank()
                                                                                                ) {
                                                                                                        val updated =
                                                                                                                currentDevice
                                                                                                                        .copy(
                                                                                                                                displayName =
                                                                                                                                        editedName
                                                                                                                        )
                                                                                                        deviceStore
                                                                                                                .saveDevice(
                                                                                                                        updated
                                                                                                                )
                                                                                                        currentDevice =
                                                                                                                updated
                                                                                                        onDeviceUpdated
                                                                                                                ?.invoke(
                                                                                                                        updated
                                                                                                                )
                                                                                                        isEditingName =
                                                                                                                false

                                                                                                        // Send name change to API
                                                                                                        scope
                                                                                                                .launch {
                                                                                                                        try {
                                                                                                                                RetrofitClient
                                                                                                                                        .instance
                                                                                                                                        .setName(
                                                                                                                                                SetNameRequest(
                                                                                                                                                        device_id =
                                                                                                                                                                currentDevice
                                                                                                                                                                        .topicId,
                                                                                                                                                        name =
                                                                                                                                                                editedName
                                                                                                                                                )
                                                                                                                                        )
                                                                                                                        } catch (
                                                                                                                                e:
                                                                                                                                        Exception) {
                                                                                                                                android.util
                                                                                                                                        .Log
                                                                                                                                        .e(
                                                                                                                                                "DeviceDetails",
                                                                                                                                                "Błąd podczas zmiany nazwy urządzenia",
                                                                                                                                                e
                                                                                                                                        )
                                                                                                                        }
                                                                                                                }
                                                                                                }
                                                                                        },
                                                                                        modifier =
                                                                                                Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                ) { Text("Zapisz") }
                                                                                OutlinedButton(
                                                                                        onClick = {
                                                                                                isEditingName =
                                                                                                        false
                                                                                        },
                                                                                        modifier =
                                                                                                Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                ) { Text("Anuluj") }
                                                                        }
                                                                } else {
                                                                        Row(
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth(),
                                                                                horizontalArrangement =
                                                                                        Arrangement
                                                                                                .SpaceBetween
                                                                        ) {
                                                                                Text(
                                                                                        text =
                                                                                                currentDevice
                                                                                                        .displayName
                                                                                )
                                                                                TextButton(
                                                                                        onClick = {
                                                                                                isEditingName =
                                                                                                        true
                                                                                        }
                                                                                ) { Text("Zmień") }
                                                                        }
                                                                }

                                                                HorizontalDivider()

                                                                // Display loaded config
                                                                if (loadedConfig != null) {
                                                                        Card(
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth(),
                                                                                colors =
                                                                                        CardDefaults
                                                                                                .cardColors(
                                                                                                        containerColor =
                                                                                                                MaterialTheme
                                                                                                                        .colorScheme
                                                                                                                        .surfaceVariant
                                                                                                )
                                                                        ) {
                                                                                Column(
                                                                                        modifier =
                                                                                                Modifier.padding(
                                                                                                        12.dp
                                                                                                ),
                                                                                        verticalArrangement =
                                                                                                Arrangement
                                                                                                        .spacedBy(
                                                                                                                4.dp
                                                                                                        )
                                                                                ) {
                                                                                        Text(
                                                                                                text =
                                                                                                        "Aktualna konfiguracja z serwera",
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .titleSmall,
                                                                                                color =
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .primary
                                                                                        )
                                                                                        loadedConfig
                                                                                                ?.let {
                                                                                                        config
                                                                                                        ->
                                                                                                        config[
                                                                                                                        "temp_lower"]
                                                                                                                ?.let {
                                                                                                                        Text(
                                                                                                                                text =
                                                                                                                                        "Temp min: $it",
                                                                                                                                style =
                                                                                                                                        MaterialTheme
                                                                                                                                                .typography
                                                                                                                                                .bodySmall
                                                                                                                        )
                                                                                                                }
                                                                                                        config[
                                                                                                                        "temp_upper"]
                                                                                                                ?.let {
                                                                                                                        Text(
                                                                                                                                text =
                                                                                                                                        "Temp max: $it",
                                                                                                                                style =
                                                                                                                                        MaterialTheme
                                                                                                                                                .typography
                                                                                                                                                .bodySmall
                                                                                                                        )
                                                                                                                }
                                                                                                        config[
                                                                                                                        "ph_lower"]
                                                                                                                ?.let {
                                                                                                                        Text(
                                                                                                                                text =
                                                                                                                                        "pH min: $it",
                                                                                                                                style =
                                                                                                                                        MaterialTheme
                                                                                                                                                .typography
                                                                                                                                                .bodySmall
                                                                                                                        )
                                                                                                                }
                                                                                                        config[
                                                                                                                        "ph_upper"]
                                                                                                                ?.let {
                                                                                                                        Text(
                                                                                                                                text =
                                                                                                                                        "pH max: $it",
                                                                                                                                style =
                                                                                                                                        MaterialTheme
                                                                                                                                                .typography
                                                                                                                                                .bodySmall
                                                                                                                        )
                                                                                                                }
                                                                                                        config[
                                                                                                                        "temp_frequency"]
                                                                                                                ?.let {
                                                                                                                        Text(
                                                                                                                                text =
                                                                                                                                        "Częstotliwość temp: ${it}s",
                                                                                                                                style =
                                                                                                                                        MaterialTheme
                                                                                                                                                .typography
                                                                                                                                                .bodySmall
                                                                                                                        )
                                                                                                                }
                                                                                                        config[
                                                                                                                        "feed_frequency"]
                                                                                                                ?.let {
                                                                                                                        Text(
                                                                                                                                text =
                                                                                                                                        "Częstotliwość karmienia: ${it}s",
                                                                                                                                style =
                                                                                                                                        MaterialTheme
                                                                                                                                                .typography
                                                                                                                                                .bodySmall
                                                                                                                        )
                                                                                                                }
                                                                                                        config[
                                                                                                                        "wake_frequency"]
                                                                                                                ?.let {
                                                                                                                        Text(
                                                                                                                                text =
                                                                                                                                        "Częstotliwość publikacji: ${it}s",
                                                                                                                                style =
                                                                                                                                        MaterialTheme
                                                                                                                                                .typography
                                                                                                                                                .bodySmall
                                                                                                                        )
                                                                                                                }
                                                                                                }
                                                                                }
                                                                        }
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                }

                                                                // Thresholds
                                                                Text(
                                                                        text = "Progi Alarmowe",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleSmall
                                                                )
                                                                Row(
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        )
                                                                ) {
                                                                        OutlinedTextField(
                                                                                value = tempLower,
                                                                                onValueChange = {
                                                                                        tempLower =
                                                                                                it
                                                                                },
                                                                                label = {
                                                                                        Text(
                                                                                                "Temp min"
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        )
                                                                        OutlinedTextField(
                                                                                value = tempUpper,
                                                                                onValueChange = {
                                                                                        tempUpper =
                                                                                                it
                                                                                },
                                                                                label = {
                                                                                        Text(
                                                                                                "Temp max"
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        )
                                                                }
                                                                Button(
                                                                        onClick = {
                                                                                scope.launch {
                                                                                        val deviceId =
                                                                                                if (currentDevice
                                                                                                                .topicId
                                                                                                                .isNotBlank()
                                                                                                )
                                                                                                        currentDevice
                                                                                                                .topicId
                                                                                                else
                                                                                                        currentDevice
                                                                                                                .mac
                                                                                        tempLower
                                                                                                .toFloatOrNull()
                                                                                                ?.let {
                                                                                                        bleManager
                                                                                                                .setTemperatureThreshold(
                                                                                                                        true,
                                                                                                                        it,
                                                                                                                        deviceId
                                                                                                                )
                                                                                                }
                                                                                        delay(
                                                                                                300
                                                                                        ) // Delay
                                                                                        // to
                                                                                        // prevent
                                                                                        // BLE
                                                                                        // write
                                                                                        // conflicts
                                                                                        tempUpper
                                                                                                .toFloatOrNull()
                                                                                                ?.let {
                                                                                                        bleManager
                                                                                                                .setTemperatureThreshold(
                                                                                                                        false,
                                                                                                                        it,
                                                                                                                        deviceId
                                                                                                                )
                                                                                                }
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Progi temperatury wysłane",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                }
                                                                        },
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        enabled =
                                                                                isConnected &&
                                                                                        (tempLower
                                                                                                .toFloatOrNull() !=
                                                                                                null ||
                                                                                                tempUpper
                                                                                                        .toFloatOrNull() !=
                                                                                                        null)
                                                                ) { Text("Zapisz progi temp") }

                                                                Row(
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        )
                                                                ) {
                                                                        OutlinedTextField(
                                                                                value = phLower,
                                                                                onValueChange = {
                                                                                        phLower = it
                                                                                },
                                                                                label = {
                                                                                        Text(
                                                                                                "pH min"
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        )
                                                                        OutlinedTextField(
                                                                                value = phUpper,
                                                                                onValueChange = {
                                                                                        phUpper = it
                                                                                },
                                                                                label = {
                                                                                        Text(
                                                                                                "pH max"
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        )
                                                                }
                                                                Button(
                                                                        onClick = {
                                                                                scope.launch {
                                                                                        val deviceId =
                                                                                                if (currentDevice
                                                                                                                .topicId
                                                                                                                .isNotBlank()
                                                                                                )
                                                                                                        currentDevice
                                                                                                                .topicId
                                                                                                else
                                                                                                        currentDevice
                                                                                                                .mac
                                                                                        phLower.toFloatOrNull()
                                                                                                ?.let {
                                                                                                        bleManager
                                                                                                                .setPhThreshold(
                                                                                                                        true,
                                                                                                                        it,
                                                                                                                        deviceId
                                                                                                                )
                                                                                                }
                                                                                        delay(
                                                                                                300
                                                                                        ) // Delay
                                                                                        // to
                                                                                        // prevent
                                                                                        // BLE
                                                                                        // write
                                                                                        // conflicts
                                                                                        phUpper.toFloatOrNull()
                                                                                                ?.let {
                                                                                                        bleManager
                                                                                                                .setPhThreshold(
                                                                                                                        false,
                                                                                                                        it,
                                                                                                                        deviceId
                                                                                                                )
                                                                                                }
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Progi pH wysłane",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                }
                                                                        },
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        enabled =
                                                                                isConnected &&
                                                                                        (phLower.toFloatOrNull() !=
                                                                                                null ||
                                                                                                phUpper.toFloatOrNull() !=
                                                                                                        null)
                                                                ) { Text("Zapisz progi pH") }

                                                                HorizontalDivider()
                                                                // Update Notifications Toggle
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceBetween,
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Column(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        ) {
                                                                                Text(
                                                                                        text =
                                                                                                "Powiadomienia o aktualizacjach",
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .titleSmall
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                "Włącz, aby otrzymywać informacje o dostępnych aktualizacjach firmware",
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .bodySmall,
                                                                                        color =
                                                                                                Color.Gray
                                                                                )
                                                                        }
                                                                        Switch(
                                                                                checked =
                                                                                        updateNotificationsEnabled,
                                                                                onCheckedChange = {
                                                                                        enabled ->
                                                                                        updateNotificationsEnabled =
                                                                                                enabled
                                                                                        updatePrefs
                                                                                                .edit()
                                                                                                .putBoolean(
                                                                                                        "${currentDevice.mac}_update_notifications",
                                                                                                        enabled
                                                                                                )
                                                                                                .apply()
                                                                                        if (enabled &&
                                                                                                        isConnected
                                                                                        ) {
                                                                                                checkFirmwareUpdate()
                                                                                        } else {
                                                                                                firmwareUpdateAvailable =
                                                                                                        false
                                                                                        }
                                                                                }
                                                                        )
                                                                }
                                                                HorizontalDivider()
                                                                // Frequency
                                                                CustomIntervalSelector(
                                                                        label = "Temp interval",
                                                                        enabled = isConnected,
                                                                        unit = tempIntervalUnit,
                                                                        value = tempIntervalValue,
                                                                        onUnitChange = { tempIntervalUnit = it },
                                                                        onValueChange = { tempIntervalValue = it },
                                                                        onApply = { seconds ->
                                                                                scope.launch {
                                                                                        selectedTempInterval = seconds
                                                                                        val deviceId =
                                                                                                if (currentDevice.topicId.isNotBlank())
                                                                                                        currentDevice.topicId
                                                                                                else
                                                                                                        currentDevice.mac
                                                                                        bleManager.sendCommand(
                                                                                                bleManager.TEMP_INTERVAL_CHR_UUID,
                                                                                                bleManager.createIntervalPayload(seconds)
                                                                                        )
                                                                                        // Send API request after writing to characteristic
                                                                                        try {
                                                                                                RetrofitClient.instance.setConfig(
                                                                                                        SetConfigRequest(
                                                                                                                device_id = deviceId,
                                                                                                                name = "temp_frequency",
                                                                                                                value = seconds.toString()
                                                                                                        )
                                                                                                )
                                                                                                Log.d(
                                                                                                        "DeviceDetails",
                                                                                                        "Sent config: temp_frequency = $seconds for device $deviceId"
                                                                                                )
                                                                                        } catch (e: Exception) {
                                                                                                Log.e(
                                                                                                        "DeviceDetails",
                                                                                                        "Failed to send config for temp_frequency",
                                                                                                        e
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }
                                                                )
                                                                CustomIntervalSelector(
                                                                        label = "Feed interval",
                                                                        enabled = isConnected,
                                                                        unit = feedIntervalUnit,
                                                                        value = feedIntervalValue,
                                                                        onUnitChange = { feedIntervalUnit = it },
                                                                        onValueChange = { feedIntervalValue = it },
                                                                        onApply = { seconds ->
                                                                                scope.launch {
                                                                                        selectedFeedInterval = seconds
                                                                                        val deviceId =
                                                                                                if (currentDevice.topicId.isNotBlank())
                                                                                                        currentDevice.topicId
                                                                                                else
                                                                                                        currentDevice.mac
                                                                                        bleManager.sendCommand(
                                                                                                bleManager.FEED_INTERVAL_CHR_UUID,
                                                                                                bleManager.createIntervalPayload(seconds)
                                                                                        )
                                                                                        // Send API request after writing to characteristic
                                                                                        try {
                                                                                                RetrofitClient.instance.setConfig(
                                                                                                        SetConfigRequest(
                                                                                                                device_id = deviceId,
                                                                                                                name = "feed_frequency",
                                                                                                                value = seconds.toString()
                                                                                                        )
                                                                                                )
                                                                                                Log.d(
                                                                                                        "DeviceDetails",
                                                                                                        "Sent config: feed_frequency = $seconds for device $deviceId"
                                                                                                )
                                                                                        } catch (e: Exception) {
                                                                                                Log.e(
                                                                                                        "DeviceDetails",
                                                                                                        "Failed to send config for feed_frequency",
                                                                                                        e
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }
                                                                )
                                                                CustomIntervalSelector(
                                                                        label = "Publish interval",
                                                                        enabled = isConnected,
                                                                        unit = publishIntervalUnit,
                                                                        value = publishIntervalValue,
                                                                        onUnitChange = { publishIntervalUnit = it },
                                                                        onValueChange = { publishIntervalValue = it },
                                                                        onApply = { seconds ->
                                                                                scope.launch {
                                                                                        selectedPublishInterval = seconds
                                                                                        val deviceId =
                                                                                                if (currentDevice.topicId.isNotBlank())
                                                                                                        currentDevice.topicId
                                                                                                else
                                                                                                        currentDevice.mac
                                                                                        bleManager.sendCommand(
                                                                                                bleManager.PUBLISH_INTERVAL_CHR_UUID,
                                                                                                bleManager.createIntervalPayload(seconds)
                                                                                        )
                                                                                        // Send API request after writing to characteristic
                                                                                        try {
                                                                                                RetrofitClient.instance.setConfig(
                                                                                                        SetConfigRequest(
                                                                                                                device_id = deviceId,
                                                                                                                name = "wake_frequency",
                                                                                                                value = seconds.toString()
                                                                                                        )
                                                                                                )
                                                                                                Log.d(
                                                                                                        "DeviceDetails",
                                                                                                        "Sent config: wake_frequency = $seconds for device $deviceId"
                                                                                                )
                                                                                        } catch (e: Exception) {
                                                                                                Log.e(
                                                                                                        "DeviceDetails",
                                                                                                        "Failed to send config for wake_frequency",
                                                                                                        e
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }
                                                                )

                                                                HorizontalDivider()
                                                                // WiFi
                                                                Text(
                                                                        text = "WiFi",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleSmall
                                                                )
                                                                OutlinedTextField(
                                                                        value = wifiSsid,
                                                                        onValueChange = {
                                                                                wifiSsid = it
                                                                        },
                                                                        label = { Text("SSID") },
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                )
                                                                OutlinedTextField(
                                                                        value = wifiPass,
                                                                        onValueChange = {
                                                                                wifiPass = it
                                                                        },
                                                                        label = { Text("Pass") },
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                )
                                                                Button(
                                                                        onClick = {
                                                                                isUpdatingWifi =
                                                                                        true
                                                                                bleManager
                                                                                        .sendWifiConfig(
                                                                                                wifiSsid,
                                                                                                wifiPass
                                                                                        ) {
                                                                                                isUpdatingWifi =
                                                                                                        false
                                                                                        }
                                                                        },
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        enabled =
                                                                                isConnected &&
                                                                                        wifiSsid.isNotBlank()
                                                                ) { Text("Zastosuj WiFi") }
                                                        }
                                                }
                                        }
                                }

                                // Firmware Update Card - only shown when notifications are enabled
                                if (updateNotificationsEnabled) {
                                        Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                elevation = CardDefaults.cardElevation(2.dp),
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        if (firmwareUpdateAvailable)
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant
                                                        )
                                        ) {
                                                Column(
                                                        modifier = Modifier.padding(16.dp),
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        Text(
                                                                text = "AKTUALIZACJA FIRMWARE",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium
                                                        )
                                                        if (firmwareUpdateAvailable) {
                                                                Text(
                                                                        text =
                                                                                "Dostępna jest nowa wersja firmware: $firmwareVersion",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall
                                                                )
                                                                Button(
                                                                        onClick = {
                                                                                if (firmwareUpdateUrl
                                                                                                .isNotBlank()
                                                                                ) {
                                                                                        firmwareDownloadStarted =
                                                                                                true
                                                                                        bleManager
                                                                                                .acceptFirmwareUpdate(
                                                                                                        firmwareUpdateUrl
                                                                                                )
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Rozpoczęto pobieranie aktualizacji",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                }
                                                                        },
                                                                        enabled =
                                                                                isConnected &&
                                                                                        firmwareUpdateUrl
                                                                                                .isNotBlank() &&
                                                                                        !firmwareDownloadStarted,
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                ) { Text("Pobierz teraz") }
                                                        } else {
                                                                Text(
                                                                        text =
                                                                                if (firmwareVersion
                                                                                                .isNotBlank()
                                                                                )
                                                                                        "Wersja: $firmwareVersion. Brak dostępnych aktualizacji."
                                                                                else if (isCheckingFirmware
                                                                                )
                                                                                        "Sprawdzanie aktualizacji..."
                                                                                else
                                                                                        "Brak dostępnych aktualizacji.",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall,
                                                                        color = Color.Gray
                                                                )
                                                                if (isCheckingFirmware) {
                                                                        LinearProgressIndicator(
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth()
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }

                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer
                                                )
                                ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                        text = "USUŃ URZĄDZENIE",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onErrorContainer
                                                )
                                                Button(
                                                        onClick = {
                                                                scope.launch {
                                                                        try {
                                                                                // Send unbind
                                                                                // request to API
                                                                                // before removing
                                                                                // device
                                                                                if (userId.isNotBlank() &&
                                                                                                currentDevice
                                                                                                        .topicId
                                                                                                        .isNotBlank()
                                                                                ) {
                                                                                        RetrofitClient
                                                                                                .instance
                                                                                                .rebind(
                                                                                                        RebindRequest(
                                                                                                                currentDevice
                                                                                                                        .topicId,
                                                                                                                userId,
                                                                                                                unbind =
                                                                                                                        true
                                                                                                        )
                                                                                                )
                                                                                }
                                                                                if (isConnected)
                                                                                        bleManager
                                                                                                .forgetDevice()
                                                                                deviceStore
                                                                                        .deleteDevice(
                                                                                                currentDevice
                                                                                                        .mac
                                                                                        )
                                                                                onBack()
                                                                        } catch (e: Exception) {
                                                                                Log.e(
                                                                                        "DeviceDetails",
                                                                                        "Delete error",
                                                                                        e
                                                                                )
                                                                        }
                                                                }
                                                        },
                                                        enabled = isConnected,
                                                        colors =
                                                                ButtonDefaults.buttonColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                )
                                                ) { Text("Usuń") }
                                        }
                                }
                        }
                }
        }
}

@Composable
fun DetailRow(
        label: String,
        value: String,
        valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = valueColor
                )
        }
}

@Composable
fun FrequencySelector(
        label: String,
        options: List<Pair<String, Int>>,
        enabled: Boolean,
        initialValue: Int? = null,
        onSelected: (Int) -> Unit
) {
        var expanded by remember { mutableStateOf(false) }
        val initialText =
                initialValue?.let { value ->
                        options.find { it.second == value }?.first ?: options[0].first
                }
                        ?: options[0].first
        var selectedText by remember { mutableStateOf(initialText) }

        // Update selectedText when initialValue changes
        LaunchedEffect(initialValue) {
                if (initialValue != null) {
                        selectedText =
                                options.find { it.second == initialValue }?.first
                                        ?: options[0].first
                }
        }
        Column(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable(enabled = enabled) { expanded = true }
                                        .padding(vertical = 8.dp)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Text(text = selectedText)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                options.forEach { (text, value) ->
                                        DropdownMenuItem(
                                                text = { Text(text) },
                                                onClick = {
                                                        selectedText = text
                                                        expanded = false
                                                        onSelected(value)
                                                }
                                        )
                                }
                        }
                }
        }
}

@Composable
fun CustomIntervalSelector(
        label: String,
        enabled: Boolean,
        unit: String,
        value: Int,
        onUnitChange: (String) -> Unit,
        onValueChange: (Int) -> Unit,
        onApply: (Int) -> Unit
) {
        var unitExpanded by remember { mutableStateOf(false) }
        var valueExpanded by remember { mutableStateOf(false) }
        
        val unitOptions = listOf("never", "min", "hr", "days")
        val valueOptions = when (unit) {
                "min" -> (1..60).toList()
                "hr" -> (1..24).toList()
                "days" -> (1..7).toList()
                else -> (1..60).toList()
        }
        
        // Helper function to convert unit/value to seconds
        fun convertToSeconds(u: String, v: Int): Int {
                return when (u) {
                        "never" -> 0
                        "min" -> v * 60
                        "hr" -> v * 3600
                        "days" -> v * 86400
                        else -> v * 60
                }
        }
        
        // Ensure value is within valid range for current unit
        LaunchedEffect(unit) {
                if (unit != "never") {
                        val validValue = when (unit) {
                                "min" -> value.coerceIn(1, 60)
                                "hr" -> value.coerceIn(1, 24)
                                "days" -> value.coerceIn(1, 7)
                                else -> value
                        }
                        if (validValue != value) {
                                onValueChange(validValue)
                        }
                }
        }
        
        Column(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Value selector (hidden when "never" is selected)
                        if (unit != "never") {
                                Box(
                                        modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = enabled) { valueExpanded = true }
                                                .padding(vertical = 8.dp)
                                ) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(text = value.toString())
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                                expanded = valueExpanded,
                                                onDismissRequest = { valueExpanded = false }
                                        ) {
                                                valueOptions.forEach { v ->
                                                        DropdownMenuItem(
                                                                text = { Text(v.toString()) },
                                                                onClick = {
                                                                        onValueChange(v)
                                                                        val seconds = convertToSeconds(unit, v)
                                                                        onApply(seconds)
                                                                        valueExpanded = false
                                                                }
                                                        )
                                                }
                                        }
                                }
                        } else {
                                // Spacer when "never" is selected to maintain layout
                                Spacer(modifier = Modifier.weight(1f))
                        }
                        
                        // Unit selector
                        Box(
                                modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = enabled) { unitExpanded = true }
                                        .padding(vertical = 8.dp)
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Text(text = unit)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                        expanded = unitExpanded,
                                        onDismissRequest = { unitExpanded = false }
                                ) {
                                        unitOptions.forEach { u ->
                                                DropdownMenuItem(
                                                        text = { Text(u) },
                                                        onClick = {
                                                                onUnitChange(u)
                                                                if (u == "never") {
                                                                        // For "never", value doesn't matter, send 0
                                                                        onApply(0)
                                                                } else {
                                                                        // Reset value to valid range for new unit
                                                                        val newValue = when (u) {
                                                                                "min" -> value.coerceIn(1, 60)
                                                                                "hr" -> value.coerceIn(1, 24)
                                                                                "days" -> value.coerceIn(1, 7)
                                                                                else -> value
                                                                        }
                                                                        onValueChange(newValue)
                                                                        val seconds = convertToSeconds(u, newValue)
                                                                        onApply(seconds)
                                                                }
                                                                unitExpanded = false
                                                        }
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun ScheduleButton(label: String, enabled: Boolean = true) {
        val context = LocalContext.current
        var selectedDateTime by remember { mutableStateOf("Nie zaplanowano") }

        Column(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(
                        text = selectedDateTime,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable(enabled = enabled) {
                                                val calendar = Calendar.getInstance()
                                                DatePickerDialog(
                                                                context,
                                                                { _, year, month, day ->
                                                                        TimePickerDialog(
                                                                                        context,
                                                                                        {
                                                                                                _,
                                                                                                hour,
                                                                                                minute
                                                                                                ->
                                                                                                selectedDateTime =
                                                                                                        String.format(
                                                                                                                "%02d.%02d.%04d %02d:%02d",
                                                                                                                day,
                                                                                                                month +
                                                                                                                        1,
                                                                                                                year,
                                                                                                                hour,
                                                                                                                minute
                                                                                                        )
                                                                                        },
                                                                                        calendar.get(
                                                                                                Calendar.HOUR_OF_DAY
                                                                                        ),
                                                                                        calendar.get(
                                                                                                Calendar.MINUTE
                                                                                        ),
                                                                                        true
                                                                                )
                                                                                .show()
                                                                },
                                                                calendar.get(Calendar.YEAR),
                                                                calendar.get(Calendar.MONTH),
                                                                calendar.get(Calendar.DAY_OF_MONTH)
                                                        )
                                                        .show()
                                        }
                                        .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
        }
}
