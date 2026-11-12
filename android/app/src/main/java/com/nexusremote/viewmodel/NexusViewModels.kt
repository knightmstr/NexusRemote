package com.nexusremote.viewmodel

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.nexusremote.protocols.DeviceController
import com.nexusremote.protocols.ProtocolRegistry
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the Home Screen TV Catalog, mDNS/SSDP Discovery, and Device Configuration.
 */
class DeviceManagementViewModel(
    private val deviceDao: DeviceDao,
    private val discoveryManager: NetworkDiscoveryManager,
    private val devicePreferences: DevicePreferences,
    private val keystoreSecretManager: KeystoreSecretManager? = null
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
            val device = SavedDevice(
                id = "${protocol.name.lowercase()}_${hostIp.replace('.', '_')}",
                name = name.ifBlank { "$brand TV" },
                brand = brand,
                modelName = modelName.ifBlank { "Smart TV" },
                hostIp = hostIp.trim(),
                port = port,
                protocolType = protocol,
                room = room,
                isFavourite = isFavourite,
                lastConnectedTimestamp = System.currentTimeMillis(),
                onlineStatus = DeviceOnlineStatus.CHECKING
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

    /**
     * Smart Reconnect: Attempts cached IP first; only relocates if the IP has changed.
     */
    fun smartReconnect(device: SavedDevice, onResolved: (SavedDevice) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
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
                deviceDao.updateOnlineStatus(device.id, DeviceOnlineStatus.OFFLINE)
                _activeMessage.value = "${device.name} is offline. Check power and Wi-Fi."
            }
        }
    }

    private fun startLightweightReachabilityMonitor() {
        reachabilityJob?.cancel()
        reachabilityJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(30000) // Poll every 30s in background
                val devices = savedDevices.value
                devices.forEach { dev ->
                    val probe = discoveryManager.verifyOrRelocateDevice(dev)
                    val status = if (probe != null) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
                    if (status != dev.onlineStatus) {
                        deviceDao.updateOnlineStatus(dev.id, status)
                    }
                }
            }
        }
    }
}

/**
 * Manages the active Remote Control screen, capability-driven UI,
 * haptics, hold-to-repeat, installed apps, and macro execution.
 */
class RemoteControlViewModel(
    private val protocolRegistry: ProtocolRegistry,
    private val context: Context,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val _activeDevice = MutableStateFlow<SavedDevice?>(null)
    val activeDevice: StateFlow<SavedDevice?> = _activeDevice.asStateFlow()

    private var activeController: DeviceController? = null

    private val _connectionStatus = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    val connectionStatus: StateFlow<DeviceOnlineStatus> = _connectionStatus.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<DeviceCapability>>(emptySet())
    val capabilities: Set<DeviceCapability> get() = _capabilities.value

    private val _installedApps = MutableStateFlow<List<InstalledTvApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledTvApp>> = _installedApps.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _isTouchpadActive = MutableStateFlow(false)
    val isTouchpadActive: StateFlow<Boolean> = _isTouchpadActive.asStateFlow()

    fun bindDevice(device: SavedDevice) {
        _activeDevice.value = device
        val controller = protocolRegistry.createController(device)
        activeController = controller
        _capabilities.value = controller.capabilities

        viewModelScope.launch {
            controller.connectionState.collectLatest { status ->
                _connectionStatus.value = status
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            controller.connect()
            if (controller.capabilities.contains(DeviceCapability.REAL_APP_LAUNCH)) {
                _installedApps.value = controller.fetchInstalledApps()
            }
        }
    }

    fun sendCommand(command: RemoteCommand) {
        triggerHaptic()
        val controller = activeController ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = controller.sendCommand(command)
            when (result) {
                is CommandResult.Success -> {}
                is CommandResult.Failed -> _feedback.value = result.error
                CommandResult.UnsupportedByDevice -> _feedback.value = "Button unsupported by this TV"
                CommandResult.AuthRequired -> _feedback.value = "Action required: Confirm on TV"
            }
        }
    }

    fun launchApp(app: InstalledTvApp) {
        triggerHaptic()
        val controller = activeController ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = controller.launchApp(app.packageNameOrAppId)
            _feedback.value = if (ok) "Launching ${app.name}" else "Failed launching ${app.name}"
        }
    }

    fun injectText(text: String) {
        if (text.isBlank()) return
        sendCommand(RemoteCommand.InjectText(text))
        _inputText.value = ""
    }

    fun toggleTouchpad() {
        _isTouchpadActive.value = !_isTouchpadActive.value
    }

    fun executeMacro(macroName: String, sequence: List<RemoteCommand>) {
        triggerHaptic()
        val controller = activeController ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _feedback.value = "Running macro: $macroName..."
            sequence.forEach { cmd ->
                val res = controller.sendCommand(cmd)
                if (res is CommandResult.UnsupportedByDevice) {
                    _feedback.value = "Macro step unsupported by TV: ${cmd.javaClass.simpleName}"
                }
                delay(300)
            }
            _feedback.value = "Macro '$macroName' complete"
        }
    }

    private fun triggerHaptic() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            activeController?.disconnect()
        }
    }
}
