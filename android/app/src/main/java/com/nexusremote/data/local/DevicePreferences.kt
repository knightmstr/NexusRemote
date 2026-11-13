package com.nexusremote.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_settings")

class DevicePreferences(private val context: Context) {

    companion object {
        private val KEY_LAST_DEVICE_ID = stringPreferencesKey("last_connected_device_id")
        private val KEY_SELECTED_IR_BRAND = stringPreferencesKey("selected_ir_brand_id")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_HOLD_TO_REPEAT = booleanPreferencesKey("hold_to_repeat_enabled")
        private val KEY_REPEAT_RATE_MS = intPreferencesKey("repeat_rate_ms")
        private val KEY_TOUCHPAD_MODE = booleanPreferencesKey("touchpad_mode_enabled")
    }

    val lastConnectedDeviceId: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_DEVICE_ID] }
    val selectedIrBrandId: Flow<String> = context.dataStore.data.map { it[KEY_SELECTED_IR_BRAND] ?: "samsung" }
    val isHapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_HAPTICS_ENABLED] ?: true }
    val isHoldToRepeatEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_HOLD_TO_REPEAT] ?: true }
    val repeatRateMs: Flow<Int> = context.dataStore.data.map { it[KEY_REPEAT_RATE_MS] ?: 120 }
    val isTouchpadMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_TOUCHPAD_MODE] ?: false }

    suspend fun setLastConnectedDeviceId(id: String) {
        context.dataStore.edit { it[KEY_LAST_DEVICE_ID] = id }
    }

    suspend fun setSelectedIrBrandId(brandId: String) {
        context.dataStore.edit { it[KEY_SELECTED_IR_BRAND] = brandId }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTICS_ENABLED] = enabled }
    }

    suspend fun setHoldToRepeatEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HOLD_TO_REPEAT] = enabled }
    }

    suspend fun setTouchpadMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TOUCHPAD_MODE] = enabled }
    }
}
