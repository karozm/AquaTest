package com.example.aquatest.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.aquatest.api.ProvisionResponseData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

@SuppressLint("MissingPermission")
class BLEManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null

    // DZIAŁAJĄCE UUID WIFI (Little-endian)
    private val WIFI_SVC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val WIFI_SSID_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    private val WIFI_PASS_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef2")
    private val WIFI_APPLY_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef3")

    // POPRAWIONE UUID PROVISIONING (Odwzorowanie bajtów e0, e1, e2... na format Androida)
    private val PROV_SVC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee0")
    private val PROV_TOPIC_ID_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee1")
    private val PROV_CERTIFICATE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee2")
    private val PROV_PRIVATE_KEY_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee3")
    private val PROV_ROOT_CA_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdee4")

    private val _foundDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            _connectionState.value = newState
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectedDevice.value = gatt.device
                Log.d("BLE", "Połączono. Odkrywam usługi...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectedDevice.value = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Usługi odkryte.")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == WIFI_APPLY_UUID) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "WiFi wysłane do płytki!", Toast.LENGTH_SHORT).show()
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

    fun stopScanning() { _isScanning.value = false }

    fun connect(device: BluetoothDevice) { bluetoothGatt = device.connectGatt(context, false, gattCallback) }

    fun disconnect() { bluetoothGatt?.disconnect() }

    fun configureWifi(ssid: String, pass: String, onDone: () -> Unit) {
        val service = bluetoothGatt?.getService(WIFI_SVC_UUID)
        if (service == null) {
            Log.e("BLE", "BŁĄD: Nie znaleziono usługi WiFi ($WIFI_SVC_UUID)")
            onDone()
            return
        }
        
        Log.d("BLE", "Wysyłam SSID: $ssid")
        writeCharacteristic(service, WIFI_SSID_UUID, ssid)
        
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("BLE", "Wysyłam Hasło WiFi")
            writeCharacteristic(service, WIFI_PASS_UUID, pass)
        }, 500)
        
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("BLE", "Wysyłam APPLY")
            writeCharacteristic(service, WIFI_APPLY_UUID, byteArrayOf(1))
            onDone()
        }, 1000)
    }

    fun sendProvisioningData(topicId: String, data: ProvisionResponseData, onDone: () -> Unit) {
        val service = bluetoothGatt?.getService(PROV_SVC_UUID)
        if (service == null) {
            Log.e("BLE_PROV", "BŁĄD: Nie znaleziono usługi Provisioning ($PROV_SVC_UUID)")
            onDone()
            return
        }

        fun sendCert() {
            fun sendPrivateKey() {
                fun sendRootCA() {
                    Log.d("BLE_PROV", "Wysyłam Root CA (długość: ${data.root_ca_url?.length})")
                    writeCharacteristicInChunks(service, PROV_ROOT_CA_UUID, data.root_ca_url) {
                        Log.d("BLE_PROV", "Wysyłanie danych provisioningu zakończone.")
                        Handler(Looper.getMainLooper()).post(onDone)
                    }
                }

                Log.d("BLE_PROV", "Wysyłam Klucz Prywatny (długość: ${data.private_key?.length})")
                writeCharacteristicInChunks(service, PROV_PRIVATE_KEY_UUID, data.private_key) {
                    sendRootCA()
                }
            }

            Log.d("BLE_PROV", "Wysyłam Certyfikat (długość: ${data.certificate_pem?.length})")
            writeCharacteristicInChunks(service, PROV_CERTIFICATE_UUID, data.certificate_pem) {
                sendPrivateKey()
            }
        }

        Log.d("BLE_PROV", "Wysyłam Topic ID: $topicId")
        writeCharacteristic(service, PROV_TOPIC_ID_UUID, topicId)

        Handler(Looper.getMainLooper()).postDelayed({
            sendCert()
        }, 500)
    }

    private fun writeCharacteristicInChunks(service: BluetoothGattService, charUuid: UUID, data: String?, onFinished: () -> Unit) {
        val characteristic = service.getCharacteristic(charUuid)
        if (characteristic == null) {
            Log.e("BLE_PROV", "BŁĄD: Nie znaleziono charakterystyki $charUuid")
            onFinished()
            return
        }

        val bytes = data?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        val chunks = bytes.asSequence().chunked(20).map { it.toByteArray() }.toMutableList()
        chunks.add(byteArrayOf()) // Add an empty chunk at the end to signal completion

        val chunkIterator = chunks.iterator()
        var chunkIndex = 0
        val totalChunks = chunks.size

        fun writeNextChunk() {
            if (chunkIterator.hasNext()) {
                val chunk = chunkIterator.next()
                characteristic.value = chunk
                chunkIndex++
                val logValue = if (chunk.isEmpty()) "<empty>" else String(chunk, Charsets.UTF_8)
                Log.d("BLE_WRITE_CHUNK", "Char: $charUuid, Chunk $chunkIndex/$totalChunks, Wartość: $logValue")
                bluetoothGatt?.writeCharacteristic(characteristic)
                Handler(Looper.getMainLooper()).postDelayed(::writeNextChunk, 75L)
            } else {
                onFinished()
            }
        }

        writeNextChunk()
    }

    private fun writeCharacteristic(service: BluetoothGattService, charUuid: UUID, value: Any?) {
        if (value == null) return
        val char = service.getCharacteristic(charUuid)
        if (char == null) {
            Log.e("BLE", "BŁĄD: Nie znaleziono charakterystyki $charUuid")
            return
        }
        val dataToSend = when (value) {
            is String -> value.toByteArray()
            is ByteArray -> value
            else -> return
        }
        char.value = dataToSend
        Log.d("BLE_WRITE", "Char: $charUuid, Wartość: ${dataToSend.toString(Charsets.UTF_8)}")
        bluetoothGatt?.writeCharacteristic(char)
    }
}
