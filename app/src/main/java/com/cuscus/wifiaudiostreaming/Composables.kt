/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package com.cuscus.wifiaudiostreaming

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.data.AutoConnectEntry
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val allMaterialShapes = listOf(
    MaterialShapes.Arch,
    MaterialShapes.Arrow,
    MaterialShapes.Boom,
    MaterialShapes.Bun,
    MaterialShapes.Burst,
    MaterialShapes.Circle,
    MaterialShapes.ClamShell,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Diamond,
    MaterialShapes.Fan,
    MaterialShapes.Flower,
    MaterialShapes.Gem,
    MaterialShapes.Ghostish,
    MaterialShapes.Heart,
    MaterialShapes.Oval,
    MaterialShapes.Pentagon,
    MaterialShapes.Pill,
    MaterialShapes.PixelCircle,
    MaterialShapes.PixelTriangle,
    MaterialShapes.Puffy,
    MaterialShapes.PuffyDiamond,
    MaterialShapes.SemiCircle,
    MaterialShapes.Slanted,
    MaterialShapes.SoftBoom,
    MaterialShapes.SoftBurst,
    MaterialShapes.Square,
    MaterialShapes.Sunny,
    MaterialShapes.Triangle,
    MaterialShapes.VerySunny
)

// Funzione per ottenere una forma casuale
@Composable
fun rememberRandomShape(): RoundedPolygon {
    return remember { allMaterialShapes.random() }
}

// Funzione per ottenere una forma casuale basata su un seed (per consistenza)
@Composable
fun rememberRandomShape(seed: String): RoundedPolygon {
    return remember(seed) {
        val random = Random(seed.hashCode())
        allMaterialShapes[random.nextInt(allMaterialShapes.size)]
    }
}

// === MAIN APP COMPOSABLE ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiAudioStreamingApp(
    appSettings: AppSettings,
    isServer: Boolean,
    isStreaming: Boolean,
    connectionStatus: String,
    discoveredDevices: Map<String, ServerInfo>,
    isMulticastMode: Boolean,
    localIp: String,
    onMulticastModeChange: (Boolean) -> Unit,
    onToggleMode: (Boolean) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onConnectManual: (String) -> Unit,
    onRefresh: () -> Unit,
    onStreamInternalChange: (Boolean) -> Unit,
    onStreamMicChange: (Boolean) -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onChannelConfigChange: (String) -> Unit,
    onBufferSizeChange: (Int) -> Unit,
    onSendClientMicrophoneChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onNetworkInterfaceChange: (String) -> Unit,
    onServerProtocolsChange: (Boolean, Int, Boolean) -> Unit,
    onHttpSettingsChange: (Int, Boolean) -> Unit,
    onToggleAutoConnectIp: (String) -> Unit
) {
    val backgroundGradient by animateColorAsState(
        targetValue = if (isStreaming) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.background,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "Background Gradient"
    )

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.app_name),
                subtitle = connectionStatus,
                isStreaming = isStreaming,
                connectionState = rememberConnectionState(connectionStatus),
                onSettingsClick = onOpenSettings
            )
        },
        containerColor = backgroundGradient
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveModeSelector(
                isServer = isServer,
                onToggleMode = { isServerMode ->
                    onToggleMode(isServerMode)
                    if (!isServerMode) {
                        onRefresh()
                    }
                },
                enabled = !isStreaming,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = isServer && !isStreaming,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn() + scaleIn(initialScale = 0.8f),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                ExpressiveAudioSourceSelector(
                    streamInternal = appSettings.streamInternal,
                    streamMic = appSettings.streamMic,
                    isMulticast = isMulticastMode,
                    rtpEnabled = appSettings.rtpEnabled,
                    onStreamInternalChange = onStreamInternalChange,
                    onStreamMicChange = onStreamMicChange,
                    onMulticastChange = onMulticastModeChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ExpressiveStreamingControlCenter(
                isServer = isServer,
                isStreaming = isStreaming,
                streamInternal = appSettings.streamInternal,
                streamMic = appSettings.streamMic,
                localIp = localIp,
                modifier = Modifier.fillMaxWidth(),
                onStartServer = onStartServer,
                onStopServer = onStopServer,
                sendClientMicrophone = appSettings.sendClientMicrophone
            )

            AnimatedVisibility(
                visible = isServer && isStreaming && appSettings.rtpEnabled,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                ExpressiveRtpSdpBanner(
                    port = appSettings.rtpPort,
                    sampleRate = appSettings.sampleRate,
                    channels = if (appSettings.channelConfig == "STEREO") 2 else 1
                )
            }

            AnimatedVisibility(
                visible = isServer && isStreaming && appSettings.httpEnabled,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                ExpressiveHttpBanner(
                    ip = localIp,
                    port = appSettings.httpPort
                )
            }

            AnimatedVisibility(
                visible = !isServer && !isStreaming,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn() + scaleIn(initialScale = 0.8f),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExpressiveClientSettingsPanel(
                        sendMicrophone = appSettings.sendClientMicrophone,
                        onSendMicrophoneChange = onSendClientMicrophoneChange,
                        modifier = Modifier.fillMaxWidth()
                    )

                    var manualIp by remember { mutableStateOf("") }
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                label = { Text(stringResource(R.string.manual_ip_hint)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                            FilledTonalIconButton(
                                onClick = { onConnectManual(manualIp) },
                                enabled = manualIp.isNotBlank(),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.connect_button_description))
                            }
                        }
                    }

                    ExpressiveDeviceDiscoveryPanel(
                        devices = discoveredDevices,
                        autoConnectList = appSettings.autoConnectList,
                        onConnect = onConnect,
                        onRefresh = onRefresh,
                        onToggleAutoConnectIp = onToggleAutoConnectIp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveClientSettingsPanel(
    sendMicrophone: Boolean,
    onSendMicrophoneChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SettingsVoice,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.client_options_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            ExpressiveAudioSourceToggle(
                icon = Icons.Outlined.MicOff,
                checkedIcon = Icons.Filled.Mic,
                title = stringResource(R.string.send_mic_to_server_title),
                subtitle = stringResource(R.string.send_mic_to_server_subtitle),
                checked = sendMicrophone,
                onCheckedChange = onSendMicrophoneChange
            )
        }
    }
}

