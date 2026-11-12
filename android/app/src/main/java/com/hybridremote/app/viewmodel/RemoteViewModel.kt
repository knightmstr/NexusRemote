package com.hybridremote.app.viewmodel

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hybridremote.app.hardware.InfraredManager
import com.hybridremote.app.network.FireTvManager
import com.hybridremote.app.network.FireTvManager.ConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Mode selector defining whether commands route over low-level Consumer IR pulses
 * or ADB TCP/IP transport (port 5555).
 */
enum class RemoteModeState {
    INFRARED,
    WI_FI
}

/**
 * Immutable Unified UI State exposed to Jetpack Compose screens.
 */
data class RemoteUiState(
    val remoteMode: RemoteModeState = RemoteModeState.WI_FI,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val savedTargetIp: String = "",
    val appDockList: List<String> = emptyList(),
    val hasIrHardware: Boolean = false,
    val isConnecting: Boolean = false,
    val activeInputText: String = "",
    val isVirtualKeyboardOpen: Boolean = false,
    val searchPresets: List<String> = listOf("4K Movies", "Netflix Originals", "Top Music", "Documentaries", "Trending News"),
    val lastActionFeedback: String? = null
)

/**
 * Principal MVVM ViewModel orchestrating hardware IR and ADB TCP/IP connection states,
 * persistent DataStore IP caching, and Compose-reactive StateFlows.
 */
