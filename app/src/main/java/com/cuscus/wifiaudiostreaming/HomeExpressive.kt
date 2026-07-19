package com.cuscus.wifiaudiostreaming

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.cuscus.wifiaudiostreaming.data.AppSettings
import kotlin.math.min

internal class MorphOutlineShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f)).asComposePath()
        val bounds = path.getBounds()
        if (bounds.width <= 0f || bounds.height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val scale = min(size.width / bounds.width, size.height / bounds.height)
        val matrix = Matrix()
        matrix.translate(size.width / 2f, size.height / 2f)
        matrix.rotateZ(rotation)
        matrix.scale(scale, scale)
        matrix.translate(
            -bounds.left - bounds.width / 2f,
            -bounds.top - bounds.height / 2f
        )
        path.transform(matrix)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is MorphOutlineShape &&
            other.morph === morph &&
            other.progress == progress &&
            other.rotation == rotation

    override fun hashCode(): Int =
        morph.hashCode() * 31 + progress.hashCode() * 31 + rotation.hashCode()
}

private enum class HeroPhase { Configuring, Ready, Searching, Live }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveHomeScreen(
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
    val sourceReady = appSettings.streamInternal || appSettings.streamMic
    val phase = when {
        isStreaming -> HeroPhase.Live
        isServer && sourceReady -> HeroPhase.Ready
        isServer -> HeroPhase.Configuring
        else -> HeroPhase.Searching
    }

    val accent by animateColorAsState(
        targetValue = when (phase) {
            HeroPhase.Live -> MaterialTheme.colorScheme.primary
            HeroPhase.Ready -> MaterialTheme.colorScheme.tertiary
            HeroPhase.Searching -> MaterialTheme.colorScheme.secondary
            HeroPhase.Configuring -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "HomeAccent"
    )

    val canvasColor by animateColorAsState(
        targetValue = if (isStreaming) MaterialTheme.colorScheme.surfaceContainerLowest
        else MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "HomeCanvas"
    )

    Scaffold(containerColor = canvasColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            HomeMasthead(
                isServer = isServer,
                accent = accent,
                onOpenSettings = onOpenSettings
            )

            StateHero(
                phase = phase,
                isServer = isServer,
                accent = accent,
                connectionStatus = connectionStatus,
                localIp = localIp,
                hasMicPermission = hasMicPermission,
                onStartServer = onStartServer,
                onStopServer = onStopServer
            )

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visible = !isStreaming,
                enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280, delayMillis = 60)),
                exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Column {
                    ModeSwitcher(
                        isServer = isServer,
                        enabled = !isStreaming,
                        onToggleMode = { serverMode ->
                            onToggleMode(serverMode)
                            if (!serverMode) onRefresh()
                        }
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }

            AnimatedVisibility(
                visible = isStreaming,
                enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280, delayMillis = 60)),
                exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                LiveControls(
                    isServer = isServer,
                    localIp = localIp,
                    accent = accent,
                    sendClientMicrophone = appSettings.sendClientMicrophone
                )
            }

            AnimatedVisibility(
                visible = isServer && !isStreaming,
                enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280, delayMillis = 60)),
                exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                ExpressiveSourceSection(
                    streamInternal = appSettings.streamInternal,
                    streamMic = appSettings.streamMic,
                    isMulticast = isMulticastMode,
                    rtpEnabled = appSettings.rtpEnabled,
                    httpEnabled = appSettings.httpEnabled,
                    securityMode = appSettings.securityMode,
                    authKey = appSettings.authKey,
                    encryptionEnabled = appSettings.encryptionEnabled,
                    accent = accent,
                    onStreamInternalChange = onStreamInternalChange,
                    onStreamMicChange = onStreamMicChange,
                    onMulticastChange = onMulticastModeChange,
                    onSecurityChange = onSecurityChange,
                    onEncryptionChange = onEncryptionChange
                )
            }

            AnimatedVisibility(
                visible = isServer && isStreaming && appSettings.rtpEnabled,
                enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280, delayMillis = 60)),
                exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveRtpSdpBanner(
                        port = appSettings.rtpPort,
                        sampleRate = appSettings.sampleRate,
                        channels = if (appSettings.channelConfig == "STEREO") 2 else 1
                    )
                }
            }

            AnimatedVisibility(
                visible = isServer && isStreaming && appSettings.httpEnabled,
                enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280, delayMillis = 60)),
                exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveHttpBanner(ip = localIp, port = appSettings.httpPort)
                }
            }

            AnimatedVisibility(
                visible = !isServer && !isStreaming,
                enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280, delayMillis = 60)),
                exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    ExpressiveClientOptionsSection(
                        sendMicrophone = appSettings.sendClientMicrophone,
                        accent = accent,
                        onSendMicrophoneChange = onSendClientMicrophoneChange
                    )

                    Column {
                        SectionHeader(stringResource(R.string.manual_ip_hint), accent)
                        var manualIp by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                placeholder = { Text("192.168.1.10") },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp)
                            )
                            val haptics = rememberAppHaptics()
                            FilledTonalIconButton(
                                onClick = {
                                    haptics.confirm()
                                    onConnectManual(manualIp)
                                },
                                enabled = manualIp.isNotBlank(),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Podcasts,
                                    contentDescription = stringResource(R.string.connect_button_description)
                                )
                            }
                        }
                    }

                    ExpressiveDiscoverySection(
                        devices = discoveredDevices,
                        accent = accent,
                        onConnect = onConnect,
                        onRefresh = onRefresh
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HomeMasthead(
    isServer: Boolean,
    accent: Color,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(if (isServer) R.string.send_title else R.string.receive_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = accent
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val haptics = rememberAppHaptics()
        IconButton(onClick = { haptics.tap(); onOpenSettings() }) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StateHero(
    phase: HeroPhase,
    isServer: Boolean,
    accent: Color,
    connectionStatus: String,
    localIp: String,
    hasMicPermission: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    val haptics = rememberAppHaptics()
    val live = phase == HeroPhase.Live

    val targetShape = when (phase) {
        HeroPhase.Configuring -> MaterialShapes.Circle
        HeroPhase.Searching -> MaterialShapes.Cookie9Sided
        HeroPhase.Ready -> MaterialShapes.Sunny
        HeroPhase.Live -> MaterialShapes.Cookie12Sided
    }

    var fromShape by remember { mutableStateOf(targetShape) }
    var toShape by remember { mutableStateOf(targetShape) }
    val morphProgressAnim = remember { Animatable(1f) }
    val tapScale = remember { Animatable(1f) }
    val tapSpin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    suspend fun morphTo(target: RoundedPolygon, bouncy: Boolean) {
        if (target === toShape) return
        fromShape = toShape
        toShape = target
        try {
            morphProgressAnim.snapTo(0f)
            morphProgressAnim.animateTo(
                targetValue = 1f,
                animationSpec = if (bouncy) spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ) else tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        } finally {
            withContext(NonCancellable) { morphProgressAnim.snapTo(1f) }
        }
    }

    LaunchedEffect(targetShape) {
        morphTo(targetShape, bouncy = false)
    }

    val morph = remember(fromShape, toShape) { Morph(fromShape, toShape) }
    val morphProgress = morphProgressAnim.value

    val onOrbTap: () -> Unit = {
        haptics.press()
        scope.launch { morphTo(randomExpressiveShape(exclude = toShape), bouncy = true) }
        scope.launch {
            tapScale.snapTo(0.86f)
            tapScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        scope.launch {
            tapSpin.animateTo(
                targetValue = tapSpin.value + 72f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessVeryLow
                )
            )
        }
    }

    var boost by remember { mutableStateOf(0f) }
    var extraSpin by remember { mutableStateOf(0f) }
    var spinJob by remember { mutableStateOf<Job?>(null) }

    suspend fun runSpinBoost() {
        var last = withFrameNanos { it }
        var held = 0f
        var untilNextPulse = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
            last = now

            held += dt
            boost = (held / 5f).coerceAtMost(1f)
            extraSpin += dt * boost * boost * 2200f

            untilNextPulse -= dt
            if (untilNextPulse <= 0f) {
                when {
                    boost > 0.85f -> haptics.longPress()
                    boost > 0.60f -> haptics.confirm()
                    boost > 0.30f -> haptics.tap()
                    else -> haptics.tick()
                }
                untilNextPulse = 0.26f - 0.215f * boost
            }
        }
    }

    suspend fun decaySpinBoost() {
        var last = withFrameNanos { it }
        while (boost > 0.002f) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
            last = now
            boost = (boost - dt * 0.7f).coerceAtLeast(0f)
            extraSpin += dt * boost * boost * 2200f
        }
        boost = 0f
    }

    val infinite = rememberInfiniteTransition(label = "HeroInfinite")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing)),
        label = "HeroSpin"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(1900, easing = EaseInOutSine),
            RepeatMode.Reverse
        ),
        label = "HeroBreathe"
    )

    val orbSize by animateDpAsState(
        targetValue = if (live) 204.dp else 180.dp,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "HeroOrbSize"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.heightIn(min = 232.dp),
            contentAlignment = Alignment.Center
        ) {
            if (live) {
                Box(
                    modifier = Modifier
                        .size(orbSize)
                        .graphicsLayer {
                            val halo = 1.18f + (breathe - 1f) * 2.2f + boost * 0.22f
                            scaleX = halo
                            scaleY = halo
                            alpha = 0.16f + boost * 0.34f
                            rotationZ = -(spin + extraSpin) * 0.6f
                        }
                        .clip(MorphOutlineShape(morph, morphProgress))
                        .background(accent)
                )
            }

            Box(
                modifier = Modifier
                    .size(orbSize)
                    .graphicsLayer {
                        rotationZ = (if (live) spin else 0f) + tapSpin.value + extraSpin
                        val s = (if (live) breathe else 1f) * tapScale.value * (1f + boost * 0.08f)
                        scaleX = s
                        scaleY = s
                    }
                    .clip(MorphOutlineShape(morph, morphProgress))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = if (live) 0.95f else 0.30f),
                                accent.copy(alpha = if (live) 0.45f else 0.14f)
                            )
                        )
                    )
                    .pointerInput(live) {
                        detectTapGestures(
                            onPress = {
                                if (live) {
                                    spinJob?.cancel()
                                    spinJob = scope.launch { runSpinBoost() }
                                    tryAwaitRelease()
                                    spinJob?.cancel()
                                    spinJob = scope.launch { decaySpinBoost() }
                                } else {
                                    tryAwaitRelease()
                                }
                            },
                            onTap = { if (!live) onOrbTap() }
                        )
                    }
            )

            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.6f)).togetherWith(
                        fadeOut(tween(160)) + scaleOut(targetScale = 0.6f)
                    )
                },
                label = "HeroIcon",
                modifier = Modifier.graphicsLayer {
                    scaleX = tapScale.value
                    scaleY = tapScale.value
                }
            ) { p ->
                Icon(
                    imageVector = when (p) {
                        HeroPhase.Live -> Icons.Outlined.GraphicEq
                        HeroPhase.Ready -> Icons.Outlined.WifiTethering
                        HeroPhase.Searching -> Icons.Outlined.Podcasts
                        HeroPhase.Configuring -> Icons.Outlined.Info
                    },
                    contentDescription = null,
                    modifier = Modifier.size(if (live) 76.dp else 60.dp),
                    tint = if (live) MaterialTheme.colorScheme.surfaceContainerLowest
                    else accent
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(260, delayMillis = 90)),
                        initialContentExit = fadeOut(tween(140)),
                        sizeTransform = SizeTransform(clip = false)
                    )
                },
                label = "HeroHeadline"
            ) { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (p) {
                            HeroPhase.Live -> stringResource(R.string.streaming_active).trimEnd('.', '…')
                            HeroPhase.Ready -> stringResource(R.string.ready_to_stream)
                            HeroPhase.Searching -> stringResource(R.string.waiting_for_server).trimEnd('.', '…')
                            HeroPhase.Configuring -> stringResource(R.string.select_source_text)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        lineHeight = 40.sp,
                        letterSpacing = (-1).sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (p) {
                            HeroPhase.Ready -> localIp
                            HeroPhase.Searching -> stringResource(R.string.find_device_text)
                            else -> connectionStatus
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (p == HeroPhase.Ready) FontFamily.Monospace else FontFamily.Default,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        var showMicExplanation by remember { mutableStateOf(false) }

        if (isServer && phase == HeroPhase.Ready && !hasMicPermission) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MicOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.mic_permission_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f),
                        modifier = Modifier.clickable {
                            haptics.tap()
                            showMicExplanation = true
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.mic_permission_why_button),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (showMicExplanation) {
            ExpressiveInfoDialog(
                icon = Icons.Outlined.MicOff,
                accent = MaterialTheme.colorScheme.error,
                title = stringResource(R.string.mic_permission_explanation_title),
                body = stringResource(R.string.mic_permission_explanation_body),
                confirmLabel = stringResource(R.string.cancel),
                onDismiss = { showMicExplanation = false }
            )
        }

        if (isServer || live) {
            Spacer(Modifier.height(28.dp))
            HeroActionButton(
                live = live,
                enabled = live || phase == HeroPhase.Ready,
                accent = accent,
                onClick = {
                    if (live) haptics.reject() else haptics.confirm()
                    if (live) onStopServer() else onStartServer()
                }
            )
        }
    }
}