@Composable
fun SettingsClickableItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ExpressiveSettingsScreen(
    isVisible: Boolean,
    appSettings: AppSettings,
    onStreamInternalChange: (Boolean) -> Unit,
    onStreamMicChange: (Boolean) -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onChannelConfigChange: (String) -> Unit,
    onBufferSizeChange: (Int) -> Unit,
    onStreamingPortChange: (Int) -> Unit,
    onMicPortChange: (Int) -> Unit,
    onClose: () -> Unit,
    onShowOnboarding: () -> Unit,
    onNetworkInterfaceChange: (String) -> Unit,
    onServerProtocolsChange: (Boolean, Int, Boolean) -> Unit,
    onHttpSettingsChange: (Int, Boolean) -> Unit,
    onClientTileIpChange: (String) -> Unit,
    onAutoConnectEnabledChange: (Boolean) -> Unit,
    onSaveAutoConnectList: (List<AutoConnectEntry>) -> Unit,
    onConnectionSoundChange: (Boolean) -> Unit,
    onDisconnectionSoundChange: (Boolean) -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut()
    ) {
        SettingsScreenContent(
            appSettings = appSettings,
            onStreamInternalChange = onStreamInternalChange,
            onStreamMicChange = onStreamMicChange,
            onSampleRateChange = onSampleRateChange,
            onChannelConfigChange = onChannelConfigChange,
            onBufferSizeChange = onBufferSizeChange,
            onStreamingPortChange = onStreamingPortChange,
            onMicPortChange = onMicPortChange,
            onClose = onClose,
            onShowOnboarding = onShowOnboarding,
            onNetworkInterfaceChange = onNetworkInterfaceChange,
            onServerProtocolsChange = onServerProtocolsChange,
            onHttpSettingsChange = onHttpSettingsChange,
            onClientTileIpChange = onClientTileIpChange,
            onSaveAutoConnectList = onSaveAutoConnectList,
            onAutoConnectEnabledChange = onAutoConnectEnabledChange,
            onConnectionSoundChange = onConnectionSoundChange,
            onDisconnectionSoundChange = onDisconnectionSoundChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    appSettings: AppSettings,
    onStreamInternalChange: (Boolean) -> Unit,
    onStreamMicChange: (Boolean) -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onChannelConfigChange: (String) -> Unit,
    onBufferSizeChange: (Int) -> Unit,
    onStreamingPortChange: (Int) -> Unit,
    onMicPortChange: (Int) -> Unit,
    onClose: () -> Unit,
    onShowOnboarding: () -> Unit,
    onNetworkInterfaceChange: (String) -> Unit,
    onServerProtocolsChange: (Boolean, Int, Boolean) -> Unit,
    onHttpSettingsChange: (Int, Boolean) -> Unit,
    onClientTileIpChange: (String) -> Unit,
    onAutoConnectEnabledChange: (Boolean) -> Unit,
    onSaveAutoConnectList: (List<AutoConnectEntry>) -> Unit,
    onConnectionSoundChange: (Boolean) -> Unit,
    onDisconnectionSoundChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            var fredClickCount by remember { mutableIntStateOf(0) }
            var showFred by remember { mutableStateOf(false) }
            var currentToast by remember { mutableStateOf<Toast?>(null) }

            if (showFred) {
                Dialog(onDismissRequest = { showFred = false }) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.fred),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            fredClickCount++

                            if (fredClickCount < 7) {
                                val toastMessage = when (fredClickCount) {
                                    1 -> context.getString(R.string.fred_hint_1)
                                    2 -> context.getString(R.string.fred_hint_2)
                                    3 -> context.getString(R.string.fred_hint_3)
                                    4 -> context.getString(R.string.fred_hint_4)
                                    5 -> context.getString(R.string.fred_hint_5)
                                    else -> context.getString(R.string.fred_hint_6)
                                }

                                currentToast?.cancel()
                                val toast = Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT)
                                toast.show()
                                currentToast = toast

                            } else {
                                currentToast?.cancel()
                                showFred = true
                                fredClickCount = 0
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_button_description))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_audio_sources),
                    icon = Icons.Outlined.Source
                ) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_internal_audio_title),
                        description = stringResource(R.string.settings_item_internal_audio_desc),
                        icon = Icons.Outlined.MobileScreenShare,
                        isChecked = appSettings.streamInternal,
                        onCheckedChange = onStreamInternalChange
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_mic_title),
                        description = stringResource(R.string.settings_item_mic_desc),
                        icon = Icons.Outlined.Mic,
                        isChecked = appSettings.streamMic,
                        onCheckedChange = onStreamMicChange
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_audio_quality),
                    icon = Icons.Outlined.Tune
                ) {
                    SettingsSelectionItem(
                        title = stringResource(R.string.settings_item_sample_rate_title),
                        description = stringResource(R.string.settings_item_sample_rate_desc),
                        icon = Icons.Outlined.GraphicEq,
                        currentValue = "${appSettings.sampleRate / 1000} kHz",
                        options = mapOf("44.1 kHz" to 44100, "48 kHz" to 48000),
                        onOptionSelected = { onSampleRateChange(it) }
                    )
                    SettingsSelectionItem(
                        title = stringResource(R.string.settings_item_channels_title),
                        description = stringResource(R.string.settings_item_channels_desc),
                        icon = Icons.Outlined.SpeakerGroup,
                        currentValue = appSettings.channelConfig.replaceFirstChar { it.uppercase() },
                        options = mapOf(
                            stringResource(R.string.settings_option_mono) to "MONO",
                            stringResource(R.string.settings_option_stereo) to "STEREO"
                        ),
                        onOptionSelected = { onChannelConfigChange(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_performance),
                    icon = Icons.Outlined.Speed
                ) {
                    val bufferSizeRange = 512f..8192f
                    val bufferStepValue = 256f
                    val bufferSteps = ((bufferSizeRange.endInclusive - bufferSizeRange.start) / bufferStepValue).toInt() - 1

                    SettingsSliderItem(
                        title = stringResource(R.string.settings_item_buffer_size_title),
                        description = stringResource(R.string.settings_item_buffer_size_desc),
                        icon = Icons.Outlined.Timer,
                        value = appSettings.bufferSize.toFloat(),
                        range = bufferSizeRange,
                        steps = bufferSteps,
                        onValueChange = { onBufferSizeChange(it.toInt()) }
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_network),
                    icon = Icons.Outlined.SettingsEthernet
                ) {
                    SettingsTextFieldItem(
                        title = stringResource(R.string.main_audio_port_title),
                        description = stringResource(R.string.main_audio_port_desc),
                        icon = Icons.Outlined.VpnKey,
                        value = appSettings.streamingPort.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let(onStreamingPortChange)
                        }
                    )
                    SettingsTextFieldItem(
                        title = stringResource(R.string.client_mic_port_title),
                        description = stringResource(R.string.client_mic_port_desc),
                        icon = Icons.Outlined.Podcasts,
                        value = appSettings.micPort.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let(onMicPortChange)
                        }
                    )
                    val interfaces = remember {
                        val map = mutableMapOf<String, String>("Auto" to "Auto")
                        try {
                            NetworkInterface.getNetworkInterfaces().toList().forEach { iface ->
                                if (iface.isUp && !iface.isLoopback && iface.inetAddresses.toList().any { it is Inet4Address }) {
                                    map[iface.displayName] = iface.name
                                }
                            }
                        } catch (e: Exception) {}
                        map
                    }

                    SettingsSelectionItem(
                        title = stringResource(R.string.settings_item_network_interface_title),
                        description = stringResource(R.string.settings_item_network_interface_desc),
                        icon = Icons.Outlined.VpnKey,
                        currentValue = appSettings.networkInterface,
                        options = interfaces,
                        onOptionSelected = { onNetworkInterfaceChange(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_server_protocols),
                    icon = Icons.Outlined.Hub
                ) {
                    SettingsInfoItem(
                        title = stringResource(R.string.settings_item_wfas_title),
                        description = stringResource(R.string.settings_item_wfas_desc),
                        painter = painterResource(id = R.drawable.wfas_protocol)
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_rtp_title),
                        description = stringResource(R.string.settings_item_rtp_desc),
                        icon = Icons.Outlined.Radio,
                        isChecked = appSettings.rtpEnabled,
                        onCheckedChange = { onServerProtocolsChange(it, appSettings.rtpPort, appSettings.httpEnabled) }
                    )

                    AnimatedVisibility(visible = appSettings.rtpEnabled) {
                        SettingsTextFieldItem(
                            title = stringResource(R.string.settings_item_rtp_port_title),
                            description = stringResource(R.string.settings_item_rtp_port_desc),
                            icon = Icons.Outlined.VpnKey,
                            value = appSettings.rtpPort.toString(),
                            onValueChange = { portStr ->
                                portStr.toIntOrNull()?.let { port ->
                                    onServerProtocolsChange(appSettings.rtpEnabled, port, appSettings.httpEnabled)
                                }
                            }
                        )
                    }

                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_http_title),
                        description = stringResource(R.string.settings_item_http_desc),
                        icon = Icons.Outlined.Language,
                        isChecked = appSettings.httpEnabled,
                        onCheckedChange = { onServerProtocolsChange(appSettings.rtpEnabled, appSettings.rtpPort, it) }
                    )

                    AnimatedVisibility(visible = appSettings.httpEnabled) {
                        SettingsTextFieldItem(
                            title = stringResource(R.string.settings_item_http_port_title),
                            description = stringResource(R.string.settings_item_http_port_desc),
                            icon = Icons.Outlined.VpnKey,
                            value = appSettings.httpPort.toString(),
                            onValueChange = { portStr ->
                                portStr.toIntOrNull()?.let { port ->
                                    onHttpSettingsChange(port, appSettings.httpSafariMode)
                                }
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.http_latency_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.auto_connect_priorities_title),
                    icon = Icons.Outlined.Autorenew
                ) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.auto_connect_switch_title),
                        description = stringResource(R.string.auto_connect_switch_desc),
                        icon = Icons.Outlined.Autorenew,
                        isChecked = appSettings.autoConnectEnabled,
                        onCheckedChange = onAutoConnectEnabledChange
                    )

                    AnimatedVisibility(visible = appSettings.autoConnectEnabled) {
                        AutoConnectPriorityListManager(
                            autoConnectListString = appSettings.autoConnectList,
                            onListChange = onSaveAutoConnectList
                        )
                    }
                }
            }

            item {
                var tileIpText by remember(appSettings.clientTileIp) { mutableStateOf(appSettings.clientTileIp) }
                val focusManager = LocalFocusManager.current

                SettingsGroupCard(
                    title = stringResource(R.string.settings_tile_title),
                    icon = Icons.Outlined.Widgets
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = tileIpText,
                            onValueChange = { tileIpText = it },
                            label = { Text(stringResource(R.string.settings_tile_ip_label)) },
                            placeholder = { Text(stringResource(R.string.settings_tile_ip_placeholder)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                onClientTileIpChange(tileIpText)
                                focusManager.clearFocus()
                            }),
                            singleLine = true,
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.AddComment, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Text(
                            text = stringResource(R.string.settings_tile_ip_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_sounds),
                    icon = Icons.Outlined.VolumeUp
                ) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_connection_sound_title),
                        description = stringResource(R.string.settings_item_connection_sound_desc),
                        icon = Icons.Outlined.VolumeUp,
                        isChecked = appSettings.connectionSoundEnabled,
                        onCheckedChange = onConnectionSoundChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_disconnection_sound_title),
                        description = stringResource(R.string.settings_item_disconnection_sound_desc),
                        icon = Icons.Outlined.VolumeOff,
                        isChecked = appSettings.disconnectionSoundEnabled,
                        onCheckedChange = onDisconnectionSoundChange
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_about),
                    icon = Icons.Outlined.Info
                ) {
                    SettingsClickableItem(
                        title = stringResource(R.string.settings_item_show_intro_title),
                        description = stringResource(R.string.settings_item_show_intro_desc),
                        icon = Icons.Outlined.HelpOutline,
                        onClick = onShowOnboarding
                    )
                    SettingsInfoItem(
                        title = stringResource(R.string.developer_name),
                        description = "Marco Morosi",
                        icon = Icons.Outlined.Person
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        title = stringResource(R.string.support_kofi_title),
                        description = stringResource(R.string.support_kofi_desc),
                        icon = Icons.Outlined.LocalCafe,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/marcomorosi"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun SettingsTextFieldItem(
    title: String,
    description: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 5) {
                    text = newValue
                }
            },
            label = { Text(title) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = icon, contentDescription = null)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            keyboardActions = KeyboardActions(onDone = {
                onValueChange(text)
                focusManager.clearFocus()
            })
        )
    }
}