class RemoteViewModel(
    private val fireTvManager: FireTvManager,
    private val infraredManager: InfraredManager,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        private const val TAG = "RemoteViewModel"
        private val KEY_TARGET_IP = stringPreferencesKey("target_tv_ip")
        private const val DEFAULT_FALLBACK_IP = "192.168.1.150"
    }

    // Active Remote Mode [INFRARED, WI_FI]
    private val _remoteMode = MutableStateFlow(RemoteModeState.WI_FI)
    val remoteMode: StateFlow<RemoteModeState> = _remoteMode.asStateFlow()

    // Transient action feedback message for snackbars/haptics
    private val _lastActionFeedback = MutableStateFlow<String?>(null)
    val lastActionFeedback: StateFlow<String?> = _lastActionFeedback.asStateFlow()

    // Staged keyboard text
    private val _currentKeyboardInput = MutableStateFlow("")
    val currentKeyboardInput: StateFlow<String> = _currentKeyboardInput.asStateFlow()

    // Virtual Keyboard overlay toggle state
    private val _isVirtualKeyboardOpen = MutableStateFlow(false)
    val isVirtualKeyboardOpen: StateFlow<Boolean> = _isVirtualKeyboardOpen.asStateFlow()

    // Persistent Target IP from Preferences DataStore
    val savedTargetIp: StateFlow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading Preferences DataStore: ${exception.message}", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_TARGET_IP] ?: DEFAULT_FALLBACK_IP
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FALLBACK_IP
        )

    // ConnectionStatus forwarded from FireTvManager StateFlow
    val connectionStatus: StateFlow<ConnectionStatus> = fireTvManager.connectionStatus

    // Dynamic 3rd-party App Dock List forwarded from FireTvManager StateFlow
    val appDockList: StateFlow<List<String>> = fireTvManager.installedApps

    // Unified UI state flow combining all reactive properties
    val uiState: StateFlow<RemoteUiState> = combine(
        _remoteMode,
        fireTvManager.connectionStatus,
        savedTargetIp,
        fireTvManager.installedApps,
        _currentKeyboardInput,
        _isVirtualKeyboardOpen,
        _lastActionFeedback
    ) { mode, status, ip, dockList, keyboardText, isKbdOpen, feedback ->
        RemoteUiState(
            remoteMode = mode,
            connectionStatus = status,
            savedTargetIp = ip,
            appDockList = dockList,
            hasIrHardware = infraredManager.hasIrEmitter(),
            isConnecting = status == ConnectionStatus.CONNECTING,
            activeInputText = keyboardText,
            isVirtualKeyboardOpen = isKbdOpen,
            lastActionFeedback = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RemoteUiState(
            hasIrHardware = infraredManager.hasIrEmitter()
        )
    )

    init {
        // Observe connection state; automatically persist IP upon successful connection
        viewModelScope.launch {
            fireTvManager.connectionStatus.collect { status ->
                if (status == ConnectionStatus.CONNECTED) {
                    val ipToSave = savedTargetIp.value
                    if (ipToSave.isNotBlank()) {
                        persistIpAddress(ipToSave)
                    }
                    _lastActionFeedback.value = "Connected to Fire TV ($ipToSave)"
                } else if (status == ConnectionStatus.AUTH_REQUIRED) {
                    _lastActionFeedback.value = "Action Required: Grant ADB Authorization on TV screen"
                }
            }
        }
    }

    // Remote Mode Switching
    fun setRemoteMode(mode: RemoteModeState) {
        _remoteMode.value = mode
        _lastActionFeedback.value = "Switched to ${mode.name} mode"
    }

    // Network / ADB Actions
    fun connectToTv(ipAddress: String) {
        val sanitizedIp = ipAddress.trim()
        viewModelScope.launch(Dispatchers.IO) {
            persistIpAddress(sanitizedIp)
            fireTvManager.connectToTv(sanitizedIp)
        }
    }

    fun disconnectTv() {
        fireTvManager.disconnect()
        _lastActionFeedback.value = "Disconnected from TV"
    }

    fun updateTargetIpInput(ip: String) {
        viewModelScope.launch(Dispatchers.IO) {
            persistIpAddress(ip.trim())
        }
    }

    private suspend fun persistIpAddress(ip: String) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_TARGET_IP] = ip
            }
            Log.d(TAG, "Cached target IP '$ip' in Preferences DataStore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed caching target IP in DataStore: ${e.message}", e)
        }
    }

    fun sendDpadCommand(keyCode: Int) {
        fireTvManager.sendDpadCommand(keyCode)
    }

    fun setKeyboardInputText(text: String) {
        _currentKeyboardInput.value = text
    }

    fun toggleVirtualKeyboard() {
        _isVirtualKeyboardOpen.value = !_isVirtualKeyboardOpen.value
    }

    fun openTvSearch() {
        fireTvManager.openSearch()
        _lastActionFeedback.value = "Activated TV Search Field (KEYCODE_SEARCH)"
    }

    fun sendBackspace() {
        fireTvManager.sendBackspace()
        if (_currentKeyboardInput.value.isNotEmpty()) {
            _currentKeyboardInput.value = _currentKeyboardInput.value.dropLast(1)
        }
        _lastActionFeedback.value = "Sent Backspace (KEYCODE_DEL)"
    }

    fun sendEnter() {
        fireTvManager.sendEnter()
        _lastActionFeedback.value = "Submitted Query (KEYCODE_ENTER)"
    }

    fun sendSearchQuery(query: String) {
        fireTvManager.sendSearchQuery(query)
        _lastActionFeedback.value = "Searched on TV: \"$query\""
        _currentKeyboardInput.value = ""
    }

    fun appendKeyboardCharacter(char: String) {
        _currentKeyboardInput.value += char
        fireTvManager.injectText(char)
    }

    fun clearKeyboardInput() {
        _currentKeyboardInput.value = ""
    }

    fun submitKeyboardInput() {
        val text = _currentKeyboardInput.value
        if (text.isNotBlank()) {
            fireTvManager.injectText(text)
            _lastActionFeedback.value = "Injected text: \"$text\""
            _currentKeyboardInput.value = ""
        }
    }

    fun launchApp(packageName: String) {
        fireTvManager.launchApp(packageName)
        _lastActionFeedback.value = "Launching: ${packageName.substringAfterLast('.')}"
    }

    fun refreshInstalledApps() {
        fireTvManager.fetchInstalledApps()
    }

    // Hardware Infrared Actions
    fun transmitIrPower() {
        val success = infraredManager.transmitPower()
        _lastActionFeedback.value = if (success) "IR: Transmitted TV Power" else "IR Error: Check hardware emitter"
    }

    fun transmitIrSource() {
        val success = infraredManager.transmitSource()
        _lastActionFeedback.value = if (success) "IR: Transmitted Input Source" else "IR Error"
    }

    fun transmitIrVolumeUp() {
        infraredManager.transmitVolumeUp()
    }

    fun transmitIrVolumeDown() {
        infraredManager.transmitVolumeDown()
    }

    fun transmitIrMute() {
        val success = infraredManager.transmitMute()
        _lastActionFeedback.value = if (success) "IR: Transmitted Mute" else "IR Error"
    }

    fun transmitIrDigit(digit: Int) {
        val success = infraredManager.transmitDigit(digit)
        if (success) {
            _lastActionFeedback.value = "IR: Digit $digit"
        }
    }

    override fun onCleared() {
        super.onCleared()
        fireTvManager.release()
    }
}
