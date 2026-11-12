package com.nexusremote.protocols

import android.util.Log
import com.nexusremote.domain.model.CommandResult
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.InstalledTvApp
import com.nexusremote.domain.model.RemoteCommand
import com.nexusremote.domain.model.SavedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Roku External Control Protocol (ECP) over HTTP (Port 8060).
 * Official protocol implemented by all Roku TV models (TCL Roku, Hisense Roku, Roku Express/Streaming Stick).
 * Fully stateless REST operations with zero authentication required by Roku design.
 */
class RokuEcpAdapter(
    override val device: SavedDevice
) : DeviceController {

    companion object {
        private const val TAG = "RokuEcpAdapter"
    }

    override val capabilities: Set<DeviceCapability> = setOf(
        DeviceCapability.POWER_TOGGLE,
        DeviceCapability.POWER_ON,
        DeviceCapability.POWER_OFF,
        DeviceCapability.DPAD_NAVIGATION,
        DeviceCapability.CENTER_OK,
        DeviceCapability.BACK,
        DeviceCapability.HOME,
        DeviceCapability.VOLUME_CONTROL,
        DeviceCapability.VOLUME_MUTE,
        DeviceCapability.CHANNEL_STEP,
        DeviceCapability.MEDIA_PLAY_PAUSE,
        DeviceCapability.MEDIA_SEEK,
        DeviceCapability.KEYBOARD_TEXT_INJECTION,
        DeviceCapability.REAL_APP_LAUNCH,
        DeviceCapability.INPUT_SOURCE_SELECT
    )

    override val protocolLimitations: List<String> = listOf(
        "Roku devices must have 'Control by mobile apps' set to 'Permissive' under Settings > System > Advanced system settings > Network access.",
        "Menu key is mapped to Options (*) on Roku."
    )

    private val _connectionState = MutableStateFlow(DeviceOnlineStatus.CHECKING)
    override val connectionState: StateFlow<DeviceOnlineStatus> = _connectionState.asStateFlow()

    private val baseUrl: String get() = "http://${device.hostIp}:${device.port}"

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = DeviceOnlineStatus.CHECKING
        val response = getHttp("$baseUrl/query/device-info")
        val isOnline = response.contains("<device-info>")
        _connectionState.value = if (isOnline) DeviceOnlineStatus.ONLINE else DeviceOnlineStatus.OFFLINE
        isOnline
    }

    override suspend fun disconnect() {
        _connectionState.value = DeviceOnlineStatus.OFFLINE
    }

    override suspend fun sendCommand(command: RemoteCommand): CommandResult = withContext(Dispatchers.IO) {
        val keyName = when (command) {
            RemoteCommand.Power -> "Power"
            RemoteCommand.PowerOn -> "PowerOn"
            RemoteCommand.PowerOff -> "PowerOff"
            RemoteCommand.Home -> "Home"
            RemoteCommand.Back -> "Back"
            RemoteCommand.Ok -> "Select"
            RemoteCommand.DpadUp -> "Up"
            RemoteCommand.DpadDown -> "Down"
            RemoteCommand.DpadLeft -> "Left"
            RemoteCommand.DpadRight -> "Right"
            RemoteCommand.VolumeUp -> "VolumeUp"
            RemoteCommand.VolumeDown -> "VolumeDown"
            RemoteCommand.Mute -> "VolumeMute"
            RemoteCommand.PlayPause -> "Play"
            RemoteCommand.FastForward -> "Fwd"
            RemoteCommand.Rewind -> "Rev"
            RemoteCommand.ChannelUp -> "ChannelUp"
            RemoteCommand.ChannelDown -> "ChannelDown"
            RemoteCommand.SourceInput -> "InputTuner"
            RemoteCommand.Menu -> "Info" // Options (*) on Roku
            RemoteCommand.Search -> "Search"
            RemoteCommand.Backspace -> "Backspace"
            RemoteCommand.Enter -> "Enter"
            is RemoteCommand.InjectText -> {
                // Roku expects keypress/Lit_<char> for text
                command.text.forEach { ch ->
                    val encoded = URLEncoder.encode(ch.toString(), "UTF-8")
                    postHttp("$baseUrl/keypress/Lit_$encoded")
                }
                return@withContext CommandResult.Success
            }
            is RemoteCommand.LaunchApp -> {
                val ok = postHttp("$baseUrl/launch/${command.appId}")
                return@withContext if (ok) CommandResult.Success else CommandResult.Failed("Failed launching Roku app")
            }
            else -> return@withContext CommandResult.UnsupportedByDevice
        }

        val success = postHttp("$baseUrl/keypress/$keyName")
        if (success) CommandResult.Success else CommandResult.Failed("Roku HTTP dispatch error")
    }

    override suspend fun fetchInstalledApps(): List<InstalledTvApp> = withContext(Dispatchers.IO) {
        val xml = getHttp("$baseUrl/query/apps")
        if (!xml.contains("<apps>")) return@withContext emptyList()

        val list = mutableListOf<InstalledTvApp>()
        val regex = Regex("""<app id="([^"]+)"[^>]*>([^<]+)</app>""")
        regex.findAll(xml).forEach { match ->
            val id = match.groupValues[1]
            val name = match.groupValues[2].trim()
            list.add(
                InstalledTvApp(
                    id = id,
                    name = name,
                    packageNameOrAppId = id,
                    iconUri = "$baseUrl/query/icon/$id",
                    bannerColorHex = "#6C3BB8"
                )
            )
        }
        list
    }

    override suspend fun launchApp(packageNameOrId: String): Boolean = withContext(Dispatchers.IO) {
        postHttp("$baseUrl/launch/$packageNameOrId")
    }

    private fun postHttp(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.doOutput = true
            conn.outputStream.close()
            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Roku POST $urlString failed: ${e.message}")
            false
        }
    }

    private fun getHttp(urlString: String): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            BufferedReader(InputStreamReader(conn.getInputStream())).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }
}