@Composable
fun ExpressiveDeviceDiscoveryPanel(
    devices: Map<String, ServerInfo>,
    autoConnectList: String,
    onConnect: (ServerInfo) -> Unit,
    onRefresh: () -> Unit,
    onToggleAutoConnectIp: (String) -> Unit,
    modifier: Modifier
) {
    val haptic = LocalHapticFeedback.current
    val cardElevation by animateFloatAsState(
        targetValue = if (devices.isNotEmpty()) 12f else 6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "Discovery Panel Elevation"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val headerIconScale by animateFloatAsState(targetValue = if (devices.isNotEmpty()) 1.1f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "Discovery Header Icon Scale")
                    Icon(imageVector = Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(28.dp).scale(headerIconScale), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = stringResource(R.string.nearby_devices_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                val refreshRotation by animateFloatAsState(targetValue = if (devices.isEmpty()) 360f else 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "Refresh Button Rotation")
                FilledTonalIconButton(onClick = { onRefresh(); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }, modifier = Modifier.graphicsLayer { rotationZ = refreshRotation }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_button_description), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            AnimatedContent(
                targetState = devices.isEmpty(),
                transitionSpec = { (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))).togetherWith(fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))) },
                label = "Device Discovery Content Animation"
            ) { isEmpty ->
                if (isEmpty) {
                    ExpressiveSearchingIndicator()
                } else {
                    ExpressiveDeviceList(devices = devices, autoConnectList = autoConnectList, onConnect = onConnect, onToggleAutoConnectIp = onToggleAutoConnectIp)
                }
            }
        }
    }
}

