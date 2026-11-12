package com.nexusremote.protocols

import android.content.Context
import com.nexusremote.domain.model.ProtocolType
import com.nexusremote.domain.model.SavedDevice
import com.nexusremote.security.KeystoreSecretManager

/**
 * Protocol Registry Factory.
 * Resolves the appropriate independent protocol adapter instance for any target SavedDevice.
 */
class ProtocolRegistry(
    private val context: Context,
    private val keystoreSecretManager: KeystoreSecretManager
) {

    fun createController(device: SavedDevice): DeviceController {
        return when (device.protocolType) {
            ProtocolType.ADB_NETWORK -> AdbProtocolAdapter(device, keystoreSecretManager)
            ProtocolType.ROKU_ECP -> RokuEcpAdapter(device)
            ProtocolType.SAMSUNG_TIZEN -> SamsungTizenAdapter(device, keystoreSecretManager)
            ProtocolType.LG_WEBOS -> LgWebOsAdapter(device, keystoreSecretManager)
            ProtocolType.SONY_BRAVIA -> SonyBraviaAdapter(device, keystoreSecretManager)
            ProtocolType.HISENSE_VIDAA -> HisenseVidaaAdapter(device, keystoreSecretManager)
            ProtocolType.PANASONIC_VIERA -> PanasonicAdapter(device)
            ProtocolType.ANDROID_TV_REMOTE -> AndroidTvRemoteAdapter(device, keystoreSecretManager)
            ProtocolType.CONSUMER_IR -> ConsumerIrAdapter(device, context)
        }
    }
}
