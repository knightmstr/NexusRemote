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
    NEC_EXT,
    SAMSUNG,
    SONY_SIRC,
    RC5,
    RC6,
    PANASONIC,
    RCA
}

data class UniversalIrBrand(
    val id: String,
    val name: String,
    val modelSeries: String,
    val protocol: IrEncodingProtocol,
    val carrierFrequencyHz: Int,
    val address: Int,
    val subAddress: Int = 0,
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
    val digitCodes: Map<Int, Int>,
    val source: String = ""
)

/**
 * Universal Hardware Consumer IR Adapter utilizing Android's ConsumerIrManager.
 * Profiles are verified against probonopd/irdb and Lucaslhm/Flipper-IRDB across
 * NEC, NEC_EXT (16-bit), Samsung32, Sony SIRC, Philips RC-5 & RC-6, Panasonic Kaseikyo, and RCA-38.
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
                subAddress = 0x07,
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
                digitCodes = mapOf(0 to 0x11, 1 to 0x04, 2 to 0x05, 3 to 0x06, 4 to 0x08, 5 to 0x09, 6 to 0x0A, 7 to 0x0C, 8 to 0x0D, 9 to 0x0E),
                source = "probonopd/irdb · Samsung/TV/7,7.csv"
            ),
            UniversalIrBrand(
                id = "lg",
                name = "LG Electronics",
                modelSeries = "webOS OLED C3 / QNED / NanoCell",
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
                digitCodes = mapOf(0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13, 4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19),
                source = "probonopd/irdb · LG/4,-1.csv & Flipper-IRDB OLED C3"
            ),
            UniversalIrBrand(
                id = "sony",
                name = "Sony Bravia",
                modelSeries = "XR OLED / X80K / Google TV",
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
                digitCodes = mapOf(0 to 0x09, 1 to 0x00, 2 to 0x01, 3 to 0x02, 4 to 0x03, 5 to 0x04, 6 to 0x05, 7 to 0x06, 8 to 0x07, 9 to 0x08),
                source = "probonopd/irdb · Sony/1,-1.csv & Flipper-IRDB KD-55X80CK"
            ),
            UniversalIrBrand(
                id = "roku_tv",
                name = "Roku TV Universal",
                modelSeries = "TCL / Hisense / Onn / RCA Roku OS",
                protocol = IrEncodingProtocol.NEC_EXT,
                carrierFrequencyHz = 38000,
                address = 0xEA,
                subAddress = 0xC7,
                powerCmd = 0x17,
                sourceCmd = 0x03,
                volUpCmd = 0x0F,
                volDownCmd = 0x10,
                muteCmd = 0x20,
                chUpCmd = 0x19,
                chDownCmd = 0x33,
                upCmd = 0x19,
                downCmd = 0x33,
                leftCmd = 0x1E,
                rightCmd = 0x2D,
                okCmd = 0x2A,
                backCmd = 0x66,
                homeCmd = 0x03,
                menuCmd = 0x61,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · TCL_32S327 & Onn_Roku_TV.ir"
            ),
            UniversalIrBrand(
                id = "tcl_google",
                name = "TCL Google TV",
                modelSeries = "QLED C635 / S4 / QM8 Android TV",
                protocol = IrEncodingProtocol.RCA,
                carrierFrequencyHz = 38000,
                address = 0x0F,
                powerCmd = 0x2A,
                sourceCmd = 0xA3,
                volUpCmd = 0x2F,
                volDownCmd = 0x2E,
                muteCmd = 0x3F,
                chUpCmd = 0x2D,
                chDownCmd = 0x2C,
                upCmd = 0x59,
                downCmd = 0x58,
                leftCmd = 0x56,
                rightCmd = 0x57,
                okCmd = 0xF4,
                backCmd = 0x27,
                homeCmd = 0x08,
                menuCmd = 0x08,
                digitCodes = mapOf(0 to 0x30, 1 to 0x31, 2 to 0x32, 3 to 0x33, 4 to 0x34, 5 to 0x35, 6 to 0x36, 7 to 0x37, 8 to 0x38, 9 to 0x39),
                source = "probonopd/irdb · TCL/15,-1.csv & Flipper-IRDB TCL_65C635K"
            ),
            UniversalIrBrand(
                id = "hisense_vidaa",
                name = "Hisense VIDAA",
                modelSeries = "E7K / A4H / ULED Smart TV",
                protocol = IrEncodingProtocol.NEC_EXT,
                carrierFrequencyHz = 38000,
                address = 0x00,
                subAddress = 0xBF,
                powerCmd = 0x0D,
                sourceCmd = 0x12,
                volUpCmd = 0x44,
                volDownCmd = 0x43,
                muteCmd = 0x0E,
                chUpCmd = 0x4A,
                chDownCmd = 0x4B,
                upCmd = 0x16,
                downCmd = 0x17,
                leftCmd = 0x19,
                rightCmd = 0x18,
                okCmd = 0x15,
                backCmd = 0x48,
                homeCmd = 0x20,
                menuCmd = 0x14,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · Hisense_55E7KQ & 32A4HAU.ir"
            ),
            UniversalIrBrand(
                id = "hisense_google",
                name = "Hisense Google TV",
                modelSeries = "U6K / U7G / U8K Mini-LED",
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
                upCmd = 0x56,
                downCmd = 0x57,
                leftCmd = 0x58,
                rightCmd = 0x59,
                okCmd = 0x5A,
                backCmd = 0x04,
                homeCmd = 0x4A,
                menuCmd = 0x43,
                digitCodes = mapOf(0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13, 4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19),
                source = "Flipper-IRDB · Hisense_55U6K & Hisense_U7G.ir"
            ),
            UniversalIrBrand(
                id = "vizio",
                name = "Vizio SmartCast",
                modelSeries = "M-Series Quantum / V-Series / P-Series",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x04,
                powerCmd = 0x08,
                sourceCmd = 0x2F,
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
                backCmd = 0x4A,
                homeCmd = 0x2D,
                menuCmd = 0x4F,
                digitCodes = mapOf(0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13, 4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19),
                source = "probonopd/irdb · Vizio/4,-1.csv & Flipper-IRDB Vizio.ir"
            ),
            UniversalIrBrand(
                id = "panasonic",
                name = "Panasonic Viera",
                modelSeries = "JX800 / OLED / 4K Pro Studio",
                protocol = IrEncodingProtocol.PANASONIC,
                carrierFrequencyHz = 37000,
                address = 0x80,
                subAddress = 0x00,
                powerCmd = 0x3D,
                sourceCmd = 0x05,
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
                homeCmd = 0x52,
                menuCmd = 0x52,
                digitCodes = mapOf(0 to 0x19, 1 to 0x10, 2 to 0x11, 3 to 0x12, 4 to 0x13, 5 to 0x14, 6 to 0x15, 7 to 0x16, 8 to 0x17, 9 to 0x18),
                source = "probonopd/irdb · Panasonic/128,0.csv & Flipper-IRDB"
            ),
            UniversalIrBrand(
                id = "philips_rc6",
                name = "Philips OLED / Android",
                modelSeries = "Ambilight 806 OLED / PUS Android TV",
                protocol = IrEncodingProtocol.RC6,
                carrierFrequencyHz = 36000,
                address = 0x00,
                powerCmd = 0x0C,
                sourceCmd = 0x38,
                volUpCmd = 0x10,
                volDownCmd = 0x11,
                muteCmd = 0x0D,
                chUpCmd = 0x20,
                chDownCmd = 0x21,
                upCmd = 0x58,
                downCmd = 0x59,
                leftCmd = 0x5A,
                rightCmd = 0x5B,
                okCmd = 0x5C,
                backCmd = 0x0A,
                homeCmd = 0x54,
                menuCmd = 0x57,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · Philips_TV_48OLED806 & Universal.ir"
            ),
            UniversalIrBrand(
                id = "philips",
                name = "Philips Classic",
                modelSeries = "PFL Smart LED / Legacy Ambilight",
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
                upCmd = 0x1C,
                downCmd = 0x1D,
                leftCmd = 0x2C,
                rightCmd = 0x2B,
                okCmd = 0x2E,
                backCmd = 0x0F,
                homeCmd = 0x2E,
                menuCmd = 0x2E,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "probonopd/irdb · Philips/TV/0,-1.csv"
            ),
            UniversalIrBrand(
                id = "amazon_firetv",
                name = "Amazon Fire TV",
                modelSeries = "Omni Series 4K / 4-Series / Insignia Fire TV",
                protocol = IrEncodingProtocol.NEC_EXT,
                carrierFrequencyHz = 38000,
                address = 0x02,
                subAddress = 0x7D,
                powerCmd = 0x46,
                sourceCmd = 0x4B,
                volUpCmd = 0x0C,
                volDownCmd = 0x19,
                muteCmd = 0x4C,
                chUpCmd = 0x0F,
                chDownCmd = 0x5A,
                upCmd = 0x48,
                downCmd = 0x4D,
                leftCmd = 0x4E,
                rightCmd = 0x49,
                okCmd = 0x4A,
                backCmd = 0x0D,
                homeCmd = 0x9F,
                menuCmd = 0x45,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · FireTV_Omni_Series_4K.ir"
            ),
            UniversalIrBrand(
                id = "toshiba_firetv",
                name = "Toshiba Fire TV",
                modelSeries = "C350 / LF421U Fire TV Edition",
                protocol = IrEncodingProtocol.NEC_EXT,
                carrierFrequencyHz = 38000,
                address = 0x02,
                subAddress = 0x7D,
                powerCmd = 0x46,
                sourceCmd = 0x4B,
                volUpCmd = 0x0C,
                volDownCmd = 0x19,
                muteCmd = 0x4C,
                chUpCmd = 0x0F,
                chDownCmd = 0x5A,
                upCmd = 0x48,
                downCmd = 0x4D,
                leftCmd = 0x4E,
                rightCmd = 0x49,
                okCmd = 0x4A,
                backCmd = 0x0D,
                homeCmd = 0x9F,
                menuCmd = 0x45,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · Toshiba_50C350LC & 43LF421U21.ir"
            ),
            UniversalIrBrand(
                id = "toshiba",
                name = "Toshiba Regza",
                modelSeries = "AV / L-Series Standard HDTV",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x40,
                powerCmd = 0x12,
                sourceCmd = 0x0F,
                volUpCmd = 0x1A,
                volDownCmd = 0x1E,
                muteCmd = 0x10,
                chUpCmd = 0x1B,
                chDownCmd = 0x1F,
                upCmd = 0x1B,
                downCmd = 0x1F,
                leftCmd = 0x1E,
                rightCmd = 0x1A,
                okCmd = 0x17,
                backCmd = 0x58,
                homeCmd = 0x80,
                menuCmd = 0x80,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "probonopd/irdb · Toshiba/64,-1.csv & 32AV502U.ir"
            ),
            UniversalIrBrand(
                id = "sharp",
                name = "Sharp Aquos",
                modelSeries = "4K UHD / BG3E / Smart LED",
                protocol = IrEncodingProtocol.NEC_EXT,
                carrierFrequencyHz = 38000,
                address = 0x00,
                subAddress = 0x7F,
                powerCmd = 0x0A,
                sourceCmd = 0x53,
                volUpCmd = 0x1E,
                volDownCmd = 0x5F,
                muteCmd = 0x50,
                chUpCmd = 0x1F,
                chDownCmd = 0x5C,
                upCmd = 0x5E,
                downCmd = 0x56,
                leftCmd = 0x5B,
                rightCmd = 0x58,
                okCmd = 0x5A,
                backCmd = 0x4E,
                homeCmd = 0x57,
                menuCmd = 0x57,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · Sharp/Aquos.ir & 32BG3E.ir"
            ),
            UniversalIrBrand(
                id = "insignia",
                name = "Insignia / Dynex",
                modelSeries = "NS-Series / DX-Series LED HDTV",
                protocol = IrEncodingProtocol.NEC_EXT,
                carrierFrequencyHz = 38000,
                address = 0x86,
                subAddress = 0x05,
                powerCmd = 0x0F,
                sourceCmd = 0x1D,
                volUpCmd = 0x0C,
                volDownCmd = 0x0D,
                muteCmd = 0x0E,
                chUpCmd = 0x0A,
                chDownCmd = 0x0B,
                upCmd = 0x42,
                downCmd = 0x43,
                leftCmd = 0x16,
                rightCmd = 0x15,
                okCmd = 0x18,
                backCmd = 0x1B,
                homeCmd = 0x14,
                menuCmd = 0x14,
                digitCodes = mapOf(0 to 0x09, 1 to 0x00, 2 to 0x01, 3 to 0x02, 4 to 0x03, 5 to 0x04, 6 to 0x05, 7 to 0x06, 8 to 0x07, 9 to 0x08),
                source = "probonopd/irdb · Insignia/134,5.csv & Flipper-IRDB"
            ),
            UniversalIrBrand(
                id = "element",
                name = "Element / Westinghouse",
                modelSeries = "100-Series / RMT Smart LED TV",
                protocol = IrEncodingProtocol.NEC_EXT,
                carrierFrequencyHz = 38000,
                address = 0x02,
                subAddress = 0x7D,
                powerCmd = 0x46,
                sourceCmd = 0x4B,
                volUpCmd = 0x0C,
                volDownCmd = 0x19,
                muteCmd = 0x4C,
                chUpCmd = 0x0F,
                chDownCmd = 0x5A,
                upCmd = 0x48,
                downCmd = 0x4D,
                leftCmd = 0x4E,
                rightCmd = 0x49,
                okCmd = 0x4A,
                backCmd = 0x0D,
                homeCmd = 0x45,
                menuCmd = 0x45,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · Element_100-Series & Westinghouse.ir"
            ),
            UniversalIrBrand(
                id = "sanyo",
                name = "Sanyo",
                modelSeries = "DP / FW Series LCD & LED TV",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x38,
                powerCmd = 0x12,
                sourceCmd = 0x13,
                volUpCmd = 0x0E,
                volDownCmd = 0x0F,
                muteCmd = 0x18,
                chUpCmd = 0x0A,
                chDownCmd = 0x0B,
                upCmd = 0x0A,
                downCmd = 0x0B,
                leftCmd = 0x0F,
                rightCmd = 0x0E,
                okCmd = 0x17,
                backCmd = 0x19,
                homeCmd = 0x17,
                menuCmd = 0x17,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "probonopd/irdb · Sanyo/56,-1.csv & Sanyo_DP26640.ir"
            ),
            UniversalIrBrand(
                id = "rca",
                name = "RCA Classic",
                modelSeries = "CRK / Classic HD & LED Series",
                protocol = IrEncodingProtocol.RCA,
                carrierFrequencyHz = 38000,
                address = 0x0F,
                powerCmd = 0x2A,
                sourceCmd = 0xA3,
                volUpCmd = 0x2F,
                volDownCmd = 0x2E,
                muteCmd = 0x3F,
                chUpCmd = 0x2D,
                chDownCmd = 0x2C,
                upCmd = 0x59,
                downCmd = 0x58,
                leftCmd = 0x56,
                rightCmd = 0x57,
                okCmd = 0xF4,
                backCmd = 0x27,
                homeCmd = 0x08,
                menuCmd = 0x08,
                digitCodes = mapOf(0 to 0x30, 1 to 0x31, 2 to 0x32, 3 to 0x33, 4 to 0x34, 5 to 0x35, 6 to 0x36, 7 to 0x37, 8 to 0x38, 9 to 0x39),
                source = "probonopd/irdb · RCA/TV/15,-1.csv"
            ),
            UniversalIrBrand(
                id = "rca_led",
                name = "RCA LED (NEC)",
                modelSeries = "RLDED Series Budget LED TV",
                protocol = IrEncodingProtocol.NEC,
                carrierFrequencyHz = 38000,
                address = 0x20,
                powerCmd = 0x52,
                sourceCmd = 0x40,
                volUpCmd = 0x03,
                volDownCmd = 0x41,
                muteCmd = 0x09,
                chUpCmd = 0x02,
                chDownCmd = 0x09,
                upCmd = 0x47,
                downCmd = 0x4D,
                leftCmd = 0x49,
                rightCmd = 0x4B,
                okCmd = 0x4A,
                backCmd = 0x0A,
                homeCmd = 0x06,
                menuCmd = 0x06,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · RCA_RLDED3258A-B.ir"
            ),
            UniversalIrBrand(
                id = "hitachi",
                name = "Hitachi / JVC Smart",
                modelSeries = "RC43140 / Vestel / 4K Smart TV",
                protocol = IrEncodingProtocol.RC5,
                carrierFrequencyHz = 36000,
                address = 0x03,
                powerCmd = 0x0C,
                sourceCmd = 0x38,
                volUpCmd = 0x10,
                volDownCmd = 0x11,
                muteCmd = 0x0D,
                chUpCmd = 0x20,
                chDownCmd = 0x21,
                upCmd = 0x14,
                downCmd = 0x13,
                leftCmd = 0x15,
                rightCmd = 0x16,
                okCmd = 0x35,
                backCmd = 0x0A,
                homeCmd = 0x30,
                menuCmd = 0x30,
                digitCodes = mapOf(0 to 0x00, 1 to 0x01, 2 to 0x02, 3 to 0x03, 4 to 0x04, 5 to 0x05, 6 to 0x06, 7 to 0x07, 8 to 0x08, 9 to 0x09),
                source = "Flipper-IRDB · Hitachi_RC43140.ir"
            ),
            UniversalIrBrand(
                id = "sceptre",
                name = "Sceptre",
                modelSeries = "X50 / U-Series 4K UHD LED",
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
                okCmd = 0x0B,
                backCmd = 0x16,
                homeCmd = 0x60,
                menuCmd = 0x60,
                digitCodes = mapOf(0 to 0x09, 1 to 0x00, 2 to 0x01, 3 to 0x02, 4 to 0x03, 5 to 0x04, 6 to 0x05, 7 to 0x06, 8 to 0x07, 9 to 0x08),
                source = "Flipper-IRDB · Sceptre_X50_Series.ir"
            ),
            UniversalIrBrand(
                id = "haier",
                name = "Haier",
                modelSeries = "L42 / Smart Android LED TV",
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
                upCmd = 0x00,
                downCmd = 0x01,
                leftCmd = 0x03,
                rightCmd = 0x02,
                okCmd = 0x44,
                backCmd = 0x5B,
                homeCmd = 0x43,
                menuCmd = 0x43,
                digitCodes = mapOf(0 to 0x10, 1 to 0x11, 2 to 0x12, 3 to 0x13, 4 to 0x14, 5 to 0x15, 6 to 0x16, 7 to 0x17, 8 to 0x18, 9 to 0x19),
                source = "Flipper-IRDB · Haier_L42C1180.ir"
            )
        )

        fun resolveBrand(brandHint: String): UniversalIrBrand {
            val lower = brandHint.trim().lowercase()
            if (lower.isEmpty()) return UNIVERSAL_BRANDS.first()
            return UNIVERSAL_BRANDS.firstOrNull { it.id == lower }
                ?: UNIVERSAL_BRANDS.firstOrNull { lower.contains(it.name.lowercase()) }
                ?: UNIVERSAL_BRANDS.firstOrNull {
                    lower.contains(it.id.substringBefore('_')) ||
                        it.modelSeries.lowercase().contains(lower)
                }
                ?: UNIVERSAL_BRANDS.first()
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
    private var rc6ToggleBit: Int = 0

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
            IrEncodingProtocol.NEC_EXT -> buildNecExtPattern(brand.address, brand.subAddress, cmdCode)
            IrEncodingProtocol.SAMSUNG -> buildSamsungPattern(brand.address, brand.subAddress, cmdCode)
            IrEncodingProtocol.SONY_SIRC -> buildSonySircPattern(cmdCode, brand.address)
            IrEncodingProtocol.RC5 -> buildRc5Pattern(brand.address, cmdCode)
            IrEncodingProtocol.RC6 -> buildRc6Pattern(brand.address, cmdCode)
            IrEncodingProtocol.PANASONIC -> buildPanasonicKaseikyoPattern(brand.address, brand.subAddress, cmdCode)
            IrEncodingProtocol.RCA -> buildRcaPattern(brand.address, cmdCode)
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

    private fun buildNecExtPattern(address: Int, subAddress: Int, command: Int): IntArray {
        val list = ArrayList<Int>(68)
        list.add(9000); list.add(4500)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(subAddress and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(command and 0xFF, 8, 560, 1690, 560, list)
        encodeBits((command.inv()) and 0xFF, 8, 560, 1690, 560, list)
        list.add(560)
        return list.toIntArray()
    }

    private fun buildSamsungPattern(address: Int, subAddress: Int, command: Int): IntArray {
        val list = ArrayList<Int>(68)
        list.add(4500); list.add(4500)
        encodeBits(address and 0xFF, 8, 560, 1690, 560, list)
        encodeBits(subAddress and 0xFF, 8, 560, 1690, 560, list)
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
     * Generates a Panasonic Kaseikyo 48-bit pattern at 37 kHz.
     * Frame: 3456/1728 leader + OEM (0x02, 0x20) + Device + Subdevice + Command + (D^S^F) LSB-first.
     */
    private fun buildPanasonicKaseikyoPattern(address: Int, subAddress: Int, command: Int): IntArray {
        val d = address and 0xFF
        val s = subAddress and 0xFF
        val f = command and 0xFF
        val checksum = d xor s xor f
        val list = ArrayList<Int>(100)
        list.add(3456); list.add(1728)
        encodeBits(0x02, 8, 432, 1296, 432, list)
        encodeBits(0x20, 8, 432, 1296, 432, list)
        encodeBits(d, 8, 432, 1296, 432, list)
        encodeBits(s, 8, 432, 1296, 432, list)
        encodeBits(f, 8, 432, 1296, 432, list)
        encodeBits(checksum, 8, 432, 1296, 432, list)
        list.add(432)
        return list.toIntArray()
    }

    /**
     * Generates an RCA-38 pulse-distance pattern at 38 kHz.
     * Frame: 4000/4000 leader + 4-bit addr + 8-bit cmd + 4-bit ~addr + 8-bit ~cmd (MSB-first).
     */
    private fun buildRcaPattern(address: Int, command: Int): IntArray {
        val list = ArrayList<Int>(52)
        list.add(4000); list.add(4000)
        encodeBitsMsb(address and 0x0F, 4, 500, 2000, 1000, list)
        encodeBitsMsb(command and 0xFF, 8, 500, 2000, 1000, list)
        encodeBitsMsb((address.inv()) and 0x0F, 4, 500, 2000, 1000, list)
        encodeBitsMsb((command.inv()) and 0xFF, 8, 500, 2000, 1000, list)
        list.add(500)
        return list.toIntArray()
    }

    /**
     * Generates a valid RC-5 Manchester bi-phase pulse array (alternating mark, space).
     * Bit 1 = Space(889) -> Mark(889), Bit 0 = Mark(889) -> Space(889).
     * Coalesces consecutive identical levels cleanly for ConsumerIrManager.transmit().
     */
    private fun buildRc5Pattern(address: Int, command: Int): IntArray {
        val halfBit = 889
        val levels = mutableListOf<Boolean>()
        val toggle = rc5ToggleBit
        rc5ToggleBit = rc5ToggleBit xor 1

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

        return coalesceLevelsToDurations(levels, halfBit)
    }

    /**
     * Generates a Philips RC-6 Mode 0 Manchester pattern at 36 kHz (base unit T = 444 us).
     * Frame: 6T Mark / 2T Space leader + Start(1) + Mode(000) + double-width Trailer + 8-bit Addr + 8-bit Cmd (MSB).
     */
    private fun buildRc6Pattern(address: Int, command: Int): IntArray {
        val unit = 444
        val levels = mutableListOf<Boolean>()
        val toggle = rc6ToggleBit
        rc6ToggleBit = rc6ToggleBit xor 1

        // Leader: 6T mark, 2T space
        repeat(6) { levels.add(true) }
        repeat(2) { levels.add(false) }

        // Start bit (1) + Mode 0 (0, 0, 0): RC-6 bit 1 = Mark->Space, bit 0 = Space->Mark
        levels.add(true); levels.add(false) // Start = 1
        repeat(3) {
            levels.add(false); levels.add(true) // Mode = 000
        }

        // Trailer bit: double-width (2T per half-bit)
        if (toggle == 1) {
            repeat(2) { levels.add(true) }
            repeat(2) { levels.add(false) }
        } else {
            repeat(2) { levels.add(false) }
            repeat(2) { levels.add(true) }
        }

        // 8-bit Address (MSB-first) + 8-bit Command (MSB-first)
        for (i in 7 downTo 0) {
            val bit = (address shr i) and 1
            if (bit == 1) {
                levels.add(true); levels.add(false)
            } else {
                levels.add(false); levels.add(true)
            }
        }
        for (i in 7 downTo 0) {
            val bit = (command shr i) and 1
            if (bit == 1) {
                levels.add(true); levels.add(false)
            } else {
                levels.add(false); levels.add(true)
            }
        }

        return coalesceLevelsToDurations(levels, unit)
    }

    private fun coalesceLevelsToDurations(levels: List<Boolean>, tickUs: Int): IntArray {
        val firstMarkIdx = levels.indexOf(true)
        if (firstMarkIdx < 0) return intArrayOf(tickUs, tickUs)

        val durations = mutableListOf<Int>()
        var currentLevel = true
        var runCount = 0
        for (i in firstMarkIdx until levels.size) {
            if (levels[i] == currentLevel) {
                runCount++
            } else {
                durations.add(runCount * tickUs)
                currentLevel = levels[i]
                runCount = 1
            }
        }
        durations.add(runCount * tickUs)
        if (durations.size % 2 != 0) durations.add(tickUs)
        return durations.toIntArray()
    }

    private fun encodeBits(data: Int, bits: Int, mark: Int, oneSpace: Int, zeroSpace: Int, list: MutableList<Int>) {
        for (i in 0 until bits) {
            val bit = (data shr i) and 1
            list.add(mark)
            list.add(if (bit == 1) oneSpace else zeroSpace)
        }
    }

    private fun encodeBitsMsb(data: Int, bits: Int, mark: Int, oneSpace: Int, zeroSpace: Int, list: MutableList<Int>) {
        for (i in (bits - 1) downTo 0) {
            val bit = (data shr i) and 1
            list.add(mark)
            list.add(if (bit == 1) oneSpace else zeroSpace)
        }
    }
}
