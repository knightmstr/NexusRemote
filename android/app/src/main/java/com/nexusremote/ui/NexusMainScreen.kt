package com.nexusremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.InstalledTvApp
import com.nexusremote.domain.model.ProtocolType
import com.nexusremote.domain.model.RemoteCommand
import com.nexusremote.domain.model.SavedDevice
import com.nexusremote.protocols.UniversalIrBrand
import com.nexusremote.viewmodel.DeviceManagementViewModel
import com.nexusremote.viewmodel.RemoteControlViewModel
import kotlinx.coroutines.delay

enum class Screen { Home, Discovery, Remote }

private val SEARCH_PRESETS = listOf(
    "4K Movies",
    "Netflix Originals",
    "Top Music",
    "Documentaries",
    "Trending News"
)

/**
 * Root Adaptive Jetpack Compose Application for NexusRemote.
 * Combines Saved Device Management, Multi-Protocol Network Discovery, and the
 * Interactive Hybrid Remote (Universal IR Blaster + Wi-Fi Smart TV Protocols).
 */
@Composable
fun NexusRemoteApp(
    deviceViewModel: DeviceManagementViewModel,
    remoteViewModel: RemoteControlViewModel,
    modifier: Modifier = Modifier
) {
    val savedDevices by deviceViewModel.savedDevices.collectAsState()
    val activeDevice by remoteViewModel.activeDevice.collectAsState()
    val isDarkTheme by remoteViewModel.isDarkTheme.collectAsState()
    val remoteFeedback by remoteViewModel.feedback.collectAsState()
    val deviceMessage by deviceViewModel.activeMessage.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.Home) }
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isExpandedLayout = configuration.screenWidthDp >= 600

    LaunchedEffect(remoteFeedback) {
        remoteFeedback?.let {
            snackbarHostState.showSnackbar(it)
            remoteViewModel.clearFeedback()
        }
    }

    LaunchedEffect(deviceMessage) {
        deviceMessage?.let {
            snackbarHostState.showSnackbar(it)
            deviceViewModel.clearMessage()
        }
    }

    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = Color(0xFF38BDF8),
            surface = Color(0xFF0F172A),
            background = Color(0xFF020617)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0284C7),
            surface = Color(0xFFF8FAFC),
            background = Color(0xFFF1F5F9)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = if (isDarkTheme) Color(0xFF070B13) else Color(0xFFF1F5F9),
            modifier = modifier.fillMaxSize()
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (currentScreen) {
                    Screen.Home -> {
                        HomeScreen(
                            devices = savedDevices,
                            deviceViewModel = deviceViewModel,
                            remoteViewModel = remoteViewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { remoteViewModel.toggleTheme() },
                            onOpenUniversalIr = {
                                remoteViewModel.bindUniversalIr()
                                currentScreen = Screen.Remote
                            },
                            onDeviceClick = { device ->
                                deviceViewModel.smartReconnect(device) { verifiedDevice ->
                                    remoteViewModel.bindDevice(verifiedDevice)
                                    currentScreen = Screen.Remote
                                }
                            },
                            onAddClick = {
                                deviceViewModel.startDiscovery()
                                currentScreen = Screen.Discovery
                            }
                        )
                    }

                    Screen.Discovery -> {
                        DiscoveryScreen(
                            deviceViewModel = deviceViewModel,
                            remoteViewModel = remoteViewModel,
                            isDarkTheme = isDarkTheme,
                            onLaunchUniversalIr = { brand ->
                                remoteViewModel.bindUniversalIr(brand)
                                currentScreen = Screen.Remote
                            },
                            onDone = {
                                deviceViewModel.stopDiscovery()
                                currentScreen = Screen.Home
                            }
                        )
                    }

                    Screen.Remote -> {
                        val device = activeDevice
                        if (device != null) {
                            InteractiveRemoteScreen(
                                device = device,
                                isExpandedLayout = isExpandedLayout,
                                isDarkTheme = isDarkTheme,
                                remoteViewModel = remoteViewModel,
                                deviceViewModel = deviceViewModel,
                                onBackToHome = { currentScreen = Screen.Home }
                            )
                        } else {
                            currentScreen = Screen.Home
                        }
                    }
                }
            }
        }
    }
}

/**
 * HOME SCREEN: Quick-launch Universal IR Blaster Remote + Saved TV Catalog with Room filters,
 * online status indicators, test/edit/re-pair actions, favourite stars, and default TV badges.
 */
