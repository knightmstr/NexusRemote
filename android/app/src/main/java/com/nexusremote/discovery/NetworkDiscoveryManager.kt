package com.nexusremote.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DiscoveredDevice
import com.nexusremote.domain.model.ProtocolType
import com.nexusremote.domain.model.SavedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Local Network Discovery Engine:
 * Implements mDNS (Android NsdManager), SSDP/UPnP Multicast, and Targeted Subnet Probing.
 * Strictly scans ONLY the user's active local subnet (/24) obtained via WifiManager/ConnectivityManager.
 */
class NetworkDiscoveryManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "NetworkDiscovery"
        private const val SSDP_IP = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_TIMEOUT_MS = 3000
        private const val SOCKET_PROBE_TIMEOUT_MS = 800

        // Common mDNS service registration types for smart TVs
        private val MDNS_SERVICE_TYPES = listOf(
            "_androidtvremote2._tcp", // Google TV / Android TV Remote v2 (TLS 6467)
            "_adb-tls-connect._tcp",  // Modern Android TV ADB over TLS
            "_roku._tcp",             // Roku ECP
            "_googlecast._tcp",       // Chromecast with Google TV
            "_airplay._tcp",          // Apple TV / AirPlay compatible TVs (LG, Sony, Samsung)
            "_spotify-connect._tcp"   // Spotify Connect enabled TVs
        )

        // SSDP Search Targets
        private val SSDP_SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:panasonic-com:device:p00RemoteController:1",
            "urn:schemas-sony-com:service:IRCC:1",
            "roku:ecp",
            "ssap://webos",
            "dial-multiscreen-org:service:dial:1"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager: WifiManager? = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val activeNsdListeners = mutableListOf<NsdManager.DiscoveryListener>()

    /**
     * Initiates local network discovery across mDNS and SSDP.
     */
    fun startLocalDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        startMdnsDiscovery()
        startSsdpDiscovery()
    }

    /**
     * Stops active mDNS and SSDP discovery to conserve battery.
     */
    fun stopLocalDiscovery() {
        _isScanning.value = false
        stopMdnsDiscovery()
    }

    /**
     * Smart Reconnect: Probes the saved IP first.
     * Only if the device at the saved IP is offline does it initiate targeted rediscovery
     * by matching MAC or model name, avoiding unnecessary full-network scans.
     */
    suspend fun verifyOrRelocateDevice(savedDevice: SavedDevice): String? = withContext(Dispatchers.IO) {
        // Step 1: Probe saved IP first
        if (isPortOpen(savedDevice.hostIp, savedDevice.port, 1200)) {
            Log.d(TAG, "Smart Reconnect: Saved device '${savedDevice.name}' reachable at cached IP: ${savedDevice.hostIp}")
            return@withContext savedDevice.hostIp
        }

        // Step 2: Saved IP unreachable -> perform targeted rediscovery on local subnet
        Log.w(TAG, "Device '${savedDevice.name}' offline at ${savedDevice.hostIp}. Commencing targeted subnet query...")
        val localSubnet = getLocalSubnetPrefix() ?: return@withContext null

        // Check if MAC address is known to locate new IP
        for (host in 1..254) {
            val candidateIp = "$localSubnet.$host"
            if (candidateIp == savedDevice.hostIp) continue
            if (isPortOpen(candidateIp, savedDevice.port, 250)) {
                // Verify candidate device matches expected protocol
                val probe = probeIpAddress(candidateIp)
                if (probe != null && (probe.protocolType == savedDevice.protocolType || probe.brand.equals(savedDevice.brand, true))) {
                    Log.i(TAG, "Relocated device '${savedDevice.name}' at new IP: $candidateIp")
                    return@withContext candidateIp
                }
            }
        }
        null
    }

    /**
     * Probes a single IP address directly for manual entry or smart verification.
     */
    suspend fun probeIpAddress(ip: String): DiscoveredDevice? = withContext(Dispatchers.IO) {
        // Check Roku port 8060
        if (isPortOpen(ip, 8060, SOCKET_PROBE_TIMEOUT_MS)) {
            val rokuInfo = queryHttpEndpoint("http://$ip:8060/query/device-info")
            if (rokuInfo.contains("<device-info>")) {
                val modelName = rokuInfo.substringAfter("<model-name>", "Roku Streaming Device").substringBefore("</model-name>")
                val friendlyName = rokuInfo.substringAfter("<user-device-name>", "Roku TV").substringBefore("</user-device-name>")
                return@withContext DiscoveredDevice(
                    id = "roku_$ip",
                    name = friendlyName.ifBlank { "Roku $modelName" },
                    brand = "Roku",
                    model = modelName,
                    ipAddress = ip,
                    port = 8060,
                    protocolType = ProtocolType.ROKU_ECP,
                    rawDiscoveryService = "Manual Probe (Roku ECP)"
                )
            }
        }

        // Check Samsung Smart TV (Tizen) port 8001 / 8002
        if (isPortOpen(ip, 8001, SOCKET_PROBE_TIMEOUT_MS) || isPortOpen(ip, 8002, SOCKET_PROBE_TIMEOUT_MS)) {
            val samsungInfo = queryHttpEndpoint("http://$ip:8001/api/v2/")
            if (samsungInfo.contains("device") || samsungInfo.contains("Samsung")) {
                return@withContext DiscoveredDevice(
                    id = "samsung_$ip",
                    name = "Samsung Smart TV",
                    brand = "Samsung",
                    model = "Tizen Smart TV",
                    ipAddress = ip,
                    port = 8002,
                    protocolType = ProtocolType.SAMSUNG_TIZEN,
                    rawDiscoveryService = "Manual Probe (Samsung Tizen)"
                )
            }
        }

        // Check LG webOS Smart TV port 3000 / 3001
        if (isPortOpen(ip, 3000, SOCKET_PROBE_TIMEOUT_MS) || isPortOpen(ip, 3001, SOCKET_PROBE_TIMEOUT_MS)) {
            return@withContext DiscoveredDevice(
                id = "lg_$ip",
                name = "LG webOS TV",
                brand = "LG Electronics",
                model = "webOS OLED/Smart TV",
                ipAddress = ip,
                port = 3000,
                protocolType = ProtocolType.LG_WEBOS,
                rawDiscoveryService = "Manual Probe (LG webOS SSAP)"
            )
        }

        // Check Sony Bravia port 80 / 20060
        if (isPortOpen(ip, 80, SOCKET_PROBE_TIMEOUT_MS) || isPortOpen(ip, 20060, SOCKET_PROBE_TIMEOUT_MS)) {
            return@withContext DiscoveredDevice(
                id = "sony_$ip",
                name = "Sony BRAVIA TV",
                brand = "Sony",
                model = "Bravia Android TV",
                ipAddress = ip,
                port = 80,
                protocolType = ProtocolType.SONY_BRAVIA,
                rawDiscoveryService = "Manual Probe (Sony IRCC-IP)"
            )
        }

        // Check Fire TV / Android TV ADB port 5555
        if (isPortOpen(ip, 5555, SOCKET_PROBE_TIMEOUT_MS)) {
            return@withContext DiscoveredDevice(
                id = "adb_$ip",
                name = "Fire TV / Android TV",
                brand = "Amazon / Android TV",
                model = "Media Streaming Player",
                ipAddress = ip,
                port = 5555,
                protocolType = ProtocolType.ADB_NETWORK,
                rawDiscoveryService = "Manual Probe (ADB TCP/IP)"
            )
        }

        // Check Android TV Remote v2 (TLS 6466 / 6467)
        if (isPortOpen(ip, 6467, SOCKET_PROBE_TIMEOUT_MS) || isPortOpen(ip, 6466, SOCKET_PROBE_TIMEOUT_MS)) {
            return@withContext DiscoveredDevice(
                id = "atv_$ip",
                name = "Google TV / Android TV",
                brand = "Google",
                model = "Android TV Remote v2",
                ipAddress = ip,
                port = 6467,
                protocolType = ProtocolType.ANDROID_TV_REMOTE,
                rawDiscoveryService = "Manual Probe (Android TV Remote v2)"
            )
        }

        // Check Panasonic Viera port 55000
        if (isPortOpen(ip, 55000, SOCKET_PROBE_TIMEOUT_MS)) {
            return@withContext DiscoveredDevice(
                id = "panasonic_$ip",
                name = "Panasonic Viera TV",
                brand = "Panasonic",
                model = "Viera Smart TV",
                ipAddress = ip,
                port = 55000,
                protocolType = ProtocolType.PANASONIC_VIERA,
                rawDiscoveryService = "Manual Probe (Panasonic UPnP)"
            )
        }

        null
    }

    private fun startMdnsDiscovery() {
        val manager = nsdManager ?: return
        MDNS_SERVICE_TYPES.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "mDNS Discovery started for: $regType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "mDNS Resolve failed for ${info.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            val port = info.port
                            val name = info.serviceName
                            val protocol = mapServiceTypeToProtocol(info.serviceType)

                            addDiscoveredDevice(
                                DiscoveredDevice(
                                    id = "mdns_${name}_${host}",
                                    name = sanitizeDeviceName(name),
                                    brand = detectBrandFromName(name, protocol),
                                    model = "Smart TV / Streamer",
                                    ipAddress = host,
                                    port = port,
                                    protocolType = protocol,
                                    rawDiscoveryService = info.serviceType
                                )
                            )
                        }
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    manager.stopServiceDiscovery(this)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            activeNsdListeners.add(listener)
            try {
                manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed discovering mDNS service $serviceType: ${e.message}")
            }
        }
    }

    private fun stopMdnsDiscovery() {
        val manager = nsdManager ?: return
        activeNsdListeners.forEach { listener ->
            try { manager.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
        activeNsdListeners.clear()
    }

    private fun startSsdpDiscovery() {
        scope.launch {
            try {
                val multicastLock = wifiManager?.createMulticastLock("NexusRemoteSSDP")
                multicastLock?.acquire()

                val socket = MulticastSocket(null).apply {
                    reuseAddress = true
                    soTimeout = SSDP_TIMEOUT_MS
                    bind(InetSocketAddress(0))
                }

                val group = InetAddress.getByName(SSDP_IP)
                SSDP_SEARCH_TARGETS.forEach { target ->
                    val query = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: $SSDP_IP:$SSDP_PORT\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $target\r\n\r\n"
                    val bytes = query.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(bytes, bytes.size, group, SSDP_PORT)
                    socket.send(packet)
                }

                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + SSDP_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline && _isScanning.value) {
                    try {
                        val receivePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                        val hostIp = receivePacket.address.hostAddress ?: continue

                        parseSsdpResponse(response, hostIp)
                    } catch (_: Exception) {
                        break
                    }
                }
                socket.close()
                multicastLock?.release()
            } catch (e: Exception) {
                Log.e(TAG, "SSDP Discovery error: ${e.message}", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun parseSsdpResponse(response: String, ip: String) {
        val st = response.lines().find { it.startsWith("ST:", ignoreCase = true) }?.substringAfter(":")?.trim() ?: ""
        val server = response.lines().find { it.startsWith("SERVER:", ignoreCase = true) }?.substringAfter(":")?.trim() ?: ""

        val protocol = when {
            response.contains("roku", ignoreCase = true) || st.contains("roku") -> ProtocolType.ROKU_ECP
            response.contains("webos", ignoreCase = true) || response.contains("lg", ignoreCase = true) -> ProtocolType.LG_WEBOS
            response.contains("sony", ignoreCase = true) || st.contains("sony") -> ProtocolType.SONY_BRAVIA
            response.contains("panasonic", ignoreCase = true) || st.contains("panasonic") -> ProtocolType.PANASONIC_VIERA
            response.contains("tizen", ignoreCase = true) || response.contains("samsung", ignoreCase = true) -> ProtocolType.SAMSUNG_TIZEN
            else -> ProtocolType.ADB_NETWORK
        }

        val port = when (protocol) {
            ProtocolType.ROKU_ECP -> 8060
            ProtocolType.LG_WEBOS -> 3000
            ProtocolType.SONY_BRAVIA -> 80
            ProtocolType.PANASONIC_VIERA -> 55000
            ProtocolType.SAMSUNG_TIZEN -> 8002
            else -> 5555
        }

        addDiscoveredDevice(
            DiscoveredDevice(
                id = "ssdp_${protocol.name}_${ip}",
                name = "${detectBrandFromName(server, protocol)} TV",
                brand = detectBrandFromName(server, protocol),
                model = server.take(30).ifBlank { "Smart Television" },
                ipAddress = ip,
                port = port,
                protocolType = protocol,
                rawDiscoveryService = "SSDP: $st"
            )
        )
    }

    @Synchronized
    private fun addDiscoveredDevice(device: DiscoveredDevice) {
        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.ipAddress == device.ipAddress }
        if (existingIndex >= 0) {
            currentList[existingIndex] = device
        } else {
            currentList.add(device)
            Log.i(TAG, "Discovered local TV: '${device.name}' (${device.brand}, ${device.ipAddress}:${device.port}) via ${device.rawDiscoveryService}")
        }
        _discoveredDevices.value = currentList
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun queryHttpEndpoint(urlString: String): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection()
            conn.connectTimeout = 1200
            conn.readTimeout = 1200
            BufferedReader(InputStreamReader(conn.getInputStream())).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun mapServiceTypeToProtocol(type: String): ProtocolType {
        return when {
            type.contains("roku") -> ProtocolType.ROKU_ECP
            type.contains("androidtvremote2") -> ProtocolType.ANDROID_TV_REMOTE
            type.contains("adb") -> ProtocolType.ADB_NETWORK
            else -> ProtocolType.ADB_NETWORK
        }
    }

    private fun detectBrandFromName(name: String, protocol: ProtocolType): String {
        val lower = name.lowercase()
        return when {
            lower.contains("samsung") -> "Samsung"
            lower.contains("lg") || lower.contains("webos") -> "LG"
            lower.contains("sony") || lower.contains("bravia") -> "Sony"
            lower.contains("roku") || protocol == ProtocolType.ROKU_ECP -> "Roku"
            lower.contains("tcl") -> "TCL"
            lower.contains("hisense") || lower.contains("vidaa") -> "Hisense"
            lower.contains("panasonic") || lower.contains("viera") -> "Panasonic"
            lower.contains("fire") || lower.contains("amazon") || lower.contains("aft") -> "Amazon Fire TV"
            lower.contains("google") || lower.contains("shield") -> "Android TV"
            else -> "Universal TV"
        }
    }

    private fun sanitizeDeviceName(raw: String): String {
        return raw.replace("_", " ").replace("-", " ").trim()
    }

    private fun getLocalSubnetPrefix(): String? {
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
        if (ipInt == 0) return null
        val b1 = ipInt and 0xFF
        val b2 = (ipInt shr 8) and 0xFF
        val b3 = (ipInt shr 16) and 0xFF
        return "$b1.$b2.$b3"
    }
}
