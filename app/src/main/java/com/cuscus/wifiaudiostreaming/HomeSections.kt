package com.cuscus.wifiaudiostreaming

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val badgeShapePool: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Cookie7Sided,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Flower,
        MaterialShapes.Sunny,
        MaterialShapes.VerySunny,
        MaterialShapes.Pentagon
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val heroShapePool: List<RoundedPolygon> by lazy {
    badgeShapePool + listOf(
        MaterialShapes.Burst,
        MaterialShapes.SoftBurst,
        MaterialShapes.Gem,
        MaterialShapes.PixelCircle
    )
}

internal fun randomBadgeShape(exclude: RoundedPolygon? = null): RoundedPolygon {
    val candidates = badgeShapePool.filter { it !== exclude }
    return candidates[Random.nextInt(candidates.size)]
}

internal fun randomExpressiveShape(exclude: RoundedPolygon? = null): RoundedPolygon {
    val candidates = heroShapePool.filter { it !== exclude }
    return candidates[Random.nextInt(candidates.size)]
}

@Composable
fun SectionHeader(
    label: String,
    accent: Color,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = accent,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphIconBadge(
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    idleColor: Color,
    contentActive: Color,
    contentIdle: Color,
    size: androidx.compose.ui.unit.Dp = 52.dp
) {
    var toShape by remember {
        mutableStateOf(if (active) randomBadgeShape() else MaterialShapes.Circle)
    }
    var fromShape by remember { mutableStateOf(toShape) }
    var morphKey by remember { mutableStateOf(0) }

    LaunchedEffect(active) {
        val target =
            if (active) randomBadgeShape(exclude = toShape) else MaterialShapes.Circle
        if (target !== toShape) {
            fromShape = toShape
            toShape = target
            morphKey++
        }
    }

    val progress = remember(morphKey) { Animatable(if (morphKey == 0) 1f else 0f) }

    LaunchedEffect(morphKey) {
        if (morphKey != 0) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val morph = remember(fromShape, toShape) { Morph(fromShape, toShape) }
    val container by animateColorAsState(
        targetValue = if (active) activeColor else idleColor,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "BadgeContainer"
    )
    val content by animateColorAsState(
        targetValue = if (active) contentActive else contentIdle,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "BadgeContent"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(MorphOutlineShape(morph, progress.value))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

@Composable
fun ExpressiveToggleTile(
    icon: ImageVector,
    activeIcon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val corner by animateDpAsState(
        targetValue = if (pressed) 14.dp else if (checked) 30.dp else 24.dp,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "TileCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "TileScale"
    )
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
            checked -> accent.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "TileContainer"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) {
                haptics.toggle(!checked)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MorphIconBadge(
            icon = if (checked) activeIcon else icon,
            active = checked,
            activeColor = accent,
            idleColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentActive = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentIdle = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.toggle(it)
                onCheckedChange(it)
            },
            enabled = enabled
        )
    }
}

data class ChoiceOption(
    val icon: ImageVector,
    val label: String,
    val value: String
)

@Composable
fun ExpressiveChoiceRow(
    options: List<ChoiceOption>,
    selectedValue: String,
    accent: Color,
    onSelect: (String) -> Unit
) {
    val haptics = rememberAppHaptics()
    var pulseIndex by remember { mutableStateOf(-1) }
    var pulseTick by remember { mutableStateOf(0) }

    LaunchedEffect(pulseTick) {
        if (pulseIndex >= 0) {
            delay(200)
            pulseIndex = -1
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEachIndexed { index, option ->
            val distance = if (pulseIndex < 0) Int.MAX_VALUE else kotlin.math.abs(index - pulseIndex)

            val popScale by animateFloatAsState(
                targetValue = if (distance == 0) 1.08f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "PillPop"
            )
            val squeezeX by animateFloatAsState(
                targetValue = if (distance == 1) 0.90f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "PillSqueeze"
            )
            val squeezeY by animateFloatAsState(
                targetValue = if (distance == 1) 1.04f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "PillSqueezeY"
            )

            ExpressiveChoicePill(
                icon = option.icon,
                label = option.label,
                selected = selectedValue == option.value,
                accent = accent,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = popScale * squeezeX
                        scaleY = popScale * squeezeY
                    }
            ) {
                haptics.confirm()
                pulseIndex = index
                pulseTick++
                onSelect(option.value)
            }
        }
    }
}

@Composable
fun ExpressiveChoicePill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "PillContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "PillContent"
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 22.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PillCorner"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}

@Composable
fun ExpressiveSourceSection(
    streamInternal: Boolean,
    streamMic: Boolean,
    isMulticast: Boolean,
    rtpEnabled: Boolean,
    securityMode: String,
    authKey: String,
    encryptionEnabled: Boolean,
    accent: Color,
    onStreamInternalChange: (Boolean) -> Unit,
    onStreamMicChange: (Boolean) -> Unit,
    onMulticastChange: (Boolean) -> Unit,
    onSecurityChange: (String, String) -> Unit,
    onEncryptionChange: (Boolean) -> Unit
) {
    val haptics = rememberAppHaptics()
    val secMode = securityMode.uppercase()
    val multicastActive = isMulticast || rtpEnabled

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.source_selector_title), accent)

        ExpressiveToggleTile(
            icon = Icons.Outlined.Speaker,
            activeIcon = Icons.Filled.Speaker,
            title = stringResource(R.string.internal_audio_title),
            subtitle = stringResource(R.string.internal_audio_subtitle),
            checked = streamInternal,
            accent = accent,
            onCheckedChange = onStreamInternalChange
        )
        Spacer(Modifier.height(10.dp))
        ExpressiveToggleTile(
            icon = Icons.Outlined.Mic,
            activeIcon = Icons.Filled.Mic,
            title = stringResource(R.string.microphone_title),
            subtitle = stringResource(R.string.microphone_subtitle),
            checked = streamMic,
            accent = accent,
            onCheckedChange = onStreamMicChange
        )

        Spacer(Modifier.height(28.dp))
        SectionHeader(stringResource(R.string.transmission_mode_title), accent)

        ExpressiveToggleTile(
            icon = if (multicastActive) Icons.Outlined.Groups else Icons.Outlined.Person,
            activeIcon = if (multicastActive) Icons.Filled.Groups else Icons.Filled.Person,
            title = stringResource(
                if (multicastActive) R.string.multicast_mode_title else R.string.unicast_mode_title
            ),
            subtitle = when {
                rtpEnabled -> stringResource(R.string.rtp_forces_multicast)
                multicastActive -> stringResource(R.string.multicast_mode_desc)
                else -> stringResource(R.string.unicast_mode_desc)
            },
            checked = multicastActive,
            enabled = !rtpEnabled,
            accent = accent,
            onCheckedChange = onMulticastChange
        )

        Spacer(Modifier.height(28.dp))
        SectionHeader(stringResource(R.string.settings_group_security), accent)

        ExpressiveChoiceRow(
            options = listOf(
                ChoiceOption(Icons.Outlined.LockOpen, stringResource(R.string.sec_mode_off), "OFF"),
                ChoiceOption(Icons.Outlined.PersonAdd, stringResource(R.string.sec_mode_ask), "ASK"),
                ChoiceOption(Icons.Outlined.Key, stringResource(R.string.sec_mode_key), "KEY")
            ),
            selectedValue = secMode,
            accent = accent,
            onSelect = { onSecurityChange(it, authKey) }
        )

        AnimatedVisibility(
            visible = secMode == "KEY",
            enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(240, delayMillis = 60)),
            exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                var keyText by remember { mutableStateOf(authKey) }
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it; onSecurityChange(securityMode, it) },
                    label = { Text(stringResource(R.string.settings_item_auth_key_title)) },
                    leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.EnhancedEncryption,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (secMode == "KEY") accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_item_encryption_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_item_encryption_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = encryptionEnabled && secMode == "KEY",
                onCheckedChange = {
                    haptics.toggle(it)
                    onEncryptionChange(it)
                },
                enabled = secMode == "KEY"
            )
        }
    }
}