@Composable
fun ExpressiveDeviceList(
    devices: Map<String, ServerInfo>,
    autoConnectList: String,
    onConnect: (ServerInfo) -> Unit,
    onToggleAutoConnectIp: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        devices.forEach { (hostname, serverInfo) ->
            ExpressiveDeviceCard(
                hostname = hostname,
                ipAddress = "${serverInfo.ip}:${serverInfo.port}",
                isMulticast = serverInfo.isMulticast,
                isAutoConnectTarget = autoConnectList.contains(serverInfo.ip),
                onConnect = { onConnect(serverInfo) },
                onToggleAutoConnect = { onToggleAutoConnectIp(serverInfo.ip) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveDeviceCard(
    hostname: String,
    ipAddress: String,
    isMulticast: Boolean,
    isAutoConnectTarget: Boolean,
    onConnect: () -> Unit,
    onToggleAutoConnect: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ElevatedCard(
        onClick = {
            onConnect()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = if (isMulticast) Icons.Default.Groups else Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = hostname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ModeTag(isMulticast = isMulticast)
                Text(text = ipAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onToggleAutoConnect(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) {
                Icon(
                    imageVector = if (isAutoConnectTarget) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (isAutoConnectTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.connect_button_description), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // oppure LongPress / Confirm
                onCheckedChange(!isChecked)
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onCheckedChange(it)
            },
            thumbContent = {
                AnimatedContent(
                    targetState = isChecked,
                    transitionSpec = {
                        scaleIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) togetherWith
                                scaleOut(animationSpec = tween(100))
                    },
                    label = "SettingsSwitchThumbIcon"
                ) { checkedState ->
                    Icon(
                        imageVector = if (checkedState) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsSelectionItem(
    title: String,
    description: String,
    icon: ImageVector,
    currentValue: String,
    options: Map<String, T>,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = currentValue, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.open_selection_desc)
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onOptionSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    description: String,
    icon: ImageVector,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val haptic = LocalHapticFeedback.current

    // Ricordiamo l’ultimo intero attraversato per evitare vibrazioni continue
    var lastStep by remember { mutableIntStateOf(value.toInt()) }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.buffer_size_value, sliderValue.toInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Light
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue

                val currentStep = newValue.toInt()
                if (currentStep != lastStep) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastStep = currentStep
                }
            },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onValueChange(sliderValue) },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}


data class ConnectionState(
    val isConnected: Boolean = false,
    val isError: Boolean = false,
    val isWaiting: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        val Connected = ConnectionState(isConnected = true)
        val Waiting = ConnectionState(isWaiting = true)
        fun Error(message: String?) = ConnectionState(isError = true, errorMessage = message)
        val Disconnected = ConnectionState()
    }
}

// Funzione helper per convertire la stringa di stato
@Composable
fun rememberConnectionState(status: String): ConnectionState {
    val connectedStatus = stringResource(R.string.connection_status_connected)
    val errorStatus = stringResource(R.string.connection_status_error)
    val waitingStatus = stringResource(R.string.connection_status_waiting)

    return remember(status) {
        when {
            status.contains(connectedStatus, ignoreCase = true) -> ConnectionState.Connected
            status.contains(errorStatus, ignoreCase = true) -> ConnectionState.Error(status)
            status.contains(waitingStatus, ignoreCase = true) -> ConnectionState.Waiting
            else -> ConnectionState.Disconnected
        }
    }
}

// === EXPRESSIVE TOP APP BAR ===
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTopAppBar(
    title: String,
    subtitle: String,
    isStreaming: Boolean,
    connectionState: ConnectionState,
    onSettingsClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isStreaming -> MaterialTheme.colorScheme.primaryContainer
            connectionState.isConnected -> MaterialTheme.colorScheme.tertiaryContainer
            connectionState.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "TopAppBar Container Color"
    )

    val indicatorColor by animateColorAsState(
        targetValue = when {
            isStreaming -> MaterialTheme.colorScheme.primary
            connectionState.isConnected -> MaterialTheme.colorScheme.tertiary
            connectionState.isError -> MaterialTheme.colorScheme.error
            connectionState.isWaiting -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.outline
        },
        label = "Indicator Color"
    )

    val statusIcon by remember(connectionState, isStreaming) {
        derivedStateOf {
            when {
                isStreaming -> Icons.Default.Podcasts
                connectionState.isConnected -> Icons.Default.Wifi
                connectionState.isError -> Icons.Default.Error
                connectionState.isWaiting -> Icons.Default.Schedule
                else -> Icons.Default.Settings
            }
        }
    }

    // 🔹 Qui sistemato: non più dentro derivedStateOf
    val statusText = when {
        isStreaming -> stringResource(R.string.streaming_active)
        connectionState.isConnected -> stringResource(R.string.connection_status_connected)
        connectionState.isError -> stringResource(R.string.connection_status_error)
        connectionState.isWaiting -> stringResource(R.string.connection_status_waiting)
        else -> stringResource(R.string.settings_title)
    }

    TopAppBar(
        title = {
            Column {
                val titleScale by animateFloatAsState(
                    targetValue = if (isStreaming) 1.05f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "Title Scale"
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.scale(titleScale)
                )

                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        (slideInVertically { height -> height } + fadeIn(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )).togetherWith(
                            slideOutVertically { height -> -height } + fadeOut()
                        )
                    },
                    label = "Subtitle Animation"
                ) { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isStreaming) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        },
        actions = {
            val iconScale by animateFloatAsState(
                targetValue = if (connectionState.isError || connectionState.isWaiting) 1.1f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "Status Icon Scale"
            )

            val isStatusSameAsSettings = statusIcon == Icons.Default.Settings

            if (!isStatusSameAsSettings) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            indicatorColor.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .scale(iconScale),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusText,
                        tint = indicatorColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    )
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor.copy(alpha = 0.95f)
        )
    )
}

// === EXPRESSIVE MODE SELECTOR ===
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveModeSelector(
    isServer: Boolean,
    onToggleMode: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val cardElevation by animateFloatAsState(
        targetValue = if (enabled) 8f else 4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Card Elevation"
    )

    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                val headerIconRotation by animateFloatAsState(
                    targetValue = if (isServer) 180f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "Header Icon Rotation"
                )

                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = headerIconRotation },
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.mode_selector_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                ExpressiveModeButton(
                    icon = Icons.Outlined.Download,
                    selectedIcon = Icons.Filled.Download,
                    title = stringResource(R.string.receive_title),
                    subtitle = stringResource(R.string.receive_subtitle),
                    isSelected = !isServer,
                    onClick = { onToggleMode(false) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                )

                ExpressiveModeButton(
                    icon = Icons.Outlined.Upload,
                    selectedIcon = Icons.Filled.Upload,
                    title = stringResource(R.string.send_title),
                    subtitle = stringResource(R.string.send_subtitle),
                    isSelected = isServer,
                    onClick = { onToggleMode(true) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveModeButton(
    icon: ImageVector,
    selectedIcon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapes()
) {
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Mode Button Scale"
    )

    ToggleButton(
        checked = isSelected,
        onCheckedChange = {
            onClick()
            haptic.performHapticFeedback(
                if (isSelected) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        },
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shapes = shapes
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp)
        ) {
            AnimatedContent(
                targetState = isSelected,
                transitionSpec = {
                    scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ).togetherWith(
                        scaleOut(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        )
                    )
                },
                label = "Mode Icon Animation"
            ) { selected ->
                Icon(
                    imageVector = if (selected) selectedIcon else icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// === EXPRESSIVE AUDIO SOURCE SELECTOR ===
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveAudioSourceSelector(
    streamInternal: Boolean,
    streamMic: Boolean,
    isMulticast: Boolean,
    rtpEnabled: Boolean, // <-- NUOVO PARAMETRO AGGIUNTO
    onStreamInternalChange: (Boolean) -> Unit,
    onStreamMicChange: (Boolean) -> Unit,
    onMulticastChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Source,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.source_selector_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            ExpressiveAudioSourceToggle(
                icon = Icons.Outlined.Speaker,
                checkedIcon = Icons.Filled.Speaker,
                title = stringResource(R.string.internal_audio_title),
                subtitle = stringResource(R.string.internal_audio_subtitle),
                checked = streamInternal,
                onCheckedChange = onStreamInternalChange
            )
            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveAudioSourceToggle(
                icon = Icons.Outlined.Mic,
                checkedIcon = Icons.Filled.Mic,
                title = stringResource(R.string.microphone_title),
                subtitle = stringResource(R.string.microphone_subtitle),
                checked = streamMic,
                onCheckedChange = onStreamMicChange
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WifiTethering,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.transmission_mode_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // QUI APPLICHIAMO IL BLOCCO:
            ExpressiveStreamingModeToggle(
                checked = isMulticast || rtpEnabled, // Forza il multicast se RTP è on
                enabled = !rtpEnabled,               // Blocca lo switch se RTP è on
                onCheckedChange = onMulticastChange
            )
        }
    }
}

@Composable
fun ExpressiveStreamingModeToggle(
    checked: Boolean, // true per Multicast, false per Singolo Client
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val title = if (checked) stringResource(R.string.multicast_mode_title) else stringResource(R.string.unicast_mode_title)

    // Logica per il sottotitolo personalizzato se l'RTP è attivo
    val subtitle = if (!enabled) stringResource(R.string.rtp_forces_multicast)
    else if (checked) stringResource(R.string.multicast_mode_desc)
    else stringResource(R.string.unicast_mode_desc)

    val icon = if (checked) Icons.Outlined.Groups else Icons.Outlined.Person
    val checkedIcon = if (checked) Icons.Filled.Groups else Icons.Filled.Person

    val rowScale by animateFloatAsState(
        targetValue = if (checked) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Streaming Mode Row Scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(28.dp))
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 0.3f else 0.15f)
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
            .padding(16.dp)
            .scale(rowScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)).togetherWith(scaleOut())
            },
            label = "Streaming Mode Icon Animation"
        ) { isChecked ->
            Icon(
                imageVector = if (isChecked) checkedIcon else icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.5f)
                else MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f)
            )
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn())
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut())
                },
                label = "Streaming Mode Subtitle"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                onCheckedChange(it)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            thumbContent = {
                AnimatedContent(targetState = checked, label = "Switch Thumb Icon") { isChecked ->
                    Icon(
                        imageVector = if (isChecked) Icons.Default.Groups else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            }
        )
    }
}

