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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cuscus.wifiaudiostreaming.data.AppScript
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.data.AutoConnectEntry
import com.cuscus.wifiaudiostreaming.scripting.ScriptActionType
import com.cuscus.wifiaudiostreaming.scripting.ScriptCommand
import com.cuscus.wifiaudiostreaming.scripting.ScriptParams
import java.util.UUID
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
    onToggleAutoConnectIp: (String) -> Unit,
    onSecurityChange: (String, String) -> Unit = { _, _ -> },
    onEncryptionChange: (Boolean) -> Unit = {},
    hasMicPermission: Boolean = true
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
                    securityMode = appSettings.securityMode,
                    authKey = appSettings.authKey,
                    onSecurityChange = onSecurityChange,
                    encryptionEnabled = appSettings.encryptionEnabled,
                    onEncryptionChange = onEncryptionChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ExpressiveStreamingControlCenter(
                isServer = isServer,
                isStreaming = isStreaming,
                streamInternal = appSettings.streamInternal,
                streamMic = appSettings.streamMic,
                localIp = localIp,
                hasMicPermission = hasMicPermission,
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
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
    val appHaptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { appHaptics.tap(); onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon)
        Spacer(Modifier.width(16.dp))
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
    onAdvancedAudioChange: (Int, Int) -> Unit = { _, _ -> },
    onSecurityChange: (String, String) -> Unit = { _, _ -> },
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
    onHapticsChange: (Boolean) -> Unit = {},
    onBlackoutOutlinedChange: (Boolean) -> Unit = {},
    onShowDonation: () -> Unit = {},
    onDeveloperModeChange: (Boolean) -> Unit = {},
    onNoiseReductionChange: (Boolean, Int) -> Unit = { _, _ -> },
    onOpenScripting: () -> Unit = {},
    onAutoUpdateCheckChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    checkingForUpdate: Boolean = false,
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
            onAdvancedAudioChange = onAdvancedAudioChange,
            onSecurityChange = onSecurityChange,
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
            onDisconnectionSoundChange = onDisconnectionSoundChange,
            onHapticsChange = onHapticsChange,
            onBlackoutOutlinedChange = onBlackoutOutlinedChange,
            onShowDonation = onShowDonation,
            onDeveloperModeChange = onDeveloperModeChange,
            onNoiseReductionChange = onNoiseReductionChange,
            onOpenScripting = onOpenScripting,
            onAutoUpdateCheckChange = onAutoUpdateCheckChange,
            onCheckForUpdates = onCheckForUpdates,
            checkingForUpdate = checkingForUpdate
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
    onAdvancedAudioChange: (Int, Int) -> Unit = { _, _ -> },
    onSecurityChange: (String, String) -> Unit = { _, _ -> },
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
    onHapticsChange: (Boolean) -> Unit = {},
    onBlackoutOutlinedChange: (Boolean) -> Unit = {},
    onShowDonation: () -> Unit = {},
    onDeveloperModeChange: (Boolean) -> Unit = {},
    onNoiseReductionChange: (Boolean, Int) -> Unit = { _, _ -> },
    onOpenScripting: () -> Unit = {},
    onAutoUpdateCheckChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    checkingForUpdate: Boolean = false,
) {
    val context = LocalContext.current
    val settingsHaptics = rememberAppHaptics()

    var showLicensesDialog by remember { mutableStateOf(false) }
    val licensesText = remember {
        runCatching {
            context.resources.openRawResource(R.raw.third_party_licenses)
                .bufferedReader().use { it.readText() }
        }.getOrDefault("See THIRD_PARTY_LICENSES.md in the project repository.")
    }
    if (showLicensesDialog) {
        ExpressiveLongTextDialog(
            icon = Icons.Outlined.Gavel,
            accent = MaterialTheme.colorScheme.tertiary,
            title = stringResource(R.string.licenses_dialog_title),
            subtitle = stringResource(R.string.licenses_open_source),
            body = licensesText,
            dismissLabel = stringResource(R.string.licenses_close),
            onDismiss = { showLicensesDialog = false }
        )
    }

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
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            fredClickCount++
                            if (fredClickCount >= 7) settingsHaptics.confirm() else settingsHaptics.tick()

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
                    IconButton(onClick = { settingsHaptics.tap(); onClose() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_button_description))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
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
                    title = stringResource(R.string.settings_group_audio),
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
                    SettingsSliderItem(
                        title = stringResource(R.string.settings_item_latency_title),
                        description = stringResource(R.string.settings_item_latency_desc),
                        icon = Icons.Outlined.Timer,
                        value = appSettings.latencyMs.toFloat(),
                        range = 40f..400f,
                        steps = ((400f - 40f) / 20f).toInt() - 1,
                        valueSuffix = "ms",
                        onValueChange = { onAdvancedAudioChange(it.toInt(), appSettings.maxPayloadBytes) }
                    )
                    SettingsSliderItem(
                        title = stringResource(R.string.settings_item_packet_size_title),
                        description = stringResource(R.string.settings_item_packet_size_desc),
                        icon = Icons.Outlined.SettingsEthernet,
                        value = appSettings.maxPayloadBytes.toFloat(),
                        range = 256f..1390f,
                        steps = ((1390f - 256f) / 32f).toInt() - 1,
                        valueSuffix = "B",
                        onValueChange = { onAdvancedAudioChange(appSettings.latencyMs, it.toInt()) }
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
                    SettingsInfoItem(
                        title = stringResource(R.string.vpn_note_title),
                        description = stringResource(R.string.vpn_note_desc),
                        icon = Icons.Outlined.Info
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
                    title = stringResource(R.string.settings_group_personalization),
                    icon = Icons.Outlined.Palette
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
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_haptics_title),
                        description = stringResource(R.string.settings_item_haptics_desc),
                        icon = Icons.Outlined.Vibration,
                        isChecked = appSettings.hapticsEnabled,
                        onCheckedChange = onHapticsChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_blackout_outlined_title),
                        description = stringResource(R.string.settings_item_blackout_outlined_desc),
                        icon = Icons.Outlined.DarkMode,
                        isChecked = appSettings.blackoutOutlinedUi,
                        onCheckedChange = onBlackoutOutlinedChange
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_developer),
                    icon = Icons.Outlined.DeveloperMode
                ) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_item_developer_title),
                        description = stringResource(R.string.settings_item_developer_desc),
                        icon = Icons.Outlined.Code,
                        isChecked = appSettings.developerMode,
                        onCheckedChange = onDeveloperModeChange
                    )

                    AnimatedVisibility(visible = appSettings.developerMode) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsSwitchItem(
                                title = stringResource(R.string.nr_title),
                                description = stringResource(R.string.nr_desc),
                                icon = Icons.Outlined.GraphicEq,
                                isChecked = appSettings.noiseReductionEnabled,
                                onCheckedChange = { on ->
                                    onNoiseReductionChange(on, appSettings.noiseReductionStrength)
                                }
                            )
                            AnimatedVisibility(visible = appSettings.noiseReductionEnabled) {
                                Column {
                                    SettingsSliderItem(
                                        title = stringResource(R.string.nr_title),
                                        description = stringResource(
                                            R.string.nr_strength, appSettings.noiseReductionStrength
                                        ),
                                        icon = Icons.Outlined.Tune,
                                        value = appSettings.noiseReductionStrength.toFloat(),
                                        range = 0f..100f,
                                        steps = 19,
                                        valueSuffix = "%",
                                        onValueChange = { v ->
                                            onNoiseReductionChange(true, v.toInt())
                                        }
                                    )
                                    Text(
                                        text = stringResource(R.string.nr_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_automation),
                    icon = Icons.Outlined.Bolt
                ) {
                    SettingsClickableItem(
                        title = stringResource(R.string.settings_item_scripting_title),
                        description = stringResource(R.string.settings_item_scripting_desc),
                        icon = Icons.Outlined.Link,
                        onClick = onOpenScripting
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = Bilingual("Updates", "Aggiornamenti").text(),
                    icon = Icons.Outlined.Update
                ) {
                    SettingsSwitchItem(
                        title = Bilingual("Check for updates automatically", "Controlla aggiornamenti automaticamente").text(),
                        description = Bilingual(
                            "On launch, check GitHub for a newer release.",
                            "All'avvio, controlla su GitHub se c'è una nuova versione."
                        ).text(),
                        icon = Icons.Outlined.Update,
                        isChecked = appSettings.autoUpdateCheckEnabled,
                        onCheckedChange = onAutoUpdateCheckChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsClickableItem(
                        title = Bilingual("Check for updates now", "Controlla aggiornamenti ora").text(),
                        description = if (checkingForUpdate)
                            Bilingual("Checking…", "Controllo in corso…").text()
                        else
                            Bilingual("Tap to check GitHub now.", "Tocca per controllare ora su GitHub.").text(),
                        icon = Icons.Outlined.Refresh,
                        onClick = onCheckForUpdates
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_info),
                    icon = Icons.Outlined.Info
                ) {
                    SettingsInfoItem(
                        title = stringResource(R.string.license_info_title),
                        description = stringResource(R.string.license_info_desc),
                        icon = Icons.Outlined.VerifiedUser
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        title = stringResource(R.string.license_read_full_title),
                        description = stringResource(R.string.license_read_full_desc),
                        icon = Icons.Outlined.OpenInBrowser,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://eupl.eu/"))
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        title = stringResource(R.string.licenses_open_source),
                        description = stringResource(R.string.licenses_open_source_desc),
                        icon = Icons.Outlined.Description,
                        onClick = { showLicensesDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        title = stringResource(R.string.settings_item_show_donation_title),
                        description = stringResource(R.string.settings_item_show_donation_desc),
                        icon = Icons.Outlined.Redeem,
                        onClick = onShowDonation
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        title = stringResource(R.string.source_code_android),
                        description = stringResource(R.string.source_code_view_on_github),
                        icon = Icons.Outlined.Code,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/marcomorosi06/WiFiAudioStreaming-Android/"))
                            context.startActivity(intent)
                        }
                    )
                    SettingsClickableItem(
                        title = stringResource(R.string.source_code_desktop),
                        description = stringResource(R.string.source_code_view_on_github),
                        icon = Icons.Outlined.Code,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop"))
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        title = stringResource(R.string.source_code_protocol),
                        description = stringResource(R.string.source_code_protocol_desc),
                        icon = Icons.Outlined.Code,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/marcomorosi06/wfas-protocol"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item {
                val versionName = remember {
                    try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }
                    catch (e: Exception) { "?" }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${stringResource(R.string.app_version_label)} $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val fieldHaptics = rememberAppHaptics()

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsRowIcon(icon)
            Spacer(Modifier.width(16.dp))
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
                fieldHaptics.confirm()
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
    val haptic = rememberAppHaptics()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
                FilledTonalIconButton(onClick = { onRefresh(); haptic.confirm() }, modifier = Modifier.graphicsLayer { rotationZ = refreshRotation }) {
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
                securityMode = serverInfo.securityMode,
                encrypted = serverInfo.encrypted,
                serverSendsMic = serverInfo.serverSendsMic,
                serverWantsMic = serverInfo.serverWantsMic,
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
    securityMode: String?,
    encrypted: Boolean,
    serverSendsMic: Boolean,
    serverWantsMic: Boolean,
    isAutoConnectTarget: Boolean,
    onConnect: () -> Unit,
    onToggleAutoConnect: () -> Unit
) {
    val haptic = rememberAppHaptics()

    ElevatedCard(
        onClick = {
            onConnect()
            haptic.confirm()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModeTag(isMulticast = isMulticast)
                    DeviceBadges(securityMode, encrypted, serverSendsMic, serverWantsMic)
                }
                Text(text = ipAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onToggleAutoConnect(); haptic.tick() }) {
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
    val appHaptics = rememberAppHaptics()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                appHaptics.toggle(!isChecked)
                onCheckedChange(!isChecked)
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon)
        Spacer(Modifier.width(16.dp))
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
                appHaptics.toggle(it)
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
    val appHaptics = rememberAppHaptics()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { appHaptics.tap(); expanded = !expanded },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsRowIcon(icon)
            Spacer(Modifier.width(16.dp))
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
                        appHaptics.confirm()
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
    valueSuffix: String = "B",
    onValueChange: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val appHaptics = rememberAppHaptics()

    // Ricordiamo l’ultimo intero attraversato per evitare vibrazioni continue
    var lastStep by remember { mutableIntStateOf(value.toInt()) }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsRowIcon(icon)
            Spacer(Modifier.width(16.dp))
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
                text = "${sliderValue.toInt()} $valueSuffix",
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
                    appHaptics.tick()
                    lastStep = currentStep
                }
            },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { appHaptics.gestureEnd(); onValueChange(sliderValue) },
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
    val haptic = rememberAppHaptics()

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
            haptic.toggle(true)
            onClick()
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
    securityMode: String = "OFF",
    authKey: String = "",
    onSecurityChange: (String, String) -> Unit = { _, _ -> },
    encryptionEnabled: Boolean = false,
    onEncryptionChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_group_security),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            val secMode = securityMode.uppercase()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = secMode == "OFF",
                    onClick = { onSecurityChange("OFF", authKey) },
                    label = { Text(stringResource(R.string.sec_mode_off), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = secMode == "ASK",
                    onClick = { onSecurityChange("ASK", authKey) },
                    label = { Text(stringResource(R.string.sec_mode_ask), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = secMode == "KEY",
                    onClick = { onSecurityChange("KEY", authKey) },
                    label = { Text(stringResource(R.string.sec_mode_key), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (secMode == "KEY") {
                Spacer(modifier = Modifier.height(12.dp))
                // Local edit state: the field must NOT be driven by the DataStore-backed
                // value, or the async save→flow→recompose round-trip resets the caret /
                // drops characters. We persist on each change but display the local text.
                var keyText by remember { mutableStateOf(authKey) }
                var keyVisible by remember { mutableStateOf(false) }
                val haptics = rememberAppHaptics()
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it; onSecurityChange(securityMode, it) },
                    label = { Text(stringResource(R.string.settings_item_auth_key_title)) },
                    leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            keyVisible = !keyVisible
                            haptics.toggle(keyVisible)
                        }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EnhancedEncryption,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_item_encryption_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_item_encryption_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = encryptionEnabled && secMode == "KEY",
                        onCheckedChange = onEncryptionChange,
                        enabled = secMode == "KEY"
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveStreamingModeToggle(
    checked: Boolean, // true per Multicast, false per Singolo Client
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = rememberAppHaptics()

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
                haptic.confirm()
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
    val haptic = rememberAppHaptics()

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
                haptic.toggle(it)
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
    hasMicPermission: Boolean,
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

                    if (isServer && localIp.isNotEmpty() && localIp != "0.0.0.0") {
                        val clipboardManager = LocalClipboardManager.current
                        val haptic = rememberAppHaptics()
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(localIp))
                                haptic.confirm()
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.server_ip_active, localIp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

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

                    if (!isServer) {
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { BlackoutController.show() },
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(Icons.Filled.DarkMode, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.blackout_button))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.playback_keep_open_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }

                    if (isServer) {
                        Spacer(modifier = Modifier.height(24.dp))
                        val volume by NetworkManager.serverVolume.collectAsState()

                        ExpressiveVolumeSlider(
                            volume = volume,
                            onVolumeChange = { NetworkManager.serverVolume.value = it },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    }
                    // --------------------------------------
                }
            } else if (isServer) {
                ExpressiveServerControls(
                    streamInternal = streamInternal,
                    streamMic = streamMic,
                    localIp = localIp,
                    hasMicPermission = hasMicPermission,
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
    val haptic = rememberAppHaptics()

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
                haptic.confirm()
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
    hasMicPermission: Boolean,
    onStartServer: () -> Unit
) {
    val haptic = rememberAppHaptics()
    val isReady = streamInternal || streamMic
    var showMicPermissionExplanation by remember { mutableStateOf(false) }

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

        AnimatedVisibility(
            visible = isReady && !hasMicPermission,
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.mic_permission_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showMicPermissionExplanation = true }) {
                        Text(
                            text = stringResource(R.string.mic_permission_why_button),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showMicPermissionExplanation) {
            AlertDialog(
                onDismissRequest = { showMicPermissionExplanation = false },
                title = { Text(stringResource(R.string.mic_permission_explanation_title)) },
                text = { Text(stringResource(R.string.mic_permission_explanation_body)) },
                confirmButton = {
                    TextButton(onClick = { showMicPermissionExplanation = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        Button(
            onClick = {
                onStartServer()
                haptic.confirm()
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

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun DeviceBadges(securityMode: String?, encrypted: Boolean, serverSendsMic: Boolean, serverWantsMic: Boolean) {
    val mode = securityMode?.uppercase()
    val badges = buildList {
        when {
            encrypted -> add(
                DeviceBadgeSpec(Icons.Filled.Lock, stringResource(R.string.sec_encrypted), true, MaterialShapes.Gem)
            )
            mode == "KEY" -> add(
                DeviceBadgeSpec(Icons.Outlined.Key, stringResource(R.string.sec_key), true, MaterialShapes.Pentagon)
            )
            mode == "ASK" -> add(
                DeviceBadgeSpec(Icons.Outlined.Security, stringResource(R.string.sec_ask), true, MaterialShapes.Sunny)
            )
        }
        if (serverSendsMic) add(
            DeviceBadgeSpec(Icons.Filled.Mic, stringResource(R.string.mic_sends), false, MaterialShapes.Cookie7Sided)
        )
        if (serverWantsMic) add(
            DeviceBadgeSpec(Icons.Filled.Hearing, stringResource(R.string.mic_wants), false, MaterialShapes.Clover4Leaf)
        )
    }
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        badges.forEach { DeviceBadge(it) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private data class DeviceBadgeSpec(
    val icon: ImageVector,
    val desc: String,
    val accent: Boolean,
    val shape: RoundedPolygon
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceBadge(spec: DeviceBadgeSpec) {
    val bg = if (spec.accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (spec.accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val morph = remember(spec.shape) { Morph(spec.shape, spec.shape) }
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(MorphOutlineShape(morph, 1f))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(spec.icon, contentDescription = spec.desc, tint = fg, modifier = Modifier.size(14.dp))
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
    val haptic = rememberAppHaptics()

    ElevatedCard(
        onClick = {
            onConnect()
            haptic.confirm()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
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

private data class FeatureItem(
    val imageVector: ImageVector? = null,
    val drawableRes: Int? = null,
    val titleRes: Int,
    val descRes: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingFinished: () -> Unit,
    autoUpdateEnabled: Boolean = true,
    onAutoUpdateChange: (Boolean) -> Unit = {}
) {
    val pagerState = rememberPagerState { 4 }
    val coroutineScope = rememberCoroutineScope()

    val cs = MaterialTheme.colorScheme
    val pageAccent = when (pagerState.currentPage) {
        0 -> cs.primary
        1 -> cs.tertiary
        2 -> cs.secondary
        else -> cs.primary
    }
    val accent by animateColorAsState(
        targetValue = pageAccent,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "onboardingAccent"
    )
    val canvas by animateColorAsState(
        targetValue = pageAccent.copy(alpha = 0.06f).compositeOver(cs.background),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "onboardingCanvas"
    )

    Scaffold(
        bottomBar = {
            OnboardingNavigation(
                pagerState = pagerState,
                accent = accent,
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
        containerColor = canvas
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalAlignment = Alignment.Top
        ) { page ->
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            when (page) {
                0 -> WelcomePage(
                    accent = accent,
                    swipeOffset = offset,
                    autoUpdateEnabled = autoUpdateEnabled,
                    onAutoUpdateChange = onAutoUpdateChange
                )
                1 -> FeaturesPage(accent = accent, swipeOffset = offset)
                2 -> ProtocolsPage(accent = accent, swipeOffset = offset)
                3 -> WhatsNewPage()
            }
        }
    }
}

@Composable
fun WelcomePage(
    accent: Color = MaterialTheme.colorScheme.primary,
    swipeOffset: Float = 0f,
    autoUpdateEnabled: Boolean = true,
    onAutoUpdateChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = rememberAppHaptics()
    val link = "https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop"

    var visible by remember { mutableStateOf(false) }
    var showPrivacyDetail by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveOnboardingOrb(
                size = 96.dp,
                accent = accent,
                shapeSeed = 0,
                swipeOffset = swipeOffset
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(76.dp),
                    tint = accent
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(420)) + slideInVertically(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) { it / 4 }
        ) {
            Column(
                modifier = Modifier.graphicsLayer {
                    translationX = swipeOffset * 260f
                    alpha = 1f - kotlin.math.abs(swipeOffset).coerceAtMost(1f)
                }
            ) {
                Text(
                    text = stringResource(R.string.welcome_eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = accent
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.welcome_headline),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                    lineHeight = 42.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(420, delayMillis = 140)) + slideInVertically(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) { it / 4 }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // passo 1: app desktop
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.tap()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            }
                        },
                    shape = RoundedCornerShape(28.dp),
                    color = accent.copy(alpha = 0.14f),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.welcome_step_desktop_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.welcome_step_desktop_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // controllo aggiornamenti + trasparenza
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (autoUpdateEnabled) accent.copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (autoUpdateEnabled) Icons.Outlined.CloudSync
                                    else Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = if (autoUpdateEnabled) accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.welcome_privacy_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        if (autoUpdateEnabled) R.string.welcome_privacy_on
                                        else R.string.welcome_privacy_off
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (autoUpdateEnabled) accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = {
                                    haptic.toggle(it)
                                    onAutoUpdateChange(it)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.welcome_privacy_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        AnimatedVisibility(visible = showPrivacyDetail) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.welcome_privacy_detail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = {
                                haptic.tap()
                                showPrivacyDetail = !showPrivacyDetail
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    if (showPrivacyDetail) R.string.less_details
                                    else R.string.more_details
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AudioOverWifiMotif() {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    val transition = rememberInfiniteTransition(label = "flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(34.dp)
        )
        Canvas(
            modifier = Modifier
                .width(104.dp)
                .height(20.dp)
        ) {
            val count = 4
            val w = size.width
            val cy = size.height / 2f
            val radius = 3.5.dp.toPx()
            drawLine(
                color = muted,
                start = Offset(0f, cy),
                end = Offset(w, cy),
                strokeWidth = 2f
            )
            for (i in 0 until count) {
                val t = (phase + i.toFloat() / count) % 1f
                val alpha = (1f - kotlin.math.abs(t - 0.5f) * 2f).coerceIn(0f, 1f)
                drawCircle(
                    color = primary.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(t * w, cy)
                )
            }
        }
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
fun FeaturesPage(accent: Color = MaterialTheme.colorScheme.primary, swipeOffset: Float = 0f) {
    OnboardingFeaturePage(
        icon = Icons.Default.SwapHoriz,
        titleRes = R.string.onboarding_features_title,
        subtitleRes = R.string.onboarding_features_subtitle,
        features = listOf(
            FeatureItem(imageVector = Icons.Default.Download, titleRes = R.string.onboarding_feature1_title, descRes = R.string.onboarding_feature1_desc),
            FeatureItem(imageVector = Icons.Default.Upload, titleRes = R.string.onboarding_feature2_title, descRes = R.string.onboarding_feature2_desc),
            FeatureItem(imageVector = Icons.Default.Tune, titleRes = R.string.onboarding_feature3_title, descRes = R.string.onboarding_feature3_desc)
        ),
        accent = accent,
        swipeOffset = swipeOffset,
        shapeSeed = 1
    )
}

@Composable
fun ProtocolsPage(accent: Color = MaterialTheme.colorScheme.primary, swipeOffset: Float = 0f) {
    OnboardingFeaturePage(
        icon = Icons.Default.Hub,
        titleRes = R.string.onboarding_protocols_title,
        subtitleRes = R.string.onboarding_protocols_subtitle,
        features = listOf(
            FeatureItem(drawableRes = R.drawable.wfas_protocol, titleRes = R.string.onboarding_proto1_title, descRes = R.string.onboarding_proto1_desc),
            FeatureItem(imageVector = Icons.Default.Radio, titleRes = R.string.onboarding_proto2_title, descRes = R.string.onboarding_proto2_desc),
            FeatureItem(imageVector = Icons.Default.Language, titleRes = R.string.onboarding_proto3_title, descRes = R.string.onboarding_proto3_desc)
        ),
        accent = accent,
        swipeOffset = swipeOffset,
        shapeSeed = 2
    )
}

@Composable
private fun OnboardingFeaturePage(
    icon: ImageVector,
    titleRes: Int,
    subtitleRes: Int,
    features: List<FeatureItem>,
    accent: Color = MaterialTheme.colorScheme.primary,
    swipeOffset: Float = 0f,
    shapeSeed: Int = 1
) {
    val haptic = rememberAppHaptics()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top)
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            val orbEnter by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(500),
                label = "onboardingOrbEnter"
            )
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = orbEnter
                    val enterScale = 0.75f + 0.25f * orbEnter
                    scaleX = enterScale
                    scaleY = enterScale
                }
            ) {
                ExpressiveOnboardingOrb(
                    size = 128.dp,
                    accent = accent,
                    shapeSeed = shapeSeed,
                    swipeOffset = swipeOffset
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = accent
                    )
                }
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, delayMillis = 80)) + slideInVertically(tween(600, delayMillis = 80)) { it / 3 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer {
                        translationX = swipeOffset * 260f
                        alpha = 1f - kotlin.math.abs(swipeOffset).coerceAtMost(1f)
                    }
                ) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 34.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            features.forEachIndexed { index, item ->
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(500, delayMillis = 180 + index * 90)) +
                            slideInVertically(tween(500, delayMillis = 180 + index * 90)) { it / 2 }
                ) {
                    OnboardingFeatureCard(
                        item = item,
                        title = stringResource(item.titleRes),
                        description = stringResource(item.descRes),
                        accent = accent,
                        onClick = { haptic.tick() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    item: FeatureItem,
    title: String,
    description: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageVector != null) {
                    Icon(
                        imageVector = item.imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = accent
                    )
                } else if (item.drawableRes != null) {
                    Icon(
                        painter = painterResource(item.drawableRes),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = accent
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingNavigation(
    pagerState: PagerState,
    accent: Color,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    val haptic = rememberAppHaptics()

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
                haptic.tick()
                onSkipClick()
            },
            content = {
                if (pagerState.currentPage < pagerState.pageCount - 1) {
                    Text(stringResource(id = R.string.skip))
                }
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val isActive = pagerState.currentPage == iteration
                val width by animateDpAsState(
                    targetValue = if (isActive) 32.dp else 10.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "pillWidth"
                )
                val heightDp by animateDpAsState(
                    targetValue = if (isActive) 12.dp else 10.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "pillHeight"
                )
                val color by animateColorAsState(
                    targetValue = if (isActive) accent
                    else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(300),
                    label = "pillColor"
                )
                Box(
                    modifier = Modifier
                        .height(heightDp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        val isLast = pagerState.currentPage == pagerState.pageCount - 1
        val btnCorner by animateDpAsState(
            targetValue = if (isLast) 18.dp else 30.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "onboardingBtnCorner"
        )
        Button(
            onClick = {
                if (isLast) haptic.confirm() else haptic.tap()
                onNextClick()
            },
            shape = RoundedCornerShape(btnCorner),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            modifier = Modifier.height(54.dp)
        ) {
            AnimatedContent(
                targetState = isLast,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.7f)).togetherWith(
                        fadeOut(tween(140)) + scaleOut(targetScale = 0.7f)
                    )
                },
                label = "Button Text Animation"
            ) { isLastPage ->
                Text(
                    text = stringResource(id = if (isLastPage) R.string.start else R.string.next),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveRtpSdpBanner(
    port: Int,
    sampleRate: Int,
    channels: Int
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = rememberAppHaptics()
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

    val iconShape = remember { MaterialShapes.Cookie6Sided }
    val morph = remember { Morph(MaterialShapes.Circle, iconShape) }
    val shapeProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        shapeProgress.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(MorphOutlineShape(morph, shapeProgress.value))
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_item_rtp_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "L16 / ${sampleRate / 1000}kHz",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.rtp_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        haptics.confirm()
                        clipboardManager.setText(AnnotatedString(sdpContent))
                        copied = true
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (copied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn()).togetherWith(fadeOut(tween(140)) + scaleOut())
                        },
                        label = "CopySdpState"
                    ) { isCopied ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isCopied) stringResource(R.string.copied) else stringResource(R.string.copy_sdp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptics.tap()
                        launcher.launch("stream.sdp")
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        Icons.Outlined.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.save_sdp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
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
    val haptic = rememberAppHaptics()

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        // Intestazione (Sopra)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsRowIcon(icon)
            Spacer(Modifier.width(16.dp))
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
                    haptic.tick()
                    onCodecChange(false)
                },
                modifier = Modifier.weight(1f)
            )

            CodecAnimatedButton(
                text = stringResource(R.string.codec_aac),
                selected = isSafariMode,
                onClick = {
                    haptic.tick()
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
    val acHaptics = rememberAppHaptics()

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
                                acHaptics.confirm()
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
                                        acHaptics.tap()
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
                                acHaptics.tick()
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
                                acHaptics.tick()
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
                                acHaptics.reject()
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
                acHaptics.confirm()
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
fun SettingsRowIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint
        )
    }
}

@Composable
fun SettingsGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            label = title,
            accent = MaterialTheme.colorScheme.primary,
            trailing = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveHttpBanner(ip: String, port: Int) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = rememberAppHaptics()
    var copied by remember { mutableStateOf(false) }
    val url = "http://$ip:$port"

    val iconShape = remember { MaterialShapes.Cookie7Sided }
    val morph = remember { Morph(MaterialShapes.Circle, iconShape) }
    val shapeProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        shapeProgress.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(MorphOutlineShape(morph, shapeProgress.value))
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_item_http_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        haptics.confirm()
                        clipboardManager.setText(AnnotatedString(url))
                        copied = true
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (copied) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn()).togetherWith(fadeOut(tween(140)) + scaleOut())
                        },
                        label = "CopyUrlState"
                    ) { isCopied ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isCopied) stringResource(R.string.copied) else stringResource(R.string.copy_url),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptics.tap()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        Icons.Outlined.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.open_browser),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    showPresets: Boolean = true
) {
    val haptics = rememberAppHaptics()
    var isDragging by remember { mutableStateOf(false) }
    var lastStep by remember { mutableStateOf((volume * 20).toInt()) }

    val percentage = (volume * 100).toInt()
    val volumeIcon = when {
        volume <= 0.01f -> Icons.Outlined.VolumeOff
        volume < 0.5f -> Icons.Outlined.VolumeMute
        volume <= 1.0f -> Icons.Outlined.VolumeDown
        else -> Icons.Outlined.VolumeUp
    }

    val mutedLabel = stringResource(R.string.volume_muted)
    val volumeStateLabel = when {
        volume <= 0.01f -> mutedLabel
        volume < 1.0f -> "$percentage%"
        volume == 1.0f -> "100% (Std)"
        else -> "$percentage% (Boost)"
    }

    val badgeColor by animateColorAsState(
        targetValue = when {
            volume <= 0.01f -> MaterialTheme.colorScheme.error
            volume > 1.0f -> MaterialTheme.colorScheme.tertiary
            else -> accent
        },
        animationSpec = spring(),
        label = "VolumeBadgeColor"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = volumeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.transmission_volume, percentage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.server_audio_restart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = volumeStateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Slider(
                value = volume,
                onValueChange = { valValue ->
                    if (!isDragging) {
                        isDragging = true
                        haptics.gestureStart()
                    }
                    val step = (valValue * 20).toInt()
                    if (step != lastStep) {
                        lastStep = step
                        if (step == 0 || step == 20 || step == 40) {
                            haptics.confirm()
                        } else {
                            haptics.tick()
                        }
                    }
                    onVolumeChange(valValue)
                },
                onValueChangeFinished = {
                    isDragging = false
                    haptics.gestureEnd()
                },
                valueRange = 0f..2f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = badgeColor,
                    activeTrackColor = badgeColor,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            if (showPresets) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        0f to stringResource(R.string.volume_preset_mute),
                        1f to stringResource(R.string.volume_preset_std),
                        2f to stringResource(R.string.volume_preset_max)
                    )
                    presets.forEach { (presetValue, label) ->
                        val isSelected = kotlin.math.abs(volume - presetValue) < 0.05f
                        val presetColor by animateColorAsState(
                            targetValue = if (isSelected) badgeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "PresetColor"
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) badgeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    haptics.confirm()
                                    onVolumeChange(presetValue)
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = presetColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveIpCopyButton(
    localIp: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val haptics = rememberAppHaptics()
    var copied by remember { mutableStateOf(false) }

    val tapScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val iconShape = remember { MaterialShapes.Clover4Leaf }
    val morph = remember { Morph(MaterialShapes.Circle, iconShape) }
    val shapeProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        shapeProgress.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val containerColor by animateColorAsState(
        targetValue = if (copied) accent else accent.copy(alpha = 0.14f),
        animationSpec = spring(),
        label = "IpCopyContainer"
    )

    val contentColor by animateColorAsState(
        targetValue = if (copied) MaterialTheme.colorScheme.surfaceContainerLowest else accent,
        animationSpec = spring(),
        label = "IpCopyContent"
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = containerColor,
        border = BorderStroke(1.5.dp, accent.copy(alpha = if (copied) 0.8f else 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = tapScale.value
                scaleY = tapScale.value
            }
            .clickable {
                haptics.confirm()
                clipboard.setText(AnnotatedString(localIp))
                copied = true
                scope.launch {
                    tapScale.snapTo(0.93f)
                    tapScale.animateTo(
                        1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioHighBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MorphOutlineShape(morph, shapeProgress.value))
                    .background(contentColor.copy(alpha = if (copied) 0.25f else 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.server_ip_format, localIp),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
                Text(
                    text = if (copied) stringResource(R.string.copied_to_clipboard) else stringResource(R.string.tap_to_copy_ip),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = contentColor.copy(alpha = if (copied) 0.25f else 0.12f)
            ) {
                AnimatedContent(
                    targetState = copied,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn()).togetherWith(fadeOut(tween(120)) + scaleOut())
                    },
                    label = "CopyBadgeState"
                ) { isCopied ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentColor
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isCopied) stringResource(R.string.copied_uppercase) else stringResource(R.string.copy_uppercase),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = contentColor
                        )
                    }
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

private enum class ScriptFieldType { BOOL, INT, TEXT, SAMPLERATE, CHANNELS, AUTHMODE }

private data class ScriptField(val key: String, val type: ScriptFieldType)

private fun scriptFieldsFor(action: ScriptActionType): List<ScriptField> = when (action) {
    ScriptActionType.START_SERVER, ScriptActionType.TOGGLE -> listOf(
        ScriptField(ScriptParams.INTERNAL, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.MIC, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.SAMPLERATE, ScriptFieldType.SAMPLERATE),
        ScriptField(ScriptParams.CHANNELS, ScriptFieldType.CHANNELS),
        ScriptField(ScriptParams.BUFFER, ScriptFieldType.INT),
        ScriptField(ScriptParams.PORT, ScriptFieldType.INT),
        ScriptField(ScriptParams.MULTICAST, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.RTP, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.RTPPORT, ScriptFieldType.INT),
        ScriptField(ScriptParams.HTTP, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.HTTPPORT, ScriptFieldType.INT),
        ScriptField(ScriptParams.IFACE, ScriptFieldType.TEXT),
        ScriptField(ScriptParams.AUTHMODE, ScriptFieldType.AUTHMODE),
        ScriptField(ScriptParams.AUTHKEY, ScriptFieldType.TEXT)
    )
    ScriptActionType.CONNECT -> listOf(
        ScriptField(ScriptParams.IP, ScriptFieldType.TEXT),
        ScriptField(ScriptParams.PORT, ScriptFieldType.INT),
        ScriptField(ScriptParams.CLIENTMIC, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.AUTHMODE, ScriptFieldType.AUTHMODE),
        ScriptField(ScriptParams.AUTHKEY, ScriptFieldType.TEXT)
    )
    ScriptActionType.STOP -> emptyList()
    ScriptActionType.SET -> listOf(
        ScriptField(ScriptParams.INTERNAL, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.MIC, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.SAMPLERATE, ScriptFieldType.SAMPLERATE),
        ScriptField(ScriptParams.CHANNELS, ScriptFieldType.CHANNELS),
        ScriptField(ScriptParams.BUFFER, ScriptFieldType.INT),
        ScriptField(ScriptParams.PORT, ScriptFieldType.INT),
        ScriptField(ScriptParams.MICPORT, ScriptFieldType.INT),
        ScriptField(ScriptParams.MULTICAST, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.RTP, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.RTPPORT, ScriptFieldType.INT),
        ScriptField(ScriptParams.HTTP, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.HTTPPORT, ScriptFieldType.INT),
        ScriptField(ScriptParams.HTTPSAFARI, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.IFACE, ScriptFieldType.TEXT),
        ScriptField(ScriptParams.CLIENTIP, ScriptFieldType.TEXT),
        ScriptField(ScriptParams.AUTOCONNECT, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.CONNSOUND, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.DISCSOUND, ScriptFieldType.BOOL),
        ScriptField(ScriptParams.AUTHMODE, ScriptFieldType.AUTHMODE),
        ScriptField(ScriptParams.AUTHKEY, ScriptFieldType.TEXT)
    )
}

@Composable
private fun scriptActionLabel(action: ScriptActionType): String = when (action) {
    ScriptActionType.START_SERVER -> stringResource(R.string.script_action_server)
    ScriptActionType.CONNECT -> stringResource(R.string.script_action_connect)
    ScriptActionType.STOP -> stringResource(R.string.script_action_stop)
    ScriptActionType.TOGGLE -> stringResource(R.string.script_action_toggle)
    ScriptActionType.SET -> stringResource(R.string.script_action_set)
}

@Composable
private fun scriptActionIcon(action: ScriptActionType): ImageVector = when (action) {
    ScriptActionType.START_SERVER -> Icons.Outlined.WifiTethering
    ScriptActionType.CONNECT      -> Icons.Outlined.Podcasts
    ScriptActionType.STOP         -> Icons.Outlined.StopCircle
    ScriptActionType.TOGGLE       -> Icons.Outlined.SwapHoriz
    ScriptActionType.SET          -> Icons.Outlined.Tune
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScriptingScreen(
    isVisible: Boolean,
    scripts: List<AppScript>,
    onClose: () -> Unit,
    onSaveScript: (AppScript) -> Unit,
    onDeleteScript: (String) -> Unit,
    onRunCommand: (ScriptCommand) -> Unit
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
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val haptics = rememberAppHaptics()
        val accent = MaterialTheme.colorScheme.primary

        var selectedAction by remember { mutableStateOf(ScriptActionType.START_SERVER) }
        var name by remember { mutableStateOf("") }
        var editingId by remember { mutableStateOf<String?>(null) }
        val params = remember { mutableStateMapOf<String, String>() }

        val command = ScriptCommand(selectedAction, params.toMap().filterValues { it.isNotBlank() })
        val generatedUri = command.toUri()
        val broadcastAction = command.toBroadcastAction()
        val selectedActionLabel = scriptActionLabel(selectedAction)

        fun resetEditor() {
            editingId = null
            name = ""
            params.clear()
            selectedAction = ScriptActionType.START_SERVER
        }

        fun loadScript(script: AppScript) {
            editingId = script.id
            name = script.name
            selectedAction = ScriptActionType.fromId(script.actionId) ?: ScriptActionType.START_SERVER
            params.clear()
            params.putAll(script.params)
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.scripting_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { haptics.tap(); onClose() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_button_description))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {

                // ── introduzione ─────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveHeroBadge(
                            size = 48.dp,
                            accent = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerAlpha = 0.18f
                        ) {
                            Icon(
                                Icons.Outlined.Nfc,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.scripting_help),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // ── azione ───────────────────────────────────────────────────
                item {
                    Column {
                        SectionHeader(stringResource(R.string.scripting_action_label), accent)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ScriptActionType.entries.forEach { action ->
                                ScriptActionChip(
                                    icon = scriptActionIcon(action),
                                    label = scriptActionLabel(action),
                                    selected = selectedAction == action,
                                    accent = accent
                                ) {
                                    haptics.toggle(true)
                                    selectedAction = action
                                    val valid = scriptFieldsFor(action).map { it.key }.toSet()
                                    params.keys.filter { it !in valid }.forEach { params.remove(it) }
                                }
                            }
                        }
                    }
                }

                // ── editor ───────────────────────────────────────────────────
                item {
                    Column {
                        SectionHeader(stringResource(R.string.scripting_editor_title), accent)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(stringResource(R.string.scripting_name_label)) },
                                placeholder = { Text(selectedActionLabel) },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            scriptFieldsFor(selectedAction).forEach { field ->
                                ScriptParamField(
                                    field = field,
                                    value = params[field.key],
                                    accent = accent,
                                    onValueChange = { newValue ->
                                        if (newValue.isNullOrBlank()) params.remove(field.key)
                                        else params[field.key] = newValue
                                    }
                                )
                            }
                        }
                    }
                }

                // ── anteprima ────────────────────────────────────────────────
                item {
                    Column {
                        SectionHeader(stringResource(R.string.scripting_preview_uri), accent)

                        CodeBlock(
                            text = generatedUri,
                            accent = accent,
                            actionIcon = Icons.Outlined.ContentCopy,
                            actionDesc = stringResource(R.string.scripting_copy_uri)
                        ) {
                            haptics.confirm()
                            clipboard.setText(AnnotatedString(generatedUri))
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.scripting_preview_broadcast).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = accent,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        CodeBlock(
                            text = broadcastAction,
                            accent = accent,
                            actionIcon = Icons.Outlined.Terminal,
                            actionDesc = stringResource(R.string.scripting_copy_broadcast)
                        ) {
                            haptics.confirm()
                            clipboard.setText(AnnotatedString(broadcastAction))
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ScriptActionButton(
                                icon = Icons.Outlined.Save,
                                label = if (editingId == null) stringResource(R.string.scripting_save)
                                        else stringResource(R.string.scripting_update),
                                container = accent,
                                content = MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.weight(1f)
                            ) {
                                haptics.confirm()
                                val finalName = name.ifBlank { selectedActionLabel }
                                onSaveScript(
                                    AppScript(
                                        id = editingId ?: UUID.randomUUID().toString(),
                                        name = finalName,
                                        actionId = selectedAction.id,
                                        params = params.toMap().filterValues { it.isNotBlank() }
                                    )
                                )
                                resetEditor()
                            }
                            ScriptActionButton(
                                icon = Icons.Outlined.PlayArrow,
                                label = stringResource(R.string.scripting_run),
                                container = accent.copy(alpha = 0.16f),
                                content = accent,
                                modifier = Modifier.weight(1f)
                            ) {
                                haptics.confirm()
                                onRunCommand(command)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        ScriptActionButton(
                            icon = Icons.Outlined.Share,
                            label = stringResource(R.string.scripting_share),
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            haptics.tap()
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, generatedUri)
                            }
                            context.startActivity(Intent.createChooser(share, null))
                        }

                        AnimatedVisibility(visible = editingId != null) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                ScriptActionButton(
                                    icon = Icons.Outlined.Close,
                                    label = stringResource(R.string.scripting_cancel_edit),
                                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    haptics.tap()
                                    resetEditor()
                                }
                            }
                        }
                    }
                }

                // ── libreria ─────────────────────────────────────────────────
                item {
                    SectionHeader(
                        label = stringResource(R.string.scripting_library_title),
                        accent = accent,
                        trailing = {
                            if (scripts.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accent.copy(alpha = 0.16f))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = scripts.size.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = accent
                                    )
                                }
                            }
                        }
                    )
                }

                if (scripts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ExpressiveHeroBadge(size = 56.dp, accent = accent) {
                                Icon(
                                    Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.scripting_library_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(scripts, key = { _, it -> it.id }) { index, script ->
                        ScriptLibraryItem(
                            script = script,
                            index = index,
                            onRun = {
                                val action = ScriptActionType.fromId(script.actionId)
                                if (action != null) onRunCommand(ScriptCommand(action, script.params))
                            },
                            onCopy = {
                                val action = ScriptActionType.fromId(script.actionId) ?: ScriptActionType.STOP
                                clipboard.setText(AnnotatedString(ScriptCommand(action, script.params).toUri()))
                            },
                            onShare = {
                                val action = ScriptActionType.fromId(script.actionId) ?: ScriptActionType.STOP
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, ScriptCommand(action, script.params).toUri())
                                }
                                context.startActivity(Intent.createChooser(share, null))
                            },
                            onEdit = { loadScript(script) },
                            onDelete = { onDeleteScript(script.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ScriptActionChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "chipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "chipContent"
    )
    val corner by animateDpAsState(
        targetValue = if (pressed) 12.dp else if (selected) 22.dp else 16.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chipCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "chipScale"
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}

@Composable
private fun CodeBlock(
    text: String,
    accent: Color,
    actionIcon: ImageVector,
    actionDesc: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = onAction,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = accent.copy(alpha = 0.20f),
                contentColor = accent
            )
        ) {
            Icon(actionIcon, contentDescription = actionDesc, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ScriptActionButton(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 14.dp else 26.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "scriptBtnCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "scriptBtnScale"
    )

    Row(
        modifier = modifier
            .heightIn(min = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun ScriptParamField(
    field: ScriptField,
    value: String?,
    accent: Color,
    onValueChange: (String?) -> Unit
) {
    when (field.type) {
        ScriptFieldType.BOOL -> ScriptTriStateRow(
            label = field.key,
            value = value,
            accent = accent,
            options = listOf(
                stringResource(R.string.scripting_value_default) to null,
                stringResource(R.string.scripting_value_on) to "true",
                stringResource(R.string.scripting_value_off) to "false"
            ),
            onValueChange = onValueChange
        )
        ScriptFieldType.SAMPLERATE -> ScriptTriStateRow(
            label = field.key,
            value = value,
            accent = accent,
            options = listOf(
                stringResource(R.string.scripting_value_default) to null,
                "44100" to "44100",
                "48000" to "48000"
            ),
            onValueChange = onValueChange
        )
        ScriptFieldType.CHANNELS -> ScriptTriStateRow(
            label = field.key,
            value = value?.uppercase(),
            accent = accent,
            options = listOf(
                stringResource(R.string.scripting_value_default) to null,
                "MONO" to "MONO",
                "STEREO" to "STEREO"
            ),
            onValueChange = onValueChange
        )
        ScriptFieldType.AUTHMODE -> ScriptTriStateRow(
            label = field.key,
            value = value?.uppercase(),
            accent = accent,
            options = listOf(
                stringResource(R.string.scripting_value_default) to null,
                stringResource(R.string.security_off) to "OFF",
                stringResource(R.string.security_ask) to "ASK",
                stringResource(R.string.security_key) to "KEY"
            ),
            onValueChange = onValueChange
        )
        ScriptFieldType.INT -> {
            val fieldHaptics = rememberAppHaptics()
            OutlinedTextField(
                value = value ?: "",
                onValueChange = { fieldHaptics.tick(); onValueChange(it.filter { c -> c.isDigit() }) },
                label = { Text(field.key, fontFamily = FontFamily.Monospace) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        ScriptFieldType.TEXT -> OutlinedTextField(
            value = value ?: "",
            onValueChange = { onValueChange(it) },
            label = { Text(field.key, fontFamily = FontFamily.Monospace) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptTriStateRow(
    label: String,
    value: String?,
    accent: Color,
    options: List<Pair<String, String?>>,
    onValueChange: (String?) -> Unit
) {
    val paramHaptics = rememberAppHaptics()
    var pulseIndex by remember { mutableStateOf(-1) }
    var pulseTick by remember { mutableStateOf(0) }

    LaunchedEffect(pulseTick) {
        if (pulseIndex >= 0) {
            delay(200)
            pulseIndex = -1
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, (optLabel, optValue) ->
                val distance = if (pulseIndex < 0) Int.MAX_VALUE else kotlin.math.abs(index - pulseIndex)
                val pop by animateFloatAsState(
                    targetValue = if (distance == 0) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
                    label = "triPop"
                )
                val squeeze by animateFloatAsState(
                    targetValue = if (distance == 1) 0.90f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "triSqueeze"
                )
                val selected = value == optValue
                val container by animateColorAsState(
                    targetValue = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    label = "triContainer"
                )
                val content by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    label = "triContent"
                )
                val corner by animateDpAsState(
                    targetValue = if (selected) 18.dp else 12.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "triCorner"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pop * squeeze
                            scaleY = pop
                        }
                        .clip(RoundedCornerShape(corner))
                        .background(container)
                        .clickable {
                            paramHaptics.toggle(true)
                            pulseIndex = index
                            pulseTick++
                            onValueChange(optValue)
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = optLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                        color = content,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScriptLibraryItem(
    script: AppScript,
    index: Int = 0,
    onRun: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = rememberAppHaptics()
    val cs = MaterialTheme.colorScheme
    val accent = when (index % 3) {
        0 -> cs.primary
        1 -> cs.secondary
        else -> cs.tertiary
    }
    val action = ScriptActionType.fromId(script.actionId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerLow)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExpressiveHeroBadge(size = 48.dp, accent = accent) {
                Icon(
                    imageVector = action?.let { scriptActionIcon(it) } ?: Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = script.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.3).sp,
                    color = cs.onSurface
                )
                Text(
                    text = (action?.let { scriptActionLabel(it) } ?: script.actionId) +
                        "  ·  " + stringResource(R.string.scripting_param_count, script.params.size),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.5.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }

        if (action != null) {
            Text(
                text = ScriptCommand(action, script.params).toUri(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surfaceContainerHighest)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalIconButton(
                onClick = { haptic.confirm(); onRun() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = accent,
                    contentColor = cs.surfaceContainerLowest
                )
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.scripting_run))
            }
            IconButton(onClick = { haptic.tap(); onCopy() }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.scripting_copy_uri))
            }
            IconButton(onClick = { haptic.tap(); onShare() }) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.scripting_share))
            }
            IconButton(onClick = { haptic.tap(); onEdit() }) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.scripting_edit))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { haptic.reject(); onDelete() }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.scripting_delete),
                    tint = cs.error
                )
            }
        }
    }
}

object BlackoutController {
    val active = mutableStateOf(false)
    val interactiveBounds = mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    val interactionTick = mutableStateOf(0)
    val wrongSpot = mutableStateOf(false)
    fun show() { active.value = true }
    fun hide() { active.value = false }
    fun poke() { interactionTick.value++ }
    fun signalWrongSpot() {
        wrongSpot.value = true
        poke()
    }
}

@Composable
fun BlackoutOverlay(outlinedUi: Boolean = false) {
    val active = BlackoutController.active.value
    val overlayAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "BlackoutOverlayAlpha"
    )

    if (!active && overlayAlpha == 0f) return

    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    val window = (view.context as? android.app.Activity)?.window
    DisposableEffect(active, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (active && controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val isStreaming by NetworkManager.isStreamingCurrent.collectAsState()
    LaunchedEffect(isStreaming) {
        if (!isStreaming) BlackoutController.hide()
    }

    BackHandler(enabled = active) { BlackoutController.hide() }

    var pendingTapAt by remember { mutableStateOf(0L) }
    var hintKey by remember { mutableStateOf(0) }
    val hintAlpha = remember { Animatable(0f) }

    LaunchedEffect(active) {
        if (active) {
            pendingTapAt = 0L
            hintKey = 0
            BlackoutController.interactionTick.value = 0
            BlackoutController.wrongSpot.value = false
            hintAlpha.snapTo(0f)
        }
    }

    LaunchedEffect(pendingTapAt) {
        if (pendingTapAt == 0L) return@LaunchedEffect
        delay(2000)
        pendingTapAt = 0L
    }

    val interactionTick = BlackoutController.interactionTick.value
    val wrongSpot = BlackoutController.wrongSpot.value
    LaunchedEffect(hintKey, interactionTick) {
        if (hintKey == 0 && !wrongSpot) return@LaunchedEffect
        hintAlpha.snapTo(1f)
        delay(5000)
        hintAlpha.animateTo(0f, animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }

    val reveal = if (outlinedUi) 1f else hintAlpha.value

    val tapCatcher = if (active) Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val now = android.os.SystemClock.elapsedRealtime()
                if (pendingTapAt != 0L && now - pendingTapAt <= 2000L) {
                    pendingTapAt = 0L
                    BlackoutController.hide()
                } else {
                    pendingTapAt = now
                    hintKey += 1
                    BlackoutController.wrongSpot.value = false
                }
            }
        )
    } else Modifier

    val hole = if (active && reveal > 0f) BlackoutController.interactiveBounds.value else null

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .graphicsLayer { alpha = overlayAlpha },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 1f - reveal))
        )

        if (hole == null) {
            Box(modifier = Modifier.matchParentSize().then(tapCatcher))
        } else {
            val d = LocalDensity.current
            val w = maxWidth
            val h = maxHeight
            val left = with(d) { hole.left.toDp() }.coerceIn(0.dp, w)
            val top = with(d) { hole.top.toDp() }.coerceIn(0.dp, h)
            val right = with(d) { hole.right.toDp() }.coerceIn(0.dp, w)
            val bottom = with(d) { hole.bottom.toDp() }.coerceIn(0.dp, h)

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(0.dp, 0.dp)
                    .size(w, top)
                    .then(tapCatcher)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(0.dp, bottom)
                    .size(w, (h - bottom).coerceAtLeast(0.dp))
                    .then(tapCatcher)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(0.dp, top)
                    .size(left, (bottom - top).coerceAtLeast(0.dp))
                    .then(tapCatcher)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(right, top)
                    .size((w - right).coerceAtLeast(0.dp), (bottom - top).coerceAtLeast(0.dp))
                    .then(tapCatcher)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .graphicsLayer { alpha = hintAlpha.value }
        ) {
            Icon(
                imageVector = Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(
                    if (wrongSpot) R.string.blackout_hint_wrong_spot else R.string.blackout_hint
                ),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}