@Composable
fun HomeScreen(
    devices: List<SavedDevice>,
    deviceViewModel: DeviceManagementViewModel,
    remoteViewModel: RemoteControlViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenUniversalIr: () -> Unit,
    onDeviceClick: (SavedDevice) -> Unit,
    onAddClick: () -> Unit
) {
    val hasIr by remoteViewModel.hasIrHardware.collectAsState()
    val selectedBrand by remoteViewModel.selectedIrBrand.collectAsState()
    val roomFilter by deviceViewModel.selectedRoomFilter.collectAsState()

    var showBrandDialog by remember { mutableStateOf(false) }
    var editingDevice by remember { mutableStateOf<SavedDevice?>(null) }

    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val cardBg = if (isDarkTheme) Color(0xFF131B2E) else Color.White
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFCBD5E1)

    val rooms = remember(devices) { listOf("All") + devices.map { it.room }.distinct() }
    val filteredDevices = remember(devices, roomFilter) {
        if (roomFilter.isNullOrBlank() || roomFilter == "All") devices
        else devices.filter { it.room.equals(roomFilter, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NexusRemote",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = textPrimary
                )
                Text(
                    text = "${devices.size} Saved TVs • Universal IR & Network",
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Dark/Light Theme",
                        tint = textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add TV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Featured Universal IR Blaster Remote Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFE0F2FE),
            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsRemote,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Universal IR Remote",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = if (hasIr) {
                                    "IR Blaster Ready • ${selectedBrand.name} (${selectedBrand.carrierFrequencyHz / 1000} kHz)"
                                } else {
                                    "No hardware IR blaster detected on this phone"
                                },
                                fontSize = 11.sp,
                                color = if (hasIr) Color(0xFF22C55E) else Color(0xFFF59E0B)
                            )
                        }
                    }

                    Surface(
                        onClick = { showBrandDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedBrand.name.split(" ")[0],
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select TV Brand",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenUniversalIr,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Universal Remote", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = { showBrandDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8))
                    ) {
                        Text("10 Brands", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Room Filter Chips
        if (devices.isNotEmpty() && rooms.size > 2) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                items(rooms) { room ->
                    val selected = (roomFilter ?: "All") == room
                    FilterChip(
                        selected = selected,
                        onClick = { deviceViewModel.setRoomFilter(if (room == "All") null else room) },
                        label = { Text(room, fontSize = 11.sp) }
                    )
                }
            }
        }

        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No saved network or IR TVs yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Text(
                        "Use Universal IR Remote above or scan Wi-Fi for Fire TV, Roku, Samsung, LG & Sony.",
                        fontSize = 12.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("Discover Local Network TVs", color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDevices, key = { it.id }) { device ->
                    SavedDeviceCard(
                        device = device,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { onDeviceClick(device) },
                        onEdit = { editingDevice = device },
                        onDelete = { deviceViewModel.deleteDevice(device) },
                        onToggleFav = { deviceViewModel.toggleFavourite(device.id, device.isFavourite) }
                    )
                }
            }
        }
    }

    if (showBrandDialog) {
        UniversalTvBrandDialog(
            brands = remoteViewModel.availableIrBrands,
            selectedBrand = selectedBrand,
            isDarkTheme = isDarkTheme,
            onSelectBrand = { brand ->
                remoteViewModel.selectIrBrand(brand)
                showBrandDialog = false
            },
            onDismiss = { showBrandDialog = false }
        )
    }

    editingDevice?.let { dev ->
        DeviceEditDialog(
            device = dev,
            isDarkTheme = isDarkTheme,
            onSave = { updated ->
                deviceViewModel.updateDeviceProfile(updated)
                editingDevice = null
            },
            onSetDefault = {
                deviceViewModel.setDefaultDevice(dev.id)
                editingDevice = null
            },
            onTest = {
                deviceViewModel.testDeviceConnection(dev)
            },
            onRePair = {
                deviceViewModel.rePairDevice(dev)
                editingDevice = null
            },
            onDismiss = { editingDevice = null }
        )
    }
}

