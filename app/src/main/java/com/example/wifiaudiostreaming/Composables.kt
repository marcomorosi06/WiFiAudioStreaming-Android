package com.example.wifiaudiostreaming

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.example.wifiaudiostreaming.data.AppSettings
import kotlin.random.Random
import com.example.wifiaudiostreaming.ServerInfo
import kotlinx.coroutines.launch

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
    onMulticastModeChange: (Boolean) -> Unit,
    onToggleMode: (Boolean) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onRefresh: () -> Unit,
    onStreamInternalChange: (Boolean) -> Unit,
    onStreamMicChange: (Boolean) -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onChannelConfigChange: (String) -> Unit,
    onBufferSizeChange: (Int) -> Unit,
    onSendClientMicrophoneChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
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
                    experimentalFeaturesEnabled = appSettings.experimentalFeaturesEnabled, // <-- NUOVO
                    isMulticast = isMulticastMode,
                    onStreamInternalChange = onStreamInternalChange,
                    onStreamMicChange = onStreamMicChange,
                    onMulticastChange = onMulticastModeChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // PANNELLO OPZIONI CLIENT ORA DIPENDE DALLE FUNZIONI SPERIMENTALI
            AnimatedVisibility(
                visible = !isServer && !isStreaming && appSettings.experimentalFeaturesEnabled, // <-- MODIFICATO
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
                ExpressiveClientSettingsPanel(
                    sendMicrophone = appSettings.sendClientMicrophone,
                    onSendMicrophoneChange = onSendClientMicrophoneChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ExpressiveStreamingControlCenter(
                isServer = isServer,
                isStreaming = isStreaming,
                streamInternal = appSettings.streamInternal,
                streamMic = appSettings.streamMic,
                modifier = Modifier.fillMaxWidth(),
                onStartServer = onStartServer,
                onStopServer = onStopServer
            )

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
                ExpressiveDeviceDiscoveryPanel(
                    devices = discoveredDevices,
                    onConnect = onConnect,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                )
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
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
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


/**
 * Composable helper per elementi cliccabili che mantengono lo stile della UI.
 */
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
    onExperimentalFeaturesChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onShowOnboarding: () -> Unit // Lambda per mostrare l'onboarding
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
            onExperimentalFeaturesChange = onExperimentalFeaturesChange,
            onClose = onClose,
            onShowOnboarding = onShowOnboarding // Passa la lambda al content
        )
    }
}

// NUOVO: AlertDialog per avviso funzioni sperimentali
@Composable
fun ExperimentalFeaturesWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Science, contentDescription = null) },
        title = { Text(stringResource(R.string.experimental_warning_title)) },
        text = { Text(stringResource(R.string.experimental_warning_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.experimental_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
    onExperimentalFeaturesChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onShowOnboarding: () -> Unit
) {
    var showExperimentalWarningDialog by remember { mutableStateOf(false) }

    if (showExperimentalWarningDialog) {
        ExperimentalFeaturesWarningDialog(
            onDismiss = { showExperimentalWarningDialog = false },
            onConfirm = {
                onExperimentalFeaturesChange(true)
                showExperimentalWarningDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
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
                    AnimatedVisibility(visible = appSettings.experimentalFeaturesEnabled) {
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_item_mic_title),
                            description = stringResource(R.string.settings_item_mic_desc),
                            icon = Icons.Outlined.Mic,
                            isChecked = appSettings.streamMic,
                            onCheckedChange = onStreamMicChange
                        )
                    }
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
                        icon = Icons.Outlined.Router,
                        value = appSettings.streamingPort.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let(onStreamingPortChange)
                        }
                    )
                    AnimatedVisibility(visible = appSettings.experimentalFeaturesEnabled) {
                        SettingsTextFieldItem(
                            title = stringResource(R.string.client_mic_port_title),
                            description = stringResource(R.string.client_mic_port_desc),
                            icon = Icons.Outlined.Podcasts,
                            value = appSettings.micPort.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let(onMicPortChange)
                            }
                        )
                    }
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_experimental),
                    icon = Icons.Outlined.Science
                ) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_experimental_title),
                        description = stringResource(R.string.settings_item_experimental_desc),
                        icon = Icons.Outlined.Warning,
                        isChecked = appSettings.experimentalFeaturesEnabled,
                        onCheckedChange = { isEnabled ->
                            if (isEnabled) {
                                showExperimentalWarningDialog = true
                            } else {
                                onExperimentalFeaturesChange(false)
                            }
                        }
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
            label = { Text(stringResource(R.string.port_number_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            keyboardActions = KeyboardActions(onDone = {
                onValueChange(text)
                focusManager.clearFocus()
            })
        )
    }
}

