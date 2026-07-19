package com.cuscus.wifiaudiostreaming

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.graphics.shapes.Morph

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DialogShapeBadge(
    icon: ImageVector,
    accent: Color,
    alert: Boolean
) {
    val infinite = rememberInfiniteTransition(label = "DialogBadge")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26000)),
        label = "DialogBadgeSpin"
    )

    val restShape = if (alert) MaterialShapes.Clover4Leaf else MaterialShapes.Cookie9Sided
    val morph = remember(restShape) { Morph(MaterialShapes.Circle, restShape) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer { rotationZ = spin }
            .clip(MorphOutlineShape(morph, progress.value))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { rotationZ = -spin },
            tint = accent
        )
    }
}

@Composable
private fun ExpressiveDialogButton(
    label: String,
    icon: ImageVector?,
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
        label = "DialogBtnCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "DialogBtnScale"
    )

    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            lineHeight = 17.sp,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
fun ExpressiveVersionDialog(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    fromVersion: String?,
    toVersion: String?,
    confirmLabel: String?,
    dismissLabel: String,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    secondaryLabel: String? = null,
    secondaryIcon: ImageVector? = null,
    onSecondary: (() -> Unit)? = null,
    confirmIcon: ImageVector? = Icons.Outlined.Download
) {
    val haptics = rememberAppHaptics()

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
                DialogShapeBadge(icon, accent, alert = false)

                Spacer(Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (fromVersion != null && toVersion != null) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VersionChip(
                            text = fromVersion,
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = accent
                        )
                        VersionChip(
                            text = toVersion,
                            container = accent,
                            content = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    }
                }

                Spacer(Modifier.height(if (fromVersion != null) 18.dp else 12.dp))

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))

                if (onConfirm != null && confirmLabel != null) {
                    ExpressiveDialogButton(
                        label = confirmLabel,
                        icon = confirmIcon,
                        container = accent,
                        content = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        haptics.confirm()
                        onConfirm()
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (onSecondary != null && secondaryLabel != null) {
                    ExpressiveDialogButton(
                        label = secondaryLabel,
                        icon = secondaryIcon,
                        container = accent.copy(alpha = 0.16f),
                        content = accent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        haptics.confirm()
                        onSecondary()
                    }
                    Spacer(Modifier.height(10.dp))
                }

                ExpressiveDialogButton(
                    label = dismissLabel,
                    icon = null,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    haptics.tap()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun VersionChip(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

@Composable
fun ExpressiveDonationDialog(
    onSupport: () -> Unit,
    onSnooze30: () -> Unit,
    onLater: () -> Unit
) {
    val haptics = rememberAppHaptics()
    val accent = MaterialTheme.colorScheme.tertiary

    Dialog(onDismissRequest = onLater) {
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
                DialogShapeBadge(Icons.Outlined.LocalCafe, accent, alert = false)

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.donation_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.donation_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))

                ExpressiveDialogButton(
                    label = stringResource(R.string.donation_support),
                    icon = Icons.Outlined.LocalCafe,
                    container = accent,
                    content = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    haptics.confirm()
                    onSupport()
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveDialogButton(
                        label = stringResource(R.string.donation_dismiss_30),
                        icon = null,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        haptics.tap()
                        onSnooze30()
                    }
                    ExpressiveDialogButton(
                        label = stringResource(R.string.donation_later),
                        icon = null,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        haptics.tap()
                        onLater()
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveLongTextDialog(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String? = null,
    body: String,
    dismissLabel: String,
    onDismiss: () -> Unit
) {
    val haptics = rememberAppHaptics()
    val scroll = rememberScrollState()

    // Sfuma il bordo superiore/inferiore solo quando c'e' altro testo in quella
    // direzione, cosi' si capisce che il blocco e' scorrevole.
    val topFade by animateFloatAsState(
        targetValue = if (scroll.value > 4) 1f else 0f,
        animationSpec = tween(200),
        label = "licTopFade"
    )
    val bottomFade by animateFloatAsState(
        targetValue = if (scroll.value < scroll.maxValue - 4) 1f else 0f,
        animationSpec = tween(200),
        label = "licBottomFade"
    )
    val surface = MaterialTheme.colorScheme.surfaceContainerLow

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveHeroBadge(size = 52.dp, accent = accent) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Box {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .verticalScroll(scroll)
                            .padding(16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(24.dp)
                            .graphicsLayer { alpha = topFade }
                            .background(Brush.verticalGradient(listOf(surface, Color.Transparent)))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(24.dp)
                            .graphicsLayer { alpha = bottomFade }
                            .background(Brush.verticalGradient(listOf(Color.Transparent, surface)))
                    )
                }

                Spacer(Modifier.height(20.dp))

                ExpressiveDialogButton(
                    label = dismissLabel,
                    icon = null,
                    container = accent,
                    content = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    haptics.tap()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun ExpressiveInfoDialog(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit
) {
    val haptics = rememberAppHaptics()

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
                DialogShapeBadge(icon, accent, alert = false)

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

                ExpressiveDialogButton(
                    label = confirmLabel,
                    icon = null,
                    container = accent,
                    content = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    haptics.tap()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun ExpressiveAuthRequestDialog(
    peer: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    val haptics = rememberAppHaptics()
    val accent = MaterialTheme.colorScheme.tertiary

    Dialog(onDismissRequest = onDeny) {
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
                DialogShapeBadge(Icons.Outlined.PersonAdd, accent, alert = true)

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.auth_request_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.auth_request_body, peer),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = peer,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveDialogButton(
                        label = stringResource(R.string.auth_deny),
                        icon = Icons.Filled.Close,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        haptics.reject()
                        onDeny()
                    }
                    ExpressiveDialogButton(
                        label = stringResource(R.string.auth_allow),
                        icon = Icons.Filled.Check,
                        container = accent,
                        content = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.weight(1f)
                    ) {
                        haptics.confirm()
                        onAllow()
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveKeyRequestDialog(
    wrong: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    val haptics = rememberAppHaptics()
    val accent =
        if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    var keyText by remember { mutableStateOf("") }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (wrong) {
            haptics.reject()
            keyText = ""
            repeat(3) {
                shake.animateTo(12f, tween(60, easing = FastOutSlowInEasing))
                shake.animateTo(-12f, tween(60, easing = FastOutSlowInEasing))
            }
            shake.animateTo(0f, tween(60, easing = FastOutSlowInEasing))
        }
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            modifier = Modifier.graphicsLayer { translationX = shake.value }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DialogShapeBadge(Icons.Outlined.VpnKey, accent, alert = wrong)

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.key_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (wrong) stringResource(R.string.key_dialog_wrong)
                    else stringResource(R.string.key_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (wrong) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_item_auth_key_title)) },
                    leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        haptics.confirm()
                        onSubmit(keyText)
                    }),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveDialogButton(
                        label = stringResource(R.string.key_dialog_cancel),
                        icon = Icons.Filled.Close,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        haptics.tap()
                        onCancel()
                    }
                    ExpressiveDialogButton(
                        label = stringResource(R.string.key_dialog_connect),
                        icon = Icons.Filled.Check,
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.weight(1f)
                    ) {
                        haptics.confirm()
                        onSubmit(keyText)
                    }
                }
            }
        }
    }
}
