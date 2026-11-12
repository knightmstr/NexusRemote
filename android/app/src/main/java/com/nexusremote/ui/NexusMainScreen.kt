package com.nexusremote.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusremote.domain.model.*
import com.nexusremote.viewmodel.DeviceManagementViewModel
import com.nexusremote.viewmodel.RemoteControlViewModel

/**
 * Root Adaptive Jetpack Compose Application for NexusRemote.
 * Supports foldable devices (e.g. HONOR Magic V5 unfold state) and tablets
 * with automatic dual-pane layouts and capability-driven button rendering.
 */
@Composable
fun NexusRemoteApp(
    deviceViewModel: DeviceManagementViewModel,
    remoteViewModel: RemoteControlViewModel,
    modifier: Modifier = Modifier
) {
    val savedDevices by deviceViewModel.savedDevices.collectAsState()
    val activeDevice by remoteViewModel.activeDevice.collectAsState()
    var currentScreen by remember { mutableStateOf(if (savedDevices.isNotEmpty()) Screen.Home else Screen.Discovery) }
    val configuration = LocalConfiguration.current
    val isFoldableExpanded = configuration.screenWidthDp >= 600

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF38BDF8),
            surface = Color(0xFF0F172A),
            background = Color(0xFF020617)
        )
    ) {
        Scaffold(
            containerColor = Color(0xFF070B13),
            modifier = modifier.fillMaxSize()
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (currentScreen) {
                    Screen.Home -> {
                        HomeScreen(
                            devices = savedDevices,
                            onDeviceClick = { device ->
                                deviceViewModel.smartReconnect(device) { verifiedDevice ->
                                    remoteViewModel.bindDevice(verifiedDevice)
                                    currentScreen = Screen.Remote
                                }
                            },
                            onAddClick = {
                                deviceViewModel.startDiscovery()
                                currentScreen = Screen.Discovery
                            },
                            onDelete = { deviceViewModel.deleteDevice(it) },
                            onToggleFav = { id, fav -> deviceViewModel.toggleFavourite(id, fav) },
                            onSetDefault = { deviceViewModel.setDefaultDevice(it) }
                        )
                    }
                    Screen.Discovery -> {
                        DiscoveryScreen(
                            deviceViewModel = deviceViewModel,
                            onDone = { currentScreen = Screen.Home }
                        )
                    }
                    Screen.Remote -> {
                        val device = activeDevice
                        if (device != null) {
                            if (isFoldableExpanded) {
                                FoldableDualPaneRemote(
                                    device = device,
                                    remoteViewModel = remoteViewModel,
                                    onBackToHome = { currentScreen = Screen.Home }
                                )
                            } else {
                                SinglePaneRemote(
                                    device = device,
                                    remoteViewModel = remoteViewModel,
                                    onBackToHome = { currentScreen = Screen.Home }
                                )
                            }
                        } else {
                            currentScreen = Screen.Home
                        }
                    }
                }
            }
        }
    }
}

enum class Screen { Home, Discovery, Remote }

/**
 * HOME SCREEN: Lists all paired Saved Devices categorized by room,
 * with real-time online status indicators, favourite stars, and default TV tags.
 */
