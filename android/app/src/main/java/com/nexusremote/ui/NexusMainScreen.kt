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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.shadow
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

enum class Screen { Remote, Home, Discovery }

private val SEARCH_PRESETS = listOf(
    "4K Movies",
    "Netflix Originals",
    "Top Music",
    "Documentaries",
    "Trending News"
)

private val PROTOCOL_PORT_MAP = mapOf(
    ProtocolType.ADB_NETWORK to 5555,
    ProtocolType.ANDROID_TV_REMOTE to 6467,
    ProtocolType.ROKU_ECP to 8060,
    ProtocolType.SAMSUNG_TIZEN to 8002,
    ProtocolType.LG_WEBOS to 3001,
    ProtocolType.SONY_BRAVIA to 80,
    ProtocolType.HISENSE_VIDAA to 5000,
    ProtocolType.PANASONIC_VIERA to 55000
)

/**
 * Root Adaptive Jetpack Compose Application for NexusRemote.
 * Launches directly into the Interactive Hybrid Remote (matching the simulated remote UI)
 * while offering Saved TV Management and mDNS/SSDP Auto-Discovery from the remote header.
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

    var currentScreen by remember { mutableStateOf(Screen.Remote) }
    var didAutoBindSavedDevice by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isExpandedLayout = configuration.screenWidthDp >= 600

    // Auto-bind the default saved Wi-Fi TV once Room loads, if any exists
    LaunchedEffect(savedDevices) {
        if (!didAutoBindSavedDevice && savedDevices.isNotEmpty()) {
            didAutoBindSavedDevice = true
            val preferred = savedDevices.firstOrNull { it.isDefault }
                ?: savedDevices.firstOrNull { it.protocolType != ProtocolType.CONSUMER_IR }
                ?: savedDevices.first()
            remoteViewModel.bindDevice(preferred, keepCurrentMode = false)
        }
    }

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
            containerColor = if (isDarkTheme) Color(0xFF020617) else Color(0xFFF1F5F9),
            modifier = modifier.fillMaxSize()
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (currentScreen) {
                    Screen.Remote -> {
                        val device = activeDevice
                        if (device != null) {
                            InteractiveRemoteScreen(
                                device = device,
                                isExpandedLayout = isExpandedLayout,
                                isDarkTheme = isDarkTheme,
                                remoteViewModel = remoteViewModel,
                                deviceViewModel = deviceViewModel,
                                onOpenSavedTvs = { currentScreen = Screen.Home },
                                onOpenDiscovery = {
                                    deviceViewModel.startDiscovery()
                                    currentScreen = Screen.Discovery
                                }
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                remoteViewModel.bindUniversalIr()
                            }
                        }
                    }

                    Screen.Home -> {
                        HomeScreen(
                            devices = savedDevices,
                            deviceViewModel = deviceViewModel,
                            remoteViewModel = remoteViewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { remoteViewModel.toggleTheme() },
                            onBackToRemote = { currentScreen = Screen.Remote },
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
                            onPairedDevice = { saved ->
                                remoteViewModel.bindDevice(saved)
                                deviceViewModel.stopDiscovery()
                                currentScreen = Screen.Remote
                            },
                            onDone = {
                                deviceViewModel.stopDiscovery()
                                currentScreen = Screen.Remote
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * INTERACTIVE HYBRID REMOTE SCREEN:
 * Mirrors the exact simulated remote layout from RemoteControl.tsx:
 * - Top glowing IR LED diode dome + carrier label
 * - Top status bar with TV Brand pill (inline 10-brand selector) & Wi-Fi IP pill (inline IP quick-connect)
 * - Master Mode Toggle: [UNIVERSAL IR] vs [SMART TV (WI-FI)]
 * - Universal IR Mode: Active TV card + Power / Carrier kHz / Input row + Horizontal VOL-/MUTE/VOL+ bar + 0-9 Numeric Keypad Matrix
 * - Wi-Fi Smart TV Mode: Circular 224.dp 5-Way D-Pad + Back/Home/Search/Menu row + Search Preset chips + Keyboard/Search bar & QWERTY touch keyboard
 * - Bottom Smart TV App Launch Dock
 */
