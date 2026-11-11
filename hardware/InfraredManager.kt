package com.hybridremote.app.hardware

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
            Log.e(TAG, "Failed to initialize ConsumerIrManager: ${e.message}", e)
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
            val ranges = manager.carrierFrequencies ?: emptyArray()
            ranges.forEach { range ->
                Log.d(TAG, "Supported IR Range: ${range.minFrequency} Hz - ${range.maxFrequency} Hz")
            }
            ranges
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying carrier frequencies: ${e.message}", e)
            emptyArray()
        }
    }

    /**
     * Validates whether a specified frequency is within hardware tolerances.
     */
    fun isFrequencySupported(frequency: Int): Boolean {
        val ranges = getCarrierFrequencies()
        if (ranges.isEmpty()) return true // Some vendors return empty ranges but support 38kHz

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
            Log.i(TAG, "Transmitting IR pulse: $frequency Hz, ${pattern.size} pulses")
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
     * - 8-bit Address
     * - 8-bit Inverted Address (~Address)
     * - 8-bit Command
     * - 8-bit Inverted Command (~Command)
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
}