@Composable
fun HomeScreen(
    devices: List<SavedDevice>,
    onDeviceClick: (SavedDevice) -> Unit,
    onAddClick: () -> Unit,
    onDelete: (SavedDevice) -> Unit,
    onToggleFav: (String, Boolean) -> Unit,
    onSetDefault: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NexusRemote",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "${devices.size} Saved Televisions",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add TV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No saved TVs found", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Pair once over Wi-Fi or IR. Survives reboots.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onAddClick) {
                        Text("Discover Local Network TVs")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    SavedDeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device) },
                        onDelete = { onDelete(device) },
                        onToggleFav = { onToggleFav(device.id, device.isFavourite) },
                        onSetDefault = { onSetDefault(device.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SavedDeviceCard(
    device: SavedDevice,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFav: () -> Unit,
    onSetDefault: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131B2E),
        border = BorderStroke(1.dp, if (device.isDefault) Color(0xFF0284C7) else Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Icon Avatar with Online Ping Diode
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(8.dp).clip(CircleShape)
                        .background(
                            when (device.onlineStatus) {
                                DeviceOnlineStatus.ONLINE -> Color(0xFF22C55E)
                                DeviceOnlineStatus.CHECKING -> Color(0xFFEAB308)
                                else -> Color(0xFFEF4444)
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = device.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (device.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF0369A1)) {
                            Text("DEFAULT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${device.brand} • ${device.protocolType.name} • ${device.hostIp}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Room: ${device.room}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            IconButton(onClick = onToggleFav) {
                Icon(
                    imageVector = if (device.isFavourite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favourite",
                    tint = if (device.isFavourite) Color(0xFFFBBF24) else Color(0xFF64748B)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * DISCOVERY SCREEN: Real-time mDNS & SSDP Multicast Scanner.
 */
@Composable
fun DiscoveryScreen(
    deviceViewModel: DeviceManagementViewModel,
    onDone: () -> Unit
) {
    val discovered by deviceViewModel.discoveredDevices.collectAsState()
    val isScanning by deviceViewModel.isScanning.collectAsState()
    var manualIp by remember { mutableStateOf("") }
    var manualBrand by remember { mutableStateOf("Samsung") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Discover Local Network TVs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = onDone) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Radar Scanning Pulse Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                } else {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isScanning) "Scanning local subnet (mDNS / SSDP)..." else "Local network scan idle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text("Only queries devices on your private Wi-Fi network", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
            }
        }

        // Discovered Devices List
        Text("FOUND TELEVISIONS (${discovered.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(discovered) { disc ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(disc.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${disc.brand} • ${disc.ipAddress}:${disc.port} (${disc.protocolType.name})", fontSize = 11.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = {
                                deviceViewModel.pairDiscoveredDevice(disc)
                                onDone()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Pair & Save", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Manual IP Entry Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Manual TV IP Entry", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        placeholder = { Text("192.168.1.150", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualIp.isNotBlank()) {
                                deviceViewModel.addDevice(
                                    name = "$manualBrand TV",
                                    brand = manualBrand,
                                    modelName = "Manual IP Entry",
                                    hostIp = manualIp.trim(),
                                    port = 5555,
                                    protocol = ProtocolType.ADB_NETWORK
                                )
                                onDone()
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

/**
 * ADAPTIVE DUAL-PANE REMOTE: Foldable (HONOR Magic V5) and Tablet Layout.
 */
@Composable
fun FoldableDualPaneRemote(
    device: SavedDevice,
    remoteViewModel: RemoteControlViewModel,
    onBackToHome: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Left Pane: Navigation & 5-Way Gesture Surface
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RemoteHeader(device = device, remoteViewModel = remoteViewModel, onBack = onBackToHome)
            Spacer(modifier = Modifier.height(20.dp))
            DpadSurface(onCommand = { remoteViewModel.sendCommand(it) })
            Spacer(modifier = Modifier.height(20.dp))
            VolumeChannelRockers(
                capabilities = remoteViewModel.capabilities,
                onCommand = { remoteViewModel.sendCommand(it) }
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Right Pane: Touchpad, Keyboard, Macro Bar, & Installed App Dock
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ACCESSORY SURFACE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))

            // Text Input Injection
            TextInputCard(
                capabilities = remoteViewModel.capabilities,
                onSendText = { remoteViewModel.injectText(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Touchpad Swipe Surface
            TouchpadSwipeSurface(onSwipe = { dx, dy -> remoteViewModel.sendCommand(RemoteCommand.Swipe(dx, dy)) })

            Spacer(modifier = Modifier.height(14.dp))

            // App Dock
            AppDock(
                apps = remoteViewModel.installedApps.collectAsState().value,
                onLaunch = { remoteViewModel.launchApp(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Macro Execution Bar
            MacroBar(onRunMacro = { name, seq -> remoteViewModel.executeMacro(name, seq) })
        }
    }
}

/**
 * SINGLE-PANE REMOTE: Phone Compact View with Capability Detection.
 */
@Composable
fun SinglePaneRemote(
    device: SavedDevice,
    remoteViewModel: RemoteControlViewModel,
    onBackToHome: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RemoteHeader(device = device, remoteViewModel = remoteViewModel, onBack = onBackToHome)

        Spacer(modifier = Modifier.height(16.dp))

        // D-Pad
        DpadSurface(onCommand = { remoteViewModel.sendCommand(it) })

        Spacer(modifier = Modifier.height(18.dp))

        // Volume & Channel Rockers
        VolumeChannelRockers(
            capabilities = remoteViewModel.capabilities,
            onCommand = { remoteViewModel.sendCommand(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Persistent Keyboard Input
        TextInputCard(
            capabilities = remoteViewModel.capabilities,
            onSendText = { remoteViewModel.injectText(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Installed App Dock
        AppDock(
            apps = remoteViewModel.installedApps.collectAsState().value,
            onLaunch = { remoteViewModel.launchApp(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Macros
        MacroBar(onRunMacro = { name, seq -> remoteViewModel.executeMacro(name, seq) })
    }
}

@Composable
fun RemoteHeader(
    device: SavedDevice,
    remoteViewModel: RemoteControlViewModel,
    onBack: () -> Unit
) {
    val status by remoteViewModel.connectionStatus.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = device.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape).background(
                        if (status == DeviceOnlineStatus.ONLINE) Color(0xFF22C55E) else Color(0xFFEF4444)
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "${device.protocolType.name} • ${status.name}", fontSize = 10.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
            }
        }

        IconButton(onClick = { remoteViewModel.sendCommand(RemoteCommand.Power) }) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power", tint = Color(0xFFEF4444))
        }
    }
}

@Composable
fun DpadSurface(onCommand: (RemoteCommand) -> Unit) {
    Box(
        modifier = Modifier.size(240.dp).clip(CircleShape).background(Color(0xFF131B2E)).border(2.dp, Color(0xFF1E293B), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = { onCommand(RemoteCommand.DpadUp) }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(56.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = { onCommand(RemoteCommand.DpadDown) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(56.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = { onCommand(RemoteCommand.DpadLeft) }, modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(56.dp)) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = { onCommand(RemoteCommand.DpadRight) }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(56.dp)) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", tint = Color.White, modifier = Modifier.size(32.dp))
        }

        Surface(
            onClick = { onCommand(RemoteCommand.Ok) },
            shape = CircleShape,
            color = Color(0xFF0284C7),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("OK", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}

@Composable
fun VolumeChannelRockers(
    capabilities: Set<DeviceCapability>,
    onCommand: (RemoteCommand) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back & Home
        IconButton(onClick = { onCommand(RemoteCommand.Back) }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Volume Up / Down
        if (capabilities.contains(DeviceCapability.VOLUME_CONTROL)) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E293B)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                    IconButton(onClick = { onCommand(RemoteCommand.VolumeUp) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Vol+", tint = Color.White)
                    }
                    Text("VOL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    IconButton(onClick = { onCommand(RemoteCommand.VolumeDown) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.VolumeDown, contentDescription = "Vol-", tint = Color.White)
                    }
                }
            }
        }

        // Mute
        if (capabilities.contains(DeviceCapability.VOLUME_MUTE)) {
            IconButton(onClick = { onCommand(RemoteCommand.Mute) }) {
                Icon(Icons.Default.VolumeMute, contentDescription = "Mute", tint = Color(0xFFF87171))
            }
        }

        // Home
        IconButton(onClick = { onCommand(RemoteCommand.Home) }) {
            Icon(Icons.Default.Home, contentDescription = "Home", tint = Color(0xFF38BDF8))
        }
    }
}

@Composable
fun TextInputCard(
    capabilities: Set<DeviceCapability>,
    onSendText: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val supported = capabilities.contains(DeviceCapability.KEYBOARD_TEXT_INJECTION)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = supported,
                placeholder = {
                    Text(
                        if (supported) "Type text on TV..." else "Text injection unsupported on this TV",
                        fontSize = 11.sp
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (supported && text.isNotBlank()) {
                        onSendText(text)
                        text = ""
                        keyboardController?.hide()
                    }
                },
                enabled = supported && text.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = if (supported) Color(0xFF38BDF8) else Color(0xFF475569))
            }
        }
    }
}

@Composable
fun TouchpadSwipeSurface(onSwipe: (Float, Float) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131B2E),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth().height(140.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onSwipe(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("TOUCHPAD & SWIPE SURFACE\nSwipe to glide pointer on TV", fontSize = 11.sp, color = Color(0xFF475569), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AppDock(
    apps: List<InstalledTvApp>,
    onLaunch: (InstalledTvApp) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("INSTALLED STREAMING APPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (apps.isEmpty()) {
            Text("App launching active over ADB and webOS", fontSize = 11.sp, color = Color(0xFF475569))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps) { app ->
                    Surface(
                        onClick = { onLaunch(app) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.width(80.dp).padding(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0284C7)), contentAlignment = Alignment.Center) {
                                Text(app.name.take(1), fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(app.name, fontSize = 10.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacroBar(onRunMacro: (String, List<RemoteCommand>) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("ONE-TAP AUTOMATION MACROS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onRunMacro("Cinema Mode", listOf(RemoteCommand.PowerOn, RemoteCommand.VolumeUp, RemoteCommand.VolumeUp)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("🎬 Cinema", fontSize = 11.sp)
            }
            Button(
                onClick = { onRunMacro("Mute & Sleep", listOf(RemoteCommand.Mute, RemoteCommand.PowerOff)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("🌙 Night", fontSize = 11.sp)
            }
        }
    }
}
