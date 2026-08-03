/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 */

package com.cuscus.wifiaudiostreaming

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.graphics.shapes.*
import kotlinx.coroutines.delay

const val EXPIRED_QR_PAYLOAD =
    "https://www.marcomorosi.eu/wifi-audio-streaming/expired/"

data class QrInvite(
    val uri: String,
    val key: String,
    val ip: String,
    val port: Int,
    val multicast: Boolean,
    val expEpochSeconds: Long,
    val encryptionForced: Boolean = false
)

private fun formatRemaining(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrInviteSheet(
    invite: QrInvite,
    onRegenerate: () -> Unit,
    onRegenerateGroupKey: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberAppHaptics()
    val clipboard = LocalClipboardManager.current
    val accent = MaterialTheme.colorScheme.primary

    val ttl = (invite.expEpochSeconds - (System.currentTimeMillis() / 1000)).coerceAtLeast(1L)
    var remaining by remember(invite.uri) { mutableStateOf(ttl) }
    var copied by remember(invite.uri) { mutableStateOf(false) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var plainCode by remember { mutableStateOf(false) }

    LaunchedEffect(invite.uri) {
        haptics.gestureStart()
        var lastTick = Long.MAX_VALUE
        while (true) {
            val left = invite.expEpochSeconds - (System.currentTimeMillis() / 1000)
            remaining = left.coerceAtLeast(0L)
            if (left in 1..5 && left < lastTick) {
                lastTick = left
                haptics.tick()
            } else if ((left == 30L || left == 10L) && left < lastTick) {
                lastTick = left
                haptics.tick()
            }
            if (left <= 0L) break
            delay(500)
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val expired = remaining <= 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    if (expired) R.string.qr_pairing_expired_title else R.string.qr_invite_title
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(
                    when {
                        expired -> R.string.qr_pairing_expired_body
                        invite.multicast -> R.string.qr_invite_subtitle_multicast
                        else -> R.string.qr_invite_subtitle_unicast
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            QrCodeWithCountdown(
                invite = invite,
                remaining = remaining,
                totalSeconds = WfasPairingUri.PAIRING_TTL_SECONDS,
                accent = accent,
                expired = expired,
                plain = plainCode && !expired,
                onTogglePlain = {
                    if (!expired) {
                        haptics.toggle(!plainCode)
                        plainCode = !plainCode
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            if (!expired) {
                Text(
                    text = stringResource(R.string.qr_invite_countdown, formatRemaining(remaining)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (remaining <= 15L) MaterialTheme.colorScheme.error else accent
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.qr_invite_endpoint, invite.ip, invite.port),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = stringResource(
                        if (plainCode) R.string.qr_invite_tap_expressive
                        else R.string.qr_invite_tap_plain
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(18.dp))

                QrManualKeyBlock(
                    key = invite.key,
                    copied = copied,
                    onCopy = {
                        haptics.confirm()
                        clipboard.setText(AnnotatedString(invite.key))
                        copied = true
                    }
                )

                if (invite.multicast && invite.encryptionForced) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.qr_invite_encryption_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExpressiveDialogButton(
                    label = stringResource(R.string.qr_invite_close),
                    icon = Icons.Filled.Close,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    haptics.tap()
                    onDismiss()
                }
                ExpressiveDialogButton(
                    label = stringResource(
                        if (expired) R.string.qr_pairing_expired_primary
                        else if (invite.multicast) R.string.qr_invite_button_multicast
                        else R.string.qr_invite_button
                    ),
                    icon = Icons.Outlined.QrCode2,
                    container = accent,
                    content = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.weight(1f)
                ) {
                    haptics.confirm()
                    onRegenerate()
                }
            }

            if (onRegenerateGroupKey != null) {
                Spacer(Modifier.height(10.dp))
                ExpressiveDialogButton(
                    label = stringResource(R.string.qr_regenerate_button),
                    icon = Icons.Outlined.Shield,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    haptics.longPress()
                    confirmRegenerate = true
                }
            }
        }
    }

    if (confirmRegenerate && onRegenerateGroupKey != null) {
        QrRegenerateConfirmDialog(
            onConfirm = {
                confirmRegenerate = false
                onRegenerateGroupKey()
            },
            onDismiss = { confirmRegenerate = false }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QrCodeWithCountdown(
    invite: QrInvite,
    remaining: Long,
    totalSeconds: Long,
    accent: Color,
    expired: Boolean,
    plain: Boolean,
    onTogglePlain: () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = (remaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "QrCountdown"
    )
    val ringColor = if (remaining <= 15L) MaterialTheme.colorScheme.error else accent

    val scheme = MaterialTheme.colorScheme
    val plate = if (plain) Color.White else lerp(scheme.primaryContainer, Color.White, 0.93f)

    val palette = remember(scheme) {
        listOf(
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.onPrimaryContainer,
            scheme.onSecondaryContainer,
            scheme.onTertiaryContainer,
            scheme.error
        )
    }
    val moduleShapes = remember {
        listOf(
            MaterialShapes.Circle,
            MaterialShapes.Cookie4Sided,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Pentagon,
            MaterialShapes.Diamond,
            MaterialShapes.PuffyDiamond
        )
    }

    val haloMorph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Flower) }
    val infinite = rememberInfiniteTransition(label = "QrHalo")
    val haloProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "QrHaloMorph"
    )
    val haloSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(48000)),
        label = "QrHaloSpin"
    )

    Box(
        modifier = Modifier.size(304.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(286.dp)
                .graphicsLayer { rotationZ = haloSpin }
                .clip(MorphOutlineShape(haloMorph, haloProgress))
                .background(accent.copy(alpha = 0.13f))
        )

        val emptyRingColor = MaterialTheme.colorScheme.outlineVariant

        Canvas(modifier = Modifier.size(304.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = if (expired) emptyRingColor else ringColor.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (!expired) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        val plateCorner by animateDpAsState(
            targetValue = if (plain) 20.dp else 52.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "QrPlateCorner"
        )

        Box(
            modifier = Modifier
                .size(236.dp)
                .clip(RoundedCornerShape(plateCorner))
                .background(plate)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlain
                ),
            contentAlignment = Alignment.Center
        ) {
            val spentInk = lerp(scheme.onSurfaceVariant, plate, 0.52f)

            QrCodeCanvas(
                content = if (expired) EXPIRED_QR_PAYLOAD else invite.uri,
                modifier = Modifier.size(196.dp),
                shapes = if (expired) emptyList() else moduleShapes,
                palette = if (expired) listOf(spentInk) else palette,
                ringColor = if (expired) spentInk else scheme.primary,
                eyeColor = if (expired) spentInk else scheme.tertiary,
                eyeShape = if (expired) null else MaterialShapes.Cookie6Sided,
                plateColor = plate,
                plain = plain,
                minContrast = if (expired) 1f else 5f,
                animated = !expired,
                quietZoneModules = 0
            )

            if (expired) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.qr_invite_expired_pill),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun QrManualKeyBlock(
    key: String,
    copied: Boolean,
    onCopy: () -> Unit
) {
    val haptics = rememberAppHaptics()
    var revealed by remember(key) { mutableStateOf(false) }
    val shown = remember(key, revealed) {
        if (revealed) WfasAuth.groupKeyForDisplay(key)
        else WfasAuth.groupKeyForDisplay("•".repeat(key.length))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.qr_invite_key_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = copied,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(220))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.qr_invite_copied),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = shown,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = if (revealed) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    revealed = !revealed
                    haptics.toggle(revealed)
                }
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Outlined.VisibilityOff
                    else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (revealed) R.string.qr_invite_key_hide
                        else R.string.qr_invite_key_reveal
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(4.dp))
            ExpressiveDialogButton(
                label = stringResource(R.string.qr_invite_copy),
                icon = Icons.Outlined.ContentCopy,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onCopy
            )
        }

    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QrInviteAction(
    isMulticast: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val label = stringResource(
        if (isMulticast) R.string.qr_invite_button_multicast else R.string.qr_invite_button
    )

    val corner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 30.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "QrInviteCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "QrInviteScale"
    )

    val badgeMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val badgeProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "QrInviteBadge"
    )
    val infinite = rememberInfiniteTransition(label = "QrInviteSpin")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000)),
        label = "QrInviteSpinAngle"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(accent)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.confirm()
                onClick()
            }
            .padding(start = 10.dp, end = 26.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { rotationZ = spin }
                .clip(MorphOutlineShape(badgeMorph, badgeProgress))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCode2,
                contentDescription = stringResource(R.string.qr_invite_content_description),
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = -spin },
                tint = MaterialTheme.colorScheme.surfaceContainerLowest
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.2).sp,
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QrScanHandoff(onFinished: () -> Unit) {
    val haptics = rememberAppHaptics()
    val accent = MaterialTheme.colorScheme.primary
    val target = HeroOrbAnchor.bounds.value

    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Flower) }
    val progress = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        HeroOrbAnchor.handoffActive.value = true
        onDispose { HeroOrbAnchor.handoffActive.value = false }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(760, easing = FastOutSlowInEasing)
        )
        haptics.gestureEnd()
        onFinished()
    }

    LaunchedEffect(Unit) {
        delay(340)
        haptics.tick()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = progress.value
        val startSide = kotlin.math.min(size.width, size.height) * 0.70f * 0.92f
        val startCx = size.width / 2f
        val startCy = size.height / 2f

        val endSide = target?.width ?: (startSide * 0.55f)
        val endCx = target?.center?.x ?: startCx
        val endCy = target?.center?.y ?: (size.height * 0.28f)

        val side = startSide + (endSide - startSide) * t
        val cx = startCx + (endCx - startCx) * t
        val arc = kotlin.math.sin(t * Math.PI).toFloat() * size.height * 0.07f
        val cy = startCy + (endCy - startCy) * t - arc

        val turn = t * 30f
        val fill = Brush.linearGradient(
            colors = listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.45f)),
            start = Offset(cx - side / 2f, cy - side / 2f),
            end = Offset(cx + side / 2f, cy + side / 2f)
        )

        val halo = shapePathFor(morph, 0f, cx, cy, side * 1.18f, -turn * 0.6f)
        drawPath(halo, accent.copy(alpha = 0.16f))

        val body = shapePathFor(morph, 0f, cx, cy, side, turn)
        drawPath(body, fill)
    }
}

