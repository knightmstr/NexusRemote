package com.nexusremote.protocols

import android.util.Log
import com.nexusremote.domain.model.CommandResult
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.InstalledTvApp
import com.nexusremote.domain.model.RemoteCommand
import com.nexusremote.domain.model.SavedDevice
import com.nexusremote.security.KeystoreSecretManager
import com.nexusremote.security.TofuTrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket

/**
 * Hardened Google Android TV Remote Protocol v2 Adapter (TLS Port 6467).
 * OWASP MASVS Controls:
 * - Uses Trust-On-First-Use (TOFU) TLS certificate pinning to prevent MitM attacks on local network TLS sockets.
 * - Communicates with Chromecast with Google TV, Sony Google TVs, and certified Android TV 10+ devices.
 */
class AndroidTvRemoteAdapter(
    override val device: SavedDevice,
    private val keystoreSecretManager: KeystoreSecretManager
) : DeviceController {

    companion object {
        private const val TAG = "AndroidTvRemoteAdapter"
        private const val DEFAULT_PORT = 6467
    }

    override val capabilities: Set<DeviceCapability> = setOf(
        DeviceCapability.POWER_TOGGLE,
        DeviceCapability.DPAD_NAVIGATION,
        DeviceCapability.CENTER_OK,
        DeviceCapability.BACK,
        DeviceCapability.HOME,
        DeviceCapability.VOLUME_CONTROL,
        DeviceCapability.VOLUME_MUTE,
        DeviceCapability.MEDIA_PLAY_PAUSE,
        DeviceCapability.KEYBOARD_TEXT_INJECTION
    )

    override val protocolLimitations: List<String> = listOf(
        "Initial pairing requires entering the 6-character PIN displayed on the Google TV screen.",
        "Requires active Wi-Fi connection on the same local subnet as the TV."
    )

    private val _connectionState = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    override val connectionState: StateFlow<DeviceOnlineStatus> = _connectionState.asStateFlow()

    private var sslSocket: SSLSocket? = null
    private var outStream: OutputStream? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = DeviceOnlineStatus.CHECKING
        val port = if (device.port != 0) device.port else DEFAULT_PORT
        try {
            // OWASP MASVS: TOFU TLS Certificate Pinning SSL Context
            val sslContext = TofuTrustManager.createSslContext(device.id, keystoreSecretManager)
            val socket = sslContext.socketFactory.createSocket() as SSLSocket
            socket.soTimeout = 4000
            socket.connect(InetSocketAddress(device.hostIp, port), 3500)
            socket.startHandshake()

            sslSocket = socket
            outStream = socket.getOutputStream()
            _connectionState.value = DeviceOnlineStatus.ONLINE
            true
        } catch (e: Exception) {
            Log.e(TAG, "Android TV Remote TLS connection failed: ${e.message}")
            _connectionState.value = DeviceOnlineStatus.OFFLINE
            false
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try { outStream?.close() } catch (_: Exception) {}
        try { sslSocket?.close() } catch (_: Exception) {}
        sslSocket = null
        outStream = null
        _connectionState.value = DeviceOnlineStatus.OFFLINE
    }

    override suspend fun sendCommand(command: RemoteCommand): CommandResult = withContext(Dispatchers.IO) {
        val keycode = when (command) {
            RemoteCommand.Power -> 26
            RemoteCommand.DpadUp -> 19
            RemoteCommand.DpadDown -> 20
            RemoteCommand.DpadLeft -> 21
            RemoteCommand.DpadRight -> 22
            RemoteCommand.Ok -> 23
            RemoteCommand.Back -> 4
            RemoteCommand.Home -> 3
            RemoteCommand.VolumeUp -> 24
            RemoteCommand.VolumeDown -> 25
            RemoteCommand.Mute -> 164
            RemoteCommand.PlayPause -> 85
            else -> return@withContext CommandResult.UnsupportedByDevice
        }

        // Send Android TV Remote Protocol v2 keypacket
        val packet = byteArrayOf(
            0x12, 0x04,
            0x08, keycode.toByte(),
            0x10, 0x01 // Key down
        )

        return@withContext try {
            outStream?.write(packet)
            outStream?.flush()
            CommandResult.Success
        } catch (e: Exception) {
            CommandResult.Failed("Android TV Remote dispatch failed: ${e.message}")
        }
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = emptyList()
    override suspend fun launchApp(packageNameOrId: String): Boolean = false
}
