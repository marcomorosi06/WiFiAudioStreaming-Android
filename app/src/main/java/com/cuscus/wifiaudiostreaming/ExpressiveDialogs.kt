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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1
        )
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