@Composable
fun ExpressiveAudioSourceToggle(
    icon: ImageVector,
    checkedIcon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val rowScale by animateFloatAsState(
        targetValue = if (checked) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Audio Source Row Scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(28.dp))
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .padding(16.dp)
            .scale(rowScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ).togetherWith(scaleOut())
            },
            label = "Audio Source Icon Animation"
        ) { isChecked ->
            Icon(
                imageVector = if (isChecked) checkedIcon else icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isChecked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = {
                onCheckedChange(it)
                haptic.performHapticFeedback(
                    if (it) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                )
            },
            thumbContent = {
                AnimatedContent(
                    targetState = checked,
                    transitionSpec = {
                        scaleIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) togetherWith
                                scaleOut(animationSpec = tween(100))
                    },
                    label = "SwitchThumbIcon"
                ) { isChecked ->
                    Icon(
                        imageVector = if (isChecked) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            }
        )
    }
}

// === EXPRESSIVE STREAMING CONTROL CENTER ===
@Composable
fun ExpressiveStreamingControlCenter(
    isServer: Boolean,
    isStreaming: Boolean,
    streamInternal: Boolean,
    streamMic: Boolean,
    localIp: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    modifier: Modifier,
    sendClientMicrophone: Boolean = false
) {
    val cardColor by animateColorAsState(
        targetValue = if (isStreaming)
            MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "Control Center Card Color"
    )

    val cardElevation by animateFloatAsState(
        targetValue = if (isStreaming) 12f else 6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Control Center Card Elevation"
    )

    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation.dp)
    ) {
        AnimatedContent(
            targetState = isStreaming,
            transitionSpec = {
                (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                        scaleIn(
                            initialScale = 0.8f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )).togetherWith(
                    fadeOut(animationSpec = tween(200)) +
                            scaleOut(targetScale = 0.8f, animationSpec = tween(200))
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            contentAlignment = Alignment.Center,
            label = "Streaming Control Animation"
        ) { streaming ->
            if (streaming) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExpressiveStreamingActiveIndicator(onStopServer = onStopServer)

                    if (!isServer && sendClientMicrophone) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val isMicMuted by NetworkManager.isMicMuted.collectAsState()
                        FilledTonalButton(
                            onClick = { NetworkManager.isMicMuted.value = !isMicMuted },
                            colors = if (isMicMuted) ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) else ButtonDefaults.filledTonalButtonColors(),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(
                                if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(if (isMicMuted) R.string.mic_muted else R.string.mic_active))
                        }
                    }

                    // --- NUOVO SLIDER VOLUME SU ANDROID ---
                    if (isServer) {
                        Spacer(modifier = Modifier.height(24.dp))
                        val volume by NetworkManager.serverVolume.collectAsState()

                        Text(
                            text = stringResource(R.string.transmission_volume, (volume * 100).toInt()),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = volume,
                            onValueChange = { NetworkManager.serverVolume.value = it },
                            valueRange = 0f..2f, // Da Muto a 200%
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }
                    // --------------------------------------
                }
            } else if (isServer) {
                ExpressiveServerControls(
                    streamInternal = streamInternal,
                    streamMic = streamMic,
                    localIp = localIp,
                    onStartServer = onStartServer
                )
            } else {
                ExpressiveClientIdleIndicator()
            }
        }
    }
}
@Composable
fun ExpressiveStreamingActiveIndicator(onStopServer: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "Streaming Active")
        val wavyScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Wavy Scale"
        )

        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Streaming Pulse"
        )

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    )
                )
                .scale(wavyScale)
                .graphicsLayer { alpha = pulse },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.RadioButtonChecked,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.streaming_active),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(28.dp))

        FilledTonalButton(
            onClick = {
                onStopServer()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.stop_streaming_button))
        }
    }
}