@Composable
private fun HeroActionButton(
    live: Boolean,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (live) 20.dp else 42.dp,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "HeroActionCorner"
    )
    val container by animateColorAsState(
        targetValue = if (live) MaterialTheme.colorScheme.errorContainer else accent,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "HeroActionContainer"
    )
    val content by animateColorAsState(
        targetValue = if (live) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "HeroActionContent"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        AnimatedContent(
            targetState = live,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.7f)).togetherWith(
                    fadeOut(tween(140)) + scaleOut(targetScale = 0.7f)
                )
            },
            label = "HeroActionIcon"
        ) { streaming ->
            Icon(
                imageVector = if (streaming) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(
                if (live) R.string.stop_streaming_button else R.string.start_server_button
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeSwitcher(
    isServer: Boolean,
    enabled: Boolean,
    onToggleMode: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        ExpressiveModeButton(
            icon = Icons.Outlined.Download,
            selectedIcon = Icons.Outlined.Download,
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
            selectedIcon = Icons.Outlined.Upload,
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

@Composable
private fun LiveControls(
    isServer: Boolean,
    localIp: String,
    accent: Color,
    sendClientMicrophone: Boolean
) {
    val clipboard = LocalClipboardManager.current
    val haptics = rememberAppHaptics()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isServer && localIp.isNotEmpty() && localIp != "0.0.0.0") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(localIp))
                    haptics.confirm()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Filled.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accent
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = localIp,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = accent.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (!isServer && sendClientMicrophone) {
            val muted by NetworkManager.isMicMuted.collectAsState()
            FilledTonalButton(
                onClick = {
                    haptics.toggle(muted)
                    NetworkManager.isMicMuted.value = !muted
                },
                shape = RoundedCornerShape(24.dp),
                colors = if (muted) ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) else ButtonDefaults.filledTonalButtonColors()
            ) {
                Icon(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (muted) R.string.mic_muted else R.string.mic_active))
            }
        }

        if (!isServer) {
            FilledTonalButton(
                onClick = {
                    haptics.longPress()
                    BlackoutController.show()
                },
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Filled.DarkMode, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.blackout_button))
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.playback_keep_open_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isServer) {
            val volume by NetworkManager.serverVolume.collectAsState()
            Text(
                text = stringResource(R.string.transmission_volume, (volume * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Slider(
                value = volume,
                onValueChange = {
                    if (kotlin.math.abs(it - volume) > 0.04f) haptics.tick()
                    NetworkManager.serverVolume.value = it
                },
                valueRange = 0f..2f,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
            Text(
                text = stringResource(R.string.server_audio_restart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
