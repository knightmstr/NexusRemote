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
import com.hybridremote.app.hardware.TvBrandProfile
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
    val isBrandSelectorOpen: Boolean = false,
    val selectedBrand: TvBrandProfile,
    val availableBrands: List<TvBrandProfile> = emptyList(),
    val searchPresets: List<String> = listOf("4K Movies", "Netflix Originals", "Top Music", "Documentaries", "Trending News"),
    val lastActionFeedback: String? = null
)

/**
 * Principal MVVM ViewModel orchestrating hardware IR and ADB TCP/IP connection states,
 * universal TV brand profile selection, persistent DataStore caching, and Compose-reactive StateFlows.
 */
class RemoteViewModel(
    private val fireTvManager: FireTvManager,
    private val infraredManager: InfraredManager,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        private const val TAG = "RemoteViewModel"
        private val KEY_TARGET_IP = stringPreferencesKey("target_tv_ip")
        private val KEY_TV_BRAND_ID = stringPreferencesKey("selected_tv_brand_id")
        private const val DEFAULT_FALLBACK_IP = "192.168.1.150"
    }

    // Active Remote Mode [INFRARED, WI_FI]
    private val _remoteMode = MutableStateFlow(RemoteModeState.WI_FI)
    val remoteMode: StateFlow<RemoteModeState> = _remoteMode.asStateFlow()

    // Universal TV Brand selection
    private val _selectedBrand = MutableStateFlow(infraredManager.getActiveBrand())
    val selectedBrand: StateFlow<TvBrandProfile> = _selectedBrand.asStateFlow()

    val availableBrands: List<TvBrandProfile> = infraredManager.getSupportedBrands()

    // Brand selector modal state
    private val _isBrandSelectorOpen = MutableStateFlow(false)
    val isBrandSelectorOpen: StateFlow<Boolean> = _isBrandSelectorOpen.asStateFlow()

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
        _selectedBrand,
        _isBrandSelectorOpen,
        _lastActionFeedback
    ) { args: Array<Any?> ->
        val mode = args[0] as RemoteModeState
        val status = args[1] as ConnectionStatus
        val ip = args[2] as String
        val dockList = args[3] as List<String>
        val keyboardText = args[4] as String
        val isKbdOpen = args[5] as Boolean
        val brand = args[6] as TvBrandProfile
        val isBrandOpen = args[7] as Boolean
        val feedback = args[8] as? String

        RemoteUiState(
            remoteMode = mode,
            connectionStatus = status,
            savedTargetIp = ip,
            appDockList = dockList,
            hasIrHardware = infraredManager.hasIrEmitter(),
            isConnecting = status == ConnectionStatus.CONNECTING,
            activeInputText = keyboardText,
            isVirtualKeyboardOpen = isKbdOpen,
            isBrandSelectorOpen = isBrandOpen,
            selectedBrand = brand,
            availableBrands = availableBrands,
            lastActionFeedback = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RemoteUiState(
            hasIrHardware = infraredManager.hasIrEmitter(),
            selectedBrand = infraredManager.getActiveBrand(),
            availableBrands = infraredManager.getSupportedBrands()
        )
    )

    init {
        // Load cached TV brand from DataStore
        viewModelScope.launch {
            dataStore.data.catch { emit(emptyPreferences()) }.collect { preferences ->
                val cachedBrandId = preferences[KEY_TV_BRAND_ID]
                if (!cachedBrandId.isNullOrBlank()) {
                    if (infraredManager.selectBrand(cachedBrandId)) {
                        _selectedBrand.value = infraredManager.getActiveBrand()
                        Log.d(TAG, "Restored cached TV Brand: ${_selectedBrand.value.name}")
                    }
                }
            }
        }

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

    // Universal TV Brand Setup
    fun selectTvBrand(brandId: String) {
        if (infraredManager.selectBrand(brandId)) {
            val newBrand = infraredManager.getActiveBrand()
            _selectedBrand.value = newBrand
            _isBrandSelectorOpen.value = false
            _lastActionFeedback.value = "Universal IR set to: ${newBrand.name}"

            // Persist brand selection to DataStore
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dataStore.edit { preferences ->
                        preferences[KEY_TV_BRAND_ID] = brandId
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed caching TV brand: ${e.message}", e)
                }
            }
        }
    }

    fun toggleBrandSelector() {
        _isBrandSelectorOpen.value = !_isBrandSelectorOpen.value
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

    // Hardware Universal Infrared Actions
    fun transmitIrPower() {
        val brand = _selectedBrand.value
        val success = infraredManager.transmitPower()
        _lastActionFeedback.value = if (success) "IR: Transmitted ${brand.name.split(" ")[0]} Power (${brand.carrierFrequencyHz / 1000}kHz)" else "IR Error: Check hardware emitter"
    }

    fun transmitIrSource() {
        val brand = _selectedBrand.value
        val success = infraredManager.transmitSource()
        _lastActionFeedback.value = if (success) "IR: Transmitted ${brand.name.split(" ")[0]} Input Source" else "IR Error"
    }

    fun transmitIrVolumeUp() {
        infraredManager.transmitVolumeUp()
    }

    fun transmitIrVolumeDown() {
        infraredManager.transmitVolumeDown()
    }

    fun transmitIrMute() {
        val brand = _selectedBrand.value
        val success = infraredManager.transmitMute()
        _lastActionFeedback.value = if (success) "IR: Transmitted ${brand.name.split(" ")[0]} Mute" else "IR Error"
    }

    fun transmitIrDigit(digit: Int) {
        val success = infraredManager.transmitDigit(digit)
        if (success) {
            _lastActionFeedback.value = "IR: ${selectedBrand.value.name.split(" ")[0]} Digit $digit"
        }
    }

    override fun onCleared() {
        super.onCleared()
        fireTvManager.release()
    }
}
