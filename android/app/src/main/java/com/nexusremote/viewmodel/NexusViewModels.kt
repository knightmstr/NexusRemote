package com.nexusremote.viewmodel

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexusremote.data.local.DeviceDao
import com.nexusremote.data.local.DevicePreferences
import com.nexusremote.discovery.NetworkDiscoveryManager
import com.nexusremote.domain.model.CommandResult
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.DiscoveredDevice
import com.nexusremote.domain.model.InstalledTvApp
import com.nexusremote.domain.model.ProtocolType
import com.nexusremote.domain.model.RemoteCommand
import com.nexusremote.domain.model.SavedDevice
import com.nexusremote.protocols.ConsumerIrAdapter
import com.nexusremote.protocols.DeviceController
import com.nexusremote.protocols.ProtocolRegistry
import com.nexusremote.protocols.UniversalIrBrand
import com.nexusremote.security.KeystoreSecretManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the Home Screen TV Catalog, mDNS/SSDP Discovery, and Device Configuration.
 */
class DeviceManagementViewModel(
    private val deviceDao: DeviceDao,
    private val discoveryManager: NetworkDiscoveryManager,
    private val devicePreferences: DevicePreferences,
    private val keystoreSecretManager: KeystoreSecretManager? = null,
    private val appContext: Context? = null
) : ViewModel() {

    companion object {
        private const val TAG = "DeviceManagementVM"
    }

    val savedDevices: StateFlow<List<SavedDevice>> = deviceDao.getAllDevicesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryManager.discoveredDevices
    val isScanning: StateFlow<Boolean> = discoveryManager.isScanning

    private val _selectedRoomFilter = MutableStateFlow<String?>(null)
    val selectedRoomFilter: StateFlow<String?> = _selectedRoomFilter.asStateFlow()

    private val _activeMessage = MutableStateFlow<String?>(null)
    val activeMessage: StateFlow<String?> = _activeMessage.asStateFlow()

    private var reachabilityJob: Job? = null

    init {
        startLightweightReachabilityMonitor()
    }

    fun startDiscovery() {
        discoveryManager.startLocalDiscovery()
    }

    fun stopDiscovery() {
        discoveryManager.stopLocalDiscovery()
    }

    fun setRoomFilter(room: String?) {
        _selectedRoomFilter.value = room
    }

    fun clearMessage() {
        _activeMessage.value = null
    }

    fun addDevice(
        name: String,
        brand: String,
        modelName: String,
        hostIp: String,
        port: Int,
        protocol: ProtocolType,
        room: String = "Living Room",
        isFavourite: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanIp = if (protocol == ProtocolType.CONSUMER_IR) "IR-${brand.lowercase().replace(" ", "_")}" else hostIp.trim()
            val device = SavedDevice(
                id = "${protocol.name.lowercase()}_${cleanIp.replace('.', '_')}",
                name = name.ifBlank { "$brand TV" },
                brand = brand,
                modelName = modelName.ifBlank { if (protocol == ProtocolType.CONSUMER_IR) "Universal IR Profile" else "Smart TV" },
                hostIp = cleanIp,
                port = port,
                protocolType = protocol,
                room = room,
                isFavourite = isFavourite,
                lastConnectedTimestamp = System.currentTimeMillis(),
                onlineStatus = if (protocol == ProtocolType.CONSUMER_IR) {
                    val hasIr = appContext?.let { ConsumerIrAdapter.isHardwareAvailable(it) } ?: false
                    if (hasIr) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
                } else {
                    DeviceOnlineStatus.CHECKING
                }
            )
            deviceDao.insertDevice(device)
            _activeMessage.value = "Saved '${device.name}' to $room"
        }
    }

    fun pairDiscoveredDevice(discovered: DiscoveredDevice, room: String = "Living Room") {
        addDevice(
            name = discovered.name,
            brand = discovered.brand,
            modelName = discovered.model,
            hostIp = discovered.ipAddress,
            port = discovered.port,
            protocol = discovered.protocolType,
            room = room
        )
    }

    fun updateDeviceProfile(device: SavedDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceDao.updateDevice(device)
            _activeMessage.value = "Updated '${device.name}'"
        }
    }

    fun deleteDevice(device: SavedDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceDao.deleteDevice(device)
            keystoreSecretManager?.removeDeviceSecrets(device.id)
            _activeMessage.value = "Removed '${device.name}' and erased device keys"
        }
    }

    fun renameDevice(id: String, newName: String, newRoom: String) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceDao.renameDevice(id, newName.trim(), newRoom.trim())
            _activeMessage.value = "Updated device profile"
        }
    }

    fun toggleFavourite(id: String, currentFav: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceDao.toggleFavourite(id, !currentFav)
        }
    }

    fun setDefaultDevice(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceDao.clearDefaultDevices()
            deviceDao.setDefaultDevice(id)
            _activeMessage.value = "Set as default television"
        }
    }

    fun rePairDevice(device: SavedDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            keystoreSecretManager?.removeDeviceSecrets(device.id)
            deviceDao.updateOnlineStatus(device.id, DeviceOnlineStatus.AUTH_REQUIRED)
            _activeMessage.value = "Cleared saved credentials for '${device.name}'. Open remote to re-pair."
        }
    }

    fun testDeviceConnection(device: SavedDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            if (device.protocolType == ProtocolType.CONSUMER_IR) {
                val hasIr = appContext?.let { ConsumerIrAdapter.isHardwareAvailable(it) } ?: false
                val status = if (hasIr) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
                deviceDao.updateOnlineStatus(device.id, status)
                _activeMessage.value = if (hasIr) "IR blaster ready for ${device.name}" else "No IR blaster hardware on this phone"
                return@launch
            }
            _activeMessage.value = "Testing ${device.name} (${device.hostIp}:${device.port})..."
            val verifiedIp = discoveryManager.verifyOrRelocateDevice(device)
            if (verifiedIp != null) {
                deviceDao.updateOnlineStatus(device.id, DeviceOnlineStatus.ONLINE)
                if (verifiedIp != device.hostIp) {
                    deviceDao.updateLastConnected(device.id, System.currentTimeMillis(), verifiedIp)
                    _activeMessage.value = "Found '${device.name}' at new IP $verifiedIp"
                } else {
                    _activeMessage.value = "'${device.name}' is online at $verifiedIp"
                }
            } else {
                deviceDao.updateOnlineStatus(device.id, DeviceOnlineStatus.OFFLINE)
                _activeMessage.value = "'${device.name}' did not respond on the local network"
            }
        }
    }

    /**
     * Smart Reconnect: Attempts cached IP first; only relocates if the IP has changed.
     * For CONSUMER_IR devices, verifies local hardware emitter directly without network scanning.
     */
    fun smartReconnect(device: SavedDevice, onResolved: (SavedDevice) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (device.protocolType == ProtocolType.CONSUMER_IR) {
                val hasIr = appContext?.let { ConsumerIrAdapter.isHardwareAvailable(it) } ?: false
                val updatedDevice = device.copy(
                    onlineStatus = if (hasIr) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
                )
                devicePreferences.setLastConnectedDeviceId(device.id)
                withContext(Dispatchers.Main) {
                    onResolved(updatedDevice)
                }
                return@launch
            }

            _activeMessage.value = "Connecting to ${device.name} (${device.hostIp})..."
            val verifiedIp = discoveryManager.verifyOrRelocateDevice(device)
            if (verifiedIp != null) {
                if (verifiedIp != device.hostIp) {
                    deviceDao.updateLastConnected(device.id, System.currentTimeMillis(), verifiedIp)
                }
                devicePreferences.setLastConnectedDeviceId(device.id)
                val updatedDevice = device.copy(hostIp = verifiedIp, onlineStatus = DeviceOnlineStatus.ONLINE)
                withContext(Dispatchers.Main) {
                    onResolved(updatedDevice)
                }
            } else {
                // Still allow opening the remote screen so user can inspect status, use IR companion, or edit IP
                deviceDao.updateOnlineStatus(device.id, DeviceOnlineStatus.OFFLINE)
                val offlineDevice = device.copy(onlineStatus = DeviceOnlineStatus.OFFLINE)
                _activeMessage.value = "${device.name} appears offline. Opened remote with IR & reconnect options."
                withContext(Dispatchers.Main) {
                    onResolved(offlineDevice)
                }
            }
        }
    }

    private fun startLightweightReachabilityMonitor() {
        reachabilityJob?.cancel()
        reachabilityJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30000) // Poll every 30s in background
                val devices = savedDevices.value
                devices.forEach { dev ->
                    val status = if (dev.protocolType == ProtocolType.CONSUMER_IR) {
                        val hasIr = appContext?.let { ConsumerIrAdapter.isHardwareAvailable(it) } ?: false
                        if (hasIr) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
                    } else {
                        val probe = discoveryManager.verifyOrRelocateDevice(dev)
                        if (probe != null) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
                    }
                    if (status != dev.onlineStatus) {
                        deviceDao.updateOnlineStatus(dev.id, status)
                    }
                }
            }
        }
    }
}