@Composable
fun SavedDeviceCard(
    device: SavedDevice,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFav: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, if (device.isDefault) Color(0xFF0284C7) else cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.protocolType == ProtocolType.CONSUMER_IR) Icons.Default.SettingsRemote else Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(24.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (device.onlineStatus) {
                                DeviceOnlineStatus.ONLINE -> Color(0xFF22C55E)
                                DeviceOnlineStatus.CHECKING -> Color(0xFFEAB308)
                                DeviceOnlineStatus.AUTH_REQUIRED -> Color(0xFFF97316)
                                else -> Color(0xFFEF4444)
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (device.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF0369A1)) {
                            Text(
                                "DEFAULT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${device.brand} • ${device.protocolType.name} • ${device.hostIp}",
                    fontSize = 11.sp,
                    color = textSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Manage TV", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * DISCOVERY SCREEN: Local mDNS/SSDP Scan + Universal IR TV Setup + Manual Multi-Protocol IP Entry.
 */
@Composable
fun DiscoveryScreen(
    deviceViewModel: DeviceManagementViewModel,
    remoteViewModel: RemoteControlViewModel,
    isDarkTheme: Boolean,
    onLaunchUniversalIr: (UniversalIrBrand) -> Unit,
    onDone: () -> Unit
) {
    val discovered by deviceViewModel.discoveredDevices.collectAsState()
    val isScanning by deviceViewModel.isScanning.collectAsState()
    val hasIr by remoteViewModel.hasIrHardware.collectAsState()

    var manualIp by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var manualRoom by remember { mutableStateOf("Living Room") }
    var selectedProtocol by remember { mutableStateOf(ProtocolType.ADB_NETWORK) }
    var selectedIrBrand by remember { mutableStateOf(remoteViewModel.availableIrBrands.first()) }

    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val surfaceBg = if (isDarkTheme) Color(0xFF0F172A) else Color.White

    val protocolPortMap = remember {
        mapOf(
            ProtocolType.ADB_NETWORK to 5555,
            ProtocolType.ANDROID_TV_REMOTE to 6467,
            ProtocolType.ROKU_ECP to 8060,
            ProtocolType.SAMSUNG_TIZEN to 8002,
            ProtocolType.LG_WEBOS to 3001,
            ProtocolType.SONY_BRAVIA to 80,
            ProtocolType.HISENSE_VIDAA to 5000,
            ProtocolType.PANASONIC_VIERA to 55000
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Add Television or Streamer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            IconButton(onClick = onDone) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = textPrimary)
            }
        }

        // Section 1: Wi-Fi mDNS & SSDP Discovery
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = surfaceBg,
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                        } else {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isScanning) "Scanning local Wi-Fi (mDNS / SSDP)..." else "Local network scan idle",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                            Text("Finds Android TV, Fire TV, Roku, Samsung, LG, Sony & Panasonic", fontSize = 10.sp, color = textSecondary)
                        }
                    }
                    TextButton(onClick = { if (isScanning) deviceViewModel.stopDiscovery() else deviceViewModel.startDiscovery() }) {
                        Text(if (isScanning) "Stop" else "Rescan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (discovered.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    discovered.forEach { disc ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(disc.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Text(
                                        "${disc.brand} • ${disc.ipAddress}:${disc.port} (${disc.protocolType.name})",
                                        fontSize = 10.sp,
                                        color = textSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Button(
                                    onClick = {
                                        deviceViewModel.pairDiscoveredDevice(disc, manualRoom)
                                        onDone()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Pair & Save", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Add / Launch Universal IR TV
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceBg),
            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsRemote, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Universal IR Blaster TV Setup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                }
                Text(
                    text = if (hasIr) {
                        "Pick your TV brand below to test or save an IR profile (no Wi-Fi required)."
                    } else {
                        "Hardware check: This device has no built-in IR emitter. You can still preview profiles, or use Wi-Fi below."
                    },
                    fontSize = 11.sp,
                    color = if (hasIr) textSecondary else Color(0xFFF59E0B),
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(remoteViewModel.availableIrBrands, key = { it.id }) { brand ->
                        val isSelected = brand.id == selectedIrBrand.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedIrBrand = brand },
                            label = {
                                Text(
                                    "${brand.name.split(" ")[0]} (${brand.carrierFrequencyHz / 1000}k)",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onLaunchUniversalIr(selectedIrBrand) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use IR Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            deviceViewModel.addDevice(
                                name = "${selectedIrBrand.name} (IR)",
                                brand = selectedIrBrand.name,
                                modelName = selectedIrBrand.modelSeries,
                                hostIp = "IR",
                                port = 0,
                                protocol = ProtocolType.CONSUMER_IR,
                                room = manualRoom
                            )
                            onDone()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save IR TV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Section 3: Manual Wi-Fi TV Entry (All Network Protocols)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceBg),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Manual Wi-Fi TV Setup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                Text("Protocol", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(protocolPortMap.keys.toList()) { proto ->
                        FilterChip(
                            selected = selectedProtocol == proto,
                            onClick = { selectedProtocol = proto },
                            label = { Text(proto.name.replace("_", " "), fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualName,
                    onValueChange = { manualName = it },
                    label = { Text("TV Name (optional)", fontSize = 11.sp) },
                    placeholder = { Text("Living Room TV", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualRoom,
                        onValueChange = { manualRoom = it },
                        label = { Text("Room", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("IP Address", fontSize = 11.sp) },
                        placeholder = { Text("192.168.1.150", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (manualIp.isNotBlank()) {
                            val defaultPort = protocolPortMap[selectedProtocol] ?: 5555
                            val inferredBrand = when (selectedProtocol) {
                                ProtocolType.SAMSUNG_TIZEN -> "Samsung"
                                ProtocolType.LG_WEBOS -> "LG"
                                ProtocolType.ROKU_ECP -> "Roku"
                                ProtocolType.SONY_BRAVIA -> "Sony"
                                ProtocolType.HISENSE_VIDAA -> "Hisense"
                                ProtocolType.PANASONIC_VIERA -> "Panasonic"
                                ProtocolType.ANDROID_TV_REMOTE -> "Google TV"
                                else -> "Fire TV / Android TV"
                            }
                            deviceViewModel.addDevice(
                                name = manualName.ifBlank { "$inferredBrand TV" },
                                brand = inferredBrand,
                                modelName = "Manual ${selectedProtocol.name}",
                                hostIp = manualIp.trim(),
                                port = defaultPort,
                                protocol = selectedProtocol,
                                room = manualRoom.ifBlank { "Living Room" }
                            )
                            onDone()
                        }
                    },
                    enabled = manualIp.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Wi-Fi TV (Port ${protocolPortMap[selectedProtocol] ?: 5555})", color = Color.White)
                }
            }
        }
    }
}

/**
 * INTERACTIVE HYBRID REMOTE SCREEN:
 * Mirrors the Interactive Remote UI design: top IR LED indicator, Brand Selector & IP Status pills,
 * Master Mode Toggle (UNIVERSAL IR vs NETWORK TV), 5-Way D-Pad / Touchpad, Vol/Channel/Media rockers,
 * 0-9 Numeric Keypad Matrix, Persistent Keyboard + Virtual Touch Keyboard, App Dock & Automation Macros.
 */
@Composable
fun InteractiveRemoteScreen(
    device: SavedDevice,
    isExpandedLayout: Boolean,
    isDarkTheme: Boolean,
    remoteViewModel: RemoteControlViewModel,
    deviceViewModel: DeviceManagementViewModel,
    onBackToHome: () -> Unit
) {
    val connectionStatus by remoteViewModel.connectionStatus.collectAsState()
    val capabilities by remoteViewModel.capabilities.collectAsState()
    val limitations by remoteViewModel.protocolLimitations.collectAsState()
    val installedApps by remoteViewModel.installedApps.collectAsState()
    val isUniversalIrMode by remoteViewModel.isUniversalIrMode.collectAsState()
    val selectedBrand by remoteViewModel.selectedIrBrand.collectAsState()
    val hasIrHardware by remoteViewModel.hasIrHardware.collectAsState()
    val lastIrPulseTimestamp by remoteViewModel.lastIrPulseTimestamp.collectAsState()
    val inputText by remoteViewModel.inputText.collectAsState()
    val isTouchpadActive by remoteViewModel.isTouchpadActive.collectAsState()
    val isVirtualKeyboardOpen by remoteViewModel.isVirtualKeyboardOpen.collectAsState()
    val isHapticsEnabled by remoteViewModel.isHapticsEnabled.collectAsState()
    val isHoldToRepeatEnabled by remoteViewModel.isHoldToRepeatEnabled.collectAsState()

    var showBrandDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var isLedGlowing by remember { mutableStateOf(false) }

    LaunchedEffect(lastIrPulseTimestamp) {
        if (lastIrPulseTimestamp > 0L) {
            isLedGlowing = true
            delay(280)
            isLedGlowing = false
        }
    }

    val chassisBg = if (isDarkTheme) Color(0xFF0E131F) else Color(0xFFFFFFFF)
    val chassisBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val surfaceElevated = if (isDarkTheme) Color(0xFF172033) else Color(0xFFE2E8F0)

    val hasNetworkController = device.protocolType != ProtocolType.CONSUMER_IR

    if (isExpandedLayout) {
        // Tablet & Unfolded Foldable Dual-Pane Adaptive Remote
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Pane: Interactive Remote Chassis
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = chassisBg,
                border = BorderStroke(2.dp, chassisBorder),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RemoteTopChassisBar(
                        device = device,
                        isUniversalIrMode = isUniversalIrMode,
                        selectedBrand = selectedBrand,
                        hasIrHardware = hasIrHardware,
                        isLedGlowing = isLedGlowing,
                        connectionStatus = connectionStatus,
                        hasNetworkController = hasNetworkController,
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onBack = onBackToHome,
                        onOpenBrandSelector = { showBrandDialog = true },
                        onOpenDeviceInfo = { showInfoDialog = true },
                        onSwitchMode = { remoteViewModel.switchRemoteMode(it) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PrimaryRemoteControls(
                        capabilities = capabilities,
                        isUniversalIrMode = isUniversalIrMode,
                        selectedBrand = selectedBrand,
                        hasIrHardware = hasIrHardware,
                        isTouchpadActive = false,
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onOpenBrandSelector = { showBrandDialog = true },
                        onCommand = { remoteViewModel.sendCommand(it) },
                        onHoldStart = { remoteViewModel.startRepeatingCommand(it) },
                        onHoldStop = { remoteViewModel.stopRepeatingCommand() },
                        onSwipe = { dx, dy -> remoteViewModel.sendCommand(RemoteCommand.Swipe(dx, dy)) },
                        onToggleTouchpad = { remoteViewModel.toggleTouchpad() }
                    )
                }
            }

            // Right Pane: Touchpad, Keyboard & Search, Installed App Dock, Automation Macros
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = chassisBg,
                border = BorderStroke(1.dp, chassisBorder),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "INTERACTIVE ACCESSORY DECK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (capabilities.contains(DeviceCapability.TOUCHPAD_SWIPE)) {
                        TouchpadSwipeSurface(
                            surfaceElevated = surfaceElevated,
                            textSecondary = textSecondary,
                            onSwipe = { dx, dy -> remoteViewModel.sendCommand(RemoteCommand.Swipe(dx, dy)) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    TextInputAndVirtualKeyboardCard(
                        capabilities = capabilities,
                        inputText = inputText,
                        isVirtualKeyboardOpen = isVirtualKeyboardOpen,
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onInputTextChange = { remoteViewModel.setInputText(it) },
                        onSubmitText = { remoteViewModel.injectText(inputText) },
                        onSearch = { remoteViewModel.sendCommand(RemoteCommand.Search) },
                        onSendPreset = { remoteViewModel.injectText(it) },
                        onBackspace = { remoteViewModel.sendCommand(RemoteCommand.Backspace) },
                        onEnter = { remoteViewModel.sendCommand(RemoteCommand.Enter) },
                        onToggleVirtualKeyboard = { remoteViewModel.toggleVirtualKeyboard() },
                        onKeyChar = { remoteViewModel.appendVirtualChar(it) }
                    )

                    if (capabilities.contains(DeviceCapability.REAL_APP_LAUNCH)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AppDockSection(
                            apps = installedApps,
                            surfaceElevated = surfaceElevated,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onLaunch = { remoteViewModel.launchApp(it) },
                            onRefresh = { remoteViewModel.refreshInstalledApps() }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    MacroBar(
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onRunMacro = { name, seq -> remoteViewModel.executeMacro(name, seq) }
                    )
                }
            }
        }
    } else {
        // Single-Pane Phone Interactive Remote Chassis
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(38.dp),
                color = chassisBg,
                border = BorderStroke(2.dp, chassisBorder),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RemoteTopChassisBar(
                        device = device,
                        isUniversalIrMode = isUniversalIrMode,
                        selectedBrand = selectedBrand,
                        hasIrHardware = hasIrHardware,
                        isLedGlowing = isLedGlowing,
                        connectionStatus = connectionStatus,
                        hasNetworkController = hasNetworkController,
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onBack = onBackToHome,
                        onOpenBrandSelector = { showBrandDialog = true },
                        onOpenDeviceInfo = { showInfoDialog = true },
                        onSwitchMode = { remoteViewModel.switchRemoteMode(it) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PrimaryRemoteControls(
                        capabilities = capabilities,
                        isUniversalIrMode = isUniversalIrMode,
                        selectedBrand = selectedBrand,
                        hasIrHardware = hasIrHardware,
                        isTouchpadActive = isTouchpadActive,
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onOpenBrandSelector = { showBrandDialog = true },
                        onCommand = { remoteViewModel.sendCommand(it) },
                        onHoldStart = { remoteViewModel.startRepeatingCommand(it) },
                        onHoldStop = { remoteViewModel.stopRepeatingCommand() },
                        onSwipe = { dx, dy -> remoteViewModel.sendCommand(RemoteCommand.Swipe(dx, dy)) },
                        onToggleTouchpad = { remoteViewModel.toggleTouchpad() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextInputAndVirtualKeyboardCard(
                        capabilities = capabilities,
                        inputText = inputText,
                        isVirtualKeyboardOpen = isVirtualKeyboardOpen,
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onInputTextChange = { remoteViewModel.setInputText(it) },
                        onSubmitText = { remoteViewModel.injectText(inputText) },
                        onSearch = { remoteViewModel.sendCommand(RemoteCommand.Search) },
                        onSendPreset = { remoteViewModel.injectText(it) },
                        onBackspace = { remoteViewModel.sendCommand(RemoteCommand.Backspace) },
                        onEnter = { remoteViewModel.sendCommand(RemoteCommand.Enter) },
                        onToggleVirtualKeyboard = { remoteViewModel.toggleVirtualKeyboard() },
                        onKeyChar = { remoteViewModel.appendVirtualChar(it) }
                    )

                    if (capabilities.contains(DeviceCapability.REAL_APP_LAUNCH)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AppDockSection(
                            apps = installedApps,
                            surfaceElevated = surfaceElevated,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onLaunch = { remoteViewModel.launchApp(it) },
                            onRefresh = { remoteViewModel.refreshInstalledApps() }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    MacroBar(
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onRunMacro = { name, seq -> remoteViewModel.executeMacro(name, seq) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }

    if (showBrandDialog) {
        UniversalTvBrandDialog(
            brands = remoteViewModel.availableIrBrands,
            selectedBrand = selectedBrand,
            isDarkTheme = isDarkTheme,
            onSelectBrand = { brand ->
                remoteViewModel.selectIrBrand(brand)
                showBrandDialog = false
            },
            onDismiss = { showBrandDialog = false }
        )
    }

    if (showInfoDialog) {
        RemoteSettingsAndLimitationsDialog(
            device = device,
            connectionStatus = connectionStatus,
            limitations = limitations,
            isDarkTheme = isDarkTheme,
            isHapticsEnabled = isHapticsEnabled,
            isHoldToRepeatEnabled = isHoldToRepeatEnabled,
            onToggleTheme = { remoteViewModel.toggleTheme() },
            onToggleHaptics = { remoteViewModel.toggleHaptics() },
            onToggleHoldToRepeat = { remoteViewModel.toggleHoldToRepeat() },
            onReconnect = {
                remoteViewModel.reconnectActiveDevice()
                showInfoDialog = false
            },
            onRePair = {
                deviceViewModel.rePairDevice(device)
                showInfoDialog = false
            },
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
private fun RemoteTopChassisBar(
    device: SavedDevice,
    isUniversalIrMode: Boolean,
    selectedBrand: UniversalIrBrand,
    hasIrHardware: Boolean,
    isLedGlowing: Boolean,
    connectionStatus: DeviceOnlineStatus,
    hasNetworkController: Boolean,
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: () -> Unit,
    onOpenBrandSelector: () -> Unit,
    onOpenDeviceInfo: () -> Unit,
    onSwitchMode: (Boolean) -> Unit
) {
    // Top Infrared LED Diode Indicator (just like physical chassis header)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(if (isLedGlowing) Color(0xFFF43F5E) else Color(0xFF334155))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isUniversalIrMode) {
                "CONSUMER IR BLASTER • ${selectedBrand.carrierFrequencyHz / 1000} KHZ"
            } else {
                "${device.protocolType.name} • ${device.hostIp}"
            },
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            color = textSecondary
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Top Status & Selector Pill Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home", tint = textPrimary)
        }

        // Brand Selector Pill
        Surface(
            onClick = onOpenBrandSelector,
            shape = RoundedCornerShape(20.dp),
            color = surfaceElevated,
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (hasIrHardware) Color(0xFF22C55E) else Color(0xFF64748B))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = selectedBrand.name.split(" ")[0].uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${selectedBrand.carrierFrequencyHz / 1000}k",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF38BDF8)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = textSecondary, modifier = Modifier.size(14.dp))
            }
        }

        // Connection / Settings Pill
        val isOnline = connectionStatus == DeviceOnlineStatus.ONLINE
        Surface(
            onClick = onOpenDeviceInfo,
            shape = RoundedCornerShape(20.dp),
            color = if (isOnline) Color(0xFF052E16) else Color(0xFF3B1219),
            border = BorderStroke(1.dp, if (isOnline) Color(0xFF22C55E) else Color(0xFFF87171))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "Status",
                    tint = if (isOnline) Color(0xFF4ADE80) else Color(0xFFF87171),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isUniversalIrMode) "IR" else device.hostIp.takeLast(9),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF94A3B8), modifier = Modifier.size(12.dp))
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Hybrid Master Mode Switch: UNIVERSAL IR vs NETWORK TV
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (surfaceElevated == Color(0xFF172033)) Color(0xFF090D16) else Color(0xFFE2E8F0),
        border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Universal IR Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isUniversalIrMode) Color(0xFF1E293B) else Color.Transparent)
                    .clickable { onSwitchMode(true) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SettingsRemote,
                        contentDescription = null,
                        tint = if (isUniversalIrMode) Color(0xFFFB7185) else textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "UNIVERSAL IR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUniversalIrMode) Color(0xFFFB7185) else textSecondary
                    )
                }
            }

            // Network Smart TV Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isUniversalIrMode) Color(0xFF0284C7) else Color.Transparent)
                    .clickable(enabled = hasNetworkController) { onSwitchMode(false) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (!isUniversalIrMode) Color.White else if (hasNetworkController) textSecondary else textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (hasNetworkController) device.protocolType.name.substringBefore("_") else "NETWORK (UNPAIRED)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isUniversalIrMode) Color.White else if (hasNetworkController) textSecondary else textSecondary.copy(alpha = 0.4f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryRemoteControls(
    capabilities: Set<DeviceCapability>,
    isUniversalIrMode: Boolean,
    selectedBrand: UniversalIrBrand,
    hasIrHardware: Boolean,
    isTouchpadActive: Boolean,
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onOpenBrandSelector: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    onHoldStart: (RemoteCommand) -> Unit,
    onHoldStop: () -> Unit,
    onSwipe: (Float, Float) -> Unit,
    onToggleTouchpad: () -> Unit
) {
    // Hardware IR Warning Notice if phone lacks an IR emitter
    if (isUniversalIrMode && !hasIrHardware) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF451A03),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hardware IR emitter not found on this phone. Select a Wi-Fi TV from Home for network control.",
                    fontSize = 11.sp,
                    color = Color(0xFFFDE68A)
                )
            }
        }
    }

    // Active IR Brand Card inside Universal IR Mode
    if (isUniversalIrMode) {
        Surface(
            onClick = onOpenBrandSelector,
            shape = RoundedCornerShape(14.dp),
            color = surfaceElevated,
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${selectedBrand.name} (${selectedBrand.carrierFrequencyHz / 1000} kHz)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "${selectedBrand.modelSeries} • ${selectedBrand.protocol.name}",
                        fontSize = 10.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF2563EB)) {
                    Text(
                        "CHANGE TV",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    // Top Power & Input / Touchpad Row (strictly capability gated)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (capabilities.contains(DeviceCapability.POWER_TOGGLE) || capabilities.contains(DeviceCapability.POWER_OFF)) {
            RemoteCircleButton(
                icon = Icons.Default.PowerSettingsNew,
                label = "POWER",
                backgroundColor = Color(0xFFDC2626),
                tint = Color.White,
                size = 50.dp,
                labelColor = textSecondary,
                onClick = { onCommand(RemoteCommand.Power) }
            )
        } else {
            Spacer(modifier = Modifier.size(50.dp))
        }

        // Center Telemetry / Touchpad Toggle
        if (capabilities.contains(DeviceCapability.TOUCHPAD_SWIPE)) {
            Surface(
                onClick = onToggleTouchpad,
                shape = RoundedCornerShape(16.dp),
                color = if (isTouchpadActive) Color(0xFF0284C7) else surfaceElevated,
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = "Touchpad",
                        tint = if (isTouchpadActive) Color.White else Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTouchpadActive) "D-PAD" else "TOUCHPAD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTouchpadActive) Color.White else textPrimary
                    )
                }
            }
        } else if (isUniversalIrMode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceElevated
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${selectedBrand.carrierFrequencyHz / 1000}.0 kHz",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        selectedBrand.protocol.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFB7185)
                    )
                }
            }
        }

        if (capabilities.contains(DeviceCapability.INPUT_SOURCE_SELECT)) {
            RemoteCircleButton(
                icon = Icons.Default.SettingsInputHdmi,
                label = "INPUT",
                backgroundColor = surfaceElevated,
                tint = Color(0xFFFBBF24),
                size = 50.dp,
                labelColor = textSecondary,
                onClick = { onCommand(RemoteCommand.SourceInput) }
            )
        } else {
            Spacer(modifier = Modifier.size(50.dp))
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Center Surface: Circular 5-Way D-Pad (with Hold-to-Repeat) OR Touchpad Swipe Pad
    AnimatedContent(
        targetState = isTouchpadActive && capabilities.contains(DeviceCapability.TOUCHPAD_SWIPE),
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
        label = "DpadTouchpadSwitch"
    ) { showTouchpad ->
        if (showTouchpad) {
            TouchpadSwipeSurface(
                surfaceElevated = surfaceElevated,
                textSecondary = textSecondary,
                onSwipe = onSwipe
            )
        } else if (capabilities.contains(DeviceCapability.DPAD_NAVIGATION)) {
            DpadCircularSurface(
                surfaceElevated = surfaceElevated,
                textPrimary = textPrimary,
                onCommand = onCommand,
                onHoldStart = onHoldStart,
                onHoldStop = onHoldStop
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Navigation Action Row: BACK / HOME / SEARCH / MENU (capability gated)
    val navButtons = buildList {
        if (capabilities.contains(DeviceCapability.BACK)) {
            add(Triple(Icons.AutoMirrored.Filled.ArrowBack, "BACK", RemoteCommand.Back))
        }
        if (capabilities.contains(DeviceCapability.HOME)) {
            add(Triple(Icons.Default.Home, "HOME", RemoteCommand.Home))
        }
        if (capabilities.contains(DeviceCapability.KEYBOARD_TEXT_INJECTION) && !isUniversalIrMode) {
            add(Triple(Icons.Default.Search, "SEARCH", RemoteCommand.Search))
        }
        if (capabilities.contains(DeviceCapability.MENU)) {
            add(Triple(Icons.Default.Menu, "MENU", RemoteCommand.Menu))
        }
    }

    if (navButtons.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navButtons.forEach { (icon, label, cmd) ->
                RemoteCircleButton(
                    icon = icon,
                    label = label,
                    backgroundColor = if (label == "SEARCH") Color(0xFF0C4A6E) else surfaceElevated,
                    tint = if (label == "HOME" || label == "SEARCH") Color(0xFF38BDF8) else textPrimary,
                    size = 46.dp,
                    labelColor = textSecondary,
                    onClick = { onCommand(cmd) }
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }

    // Volume, Mute & Channel Rockers (strictly capability gated, with Hold-to-Repeat)
    if (capabilities.contains(DeviceCapability.VOLUME_CONTROL) || capabilities.contains(DeviceCapability.CHANNEL_STEP)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (capabilities.contains(DeviceCapability.VOLUME_CONTROL)) {
                RockerPill(
                    title = "VOL",
                    upIcon = Icons.Default.VolumeUp,
                    downIcon = Icons.Default.VolumeDown,
                    surfaceElevated = surfaceElevated,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onUpHoldStart = { onHoldStart(RemoteCommand.VolumeUp) },
                    onDownHoldStart = { onHoldStart(RemoteCommand.VolumeDown) },
                    onHoldStop = onHoldStop
                )
            }

            if (capabilities.contains(DeviceCapability.VOLUME_MUTE)) {
                RemoteCircleButton(
                    icon = Icons.Default.VolumeMute,
                    label = "MUTE",
                    backgroundColor = Color(0xFF3F1D22),
                    tint = Color(0xFFF87171),
                    size = 50.dp,
                    labelColor = textSecondary,
                    onClick = { onCommand(RemoteCommand.Mute) }
                )
            }

            if (capabilities.contains(DeviceCapability.CHANNEL_STEP)) {
                RockerPill(
                    title = "CH",
                    upIcon = Icons.Default.KeyboardArrowUp,
                    downIcon = Icons.Default.KeyboardArrowDown,
                    surfaceElevated = surfaceElevated,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onUpHoldStart = { onHoldStart(RemoteCommand.ChannelUp) },
                    onDownHoldStart = { onHoldStart(RemoteCommand.ChannelDown) },
                    onHoldStop = onHoldStop
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }

    // Media Playback Transport Bar (Rewind / Play-Pause / Fast-Forward)
    if (capabilities.contains(DeviceCapability.MEDIA_PLAY_PAUSE)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (capabilities.contains(DeviceCapability.MEDIA_SEEK)) {
                    IconButton(onClick = { onCommand(RemoteCommand.Rewind) }) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = textPrimary)
                    }
                }
                IconButton(
                    onClick = { onCommand(RemoteCommand.PlayPause) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White)
                }
                if (capabilities.contains(DeviceCapability.MEDIA_SEEK)) {
                    IconButton(onClick = { onCommand(RemoteCommand.FastForward) }) {
                        Icon(Icons.Default.FastForward, contentDescription = "Fast Forward", tint = textPrimary)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }

    // Full 0-9 Numeric Keypad Matrix (shown whenever NUMERIC_KEYPAD capability is supported)
    if (capabilities.contains(DeviceCapability.NUMERIC_KEYPAD)) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = surfaceElevated.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isUniversalIrMode) "NUMERIC KEYPAD (${selectedBrand.name.uppercase()})" else "NUMERIC KEYPAD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                val grid = listOf(
                    listOf(1, 2, 3),
                    listOf(4, 5, 6),
                    listOf(7, 8, 9),
                    listOf(-1, 0, -2)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grid.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { key ->
                                when (key) {
                                    in 0..9 -> {
                                        Surface(
                                            onClick = { onCommand(RemoteCommand.Digit(key)) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = surfaceElevated,
                                            border = BorderStroke(1.dp, Color(0xFF334155)),
                                            modifier = Modifier.size(width = 66.dp, height = 44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = key.toString(),
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = textPrimary
                                                )
                                            }
                                        }
                                    }
                                    -1 -> {
                                        Surface(
                                            onClick = { onCommand(RemoteCommand.Back) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = surfaceElevated.copy(alpha = 0.5f),
                                            modifier = Modifier.size(width = 66.dp, height = 44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("PREV", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                                            }
                                        }
                                    }
                                    -2 -> {
                                        Surface(
                                            onClick = { onCommand(RemoteCommand.Ok) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color(0xFF1E3A8A),
                                            modifier = Modifier.size(width = 66.dp, height = 44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("ENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DpadCircularSurface(
    surfaceElevated: Color,
    textPrimary: Color,
    onCommand: (RemoteCommand) -> Unit,
    onHoldStart: (RemoteCommand) -> Unit,
    onHoldStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(228.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(surfaceElevated, surfaceElevated.copy(alpha = 0.65f))
                )
            )
            .border(2.dp, Color(0xFF334155), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        HoldableIconButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Up",
            tint = textPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size(56.dp),
            onHoldStart = { onHoldStart(RemoteCommand.DpadUp) },
            onHoldStop = onHoldStop
        )
        HoldableIconButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Down",
            tint = textPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .size(56.dp),
            onHoldStart = { onHoldStart(RemoteCommand.DpadDown) },
            onHoldStop = onHoldStop
        )
        HoldableIconButton(
            icon = Icons.Default.KeyboardArrowLeft,
            contentDescription = "Left",
            tint = textPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(56.dp),
            onHoldStart = { onHoldStart(RemoteCommand.DpadLeft) },
            onHoldStop = onHoldStop
        )
        HoldableIconButton(
            icon = Icons.Default.KeyboardArrowRight,
            contentDescription = "Right",
            tint = textPrimary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(56.dp),
            onHoldStart = { onHoldStart(RemoteCommand.DpadRight) },
            onHoldStop = onHoldStop
        )

        Surface(
            onClick = { onCommand(RemoteCommand.Ok) },
            shape = CircleShape,
            color = Color(0xFF0284C7),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.5f)),
            modifier = Modifier.size(82.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("OK", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun HoldableIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onHoldStart: () -> Unit,
    onHoldStop: () -> Unit
) {
    val latestOnHoldStart by rememberUpdatedState(onHoldStart)
    val latestOnHoldStop by rememberUpdatedState(onHoldStop)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(contentDescription) {
                detectTapGestures(
                    onPress = {
                        latestOnHoldStart()
                        try {
                            tryAwaitRelease()
                        } finally {
                            latestOnHoldStop()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun RockerPill(
    title: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onUpHoldStart: () -> Unit,
    onDownHoldStart: () -> Unit,
    onHoldStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = surfaceElevated,
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            HoldableIconButton(
                icon = upIcon,
                contentDescription = "$title Up",
                tint = textPrimary,
                modifier = Modifier.size(40.dp),
                onHoldStart = onUpHoldStart,
                onHoldStop = onHoldStop
            )
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondary)
            HoldableIconButton(
                icon = downIcon,
                contentDescription = "$title Down",
                tint = textPrimary,
                modifier = Modifier.size(40.dp),
                onHoldStart = onDownHoldStart,
                onHoldStop = onHoldStop
            )
        }
    }
}

@Composable
private fun TextInputAndVirtualKeyboardCard(
    capabilities: Set<DeviceCapability>,
    inputText: String,
    isVirtualKeyboardOpen: Boolean,
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onInputTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onSearch: () -> Unit,
    onSendPreset: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onToggleVirtualKeyboard: () -> Unit,
    onKeyChar: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val supported = capabilities.contains(DeviceCapability.KEYBOARD_TEXT_INJECTION)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (supported) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(SEARCH_PRESETS) { preset ->
                    Surface(
                        onClick = { onSendPreset(preset) },
                        shape = RoundedCornerShape(14.dp),
                        color = surfaceElevated,
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = preset,
                            fontSize = 10.sp,
                            color = textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceElevated),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSearch,
                        enabled = supported,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search on TV",
                            tint = if (supported) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        enabled = supported,
                        placeholder = {
                            Text(
                                if (supported) "Type or search on TV..." else "Text injection unsupported by active protocol",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (supported && inputText.isNotBlank()) {
                                onSubmitText()
                                keyboardController?.hide()
                            }
                        }),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = Color(0xFF0284C7),
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (supported && inputText.isNotEmpty()) {
                        IconButton(onClick = { onInputTextChange("") }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (supported) {
                        IconButton(onClick = onBackspace, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Backspace, contentDescription = "Backspace", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onToggleVirtualKeyboard, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.Keyboard,
                                contentDescription = "Toggle Virtual Keyboard",
                                tint = if (isVirtualKeyboardOpen) Color(0xFF38BDF8) else textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (supported && inputText.isNotBlank()) {
                                onSubmitText()
                                keyboardController?.hide()
                            }
                        },
                        enabled = supported && inputText.isNotBlank(),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (supported && inputText.isNotBlank()) Color(0xFF0284C7) else Color(0xFF334155))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Text", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                // Expandable Virtual QWERTY Touch Keyboard
                AnimatedVisibility(visible = supported && isVirtualKeyboardOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
                        rows.forEachIndexed { idx, keys ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth(if (idx == 0) 1f else if (idx == 1) 0.92f else 0.82f)
                            ) {
                                keys.forEach { ch ->
                                    Surface(
                                        onClick = { onKeyChar(ch.toString()) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(ch.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                onClick = onBackspace,
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF3F1D22),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("DEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                }
                            }
                            Surface(
                                onClick = { onKeyChar(" ") },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .weight(2.2f)
                                    .height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("SPACE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Surface(
                                onClick = onEnter,
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0284C7),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("ENTER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchpadSwipeSurface(
    surfaceElevated: Color,
    textSecondary: Color,
    onSwipe: (Float, Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = surfaceElevated,
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onSwipe(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TOUCHPAD & SWIPE SURFACE\nDrag to move cursor or navigate",
                    fontSize = 11.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AppDockSection(
    apps: List<InstalledTvApp>,
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onLaunch: (InstalledTvApp) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TV APP LAUNCH DOCK (${apps.size})",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                letterSpacing = 1.sp
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps", tint = textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (apps.isEmpty()) {
            Text("Connect to TV to discover installed streaming apps", fontSize = 11.sp, color = textSecondary)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps, key = { it.id }) { app ->
                    val badgeColor = remember(app.bannerColorHex) {
                        try {
                            Color(android.graphics.Color.parseColor(app.bannerColorHex))
                        } catch (_: Exception) {
                            Color(0xFF0284C7)
                        }
                    }
                    Surface(
                        onClick = { onLaunch(app) },
                        shape = RoundedCornerShape(14.dp),
                        color = surfaceElevated,
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.width(82.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badgeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(app.name.take(1).uppercase(), fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = app.name,
                                fontSize = 10.sp,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroBar(
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onRunMacro: (String, List<RemoteCommand>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ONE-TAP AUTOMATION MACROS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textSecondary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onRunMacro("Cinema Mode", listOf(RemoteCommand.PowerOn, RemoteCommand.VolumeUp, RemoteCommand.VolumeUp)) },
                colors = ButtonDefaults.buttonColors(containerColor = surfaceElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Cinema", fontSize = 11.sp, color = textPrimary)
            }
            Button(
                onClick = { onRunMacro("Night Mute", listOf(RemoteCommand.Mute, RemoteCommand.PowerOff)) },
                colors = ButtonDefaults.buttonColors(containerColor = surfaceElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Night", fontSize = 11.sp, color = textPrimary)
            }
        }
    }
}

@Composable
private fun RemoteCircleButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    tint: Color,
    size: Dp,
    labelColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = backgroundColor,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f)),
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(size * 0.45f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = labelColor)
    }
}

/**
 * Universal TV Brand Selection Modal Dialog (10 Brands + Carrier Frequency & Protocol Badges).
 */
@Composable
private fun UniversalTvBrandDialog(
    brands: List<UniversalIrBrand>,
    selectedBrand: UniversalIrBrand,
    isDarkTheme: Boolean,
    onSelectBrand: (UniversalIrBrand) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDarkTheme) Color(0xFF141822) else Color.White
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Universal TV Selector", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Text("Calibrate IR carrier & protocol profile", fontSize = 11.sp, color = textSecondary)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(brands, key = { it.id }) { brand ->
                    val isSelected = brand.id == selectedBrand.id
                    Surface(
                        onClick = { onSelectBrand(brand) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1E3A8A) else if (isDarkTheme) Color(0xFF1E232F) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(brand.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else textPrimary)
                                Text(brand.modelSeries, fontSize = 10.sp, color = if (isSelected) Color(0xFFBFDBFE) else textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF0F172A)) {
                                        Text(
                                            "${brand.carrierFrequencyHz / 1000} kHz",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF0F172A)) {
                                        Text(
                                            brand.protocol.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFA78BFA),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF60A5FA))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
        }
    )
}

@Composable
private fun DeviceEditDialog(
    device: SavedDevice,
    isDarkTheme: Boolean,
    onSave: (SavedDevice) -> Unit,
    onSetDefault: () -> Unit,
    onTest: () -> Unit,
    onRePair: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(device) { mutableStateOf(device.name) }
    var room by remember(device) { mutableStateOf(device.room) }
    var hostIp by remember(device) { mutableStateOf(device.hostIp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDarkTheme) Color(0xFF141822) else Color.White,
        title = { Text("Manage Saved TV", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room") }, singleLine = true)
                if (device.protocolType != ProtocolType.CONSUMER_IR) {
                    OutlinedTextField(value = hostIp, onValueChange = { hostIp = it }, label = { Text("IP Address") }, singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) {
                        Text("Test", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onSetDefault, modifier = Modifier.weight(1f)) {
                        Text("Set Default", fontSize = 11.sp)
                    }
                }
                if (device.protocolType != ProtocolType.CONSUMER_IR) {
                    OutlinedButton(onClick = onRePair, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear Saved Keys & Re-Pair", fontSize = 11.sp, color = Color(0xFFF87171))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(device.copy(name = name.trim(), room = room.trim(), hostIp = hostIp.trim()))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RemoteSettingsAndLimitationsDialog(
    device: SavedDevice,
    connectionStatus: DeviceOnlineStatus,
    limitations: List<String>,
    isDarkTheme: Boolean,
    isHapticsEnabled: Boolean,
    isHoldToRepeatEnabled: Boolean,
    onToggleTheme: () -> Unit,
    onToggleHaptics: () -> Unit,
    onToggleHoldToRepeat: () -> Unit,
    onReconnect: () -> Unit,
    onRePair: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDarkTheme) Color(0xFF141822) else Color.White,
        title = { Text("${device.name} • Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Status: ${connectionStatus.name} (${device.protocolType.name})",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Remote Theme", fontSize = 13.sp)
                    Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Feedback", fontSize = 13.sp)
                    Switch(checked = isHapticsEnabled, onCheckedChange = { onToggleHaptics() })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hold-to-Repeat D-Pad/Vol", fontSize = 13.sp)
                    Switch(checked = isHoldToRepeatEnabled, onCheckedChange = { onToggleHoldToRepeat() })
                }

                if (limitations.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Protocol Limitations:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    limitations.forEach { item ->
                        Text("• $item", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }

                if (device.protocolType != ProtocolType.CONSUMER_IR) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReconnect, modifier = Modifier.weight(1f)) {
                            Text("Reconnect", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = onRePair, modifier = Modifier.weight(1f)) {
                            Text("Reset Keys", fontSize = 11.sp, color = Color(0xFFF87171))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.Bold) }
        }
    )
}