private fun shapePathFor(
    morph: Morph,
    progress: Float,
    cx: Float,
    cy: Float,
    side: Float,
    rotation: Float
): Path {
    val p = morph.toPath(progress.coerceIn(0f, 1f)).asComposePath()
    val b = p.getBounds()
    if (b.width <= 0f || b.height <= 0f) return Path()
    val scale = side / kotlin.math.max(b.width, b.height)
    val m = Matrix()
    m.translate(cx, cy)
    m.rotateZ(rotation)
    m.scale(scale, scale)
    m.translate(-b.left - b.width / 2f, -b.top - b.height / 2f)
    p.transform(m)
    return p
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EncryptedBadge() {
    val infinite = rememberInfiniteTransition(label = "EncBadge")
    val morphProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EncBadgeMorph"
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Gem) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(MorphOutlineShape(morph, morphProgress))
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiary
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.sec_encrypted),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
fun QrSecurityInfoCard(accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = accent
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.sec_mode_qr_info_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sec_mode_qr_info_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QrTwoButtonDialog(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    alert: Boolean,
    title: String,
    body: String,
    primaryLabel: String,
    primaryContainer: Color,
    primaryContent: Color,
    secondaryLabel: String?,
    destructive: Boolean = false,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberAppHaptics()

    LaunchedEffect(alert) {
        if (alert) haptics.reject() else haptics.gestureStart()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DialogShapeBadge(icon, accent, alert = alert)

                Spacer(Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))

                if (secondaryLabel == null) {
                    ExpressiveDialogButton(
                        label = primaryLabel,
                        icon = null,
                        container = primaryContainer,
                        content = primaryContent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        haptics.tap()
                        onPrimary()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExpressiveDialogButton(
                            label = secondaryLabel,
                            icon = null,
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            haptics.tap()
                            onDismiss()
                        }
                        ExpressiveDialogButton(
                            label = primaryLabel,
                            icon = null,
                            container = primaryContainer,
                            content = primaryContent,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (destructive) haptics.longPress() else haptics.confirm()
                            onPrimary()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrExpiredDialog(onRegenerate: () -> Unit, onDismiss: () -> Unit) {
    QrTwoButtonDialog(
        icon = Icons.Outlined.HourglassEmpty,
        accent = MaterialTheme.colorScheme.tertiary,
        alert = true,
        title = stringResource(R.string.qr_pairing_expired_title),
        body = stringResource(R.string.qr_pairing_expired_body),
        primaryLabel = stringResource(R.string.qr_pairing_expired_primary),
        primaryContainer = MaterialTheme.colorScheme.primary,
        primaryContent = MaterialTheme.colorScheme.surfaceContainerLowest,
        secondaryLabel = stringResource(R.string.qr_pairing_expired_secondary),
        onPrimary = onRegenerate,
        onDismiss = onDismiss
    )
}

@Composable
fun QrInvalidDialog(onRetry: () -> Unit, onDismiss: () -> Unit) {
    QrTwoButtonDialog(
        icon = Icons.Outlined.QrCodeScanner,
        accent = MaterialTheme.colorScheme.error,
        alert = true,
        title = stringResource(R.string.qr_pairing_invalid_title),
        body = stringResource(R.string.qr_pairing_invalid_body),
        primaryLabel = stringResource(R.string.qr_pairing_invalid_retry),
        primaryContainer = MaterialTheme.colorScheme.primary,
        primaryContent = MaterialTheme.colorScheme.surfaceContainerLowest,
        secondaryLabel = stringResource(R.string.qr_pairing_invalid_cancel),
        onPrimary = onRetry,
        onDismiss = onDismiss
    )
}

@Composable
fun QrInviteRejectedDialog(onRescan: () -> Unit, onDismiss: () -> Unit) {
    QrTwoButtonDialog(
        icon = Icons.Outlined.HourglassEmpty,
        accent = MaterialTheme.colorScheme.tertiary,
        alert = true,
        title = stringResource(R.string.qr_pairing_superseded_title),
        body = stringResource(R.string.qr_pairing_superseded_body),
        primaryLabel = stringResource(R.string.qr_pairing_superseded_button),
        primaryContainer = MaterialTheme.colorScheme.primary,
        primaryContent = MaterialTheme.colorScheme.surfaceContainerLowest,
        secondaryLabel = stringResource(R.string.qr_pairing_superseded_dismiss),
        onPrimary = onRescan,
        onDismiss = onDismiss
    )
}

@Composable
fun QrSelfPairingDialog(onDismiss: () -> Unit) {
    QrTwoButtonDialog(
        icon = Icons.Outlined.Smartphone,
        accent = MaterialTheme.colorScheme.tertiary,
        alert = true,
        title = stringResource(R.string.qr_pairing_self_title),
        body = stringResource(R.string.qr_pairing_self_body),
        primaryLabel = stringResource(R.string.qr_pairing_self_button),
        primaryContainer = MaterialTheme.colorScheme.tertiary,
        primaryContent = MaterialTheme.colorScheme.surfaceContainerLowest,
        secondaryLabel = null,
        onPrimary = onDismiss,
        onDismiss = onDismiss
    )
}

@Composable
fun QrEpochMismatchDialog(onDismiss: () -> Unit) {
    QrTwoButtonDialog(
        icon = Icons.Outlined.Groups,
        accent = MaterialTheme.colorScheme.tertiary,
        alert = true,
        title = stringResource(R.string.qr_pairing_epoch_mismatch_title),
        body = stringResource(R.string.qr_pairing_epoch_mismatch_body),
        primaryLabel = stringResource(R.string.qr_pairing_epoch_mismatch_button),
        primaryContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
        primaryContent = MaterialTheme.colorScheme.onSurfaceVariant,
        secondaryLabel = null,
        onPrimary = onDismiss,
        onDismiss = onDismiss
    )
}

@Composable
fun QrNoCameraDialog(onManual: () -> Unit, onDismiss: () -> Unit) {
    QrTwoButtonDialog(
        icon = Icons.Outlined.VideocamOff,
        accent = MaterialTheme.colorScheme.secondary,
        alert = false,
        title = stringResource(R.string.qr_pairing_no_camera_title),
        body = stringResource(R.string.qr_pairing_no_camera_body),
        primaryLabel = stringResource(R.string.qr_pairing_no_camera_manual),
        primaryContainer = MaterialTheme.colorScheme.primary,
        primaryContent = MaterialTheme.colorScheme.surfaceContainerLowest,
        secondaryLabel = stringResource(R.string.qr_pairing_no_camera_close),
        onPrimary = onManual,
        onDismiss = onDismiss
    )
}

@Composable
fun QrRegenerateConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    QrTwoButtonDialog(
        icon = Icons.Outlined.Refresh,
        accent = MaterialTheme.colorScheme.error,
        alert = true,
        title = stringResource(R.string.qr_pairing_regenerate_confirm_title),
        body = stringResource(R.string.qr_pairing_regenerate_confirm_body),
        primaryLabel = stringResource(R.string.qr_pairing_regenerate_confirm_confirm),
        primaryContainer = MaterialTheme.colorScheme.error,
        primaryContent = MaterialTheme.colorScheme.onError,
        secondaryLabel = stringResource(R.string.qr_pairing_regenerate_confirm_cancel),
        destructive = true,
        onPrimary = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun QrDeepLinkConfirmDialog(
    payload: PairingPayload,
    serverRunning: Boolean,
    onConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val base = stringResource(
        if (payload.isMulticast) R.string.qr_pairing_deeplink_confirm_body_multicast
        else R.string.qr_pairing_deeplink_confirm_body_unicast
    )
    val warning = stringResource(R.string.qr_pairing_deeplink_confirm_stops_server)

    QrTwoButtonDialog(
        icon = Icons.Outlined.QrCode2,
        accent = if (serverRunning) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.primary,
        alert = serverRunning,
        title = stringResource(R.string.qr_pairing_deeplink_confirm_title, NetAddr.display(payload.ip)),
        body = if (serverRunning) "$warning\n\n$base" else base,
        primaryLabel = stringResource(R.string.qr_pairing_deeplink_confirm_connect),
        primaryContainer = MaterialTheme.colorScheme.primary,
        primaryContent = MaterialTheme.colorScheme.surfaceContainerLowest,
        secondaryLabel = stringResource(R.string.qr_pairing_deeplink_confirm_ignore),
        onPrimary = onConnect,
        onDismiss = onDismiss
    )
}
