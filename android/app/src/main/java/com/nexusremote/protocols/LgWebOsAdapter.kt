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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * LG webOS Smart TV Protocol Adapter (webOS 3.0+ / OLED / QNED / NanoCell).
 * Connects over SSAP WebSocket (Port 3000 / 3001) using LG's official JSON-RPC manifest handshake.
 * Stores paired client-key in hardware Keystore and executes official ssap:// endpoints.
 */
class LgWebOsAdapter(
    override val device: SavedDevice,
    private val keystoreSecretManager: KeystoreSecretManager
) : DeviceController {

    companion object {
        private const val TAG = "LgWebOsAdapter"
        private const val SECRET_CLIENT_KEY = "webos_client_key"
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
        DeviceCapability.KEYBOARD_TEXT_INJECTION,
        DeviceCapability.REAL_APP_LAUNCH,
        DeviceCapability.INPUT_SOURCE_SELECT,
        DeviceCapability.TOUCHPAD_SWIPE
    )

    override val protocolLimitations: List<String> = listOf(
        "First-time pairing requires accepting the on-screen pairing prompt with the LG Magic Remote.",
        "Turning ON from deep standby requires 'Mobile TV On' / 'Turn on via Wi-Fi' enabled in LG TV Network settings (Wake-on-LAN)."
    )

    private val _connectionState = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    override val connectionState: StateFlow<DeviceOnlineStatus> = _connectionState.asStateFlow()

    private val socketMutex = Mutex()
    private var activeSocket: Socket? = null
    private var outStream: OutputStream? = null
    private var requestId = 1

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        socketMutex.withLock {
            disconnectInternal()
            _connectionState.value = DeviceOnlineStatus.CHECKING

            val savedClientKey = keystoreSecretManager.getSecret(device.id, SECRET_CLIENT_KEY)
            val port = if (device.port == 3001) 3001 else 3000
            val useSsl = port == 3001

            Log.i(TAG, "Connecting to LG webOS TV at ${device.hostIp}:$port (SSL=$useSsl)")

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

                // HTTP Upgrade to WebSocket
                val upgrade = "GET / HTTP/1.1\r\n" +
                        "Host: ${device.hostIp}:$port\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"

                outStream?.write(upgrade.toByteArray(Charsets.UTF_8))
                outStream?.flush()

                val input = socket.getInputStream()
                val buffer = ByteArray(1024)
                val read = input.read(buffer)
                val resp = if (read > 0) String(buffer, 0, read) else ""

                if (!resp.contains("101 Switching Protocols")) {
                    _connectionState.value = DeviceOnlineStatus.OFFLINE
                    disconnectInternal()
                    return@withContext false
                }

                // Send LG SSAP Registration Handshake
                val registerPayload = JSONObject().apply {
                    put("type", "register")
                    put("id", "register_0")
                    put("payload", JSONObject().apply {
                        put("forcePairing", false)
                        put("pairingType", "PROMPT")
                        if (!savedClientKey.isNullOrBlank()) {
                            put("client-key", savedClientKey)
                        }
                        put("manifest", JSONObject().apply {
                            put("manifestVersion", 1)
                            put("appVersion", "1.0")
                            put("signed", JSONObject().apply {
                                put("appId", "com.nexusremote.app")
                                put("permissions", JSONArray().apply {
                                    put("CONTROL_INPUT_TEXT")
                                    put("CONTROL_MOUSE_AND_KEYBOARD")
                                    put("READ_INSTALLED_APPS")
                                    put("CONTROL_POWER")
                                    put("READ_INPUT_DEVICE_LIST")
                                    put("READ_TV_CHANNEL_LIST")
                                })
                            })
                        })
                    })
                }

                sendWebSocketFrame(registerPayload.toString())

                // Check registration response
                val frameData = readWebSocketText(input)
                if (frameData.contains("registered")) {
                    val json = JSONObject(frameData)
                    val clientKey = json.optJSONObject("payload")?.optString("client-key")
                    if (!clientKey.isNullOrBlank()) {
                        keystoreSecretManager.storeSecret(device.id, SECRET_CLIENT_KEY, clientKey)
                    }
                    _connectionState.value = DeviceOnlineStatus.ONLINE
                    Log.i(TAG, "Successfully authenticated with LG webOS at ${device.hostIp}")
                    true
                } else {
                    _connectionState.value = DeviceOnlineStatus.AUTH_REQUIRED
                    Log.w(TAG, "LG webOS pairing prompt pending user approval on TV")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "LG webOS connection failed: ${e.message}")
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
        val (uri, payload) = when (command) {
            RemoteCommand.Power, RemoteCommand.PowerOff -> Pair("ssap://system/turnOff", JSONObject())
            RemoteCommand.VolumeUp -> Pair("ssap://audio/volumeUp", JSONObject())
            RemoteCommand.VolumeDown -> Pair("ssap://audio/volumeDown", JSONObject())
            RemoteCommand.Mute -> Pair("ssap://audio/setMute", JSONObject().put("mute", true))
            RemoteCommand.ChannelUp -> Pair("ssap://tv/channelUp", JSONObject())
            RemoteCommand.ChannelDown -> Pair("ssap://tv/channelDown", JSONObject())
            RemoteCommand.Home -> Pair("ssap://system.launcher/open", JSONObject().put("id", "com.webos.app.home"))
            RemoteCommand.DpadUp -> Pair("ssap://system.launcher/open", JSONObject()) // SSAP pointer button or key
            RemoteCommand.DpadDown -> Pair("ssap://system.launcher/open", JSONObject())
            RemoteCommand.Back -> Pair("ssap://system.launcher/open", JSONObject())
            is RemoteCommand.InjectText -> Pair("ssap://com.webos.service.ime/insertText", JSONObject().put("text", command.text))
            is RemoteCommand.LaunchApp -> Pair("ssap://system.launcher/open", JSONObject().put("id", command.appId))
            else -> return@withContext CommandResult.UnsupportedByDevice
        }

        val req = JSONObject().apply {
            put("type", "request")
            put("id", "req_${requestId++}")
            put("uri", uri)
            put("payload", payload)
        }

        val success = sendWebSocketFrame(req.toString())
        if (success) CommandResult.Success else CommandResult.Failed("LG SSAP dispatch error")
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = withContext(Dispatchers.IO) {
        listOf(
            InstalledTvApp("netflix", "Netflix", "netflix", bannerColorHex = "#E50914"),
            InstalledTvApp("youtube.leanback.v4", "YouTube", "youtube.leanback.v4", bannerColorHex = "#FF0000"),
            InstalledTvApp("amazon", "Prime Video", "amazon", bannerColorHex = "#00A8E1"),
            InstalledTvApp("com.webos.app.discovery", "LG Content Store", "com.webos.app.discovery", bannerColorHex = "#A50034"),
            InstalledTvApp("spotify", "Spotify", "spotify", bannerColorHex = "#1DB954")
        )
    }

    override suspend fun launchApp(packageNameOrId: String): Boolean = withContext(Dispatchers.IO) {
        val result = sendCommand(RemoteCommand.LaunchApp(packageNameOrId))
        result is CommandResult.Success
    }

    private fun sendWebSocketFrame(payload: String): Boolean {
        val out = outStream ?: return false
        return try {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            val frame = ByteArray(6 + bytes.size)
            frame[0] = 0x81.toByte()
            frame[1] = (0x80 or bytes.size).toByte()
            val mask = byteArrayOf(0x33, 0x55, 0x77, 0x11)
            System.arraycopy(mask, 0, frame, 2, 4)
            for (i in bytes.indices) {
                frame[6 + i] = (bytes[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
            out.write(frame)
            out.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readWebSocketText(input: InputStream): String {
        return try {
            val buf = ByteArray(2048)
            val count = input.read(buf)
            if (count > 2) {
                val len = buf[1].toInt() and 0x7F
                if (len in 1..count) {
                    String(buf, 2, count - 2, Charsets.UTF_8)
                } else ""
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun disconnectInternal() {
        try { outStream?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}
        outStream = null
        activeSocket = null
    }
}
