package com.example.aquatest.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

@SuppressLint("MissingPermission")
class BLEManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null

    // UUIDs z Twojej płytki ESP32
    private val WIFI_SVC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val SSID_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    private val PASS_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef2")
    private val APPLY_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef3")
    
    private val BATTERY_SVC_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private val _foundDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel = _batteryLevel.asStateFlow()

    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            _connectionState.value = newState
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectedDevice.value = gatt.device
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectedDevice.value = null
                _batteryLevel.value = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readBatteryLevel()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                _batteryLevel.value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
            }
        }

        // To wywoła się po udanym zapisie do charakterystyki
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == APPLY_UUID) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Konfiguracja WiFi zapisana!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startScanning() {
        if (adapter == null || !adapter.isEnabled) return
        _foundDevices.value = emptyList()
        _isScanning.value = true
        scanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (device.name != null && !_foundDevices.value.any { it.address == device.address }) {
                    _foundDevices.value = _foundDevices.value + device
                }
            }
        })
        Handler(Looper.getMainLooper()).postDelayed({ stopScanning() }, 10000)
    }

    fun stopScanning() {
        _isScanning.value = false
    }

    fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    fun readBatteryLevel() {
        val service = bluetoothGatt?.getService(BATTERY_SVC_UUID)
        val char = service?.getCharacteristic(BATTERY_LEVEL_UUID)
        if (char != null) bluetoothGatt?.readCharacteristic(char)
    }

    fun configureWifi(ssid: String, pass: String) {
        val service = bluetoothGatt?.getService(WIFI_SVC_UUID) ?: return
        
        val ssidChar = service.getCharacteristic(SSID_UUID)
        val passChar = service.getCharacteristic(PASS_UUID)
        val applyChar = service.getCharacteristic(APPLY_UUID)

        ssidChar?.let {
            it.value = ssid.toByteArray()
            bluetoothGatt?.writeCharacteristic(it)
        }
        
        Handler(Looper.getMainLooper()).postDelayed({
            passChar?.let {
                it.value = pass.toByteArray()
                bluetoothGatt?.writeCharacteristic(it)
            }
        }, 500)

        Handler(Looper.getMainLooper()).postDelayed({
            applyChar?.let {
                it.value = byteArrayOf(1)
                bluetoothGatt?.writeCharacteristic(it)
            }
        }, 1000)
    }
}
