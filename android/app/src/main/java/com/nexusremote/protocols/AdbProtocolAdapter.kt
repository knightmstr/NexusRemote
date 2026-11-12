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
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real ADB over TCP/IP (Port 5555) Protocol Adapter for Amazon Fire TV & Android TV.
 * Implements binary ADB framing, RSA-2048 Keystore signing for A_AUTH handshake,
 * stream management for 'input keyevent', 'input text', and dynamic package parsing.
 */
class AdbProtocolAdapter(
    override val device: SavedDevice,
    private val keystoreSecretManager: KeystoreSecretManager
) : DeviceController {

    companion object {
        private const val TAG = "AdbProtocolAdapter"
        private const val ADB_VERSION = 0x01000000
        private const val MAX_ADB_DATA = 4096
        private const val SOCKET_TIMEOUT_MS = 6000

        // ADB Protocol Command Identifiers
        private const val A_SYNC = 0x434e5953
        private const val A_CNXN = 0x4e584e43
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257
        private const val A_AUTH = 0x48545541

        // A_AUTH Sub-types
        private const val ADB_AUTH_TOKEN = 1
        private const val ADB_AUTH_SIGNATURE = 2
        private const val ADB_AUTH_RSAPUBLICKEY = 3
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
        DeviceCapability.MEDIA_PLAY_PAUSE,
        DeviceCapability.MEDIA_SEEK,
        DeviceCapability.KEYBOARD_TEXT_INJECTION,
        DeviceCapability.REAL_APP_LAUNCH
    )

    override val protocolLimitations: List<String> = listOf(
        "Requires 'ADB Debugging' enabled in TV Developer Options.",
        "Initial pairing requires RSA key authorization confirmation on the TV display.",
        "Deep standby power-on is not supported over TCP/IP without Wake-on-LAN."
    )

    private val _connectionState = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    override val connectionState: StateFlow<DeviceOnlineStatus> = _connectionState.asStateFlow()

    private val socketMutex = Mutex()
    private var activeSocket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var localStreamId = 1

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        socketMutex.withLock {
            disconnectInternal()
            _connectionState.value = DeviceOnlineStatus.CHECKING
            Log.i(TAG, "Connecting to Fire TV / Android TV ADB at ${device.hostIp}:${device.port}")

            try {
                val socket = Socket()
                socket.soTimeout = SOCKET_TIMEOUT_MS
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(device.hostIp, device.port), 4000)

                activeSocket = socket
                outputStream = socket.getOutputStream()
                inputStream = socket.getInputStream()

                // Execute ADB Handshake with RSA Keystore signature
                val authenticated = performAdbHandshake(socket)
                if (authenticated) {
                    _connectionState.value = DeviceOnlineStatus.ONLINE
                    Log.i(TAG, "Successfully authenticated ADB session with ${device.hostIp}")
                    true
                } else {
                    _connectionState.value = DeviceOnlineStatus.AUTH_REQUIRED
                    Log.w(TAG, "ADB Authentication required: Prompt displayed on TV screen")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "ADB connection to ${device.hostIp} failed: ${e.message}", e)
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
        if (_connectionState.value != DeviceOnlineStatus.ONLINE) {
            val reconnected = connect()
            if (!reconnected) return@withContext CommandResult.Failed("Device offline or authentication pending")
        }

        val adbCmd = when (command) {
            RemoteCommand.Power -> "input keyevent 26"
            RemoteCommand.PowerOn -> "input keyevent 224"
            RemoteCommand.PowerOff -> "input keyevent 223"
            RemoteCommand.DpadUp -> "input keyevent 19"
            RemoteCommand.DpadDown -> "input keyevent 20"
            RemoteCommand.DpadLeft -> "input keyevent 21"
            RemoteCommand.DpadRight -> "input keyevent 22"
            RemoteCommand.Ok -> "input keyevent 23"
            RemoteCommand.Back -> "input keyevent 4"
            RemoteCommand.Home -> "input keyevent 3"
            RemoteCommand.Menu -> "input keyevent 82"
            RemoteCommand.Search -> "input keyevent 84"
            RemoteCommand.VolumeUp -> "input keyevent 24"
            RemoteCommand.VolumeDown -> "input keyevent 25"
            RemoteCommand.Mute -> "input keyevent 164"
            RemoteCommand.PlayPause -> "input keyevent 85"
            RemoteCommand.FastForward -> "input keyevent 90"
            RemoteCommand.Rewind -> "input keyevent 89"
            RemoteCommand.Backspace -> "input keyevent 67"
            RemoteCommand.Enter -> "input keyevent 66"
            is RemoteCommand.Digit -> "input keyevent ${7 + command.number}"
            is RemoteCommand.InjectText -> {
                val sanitized = sanitizeAdbText(command.text)
                "input text \"$sanitized\""
            }
            is RemoteCommand.LaunchApp -> "monkey -p ${command.appId} -c android.intent.category.LAUNCHER 1"
            else -> return@withContext CommandResult.UnsupportedByDevice
        }

        val output = executeShellCommand(adbCmd)
        if (output != null) CommandResult.Success else CommandResult.Failed("ADB command execution timed out", canRetry = true)
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = withContext(Dispatchers.IO) {
        val raw = executeShellCommand("pm list packages -3") ?: return@withContext emptyList()
        val packages = raw.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        packages.map { pkg ->
            InstalledTvApp(
                id = pkg,
                name = formatDisplayName(pkg),
                packageNameOrAppId = pkg,
                bannerColorHex = getPackageColor(pkg)
            )
        }
    }

    override suspend fun launchApp(packageNameOrId: String): Boolean = withContext(Dispatchers.IO) {
        val result = sendCommand(RemoteCommand.LaunchApp(packageNameOrId))
        result is CommandResult.Success
    }

    private fun performAdbHandshake(socket: Socket): Boolean {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()

        // 1. Send A_CNXN
        val banner = "host::NexusRemote\u0000".toByteArray(Charsets.UTF_8)
        val cnxnPacket = buildPacket(A_CNXN, ADB_VERSION, MAX_ADB_DATA, banner)
        out.write(cnxnPacket)
        out.flush()

        // 2. Read Response header
        val header = ByteArray(24)
        var bytesRead = input.read(header)
        if (bytesRead < 24) return false

        var buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        var cmd = buffer.int
        var arg0 = buffer.int
        var arg1 = buffer.int
        var dataLen = buffer.int

        if (cmd == A_CNXN) {
            // Already authorized
            readPayload(input, dataLen)
            return true
        }

        if (cmd == A_AUTH && arg0 == ADB_AUTH_TOKEN) {
            // Read authentication token challenge from TV
            val token = readPayload(input, dataLen)

            // Try hardware RSA signature first
            val signature = keystoreSecretManager.signAdbChallenge(token)
            if (signature != null) {
                val sigPacket = buildPacket(A_AUTH, ADB_AUTH_SIGNATURE, 0, signature)
                out.write(sigPacket)
                out.flush()

                bytesRead = input.read(header)
                if (bytesRead >= 24) {
                    buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    cmd = buffer.int
                    dataLen = buffer.int
                    if (cmd == A_CNXN) {
                        readPayload(input, dataLen)
                        return true
                    }
                }
            }

            // Signature failed or TV doesn't have our key yet -> Send RSA Public Key
            val pubKey = keystoreSecretManager.getAdbPublicKeyBytes()
            if (pubKey != null) {
                val base64PubKey = Base64.encodeToString(pubKey, Base64.NO_WRAP) + " nexus@nexusremote\u0000"
                val pubKeyBytes = base64PubKey.toByteArray(Charsets.UTF_8)
                val pubKeyPacket = buildPacket(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pubKeyBytes)
                out.write(pubKeyPacket)
                out.flush()

                // Wait for TV prompt acceptance
                bytesRead = input.read(header)
                if (bytesRead >= 24) {
                    buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    cmd = buffer.int
                    dataLen = buffer.int
                    if (cmd == A_CNXN) {
                        readPayload(input, dataLen)
                        return true
                    }
                }
            }
        }

        return false
    }

    private suspend fun executeShellCommand(cmd: String): String? = withContext(Dispatchers.IO) {
        socketMutex.withLock {
            val socket = activeSocket ?: return@withContext null
            val out = outputStream ?: return@withContext null
            val input = inputStream ?: return@withContext null

            try {
                val myId = localStreamId++
                val payload = "shell:$cmd\u0000".toByteArray(Charsets.UTF_8)
                val openPacket = buildPacket(A_OPEN, myId, 0, payload)
                out.write(openPacket)
                out.flush()

                val header = ByteArray(24)
                val readBytes = input.read(header)
                if (readBytes < 24) return@withContext null

                val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val responseCmd = buffer.int
                buffer.int
                buffer.int
                val dataLen = buffer.int

                if (responseCmd == A_OKAY || responseCmd == A_WRTE) {
                    val data = readPayload(input, dataLen)
                    return@withContext String(data, Charsets.UTF_8)
                }
                return@withContext ""
            } catch (e: Exception) {
                Log.e(TAG, "Shell command '$cmd' failed: ${e.message}")
                return@withContext null
            }
        }
    }

    private fun readPayload(input: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(data, offset, length - offset)
            if (count < 0) break
            offset += count
        }
        return data
    }

    private fun buildPacket(command: Int, arg0: Int, arg1: Int, data: ByteArray): ByteArray {
        var checksum = 0
        for (b in data) checksum += (b.toInt() and 0xFF)
        val magic = command xor -0x1

        val buffer = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(data.size)
        buffer.putInt(checksum)
        buffer.putInt(magic)
        if (data.isNotEmpty()) buffer.put(data)
        return buffer.array()
    }

    private fun sanitizeAdbText(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace(" ", "%s")
            .replace("&", "\\&")
            .replace(";", "\\;")
            .replace("$", "\\$")
    }

    private fun formatDisplayName(pkg: String): String {
        return when {
            pkg.contains("netflix") -> "Netflix"
            pkg.contains("amazonvideo") || pkg.contains("prime") -> "Prime Video"
            pkg.contains("youtube") -> "YouTube"
            pkg.contains("disney") -> "Disney+"
            pkg.contains("plex") -> "Plex"
            pkg.contains("spotify") -> "Spotify"
            pkg.contains("kodi") -> "Kodi"
            else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun getPackageColor(pkg: String): String {
        return when {
            pkg.contains("netflix") -> "#E50914"
            pkg.contains("amazon") || pkg.contains("prime") -> "#00A8E1"
            pkg.contains("youtube") -> "#FF0000"
            pkg.contains("disney") -> "#113CCF"
            pkg.contains("plex") -> "#E5A00D"
            pkg.contains("spotify") -> "#1DB954"
            else -> "#2563EB"
        }
    }

    private fun disconnectInternal() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}
        outputStream = null
        inputStream = null
        activeSocket = null
    }
}
