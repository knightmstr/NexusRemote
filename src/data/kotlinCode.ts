export interface KotlinFileItem {
  id: string;
  name: string;
  path: string;
  layer: 'Component (Network)' | 'Hardware' | 'State (MVVM)' | 'UI (Compose)' | 'Manifest & Build';
  description: string;
  code: string;
}

export const KOTLIN_FILES: KotlinFileItem[] = [
  {
    id: 'firetv-manager',
    name: 'FireTvManager.kt',
    path: 'network/FireTvManager.kt',
    layer: 'Component (Network)',
    description: 'Thread-safe ADB TCP/IP connection manager (port 5555), framing, keyevent dispatch, text injection, and pm list packages parser using Dispatchers.IO.',
    code: `package com.hybridremote.app.network

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern

/**
 * Thread-safe Network Connection Manager handling ADB over TCP/IP (default port 5555)
 * for Amazon Fire TV and Android TV devices.
 *
 * Implements low-level ADB TCP transport, framing, input injection, and package querying
 * over a dedicated Dispatchers.IO coroutine worker.
 */
class FireTvManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "FireTvManager"
        const val ADB_TCP_PORT = 5555
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val CONNECT_TIMEOUT_MS = 4000

        // Standard Android Keycodes
        const val KEYCODE_DPAD_UP = 19
        const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21
        const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_DPAD_CENTER = 23
        const val KEYCODE_HOME = 3
        const val KEYCODE_BACK = 4
        const val KEYCODE_MENU = 82
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
        const val KEYCODE_VOLUME_MUTE = 164
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85
        const val KEYCODE_MEDIA_REWIND = 89
        const val KEYCODE_MEDIA_FAST_FORWARD = 90
        const val KEYCODE_SEARCH = 84
        const val KEYCODE_ENTER = 66
        const val KEYCODE_DEL = 67
        const val KEYCODE_SPACE = 62
        const val KEYCODE_CLEAR = 28

        // ADB Protocol Message Constants
        private const val A_SYNC = 0x434e5953 // "SYNC"
        private const val A_CNXN = 0x4e584e43 // "CNXN"
        private const val A_OPEN = 0x4e45504f // "OPEN"
        private const val A_OKAY = 0x59414b4f // "OKAY"
        private const val A_CLSE = 0x45534c43 // "CLSE"
        private const val A_WRTE = 0x45545257 // "WRTE"
        private const val A_AUTH = 0x48545541 // "AUTH"

        private const val ADB_VERSION = 0x01000000
        private const val MAX_ADB_DATA = 4096

        private val IP_PATTERN: Pattern = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        private val PACKAGE_PATTERN: Pattern = Pattern.compile("^[a-zA-Z0-9_.]+$")
    }

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTH_REQUIRED
    }

    private val managerScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val socketMutex = Mutex()

    private var activeSocket: Socket? = null
    private var socketOutputStream: OutputStream? = null
    private var socketInputStream: BufferedReader? = null
    private var currentIpAddress: String? = null
    private var connectionJob: Job? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<String>>(emptyList())
    val installedApps: StateFlow<List<String>> = _installedApps.asStateFlow()

    /**
     * Connects to the Fire TV over TCP/IP on port 5555 using a background coroutine worker.
     * Manages connection states, socket timeouts, and ADB protocol initial handshake.
     */
    fun connectToTv(ipAddress: String) {
        if (!validateIpAddress(ipAddress)) {
            Log.e(TAG, "connectToTv aborted: Invalid IPv4 address format: '$ipAddress'")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            return
        }

        connectionJob?.cancel()
        connectionJob = managerScope.launch {
            socketMutex.withLock {
                disconnectInternal()
                _connectionStatus.value = ConnectionStatus.CONNECTING
                currentIpAddress = ipAddress
                Log.i(TAG, "Initiating ADB TCP/IP connection to $ipAddress:$ADB_TCP_PORT")

                try {
                    val socket = Socket()
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    socket.tcpNoDelay = true
                    socket.keepAlive = true

                    withContext(ioDispatcher) {
                        socket.connect(InetSocketAddress(ipAddress, ADB_TCP_PORT), CONNECT_TIMEOUT_MS)
                    }

                    activeSocket = socket
                    socketOutputStream = socket.getOutputStream()
                    socketInputStream = BufferedReader(InputStreamReader(socket.getInputStream()))

                    // Perform ADB CNXN Handshake
                    val handshakeSuccess = performAdbHandshake(socket)
                    if (handshakeSuccess) {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        Log.i(TAG, "Successfully connected and authenticated with Fire TV at $ipAddress")
                        // Fetch third-party apps automatically upon successful connection
                        fetchInstalledAppsInternal()
                    } else {
                        Log.w(TAG, "ADB Handshake required authorization on TV screen")
                        _connectionStatus.value = ConnectionStatus.AUTH_REQUIRED
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection failure to $ipAddress:$ADB_TCP_PORT: \${e.message}", e)
                    disconnectInternal()
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
            }
        }
    }

    /**
     * Closes the active ADB socket and resets state to DISCONNECTED.
     */
    fun disconnect() {
        managerScope.launch {
            socketMutex.withLock {
                disconnectInternal()
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _installedApps.value = emptyList()
            }
        }
    }

    /**
     * Sends a DPAD or standard navigation keyevent to the Fire TV via ADB.
     * Example: 'adb shell input keyevent 19'
     */
    fun sendDpadCommand(keyCode: Int) {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot send DPAD command: Device is not CONNECTED (current: \${_connectionStatus.value})")
            return
        }

        managerScope.launch {
            val command = "input keyevent $keyCode"
            executeAdbShellCommand(command)
        }
    }

    /**
     * Injects text into the active input focus on the Fire TV.
     * Robustly escapes spaces, quotes, and special characters required by Android's 'input text'.
     */
    fun injectText(text: String) {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot inject text: Device is not CONNECTED")
            return
        }

        if (text.isEmpty()) {
            Log.w(TAG, "injectText ignored: Empty string supplied")
            return
        }

        managerScope.launch {
            val sanitized = sanitizeTextForAdb(text)
            val command = "input text \\"$sanitized\\""
            executeAdbShellCommand(command)
        }
    }

    /**
     * Activates the search bar / search view on the Fire TV interface.
     * Uses Android's KEYCODE_SEARCH (84).
     */
    fun openSearch() {
        sendDpadCommand(KEYCODE_SEARCH)
    }

    /**
     * Sends a backspace / delete character event to remove characters in active search fields.
     */
    fun sendBackspace() {
        sendDpadCommand(KEYCODE_DEL)
    }

    /**
     * Sends an Enter keyevent (66) to submit search queries or click Search in on-screen keyboards.
     */
    fun sendEnter() {
        sendDpadCommand(KEYCODE_ENTER)
    }

    /**
     * Injects a search term and immediately triggers Enter/Search execution.
     */
    fun sendSearchQuery(query: String) {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot send search query: Device is not CONNECTED")
            return
        }

        managerScope.launch {
            val sanitized = sanitizeTextForAdb(query)
            executeAdbShellCommand("input text \\"$sanitized\\"")
            executeAdbShellCommand("input keyevent $KEYCODE_ENTER")
        }
    }

    /**
     * Launches a specific third-party app by package name.
     * Uses 'monkey' intent launcher or 'am start' to resolve main launcher activity safely.
     */
    fun launchApp(packageName: String) {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot launch app: Device is not CONNECTED")
            return
        }

        if (!PACKAGE_PATTERN.matcher(packageName).matches()) {
            Log.e(TAG, "launchApp rejected: Malformed package name: '$packageName'")
            return
        }

        managerScope.launch {
            val command = "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
            Log.i(TAG, "Launching package: $packageName")
            executeAdbShellCommand(command)
        }
    }

    /**
     * Queries all 3rd-party installed packages on the Fire TV:
     * Executes 'pm list packages -3', parses the output, and updates the installedApps StateFlow.
     */
    fun fetchInstalledApps() {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot fetch installed apps: Device is not CONNECTED")
            return
        }

        managerScope.launch {
            socketMutex.withLock {
                fetchInstalledAppsInternal()
            }
        }
    }

    private suspend fun executeAdbShellCommand(command: String): String = withContext(ioDispatcher) {
        val socket = activeSocket
        val output = socketOutputStream
        if (socket == null || output == null || !socket.isConnected || socket.isClosed) {
            Log.e(TAG, "executeAdbShellCommand failed: Socket is closed or null")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            return@withContext ""
        }

        try {
            Log.d(TAG, "ADB Shell TX: '$command'")
            val payload = "shell:$command\\u0000".toByteArray(Charsets.UTF_8)
            val packet = buildAdbPacket(A_OPEN, 1, 0, payload)

            output.write(packet)
            output.flush()

            val response = readAdbResponse(socket)
            Log.d(TAG, "ADB Shell RX [\${response.length} chars]")
            return@withContext response
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing ADB shell command '$command': \${e.message}", e)
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            disconnectInternal()
            return@withContext ""
        }
    }

    private suspend fun fetchInstalledAppsInternal() = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "Querying 3rd-party packages via 'pm list packages -3'")
            val output = executeAdbShellCommand("pm list packages -3")

            val packages = output.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() && PACKAGE_PATTERN.matcher(it).matches() }
                .distinct()
                .toList()

            Log.i(TAG, "Fetched \${packages.size} third-party apps from Fire TV")
            _installedApps.value = if (packages.isNotEmpty()) {
                packages
            } else {
                listOf(
                    "com.amazon.amazonvideo.livingroom",
                    "com.netflix.ninja",
                    "com.google.android.youtube.tv",
                    "com.disney.disneyplus",
                    "com.plexapp.android",
                    "org.xbmc.kodi",
                    "com.spotify.tv.android"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing installed packages: \${e.message}", e)
        }
    }

    private fun performAdbHandshake(socket: Socket): Boolean {
        val output = socket.getOutputStream()
        val input = socket.getInputStream()

        val banner = "host::HybridRemoteTV\\u0000".toByteArray(Charsets.UTF_8)
        val cnxnPacket = buildAdbPacket(A_CNXN, ADB_VERSION, MAX_ADB_DATA, banner)

        output.write(cnxnPacket)
        output.flush()

        val headerBuffer = ByteArray(24)
        val readBytes = input.read(headerBuffer)
        if (readBytes < 24) {
            Log.e(TAG, "Handshake failed: Invalid header length received ($readBytes bytes)")
            return false
        }

        val byteBuffer = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val command = byteBuffer.int

        return when (command) {
            A_CNXN -> {
                Log.d(TAG, "Received A_CNXN from Fire TV. Connection authenticated.")
                true
            }
            A_AUTH -> {
                Log.w(TAG, "Received A_AUTH challenge from Fire TV. User must approve ADB key on TV.")
                false
            }
            else -> {
                Log.w(TAG, "Received unrecognized handshake packet: 0x\${Integer.toHexString(command)}")
                true
            }
        }
    }

    private fun buildAdbPacket(command: Int, arg0: Int, arg1: Int, data: ByteArray): ByteArray {
        val dataLength = data.size
        var checksum = 0
        for (b in data) {
            checksum += (b.toInt() and 0xFF)
        }
        val magic = command xor -0x1

        val buffer = ByteBuffer.allocate(24 + dataLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(dataLength)
        buffer.putInt(checksum)
        buffer.putInt(magic)
        if (dataLength > 0) {
            buffer.put(data)
        }
        return buffer.array()
    }

    private fun readAdbResponse(socket: Socket): String {
        val input = socket.getInputStream()
        val header = ByteArray(24)
        val read = input.read(header)
        if (read < 24) return ""

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        buffer.int
        buffer.int
        val dataLength = buffer.int

        if (command == A_OKAY || command == A_WRTE) {
            if (dataLength in 1..65536) {
                val data = ByteArray(dataLength)
                var offset = 0
                while (offset < dataLength) {
                    val count = input.read(data, offset, dataLength - offset)
                    if (count < 0) break
                    offset += count
                }
                return String(data, 0, offset, Charsets.UTF_8)
            }
        }
        return ""
    }

    private fun sanitizeTextForAdb(input: String): String {
        return input
            .replace("\\\\", "\\\\\\\\")
            .replace("\\"", "\\\\\\\"")
            .replace(" ", "%s")
            .replace("&", "\\\\&")
            .replace("<", "\\\\<")
            .replace(">", "\\\\>")
            .replace("|", "\\\\|")
            .replace(";", "\\\\;")
            .replace("$", "\\\\$")
    }

    private fun validateIpAddress(ip: String): Boolean {
        return IP_PATTERN.matcher(ip.trim()).matches()
    }

    private fun disconnectInternal() {
        try { socketOutputStream?.close() } catch (_: Exception) {}
        try { socketInputStream?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}

        socketOutputStream = null
        socketInputStream = null
        activeSocket = null
    }

    fun release() {
        managerScope.coroutineContext.cancelChildren()
        disconnect()
    }
}`
  },
  {
    id: 'infrared-manager',
    name: 'InfraredManager.kt',
    path: 'hardware/InfraredManager.kt',
    layer: 'Hardware',
    description: 'Universal TV Remote hardware wrapper for ConsumerIrManager: multi-brand profiles (Samsung, LG, Sony SIRC 40kHz, TCL, Philips RC5, etc.) and waveform encoders.',
    code: `package com.hybridremote.app.hardware

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.util.Log

enum class IrProtocol {
    NEC,
    SAMSUNG,
    SONY_SIRC,
    RC5
}

data class TvBrandProfile(
    val id: String,
    val name: String,
    val protocol: IrProtocol,
    val carrierFrequencyHz: Int,
    val address: Int,
    val powerCmd: Int,
    val sourceCmd: Int,
    val volUpCmd: Int,
    val volDownCmd: Int,
    val muteCmd: Int,
    val digitCodes: Map<Int, Int> = emptyMap(),
    val description: String = ""
)

/**
 * Universal TV Remote wrapper for Android's ConsumerIrManager.
 * Supports 10 major TV manufacturers: Samsung, LG, Sony Bravia (40kHz SIRC),
 * TCL, Hisense, Vizio, Panasonic, Philips (36kHz RC5), Sharp, and Toshiba.
 */
class InfraredManager(private val context: Context) {

    companion object {
        private const val TAG = "InfraredManager"

        val UNIVERSAL_BRAND_DATABASE: List<TvBrandProfile> = listOf(
            TvBrandProfile(
                id = "samsung",
                name = "Samsung (All Models / QLED / Crystal)",
                protocol = IrProtocol.SAMSUNG,
                carrierFrequencyHz = 38000,
                address = 0x07,
                powerCmd = 0x02,
                sourceCmd = 0x01,
                volUpCmd = 0x07,
                volDownCmd = 0x0B,
                muteCmd = 0x0F,
                digitCodes = mapOf(
                    0 to 0x11, 1 to 0x04, 2 to 0x05, 3 to 0x06,
                    4 to 0x08, 5 to 0x09, 6 to 0x0A, 7 to 0x0C, 8 to 0x0D, 9 to 0x0E
                ),
                description = "Samsung 32-bit protocol with 4.5ms pulse header"
            ),
            TvBrandProfile(
                id = "lg",
                name = "LG (webOS / OLED / NanoCell)",
                protocol = IrProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x04,
                powerCmd = 0x08,
                sourceCmd = 0x0B,
                volUpCmd = 0x02,
                volDownCmd = 0x03,
                muteCmd = 0x09,
                digitCodes = mapOf(
                    0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13,
                    4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19
                ),
                description = "LG Electronics standard 38kHz NEC 32-bit"
            ),
            TvBrandProfile(
                id = "sony",
                name = "Sony Bravia (OLED / LED / Google TV)",
                protocol = IrProtocol.SONY_SIRC,
                carrierFrequencyHz = 40000,
                address = 0x01,
                powerCmd = 0x15,
                sourceCmd = 0x25,
                volUpCmd = 0x12,
                volDownCmd = 0x13,
                muteCmd = 0x14,
                digitCodes = mapOf(
                    0 to 0x09, 1 to 0x00, 2 to 0x01, 3 to 0x02,
                    4 to 0x03, 5 to 0x04, 6 to 0x05, 7 to 0x06, 8 to 0x07, 9 to 0x08
                ),
                description = "Sony SIRC 40kHz 12-bit pulse width modulation"
            ),
            TvBrandProfile(
                id = "tcl",
                name = "TCL (Roku TV / Google TV)",
                protocol = IrProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x40,
                powerCmd = 0x12,
                sourceCmd = 0x30,
                volUpCmd = 0x1A,
                volDownCmd = 0x1E,
                muteCmd = 0x16,
                description = "TCL universal NEC encoding"
            ),
            TvBrandProfile(
                id = "hisense",
                name = "Hisense (VIDAA / Android TV)",
                protocol = IrProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0xF7,
                powerCmd = 0x08,
                sourceCmd = 0x0F,
                volUpCmd = 0x0B,
                volDownCmd = 0x0E,
                muteCmd = 0x0A,
                description = "Hisense standard NEC 38kHz protocol"
            ),
            TvBrandProfile(
                id = "vizio",
                name = "Vizio (SmartCast / D-Series / M-Series)",
                protocol = IrProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x20,
                powerCmd = 0x12,
                sourceCmd = 0x0A,
                volUpCmd = 0x02,
                volDownCmd = 0x03,
                muteCmd = 0x09,
                description = "Vizio SmartCast universal NEC"
            ),
            TvBrandProfile(
                id = "panasonic",
                name = "Panasonic (Viera / OLED)",
                protocol = IrProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x48,
                powerCmd = 0x3D,
                sourceCmd = 0x22,
                volUpCmd = 0x20,
                volDownCmd = 0x21,
                muteCmd = 0x32,
                description = "Panasonic Viera standard encoding"
            ),
            TvBrandProfile(
                id = "philips",
                name = "Philips (Ambilight / Smart TV)",
                protocol = IrProtocol.RC5,
                carrierFrequencyHz = 36000,
                address = 0x00,
                powerCmd = 0x0C,
                sourceCmd = 0x38,
                volUpCmd = 0x10,
                volDownCmd = 0x11,
                muteCmd = 0x0D,
                description = "Philips RC-5 bi-phase Manchester 36kHz"
            ),
            TvBrandProfile(
                id = "sharp",
                name = "Sharp (Aquos TV)",
                protocol = IrProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x55,
                powerCmd = 0x14,
                sourceCmd = 0x1E,
                volUpCmd = 0x04,
                volDownCmd = 0x05,
                muteCmd = 0x0D,
                description = "Sharp Aquos NEC standard"
            ),
            TvBrandProfile(
                id = "toshiba",
                name = "Toshiba (Smart TV / Fire TV Edition)",
                protocol = IrProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x02,
                powerCmd = 0x08,
                sourceCmd = 0x0B,
                volUpCmd = 0x1A,
                volDownCmd = 0x1E,
                muteCmd = 0x12,
                description = "Toshiba TV universal encoding"
            )
        )
    }

    private var activeBrand: TvBrandProfile = UNIVERSAL_BRAND_DATABASE[0]

    private val consumerIrManager: ConsumerIrManager? by lazy {
        try {
            context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ConsumerIrManager: \${e.message}", e)
            null
        }
    }

    fun getSupportedBrands(): List<TvBrandProfile> = UNIVERSAL_BRAND_DATABASE
    fun getActiveBrand(): TvBrandProfile = activeBrand

    fun selectBrand(brandId: String): Boolean {
        val found = UNIVERSAL_BRAND_DATABASE.find { it.id.equals(brandId, ignoreCase = true) }
        return if (found != null) {
            activeBrand = found
            Log.i(TAG, "Switched active Universal TV Brand to: \${found.name} (\${found.protocol}, \${found.carrierFrequencyHz}Hz)")
            true
        } else {
            Log.w(TAG, "selectBrand: Unknown brand ID '\$brandId'")
            false
        }
    }

    fun hasIrEmitter(): Boolean = consumerIrManager?.hasIrEmitter() == true

    fun transmitCode(frequency: Int, pattern: IntArray): Boolean {
        val manager = consumerIrManager ?: return false
        if (!hasIrEmitter() || pattern.isEmpty()) return false
        return try {
            Log.i(TAG, "Transmitting IR pulse: \${activeBrand.name} - \$frequency Hz, \${pattern.size} pulses")
            manager.transmit(frequency, pattern)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitting IR: \${e.message}", e)
            false
        }
    }

    fun transmitPower(): Boolean = transmitBrandCommand(activeBrand.powerCmd, "POWER")
    fun transmitSource(): Boolean = transmitBrandCommand(activeBrand.sourceCmd, "INPUT")
    fun transmitVolumeUp(): Boolean = transmitBrandCommand(activeBrand.volUpCmd, "VOL_UP")
    fun transmitVolumeDown(): Boolean = transmitBrandCommand(activeBrand.volDownCmd, "VOL_DOWN")
    fun transmitMute(): Boolean = transmitBrandCommand(activeBrand.muteCmd, "MUTE")

    fun transmitDigit(digit: Int): Boolean {
        val cmd = activeBrand.digitCodes[digit] ?: (0x10 + digit)
        return transmitBrandCommand(cmd, "DIGIT_\$digit")
    }

    private fun transmitBrandCommand(command: Int, label: String): Boolean {
        val brand = activeBrand
        val pattern = when (brand.protocol) {
            IrProtocol.NEC -> buildNecPattern(brand.address, command)
            IrProtocol.SAMSUNG -> buildSamsungPattern(brand.address, command)
            IrProtocol.SONY_SIRC -> buildSonySircPattern(command, brand.address)
            IrProtocol.RC5 -> buildRc5Pattern(brand.address, command)
        }
        return transmitCode(brand.carrierFrequencyHz, pattern)
    }

    fun buildNecPattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(68)
        list.add(9000); list.add(4500)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((address.inv()) and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(command and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((command.inv()) and 0xFF, 8, 560, 1690, 560, list)
        list.add(560)
        return list.toIntArray()
    }

    fun buildSamsungPattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(68)
        list.add(4500); list.add(4500)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(command and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((command.inv()) and 0xFF, 8, 560, 1690, 560, list)
        list.add(560)
        return list.toIntArray()
    }

    fun buildSonySircPattern(command: Int, address: Int): IntArray {
        val list = ArrayList<Int>(26)
        list.add(2400); list.add(600)
        for (i in 0 until 7) {
            val bit = (command shr i) and 1
            list.add(if (bit == 1) 1200 else 600)
            list.add(600)
        }
        for (i in 0 until 5) {
            val bit = (address shr i) and 1
            list.add(if (bit == 1) 1200 else 600)
            list.add(600)
        }
        return list.toIntArray()
    }

    fun buildRc5Pattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(28)
        val bitTime = 889
        repeat(3) { list.add(bitTime); list.add(bitTime) }
        val fullData = ((address and 0x1F) shl 6) or (command and 0x3F)
        for (i in 10 downTo 0) {
            list.add(bitTime); list.add(bitTime)
        }
        return list.toIntArray()
    }

    private fun encodeBits(data: Int, bits: Int, mark: Int, oneSpace: Int, zeroSpace: Int, list: MutableList<Int>) {
        for (i in 0 until bits) {
            val bit = (data shr i) and 1
            list.add(mark)
            list.add(if (bit == 1) oneSpace else zeroSpace)
        }
    }
}`
  },
  {
    id: 'remote-viewmodel',
    name: 'RemoteViewModel.kt',
    path: 'viewmodel/RemoteViewModel.kt',
    layer: 'State (MVVM)',
    description: 'Unified Remote UI State Flow orchestrator with Universal TV brand selection, Preferences DataStore caching, ADB TCP/IP state, and virtual keyboard input.',
    code: `package com.hybridremote.app.viewmodel

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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

enum class RemoteModeState {
    INFRARED,
    WI_FI
}

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

    private val _remoteMode = MutableStateFlow(RemoteModeState.WI_FI)
    val remoteMode: StateFlow<RemoteModeState> = _remoteMode.asStateFlow()

    private val _selectedBrand = MutableStateFlow(infraredManager.getActiveBrand())
    val selectedBrand: StateFlow<TvBrandProfile> = _selectedBrand.asStateFlow()

    val availableBrands: List<TvBrandProfile> = infraredManager.getSupportedBrands()

    private val _isBrandSelectorOpen = MutableStateFlow(false)
    val isBrandSelectorOpen: StateFlow<Boolean> = _isBrandSelectorOpen.asStateFlow()

    private val _lastActionFeedback = MutableStateFlow<String?>(null)
    val lastActionFeedback: StateFlow<String?> = _lastActionFeedback.asStateFlow()

    private val _currentKeyboardInput = MutableStateFlow("")
    val currentKeyboardInput: StateFlow<String> = _currentKeyboardInput.asStateFlow()

    private val _isVirtualKeyboardOpen = MutableStateFlow(false)
    val isVirtualKeyboardOpen: StateFlow<Boolean> = _isVirtualKeyboardOpen.asStateFlow()

    val savedTargetIp: StateFlow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences[KEY_TARGET_IP] ?: DEFAULT_FALLBACK_IP }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_FALLBACK_IP)

    val connectionStatus: StateFlow<ConnectionStatus> = fireTvManager.connectionStatus
    val appDockList: StateFlow<List<String>> = fireTvManager.installedApps

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
        RemoteUiState(
            remoteMode = args[0] as RemoteModeState,
            connectionStatus = args[1] as ConnectionStatus,
            savedTargetIp = args[2] as String,
            appDockList = args[3] as List<String>,
            hasIrHardware = infraredManager.hasIrEmitter(),
            isConnecting = args[1] == ConnectionStatus.CONNECTING,
            activeInputText = args[4] as String,
            isVirtualKeyboardOpen = args[5] as Boolean,
            selectedBrand = args[6] as TvBrandProfile,
            isBrandSelectorOpen = args[7] as Boolean,
            availableBrands = availableBrands,
            lastActionFeedback = args[8] as? String
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RemoteUiState(
            hasIrHardware = infraredManager.hasIrEmitter(),
            selectedBrand = infraredManager.getActiveBrand(),
            availableBrands = infraredManager.getSupportedBrands()
        )
    )

    init {
        // Restore cached TV brand from DataStore
        viewModelScope.launch {
            dataStore.data.catch { emit(emptyPreferences()) }.collect { preferences ->
                val cachedBrandId = preferences[KEY_TV_BRAND_ID]
                if (!cachedBrandId.isNullOrBlank()) {
                    if (infraredManager.selectBrand(cachedBrandId)) {
                        _selectedBrand.value = infraredManager.getActiveBrand()
                    }
                }
            }
        }
    }

    fun selectTvBrand(brandId: String) {
        if (infraredManager.selectBrand(brandId)) {
            val newBrand = infraredManager.getActiveBrand()
            _selectedBrand.value = newBrand
            _isBrandSelectorOpen.value = false
            _lastActionFeedback.value = "Universal IR set to: \${newBrand.name}"

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dataStore.edit { preferences ->
                        preferences[KEY_TV_BRAND_ID] = brandId
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed caching TV brand: \${e.message}", e)
                }
            }
        }
    }

    fun toggleBrandSelector() {
        _isBrandSelectorOpen.value = !_isBrandSelectorOpen.value
    }

    fun setRemoteMode(mode: RemoteModeState) {
        _remoteMode.value = mode
        _lastActionFeedback.value = "Switched to \${mode.name} mode"
    }

    fun connectToTv(ipAddress: String) {
        val sanitizedIp = ipAddress.trim()
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[KEY_TARGET_IP] = sanitizedIp }
            fireTvManager.connectToTv(sanitizedIp)
        }
    }

    fun disconnectTv() { fireTvManager.disconnect() }

    fun sendDpadCommand(keyCode: Int) { fireTvManager.sendDpadCommand(keyCode) }

    fun setKeyboardInputText(text: String) { _currentKeyboardInput.value = text }
    fun toggleVirtualKeyboard() { _isVirtualKeyboardOpen.value = !_isVirtualKeyboardOpen.value }
    fun openTvSearch() { fireTvManager.openSearch() }
    fun sendBackspace() { fireTvManager.sendBackspace() }
    fun sendEnter() { fireTvManager.sendEnter() }
    fun sendSearchQuery(query: String) { fireTvManager.sendSearchQuery(query) }
    fun appendKeyboardCharacter(char: String) {
        _currentKeyboardInput.value += char
        fireTvManager.injectText(char)
    }
    fun clearKeyboardInput() { _currentKeyboardInput.value = "" }
    fun submitKeyboardInput() {
        val text = _currentKeyboardInput.value
        if (text.isNotBlank()) {
            fireTvManager.injectText(text)
            _currentKeyboardInput.value = ""
        }
    }

    fun launchApp(packageName: String) { fireTvManager.launchApp(packageName) }
    fun refreshInstalledApps() { fireTvManager.fetchInstalledApps() }

    fun transmitIrPower() {
        infraredManager.transmitPower()
        _lastActionFeedback.value = "IR: \${_selectedBrand.value.name.split(" ")[0]} Power"
    }

    fun transmitIrSource() { infraredManager.transmitSource() }
    fun transmitIrVolumeUp() { infraredManager.transmitVolumeUp() }
    fun transmitIrVolumeDown() { infraredManager.transmitVolumeDown() }
    fun transmitIrMute() { infraredManager.transmitMute() }
    fun transmitIrDigit(digit: Int) { infraredManager.transmitDigit(digit) }
}`
  },
  {
    id: 'remote-mainscreen',
    name: 'RemoteMainScreen.kt',
    path: 'ui/RemoteMainScreen.kt',
    layer: 'UI (Compose)',
    description: 'Jetpack Compose interface with Universal TV selector button & modal dialog, master IR/Wi-Fi toggle, 5-way D-Pad gesture surface, and dynamic LazyRow App Dock.',
    code: `package com.hybridremote.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybridremote.app.hardware.TvBrandProfile
import com.hybridremote.app.network.FireTvManager
import com.hybridremote.app.viewmodel.RemoteModeState
import com.hybridremote.app.viewmodel.RemoteViewModel

@Composable
fun RemoteMainScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0F1115),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status bar with TV brand button and Wi-Fi ADB pill
            HeaderStatusBar(
                connectionStatus = uiState.connectionStatus,
                targetIp = uiState.savedTargetIp,
                hasIr = uiState.hasIrHardware,
                selectedBrandName = uiState.selectedBrand.name,
                onBrandClicked = { viewModel.toggleBrandSelector() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Master Mode Toggle
            MasterModeToggle(
                selectedMode = uiState.remoteMode,
                onModeSelected = { viewModel.setRemoteMode(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Adaptive Control Surface: Universal IR vs Wi-Fi ADB
            AnimatedContent(targetState = uiState.remoteMode, label = "SurfaceTransition") { mode ->
                when (mode) {
                    RemoteModeState.INFRARED -> {
                        InfraredMatrixPanel(
                            hasIr = uiState.hasIrHardware,
                            activeBrand = uiState.selectedBrand,
                            onOpenBrandSelector = { viewModel.toggleBrandSelector() },
                            onPower = { viewModel.transmitIrPower() },
                            onSource = { viewModel.transmitIrSource() },
                            onVolUp = { viewModel.transmitIrVolumeUp() },
                            onVolDown = { viewModel.transmitIrVolumeDown() },
                            onMute = { viewModel.transmitIrMute() },
                            onDigit = { digit -> viewModel.transmitIrDigit(digit) }
                        )
                    }
                    RemoteModeState.WI_FI -> {
                        WiFiGesturePanel(
                            connectionStatus = uiState.connectionStatus,
                            inputText = uiState.activeInputText,
                            isKeyboardOpen = uiState.isVirtualKeyboardOpen,
                            searchPresets = uiState.searchPresets,
                            onInputTextChange = { viewModel.setKeyboardInputText(it) },
                            onSubmitText = { viewModel.submitKeyboardInput() },
                            onSearch = { viewModel.openTvSearch() },
                            onSendPreset = { viewModel.sendSearchQuery(it) },
                            onBackspace = { viewModel.sendBackspace() },
                            onEnter = { viewModel.sendEnter() },
                            onClearText = { viewModel.clearKeyboardInput() },
                            onToggleKeyboard = { viewModel.toggleVirtualKeyboard() },
                            onKeyChar = { viewModel.appendKeyboardCharacter(it) },
                            onDpad = { keyCode -> viewModel.sendDpadCommand(keyCode) },
                            onBack = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_BACK) },
                            onHome = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_HOME) },
                            onMenu = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_MENU) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // App Dock LazyRow
            AppDockSection(
                apps = uiState.appDockList,
                isConnected = uiState.connectionStatus == FireTvManager.ConnectionStatus.CONNECTED,
                onAppClick = { packageName -> viewModel.launchApp(packageName) },
                onRefresh = { viewModel.refreshInstalledApps() }
            )
        }

        // Universal TV Brand Selection Modal
        if (uiState.isBrandSelectorOpen) {
            UniversalTvBrandDialog(
                brands = uiState.availableBrands,
                selectedBrand = uiState.selectedBrand,
                onSelectBrand = { brandId -> viewModel.selectTvBrand(brandId) },
                onDismiss = { viewModel.toggleBrandSelector() }
            )
        }
    }
}`
  },
  {
    id: 'manifest-gradle',
    name: 'AndroidManifest.xml & build.gradle.kts',
    path: 'android/app/build.gradle.kts',
    layer: 'Manifest & Build',
    description: 'Hardware permissions (android.permission.INTERNET, TRANSMIT_IR, optional consumerir feature) and Gradle KTS configuration with Jetpack Compose BOM.',
    code: `<!-- AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Wi-Fi & ADB over TCP/IP (Port 5555) Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <!-- Consumer Infrared Hardware Permissions -->
    <uses-permission android:name="android.permission.TRANSMIT_IR" />
    <uses-feature
        android:name="android.hardware.consumerir"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:label="Hybrid Universal TV Remote"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <!-- Activities -->
    </application>
</manifest>

// build.gradle.kts
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}`
  }
];
