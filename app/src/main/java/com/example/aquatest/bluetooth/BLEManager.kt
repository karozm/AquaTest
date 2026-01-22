package com.example.aquatest.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.aquatest.api.ProvisionResponseData
import com.example.aquatest.api.RetrofitClient
import com.example.aquatest.api.SetConfigRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

@SuppressLint("MissingPermission")
class BLEManager(private val context: Context) {
    private val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner = adapter?.bluetoothLeScanner
    private var scanCallback: ScanCallback? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var currentMtu = 23 // Domyślna wartość MTU dla BLE

    // SharedPreferences for storing telemetry data
    private val telemetryPrefs: SharedPreferences =
            context.getSharedPreferences("device_telemetry", Context.MODE_PRIVATE)

    // PROVISIONING SERVICE
    private val PROV_SVC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee0")
    private val PROV_SSID_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee1")
    private val PROV_PASS_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee2")
    private val PROV_CERTIFICATE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee3")
    private val PROV_PRIVATE_KEY_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee4")
    private val PROV_ROOT_CA_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee5")
    private val PROV_MAC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee6")
    private val PROV_FORGET_DEVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee7")
    private val PROV_APPLY_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee8")

    // COMMAND SERVICE (abcdec0)
    private val COMMAND_SVC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec0")
    val FORCE_FEED_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec1")
    val FORCE_TEMP_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec2")
    val FORCE_PH_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec3")
    val TEMP_INTERVAL_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec4")
    val FEED_INTERVAL_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec5")
    val PUBLISH_INTERVAL_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec6")
    val FIRMWARE_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec7")
    val TEMP_LOWER_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec8")
    val TEMP_UPPER_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdec9")
    val PH_LOWER_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdeca")
    val PH_UPPER_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdecb")

    // TELEMETRY SERVICE (abcded0)
    private val TELEMETRY_SVC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcded0")
    val TEMP_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcded1")
    val PH_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcded2")
    val FEED_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcded3")
    val ALERT_CHR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcded4")

    private val CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _foundDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    // Map to store device names extracted from scan records (key: device address, value: device
    // name)
    private val _deviceNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceNames = _deviceNames.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _deviceMac = MutableStateFlow("")
    val deviceMac = _deviceMac.asStateFlow()

    private val _firmwareVersion = MutableStateFlow("")
    val firmwareVersion = _firmwareVersion.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // Telemetry flows
    private val _temperature = MutableStateFlow("-- °C")
    val temperature = _temperature.asStateFlow()

    private val _ph = MutableStateFlow("--")
    val ph = _ph.asStateFlow()

    private val _feedStatus = MutableStateFlow("Nieznany")
    val feedStatus = _feedStatus.asStateFlow()

    // Alert flow - holds current alert message, empty string means no alert
    private val _alert = MutableStateFlow("")
    val alert = _alert.asStateFlow()