/**
 * Manages the active Remote Control screen: supports both Network Smart TV protocols
 * AND the Hybrid Universal IR Blaster Remote with brand switching, hold-to-repeat, haptics,
 * keyboard input, app launching, macros, and dark/light theming.
 */
class RemoteControlViewModel(
    private val protocolRegistry: ProtocolRegistry,
    private val context: Context,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val availableIrBrands: List<UniversalIrBrand> = ConsumerIrAdapter.UNIVERSAL_BRANDS

    private val _hasIrHardware = MutableStateFlow(ConsumerIrAdapter.isHardwareAvailable(context))
    val hasIrHardware: StateFlow<Boolean> = _hasIrHardware.asStateFlow()

    private val _selectedIrBrand = MutableStateFlow(ConsumerIrAdapter.UNIVERSAL_BRANDS.first())
    val selectedIrBrand: StateFlow<UniversalIrBrand> = _selectedIrBrand.asStateFlow()

    private val _isUniversalIrMode = MutableStateFlow(false)
    val isUniversalIrMode: StateFlow<Boolean> = _isUniversalIrMode.asStateFlow()

    private val _lastIrPulseTimestamp = MutableStateFlow(0L)
    val lastIrPulseTimestamp: StateFlow<Long> = _lastIrPulseTimestamp.asStateFlow()

    private val _activeDevice = MutableStateFlow<SavedDevice?>(null)
    val activeDevice: StateFlow<SavedDevice?> = _activeDevice.asStateFlow()

    private var networkController: DeviceController? = null
    private var irController: ConsumerIrAdapter? = null
    private var connectionObserverJob: Job? = null
    private var repeatJob: Job? = null

    private val _connectionStatus = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    val connectionStatus: StateFlow<DeviceOnlineStatus> = _connectionStatus.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<DeviceCapability>>(emptySet())
    val capabilities: StateFlow<Set<DeviceCapability>> = _capabilities.asStateFlow()

    private val _protocolLimitations = MutableStateFlow<List<String>>(emptyList())
    val protocolLimitations: StateFlow<List<String>> = _protocolLimitations.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledTvApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledTvApp>> = _installedApps.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _isTouchpadActive = MutableStateFlow(false)
    val isTouchpadActive: StateFlow<Boolean> = _isTouchpadActive.asStateFlow()

    private val _isVirtualKeyboardOpen = MutableStateFlow(false)
    val isVirtualKeyboardOpen: StateFlow<Boolean> = _isVirtualKeyboardOpen.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isHapticsEnabled = MutableStateFlow(true)
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    private val _isHoldToRepeatEnabled = MutableStateFlow(true)
    val isHoldToRepeatEnabled: StateFlow<Boolean> = _isHoldToRepeatEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            devicePreferences.isHapticsEnabled.collect { _isHapticsEnabled.value = it }
        }
        viewModelScope.launch {
            devicePreferences.isHoldToRepeatEnabled.collect { _isHoldToRepeatEnabled.value = it }
        }
    }

    private fun currentController(): DeviceController? {
        return if (_isUniversalIrMode.value) irController else networkController
    }

    fun bindDevice(device: SavedDevice) {
        _hasIrHardware.value = ConsumerIrAdapter.isHardwareAvailable(context)
        _activeDevice.value = device
        _installedApps.value = emptyList()
        _isTouchpadActive.value = false

        val matchedBrand = ConsumerIrAdapter.resolveBrand(device.brand)
        _selectedIrBrand.value = matchedBrand

        // Always keep an IR adapter ready so user can seamlessly toggle between Network and Universal IR
        val irProfile = device.copy(protocolType = ProtocolType.CONSUMER_IR, brand = matchedBrand.name)
        irController = ConsumerIrAdapter(irProfile, context).also { it.setActiveBrand(matchedBrand) }

        if (device.protocolType == ProtocolType.CONSUMER_IR) {
            networkController = null
            _isUniversalIrMode.value = true
            refreshActiveControllerState(connectNow = true)
        } else {
            networkController = protocolRegistry.createController(device)
            _isUniversalIrMode.value = false
            refreshActiveControllerState(connectNow = true)
        }
    }

    /**
     * Opens the standalone Universal IR Blaster Remote without needing a saved Wi-Fi TV.
     */
    fun bindUniversalIr(brand: UniversalIrBrand = _selectedIrBrand.value) {
        _hasIrHardware.value = ConsumerIrAdapter.isHardwareAvailable(context)
        _selectedIrBrand.value = brand
        _installedApps.value = emptyList()
        _isTouchpadActive.value = false

        val virtualIrDevice = SavedDevice(
            id = "universal_ir_${brand.id}",
            name = "${brand.name} Universal IR",
            brand = brand.name,
            modelName = brand.modelSeries,
            hostIp = "IR Blaster",
            port = 0,
            protocolType = ProtocolType.CONSUMER_IR,
            room = "Universal Remote",
            onlineStatus = if (_hasIrHardware.value) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
        )
        _activeDevice.value = virtualIrDevice
        networkController = null
        irController = ConsumerIrAdapter(virtualIrDevice, context).also { it.setActiveBrand(brand) }
        _isUniversalIrMode.value = true
        refreshActiveControllerState(connectNow = true)
    }

    fun switchRemoteMode(useUniversalIr: Boolean) {
        if (!useUniversalIr && networkController == null) {
            _feedback.value = "Pair a Wi-Fi TV on the Home screen to use Network mode"
            return
        }
        _isUniversalIrMode.value = useUniversalIr
        refreshActiveControllerState(connectNow = !useUniversalIr && _connectionStatus.value != DeviceOnlineStatus.ONLINE)
    }

    fun selectIrBrand(brand: UniversalIrBrand) {
        _selectedIrBrand.value = brand
        irController?.setActiveBrand(brand)
        if (_activeDevice.value?.protocolType == ProtocolType.CONSUMER_IR) {
            _activeDevice.value = _activeDevice.value?.copy(
                name = "${brand.name} Universal IR",
                brand = brand.name,
                modelName = brand.modelSeries
            )
        }
        _feedback.value = "IR Profile: ${brand.name} (${brand.carrierFrequencyHz / 1000} kHz ${brand.protocol.name})"
    }

    private fun refreshActiveControllerState(connectNow: Boolean) {
        val controller = currentController() ?: return
        _capabilities.value = controller.capabilities
        _protocolLimitations.value = controller.protocolLimitations

        connectionObserverJob?.cancel()
        connectionObserverJob = viewModelScope.launch {
            controller.connectionState.collectLatest { status ->
                _connectionStatus.value = status
            }
        }

        if (connectNow) {
            viewModelScope.launch(Dispatchers.IO) {
                controller.connect()
                if (controller.capabilities.contains(DeviceCapability.REAL_APP_LAUNCH)) {
                    _installedApps.value = controller.fetchInstalledApps()
                }
            }
        } else if (controller.capabilities.contains(DeviceCapability.REAL_APP_LAUNCH) && _installedApps.value.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                _installedApps.value = controller.fetchInstalledApps()
            }
        }
    }

    fun reconnectActiveDevice() {
        refreshActiveControllerState(connectNow = true)
    }

    fun refreshInstalledApps() {
        val controller = currentController() ?: return
        if (!controller.capabilities.contains(DeviceCapability.REAL_APP_LAUNCH)) return
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = controller.fetchInstalledApps()
            _feedback.value = "Refreshed ${_installedApps.value.size} installed TV apps"
        }
    }

    fun sendCommand(command: RemoteCommand) {
        triggerHaptic()
        val controller = currentController() ?: return
        if (_isUniversalIrMode.value) {
            _lastIrPulseTimestamp.value = System.currentTimeMillis()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = controller.sendCommand(command)
            when (result) {
                is CommandResult.Success -> {}
                is CommandResult.Failed -> _feedback.value = result.error
                CommandResult.UnsupportedByDevice -> _feedback.value = "Button unsupported by active protocol"
                CommandResult.AuthRequired -> _feedback.value = "Action required: Confirm pairing on TV"
            }
        }
    }

    fun startRepeatingCommand(command: RemoteCommand) {
        sendCommand(command)
        if (!_isHoldToRepeatEnabled.value) return
        repeatJob?.cancel()
        repeatJob = viewModelScope.launch(Dispatchers.IO) {
            delay(380)
            while (isActive) {
                val controller = currentController() ?: break
                if (_isUniversalIrMode.value) {
                    _lastIrPulseTimestamp.value = System.currentTimeMillis()
                }
                controller.sendCommand(command)
                withContext(Dispatchers.Main) { triggerHaptic() }
                delay(120)
            }
        }
    }

    fun stopRepeatingCommand() {
        repeatJob?.cancel()
        repeatJob = null
    }

    fun launchApp(app: InstalledTvApp) {
        triggerHaptic()
        val controller = currentController() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = controller.launchApp(app.packageNameOrAppId)
            _feedback.value = if (ok) "Launching ${app.name}" else "Failed launching ${app.name}"
        }
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun injectText(text: String) {
        if (text.isBlank()) return
        sendCommand(RemoteCommand.InjectText(text))
        _inputText.value = ""
    }

    fun appendVirtualChar(ch: String) {
        _inputText.value += ch
        sendCommand(RemoteCommand.InjectText(ch))
    }

    fun toggleTouchpad() {
        _isTouchpadActive.value = !_isTouchpadActive.value
    }

    fun toggleVirtualKeyboard() {
        _isVirtualKeyboardOpen.value = !_isVirtualKeyboardOpen.value
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun toggleHaptics() {
        val next = !_isHapticsEnabled.value
        _isHapticsEnabled.value = next
        viewModelScope.launch(Dispatchers.IO) { devicePreferences.setHapticsEnabled(next) }
    }

    fun toggleHoldToRepeat() {
        val next = !_isHoldToRepeatEnabled.value
        _isHoldToRepeatEnabled.value = next
        viewModelScope.launch(Dispatchers.IO) { devicePreferences.setHoldToRepeatEnabled(next) }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun executeMacro(macroName: String, sequence: List<RemoteCommand>) {
        triggerHaptic()
        val controller = currentController() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _feedback.value = "Running macro: $macroName..."
            val unsupportedSteps = mutableListOf<String>()
            sequence.forEach { cmd ->
                if (!isSupportedCommand(cmd, controller.capabilities)) {
                    unsupportedSteps.add(cmd.javaClass.simpleName)
                } else {
                    val res = controller.sendCommand(cmd)
                    if (res is CommandResult.UnsupportedByDevice) {
                        unsupportedSteps.add(cmd.javaClass.simpleName)
                    }
                    delay(300)
                }
            }
            _feedback.value = if (unsupportedSteps.isEmpty()) {
                "Macro '$macroName' complete"
            } else {
                "Macro '$macroName': skipped unsupported (${unsupportedSteps.joinToString(", ")})"
            }
        }
    }

    private fun isSupportedCommand(cmd: RemoteCommand, caps: Set<DeviceCapability>): Boolean {
        return when (cmd) {
            RemoteCommand.Power -> caps.contains(DeviceCapability.POWER_TOGGLE)
            RemoteCommand.PowerOn -> caps.contains(DeviceCapability.POWER_ON) || caps.contains(DeviceCapability.POWER_TOGGLE)
            RemoteCommand.PowerOff -> caps.contains(DeviceCapability.POWER_OFF) || caps.contains(DeviceCapability.POWER_TOGGLE)
            RemoteCommand.Mute -> caps.contains(DeviceCapability.VOLUME_MUTE)
            RemoteCommand.VolumeUp, RemoteCommand.VolumeDown -> caps.contains(DeviceCapability.VOLUME_CONTROL)
            else -> true
        }
    }

    private fun triggerHaptic() {
        if (!_isHapticsEnabled.value) return
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        repeatJob?.cancel()
        connectionObserverJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            networkController?.disconnect()
        }
    }
}