@Composable
fun InteractiveRemoteScreen(
    device: SavedDevice,
    isExpandedLayout: Boolean,
    isDarkTheme: Boolean,
    remoteViewModel: RemoteControlViewModel,
    deviceViewModel: DeviceManagementViewModel,
    onOpenSavedTvs: () -> Unit,
    onOpenDiscovery: () -> Unit
) {
    val connectionStatus by remoteViewModel.connectionStatus.collectAsState()
    val capabilities by remoteViewModel.capabilities.collectAsState()
    val limitations by remoteViewModel.protocolLimitations.collectAsState()
    val installedApps by remoteViewModel.installedApps.collectAsState()
    val isUniversalIrMode by remoteViewModel.isUniversalIrMode.collectAsState()
    val hasNetworkTarget by remoteViewModel.hasNetworkTarget.collectAsState()
    val selectedBrand by remoteViewModel.selectedIrBrand.collectAsState()
    val hasIrHardware by remoteViewModel.hasIrHardware.collectAsState()
    val lastIrPulseTimestamp by remoteViewModel.lastIrPulseTimestamp.collectAsState()
    val inputText by remoteViewModel.inputText.collectAsState()
    val isTouchpadActive by remoteViewModel.isTouchpadActive.collectAsState()
    val isVirtualKeyboardOpen by remoteViewModel.isVirtualKeyboardOpen.collectAsState()
    val isHapticsEnabled by remoteViewModel.isHapticsEnabled.collectAsState()
    val isHoldToRepeatEnabled by remoteViewModel.isHoldToRepeatEnabled.collectAsState()

    var showInlineBrandSelector by remember { mutableStateOf(false) }
    var showInlineIpConfig by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isLedGlowing by remember { mutableStateOf(false) }
    var showIrDpadMode by remember { mutableStateOf(false) }

    var inlineIp by remember(device.hostIp) {
        mutableStateOf(if (device.protocolType == ProtocolType.CONSUMER_IR) "192.168.1.150" else device.hostIp)
    }
    var inlineProtocol by remember(device.protocolType) {
        mutableStateOf(if (device.protocolType == ProtocolType.CONSUMER_IR) ProtocolType.ADB_NETWORK else device.protocolType)
    }

    LaunchedEffect(lastIrPulseTimestamp) {
        if (lastIrPulseTimestamp > 0L) {
            isLedGlowing = true
            delay(300)
            isLedGlowing = false
        }
    }

    val chassisGradient = if (isDarkTheme) {
        Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF090E1A), Color(0xFF020617)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFF1F5F9)))
    }
    val chassisBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val cardBg = if (isDarkTheme) Color(0xFF0F172A) else Color.White
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val surfaceElevated = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)

    val displayNetworkTabLabel = remember(hasNetworkTarget, device.protocolType) {
        if (!hasNetworkTarget) {
            "SMART TV (WI-FI)"
        } else {
            when (device.protocolType) {
                ProtocolType.ADB_NETWORK -> "FIRE TV (ADB)"
                ProtocolType.SAMSUNG_TIZEN -> "SAMSUNG WI-FI"
                ProtocolType.LG_WEBOS -> "LG WEBOS"
                ProtocolType.ROKU_ECP -> "ROKU WI-FI"
                ProtocolType.ANDROID_TV_REMOTE -> "GOOGLE TV"
                ProtocolType.SONY_BRAVIA -> "SONY BRAVIA"
                ProtocolType.HISENSE_VIDAA -> "HISENSE VIDAA"
                ProtocolType.PANASONIC_VIERA -> "PANASONIC"
                ProtocolType.CONSUMER_IR -> "SMART TV (WI-FI)"
            }
        }
    }

    if (isExpandedLayout) {
        // Dual-Pane Tablet / Unfolded Foldable Adaptive Remote
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Left Pane: Core Remote Chassis
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                RemoteChassisSurface(
                    chassisGradient = chassisGradient,
                    chassisBorder = chassisBorder,
                    modifier = Modifier
                        .widthIn(max = 390.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RemoteChassisHeader(
                            device = device,
                            hasNetworkTarget = hasNetworkTarget,
                            isUniversalIrMode = isUniversalIrMode,
                            selectedBrand = selectedBrand,
                            hasIrHardware = hasIrHardware,
                            isLedGlowing = isLedGlowing,
                            connectionStatus = connectionStatus,
                            networkTabLabel = displayNetworkTabLabel,
                            showBrandSelector = showInlineBrandSelector,
                            showIpConfig = showInlineIpConfig,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            surfaceElevated = surfaceElevated,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onToggleBrandSelector = {
                                showInlineBrandSelector = !showInlineBrandSelector
                                showInlineIpConfig = false
                            },
                            onToggleIpConfig = {
                                showInlineIpConfig = !showInlineIpConfig
                                showInlineBrandSelector = false
                            },
                            onOpenSavedTvs = onOpenSavedTvs,
                            onOpenSettings = { showSettingsDialog = true },
                            onSwitchMode = { useIr -> remoteViewModel.switchRemoteMode(useIr) }
                        )

                        InlineRemoteDrawers(
                            showBrandSelector = showInlineBrandSelector,
                            showIpConfig = showInlineIpConfig,
                            brands = remoteViewModel.availableIrBrands,
                            selectedBrand = selectedBrand,
                            inlineIp = inlineIp,
                            inlineProtocol = inlineProtocol,
                            hasNetworkTarget = hasNetworkTarget,
                            connectionStatus = connectionStatus,
                            isDarkTheme = isDarkTheme,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onSelectBrand = { brand ->
                                remoteViewModel.selectIrBrand(brand)
                                showInlineBrandSelector = false
                            },
                            onCloseBrandSelector = { showInlineBrandSelector = false },
                            onInlineIpChange = { inlineIp = it },
                            onInlineProtocolChange = { inlineProtocol = it },
                            onConnectQuickIp = {
                                deviceViewModel.connectAndSaveQuickIp(inlineIp, inlineProtocol) { saved ->
                                    remoteViewModel.bindDevice(saved)
                                    showInlineIpConfig = false
                                }
                            },
                            onDisconnectNetwork = {
                                remoteViewModel.disconnectNetwork()
                                showInlineIpConfig = false
                            },
                            onOpenDiscovery = {
                                showInlineIpConfig = false
                                onOpenDiscovery()
                            },
                            onOpenSavedTvs = {
                                showInlineIpConfig = false
                                onOpenSavedTvs()
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isUniversalIrMode) {
                            UniversalIrPanel(
                                selectedBrand = selectedBrand,
                                hasIrHardware = hasIrHardware,
                                showIrDpadMode = showIrDpadMode,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                surfaceElevated = surfaceElevated,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onToggleBrandSelector = {
                                    showInlineBrandSelector = true
                                    showInlineIpConfig = false
                                },
                                onToggleDpadMode = { showIrDpadMode = !showIrDpadMode },
                                onCommand = { remoteViewModel.sendCommand(it) },
                                onHoldStart = { remoteViewModel.startRepeatingCommand(it) },
                                onHoldStop = { remoteViewModel.stopRepeatingCommand() }
                            )
                        } else {
                            WifiSmartTvPrimaryControls(
                                hasNetworkTarget = hasNetworkTarget,
                                connectionStatus = connectionStatus,
                                capabilities = capabilities,
                                isTouchpadActive = isTouchpadActive,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                surfaceElevated = surfaceElevated,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onOpenIpConfig = { showInlineIpConfig = true },
                                onOpenDiscovery = onOpenDiscovery,
                                onCommand = { remoteViewModel.sendCommand(it) },
                                onHoldStart = { remoteViewModel.startRepeatingCommand(it) },
                                onHoldStop = { remoteViewModel.stopRepeatingCommand() },
                                onSwipe = { dx, dy -> remoteViewModel.sendCommand(RemoteCommand.Swipe(dx, dy)) },
                                onToggleTouchpad = { remoteViewModel.toggleTouchpad() }
                            )
                        }
                    }
                }
            }

            // Right Pane: Text Input, Touchpad, App Launch Dock & Macros
            RemoteChassisSurface(
                chassisGradient = chassisGradient,
                chassisBorder = chassisBorder,
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
                        text = "KEYBOARD, APPS & MACROS",
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

                    TextInputAndVirtualKeyboardSection(
                        supported = !isUniversalIrMode && capabilities.contains(DeviceCapability.KEYBOARD_TEXT_INJECTION),
                        inputText = inputText,
                        isVirtualKeyboardOpen = isVirtualKeyboardOpen,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
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

                    Spacer(modifier = Modifier.height(16.dp))

                    AppDockSection(
                        apps = installedApps,
                        hasNetworkTarget = hasNetworkTarget,
                        isUniversalIrMode = isUniversalIrMode,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onLaunch = { remoteViewModel.launchApp(it) },
                        onRefresh = { remoteViewModel.refreshInstalledApps() },
                        onConnectWifi = {
                            remoteViewModel.switchRemoteMode(false)
                            showInlineIpConfig = true
                        }
                    )

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
        // Single-Pane Phone Interactive Remote Chassis (Exact Match to RemoteControl.tsx)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            RemoteChassisSurface(
                chassisGradient = chassisGradient,
                chassisBorder = chassisBorder,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RemoteChassisHeader(
                        device = device,
                        hasNetworkTarget = hasNetworkTarget,
                        isUniversalIrMode = isUniversalIrMode,
                        selectedBrand = selectedBrand,
                        hasIrHardware = hasIrHardware,
                        isLedGlowing = isLedGlowing,
                        connectionStatus = connectionStatus,
                        networkTabLabel = displayNetworkTabLabel,
                        showBrandSelector = showInlineBrandSelector,
                        showIpConfig = showInlineIpConfig,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        surfaceElevated = surfaceElevated,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onToggleBrandSelector = {
                            showInlineBrandSelector = !showInlineBrandSelector
                            showInlineIpConfig = false
                        },
                        onToggleIpConfig = {
                            showInlineIpConfig = !showInlineIpConfig
                            showInlineBrandSelector = false
                        },
                        onOpenSavedTvs = onOpenSavedTvs,
                        onOpenSettings = { showSettingsDialog = true },
                        onSwitchMode = { useIr -> remoteViewModel.switchRemoteMode(useIr) }
                    )

                    InlineRemoteDrawers(
                        showBrandSelector = showInlineBrandSelector,
                        showIpConfig = showInlineIpConfig,
                        brands = remoteViewModel.availableIrBrands,
                        selectedBrand = selectedBrand,
                        inlineIp = inlineIp,
                        inlineProtocol = inlineProtocol,
                        hasNetworkTarget = hasNetworkTarget,
                        connectionStatus = connectionStatus,
                        isDarkTheme = isDarkTheme,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onSelectBrand = { brand ->
                            remoteViewModel.selectIrBrand(brand)
                            showInlineBrandSelector = false
                        },
                        onCloseBrandSelector = { showInlineBrandSelector = false },
                        onInlineIpChange = { inlineIp = it },
                        onInlineProtocolChange = { inlineProtocol = it },
                        onConnectQuickIp = {
                            deviceViewModel.connectAndSaveQuickIp(inlineIp, inlineProtocol) { saved ->
                                remoteViewModel.bindDevice(saved)
                                showInlineIpConfig = false
                            }
                        },
                        onDisconnectNetwork = {
                            remoteViewModel.disconnectNetwork()
                            showInlineIpConfig = false
                        },
                        onOpenDiscovery = {
                            showInlineIpConfig = false
                            onOpenDiscovery()
                        },
                        onOpenSavedTvs = {
                            showInlineIpConfig = false
                            onOpenSavedTvs()
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedContent(
                        targetState = isUniversalIrMode,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "RemoteModeSwitch"
                    ) { irMode ->
                        if (irMode) {
                            UniversalIrPanel(
                                selectedBrand = selectedBrand,
                                hasIrHardware = hasIrHardware,
                                showIrDpadMode = showIrDpadMode,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                surfaceElevated = surfaceElevated,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onToggleBrandSelector = {
                                    showInlineBrandSelector = true
                                    showInlineIpConfig = false
                                },
                                onToggleDpadMode = { showIrDpadMode = !showIrDpadMode },
                                onCommand = { remoteViewModel.sendCommand(it) },
                                onHoldStart = { remoteViewModel.startRepeatingCommand(it) },
                                onHoldStop = { remoteViewModel.stopRepeatingCommand() }
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                WifiSmartTvPrimaryControls(
                                    hasNetworkTarget = hasNetworkTarget,
                                    connectionStatus = connectionStatus,
                                    capabilities = capabilities,
                                    isTouchpadActive = isTouchpadActive,
                                    cardBg = cardBg,
                                    cardBorder = cardBorder,
                                    surfaceElevated = surfaceElevated,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    onOpenIpConfig = { showInlineIpConfig = true },
                                    onOpenDiscovery = onOpenDiscovery,
                                    onCommand = { remoteViewModel.sendCommand(it) },
                                    onHoldStart = { remoteViewModel.startRepeatingCommand(it) },
                                    onHoldStop = { remoteViewModel.stopRepeatingCommand() },
                                    onSwipe = { dx, dy -> remoteViewModel.sendCommand(RemoteCommand.Swipe(dx, dy)) },
                                    onToggleTouchpad = { remoteViewModel.toggleTouchpad() }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                TextInputAndVirtualKeyboardSection(
                                    supported = capabilities.contains(DeviceCapability.KEYBOARD_TEXT_INJECTION),
                                    inputText = inputText,
                                    isVirtualKeyboardOpen = isVirtualKeyboardOpen,
                                    cardBg = cardBg,
                                    cardBorder = cardBorder,
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
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AppDockSection(
                        apps = installedApps,
                        hasNetworkTarget = hasNetworkTarget,
                        isUniversalIrMode = isUniversalIrMode,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onLaunch = { remoteViewModel.launchApp(it) },
                        onRefresh = { remoteViewModel.refreshInstalledApps() },
                        onConnectWifi = {
                            remoteViewModel.switchRemoteMode(false)
                            showInlineIpConfig = true
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showSettingsDialog) {
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
            onOpenSavedTvs = {
                showSettingsDialog = false
                onOpenSavedTvs()
            },
            onReconnect = {
                remoteViewModel.reconnectActiveDevice()
                showSettingsDialog = false
            },
            onRePair = {
                deviceViewModel.rePairDevice(device)
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun RemoteChassisSurface(
    chassisGradient: Brush,
    chassisBorder: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(44.dp)
    Box(
        modifier = modifier
            .shadow(20.dp, shape)
            .clip(shape)
            .background(chassisGradient)
            .border(4.dp, chassisBorder, shape)
            .padding(1.dp)
            .border(1.dp, Color(0xFF334155).copy(alpha = 0.45f), shape)
    ) {
        content()
    }
}

@Composable
private fun RemoteChassisHeader(
    device: SavedDevice,
    hasNetworkTarget: Boolean,
    isUniversalIrMode: Boolean,
    selectedBrand: UniversalIrBrand,
    hasIrHardware: Boolean,
    isLedGlowing: Boolean,
    connectionStatus: DeviceOnlineStatus,
    networkTabLabel: String,
    showBrandSelector: Boolean,
    showIpConfig: Boolean,
    cardBg: Color,
    cardBorder: Color,
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onToggleBrandSelector: () -> Unit,
    onToggleIpConfig: () -> Unit,
    onOpenSavedTvs: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchMode: (Boolean) -> Unit
) {
    // Top Infrared LED Blaster Emitter Dome
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(if (isLedGlowing) Color(0xFFF43F5E) else Color(0xFF1E293B))
                .border(
                    width = 2.dp,
                    color = if (isLedGlowing) Color(0xFFFB7185) else Color(0xFF334155),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "CONSUMER IR BLASTER • ${selectedBrand.carrierFrequencyHz / 1000} KHZ",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            color = textSecondary
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Top Status Bar: [Brand Pill] [IP Pill] [Saved TVs] [Settings]
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TV Brand Selector Pill
            Surface(
                onClick = onToggleBrandSelector,
                shape = RoundedCornerShape(12.dp),
                color = if (showBrandSelector) Color(0xFF0C4A6E) else surfaceElevated,
                border = BorderStroke(1.dp, if (showBrandSelector) Color(0xFF38BDF8) else Color(0xFF334155).copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (hasIrHardware) Color(0xFF34D399) else Color(0xFF64748B))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedBrand.name.split(" ")[0],
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${selectedBrand.carrierFrequencyHz / 1000}k",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF7DD3FC)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Wi-Fi IP Pill
                val isWifiOnline = hasNetworkTarget && connectionStatus == DeviceOnlineStatus.ONLINE
                val isAuthReq = hasNetworkTarget && connectionStatus == DeviceOnlineStatus.AUTH_REQUIRED
                val pillBg = when {
                    isWifiOnline -> Color(0xFF052E16)
                    isAuthReq -> Color(0xFF451A03)
                    else -> Color(0xFF3B0A14)
                }
                val pillBorder = when {
                    showIpConfig -> Color(0xFF38BDF8)
                    isWifiOnline -> Color(0xFF22C55E).copy(alpha = 0.5f)
                    isAuthReq -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                    else -> Color(0xFFF43F5E).copy(alpha = 0.45f)
                }
                val pillTextColor = when {
                    isWifiOnline -> Color(0xFF6EE7B7)
                    isAuthReq -> Color(0xFFFCD34D)
                    else -> Color(0xFFFDA4AF)
                }

                Surface(
                    onClick = onToggleIpConfig,
                    shape = CircleShape,
                    color = pillBg,
                    border = BorderStroke(1.dp, pillBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isWifiOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Wi-Fi Connection",
                            tint = if (isWifiOnline) Color(0xFF34D399) else Color(0xFFFB7185),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasNetworkTarget) device.hostIp else "Set IP",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = pillTextColor,
                            maxLines = 1
                        )
                    }
                }

                IconButton(onClick = onOpenSavedTvs, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = "Saved TVs",
                        tint = textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onOpenSettings, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // MASTER MODE TOGGLE: [UNIVERSAL IR] vs [SMART TV (WI-FI)]
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF020617),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // UNIVERSAL IR Tab
            Surface(
                onClick = { onSwitchMode(true) },
                shape = RoundedCornerShape(12.dp),
                color = if (isUniversalIrMode) Color(0xFF1E293B) else Color.Transparent,
                border = if (isUniversalIrMode) BorderStroke(1.dp, Color(0xFFFB7185).copy(alpha = 0.35f)) else null,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = if (isUniversalIrMode) Color(0xFFFB7185) else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "UNIVERSAL IR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUniversalIrMode) Color(0xFFFB7185) else Color(0xFF94A3B8)
                    )
                }
            }

            // SMART TV / WI-FI Tab
            Surface(
                onClick = { onSwitchMode(false) },
                shape = RoundedCornerShape(12.dp),
                color = if (!isUniversalIrMode) Color(0xFF0284C7) else Color.Transparent,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (!isUniversalIrMode) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = networkTabLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isUniversalIrMode) Color.White else Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineRemoteDrawers(
    showBrandSelector: Boolean,
    showIpConfig: Boolean,
    brands: List<UniversalIrBrand>,
    selectedBrand: UniversalIrBrand,
    inlineIp: String,
    inlineProtocol: ProtocolType,
    hasNetworkTarget: Boolean,
    connectionStatus: DeviceOnlineStatus,
    isDarkTheme: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    onSelectBrand: (UniversalIrBrand) -> Unit,
    onCloseBrandSelector: () -> Unit,
    onInlineIpChange: (String) -> Unit,
    onInlineProtocolChange: (ProtocolType) -> Unit,
    onConnectQuickIp: () -> Unit,
    onDisconnectNetwork: () -> Unit,
    onOpenDiscovery: () -> Unit,
    onOpenSavedTvs: () -> Unit
) {
    // Expandable Inline Universal TV Brand Selector (mirrors RemoteControl.tsx lines 199-252)
    AnimatedVisibility(visible = showBrandSelector) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDarkTheme) Color(0xFF0F172A) else Color.White,
            border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Universal TV Remote Setup", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    }
                    IconButton(onClick = onCloseBrandSelector, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = "Select your TV brand & series to calibrate IR pulse frequency and waveform protocol:",
                    fontSize = 10.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    brands.forEach { brand ->
                        val isSelected = brand.id == selectedBrand.id
                        Surface(
                            onClick = { onSelectBrand(brand) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF082F49) else Color(0xFF1E293B).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0EA5E9) else Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(brand.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF0F172A), border = BorderStroke(1.dp, Color(0xFF334155))) {
                                            Text(
                                                "${brand.carrierFrequencyHz / 1000} kHz",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF7DD3FC),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF2E1065).copy(alpha = 0.7f)) {
                                            Text(
                                                brand.protocol.name,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFD8B4FE),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        brand.modelSeries,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Expandable Inline Wi-Fi IP Target Setup (mirrors RemoteControl.tsx lines 255-292)
    AnimatedVisibility(visible = showIpConfig) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDarkTheme) Color(0xFF0F172A) else Color.White,
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Smart TV Wi-Fi Target Setup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Text(
                        "Port ${PROTOCOL_PORT_MAP[inlineProtocol] ?: 5555}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PROTOCOL_PORT_MAP.keys.toList()) { proto ->
                        FilterChip(
                            selected = inlineProtocol == proto,
                            onClick = { onInlineProtocolChange(proto) },
                            label = { Text(proto.name.replace("_", " "), fontSize = 9.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inlineIp,
                        onValueChange = onInlineIpChange,
                        placeholder = { Text("192.168.1.150", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (hasNetworkTarget && connectionStatus == DeviceOnlineStatus.ONLINE) {
                        Button(
                            onClick = onDisconnectNetwork,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Disconnect", fontSize = 11.sp, color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = onConnectQuickIp,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Connect", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenDiscovery,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan Wi-Fi TVs", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenSavedTvs,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Saved TVs", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * UNIVERSAL IR PANEL — exact visual layout match to RemoteControl.tsx lines 322-445:
 * - Active TV Brand selector card
 * - Power (crimson) / Carrier kHz & Protocol pill / Input (amber)
 * - Horizontal VOL- / MUTE / VOL+ bar
 * - Compact CH- / D-PAD / CH+ quick toggle row
 * - Full 0-9 Numeric Keypad Matrix (PREV, 0, ENT)
 */
@Composable
private fun UniversalIrPanel(
    selectedBrand: UniversalIrBrand,
    hasIrHardware: Boolean,
    showIrDpadMode: Boolean,
    cardBg: Color,
    cardBorder: Color,
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onToggleBrandSelector: () -> Unit,
    onToggleDpadMode: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    onHoldStart: (RemoteCommand) -> Unit,
    onHoldStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasIrHardware) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF451A03).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "No IR blaster hardware detected on this phone",
                        fontSize = 10.sp,
                        color = Color(0xFFFDE68A)
                    )
                }
            }
        }

        // Active Universal TV Model Badge & Selector Trigger
        Surface(
            onClick = onToggleBrandSelector,
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0EA5E9).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.35f)),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(selectedBrand.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF082F49),
                                border = BorderStroke(1.dp, Color(0xFF075985))
                            ) {
                                Text(
                                    text = "${selectedBrand.carrierFrequencyHz / 1000} kHz",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF7DD3FC),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = selectedBrand.modelSeries,
                            fontSize = 10.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Change TV", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                }
            }
        }

        // Top Row: POWER / Carrier Pill / INPUT
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteCircleButton(
                icon = Icons.Default.PowerSettingsNew,
                label = "POWER",
                backgroundColor = Color(0xFFE11D48),
                borderColor = Color(0xFFFB7185).copy(alpha = 0.45f),
                tint = Color.White,
                size = 52.dp,
                labelColor = textSecondary,
                onClick = { onCommand(RemoteCommand.Power) }
            )

            Surface(
                shape = CircleShape,
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${selectedBrand.carrierFrequencyHz / 1000}.0 kHz",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textSecondary
                    )
                    Text(
                        selectedBrand.protocol.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFB7185)
                    )
                }
            }

            RemoteCircleButton(
                icon = Icons.Default.Tv,
                label = "INPUT",
                backgroundColor = surfaceElevated,
                borderColor = Color(0xFFF59E0B).copy(alpha = 0.45f),
                tint = Color(0xFFFBBF24),
                size = 52.dp,
                labelColor = textSecondary,
                onClick = { onCommand(RemoteCommand.SourceInput) }
            )
        }

        // Horizontal Volume & Mute Bar (exact match to RemoteControl.tsx lines 384-408)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalActionTile(
                    icon = Icons.Default.VolumeDown,
                    label = "VOL -",
                    tint = textPrimary,
                    onClick = { onCommand(RemoteCommand.VolumeDown) }
                )
                HorizontalActionTile(
                    icon = Icons.Default.VolumeMute,
                    label = "MUTE",
                    tint = Color(0xFFFB7185),
                    onClick = { onCommand(RemoteCommand.Mute) }
                )
                HorizontalActionTile(
                    icon = Icons.Default.VolumeUp,
                    label = "VOL +",
                    tint = textPrimary,
                    onClick = { onCommand(RemoteCommand.VolumeUp) }
                )
            }
        }

        // Compact Channel & Keypad/D-Pad Toggle Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { onCommand(RemoteCommand.ChannelDown) },
                shape = RoundedCornerShape(12.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "CH -",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Surface(
                onClick = onToggleDpadMode,
                shape = RoundedCornerShape(12.dp),
                color = if (showIrDpadMode) Color(0xFF0C4A6E) else cardBg,
                border = BorderStroke(1.dp, if (showIrDpadMode) Color(0xFF38BDF8) else cardBorder),
                modifier = Modifier.weight(1.2f)
            ) {
                Text(
                    text = if (showIrDpadMode) "SHOW KEYPAD" else "IR D-PAD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showIrDpadMode) Color(0xFF7DD3FC) else Color(0xFF38BDF8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Surface(
                onClick = { onCommand(RemoteCommand.ChannelUp) },
                shape = RoundedCornerShape(12.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "CH +",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // Either 0-9 Numeric Keypad Matrix (default) OR IR 5-Way D-Pad + Nav Row
        if (showIrDpadMode) {
            DpadCircularSurface(
                surfaceElevated = surfaceElevated,
                textPrimary = textPrimary,
                onCommand = onCommand,
                onHoldStart = onHoldStart,
                onHoldStop = onHoldStop
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RemoteCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "BACK",
                    backgroundColor = surfaceElevated,
                    borderColor = cardBorder,
                    tint = textPrimary,
                    size = 44.dp,
                    labelColor = textSecondary,
                    onClick = { onCommand(RemoteCommand.Back) }
                )
                RemoteCircleButton(
                    icon = Icons.Default.Home,
                    label = "HOME",
                    backgroundColor = surfaceElevated,
                    borderColor = cardBorder,
                    tint = Color(0xFF38BDF8),
                    size = 44.dp,
                    labelColor = textSecondary,
                    onClick = { onCommand(RemoteCommand.Home) }
                )
                RemoteCircleButton(
                    icon = Icons.Default.Menu,
                    label = "MENU",
                    backgroundColor = surfaceElevated,
                    borderColor = cardBorder,
                    tint = textPrimary,
                    size = 44.dp,
                    labelColor = textSecondary,
                    onClick = { onCommand(RemoteCommand.Menu) }
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NUMERIC KEYPAD MATRIX (${selectedBrand.name.uppercase()})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        letterSpacing = 0.8.sp,
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
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { key ->
                                    when (key) {
                                        in 0..9 -> {
                                            Surface(
                                                onClick = { onCommand(RemoteCommand.Digit(key)) },
                                                shape = RoundedCornerShape(16.dp),
                                                color = surfaceElevated,
                                                border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.7f)),
                                                modifier = Modifier.size(width = 72.dp, height = 44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = key.toString(),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = textPrimary
                                                    )
                                                }
                                            }
                                        }
                                        -1 -> {
                                            Surface(
                                                onClick = { onCommand(RemoteCommand.Back) },
                                                shape = RoundedCornerShape(16.dp),
                                                color = cardBg,
                                                border = BorderStroke(1.dp, cardBorder),
                                                modifier = Modifier.size(width = 72.dp, height = 44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("PREV", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                                                }
                                            }
                                        }
                                        -2 -> {
                                            Surface(
                                                onClick = { onCommand(RemoteCommand.Ok) },
                                                shape = RoundedCornerShape(16.dp),
                                                color = cardBg,
                                                border = BorderStroke(1.dp, cardBorder),
                                                modifier = Modifier.size(width = 72.dp, height = 44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("ENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textSecondary)
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
}

@Composable
private fun HorizontalActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

/**
 * WI-FI SMART TV MODE PANEL — exact visual layout match to RemoteControl.tsx lines 446-540:
 * - Massive 224.dp Circular 5-Way D-Pad with sky-blue gradient OK center
 * - 4-button Nav Row (BACK, HOME, SEARCH highlighted in sky-950, MENU)
 * - Compact Power/Vol/Media pills when supported by active protocol
 */
@Composable
private fun WifiSmartTvPrimaryControls(
    hasNetworkTarget: Boolean,
    connectionStatus: DeviceOnlineStatus,
    capabilities: Set<DeviceCapability>,
    isTouchpadActive: Boolean,
    cardBg: Color,
    cardBorder: Color,
    surfaceElevated: Color,
    textPrimary: Color,
    textSecondary: Color,
    onOpenIpConfig: () -> Unit,
    onOpenDiscovery: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    onHoldStart: (RemoteCommand) -> Unit,
    onHoldStop: () -> Unit,
    onSwipe: (Float, Float) -> Unit,
    onToggleTouchpad: () -> Unit
) {
    if (!hasNetworkTarget || connectionStatus == DeviceOnlineStatus.OFFLINE) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0C4A6E).copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!hasNetworkTarget) "Pair Wi-Fi TV to send commands" else "TV unreachable — check IP or Wi-Fi",
                    fontSize = 10.sp,
                    color = Color(0xFFBAE6FD),
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Set IP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenIpConfig)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "Scan",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0284C7))
                            .clickable(onClick = onOpenDiscovery)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    // Massive 5-Way D-Pad Wheel (or Touchpad Surface when toggled)
    if (capabilities.contains(DeviceCapability.TOUCHPAD_SWIPE)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = onToggleTouchpad,
                shape = RoundedCornerShape(12.dp),
                color = if (isTouchpadActive) Color(0xFF0284C7) else cardBg,
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTouchpadActive) "D-Pad" else "Touchpad",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTouchpadActive) Color.White else textSecondary
                    )
                }
            }
        }
    }

    AnimatedContent(
        targetState = isTouchpadActive && capabilities.contains(DeviceCapability.TOUCHPAD_SWIPE),
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
        label = "WifiDpadTouchpad"
    ) { showTouchpad ->
        if (showTouchpad) {
            TouchpadSwipeSurface(
                surfaceElevated = surfaceElevated,
                textSecondary = textSecondary,
                onSwipe = onSwipe
            )
        } else {
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

    // Navigation Row: BACK / HOME / SEARCH / MENU
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (capabilities.contains(DeviceCapability.BACK)) {
            RemoteCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "BACK",
                backgroundColor = surfaceElevated,
                borderColor = cardBorder,
                tint = textPrimary,
                size = 44.dp,
                labelColor = textSecondary,
                onClick = { onCommand(RemoteCommand.Back) }
            )
        }
        if (capabilities.contains(DeviceCapability.HOME)) {
            RemoteCircleButton(
                icon = Icons.Default.Home,
                label = "HOME",
                backgroundColor = surfaceElevated,
                borderColor = cardBorder,
                tint = Color(0xFF38BDF8),
                size = 44.dp,
                labelColor = textSecondary,
                onClick = { onCommand(RemoteCommand.Home) }
            )
        }
        RemoteCircleButton(
            icon = Icons.Default.Search,
            label = "SEARCH",
            backgroundColor = Color(0xFF082F49),
            borderColor = Color(0xFF0EA5E9).copy(alpha = 0.55f),
            tint = Color(0xFF7DD3FC),
            size = 44.dp,
            labelColor = Color(0xFF38BDF8),
            onClick = { onCommand(RemoteCommand.Search) }
        )
        if (capabilities.contains(DeviceCapability.MENU)) {
            RemoteCircleButton(
                icon = Icons.Default.Menu,
                label = "MENU",
                backgroundColor = surfaceElevated,
                borderColor = cardBorder,
                tint = textPrimary,
                size = 44.dp,
                labelColor = textSecondary,
                onClick = { onCommand(RemoteCommand.Menu) }
            )
        }
    }

    // Compact Power / Volume / Media Strip when supported by network protocol
    val hasMedia = capabilities.contains(DeviceCapability.MEDIA_PLAY_PAUSE)
    val hasVol = capabilities.contains(DeviceCapability.VOLUME_CONTROL)
    val hasPower = capabilities.contains(DeviceCapability.POWER_TOGGLE) || capabilities.contains(DeviceCapability.POWER_OFF)

    if (hasMedia || hasVol || hasPower) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPower) {
                    IconButton(onClick = { onCommand(RemoteCommand.Power) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power", tint = Color(0xFFFB7185), modifier = Modifier.size(17.dp))
                    }
                }
                if (hasVol) {
                    IconButton(onClick = { onCommand(RemoteCommand.VolumeDown) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.VolumeDown, contentDescription = "Vol -", tint = textPrimary, modifier = Modifier.size(18.dp))
                    }
                    if (capabilities.contains(DeviceCapability.VOLUME_MUTE)) {
                        IconButton(onClick = { onCommand(RemoteCommand.Mute) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.VolumeMute, contentDescription = "Mute", tint = Color(0xFFFB7185), modifier = Modifier.size(17.dp))
                        }
                    }
                    IconButton(onClick = { onCommand(RemoteCommand.VolumeUp) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Vol +", tint = textPrimary, modifier = Modifier.size(18.dp))
                    }
                }
                if (hasMedia) {
                    if (capabilities.contains(DeviceCapability.MEDIA_SEEK)) {
                        IconButton(onClick = { onCommand(RemoteCommand.Rewind) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = textSecondary, modifier = Modifier.size(17.dp))
                        }
                    }
                    IconButton(onClick = { onCommand(RemoteCommand.PlayPause) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color(0xFF38BDF8), modifier = Modifier.size(19.dp))
                    }
                    if (capabilities.contains(DeviceCapability.MEDIA_SEEK)) {
                        IconButton(onClick = { onCommand(RemoteCommand.FastForward) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.FastForward, contentDescription = "Forward", tint = textSecondary, modifier = Modifier.size(17.dp))
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
            .size(224.dp)
            .shadow(14.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF020617), Color(0xFF0F172A), surfaceElevated)
                )
            )
            .border(4.dp, Color(0xFF334155).copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        HoldableIconButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Up",
            tint = textPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(54.dp),
            onHoldStart = { onHoldStart(RemoteCommand.DpadUp) },
            onHoldStop = onHoldStop
        )
        HoldableIconButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Down",
            tint = textPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(54.dp),
            onHoldStart = { onHoldStart(RemoteCommand.DpadDown) },
            onHoldStop = onHoldStop
        )
        HoldableIconButton(
            icon = Icons.Default.KeyboardArrowLeft,
            contentDescription = "Left",
            tint = textPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(54.dp),
            onHoldStart = { onHoldStart(RemoteCommand.DpadLeft) },
            onHoldStop = onHoldStop
        )
        HoldableIconButton(
            icon = Icons.Default.KeyboardArrowRight,
            contentDescription = "Right",
            tint = textPrimary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .size(54.dp),
            onHoldStart = { onHoldStart(RemoteCommand.DpadRight) },
            onHoldStop = onHoldStop
        )

        Surface(
            onClick = { onCommand(RemoteCommand.Ok) },
            shape = CircleShape,
            color = Color(0xFF0284C7),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.45f)),
            modifier = Modifier.size(80.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF3B82F6)))),
                contentAlignment = Alignment.Center
            ) {
                Text("OK", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.5.sp)
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
private fun TextInputAndVirtualKeyboardSection(
    supported: Boolean,
    inputText: String,
    isVirtualKeyboardOpen: Boolean,
    cardBg: Color,
    cardBorder: Color,
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

    Column(modifier = Modifier.fillMaxWidth()) {
        // Quick Search Preset Chips (always mirrors RemoteControl.tsx lines 541-552)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(SEARCH_PRESETS) { preset ->
                Surface(
                    onClick = { onSendPreset(preset) },
                    shape = CircleShape,
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Text(
                        text = preset,
                        fontSize = 10.sp,
                        color = if (supported) textPrimary else textSecondary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Persistent Floating Keyboard & Search Input Field
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSearch, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search on TV",
                            tint = if (supported) Color(0xFF38BDF8) else textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        enabled = supported,
                        placeholder = {
                            Text(
                                text = if (supported) "Type or search on TV..." else "Connect Wi-Fi TV to type...",
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
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (supported && inputText.isNotEmpty()) {
                        IconButton(onClick = { onInputTextChange("") }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = textSecondary, modifier = Modifier.size(14.dp))
                        }
                    }

                    IconButton(onClick = onBackspace, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Backspace, contentDescription = "Backspace", tint = Color(0xFFFB7185), modifier = Modifier.size(15.dp))
                    }

                    IconButton(onClick = onToggleVirtualKeyboard, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = "Toggle Virtual Keyboard",
                            tint = if (isVirtualKeyboardOpen) Color(0xFF38BDF8) else textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Surface(
                        onClick = {
                            if (supported && inputText.isNotBlank()) {
                                onSubmitText()
                                keyboardController?.hide()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (supported) Color(0xFF0284C7) else Color(0xFF1E293B)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }

                // Expandable Virtual Touch Keyboard
                AnimatedVisibility(visible = supported && isVirtualKeyboardOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(color = cardBorder, modifier = Modifier.padding(bottom = 4.dp))
                        val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
                        rows.forEachIndexed { idx, keys ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth(if (idx == 0) 1f else if (idx == 1) 0.92f else 0.80f)
                            ) {
                                keys.forEach { ch ->
                                    Surface(
                                        onClick = { onKeyChar(ch.toString()) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = surfaceElevated,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(ch.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        ) {
                            Surface(
                                onClick = onBackspace,
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF4C0519),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("DEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDA4AF))
                                }
                            }
                            Surface(
                                onClick = { onKeyChar(" ") },
                                shape = RoundedCornerShape(6.dp),
                                color = surfaceElevated,
                                modifier = Modifier
                                    .weight(2.4f)
                                    .height(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("SPACE", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                                }
                            }
                            Surface(
                                onClick = onEnter,
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0284C7),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(30.dp)
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
            .height(210.dp)
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
    hasNetworkTarget: Boolean,
    isUniversalIrMode: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    onLaunch: (InstalledTvApp) -> Unit,
    onRefresh: () -> Unit,
    onConnectWifi: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = cardBorder, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "SMART TV APP DOCK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    letterSpacing = 0.8.sp
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps", tint = textSecondary, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (apps.isEmpty()) {
            Surface(
                onClick = onConnectWifi,
                shape = RoundedCornerShape(12.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isUniversalIrMode || !hasNetworkTarget) {
                        "Connect a Wi-Fi Smart TV (e.g. Fire TV, Roku, webOS, Bravia) to load & launch installed TV apps."
                    } else {
                        "Tap Refresh after connecting to load installed TV apps."
                    },
                    fontSize = 10.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }
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
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.width(68.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badgeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(app.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
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
    borderColor: Color,
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
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(size * 0.42f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = labelColor)
    }
}

/**
 * SAVED TVS MANAGER SCREEN (accessible from Remote header Saved TVs icon)
 */
@Composable
fun HomeScreen(
    devices: List<SavedDevice>,
    deviceViewModel: DeviceManagementViewModel,
    remoteViewModel: RemoteControlViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBackToRemote: () -> Unit,
    onOpenUniversalIr: () -> Unit,
    onDeviceClick: (SavedDevice) -> Unit,
    onAddClick: () -> Unit
) {
    val roomFilter by deviceViewModel.selectedRoomFilter.collectAsState()
    var editingDevice by remember { mutableStateOf<SavedDevice?>(null) }

    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val cardBg = if (isDarkTheme) Color(0xFF0F172A) else Color.White
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToRemote) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Remote", tint = textPrimary)
                }
                Column {
                    Text("Saved Televisions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Text("${devices.size} Saved • Universal IR & Network", fontSize = 11.sp, color = textSecondary)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Theme",
                        tint = textSecondary
                    )
                }
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add TV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        OutlinedButton(
            onClick = onOpenUniversalIr,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Switch to Universal IR Remote", fontWeight = FontWeight.SemiBold)
        }

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
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No saved televisions yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Text(
                        "Pair a Wi-Fi Smart TV via mDNS/SSDP or save an IR profile.",
                        fontSize = 12.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))) {
                        Text("Discover Local TVs", color = Color.White)
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
            onTest = { deviceViewModel.testDeviceConnection(dev) },
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.protocolType == ProtocolType.CONSUMER_IR) Icons.Default.SettingsRemote else Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (device.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF0369A1)) {
                            Text("DEFAULT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    text = "${device.brand} • ${device.protocolType.name} • ${device.hostIp}",
                    fontSize = 11.sp,
                    color = textSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Room: ${device.room}", fontSize = 10.sp, color = Color(0xFF64748B))
            }

            IconButton(onClick = onToggleFav) {
                Icon(
                    imageVector = if (device.isFavourite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favourite",
                    tint = if (device.isFavourite) Color(0xFFFBBF24) else Color(0xFF64748B)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * DISCOVERY SCREEN: Local mDNS/SSDP Scan + Universal IR Setup + Multi-Protocol Manual IP Entry.
 */
@Composable
fun DiscoveryScreen(
    deviceViewModel: DeviceManagementViewModel,
    remoteViewModel: RemoteControlViewModel,
    isDarkTheme: Boolean,
    onLaunchUniversalIr: (UniversalIrBrand) -> Unit,
    onPairedDevice: (SavedDevice) -> Unit,
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

        // Local Wi-Fi mDNS & SSDP Auto-Discovery
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
                            Text("Android TV, Fire TV, Roku, Samsung, LG, Sony & Panasonic", fontSize = 10.sp, color = textSecondary)
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
                                        onPairedDevice(
                                            SavedDevice(
                                                id = "${disc.protocolType.name.lowercase()}_${disc.ipAddress.replace('.', '_')}",
                                                name = disc.name,
                                                brand = disc.brand,
                                                modelName = disc.model,
                                                hostIp = disc.ipAddress,
                                                port = disc.port,
                                                protocolType = disc.protocolType,
                                                room = manualRoom
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Pair & Use", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Universal IR Blaster Quick Profile
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
                    text = if (hasIr) "Select TV brand for instant optical IR control." else "No built-in IR emitter detected on this device.",
                    fontSize = 11.sp,
                    color = if (hasIr) textSecondary else Color(0xFFF59E0B),
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(remoteViewModel.availableIrBrands, key = { it.id }) { brand ->
                        FilterChip(
                            selected = brand.id == selectedIrBrand.id,
                            onClick = { selectedIrBrand = brand },
                            label = { Text("${brand.name.split(" ")[0]} (${brand.carrierFrequencyHz / 1000}k)", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onLaunchUniversalIr(selectedIrBrand) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
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
                            onLaunchUniversalIr(selectedIrBrand)
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

        // Manual Wi-Fi Setup
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceBg),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Manual Wi-Fi TV Setup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PROTOCOL_PORT_MAP.keys.toList()) { proto ->
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
                            deviceViewModel.connectAndSaveQuickIp(manualIp.trim(), selectedProtocol) { saved ->
                                onPairedDevice(saved)
                            }
                        }
                    },
                    enabled = manualIp.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect & Save (Port ${PROTOCOL_PORT_MAP[selectedProtocol] ?: 5555})", color = Color.White)
                }
            }
        }
    }
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
                    OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) { Text("Test", fontSize = 11.sp) }
                    OutlinedButton(onClick = onSetDefault, modifier = Modifier.weight(1f)) { Text("Set Default", fontSize = 11.sp) }
                }
                if (device.protocolType != ProtocolType.CONSUMER_IR) {
                    OutlinedButton(onClick = onRePair, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear Saved Keys & Re-Pair", fontSize = 11.sp, color = Color(0xFFF87171))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(device.copy(name = name.trim(), room = room.trim(), hostIp = hostIp.trim())) }) {
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
    onOpenSavedTvs: () -> Unit,
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

                OutlinedButton(onClick = onOpenSavedTvs, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage Saved TVs & Rooms", fontSize = 11.sp)
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
                        OutlinedButton(onClick = onReconnect, modifier = Modifier.weight(1f)) { Text("Reconnect", fontSize = 11.sp) }
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