@Composable
fun ExpressiveServerControls(
    streamInternal: Boolean,
    streamMic: Boolean,
    localIp: String,
    onStartServer: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isReady = streamInternal || streamMic

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        val readyScale by animateFloatAsState(
            targetValue = if (isReady) 1f else 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "Server Ready Scale"
        )

        val iconColor by animateColorAsState(
            targetValue = if (isReady) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            animationSpec = spring(),
            label = "Server Icon Color"
        )

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (isReady) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
                )
                .scale(readyScale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.WifiTethering,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = iconColor
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isReady) stringResource(R.string.ready_to_stream) else stringResource(R.string.select_source_text),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (isReady) {
            Text(
                text = stringResource(R.string.server_ip_format, localIp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (isReady) {
            Text(
                text = stringResource(R.string.server_ready_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                onStartServer()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            enabled = isReady,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            AnimatedContent(
                targetState = isReady,
                transitionSpec = {
                    scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ).togetherWith(scaleOut())
                },
                label = "Start Button Icon Animation"
            ) { ready ->
                Icon(
                    imageVector = if (ready) Icons.Default.PlayArrow else Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isReady) stringResource(R.string.start_server_button) else stringResource(R.string.select_source_text),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveClientIdleIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        LoadingIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.waiting_for_server),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.find_device_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSearchingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        LoadingIndicator(
            modifier = Modifier.size(56.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.searching_indicator_text),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.searching_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
fun ModeTag(isMulticast: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isMulticast) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = spring(),
        label = "TagBackgroundColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isMulticast) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = spring(),
        label = "TagContentColor"
    )

    Box(
        modifier = Modifier
            .wrapContentWidth(Alignment.Start)
            .clip(CircleShape)
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isMulticast) stringResource(R.string.mode_multicast) else stringResource(R.string.mode_unicast),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveDeviceCard(
    hostname: String,
    ipAddress: String,
    isMulticast: Boolean,
    onConnect: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ElevatedCard(
        onClick = {
            onConnect()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMulticast) Icons.Default.Groups else Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // --- MODIFICA CHIAVE: Layout verticale per le informazioni ---
            Column(
                modifier = Modifier.weight(1f),
                // Aggiunge uno spazio di 4.dp tra ogni elemento nella colonna
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Nome Host
                Text(
                    text = hostname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 2. Etichetta (Tag) Unicast/Multicast
                ModeTag(isMulticast = isMulticast)

                // 3. Indirizzo IP
                Text(
                    text = ipAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // --- FINE MODIFICA ---

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.connect_button_description),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// === BUTTON GROUP DEFAULTS (Material 3 Expressive) ===
object ButtonGroupDefaults {
    val ConnectedSpaceBetween = 0.dp

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun connectedLeadingButtonShapes() = ToggleButtonDefaults.shapes(
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 4.dp,
            bottomStart = 28.dp,
            bottomEnd = 4.dp
        )
    )

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun connectedTrailingButtonShapes() = ToggleButtonDefaults.shapes(
        shape = RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 28.dp,
            bottomStart = 4.dp,
            bottomEnd = 28.dp
        )
    )
}

/**
 * Composable helper per mostrare informazioni statiche nelle impostazioni.
 */
@Composable
fun SettingsInfoItem(
    title: String,
    description: String,
    icon: ImageVector
) {
    SettingsInfoItem(
        title = title,
        description = description,
        painter = rememberVectorPainter(icon)
    )
}

@Composable
fun SettingsInfoItem(
    title: String,
    description: String,
    painter: Painter
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onOnboardingFinished: () -> Unit) {
    val pagerState = rememberPagerState { 3 }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            OnboardingNavigation(
                pagerState = pagerState,
                onNextClick = {
                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onOnboardingFinished()
                    }
                },
                onSkipClick = onOnboardingFinished
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalAlignment = Alignment.Top
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> FeaturesPage()
                2 -> ProtocolsPage()
            }
        }
    }
}

@Composable
fun WelcomePage() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val link = "https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop" //TODO link github

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.ImportantDevices,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    context.startActivity(intent)
                },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.onboarding_download_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.onboarding_download_link, link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(2f))
    }
}

