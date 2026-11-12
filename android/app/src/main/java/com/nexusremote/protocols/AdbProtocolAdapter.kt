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
 * Hardened ADB over TCP/IP (Port 5555) Protocol Adapter for Amazon Fire TV & Android TV.
 * OWASP MASVS Security Hardening Controls:
 * - Enforces strict allowlist regex validation (^[a-zA-Z0-9._]+$) on package names passed to launcher.
 * - Restricts input keyevents strictly to validated standard Android KeyEvent.KEYCODE_* integers.
 * - Authenticates A_AUTH token challenges using hardware-backed NONEwithRSA signatures and 524-byte RSAPublicKey structs.
 * - Removes arbitrary shell command execution capabilities.
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

        // Strict Allowlist Pattern for Package Names (OWASP MASVS Input Validation)
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9._]+$")

        // Allowed Android KeyEvent keycodes
        private val ALLOWED_KEYCODES = setOf(
            3,   // KEYCODE_HOME
            4,   // KEYCODE_BACK
            7, 8, 9, 10, 11, 12, 13, 14, 15, 16, // KEYCODE_0 .. KEYCODE_9
            19,  // KEYCODE_DPAD_UP
            20,  // KEYCODE_DPAD_DOWN
            21,  // KEYCODE_DPAD_LEFT
            22,  // KEYCODE_DPAD_RIGHT
            23,  // KEYCODE_DPAD_CENTER
            24,  // KEYCODE_VOLUME_UP
            25,  // KEYCODE_VOLUME_DOWN
            26,  // KEYCODE_POWER
            66,  // KEYCODE_ENTER
            67,  // KEYCODE_DEL / BACKSPACE
            82,  // KEYCODE_MENU
            84,  // KEYCODE_SEARCH
            85,  // KEYCODE_MEDIA_PLAY_PAUSE
            89,  // KEYCODE_MEDIA_REWIND
            90,  // KEYCODE_MEDIA_FAST_FORWARD
            164, // KEYCODE_VOLUME_MUTE
            223, // KEYCODE_SLEEP
            224  // KEYCODE_WAKEUP
        )

        // ADB Protocol Command Identifiers
        private const val A_CNXN = 0x4e584e43
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
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

                // Execute ADB Handshake with hardware RSA Keystore signature
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

        return@withContext when (command) {
            RemoteCommand.Power -> sendKeyevent(26)
            RemoteCommand.PowerOn -> sendKeyevent(224)
            RemoteCommand.PowerOff -> sendKeyevent(223)
            RemoteCommand.DpadUp -> sendKeyevent(19)
            RemoteCommand.DpadDown -> sendKeyevent(20)
            RemoteCommand.DpadLeft -> sendKeyevent(21)
            RemoteCommand.DpadRight -> sendKeyevent(22)
            RemoteCommand.Ok -> sendKeyevent(23)
            RemoteCommand.Back -> sendKeyevent(4)
            RemoteCommand.Home -> sendKeyevent(3)
            RemoteCommand.Menu -> sendKeyevent(82)
            RemoteCommand.Search -> sendKeyevent(84)
            RemoteCommand.VolumeUp -> sendKeyevent(24)
            RemoteCommand.VolumeDown -> sendKeyevent(25)
            RemoteCommand.Mute -> sendKeyevent(164)
            RemoteCommand.PlayPause -> sendKeyevent(85)
            RemoteCommand.FastForward -> sendKeyevent(90)
            RemoteCommand.Rewind -> sendKeyevent(89)
            RemoteCommand.Backspace -> sendKeyevent(67)
            RemoteCommand.Enter -> sendKeyevent(66)
            is RemoteCommand.Digit -> sendKeyevent(7 + command.number)
            is RemoteCommand.InjectText -> injectTextInternal(command.text)
            is RemoteCommand.LaunchApp -> launchAppInternal(command.appId)
            else -> CommandResult.UnsupportedByDevice
        }
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = withContext(Dispatchers.IO) {
        val raw = executeValidatedShellCommand("pm list packages -3") ?: return@withContext emptyList()
        val packages = raw.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { PACKAGE_NAME_REGEX.matches(it) } // Enforce allowlist validation
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

    private suspend fun sendKeyevent(keycode: Int): CommandResult {
        // OWASP MASVS: Validate keycode strictly against allowlisted Android keycodes
        if (!ALLOWED_KEYCODES.contains(keycode)) {
            Log.e(TAG, "Input Validation Error: Invalid keycode $keycode rejected")
            return CommandResult.Failed("Invalid keycode rejected by security policy")
        }
        val output = executeValidatedShellCommand("input keyevent $keycode")
        return if (output != null) CommandResult.Success else CommandResult.Failed("ADB keyevent timed out")
    }

    private suspend fun injectTextInternal(text: String): CommandResult {
        val sanitized = sanitizeAdbText(text)
        val output = executeValidatedShellCommand("input text \"$sanitized\"")
        return if (output != null) CommandResult.Success else CommandResult.Failed("ADB text injection failed")
    }

    private suspend fun launchAppInternal(packageName: String): CommandResult {
        // OWASP MASVS: Validate package name strictly against allowlist regex
        if (!PACKAGE_NAME_REGEX.matches(packageName)) {
            Log.e(TAG, "Security Validation Failure: Invalid package name '$packageName'")
            return CommandResult.Failed("Invalid package name rejected by security policy")
        }
        val output = executeValidatedShellCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        return if (output != null) CommandResult.Success else CommandResult.Failed("App launch command failed")
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
        buffer.int
        var dataLen = buffer.int

        if (cmd == A_CNXN) {
            readPayload(input, dataLen)
            return true
        }

        if (cmd == A_AUTH && arg0 == ADB_AUTH_TOKEN) {
            // Read 20-byte authentication token challenge from TV
            val token = readPayload(input, dataLen)

            // Try hardware RSA signature via Android Keystore
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

            // Signature failed or TV requires key registration -> Send 524-byte RSAPublicKey Struct
            val pubKeyBytes = keystoreSecretManager.getAdbPublicKeyBytes()
            if (pubKeyBytes != null) {
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

    private suspend fun executeValidatedShellCommand(cmd: String): String? = withContext(Dispatchers.IO) {
        socketMutex.withLock {
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
            .replace("`", "\\`")
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