@Composable
fun ExpressiveClientOptionsSection(
    sendMicrophone: Boolean,
    accent: Color,
    onSendMicrophoneChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.client_options_title), accent)
        ExpressiveToggleTile(
            icon = Icons.Outlined.MicOff,
            activeIcon = Icons.Filled.Mic,
            title = stringResource(R.string.send_mic_to_server_title),
            subtitle = stringResource(R.string.send_mic_to_server_subtitle),
            checked = sendMicrophone,
            accent = accent,
            onCheckedChange = onSendMicrophoneChange
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveDiscoverySection(
    devices: Map<String, ServerInfo>,
    accent: Color,
    onConnect: (ServerInfo) -> Unit,
    onRefresh: () -> Unit
) {
    val haptics = rememberAppHaptics()

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            label = stringResource(R.string.nearby_devices_title),
            accent = accent,
            trailing = {
                if (devices.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = devices.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = accent
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                FilledTonalIconButton(
                    onClick = {
                        haptics.tap()
                        onRefresh()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.refresh_button_description),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        AnimatedContent(
            targetState = devices.isEmpty(),
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + scaleIn(initialScale = 0.94f))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.94f))
            },
            label = "DiscoveryContent"
        ) { empty ->
            if (empty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(52.dp),
                        color = accent
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.searching_indicator_text),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.searching_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    devices.values.forEach { info ->
                        ExpressiveDeviceRow(
                            info = info,
                            accent = accent,
                            onConnect = { onConnect(info) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveDeviceRow(
    info: ServerInfo,
    accent: Color,
    onConnect: () -> Unit
) {
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val corner by animateDpAsState(
        targetValue = if (pressed) 14.dp else 28.dp,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "DeviceCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "DeviceScale"
    )

    val shape = remember(info.ip) {
        val pool = listOf(
            MaterialShapes.Cookie7Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Gem,
            MaterialShapes.Pentagon,
            MaterialShapes.Flower
        )
        pool[(info.ip.hashCode() and 0x7fffffff) % pool.size]
    }
    val avatarMorph = remember(shape) { Morph(shape, shape) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.confirm()
                onConnect()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(MorphOutlineShape(avatarMorph, 1f))
                .background(accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (info.isMulticast) Icons.Outlined.Groups else Icons.Outlined.Person,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = accent
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = info.ip,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    if (info.isMulticast) R.string.mode_multicast else R.string.mode_unicast
                ).uppercase() + "  ·  " + info.port,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DeviceBadges(
                securityMode = info.securityMode,
                encrypted = info.encrypted,
                serverSendsMic = info.serverSendsMic,
                serverWantsMic = info.serverWantsMic
            )
        }

        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.connect_button_description),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.surfaceContainerLowest
            )
        }
    }
}
