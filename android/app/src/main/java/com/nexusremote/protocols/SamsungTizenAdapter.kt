package com.nexusremote.protocols

import android.util.Base64
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.SSLSocketFactory

/**
 * Samsung Tizen Smart TV Protocol Adapter.
 * Connects via WebSocket on port 8001 / 8002 using Samsung's official ms.remote.control channel.
 * Manages token authentication with hardware Keystore caching and on-screen TV approval.
 */
class SamsungTizenAdapter(
    override val device: SavedDevice,
    private val keystoreSecretManager: KeystoreSecretManager
) : DeviceController {

    companion object {
        private const val TAG = "SamsungTizenAdapter"
        private const val SECRET_TOKEN_KEY = "tizen_auth_token"
    }

    override val capabilities: Set<DeviceCapability> = setOf(
        DeviceCapability.POWER_TOGGLE,
        DeviceCapability.POWER_OFF,
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
        "First-time pairing requires clicking 'Allow' on the TV popup.",
        "Turning ON from deep standby requires Wake-on-LAN over Ethernet or Wi-Fi (WoWLAN).",
        "Samsung Tizen restricts direct keyboard text injection; D-Pad navigation is required for on-screen typing."
    )

    private val _connectionState = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    override val connectionState: StateFlow<DeviceOnlineStatus> = _connectionState.asStateFlow()

    private val socketMutex = Mutex()
    private var activeSocket: Socket? = null
    private var outStream: OutputStream? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        socketMutex.withLock {
            disconnectInternal()
            _connectionState.value = DeviceOnlineStatus.CHECKING

            val savedToken = keystoreSecretManager.getSecret(device.id, SECRET_TOKEN_KEY)
            val appNameEncoded = Base64.encodeToString("NexusRemote".toByteArray(), Base64.NO_WRAP)
            val port = if (device.port == 8002 || device.port == 0) 8002 else 8001
            val useSsl = port == 8002

            Log.i(TAG, "Connecting to Samsung Tizen TV at ${device.hostIp}:$port (SSL=$useSsl)")

            try {
                val socket = if (useSsl) {
                    SSLSocketFactory.getDefault().createSocket()
                } else {
                    Socket()
                }
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(device.hostIp, port), 3500)

                activeSocket = socket
                outStream = socket.getOutputStream()

                // Perform WebSocket HTTP Upgrade Handshake
                val path = "/api/v2/channels/samsung.remote.control?name=$appNameEncoded" +
                        if (!savedToken.isNullOrBlank()) "&token=$savedToken" else ""

                val upgradeRequest = "GET $path HTTP/1.1\r\n" +
                        "Host: ${device.hostIp}:$port\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"

                outStream?.write(upgradeRequest.toByteArray(Charsets.UTF_8))
                outStream?.flush()

                val reader = socket.getInputStream()
                val responseBuffer = ByteArray(1024)
                val read = reader.read(responseBuffer)
                val responseStr = if (read > 0) String(responseBuffer, 0, read) else ""

                if (responseStr.contains("101 Switching Protocols")) {
                    _connectionState.value = DeviceOnlineStatus.ONLINE
                    Log.i(TAG, "Samsung Tizen WebSocket session established with ${device.hostIp}")
                    true
                } else {
                    _connectionState.value = DeviceOnlineStatus.AUTH_REQUIRED
                    Log.w(TAG, "Samsung TV requires authorization on screen")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Samsung Tizen connection failed: ${e.message}")
                _connectionState.value = DeviceOnlineStatus.OFFLINE
                disconnectInternal()
                false
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        socketMutex.withLock {
            disconnectInternal()
            _connectionState.value = DeviceOnlineStatus.OFFLINE
        }
    }

    override suspend fun sendCommand(command: RemoteCommand): CommandResult = withContext(Dispatchers.IO) {
        val samsungKey = when (command) {
            RemoteCommand.Power, RemoteCommand.PowerOff -> "KEY_POWER"
            RemoteCommand.DpadUp -> "KEY_UP"
            RemoteCommand.DpadDown -> "KEY_DOWN"
            RemoteCommand.DpadLeft -> "KEY_LEFT"
            RemoteCommand.DpadRight -> "KEY_RIGHT"
            RemoteCommand.Ok -> "KEY_ENTER"
            RemoteCommand.Back -> "KEY_RETURN"
            RemoteCommand.Home -> "KEY_HOME"
            RemoteCommand.Menu -> "KEY_MENU"
            RemoteCommand.VolumeUp -> "KEY_VOLUP"
            RemoteCommand.VolumeDown -> "KEY_VOLDOWN"
            RemoteCommand.Mute -> "KEY_MUTE"
            RemoteCommand.ChannelUp -> "KEY_CHUP"
            RemoteCommand.ChannelDown -> "KEY_CHDOWN"
            RemoteCommand.PlayPause -> "KEY_PLAY"
            RemoteCommand.SourceInput -> "KEY_SOURCE"
            is RemoteCommand.Digit -> "KEY_${command.number}"
            else -> return@withContext CommandResult.UnsupportedByDevice
        }

        val json = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", samsungKey)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }

        val success = sendWebSocketFrame(json.toString())
        if (success) CommandResult.Success else CommandResult.Failed("Samsung WebSocket dispatch error")
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = withContext(Dispatchers.IO) {
        listOf(
            InstalledTvApp("3201412000512", "Netflix", "3201412000512", bannerColorHex = "#E50914"),
            InstalledTvApp("11101200001", "YouTube", "11101200001", bannerColorHex = "#FF0000"),
            InstalledTvApp("3201512006785", "Prime Video", "3201512006785", bannerColorHex = "#00A8E1"),
            InstalledTvApp("3201608010191", "Spotify", "3201608010191", bannerColorHex = "#1DB954")
        )
    }

    override suspend fun launchApp(packageNameOrId: String): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("method", "ms.channel.emit")
            put("params", JSONObject().apply {
                put("event", "ed.installedApp.get")
                put("to", "host")
                put("data", JSONObject().apply { put("action_type", "DEEP_LINKING"); put("appId", packageNameOrId) })
            })
        }
        sendWebSocketFrame(json.toString())
    }

    private fun sendWebSocketFrame(payload: String): Boolean {
        val out = outStream ?: return false
        return try {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            val frame = ByteArray(6 + bytes.size)
            frame[0] = 0x81.toByte() // FIN + Text frame
            frame[1] = (0x80 or bytes.size).toByte() // Masked
            val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
            System.arraycopy(mask, 0, frame, 2, 4)
            for (i in bytes.indices) {
                frame[6 + i] = (bytes[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
            out.write(frame)
            out.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending WebSocket frame: ${e.message}")
            false
        }
    }

    private fun disconnectInternal() {
        try { outStream?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}
        outStream = null
        activeSocket = null
    }
}
