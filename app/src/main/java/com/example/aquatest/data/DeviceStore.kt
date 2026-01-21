package com.example.aquatest.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedDevice(val mac: String, val displayName: String, val topicId: String)

class DeviceStore(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("saved_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveDevice(device: SavedDevice) {
        val devices = getSavedDevices().toMutableList()
        // Usuwamy stare wystąpienie jeśli istnieje (ten sam MAC)
        devices.removeAll { it.mac == device.mac }
        devices.add(device)
        sharedPrefs.edit().putString("devices_list", gson.toJson(devices)).apply()
    }

    fun getSavedDevices(): List<SavedDevice> {
        val json = sharedPrefs.getString("devices_list", null) ?: return emptyList()
        val type = object : TypeToken<List<SavedDevice>>() {}.type
        return gson.fromJson(json, type)
    }

    fun deleteDevice(mac: String) {
        val devices = getSavedDevices().toMutableList()
        devices.removeAll { it.mac == mac }
        sharedPrefs.edit().putString("devices_list", gson.toJson(devices)).apply()
    }

    fun clearAllDevices() {
        sharedPrefs.edit().putString("devices_list", null).apply()
    }

    // MAC mapping: device MAC (from characteristic) -> BLE MAC (that app sees)
    fun saveMacMapping(deviceMac: String, bleMac: String) {
        val mappings = getMacMappings().toMutableMap()
        mappings[deviceMac] = bleMac
        sharedPrefs.edit().putString("mac_mappings", gson.toJson(mappings)).apply()
    }

    fun getMacMappings(): Map<String, String> {
        val json = sharedPrefs.getString("mac_mappings", null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    fun getBleMacForDeviceMac(deviceMac: String): String? {
        return getMacMappings()[deviceMac]
    }
}
