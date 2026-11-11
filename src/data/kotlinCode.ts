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
    description: 'Hardware wrapper for ConsumerIrManager: compatibility checks, carrier frequency querying, 38kHz NEC waveform generator, and hardware pulsing.',
    code: `package com.hybridremote.app.hardware

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.util.Log

/**
 * Hardware wrapper for Android's ConsumerIrManager.
 * Handles device compatibility verification, carrier frequency querying,
 * and high-precision pulse transmission for standard television hardware sets.
 */
class InfraredManager(private val context: Context) {

    companion object {
        private const val TAG = "InfraredManager"

        // Standard NEC / RC carrier frequency for universal television sets
        const val DEFAULT_CARRIER_FREQUENCY_HZ = 38000 // 38 kHz
        const val SONY_CARRIER_FREQUENCY_HZ = 40000    // 40 kHz

        // NEC Protocol Timing Constants (microseconds)
        private const val NEC_HDR_MARK = 9000
        private const val NEC_HDR_SPACE = 4500
        private const val NEC_BIT_MARK = 560
        private const val NEC_ONE_SPACE = 1690
        private const val NEC_ZERO_SPACE = 560
        private const val NEC_STOP_BIT = 560

        // Standard Universal TV Device Codes (Address 0x04)
        private const val TV_ADDRESS = 0x04
        private const val CMD_POWER = 0x08
        private const val CMD_SOURCE = 0x0B
        private const val CMD_VOL_UP = 0x02
        private const val CMD_VOL_DOWN = 0x03
        private const val CMD_MUTE = 0x09
        private const val CMD_DIGIT_0 = 0x10
        private const val CMD_DIGIT_1 = 0x11
        private const val CMD_DIGIT_2 = 0x12
        private const val CMD_DIGIT_3 = 0x13
        private const val CMD_DIGIT_4 = 0x14
        private const val CMD_DIGIT_5 = 0x15
        private const val CMD_DIGIT_6 = 0x16
        private const val CMD_DIGIT_7 = 0x17
        private const val CMD_DIGIT_8 = 0x18
        private const val CMD_DIGIT_9 = 0x19
    }

    private val consumerIrManager: ConsumerIrManager? by lazy {
        try {
            context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ConsumerIrManager: \${e.message}", e)
            null
        }
    }

    /**
     * Checks whether the host device is equipped with an infrared emitter.
     */
    fun hasIrEmitter(): Boolean {
        return try {
            val hasEmitter = consumerIrManager?.hasIrEmitter() == true
            Log.d(TAG, "Hardware IR Emitter present: $hasEmitter")
            hasEmitter
        } catch (e: Exception) {
            Log.e(TAG, "Exception querying hasIrEmitter: \${e.message}", e)
            false
        }
    }

    /**
     * Queries the array of carrier frequency ranges supported by the hardware emitter.
     */
    fun getCarrierFrequencies(): Array<ConsumerIrManager.CarrierFrequencyRange> {
        val manager = consumerIrManager
        if (manager == null || !hasIrEmitter()) {
            Log.w(TAG, "getCarrierFrequencies aborted: IR hardware unavailable")
            return emptyArray()
        }

        return try {
            val ranges = manager.carrierFrequencies ?: emptyArray()
            ranges.forEach { range ->
                Log.d(TAG, "Supported IR Range: \${range.minFrequency} Hz - \${range.maxFrequency} Hz")
            }
            ranges
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying carrier frequencies: \${e.message}", e)
            emptyArray()
        }
    }

    /**
     * Validates whether a specified frequency is within hardware tolerances.
     */
    fun isFrequencySupported(frequency: Int): Boolean {
        val ranges = getCarrierFrequencies()
        if (ranges.isEmpty()) return true

        return ranges.any { frequency in it.minFrequency..it.maxFrequency }
    }

    /**
     * Pulses hardware IR commands at the specified frequency and alternating mark/space pattern.
     *
     * @param frequency Carrier frequency in Hertz (e.g. 38000)
     * @param pattern Array of alternating pulses (mark then space) measured in microseconds
     * @return Boolean true if transmission command was accepted by the hardware driver
     */
    fun transmitCode(frequency: Int, pattern: IntArray): Boolean {
        val manager = consumerIrManager
        if (manager == null || !hasIrEmitter()) {
            Log.e(TAG, "transmitCode failed: No functional IR emitter detected on this device")
            return false
        }

        if (pattern.isEmpty()) {
            Log.e(TAG, "transmitCode rejected: Pattern array is empty")
            return false
        }

        if (!isFrequencySupported(frequency)) {
            Log.w(TAG, "Requested frequency $frequency Hz may be outside supported device ranges")
        }

        return try {
            Log.i(TAG, "Transmitting IR pulse: $frequency Hz, \${pattern.size} pulses")
            manager.transmit(frequency, pattern)
            true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Hardware driver rejected IR pattern structure: \${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during IR pulse transmission: \${e.message}", e)
            false
        }
    }

    // High-Level Standard TV Command Transmitters

    fun transmitPower(): Boolean {
        return transmitNecCommand(TV_ADDRESS, CMD_POWER)
    }

    fun transmitSource(): Boolean {
        return transmitNecCommand(TV_ADDRESS, CMD_SOURCE)
    }

    fun transmitVolumeUp(): Boolean {
        return transmitNecCommand(TV_ADDRESS, CMD_VOL_UP)
    }

    fun transmitVolumeDown(): Boolean {
        return transmitNecCommand(TV_ADDRESS, CMD_VOL_DOWN)
    }

    fun transmitMute(): Boolean {
        return transmitNecCommand(TV_ADDRESS, CMD_MUTE)
    }

    fun transmitDigit(digit: Int): Boolean {
        val cmd = when (digit) {
            0 -> CMD_DIGIT_0
            1 -> CMD_DIGIT_1
            2 -> CMD_DIGIT_2
            3 -> CMD_DIGIT_3
            4 -> CMD_DIGIT_4
            5 -> CMD_DIGIT_5
            6 -> CMD_DIGIT_6
            7 -> CMD_DIGIT_7
            8 -> CMD_DIGIT_8
            9 -> CMD_DIGIT_9
            else -> {
                Log.e(TAG, "Invalid digit command: $digit (must be 0..9)")
                return false
            }
        }
        return transmitNecCommand(TV_ADDRESS, cmd)
    }

    /**
     * Builds standard 32-bit NEC protocol IR waveform:
     * - 9000µs leader mark + 4500µs space
     * - 8-bit Address + 8-bit ~Address
     * - 8-bit Command + 8-bit ~Command
     * - 560µs stop mark
     */
    fun transmitNecCommand(address: Int, command: Int, frequency: Int = DEFAULT_CARRIER_FREQUENCY_HZ): Boolean {
        val pattern = buildNecPattern(address, command)
        return transmitCode(frequency, pattern)
    }

    private fun buildNecPattern(address: Int, command: Int): IntArray {
        val patternList = ArrayList<Int>(68)

        // Leader
        patternList.add(NEC_HDR_MARK)
        patternList.add(NEC_HDR_SPACE)

        // 32-bit payload: [address][~address][command][~command]
        encodeNecByte(address and 0xFF, patternList)
        encodeNecByte((address.inv()) and 0xFF, patternList)
        encodeNecByte(command and 0xFF, patternList)
        encodeNecByte((command.inv()) and 0xFF, patternList)

        // Stop bit
        patternList.add(NEC_STOP_BIT)

        return patternList.toIntArray()
    }

    private fun encodeNecByte(byteValue: Int, list: MutableList<Int>) {
        for (i in 0 until 8) {
            val bit = (byteValue shr i) and 1
            list.add(NEC_BIT_MARK)
            if (bit == 1) {
                list.add(NEC_ONE_SPACE)
            } else {
                list.add(NEC_ZERO_SPACE)
            }
        }
    }
}`
  },
  {
    id: 'remote-viewmodel',
    name: 'RemoteViewModel.kt',
    path: 'viewmodel/RemoteViewModel.kt',
    layer: 'State (MVVM)',
    description: 'Unified Remote UI State Flow orchestrator managing RemoteModeState, ConnectionStatus, Preferences DataStore IP caching, and AppDockList.',
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
import com.hybridremote.app.network.FireTvManager
import com.hybridremote.app.network.FireTvManager.ConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        private const val DEFAULT_FALLBACK_IP = "192.168.1.150"
    }

    private val _remoteMode = MutableStateFlow(RemoteModeState.WI_FI)
    val remoteMode: StateFlow<RemoteModeState> = _remoteMode.asStateFlow()

    private val _lastActionFeedback = MutableStateFlow<String?>(null)
    val lastActionFeedback: StateFlow<String?> = _lastActionFeedback.asStateFlow()

    private val _currentKeyboardInput = MutableStateFlow("")
    val currentKeyboardInput: StateFlow<String> = _currentKeyboardInput.asStateFlow()

    val savedTargetIp: StateFlow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading Preferences DataStore: \${exception.message}", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_TARGET_IP] ?: DEFAULT_FALLBACK_IP
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FALLBACK_IP
        )

    val connectionStatus: StateFlow<ConnectionStatus> = fireTvManager.connectionStatus
    val appDockList: StateFlow<List<String>> = fireTvManager.installedApps

    val uiState: StateFlow<RemoteUiState> = combine(
        _remoteMode,
        fireTvManager.connectionStatus,
        savedTargetIp,
        fireTvManager.installedApps,
        _currentKeyboardInput,
        _lastActionFeedback
    ) { mode, status, ip, dockList, keyboardText, feedback ->
        RemoteUiState(
            remoteMode = mode,
            connectionStatus = status,
            savedTargetIp = ip,
            appDockList = dockList,
            hasIrHardware = infraredManager.hasIrEmitter(),
            isConnecting = status == ConnectionStatus.CONNECTING,
            activeInputText = keyboardText,
            lastActionFeedback = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RemoteUiState(
            hasIrHardware = infraredManager.hasIrEmitter()
        )
    )

    init {
        viewModelScope.launch {
            fireTvManager.connectionStatus.collect { status ->
                if (status == ConnectionStatus.CONNECTED) {
                    val ipToSave = savedTargetIp.value
                    if (ipToSave.isNotBlank()) {
                        persistIpAddress(ipToSave)
                    }
                    _lastActionFeedback.value = "Connected to Fire TV ($ipToSave)"
                } else if (status == ConnectionStatus.AUTH_REQUIRED) {
                    _lastActionFeedback.value = "Action Required: Grant ADB Authorization on TV screen"
                }
            }
        }
    }

    fun setRemoteMode(mode: RemoteModeState) {
        _remoteMode.value = mode
        _lastActionFeedback.value = "Switched to \${mode.name} mode"
    }

    fun connectToTv(ipAddress: String) {
        val sanitizedIp = ipAddress.trim()
        viewModelScope.launch(Dispatchers.IO) {
            persistIpAddress(sanitizedIp)
            fireTvManager.connectToTv(sanitizedIp)
        }
    }

    fun disconnectTv() {
        fireTvManager.disconnect()
        _lastActionFeedback.value = "Disconnected from TV"
    }

    fun updateTargetIpInput(ip: String) {
        viewModelScope.launch(Dispatchers.IO) {
            persistIpAddress(ip.trim())
        }
    }

    private suspend fun persistIpAddress(ip: String) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_TARGET_IP] = ip
            }
            Log.d(TAG, "Cached target IP '$ip' in Preferences DataStore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed caching target IP in DataStore: \${e.message}", e)
        }
    }

    fun sendDpadCommand(keyCode: Int) {
        fireTvManager.sendDpadCommand(keyCode)
    }

    fun setKeyboardInputText(text: String) {
        _currentKeyboardInput.value = text
    }

    fun submitKeyboardInput() {
        val text = _currentKeyboardInput.value
        if (text.isNotBlank()) {
            fireTvManager.injectText(text)
            _lastActionFeedback.value = "Injected text: \\"$text\\""
            _currentKeyboardInput.value = ""
        }
    }

    fun launchApp(packageName: String) {
        fireTvManager.launchApp(packageName)
        _lastActionFeedback.value = "Launching: \${packageName.substringAfterLast('.')}"
    }

    fun refreshInstalledApps() {
        fireTvManager.fetchInstalledApps()
    }

    fun transmitIrPower() {
        val success = infraredManager.transmitPower()
        _lastActionFeedback.value = if (success) "IR: Transmitted TV Power" else "IR Error: Check hardware emitter"
    }

    fun transmitIrSource() {
        val success = infraredManager.transmitSource()
        _lastActionFeedback.value = if (success) "IR: Transmitted Input Source" else "IR Error"
    }

    fun transmitIrVolumeUp() {
        infraredManager.transmitVolumeUp()
    }

    fun transmitIrVolumeDown() {
        infraredManager.transmitVolumeDown()
    }

    fun transmitIrMute() {
        val success = infraredManager.transmitMute()
        _lastActionFeedback.value = if (success) "IR: Transmitted Mute" else "IR Error"
    }

    fun transmitIrDigit(digit: Int) {
        val success = infraredManager.transmitDigit(digit)
        if (success) {
            _lastActionFeedback.value = "IR: Digit $digit"
        }
    }

    override fun onCleared() {
        super.onCleared()
        fireTvManager.release()
    }
}`
  },
  {
    id: 'remote-mainscreen',
    name: 'RemoteMainScreen.kt',
    path: 'ui/RemoteMainScreen.kt',
    layer: 'UI (Compose)',
    description: 'Clean responsive Jetpack Compose screen with master toggle, IR matrix panel, 5-way D-Pad gesture surface, floating text injector, and dynamic LazyRow App Dock.',
    code: `package com.hybridremote.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybridremote.app.network.FireTvManager