// MODIFICATO: Firma aggiornata per usare ServerInfo
@Composable
fun ExpressiveDeviceDiscoveryPanel(
    devices: Map<String, ServerInfo>,
    onConnect: (ServerInfo) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier
) {
    // ... (Il corpo di questa funzione non cambia, solo la sua firma)
    val haptic = LocalHapticFeedback.current
    val cardElevation by animateFloatAsState(
        targetValue = if (devices.isNotEmpty()) 12f else 6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Discovery Panel Elevation"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val headerIconScale by animateFloatAsState(
                        targetValue = if (devices.isNotEmpty()) 1.1f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "Discovery Header Icon Scale"
                    )

                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .scale(headerIconScale),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.nearby_devices_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                val refreshRotation by animateFloatAsState(
                    targetValue = if (devices.isEmpty()) 360f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "Refresh Button Rotation"
                )

                FilledTonalIconButton(
                    onClick = {
                        onRefresh()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.graphicsLayer { rotationZ = refreshRotation }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh_button_description),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = devices.isEmpty(),
                transitionSpec = {
                    (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                            expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )).togetherWith(
                        fadeOut(animationSpec = tween(200)) +
                                shrinkVertically(animationSpec = tween(200))
                    )
                },
                label = "Device Discovery Content Animation"
            ) { isEmpty ->
                if (isEmpty) {
                    ExpressiveSearchingIndicator()
                } else {
                    ExpressiveDeviceList(devices = devices, onConnect = onConnect)
                }
            }
        }
    }
}


@Composable
fun ExpressiveDeviceList(
    devices: Map<String, ServerInfo>,
    onConnect: (ServerInfo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // La logica del forEach ora passa l'informazione sulla modalità
        devices.forEach { (hostname, serverInfo) ->
            ExpressiveDeviceCard(
                hostname = hostname,
                ipAddress = "${serverInfo.ip}:${serverInfo.port}",
                isMulticast = serverInfo.isMulticast,
                onConnect = { onConnect(serverInfo) }
            )
        }
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
            Divider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
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

    LargeTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isStreaming) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
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

            val isSettingsButton = statusIcon == Icons.Default.Settings

            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        indicatorColor.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .scale(iconScale)
                    .then(
                        if (isSettingsButton) Modifier.clickable(onClick = onSettingsClick) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusText,
                    tint = indicatorColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(
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
    experimentalFeaturesEnabled: Boolean, // <-- NUOVO
    isMulticast: Boolean,
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
            // --- Sezione Sorgenti Audio (Invariata) ---
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

            // Il microfono è visibile solo se le funzioni sperimentali sono attive
            AnimatedVisibility(visible = experimentalFeaturesEnabled) {
                ExpressiveAudioSourceToggle(
                    icon = Icons.Outlined.Mic,
                    checkedIcon = Icons.Filled.Mic,
                    title = stringResource(R.string.microphone_title),
                    subtitle = stringResource(R.string.microphone_subtitle),
                    checked = streamMic,
                    onCheckedChange = onStreamMicChange
                )
            }

            Divider(modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp))

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

            ExpressiveStreamingModeToggle(
                checked = isMulticast,
                onCheckedChange = onMulticastChange
            )
        }
    }
}

@Composable
fun ExpressiveStreamingModeToggle(
    checked: Boolean, // true per Multicast, false per Singolo Client
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Le descrizioni e le icone cambiano in base alla selezione
    val title = if (checked) stringResource(R.string.multicast_mode_title) else stringResource(R.string.unicast_mode_title)
    val subtitle = if (checked) stringResource(R.string.multicast_mode_desc) else stringResource(R.string.unicast_mode_desc)
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
                if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
            .padding(16.dp)
            .scale(rowScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                scaleIn(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ).togetherWith(scaleOut())
            },
            label = "Streaming Mode Icon Animation"
        ) { isChecked ->
            Icon(
                imageVector = if (isChecked) checkedIcon else icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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
            onCheckedChange = {
                onCheckedChange(it)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            thumbContent = {
                AnimatedContent(
                    targetState = checked,
                    label = "Switch Thumb Icon"
                ) { isChecked ->
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
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    modifier: Modifier
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
        modifier = Modifier.fillMaxWidth(),
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
                ExpressiveStreamingActiveIndicator(onStopServer = onStopServer)
            } else if (isServer) {
                ExpressiveServerControls(
                    streamInternal = streamInternal,
                    streamMic = streamMic,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}




@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onOnboardingFinished: () -> Unit) {
    val pagerState = rememberPagerState { 2 } // 2 pagine totali
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