@Composable
fun FeaturesPage() {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.onboarding_features_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_features_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            FeatureRow(
                icon = Icons.Default.Download,
                title = stringResource(R.string.onboarding_feature1_title),
                description = stringResource(R.string.onboarding_feature1_desc),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            )
            FeatureRow(
                icon = Icons.Default.Upload,
                title = stringResource(R.string.onboarding_feature2_title),
                description = stringResource(R.string.onboarding_feature2_desc),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            )
            FeatureRow(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.onboarding_feature3_title),
                description = stringResource(R.string.onboarding_feature3_desc),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            )
        }
    }
}

@Composable
fun ProtocolsPage() {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.Hub,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.onboarding_protocols_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_protocols_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureRow(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.onboarding_proto1_title),
                description = stringResource(R.string.onboarding_proto1_desc),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            )
            FeatureRow(
                icon = Icons.Default.Radio,
                title = stringResource(R.string.onboarding_proto2_title),
                description = stringResource(R.string.onboarding_proto2_desc),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            )
            FeatureRow(
                icon = Icons.Default.Language,
                title = stringResource(R.string.onboarding_proto3_title),
                description = stringResource(R.string.onboarding_proto3_desc),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingNavigation(
    pagerState: PagerState,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSkipClick()
            },
            content = {
                if (pagerState.currentPage < pagerState.pageCount - 1) {
                    Text(stringResource(id = R.string.skip))
                }
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(pagerState.pageCount) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color)
                        .size(10.dp)
                )
            }
        }

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNextClick()
            },
            shape = RoundedCornerShape(16.dp)
        ) {
            AnimatedContent(
                targetState = pagerState.currentPage == pagerState.pageCount - 1,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                },
                label = "Button Text Animation"
            ) { isLastPage ->
                if (isLastPage) {
                    Text(stringResource(id = R.string.start))
                } else {
                    Text(stringResource(id = R.string.next))
                }
            }
        }
    }
}

