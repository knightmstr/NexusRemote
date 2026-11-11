package com.hybridremote.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybridremote.app.network.FireTvManager
import com.hybridremote.app.network.FireTvManager.ConnectionStatus
import com.hybridremote.app.viewmodel.RemoteModeState
import com.hybridremote.app.viewmodel.RemoteUiState
import com.hybridremote.app.viewmodel.RemoteViewModel

/**
 * Root Adaptive Jetpack Compose Screen for the Hybrid Unified TV Remote.
 * Hosts master IR/Wi-Fi toggle, hardware indicators, dynamic mode panels,
 * and the dynamic third-party Fire TV app dock.
 */
@Composable
fun RemoteMainScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showIpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.lastActionFeedback) {
        uiState.lastActionFeedback?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0F1115),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar / Connection Header
            HeaderStatusBar(
                connectionStatus = uiState.connectionStatus,
                targetIp = uiState.savedTargetIp,
                hasIr = uiState.hasIrHardware,
                onIpClicked = { showIpDialog = !showIpDialog }
            )

            AnimatedVisibility(visible = showIpDialog) {
                IpConfigurationCard(
                    currentIp = uiState.savedTargetIp,
                    connectionStatus = uiState.connectionStatus,
                    onConnect = { ip ->
                        viewModel.connectToTv(ip)
                        showIpDialog = false
                    },
                    onDisconnect = {
                        viewModel.disconnectTv()
                        showIpDialog = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Master Mode Toggle: INFRARED vs WI-FI
            MasterModeToggle(
                selectedMode = uiState.remoteMode,
                onModeSelected = { viewModel.setRemoteMode(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Adaptive Control Surface
            AnimatedContent(
                targetState = uiState.remoteMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "RemoteSurfaceTransition"
            ) { mode ->
                when (mode) {
                    RemoteModeState.INFRARED -> {
                        InfraredMatrixPanel(
                            hasIr = uiState.hasIrHardware,
                            onPower = { viewModel.transmitIrPower() },
                            onSource = { viewModel.transmitIrSource() },
                            onVolUp = { viewModel.transmitIrVolumeUp() },
                            onVolDown = { viewModel.transmitIrVolumeDown() },
                            onMute = { viewModel.transmitIrMute() },
                            onDigit = { digit -> viewModel.transmitIrDigit(digit) }
                        )
                    }
                    RemoteModeState.WI_FI -> {
                        WiFiGesturePanel(
                            connectionStatus = uiState.connectionStatus,
                            inputText = uiState.activeInputText,
                            onInputTextChange = { viewModel.setKeyboardInputText(it) },
                            onSubmitText = { viewModel.submitKeyboardInput() },
                            onDpad = { keyCode -> viewModel.sendDpadCommand(keyCode) },
                            onBack = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_BACK) },
                            onHome = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_HOME) },
                            onMenu = { viewModel.sendDpadCommand(FireTvManager.KEYCODE_MENU) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // APP DOCK INTEGRATION: Horizontal LazyRow for Fire TV apps
            AppDockSection(
                apps = uiState.appDockList,
                isWifiMode = uiState.remoteMode == RemoteModeState.WI_FI,
                isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED,
                onAppClick = { packageName -> viewModel.launchApp(packageName) },
                onRefresh = { viewModel.refreshInstalledApps() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Top Status Bar displaying connection state badge, IP address pill, and IR hardware status.
 */
@Composable
private fun HeaderStatusBar(
    connectionStatus: ConnectionStatus,
    targetIp: String,
    hasIr: Boolean,
    onIpClicked: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181C24)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Device Brand & IR Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (hasIr) Color(0xFF00E676) else Color(0xFF757575))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasIr) "IR EMITTER READY" else "NO IR HARDWARE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasIr) Color(0xFF81C784) else Color(0xFF9E9E9E),
                    letterSpacing = 0.5.sp
                )
            }

            // Right: Wi-Fi ADB Target IP & Connection Pill
            Surface(
                onClick = onIpClicked,
                shape = RoundedCornerShape(20.dp),
                color = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> Color(0xFF003314)
                    ConnectionStatus.CONNECTING -> Color(0xFF1A2A44)
                    ConnectionStatus.AUTH_REQUIRED -> Color(0xFF3E2800)
                    ConnectionStatus.DISCONNECTED -> Color(0xFF261214)
                },
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> Color(0xFF00E676)
                        ConnectionStatus.CONNECTING -> Color(0xFF448AFF)
                        ConnectionStatus.AUTH_REQUIRED -> Color(0xFFFFB300)
                        ConnectionStatus.DISCONNECTED -> Color(0xFFFF5252)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Connected",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(14.dp)
                        )
                        ConnectionStatus.CONNECTING -> CircularProgressIndicator(
                            color = Color(0xFF448AFF),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        ConnectionStatus.AUTH_REQUIRED -> Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Auth Required",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp)
                        )
                        ConnectionStatus.DISCONNECTED -> Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Disconnected",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (targetIp.isNotBlank()) targetIp else "Configure IP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Expandable IP Configuration Box to pair with Fire TV ADB TCP/IP.
 */
@Composable
private fun IpConfigurationCard(
    currentIp: String,
    connectionStatus: ConnectionStatus,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    var ipText by remember(currentIp) { mutableStateOf(currentIp) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E232E)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Fire TV ADB Connection (Port 5555)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Ensure ADB Debugging is enabled in Fire TV: Settings > My Fire TV > Developer Options.",
                fontSize = 11.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    placeholder = { Text("e.g. 192.168.1.150", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3D7EFF),
                        unfocusedBorderColor = Color(0xFF384050)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("Disconnect", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = { onConnect(ipText) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Connect", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Master Toggle between Infrared and Wi-Fi modes.
 */
@Composable
private fun MasterModeToggle(
    selectedMode: RemoteModeState,
    onModeSelected: (RemoteModeState) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161922),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B313E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            val modes = listOf(RemoteModeState.INFRARED, RemoteModeState.WI_FI)
            modes.forEach { mode ->
                val isSelected = selectedMode == mode
                val bg = if (isSelected) Color(0xFF2D3748) else Color.Transparent
                val textColor = if (isSelected) Color.White else Color(0xFF8C95A6)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (mode == RemoteModeState.INFRARED) "INFRARED (IR)" else "WI-FI (ADB)",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * INFRARED MODE PANEL:
 * Standard matrix layout with Power, Source Selection, Volume Up/Down, Mute, and full 0-9 Numeric D-Pad.
 */
@Composable
private fun InfraredMatrixPanel(
    hasIr: Boolean,
    onPower: () -> Unit,
    onSource: () -> Unit,
    onVolUp: () -> Unit,
    onVolDown: () -> Unit,
    onMute: () -> Unit,
    onDigit: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141720)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232938)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Control Row: Power & Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TV Power Button (Red)
                RemoteCircleButton(
                    icon = Icons.Default.PowerSettingsNew,
                    label = "POWER",
                    backgroundColor = Color(0xFFC62828),
                    tint = Color.White,
                    onClick = onPower,
                    size = 56.dp
                )

                // TV Source Input Button
                RemoteCircleButton(
                    icon = Icons.Default.SettingsInputHdmi,
                    label = "INPUT",
                    backgroundColor = Color(0xFF2A3142),
                    tint = Color(0xFFFFB300),
                    onClick = onSource,
                    size = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Volume & Mute Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteCircleButton(
                    icon = Icons.Default.VolumeDown,
                    label = "VOL -",
                    backgroundColor = Color(0xFF1E2433),
                    tint = Color.White,
                    onClick = onVolDown,
                    size = 52.dp
                )

                RemoteCircleButton(
                    icon = Icons.Default.VolumeMute,
                    label = "MUTE",
                    backgroundColor = Color(0xFF372727),
                    tint = Color(0xFFFF7043),
                    onClick = onMute,
                    size = 52.dp
                )

                RemoteCircleButton(
                    icon = Icons.Default.VolumeUp,
                    label = "VOL +",
                    backgroundColor = Color(0xFF1E2433),
                    tint = Color.White,
                    onClick = onVolUp,
                    size = 52.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 0-9 Full Numeric D-Pad Grid
            Text(
                text = "NUMERIC KEYPAD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6B7280),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val numberGrid = listOf(
                listOf(1, 2, 3),
                listOf(4, 5, 6),
                listOf(7, 8, 9),
                listOf(-1, 0, -2) // -1 is spacer/clear, -2 is enter
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                numberGrid.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { digit ->
                            when (digit) {
                                in 0..9 -> {
                                    NumberKeyButton(digit = digit, onClick = { onDigit(digit) })
                                }
                                -1 -> {
                                    NumberAuxButton(label = "PREV", onClick = { onDigit(0) })
                                }
                                -2 -> {
                                    NumberAuxButton(label = "ENT", onClick = { onDigit(0) })
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
private fun NumberKeyButton(digit: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF1C2230),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E374A)),
        modifier = Modifier.size(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun NumberAuxButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF151922),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF242C3B)),
        modifier = Modifier.size(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7E8B9F)
            )
        }
    }
}

/**
 * WI-FI (ADB) MODE PANEL:
 * Massive 5-way D-Pad gesture surface (Up, Down, Left, Right, Center Click),
 * Navigation buttons (Back, Home, Menu), and floating persistent Keyboard Input.
 */
@Composable
private fun WiFiGesturePanel(
    connectionStatus: ConnectionStatus,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onDpad: (Int) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onMenu: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Massive 5-Way D-Pad Gesture Surface
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF222838), Color(0xFF131722))
                    )
                )
                .border(2.dp, Color(0xFF333D52), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // UP
            IconButton(
                onClick = { onDpad(FireTvManager.KEYCODE_DPAD_UP) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "D-Pad Up",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // DOWN
            IconButton(
                onClick = { onDpad(FireTvManager.KEYCODE_DPAD_DOWN) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "D-Pad Down",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // LEFT
            IconButton(
                onClick = { onDpad(FireTvManager.KEYCODE_DPAD_LEFT) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "D-Pad Left",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // RIGHT
            IconButton(
                onClick = { onDpad(FireTvManager.KEYCODE_DPAD_RIGHT) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "D-Pad Right",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // CENTER SELECT BUTTON (Massive Click Surface)
            Surface(
                onClick = { onDpad(FireTvManager.KEYCODE_DPAD_CENTER) },
                shape = CircleShape,
                color = Color(0xFF2563EB),
                shadowElevation = 8.dp,
                modifier = Modifier.size(92.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "OK",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Navigation Row: Back, Home, Menu
        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavActionButton(
                icon = Icons.Default.ArrowBack,
                label = "BACK",
                onClick = onBack
            )

            NavActionButton(
                icon = Icons.Default.Home,
                label = "HOME",
                onClick = onHome
            )

            NavActionButton(
                icon = Icons.Default.Menu,
                label = "MENU",
                onClick = onMenu
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Persistent Floating Keyboard Input Field
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181C26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C3446)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    placeholder = {
                        Text("Search or inject text to TV...", fontSize = 13.sp, color = Color(0xFF6B7280))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        onSubmitText()
                        keyboardController?.hide()
                    }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        onSubmitText()
                        keyboardController?.hide()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF2563EB), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Inject Text",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color(0xFF1E2330),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2F394D)),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8D99AE)
        )
    }
}

/**
 * APP DOCK INTEGRATION:
 * Horizontal LazyRow acting as a quick-launch dock beneath main controls.
 * Dynamically populated from FireTvManager.installedApps StateFlow.
 */
@Composable
private fun AppDockSection(
    apps: List<String>,
    isWifiMode: Boolean,
    isConnected: Boolean,
    onAppClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FIRE TV APP DOCK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF758296),
                letterSpacing = 1.sp
            )

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Apps",
                    tint = Color(0xFF758296),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (apps.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF151821),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232836)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isConnected) "Scanning installed Fire TV packages..." else "Connect via Wi-Fi to load TV App Dock",
                        fontSize = 12.sp,
                        color = Color(0xFF737D8D),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(apps, key = { it }) { packageName ->
                    AppDockItemChip(
                        packageName = packageName,
                        onClick = { onAppClick(packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDockItemChip(
    packageName: String,
    onClick: () -> Unit
) {
    val simpleName = formatPackageDisplayName(packageName)
    val appColor = getPackageBadgeColor(packageName)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1B202B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C3446)),
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon Avatar with initial
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(appColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = simpleName.take(1).uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = simpleName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RemoteCircleButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    tint: Color,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = backgroundColor,
            shadowElevation = 4.dp,
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
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8895A7)
        )
    }
}

private fun formatPackageDisplayName(pkg: String): String {
    return when {
        pkg.contains("netflix", ignoreCase = true) -> "Netflix"
        pkg.contains("amazonvideo", ignoreCase = true) || pkg.contains("prime", ignoreCase = true) -> "Prime Video"
        pkg.contains("youtube", ignoreCase = true) -> "YouTube"
        pkg.contains("disney", ignoreCase = true) -> "Disney+"
        pkg.contains("plex", ignoreCase = true) -> "Plex"
        pkg.contains("kodi", ignoreCase = true) -> "Kodi"
        pkg.contains("spotify", ignoreCase = true) -> "Spotify"
        pkg.contains("hulu", ignoreCase = true) -> "Hulu"
        pkg.contains("max", ignoreCase = true) || pkg.contains("hbo", ignoreCase = true) -> "Max"
        else -> {
            val segment = pkg.substringAfterLast('.')
            segment.replaceFirstChar { it.uppercase() }
        }
    }
}

private fun getPackageBadgeColor(pkg: String): Color {
    return when {
        pkg.contains("netflix") -> Color(0xFFE50914)
        pkg.contains("amazonvideo") || pkg.contains("prime") -> Color(0xFF00A8E1)
        pkg.contains("youtube") -> Color(0xFFFF0000)
        pkg.contains("disney") -> Color(0xFF113CCF)
        pkg.contains("plex") -> Color(0xFFE5A00D)
        pkg.contains("kodi") -> Color(0xFF17B2E7)
        pkg.contains("spotify") -> Color(0xFF1DB954)
        else -> Color(0xFF3F51B5)
    }
}