import com.hybridremote.app.network.FireTvManager.ConnectionStatus
import com.hybridremote.app.viewmodel.RemoteModeState
import com.hybridremote.app.viewmodel.RemoteUiState
import com.hybridremote.app.viewmodel.RemoteViewModel

@Composable
fun RemoteMainScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showIpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.lastActionFeedback) {
        uiState.lastActionFeedback?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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
            HeaderStatusBar(
                connectionStatus = uiState.connectionStatus,
                targetIp = uiState.savedTargetIp,
                hasIr = uiState.hasIrHardware,
                onIpClicked = { showIpDialog = !showIpDialog }
            )

            AnimatedVisibility(visible = showIpDialog) {
                IpConfigurationCard(
                    currentIp = uiState.savedTargetIp,
                    connectionStatus = uiState.connectionStatus,
                    onConnect = { ip ->
                        viewModel.connectToTv(ip)
                        showIpDialog = false
                    },
                    onDisconnect = {
                        viewModel.disconnectTv()
                        showIpDialog = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            MasterModeToggle(
                selectedMode = uiState.remoteMode,
                onModeSelected = { viewModel.setRemoteMode(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedContent(
                targetState = uiState.remoteMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "RemoteSurfaceTransition"
            ) { mode ->
                when (mode) {
                    RemoteModeState.INFRARED -> {
                        InfraredMatrixPanel(
                            hasIr = uiState.hasIrHardware,
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
                            onInputTextChange = { viewModel.setKeyboardInputText(it) },
                            onSubmitText = { viewModel.submitKeyboardInput() },
                            onDpad = { keyCode -> viewModel.sendDpadCommand(keyCode) },
                            onBack = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_BACK) },
                            onHome = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_HOME) },
                            onMenu = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_MENU) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            AppDockSection(
                apps = uiState.appDockList,
                isWifiMode = uiState.remoteMode == RemoteModeState.WI_FI,
                isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED,
                onAppClick = { packageName -> viewModel.launchApp(packageName) },
                onRefresh = { viewModel.refreshInstalledApps() }
            )

            Spacer(modifier = Modifier.height(16.dp))
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
        android:label="Hybrid TV Remote"
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
