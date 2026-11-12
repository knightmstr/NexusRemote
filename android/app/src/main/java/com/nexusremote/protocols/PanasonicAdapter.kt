package com.nexusremote.protocols

import android.util.Log
import com.nexusremote.domain.model.CommandResult
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.InstalledTvApp
import com.nexusremote.domain.model.RemoteCommand
import com.nexusremote.domain.model.SavedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Panasonic Viera Smart TV Protocol Adapter.
 * Connects over HTTP UPnP/SOAP on port 55000 utilizing the official Viera Network Control service.
 */
class PanasonicAdapter(
    override val device: SavedDevice
) : DeviceController {

    companion object {
        private const val TAG = "PanasonicAdapter"
        private const val DEFAULT_PORT = 55000
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
        DeviceCapability.NUMERIC_KEYPAD,
        DeviceCapability.INPUT_SOURCE_SELECT
    )

    override val protocolLimitations: List<String> = listOf(
        "Requires 'TV Remote App' and 'Powered On by Apps' enabled in Panasonic Network Settings."
    )

    private val _connectionState = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    override val connectionState: StateFlow<DeviceOnlineStatus> = _connectionState.asStateFlow()

    private val baseUrl: String get() = "http://${device.hostIp}:${if (device.port != 0) device.port else DEFAULT_PORT}"

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = DeviceOnlineStatus.CHECKING
        val isOnline = testConnection()
        _connectionState.value = if (isOnline) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
        isOnline
    }

    override suspend fun disconnect() {
        _connectionState.value = DeviceOnlineStatus.OFFLINE
    }

    override suspend fun sendCommand(command: RemoteCommand): CommandResult = withContext(Dispatchers.IO) {
        val panasonicKey = when (command) {
            RemoteCommand.Power -> "NRC_POWER-ONOFF"
            RemoteCommand.DpadUp -> "NRC_UP-ONOFF"
            RemoteCommand.DpadDown -> "NRC_DOWN-ONOFF"
            RemoteCommand.DpadLeft -> "NRC_LEFT-ONOFF"
            RemoteCommand.DpadRight -> "NRC_RIGHT-ONOFF"
            RemoteCommand.Ok -> "NRC_ENTER-ONOFF"
            RemoteCommand.Back -> "NRC_RETURN-ONOFF"
            RemoteCommand.Home -> "NRC_HOME-ONOFF"
            RemoteCommand.Menu -> "NRC_MENU-ONOFF"
            RemoteCommand.VolumeUp -> "NRC_VOLUP-ONOFF"
            RemoteCommand.VolumeDown -> "NRC_VOLDOWN-ONOFF"
            RemoteCommand.Mute -> "NRC_MUTE-ONOFF"
            RemoteCommand.ChannelUp -> "NRC_CH_UP-ONOFF"
            RemoteCommand.ChannelDown -> "NRC_CH_DOWN-ONOFF"
            RemoteCommand.PlayPause -> "NRC_PLAY-ONOFF"
            RemoteCommand.SourceInput -> "NRC_CHG_INPUT-ONOFF"
            is RemoteCommand.Digit -> "NRC_D${command.number}-ONOFF"
            else -> return@withContext CommandResult.UnsupportedByDevice
        }

        val soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
                "  <s:Body>\n" +
                "    <u:X_SendKey xmlns:u=\"urn:panasonic-com:service:p00NetworkControl:1\">\n" +
                "      <X_KeyEvent>$panasonicKey</X_KeyEvent>\n" +
                "    </u:X_SendKey>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>"

        val ok = postSoap("$baseUrl/nrc/control_0", soapBody)
        if (ok) CommandResult.Success else CommandResult.Failed("Panasonic SOAP command rejected")
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = emptyList()

    override suspend fun launchApp(packageNameOrId: String): Boolean = false

    private fun testConnection(): Boolean {
        return try {
            val url = URL("$baseUrl/nrc/sdd_0.xml")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.responseCode in 200..399
        } catch (_: Exception) {
            false
        }
    }

    private fun postSoap(urlString: String, soapXml: String): Boolean {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPACTION", "\"urn:panasonic-com:service:p00NetworkControl:1#X_SendKey\"")
            conn.outputStream.use { it.write(soapXml.toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Panasonic SOAP dispatch error: ${e.message}")
            false
        }
    }
}