    private val gattCallback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                ) {
                    Log.d(
                            "BLE_CONN",
                            "onConnectionStateChange: gatt=${gatt.device.address}, status=$status, newState=$newState"
                    )

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e("BLE_CONN", "Błąd połączenia: status=$status")
                        gatt.close()
                        if (bluetoothGatt?.device?.address == gatt.device.address) {
                            bluetoothGatt = null
                        }
                        _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
                        _connectedDevice.value = null
                        return
                    }

                    _connectionState.value = newState
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        _connectedDevice.value = gatt.device
                        Log.d("BLE_CONN", "Połączono z ${gatt.device.address}. Żądam MTU 512...")
                        gatt.requestMtu(512)
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d("BLE_CONN", "Rozłączono z ${gatt.device.address}. Zamykam GATT.")
                        gatt.close()
                        if (bluetoothGatt?.device?.address == gatt.device.address) {
                            bluetoothGatt = null
                        }
                        _connectedDevice.value = null
                        _deviceMac.value = ""
                        _temperature.value = "-- °C"
                        _ph.value = "--"
                        _feedStatus.value = "Nieznany"
                        currentMtu = 23
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        currentMtu = mtu
                        Log.d("BLE_CONN", "MTU zmienione na: $mtu")
                    } else {
                        Log.e("BLE_CONN", "Błąd zmiany MTU: $status")
                    }
                    Log.d("BLE_CONN", "Odkrywam usługi...")
                    gatt.discoverServices()
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d("BLE", "=== Usługi odkryte ===")
                        // ... existing logging code ...

                        // Auto-subscribe to telemetry notifications
                        Log.d("BLE", "Automatyczna subskrypcja telemetrii...")
                        setupTelemetryNotifications()
                    } else {
                        Log.e("BLE", "Błąd odkrywania usług: status=$status")
                    }
                }

                override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        when (characteristic.uuid) {
                            PROV_MAC_UUID -> {
                                val mac = characteristic.getStringValue(0)
                                Log.d("BLE_PROV", "Odczytano MAC urządzenia: $mac")
                                _deviceMac.value = mac ?: ""
                            }
                            FIRMWARE_CHR_UUID -> {
                                val version = characteristic.getStringValue(0)
                                Log.d("BLE_FW", "Odczytano wersję firmware: $version")
                                _firmwareVersion.value = version ?: ""
                            }
                        }
                    }
                }

                override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic
                ) {
                    val data = characteristic.value
                    val charUuid = characteristic.uuid

                    when (characteristic.uuid) {
                        TEMP_CHR_UUID, PH_CHR_UUID -> {
                            val numericValue =
                                    try {
                                        when (data.size) {
                                            4 -> {
                                                val buffer = ByteBuffer.wrap(data)
                                                buffer.order(ByteOrder.LITTLE_ENDIAN)
                                                buffer.float
                                            }
                                            else -> {
                                                val stringValue =
                                                        String(data, Charsets.UTF_8).trim()
                                                stringValue.toFloatOrNull() ?: Float.NaN
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Float.NaN
                                    }

                            if (!numericValue.isNaN()) {
                                if (characteristic.uuid == TEMP_CHR_UUID) {
                                    val formatted = String.format("%.1f", numericValue)
                                    _temperature.value = "$formatted °C"
                                    _connectedDevice.value?.address?.let { address ->
                                        saveTelemetryValue(
                                                address,
                                                "temperature",
                                                _temperature.value
                                        )
                                    }
                                } else {
                                    val formatted = String.format("%.2f", numericValue)
                                    _ph.value = formatted
                                    _connectedDevice.value?.address?.let { address ->
                                        saveTelemetryValue(address, "ph", _ph.value)
                                    }
                                }
                            }
                        }
                        FEED_CHR_UUID -> {
                            val feedResult =
                                    if (data.isNotEmpty()) (data[0].toInt() and 0xFF) == 0x01
                                    else false
                            val timestamp =
                                    java.text.SimpleDateFormat(
                                                    "HH:mm:ss",
                                                    java.util.Locale.getDefault()
                                            )
                                            .format(java.util.Date())
                            val statusText =
                                    if (feedResult) "udane ($timestamp)"
                                    else "nieudane ($timestamp)"
                            _feedStatus.value = statusText
                            _connectedDevice.value?.address?.let { address ->
                                saveTelemetryValue(address, "feedStatus", _feedStatus.value)
                            }
                        }
                        ALERT_CHR_UUID -> {
                            val alertMessage =
                                    try {
                                        val jsonString = String(data, Charsets.UTF_8).trim()
                                        val json = JSONObject(jsonString)
                                        val event = json.getString("event")
                                        val value = json.getString("value")

                                        // Format alert message based on event type
                                        when (event) {
                                            "temp_below" -> "Temperatura poniżej normy: $value"
                                            "temp_above" -> "Temperatura powyżej normy: $value"
                                            "ph_below" -> "pH poniżej normy: $value"
                                            "ph_above" -> "pH powyżej normy: $value"
                                            "hardware_error" -> {
                                                val errorType =
                                                        if (value == "feed_failed") "Błąd karmienia"
                                                        else value
                                                "❌ Błąd sprzętu: $errorType"
                                            }
                                            else -> "Alert: $event = $value"
                                        }
                                    } catch (e: Exception) {
                                        Log.e("BLE_ALERT", "Błąd parsowania alertu", e)
                                        // Fallback to raw string if JSON parsing fails
                                        try {
                                            String(data, Charsets.UTF_8).trim()
                                        } catch (e2: Exception) {
                                            ""
                                        }
                                    }

                            if (alertMessage.isNotEmpty()) {
                                _alert.value = alertMessage
                                Handler(Looper.getMainLooper())
                                        .postDelayed(
                                                {
                                                    if (_alert.value == alertMessage)
                                                            _alert.value = ""
                                                },
                                                5000
                                        )
                            }
                        }
                    }
                }
            }

    fun setupTelemetryNotifications() {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(TELEMETRY_SVC_UUID) ?: return
        val chars = listOf(TEMP_CHR_UUID, PH_CHR_UUID, FEED_CHR_UUID, ALERT_CHR_UUID)

        chars.forEachIndexed { index, uuid ->
            val characteristic = service.getCharacteristic(uuid)
            if (characteristic != null) {
                Handler(Looper.getMainLooper())
                        .postDelayed(
                                {
                                    gatt.setCharacteristicNotification(characteristic, true)
                                    val descriptor =
                                            characteristic.getDescriptor(
                                                    CLIENT_CHARACTERISTIC_CONFIG
                                            )
                                    if (descriptor != null) {
                                        descriptor.value =
                                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        gatt.writeDescriptor(descriptor)
                                        Log.d("BLE", "Zasubskrybowano: $uuid")
                                    }
                                },
                                (index * 200).toLong()
                        )
            }
        }
    }

    fun startScanning() {
        if (adapter == null || !adapter.isEnabled) return
        stopScanning()
        _foundDevices.value = emptyList()
        _deviceNames.value = emptyMap()
        _isScanning.value = true

        scanCallback =
                object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val device = result.device
                        val scanRecord = result.scanRecord
                        val deviceName = scanRecord?.deviceName ?: device.name

                        if (!_foundDevices.value.any { it.address == device.address }) {
                            _foundDevices.value = _foundDevices.value + device
                            if (deviceName != null) {
                                _deviceNames.value =
                                        _deviceNames.value + (device.address to deviceName)
                            }
                        }
                    }
                }

        scanner?.startScan(scanCallback)
        Handler(Looper.getMainLooper()).postDelayed({ stopScanning() }, 10000)
    }

    fun stopScanning() {
        scanCallback?.let {
            scanner?.stopScan(it)
            scanCallback = null
        }
        _isScanning.value = false
    }

    fun connect(device: BluetoothDevice) {
        Log.d("BLE_CONN", "Próba połączenia z: ${device.address}")

        // Always close previous connection if any
        bluetoothGatt?.close()

        _connectionState.value = BluetoothProfile.STATE_CONNECTING
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        Log.d("BLE_CONN", "Rozłączanie manualne...")
        bluetoothGatt?.disconnect()
    }

    fun readDeviceMac() {
        val service = bluetoothGatt?.getService(PROV_SVC_UUID) ?: return
        val char = service.getCharacteristic(PROV_MAC_UUID) ?: return
        bluetoothGatt?.readCharacteristic(char)
    }

    fun sendCommand(charUuid: UUID, payload: ByteArray, onDone: (() -> Unit)? = null) {
        val service = bluetoothGatt?.getService(COMMAND_SVC_UUID) ?: return
        val characteristic = service.getCharacteristic(charUuid) ?: return
        characteristic.value = payload
        bluetoothGatt?.writeCharacteristic(characteristic)
        onDone?.invoke()
    }

    fun sendProvisioningCerts(rootCa: String, data: ProvisionResponseData, onDone: () -> Unit) {
        val service = bluetoothGatt?.getService(PROV_SVC_UUID) ?: return

        fun sendRootCa() {
            writeCharacteristicInChunks(service, PROV_ROOT_CA_UUID, rootCa) {
                Handler(Looper.getMainLooper())
                        .postDelayed(
                                {
                                    writeCharacteristic(service, PROV_APPLY_UUID, byteArrayOf(1))
                                    onDone()
                                },
                                500
                        )
            }
        }

        fun sendPrivateKey() {
            writeCharacteristicInChunks(service, PROV_PRIVATE_KEY_UUID, data.private_key) {
                Handler(Looper.getMainLooper()).postDelayed({ sendRootCa() }, 1000)
            }
        }

        writeCharacteristicInChunks(service, PROV_CERTIFICATE_UUID, data.certificate_pem) {
            Handler(Looper.getMainLooper()).postDelayed({ sendPrivateKey() }, 1000)
        }
    }

    fun sendWifiConfig(ssid: String, pass: String, onDone: () -> Unit) {
        val service = bluetoothGatt?.getService(PROV_SVC_UUID) ?: return

        fun apply() {
            writeCharacteristic(service, PROV_APPLY_UUID, byteArrayOf(1))
            Handler(Looper.getMainLooper()).postDelayed({ onDone() }, 1000)
        }

        fun sendPass() {
            writeCharacteristic(service, PROV_PASS_UUID, pass)
            Handler(Looper.getMainLooper()).postDelayed({ apply() }, 1000)
        }

        writeCharacteristic(service, PROV_SSID_UUID, ssid)
        Handler(Looper.getMainLooper()).postDelayed({ sendPass() }, 1000)
    }

    fun forgetDevice() {
        val service = bluetoothGatt?.getService(PROV_SVC_UUID) ?: return
        writeCharacteristic(service, PROV_FORGET_DEVICE_UUID, byteArrayOf(1))
    }

    private fun writeCharacteristicInChunks(
            service: BluetoothGattService,
            charUuid: UUID,
            data: String?,
            onFinished: () -> Unit
    ) {
        val characteristic = service.getCharacteristic(charUuid) ?: return
        val bytes = data?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        val chunkSize = currentMtu - 3
        val chunks = bytes.asSequence().chunked(chunkSize).map { it.toByteArray() }.toMutableList()
        chunks.add(byteArrayOf(0x00))

        val chunkIterator = chunks.iterator()
        fun writeNextChunk() {
            if (chunkIterator.hasNext()) {
                characteristic.value = chunkIterator.next()
                bluetoothGatt?.writeCharacteristic(characteristic)
                Handler(Looper.getMainLooper()).postDelayed(::writeNextChunk, 500L)
            } else {
                onFinished()
            }
        }
        writeNextChunk()
    }

    private fun writeCharacteristic(service: BluetoothGattService, charUuid: UUID, value: Any?) {
        val char = service.getCharacteristic(charUuid) ?: return
        val dataToSend =
                when (value) {
                    is String -> value.toByteArray()
                    is ByteArray -> value
                    else -> return
                }
        char.value = dataToSend
        bluetoothGatt?.writeCharacteristic(char)
    }

    fun createIntervalPayload(intervalInSeconds: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(intervalInSeconds)
        return buffer.array()
    }

    fun acceptFirmwareUpdate(firmwareUrl: String) {
        val service = bluetoothGatt?.getService(COMMAND_SVC_UUID) ?: return
        writeCharacteristicInChunks(service, FIRMWARE_CHR_UUID, firmwareUrl) {}
    }

    fun rejectFirmwareUpdate() {}

    private fun saveTelemetryValue(deviceAddress: String, key: String, value: String) {
        telemetryPrefs.edit().putString("${deviceAddress}_$key", value).apply()
    }

    suspend fun setTemperatureThreshold(isLower: Boolean, value: Float, deviceId: String) {
        val charUuid = if (isLower) TEMP_LOWER_CHR_UUID else TEMP_UPPER_CHR_UUID
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
        sendCommand(charUuid, buffer.array())

        // Send API request after writing to characteristic
        try {
            val configName = if (isLower) "temp_lower" else "temp_upper"
            RetrofitClient.instance.setConfig(
                    SetConfigRequest(
                            device_id = deviceId,
                            name = configName,
                            value = value.toString()
                    )
            )
            Log.d("BLE_CONFIG", "Sent config: $configName = $value for device $deviceId")
        } catch (e: Exception) {
            Log.e("BLE_CONFIG", "Failed to send config for temperature threshold", e)
        }
    }

    suspend fun setPhThreshold(isLower: Boolean, value: Float, deviceId: String) {
        val charUuid = if (isLower) PH_LOWER_CHR_UUID else PH_UPPER_CHR_UUID
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
        sendCommand(charUuid, buffer.array())

        // Send API request after writing to characteristic
        try {
            val configName = if (isLower) "ph_lower" else "ph_upper"
            RetrofitClient.instance.setConfig(
                    SetConfigRequest(
                            device_id = deviceId,
                            name = configName,
                            value = value.toString()
                    )
            )
            Log.d("BLE_CONFIG", "Sent config: $configName = $value for device $deviceId")
        } catch (e: Exception) {
            Log.e("BLE_CONFIG", "Failed to send config for pH threshold", e)
        }
    }
}
