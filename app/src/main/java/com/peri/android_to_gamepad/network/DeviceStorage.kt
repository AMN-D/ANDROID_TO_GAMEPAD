package com.peri.android_to_gamepad.network

import android.content.Context
import android.content.SharedPreferences

class DeviceStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("paired_devices", Context.MODE_PRIVATE)

    fun savePin(deviceName: String, pin: String) {
        prefs.edit().putString(deviceName, pin).apply()
    }

    fun getPin(deviceName: String): String? {
        return prefs.getString(deviceName, null)
    }

    fun removePin(deviceName: String) {
        prefs.edit().remove(deviceName).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
