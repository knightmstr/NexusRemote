package com.nexusremote.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Standard protocol communication types supported by NexusRemote.
 */
enum class ProtocolType {
    ADB_NETWORK,           // Fire TV / Android TV over TCP/IP 5555
    ANDROID_TV_REMOTE,     // Google Android TV Remote v2 (mDNS TLS 6466/6467)
    ROKU_ECP,              // Roku External Control Protocol (HTTP 8060)
    SAMSUNG_TIZEN,         // Samsung Smart TV Tizen WebSocket (8001/8002)
    LG_WEBOS,              // LG webOS SSAP WebSocket (3000/3001)
    SONY_BRAVIA,           // Sony BRAVIA REST / IRCC-IP HTTP
    HISENSE_VIDAA,         // Hisense VIDAA SSL TCP/MQTT (5000)
    PANASONIC_VIERA,       // Panasonic Viera UPnP/SOAP HTTP (55000)
    CONSUMER_IR            // Hardware Consumer Infrared Blaster
}

/**
 * Granular hardware/protocol capabilities. Buttons are ONLY displayed
 * in the UI if the active target device explicitly declares support.
 */
enum class DeviceCapability {
    POWER_TOGGLE,
    POWER_ON,
    POWER_OFF,
    DPAD_NAVIGATION,
    CENTER_OK,
    BACK,
    HOME,
    MENU,
    VOLUME_CONTROL,
    VOLUME_MUTE,
    CHANNEL_STEP,
    MEDIA_PLAY_PAUSE,
    MEDIA_SEEK,
    NUMERIC_KEYPAD,
    KEYBOARD_TEXT_INJECTION,
    REAL_APP_LAUNCH,
    TOUCHPAD_SWIPE,
    INPUT_SOURCE_SELECT,
    COLOR_KEYS_RED_GREEN_YELLOW_BLUE
}

/**
 * Online reachability status of a target device.
 */
enum class DeviceOnlineStatus {
    ONLINE,
    OFFLINE,
    CHECKING,
    AUTH_REQUIRED
}

/**
 * Core Room Entity representing a saved, paired television or media streamer.
 * Survives process death, device reboots, and application updates.
 */
@Entity(tableName = "saved_devices")
data class SavedDevice(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String,
    val modelName: String,
    val hostIp: String,
    val port: Int,
    val macAddress: String? = null,
    val protocolType: ProtocolType,
    val room: String = "Living Room",
    val isFavourite: Boolean = false,
    val isDefault: Boolean = false,
    val lastConnectedTimestamp: Long = 0L,
    val capabilities: Set<DeviceCapability> = emptySet(),
    val onlineStatus: DeviceOnlineStatus = DeviceOnlineStatus.CHECKING
)

/**
 * Discovered device broadcast during local mDNS / SSDP scanning.
 */
data class DiscoveredDevice(
    val id: String,
    val name: String,
    val brand: String,
    val model: String,
    val ipAddress: String,
    val port: Int,
    val protocolType: ProtocolType,
    val rawDiscoveryService: String,
    val isAlreadyPaired: Boolean = false
)

/**
 * Installed application on a target device.
 */
data class InstalledTvApp(
    val id: String,
    val name: String,
    val packageNameOrAppId: String,
    val iconUri: String? = null,
    val bannerColorHex: String = "#1E293B"
)

/**
 * Universal Remote Command definitions dispatched to Protocol Adapters.
 */
sealed class RemoteCommand {
    object Power : RemoteCommand()
    object PowerOn : RemoteCommand()
    object PowerOff : RemoteCommand()
    object DpadUp : RemoteCommand()
    object DpadDown : RemoteCommand()
    object DpadLeft : RemoteCommand()
    object DpadRight : RemoteCommand()
    object Ok : RemoteCommand()
    object Back : RemoteCommand()
    object Home : RemoteCommand()
    object Menu : RemoteCommand()
    object Search : RemoteCommand()
    object VolumeUp : RemoteCommand()
    object VolumeDown : RemoteCommand()
    object Mute : RemoteCommand()
    object ChannelUp : RemoteCommand()
    object ChannelDown : RemoteCommand()
    object PlayPause : RemoteCommand()
    object FastForward : RemoteCommand()
    object Rewind : RemoteCommand()
    object SourceInput : RemoteCommand()
    data class Digit(val number: Int) : RemoteCommand()
    data class InjectText(val text: String) : RemoteCommand()
    object Backspace : RemoteCommand()
    object Enter : RemoteCommand()
    data class LaunchApp(val appId: String) : RemoteCommand()
    data class Swipe(val deltaX: Float, val deltaY: Float) : RemoteCommand()
}

/**
 * Result of command dispatch.
 */
sealed class CommandResult {
    object Success : CommandResult()
    data class Failed(val error: String, val canRetry: Boolean = false) : CommandResult()
    object UnsupportedByDevice : CommandResult()
    object AuthRequired : CommandResult()
}
