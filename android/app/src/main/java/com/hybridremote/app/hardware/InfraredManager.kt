package com.hybridremote.app.hardware

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.util.Log

/**
 * Supported IR Encoding Protocols for Universal Television control.
 */
enum class IrProtocol {
    NEC,
    SAMSUNG,
    SONY_SIRC,
    RC5
}

/**
 * Hardware configuration profile for a specific TV brand and model series.
 */
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
 * Hardware wrapper for Android's ConsumerIrManager.
 * Operates as a Universal Television Remote across all major TV brands & models
 * (Samsung, LG, Sony, TCL, Hisense, Panasonic, Vizio, Philips, Sharp, Toshiba).
 *
 * Implements hardware pulse generation for NEC, Samsung, Sony SIRC (40kHz), and RC5 protocols.
 */
class InfraredManager(private val context: Context) {

    companion object {
        private const val TAG = "InfraredManager"

        // Default frequencies
        const val DEFAULT_CARRIER_FREQUENCY_HZ = 38000 // 38 kHz
        const val SONY_CARRIER_FREQUENCY_HZ = 40000    // 40 kHz
        const val RC5_CARRIER_FREQUENCY_HZ = 36000     // 36 kHz

        // Universal TV Brand Database
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
                description = "Sony SIRC 40kHz 12-bit / 15-bit pulse width modulation"
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
                digitCodes = mapOf(
                    0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03,
                    4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09
                ),
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
                digitCodes = mapOf(
                    0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13,
                    4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19
                ),
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
                digitCodes = mapOf(
                    0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03,
                    4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09
                ),
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
                digitCodes = mapOf(
                    0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13,
                    4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19
                ),
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
                digitCodes = mapOf(
                    0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03,
                    4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09
                ),
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
                digitCodes = mapOf(
                    0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03,
                    4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09
                ),
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
                digitCodes = mapOf(
                    0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13,
                    4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19
                ),
                description = "Toshiba TV universal encoding"
            )
        )
    }

    private var activeBrand: TvBrandProfile = UNIVERSAL_BRAND_DATABASE[0] // Default Samsung

    private val consumerIrManager: ConsumerIrManager? by lazy {
        try {
            context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ConsumerIrManager: ${e.message}", e)
            null
        }
    }

    /**
     * Returns the full catalog of pre-configured Universal TV brand profiles.
     */
    fun getSupportedBrands(): List<TvBrandProfile> = UNIVERSAL_BRAND_DATABASE

    /**
     * Gets the currently active TV Brand profile.
     */
    fun getActiveBrand(): TvBrandProfile = activeBrand

    /**
     * Switches the active TV Brand profile to match the user's specific television.
     */
    fun selectBrand(brandId: String): Boolean {
        val found = UNIVERSAL_BRAND_DATABASE.find { it.id.equals(brandId, ignoreCase = true) }
        return if (found != null) {
            activeBrand = found
            Log.i(TAG, "Switched active Universal TV Brand to: ${found.name} (${found.protocol}, ${found.carrierFrequencyHz}Hz)")
            true
        } else {
            Log.w(TAG, "selectBrand: Unknown brand ID '$brandId'")
            false
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
            Log.e(TAG, "Exception querying hasIrEmitter: ${e.message}", e)
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
            manager.carrierFrequencies ?: emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying carrier frequencies: ${e.message}", e)
            emptyArray()
        }
    }

    fun isFrequencySupported(frequency: Int): Boolean {
        val ranges = getCarrierFrequencies()
        if (ranges.isEmpty()) return true
        return ranges.any { frequency in it.minFrequency..it.maxFrequency }
    }

    /**
     * Pulses hardware IR commands at the specified frequency and alternating mark/space pattern.
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

        return try {
            Log.i(TAG, "Transmitting IR pulse: ${activeBrand.name} - $frequency Hz, ${pattern.size} pulses")
            manager.transmit(frequency, pattern)
            true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Hardware driver rejected IR pattern structure: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during IR pulse transmission: ${e.message}", e)
            false
        }
    }

    // High-Level Universal TV Command Transmitters (Dispatches according to active brand profile)

    fun transmitPower(): Boolean {
        return transmitBrandCommand(activeBrand.powerCmd, "POWER")
    }

    fun transmitSource(): Boolean {
        return transmitBrandCommand(activeBrand.sourceCmd, "INPUT/SOURCE")
    }

    fun transmitVolumeUp(): Boolean {
        return transmitBrandCommand(activeBrand.volUpCmd, "VOL_UP")
    }

    fun transmitVolumeDown(): Boolean {
        return transmitBrandCommand(activeBrand.volDownCmd, "VOL_DOWN")
    }

    fun transmitMute(): Boolean {
        return transmitBrandCommand(activeBrand.muteCmd, "MUTE")
    }

    fun transmitDigit(digit: Int): Boolean {
        val cmd = activeBrand.digitCodes[digit] ?: run {
            Log.w(TAG, "No specific digit code mapped for $digit on brand ${activeBrand.name}. Using default.")
            0x10 + digit
        }
        return transmitBrandCommand(cmd, "DIGIT_$digit")
    }

    private fun transmitBrandCommand(command: Int, label: String): Boolean {
        val brand = activeBrand
        val pattern = when (brand.protocol) {
            IrProtocol.NEC -> buildNecPattern(brand.address, command)
            IrProtocol.SAMSUNG -> buildSamsungPattern(brand.address, command)
            IrProtocol.SONY_SIRC -> buildSonySircPattern(command, brand.address)
            IrProtocol.RC5 -> buildRc5Pattern(brand.address, command)
        }
        Log.d(TAG, "Transmitting $label for ${brand.name} [${brand.protocol}]")
        return transmitCode(brand.carrierFrequencyHz, pattern)
    }

    /**
     * NEC Protocol: 9000µs mark + 4500µs space, 32-bit payload [addr][~addr][cmd][~cmd], 560µs stop mark.
     */
    fun buildNecPattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(68)
        list.add(9000)
        list.add(4500)

        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((address.inv()) and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(command and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((command.inv()) and 0xFF, 8, 560, 1690, 560, list)

        list.add(560)
        return list.toIntArray()
    }

    /**
     * Samsung Protocol: 4500µs mark + 4500µs space leader, 32-bit payload.
     */
    fun buildSamsungPattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(68)
        list.add(4500)
        list.add(4500)

        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(command and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((command.inv()) and 0xFF, 8, 560, 1690, 560, list)

        list.add(560)
        return list.toIntArray()
    }

    /**
     * Sony SIRC Protocol (40kHz): 2400µs leader mark + 600µs space, pulse-width modulated (1 = 1200µs, 0 = 600µs).
     */
    fun buildSonySircPattern(command: Int, address: Int, numBits: Int = 12): IntArray {
        val list = ArrayList<Int>(numBits * 2 + 2)
        list.add(2400)
        list.add(600)

        // 7-bit command
        for (i in 0 until 7) {
            val bit = (command shr i) and 1
            list.add(if (bit == 1) 1200 else 600)
            list.add(600)
        }

        // 5-bit address
        for (i in 0 until 5) {
            val bit = (address shr i) and 1
            list.add(if (bit == 1) 1200 else 600)
            list.add(600)
        }

        return list.toIntArray()
    }

    /**
     * Philips RC-5 Protocol (36kHz): Bi-phase Manchester modulation (889µs half-bit intervals).
     */
    fun buildRc5Pattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(28)
        val bitTime = 889

        // Start bit 1
        list.add(bitTime)
        list.add(bitTime)
        // Start bit 2
        list.add(bitTime)
        list.add(bitTime)
        // Toggle bit
        list.add(bitTime)
        list.add(bitTime)

        // 5-bit Address + 6-bit Command
        val fullData = ((address and 0x1F) shl 6) or (command and 0x3F)
        for (i in 10 downTo 0) {
            val bit = (fullData shr i) and 1
            if (bit == 1) {
                list.add(bitTime)
                list.add(bitTime)
            } else {
                list.add(bitTime)
                list.add(bitTime)
            }
        }
        return list.toIntArray()
    }

    private fun encodeBits(
        data: Int,
        numBits: Int,
        markTime: Int,
        oneSpaceTime: Int,
        zeroSpaceTime: Int,
        list: MutableList<Int>
    ) {
        for (i in 0 until numBits) {
            val bit = (data shr i) and 1
            list.add(markTime)
            list.add(if (bit == 1) oneSpaceTime else zeroSpaceTime)
        }
    }
}
