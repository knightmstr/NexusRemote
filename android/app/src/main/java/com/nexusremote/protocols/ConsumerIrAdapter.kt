package com.nexusremote.protocols

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import com.nexusremote.domain.model.CommandResult
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.InstalledTvApp
import com.nexusremote.domain.model.RemoteCommand
import com.nexusremote.domain.model.SavedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IrEncodingProtocol {
    NEC,
    SAMSUNG,
    SONY_SIRC,
    RC5
}

data class UniversalIrBrand(
    val id: String,
    val name: String,
    val modelSeries: String,
    val protocol: IrEncodingProtocol,
    val carrierFrequencyHz: Int,
    val address: Int,
    val powerCmd: Int,
    val sourceCmd: Int,
    val volUpCmd: Int,
    val volDownCmd: Int,
    val muteCmd: Int,
    val chUpCmd: Int,
    val chDownCmd: Int,
    val upCmd: Int,
    val downCmd: Int,
    val leftCmd: Int,
    val rightCmd: Int,
    val okCmd: Int,
    val backCmd: Int,
    val homeCmd: Int,
    val menuCmd: Int,
    val digitCodes: Map<Int, Int>
)

/**
 * Universal Hardware Consumer IR Adapter utilizing Android's ConsumerIrManager.
 * Detects hardware availability dynamically via manager.hasIrEmitter() and supports
 * 10 major TV brands across NEC (38 kHz), Samsung (38 kHz), Sony SIRC (40 kHz), and RC-5 (36 kHz).
 */
