package com.nexusremote.protocols

import com.nexusremote.domain.model.CommandResult
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.InstalledTvApp
import com.nexusremote.domain.model.RemoteCommand
import com.nexusremote.domain.model.SavedDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * Common Device Controller Interface.
 * Every TV communication protocol (ADB, Roku ECP, Samsung Tizen, LG webOS, Sony Bravia,
 * Hisense VIDAA, Panasonic, Android TV Remote, Consumer IR) implements this interface.
 *
 * Each protocol is completely independent and encapsulates its own framing, auth keys,
 * socket management, and capability declarations.
 */
interface DeviceController {

    /**
     * Target saved device profile being controlled.
     */
    val device: SavedDevice

    /**
     * Observable connection state [ONLINE, OFFLINE, CHECKING, AUTH_REQUIRED].
     */
    val connectionState: StateFlow<DeviceOnlineStatus>

    /**
     * Set of exact hardware and protocol capabilities supported by this protocol.
     * The Compose UI dynamically displays ONLY the buttons present in this set.
     */
    val capabilities: Set<DeviceCapability>

    /**
     * Documented protocol limitations (e.g. keyboard text injection unsupported,
     * power-on requiring Wake-on-LAN when in deep standby).
     */
    val protocolLimitations: List<String>

    /**
     * Initiates connection, handshake, and authentication with the target television.
     */
    suspend fun connect(): Boolean

    /**
     * Disconnects active sockets/sessions and releases resources.
     */
    suspend fun disconnect()

    /**
     * Dispatches a remote command according to protocol rules.
     */
    suspend fun sendCommand(command: RemoteCommand): CommandResult

    /**
     * Queries real third-party installed streaming apps on the target device.
     */
    suspend fun fetchInstalledApps(): List<InstalledTvApp>

    /**
     * Launches a specific installed app by package name or app ID.
     */
    suspend fun launchApp(packageNameOrId: String): Boolean

    /**
     * Optional PIN/pairing confirmation for protocols requiring challenge-response (LG webOS, Tizen, Hisense).
     */
    suspend fun pairWithPin(pin: String): Boolean = true
}
