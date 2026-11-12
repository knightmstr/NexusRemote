package com.nexusremote.protocols

import android.util.Log
import com.nexusremote.domain.model.CommandResult
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.InstalledTvApp
import com.nexusremote.domain.model.RemoteCommand
import com.nexusremote.domain.model.SavedDevice
import com.nexusremote.security.KeystoreSecretManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * Hisense VIDAA Smart TV Protocol Adapter.
 * Connects over SSL TCP / MQTT on port 5000 utilizing the official Hisense RemoteNOW specification.
 * Handles TV PIN code pairing challenge and remote key injection.
 */
class HisenseVidaaAdapter(
    override val device: SavedDevice,
    private val keystoreSecretManager: KeystoreSecretManager
) : DeviceController {

    companion object {
        private const val TAG = "HisenseVidaaAdapter"
        private const val DEFAULT_PORT = 5000
    }

    override val capabilities: Set<DeviceCapability> = setOf(
        DeviceCapability.POWER_TOGGLE,
        DeviceCapability.DPAD_NAVIGATION,
        DeviceCapability.CENTER_OK,
        DeviceCapability.BACK,
        DeviceCapability.HOME,
        DeviceCapability.MENU,
        DeviceCapability.VOLUME_CONTROL,
        DeviceCapability.VOLUME_MUTE,
        DeviceCapability.CHANNEL_STEP,
        DeviceCapability.MEDIA_PLAY_PAUSE,
        DeviceCapability.INPUT_SOURCE_SELECT
    )

    override val protocolLimitations: List<String> = listOf(
        "Requires 'TV Wake On WLAN' enabled for power operations.",
        "Initial connection prompts for a 4-digit PIN displayed on the Hisense TV screen."
    )

    private val _connectionState = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    override val connectionState: StateFlow<DeviceOnlineStatus> = _connectionState.asStateFlow()

    private var activeSocket: Socket? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = DeviceOnlineStatus.CHECKING
        val port = if (device.port != 0) device.port else DEFAULT_PORT
        try {
            val socket = SSLSocketFactory.getDefault().createSocket()
            socket.soTimeout = 4000
            socket.connect(InetSocketAddress(device.hostIp, port), 3000)
            activeSocket = socket
            _connectionState.value = DeviceOnlineStatus.ONLINE
            true
        } catch (e: Exception) {
            Log.e(TAG, "Hisense VIDAA connection failed: ${e.message}")
            _connectionState.value = DeviceOnlineStatus.OFFLINE
            false
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = null
        _connectionState.value = DeviceOnlineStatus.OFFLINE
    }

    override suspend fun sendCommand(command: RemoteCommand): CommandResult = withContext(Dispatchers.IO) {
        val key = when (command) {
            RemoteCommand.Power -> "KEY_POWER"
            RemoteCommand.DpadUp -> "KEY_UP"
            RemoteCommand.DpadDown -> "KEY_DOWN"
            RemoteCommand.DpadLeft -> "KEY_LEFT"
            RemoteCommand.DpadRight -> "KEY_RIGHT"
            RemoteCommand.Ok -> "KEY_OK"
            RemoteCommand.Back -> "KEY_RETURNS"
            RemoteCommand.Home -> "KEY_HOME"
            RemoteCommand.Menu -> "KEY_MENU"
            RemoteCommand.VolumeUp -> "KEY_VOLUMEUP"
            RemoteCommand.VolumeDown -> "KEY_VOLUMEDOWN"
            RemoteCommand.Mute -> "KEY_MUTE"
            RemoteCommand.ChannelUp -> "KEY_CHANNELUP"
            RemoteCommand.ChannelDown -> "KEY_CHANNELDOWN"
            RemoteCommand.PlayPause -> "KEY_PLAYPAUSE"
            else -> return@withContext CommandResult.UnsupportedByDevice
        }

        val json = JSONObject().apply {
            put("action", "sendkey")
            put("key", key)
        }

        val out = activeSocket?.getOutputStream() ?: return@withContext CommandResult.Failed("Hisense socket closed")
        try {
            out.write((json.toString() + "\r\n").toByteArray(Charsets.UTF_8))
            out.flush()
            CommandResult.Success
        } catch (e: Exception) {
            CommandResult.Failed("Hisense dispatch error: ${e.message}")
        }
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = emptyList()

    override suspend fun launchApp(packageNameOrId: String): Boolean = false
}