class ConsumerIrAdapter(
    override val device: SavedDevice,
    private val context: Context
) : DeviceController {

    companion object {
        private const val TAG = "ConsumerIrAdapter"

        val UNIVERSAL_BRANDS: List<UniversalIrBrand> = listOf(
            UniversalIrBrand(
                id = "samsung",
                name = "Samsung",
                modelSeries = "Neo QLED / OLED / Crystal 4K",
                protocol = IrEncodingProtocol.SAMSUNG,
                carrierFrequencyHz = 38000,
                address = 0x07,
                powerCmd = 0x02,
                sourceCmd = 0x01,
                volUpCmd = 0x07,
                volDownCmd = 0x0B,
                muteCmd = 0x0F,
                chUpCmd = 0x12,
                chDownCmd = 0x10,
                upCmd = 0x60,
                downCmd = 0x61,
                leftCmd = 0x65,
                rightCmd = 0x62,
                okCmd = 0x68,
                backCmd = 0x58,
                homeCmd = 0x79,
                menuCmd = 0x1A,
                digitCodes = mapOf(0 to 0x11, 1 to 0x04, 2 to 0x05, 3 to 0x06, 4 to 0x08, 5 to 0x09, 6 to 0x0A, 7 to 0x0C, 8 to 0x0D, 9 to 0x0E)
            ),
            UniversalIrBrand(
                id = "lg",
                name = "LG Electronics",
                modelSeries = "webOS OLED / QNED / NanoCell",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x04,
                powerCmd = 0x08,
                sourceCmd = 0x0B,
                volUpCmd = 0x02,
                volDownCmd = 0x03,
                muteCmd = 0x09,
                chUpCmd = 0x00,
                chDownCmd = 0x01,
                upCmd = 0x40,
                downCmd = 0x41,
                leftCmd = 0x07,
                rightCmd = 0x06,
                okCmd = 0x44,
                backCmd = 0x28,
                homeCmd = 0x7C,
                menuCmd = 0x43,
                digitCodes = mapOf(0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13, 4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19)
            ),
            UniversalIrBrand(
                id = "sony",
                name = "Sony Bravia",
                modelSeries = "XR OLED / Mini-LED / Google TV",
                protocol = IrEncodingProtocol.SONY_SIRC,
                carrierFrequencyHz = 40000,
                address = 0x01,
                powerCmd = 0x15,
                sourceCmd = 0x25,
                volUpCmd = 0x12,
                volDownCmd = 0x13,
                muteCmd = 0x14,
                chUpCmd = 0x10,
                chDownCmd = 0x11,
                upCmd = 0x74,
                downCmd = 0x75,
                leftCmd = 0x34,
                rightCmd = 0x33,
                okCmd = 0x65,
                backCmd = 0x62,
                homeCmd = 0x60,
                menuCmd = 0x60,
                digitCodes = mapOf(0 to 0x09, 1 to 0x00, 2 to 0x01, 3 to 0x02, 4 to 0x03, 5 to 0x04, 6 to 0x05, 7 to 0x06, 8 to 0x07, 9 to 0x08)
            ),
            UniversalIrBrand(
                id = "tcl",
                name = "TCL",
                modelSeries = "Roku TV / QLED Google TV Series",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x40,
                powerCmd = 0x12,
                sourceCmd = 0x30,
                volUpCmd = 0x1A,
                volDownCmd = 0x1E,
                muteCmd = 0x16,
                chUpCmd = 0x1B,
                chDownCmd = 0x1F,
                upCmd = 0x0B,
                downCmd = 0x0C,
                leftCmd = 0x0D,
                rightCmd = 0x0E,
                okCmd = 0x0F,
                backCmd = 0x15,
                homeCmd = 0x10,
                menuCmd = 0x14,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09)
            ),
            UniversalIrBrand(
                id = "hisense",
                name = "Hisense",
                modelSeries = "ULED / VIDAA / Android TV",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0xF7,
                powerCmd = 0x08,
                sourceCmd = 0x0F,
                volUpCmd = 0x0B,
                volDownCmd = 0x0E,
                muteCmd = 0x0A,
                chUpCmd = 0x0C,
                chDownCmd = 0x0D,
                upCmd = 0x16,
                downCmd = 0x17,
                leftCmd = 0x18,
                rightCmd = 0x19,
                okCmd = 0x1A,
                backCmd = 0x1B,
                homeCmd = 0x1C,
                menuCmd = 0x1D,
                digitCodes = mapOf(0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13, 4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19)
            ),
            UniversalIrBrand(
                id = "vizio",
                name = "Vizio",
                modelSeries = "SmartCast / M-Series Quantum",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x20,
                powerCmd = 0x12,
                sourceCmd = 0x0A,
                volUpCmd = 0x02,
                volDownCmd = 0x03,
                muteCmd = 0x09,
                chUpCmd = 0x00,
                chDownCmd = 0x01,
                upCmd = 0x45,
                downCmd = 0x46,
                leftCmd = 0x47,
                rightCmd = 0x48,
                okCmd = 0x44,
                backCmd = 0x28,
                homeCmd = 0x43,
                menuCmd = 0x4B,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09)
            ),
            UniversalIrBrand(
                id = "panasonic",
                name = "Panasonic",
                modelSeries = "Viera OLED / 4K Pro Studio",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x48,
                powerCmd = 0x3D,
                sourceCmd = 0x22,
                volUpCmd = 0x20,
                volDownCmd = 0x21,
                muteCmd = 0x32,
                chUpCmd = 0x34,
                chDownCmd = 0x35,
                upCmd = 0x4A,
                downCmd = 0x4B,
                leftCmd = 0x4E,
                rightCmd = 0x4F,
                okCmd = 0x49,
                backCmd = 0xD4,
                homeCmd = 0x9C,
                menuCmd = 0x52,
                digitCodes = mapOf(0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13, 4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19)
            ),
            UniversalIrBrand(
                id = "philips",
                name = "Philips",
                modelSeries = "Ambilight OLED / Android TV",
                protocol = IrEncodingProtocol.RC5,
                carrierFrequencyHz = 36000,
                address = 0x00,
                powerCmd = 0x0C,
                sourceCmd = 0x38,
                volUpCmd = 0x10,
                volDownCmd = 0x11,
                muteCmd = 0x0D,
                chUpCmd = 0x20,
                chDownCmd = 0x21,
                upCmd = 0x10,
                downCmd = 0x11,
                leftCmd = 0x15,
                rightCmd = 0x16,
                okCmd = 0x17,
                backCmd = 0x0A,
                homeCmd = 0x54,
                menuCmd = 0x12,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09)
            ),
            UniversalIrBrand(
                id = "sharp",
                name = "Sharp",
                modelSeries = "Aquos 4K UHD / Android TV",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x55,
                powerCmd = 0x14,
                sourceCmd = 0x1E,
                volUpCmd = 0x04,
                volDownCmd = 0x05,
                muteCmd = 0x0D,
                chUpCmd = 0x06,
                chDownCmd = 0x07,
                upCmd = 0x57,
                downCmd = 0x58,
                leftCmd = 0x59,
                rightCmd = 0x5A,
                okCmd = 0x52,
                backCmd = 0x45,
                homeCmd = 0x46,
                menuCmd = 0x1F,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09)
            ),
            UniversalIrBrand(
                id = "toshiba",
                name = "Toshiba",
                modelSeries = "Fire TV Edition / Smart TV",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x02,
                powerCmd = 0x08,
                sourceCmd = 0x0B,
                volUpCmd = 0x1A,
                volDownCmd = 0x1E,
                muteCmd = 0x12,
                chUpCmd = 0x1B,
                chDownCmd = 0x1F,
                upCmd = 0x40,
                downCmd = 0x41,
                leftCmd = 0x42,
                rightCmd = 0x43,
                okCmd = 0x21,
                backCmd = 0x22,
                homeCmd = 0x23,
                menuCmd = 0x14,
                digitCodes = mapOf(0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13, 4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19)
            )
        )

        fun resolveBrand(brandHint: String): UniversalIrBrand {
            val lower = brandHint.lowercase()
            return UNIVERSAL_BRANDS.firstOrNull {
                lower.contains(it.id) || lower.contains(it.name.lowercase())
            } ?: UNIVERSAL_BRANDS.first()
        }

        fun isHardwareAvailable(context: Context): Boolean {
            return try {
                val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
                manager?.hasIrEmitter() == true
            } catch (_: Exception) {
                false
            }
        }
    }

    private var activeBrand: UniversalIrBrand = resolveBrand(device.brand)
    private var rc5ToggleBit: Int = 0

    private val consumerIrManager: ConsumerIrManager? by lazy {
        try {
            context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (e: Exception) {
            Log.e(TAG, "ConsumerIrManager unavailable: ${e.message}")
            null
        }
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
        DeviceCapability.NUMERIC_KEYPAD,
        DeviceCapability.INPUT_SOURCE_SELECT
    )

    override val protocolLimitations: List<String> = listOf(
        "Requires a phone with a built-in hardware IR emitter. Devices without an IR diode cannot transmit optical pulses.",
        "One-way (simplex) optical transmission with no Wi-Fi feedback or installed-app listing from the TV.",
        "Keyboard text injection is unsupported over IR; use the numeric keypad or on-screen TV keyboard."
    )

    private val _connectionState = MutableStateFlow(
        if (isHardwareAvailable(context)) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
    )
    override val connectionState: StateFlow<DeviceOnlineStatus> = _connectionState.asStateFlow()

    fun setActiveBrand(brand: UniversalIrBrand) {
        activeBrand = brand
    }

    fun getActiveBrand(): UniversalIrBrand = activeBrand

    override suspend fun connect(): Boolean {
        val hasIr = isHardwareAvailable(context)
        _connectionState.value = if (hasIr) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
        return hasIr
    }

    override suspend fun disconnect() {}

    override suspend fun sendCommand(command: RemoteCommand): CommandResult {
        val manager = consumerIrManager
        if (manager == null || !manager.hasIrEmitter()) {
            _connectionState.value = DeviceOnlineStatus.OFFLINE
            return CommandResult.Failed("No hardware IR emitter detected on this device")
        }

        val brand = activeBrand
        val cmdCode = when (command) {
            RemoteCommand.Power, RemoteCommand.PowerOn, RemoteCommand.PowerOff -> brand.powerCmd
            RemoteCommand.SourceInput -> brand.sourceCmd
            RemoteCommand.VolumeUp -> brand.volUpCmd
            RemoteCommand.VolumeDown -> brand.volDownCmd
            RemoteCommand.Mute -> brand.muteCmd
            RemoteCommand.ChannelUp -> brand.chUpCmd
            RemoteCommand.ChannelDown -> brand.chDownCmd
            RemoteCommand.DpadUp -> brand.upCmd
            RemoteCommand.DpadDown -> brand.downCmd
            RemoteCommand.DpadLeft -> brand.leftCmd
            RemoteCommand.DpadRight -> brand.rightCmd
            RemoteCommand.Ok, RemoteCommand.Enter -> brand.okCmd
            RemoteCommand.Back -> brand.backCmd
            RemoteCommand.Home -> brand.homeCmd
            RemoteCommand.Menu -> brand.menuCmd
            is RemoteCommand.Digit -> brand.digitCodes[command.number] ?: (0x10 + command.number)
            else -> return CommandResult.UnsupportedByDevice
        }

        val pattern = when (brand.protocol) {
            IrEncodingProtocol.NEC -> buildNecPattern(brand.address, cmdCode)
            IrEncodingProtocol.SAMSUNG -> buildSamsungPattern(brand.address, cmdCode)
            IrEncodingProtocol.SONY_SIRC -> buildSonySircPattern(cmdCode, brand.address)
            IrEncodingProtocol.RC5 -> buildRc5Pattern(brand.address, cmdCode)
        }

        return transmitHardware(brand.carrierFrequencyHz, pattern)
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = emptyList()
    override suspend fun launchApp(packageNameOrId: String): Boolean = false

    private fun transmitHardware(freq: Int, pattern: IntArray): CommandResult {
        return try {
            consumerIrManager?.transmit(freq, pattern)
            CommandResult.Success
        } catch (e: Exception) {
            CommandResult.Failed("IR Pulse error: ${e.message}")
        }
    }

    private fun buildNecPattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(68)
        list.add(9000); list.add(4500)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((address.inv()) and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(command and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((command.inv()) and 0xFF, 8, 560, 1690, 560, list)
        list.add(560)
        return list.toIntArray()
    }

    private fun buildSamsungPattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(68)
        list.add(4500); list.add(4500)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(command and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((command.inv()) and 0xFF, 8, 560, 1690, 560, list)
        list.add(560)
        return list.toIntArray()
    }

    private fun buildSonySircPattern(command: Int, address: Int): IntArray {
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

    /**
     * Generates a valid RC-5 Manchester bi-phase pulse array (alternating mark, space).
     * Bit 1 = Space(889) -> Mark(889), Bit 0 = Mark(889) -> Space(889).
     * Coalesces consecutive identical levels cleanly for ConsumerIrManager.transmit().
     */
    private fun buildRc5Pattern(address: Int, command: Int): IntArray {
        val halfBit = 889
        val levels = mutableListOf<Boolean>() // true = IR LED ON (mark), false = OFF (space)
        val toggle = rc5ToggleBit
        rc5ToggleBit = rc5ToggleBit xor 1

        // 14 RC-5 bits: S1(1), S2(1), Toggle, 5-bit address, 6-bit command
        val frame14 = (1 shl 13) or (1 shl 12) or (toggle shl 11) or
            ((address and 0x1F) shl 6) or (command and 0x3F)

        for (i in 13 downTo 0) {
            val bit = (frame14 shr i) and 1
            if (bit == 1) {
                levels.add(false)
                levels.add(true)
            } else {
                levels.add(true)
                levels.add(false)
            }
        }

        // Convert logical level sequence into alternating mark/space durations starting with mark
        val firstMarkIdx = levels.indexOf(true)
        if (firstMarkIdx < 0) return intArrayOf(halfBit, halfBit)

        val durations = mutableListOf<Int>()
        var currentLevel = true
        var runCount = 0
        for (i in firstMarkIdx until levels.size) {
            if (levels[i] == currentLevel) {
                runCount++
            } else {
                durations.add(runCount * halfBit)
                currentLevel = levels[i]
                runCount = 1
            }
        }
        durations.add(runCount * halfBit)
        if (durations.size % 2 != 0) durations.add(halfBit)
        return durations.toIntArray()
    }

    private fun encodeBits(data: Int, bits: Int, mark: Int, oneSpace: Int, zeroSpace: Int, list: MutableList<Int>) {
        for (i in 0 until bits) {
            val bit = (data shr i) and 1
            list.add(mark)
            list.add(if (bit == 1) oneSpace else zeroSpace)
        }
    }
}