@Composable
fun ExpressiveRtpSdpBanner(
    port: Int,
    sampleRate: Int,
    channels: Int
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val sdpContent = """
        v=0
        o=- 0 0 IN IP4 127.0.0.1
        s=WiFiAudioStreaming RTP
        c=IN IP4 239.255.0.1
        t=0 0
        m=audio $port RTP/AVP 96
        a=rtpmap:96 L16/$sampleRate/$channels
    """.trimIndent()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/sdp")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                out.write(sdpContent.toByteArray())
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_item_rtp_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(stringResource(R.string.rtp_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(sdpContent))
                        copied = true
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                ) {
                    Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy_sdp))
                }
                FilledTonalIconButton(
                    onClick = { launcher.launch("stream.sdp") },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = stringResource(R.string.save_sdp))
                }
            }
        }
    }

    if (copied) {
        LaunchedEffect(Unit) {
            delay(2000)
            copied = false
        }
    }
}

@Composable
fun SettingsCodecSelectorItem(
    title: String,
    description: String,
    icon: ImageVector,
    isSafariMode: Boolean,
    onCodecChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        // Intestazione (Sopra)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Contenitore Pulsanti (Sotto, orizzontale)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CodecAnimatedButton(
                text = stringResource(R.string.codec_opus),
                selected = !isSafariMode,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCodecChange(false)
                },
                modifier = Modifier.weight(1f)
            )

            CodecAnimatedButton(
                text = stringResource(R.string.codec_aac),
                selected = isSafariMode,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCodecChange(true)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AutoConnectPriorityListManager(
    autoConnectListString: String,
    onListChange: (List<AutoConnectEntry>) -> Unit
) {
    var localList by remember(autoConnectListString) { mutableStateOf(AutoConnectEntry.parseList(autoConnectListString)) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.auto_connect_priority_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        localList.forEachIndexed { index, entry ->
            var localIp by remember(index) { mutableStateOf(entry.ip) }
            var localSsid by remember(index) { mutableStateOf(entry.ssid) }
            val focusManager = LocalFocusManager.current

            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = localIp,
                            onValueChange = {
                                localIp = it
                                localList = localList.toMutableList().also { l -> l[index] = entry.copy(ip = it, ssid = localSsid) }
                            },
                            label = { Text(stringResource(R.string.auto_connect_ip_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onListChange(localList)
                                focusManager.clearFocus()
                            }),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) }
                        )
                        OutlinedTextField(
                            value = localSsid,
                            onValueChange = {
                                localSsid = it
                                localList = localList.toMutableList().also { l -> l[index] = entry.copy(ip = localIp, ssid = it) }
                            },
                            label = { Text(stringResource(R.string.auto_connect_ssid_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onListChange(localList)
                                focusManager.clearFocus()
                            }),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        Toast.makeText(context, context.getString(R.string.auto_connect_wifi_hint), Toast.LENGTH_SHORT).show()
                                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = stringResource(R.string.auto_connect_use_current_wifi),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val newList = localList.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index - 1]
                                    newList[index - 1] = temp
                                    localList = newList
                                    onListChange(newList)
                                }
                            },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.auto_connect_move_up))
                        }
                        IconButton(
                            onClick = {
                                if (index < localList.size - 1) {
                                    val newList = localList.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index + 1]
                                    newList[index + 1] = temp
                                    localList = newList
                                    onListChange(newList)
                                }
                            },
                            enabled = index < localList.size - 1
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.auto_connect_move_down))
                        }
                        IconButton(
                            onClick = {
                                val newList = localList.toMutableList()
                                newList.removeAt(index)
                                localList = newList
                                onListChange(newList)
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.auto_connect_remove), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        TextButton(
            onClick = {
                val newList = localList.toMutableList()
                newList.add(AutoConnectEntry("", ""))
                localList = newList
                onListChange(newList)
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.auto_connect_add_button))
        }
    }
}

@Composable
private fun CodecAnimatedButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Fisica dell'animazione: Bouncy e fluida
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = spring(),
        label = "button_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(),
        label = "button_text_color"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}
@Composable
fun SettingsGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
fun ExpressiveHttpBanner(ip: String, port: Int) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val url = "http://$ip:$port"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_item_http_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(url))
                        copied = true
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                ) {
                    Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, contentDescription = "Copia URL")
                }
                FilledTonalIconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                ) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Apri nel browser")
                }
            }
        }
    }

    if (copied) {
        LaunchedEffect(Unit) {
            delay(2000)
            copied = false
        }
    }
}