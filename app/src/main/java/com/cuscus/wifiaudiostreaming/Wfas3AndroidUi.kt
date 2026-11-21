/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
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

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.wifiaudiostreaming

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.wfas3.W3
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Beacon
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3ClientState
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3PairInfo
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Module: WFAS v3 screens (Android)
 *
 * Everything the person sees of WFAS v3, drawn with the expressive kit and
 * laid out the way WiFi Screen Streaming does it:
 *  - server: who can connect, how the stream is sent, the pairing card (an
 *    offer, then the QR or the PIN on screen, then the result), and while
 *    streaming the clients with rings that spread while somebody listens;
 *  - client: the paired servers as cards with their own shape, one large
 *    button to pair a new one, and the pairing itself in one dialog whose
 *    shape follows each step (method, waiting, codes to compare, result);
 *  - every question (a request to pair, a v2 warning, a notice) in the same
 *    dialog: No never waits, yes is ready after a moment on screen.
 * Codes, PINs and QR codes are kept out of screenshots and of the recents
 * preview while they are shown.
 */

/** Screen state of the v3 pairing on the client side, kept across the scanner round trip. */
object Wfas3UiState {
    val clientPairOpen = MutableStateFlow(false)
    val replace = MutableStateFlow<Wfas3PairInfo?>(null)
    val scanning = MutableStateFlow(false)
    val scannedCode = MutableStateFlow<String?>(null)
    val noCamera = MutableStateFlow(false)
    /** A server just paired from the dialog: the list of servers connects to it by itself. */
    val connectRequest = MutableStateFlow<Int?>(null)

    fun openClientPair(replace: Wfas3PairInfo? = null) {
        this.replace.value = replace
        scannedCode.value = null
        noCamera.value = false
        connectRequest.value = null
        Wfas3Android.resetPairing()
        clientPairOpen.value = true
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** FLAG_SECURE on the activity window while shown (codes on the screen itself, the scanner). */
@Composable
private fun SecureActivityWindow() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

// -- states ------------------------------------------------------------------------------

/** The shape, icon and colour of every v3 moment. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object W3Look {
    val Method = KitStatus("w3-method", MaterialShapes.Clover8Leaf, Icons.Rounded.Key, Hue.Violet, Motif.Still)
    val Waiting = KitStatus("w3-wait", MaterialShapes.Clover4Leaf, Icons.Rounded.HourglassTop, Hue.Amber, Motif.Spin)
    val Compare = KitStatus("w3-compare", MaterialShapes.Clover8Leaf, Icons.Rounded.Password, Hue.Violet, Motif.Breathe)
    val Done = KitStatus("w3-done", MaterialShapes.Cookie12Sided, Icons.Rounded.CheckCircle, Hue.Green, Motif.Breathe)
    val Failed = KitStatus("w3-failed", MaterialShapes.Boom, Icons.Rounded.ErrorOutline, Hue.Red, Motif.Shake)
    val Asked = KitStatus("w3-asked", MaterialShapes.Gem, Icons.Rounded.HowToReg, Hue.Amber, Motif.Breathe)
    val Legacy = KitStatus("w3-legacy", MaterialShapes.Pentagon, Icons.Rounded.History, Hue.Pink, Motif.Still)
    val Warning = KitStatus("w3-warning", MaterialShapes.Pentagon, Icons.Rounded.GppMaybe, Hue.Pink, Motif.Still)
    val Gone = KitStatus("w3-gone", MaterialShapes.Ghostish, Icons.Rounded.LinkOff, Hue.Orange, Motif.Still)
    val Info = KitStatus("w3-info", MaterialShapes.Cookie9Sided, Icons.Rounded.Info, Hue.Primary, Motif.Still)
    val Erase = KitStatus("w3-erase", MaterialShapes.Burst, Icons.Rounded.DeleteForever, Hue.Red, Motif.Still)
}

// -- small pieces ----------------------------------------------------------------------------

/** The little "v2" mark shown in front of a server that still uses the previous protocol. */
@Composable
fun V2Badge() {
    val tone = toneOf(Hue.Pink)
    val desc = stringResource(R.string.w3_legacy_badge_desc)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tone.container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(Icons.Rounded.History, contentDescription = desc, modifier = Modifier.size(12.dp), tint = tone.onContainer)
        Text(stringResource(R.string.w3_legacy_badge), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = tone.onContainer)
    }
}

/** A device's badge: its own shape (the same every time), its initial, and a turn while it is live. */
@Composable
private fun DeviceBadge(name: String, live: Boolean, size: Int = 54) {
    val shape = remember(name) { PolygonShape(signatureShape(name)) }
    val glow by animateFloatAsState(if (live) 1f else 0f, Motion.slowSpatial(), label = "glow")
    val back by animateColorAsState(
        if (live) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
        Motion.defaultEffects(),
        label = "badgeBack"
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer { rotationZ = glow * 20f }
            .background(back, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (live) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.graphicsLayer { rotationZ = -glow * 20f }
        )
    }
}

/** The marks of a pairing: guest, microphone, to be renewed. */
@Composable
private fun PairChips(p: Wfas3PairInfo) {
    if (!p.guest && !p.microphone && !p.suspect) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        if (p.suspect) KitChip(stringResource(R.string.w3_badge_suspect), Icons.Rounded.GppMaybe, Hue.Red)
        if (p.guest) KitChip(stringResource(R.string.w3_badge_guest), Icons.Rounded.PersonOff, Hue.Neutral)
        if (p.microphone) KitChip(stringResource(R.string.w3_badge_mic), Icons.Rounded.Mic, Hue.Teal)
    }
}

/** A stepped value on a slider: the chosen value sits on a pill, each step is a tick. */
@Composable
private fun StepPicker(
    title: String,
    icon: ImageVector,
    values: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onPick: (Int) -> Unit
) {
    val touch = rememberAppHaptics()
    val all = remember(values, selected) { (values + selected).distinct().sorted() }
    var index by remember(all, selected) { mutableFloatStateOf(all.indexOf(selected).coerceAtLeast(0).toFloat()) }
    val shown = all[index.toInt().coerceIn(0, all.lastIndex)]
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val roll = Motion.fastSpatial<androidx.compose.ui.unit.IntOffset>()
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                AnimatedContent(
                    targetState = shown,
                    transitionSpec = {
                        (slideInVertically(roll) { it } + fadeIn(Motion.fastEffects())) togetherWith
                            (slideOutVertically(roll) { -it } + fadeOut(Motion.fastEffects())) using SizeTransform(clip = true)
                    },
                    label = "step"
                ) { v ->
                    Text(label(v), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }
        if (all.size > 1) {
            Slider(
                value = index,
                onValueChange = { v ->
                    if (v.toInt() != index.toInt()) touch.tick()
                    index = v
                },
                valueRange = 0f..all.lastIndex.toFloat(),
                steps = (all.size - 2).coerceAtLeast(0),
                onValueChangeFinished = {
                    touch.gestureEnd()
                    val v = all[index.toInt().coerceIn(0, all.lastIndex)]
                    if (v != selected) onPick(v)
                }
            )
        }
    }
}

// -- settings: protocol ------------------------------------------------------------------

/**
 * The rows of the protocol section of the settings: which generation, the
 * name shown when pairing, the paired devices, where the keys are, and the
 * reset. Each row is one slat of the connected group around it.
 */
@Composable
fun Wfas3ProtocolSettings(
    appSettings: AppSettings,
    onProtocol: (String) -> Unit,
    onDeviceName: (String) -> Unit,
) {
    val context = LocalContext.current
    val touch = rememberAppHaptics()
    val v3 = Wfas3Settings.isV3(appSettings)
    val pairs by Wfas3Android.pairs.collectAsState()
    val failure by Wfas3Android.failure.collectAsState()
    var askV2 by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Wfas3PairInfo?>(null) }

    LaunchedEffect(v3) { if (v3) withContext(Dispatchers.IO) { Wfas3Android.ensure(context, appSettings); Wfas3Android.refreshPairs() } }

    if (v3) {
        failure?.let {
            GroupRow(color = MaterialTheme.colorScheme.errorContainer) {
                InfoRow(Icons.Rounded.Warning, stringResource(R.string.w3u_trouble), Wfas3Android.failureText(context),
                    modifier = Modifier.padding(16.dp), container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError)
            }
        }
        NameRow(appSettings, onDeviceName)
        GroupRow {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Overline(stringResource(R.string.w3_paired_title))
                if (pairs.isEmpty()) {
                    Text(stringResource(R.string.w3_paired_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                pairs.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DeviceBadge(p.name, live = false, size = 40)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(p.name.ifBlank { "?" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            PairChips(p)
                        }
                        IconButton(onClick = { touch.tap(); removing = p }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w3_paired_remove) + " " + p.name)
                        }
                    }
                }
            }
        }
    }

    // The rest is for whoever wants it: folded, one slat that opens.
    var more by rememberSaveable { mutableStateOf(!v3) }
    FoldRow(open = more, onToggle = { more = it }, summary = stringResource(if (v3) R.string.w3u_adv_summary_v3 else R.string.w3u_adv_summary_v2))
    AnimatedVisibility(
        visible = more,
        enter = expandVertically(Motion.defaultSpatial()) + fadeIn(Motion.defaultEffects()),
        exit = shrinkVertically(Motion.defaultSpatial()) + fadeOut(Motion.fastEffects())
    ) {
        Column {
            GroupRow {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow(
                        Icons.Rounded.VerifiedUser,
                        stringResource(R.string.w3u_generation_title),
                        stringResource(R.string.w3u_generation_desc),
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChoiceList(
                        options = listOf(
                            Option(Wfas3Settings.PROTOCOL_V3, stringResource(R.string.w3_v3_title), Icons.Rounded.VerifiedUser, stringResource(R.string.w3_v3_desc)),
                            Option(Wfas3Settings.PROTOCOL_V2, stringResource(R.string.w3_v2_title), Icons.Rounded.History, stringResource(R.string.w3_v2_desc), risky = true)
                        ),
                        selected = if (v3) Wfas3Settings.PROTOCOL_V3 else Wfas3Settings.PROTOCOL_V2,
                        onChange = { p -> if (p == Wfas3Settings.PROTOCOL_V2 && v3) askV2 = true else if (p != (if (v3) Wfas3Settings.PROTOCOL_V3 else Wfas3Settings.PROTOCOL_V2)) onProtocol(p) }
                    )
                }
            }
            if (v3) {
                GroupRow {
                    InfoRow(
                        Icons.Rounded.Lock,
                        stringResource(R.string.w3u_keys_title),
                        stringResource(R.string.w3_keys_where, stringResource(R.string.w3_keys_android)),
                        modifier = Modifier.padding(16.dp),
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EraseRow(onErase = { Wfas3Android.bg { Wfas3Android.eraseAll(context) } })
            }
        }
    }

    if (askV2) {
        AskDialog(
            status = W3Look.Legacy,
            title = stringResource(R.string.w3_v2_enable_title),
            detail = null,
            onDismiss = { askV2 = false }
        ) {
            Text(stringResource(R.string.w3_v2_enable_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnswerRow(
                key = "v2",
                no = stringResource(R.string.w3_v2_enable_stay),
                yes = stringResource(R.string.w3_v2_enable_confirm),
                yesColor = MaterialTheme.colorScheme.error,
                onYesColor = MaterialTheme.colorScheme.onError,
                onNo = { touch.tap(); askV2 = false },
                onYes = { touch.reject(); askV2 = false; onProtocol(Wfas3Settings.PROTOCOL_V2) }
            )
        }
    }
    removing?.let { p -> RemoveDialog(p) { removing = null } }
}

/** A slat that folds the advanced rows under it: a chevron that turns, a short summary. */
@Composable
private fun FoldRow(open: Boolean, onToggle: (Boolean) -> Unit, summary: String) {
    val touch = rememberAppHaptics()
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.fastSpatial(), label = "foldTurn")
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { touch.toggle(!open); onToggle(!open) },
        shape = RoundedCornerShape(SETTINGS_SLAT_CORNER),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SETTINGS_SLAT_GAP / 2)
    ) {
        InfoRow(
            Icons.Rounded.Tune,
            stringResource(R.string.w3u_adv_title),
            summary,
            modifier = Modifier.padding(16.dp),
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            end = {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
        )
    }
}

/** The name shown when pairing: kept when the keyboard closes or the field is left. */
@Composable
private fun NameRow(appSettings: AppSettings, onDeviceName: (String) -> Unit) {
    var text by remember(appSettings.v3DeviceName) { mutableStateOf(appSettings.v3DeviceName) }
    var hadFocus by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val touch = rememberAppHaptics()
    fun commit() { if (text.trim() != appSettings.v3DeviceName) onDeviceName(text.trim()) }
    GroupRow {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow(
                Icons.Rounded.Badge,
                stringResource(R.string.w3_device_name),
                stringResource(R.string.w3_device_name_hint, Wfas3Settings.deviceName(appSettings.copy(v3DeviceName = ""))),
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(32) },
                singleLine = true,
                placeholder = { Text(Wfas3Settings.deviceName(appSettings.copy(v3DeviceName = ""))) },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { touch.confirm(); commit(); focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { s ->
                        if (hadFocus && !s.isFocused) commit()
                        hadFocus = s.isFocused
                    }
            )
        }
    }
}

/** Erasing everything of v3: a row that opens a confirmation, whose yes waits a moment. */
@Composable
private fun EraseRow(onErase: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val touch = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val back by animateColorAsState(if (confirm) scheme.errorContainer else scheme.surfaceContainerHigh, Motion.defaultEffects(), label = "eraseBack")
    Surface(
        onClick = { touch.tap(); confirm = !confirm },
        shape = RoundedCornerShape(SETTINGS_SLAT_CORNER),
        color = back,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SETTINGS_SLAT_GAP / 2)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow(
                Icons.Rounded.DeleteForever,
                stringResource(R.string.w3_reset),
                stringResource(R.string.w3_reset_desc),
                container = scheme.error,
                content = scheme.onError,
                end = {
                    val turn by animateFloatAsState(if (confirm) 90f else 0f, Motion.fastSpatial(), label = "eraseChevron")
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = turn })
                }
            )
            AnimatedVisibility(
                visible = confirm,
                enter = expandVertically(Motion.defaultSpatial()) + fadeIn(),
                exit = shrinkVertically(Motion.defaultSpatial()) + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.w3_reset_body), style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer)
                    AnswerRow(
                        key = confirm,
                        no = stringResource(R.string.w3_cancel),
                        yes = stringResource(R.string.w3_reset_confirm),
                        yesColor = scheme.error,
                        onYesColor = scheme.onError,
                        onNo = { touch.tap(); confirm = false },
                        onYes = { touch.failure(); confirm = false; onErase() }
                    )
                }
            }
        }
    }
}

/** Removing one pairing: what it means, No first. */
@Composable
private fun RemoveDialog(p: Wfas3PairInfo, onClose: () -> Unit) {
    val touch = rememberAppHaptics()
    AskDialog(
        status = W3Look.Erase,
        title = stringResource(R.string.w3_paired_remove_title, p.name.ifBlank { "?" }),
        detail = stringResource(R.string.w3_paired_remove_body),
        onDismiss = onClose
    ) {
        AnswerRow(
            key = p.slot,
            no = stringResource(R.string.w3_cancel),
            yes = stringResource(R.string.w3_paired_remove),
            yesColor = MaterialTheme.colorScheme.error,
            onYesColor = MaterialTheme.colorScheme.onError,
            onNo = { touch.tap(); onClose() },
            onYes = { touch.reject(); onClose(); Wfas3Android.bg { Wfas3Android.removePair(p.slot) } }
        )
    }
}

// -- server -----------------------------------------------------------------------------------

/**
 * The server side of v3, before and while streaming: the clients (while
 * streaming), the pairing card, who can connect and how the stream is sent.
 * Changes apply live; synchronisation and multicast make the clients
 * reconnect by themselves.
 */
@Composable
fun Wfas3ServerCard(
    appSettings: AppSettings,
    isMulticast: Boolean,
    isStreaming: Boolean,
    onChange: (codec: String, opusKbps: Int, opusFrame: Int, latencyMs: Int, sync: Boolean, acceptNew: Boolean, pairAsk: Boolean) -> Unit,
    onMulticastChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val a = appSettings
    fun change(codec: String = a.v3Codec, kbps: Int = a.v3OpusKbps, frame: Int = a.v3OpusFrameSamples, lat: Int = a.v3LatencyMs,
               sync: Boolean = a.v3Sync, acceptNew: Boolean = a.v3AcceptNew, ask: Boolean = a.v3PairAsk) =
        onChange(codec, kbps, frame, lat, sync, acceptNew, ask)

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Wfas3Android.ensure(context, appSettings); Wfas3Android.refreshPairs() } }

    // Plug and play first: who listens, pairing, and one tap for how it sounds.
    // Everything else is one fold away, for whoever wants to tune it.
    val preset = presetOf(a)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (isStreaming) ClientsCard()
        PairingCard(a)
        SoundPresets(preset) { pr ->
            change(codec = pr.codec, kbps = pr.kbps ?: a.v3OpusKbps, frame = pr.frame ?: a.v3OpusFrameSamples, lat = pr.latencyMs, sync = pr.sync)
        }
        // The latency is the setting people reach for most: in view, not in the fold.
        LatencyPanel(a.v3LatencyMs) { change(lat = it) }
        // Chosen before starting: a running session keeps the way it began.
        if (!isStreaming) DeliveryPicker(
            shared = isMulticast,
            lockedBy = ProtocolStatus.names(
                wfas = false,
                rtp = a.rtpEnabled,
                http = a.httpEnabled,
                dlna = a.dlnaEnabled,
                snapcast = a.snapcastEnabled
            ),
            onChange = onMulticastChange
        )
        if (isStreaming) {
            val all = isMulticast || a.rtpEnabled || a.httpEnabled || a.dlnaEnabled || a.snapcastEnabled
            NotePanel(
                stringResource(if (all) R.string.w3u_delivery_live_all else R.string.w3u_delivery_live_each),
                if (all) Icons.Rounded.Groups else Icons.Rounded.Person
            )
            NotePanel(stringResource(R.string.w3_change_live), Icons.Rounded.Sync)
        }
        if (!isStreaming) PairedQuickList()
        Disclosure(
            key = "w3.server.more",
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.w3u_more_title),
            detail = streamSummary(a),
            badge = if (preset == null) stringResource(R.string.w3u_preset_custom) else null
        ) {
            StreamPanel(a, isMulticast) { codec, kbps, frame, lat, sync -> change(codec = codec, kbps = kbps, frame = frame, lat = lat, sync = sync) }
            WhoCanConnect(a) { ask, acceptNew -> change(ask = ask, acceptNew = acceptNew) }
        }
    }
}

/**
 * The ready-made ways to stream. Each one sets codec, bitrate, packet size,
 * latency and synchronisation together; values set by hand that match none
 * of them show as Custom in the fold below.
 */
private enum class SoundPreset(
    val title: Int,
    val detail: Int,
    val icon: ImageVector,
    val hue: Hue,
    val codec: String,
    /** Null: not used by this codec, the stored value is kept. */
    val kbps: Int?,
    val frame: Int?,
    val latencyMs: Int,
    val sync: Boolean
) {
    HiFi(R.string.w3u_preset_hifi, R.string.w3u_preset_hifi_desc, Icons.Rounded.GraphicEq, Hue.Violet, Wfas3Settings.CODEC_PCM, null, null, 60, false),
    Balanced(R.string.w3u_preset_balanced, R.string.w3u_preset_balanced_desc, Icons.Rounded.Wifi, Hue.Teal, Wfas3Settings.CODEC_OPUS, 256, 480, 60, false),
    Quick(R.string.w3u_preset_quick, R.string.w3u_preset_quick_desc, Icons.Rounded.Bolt, Hue.Amber, Wfas3Settings.CODEC_OPUS, 160, 120, 40, false),
    Together(R.string.w3u_preset_together, R.string.w3u_preset_together_desc, Icons.Rounded.Groups, Hue.Pink, Wfas3Settings.CODEC_OPUS, 256, 480, 120, true);
}

private fun presetOf(a: AppSettings): SoundPreset? = SoundPreset.entries.firstOrNull { p ->
    a.v3Codec == p.codec && a.v3LatencyMs == p.latencyMs && a.v3Sync == p.sync &&
        (p.kbps == null || a.v3OpusKbps == p.kbps) && (p.frame == null || a.v3OpusFrameSamples == p.frame)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun presetShape(p: SoundPreset) = when (p) {
    SoundPreset.HiFi -> MaterialShapes.Flower
    SoundPreset.Balanced -> MaterialShapes.Cookie9Sided
    SoundPreset.Quick -> MaterialShapes.Burst
    SoundPreset.Together -> MaterialShapes.Clover4Leaf
}

/** One line of what the stream is now, for the folded header. */
@Composable
private fun streamSummary(a: AppSettings): String {
    val codec = if (a.v3Codec == Wfas3Settings.CODEC_OPUS) stringResource(R.string.w3u_summary_opus, a.v3OpusKbps) else stringResource(R.string.w3_codec_pcm)
    val sync = if (a.v3Sync) stringResource(R.string.w3u_summary_sync) else null
    return listOfNotNull(codec, sync).joinToString(" \u00B7 ")
}

/** Four tiles, two by two: one tap and the stream sounds that way. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SoundPresets(selected: SoundPreset?, onPick: (SoundPreset) -> Unit) {
    Panel {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline(stringResource(R.string.w3u_presets_title), Modifier.padding(start = 6.dp, top = 4.dp))
            SoundPreset.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { pr ->
                        PresetTile(
                            icon = pr.icon,
                            title = stringResource(pr.title),
                            detail = stringResource(pr.detail),
                            hue = pr.hue,
                            shape = presetShape(pr),
                            selected = pr == selected,
                            onClick = { onPick(pr) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Who is listening, with rings that spread from the middle while somebody does. */
@Composable
private fun ClientsCard() {
    val peers by Wfas3Android.peers.collectAsState()
    val listening = peers.count { it.streaming }
    val tone = toneOf(if (listening > 0) Hue.Green else Hue.Teal)
    val rise = Motion.defaultSpatial<androidx.compose.ui.unit.IntOffset>()
    Panel(color = tone.container) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Beacon(tone, active = listening > 0)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    AnimatedContent(
                        targetState = peers.size to listening,
                        transitionSpec = {
                            (slideInVertically(rise) { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically(rise) { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
                        },
                        label = "clients"
                    ) { (count, live) ->
                        Column {
                            Text(
                                when {
                                    count == 0 -> stringResource(R.string.w3_clients_none)
                                    else -> stringResource(R.string.w3u_clients_count, count, live)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = tone.onContainer
                            )
                            Text(
                                stringResource(if (count == 0) R.string.w3u_clients_none_detail else R.string.w3u_clients_detail, 8),
                                style = MaterialTheme.typography.bodySmall,
                                color = tone.onContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            peers.forEachIndexed { index, p -> key(p.session) { PeerRow(p, index, tone) } }
        }
    }
}

@Composable
private fun PeerRow(p: Wfas3Peer, index: Int, tone: Tone) {
    val entrance = remember { Animatable(0f) }
    val arrive = Motion.defaultSpatial<Float>()
    LaunchedEffect(p.session) {
        delay(40L * index.coerceAtMost(8))
        entrance.animateTo(1f, arrive)
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrance.value.coerceIn(0f, 1f)
                translationY = (1f - entrance.value) * 40f
            }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceBadge(p.name, live = p.streaming, size = 42)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(p.name.ifBlank { "?" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    KitChip(
                        stringResource(if (p.streaming) R.string.w3_client_streaming else R.string.w3_client_waiting),
                        if (p.streaming) Icons.Rounded.GraphicEq else Icons.Rounded.HourglassTop,
                        if (p.streaming) Hue.Green else Hue.Amber
                    )
                    if (p.synchronised) KitChip(stringResource(R.string.w3_client_sync), Icons.Rounded.Sync, Hue.Violet)
                    if (p.multicast) KitChip(stringResource(R.string.w3_client_multicast), Icons.Rounded.Groups, Hue.Teal)
                    if (p.microphone) KitChip(stringResource(R.string.w3_client_mic), Icons.Rounded.Mic, Hue.Pink)
                }
            }
            if (p.streaming) LiveDot(tone.accent)
        }
    }
}

/** A shape in the middle and rings that leave it: serving. Rings only while somebody listens. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Beacon(tone: Tone, active: Boolean) {
    val ambient = Motion.ambient()
    val waves = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(active, ambient) {
        if (!active || ambient < 1f) {
            waves.forEach { it.snapTo(0f) }
            return@LaunchedEffect
        }
        waves.forEachIndexed { index, wave ->
            launch {
                delay(500L * index)
                while (true) {
                    wave.snapTo(0f)
                    wave.animateTo(1f, tween(1500, easing = LinearEasing))
                }
            }
        }
    }
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        waves.forEach { wave ->
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        val v = wave.value
                        val grow = 0.45f + 0.55f * v
                        scaleX = grow
                        scaleY = grow
                        alpha = if (v == 0f) 0f else (1f - v) * 0.7f
                    }
                    .border(2.dp, tone.accent, CircleShape)
            )
        }
        ShapeIcon(
            if (active) Icons.Rounded.GraphicEq else Icons.Rounded.Sensors,
            tone.accent,
            tone.container,
            shape = if (active) MaterialShapes.Flower else MaterialShapes.VerySunny,
            size = 48.dp,
            iconSize = 24.dp
        )
    }
}

private enum class PairStage { None, Opening, Code, Pin, Paired }

/**
 * Pairing, in steps that replace each other: the offer, then the QR and the
 * code or the PIN while the window is open, then who paired. The codes are
 * secrets for two minutes: while they are on screen the window is hidden
 * from screenshots and from the recents preview.
 */
@Composable
private fun PairingCard(a: AppSettings) {
    val context = LocalContext.current
    val touch = rememberAppHaptics()
    val window by Wfas3Android.window.collectAsState()
    val fails by Wfas3Android.windowFailures.collectAsState()
    var openFailed by remember { mutableStateOf(false) }
    val w = window
    val stage = when {
        w == null -> PairStage.None
        w.paired != null -> PairStage.Paired
        w.text.isEmpty() -> PairStage.Opening
        w.kind == W3.PAIR_PIN -> PairStage.Pin
        else -> PairStage.Code
    }
    if (stage == PairStage.Code || stage == PairStage.Pin) SecureActivityWindow()
    LaunchedEffect(stage) {
        if (stage == PairStage.Paired) {
            touch.success()
            // The result stays a moment, then the card goes back to its offer by itself.
            delay(4000)
            Wfas3Android.bg { Wfas3Android.windowDismissed() }
        }
    }

    val violet = toneOf(Hue.Violet)
    val green = toneOf(Hue.Green)
    val pop = Motion.defaultSpatial<Float>()
    val back by animateColorAsState(
        when (stage) {
            PairStage.None -> MaterialTheme.colorScheme.surfaceContainerLow
            PairStage.Paired -> green.container
            else -> violet.container
        },
        Motion.defaultEffects(),
        label = "pairBack"
    )
    Panel(color = back) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(Motion.defaultEffects()) + scaleIn(pop, initialScale = 0.92f)) togetherWith
                    fadeOut(Motion.fastEffects()) using SizeTransform(clip = false)
            },
            label = "pairing"
        ) { shown ->
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (shown) {
                    PairStage.None -> {
                        InfoRow(Icons.Rounded.Key, stringResource(R.string.w3u_pair_offer_title), stringResource(R.string.w3u_pair_offer_desc))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigButton(
                                text = stringResource(R.string.w3_pair_code_button),
                                icon = Icons.Rounded.QrCode2,
                                onClick = {
                                    touch.confirm()
                                    Wfas3Android.bg { openFailed = !Wfas3Android.openWindow(context, W3.PAIR_CODE, a.v3PairAsk) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            BigButton(
                                text = stringResource(R.string.w3_pair_pin_button),
                                icon = Icons.Rounded.Dialpad,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = {
                                    touch.confirm()
                                    // With a PIN the two users compare codes: the server always asks.
                                    Wfas3Android.bg { openFailed = !Wfas3Android.openWindow(context, W3.PAIR_PIN, true) }
                                },
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                        if (openFailed) NotePanel(Wfas3Android.failureText(context), Icons.Rounded.Warning, Hue.Red)
                    }
                    PairStage.Opening -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(modifier = Modifier.size(40.dp), color = violet.accent)
                            Spacer(Modifier.width(14.dp))
                            Text(stringResource(R.string.w3u_pair_opening), style = MaterialTheme.typography.titleMedium, color = violet.onContainer)
                        }
                    }
                    PairStage.Code -> {
                        val ww = w ?: return@Column
                        Overline(stringResource(R.string.w3u_pair_scan_this), color = violet.onContainer)
                        ww.qr?.let { qr ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(12.dp)) {
                                    QrCodeCanvas(
                                        content = qr,
                                        modifier = Modifier.size(220.dp),
                                        shapes = emptyList(),
                                        palette = listOf(Color.Black),
                                        ringColor = Color.Black,
                                        eyeColor = Color.Black,
                                        plateColor = Color.White,
                                        plain = true,
                                        animated = false,
                                        quietZoneModules = 2
                                    )
                                }
                            }
                        }
                        Text(stringResource(R.string.w3u_pair_or_type), style = MaterialTheme.typography.bodySmall, color = violet.onContainer.copy(alpha = 0.85f))
                        Surface(shape = RoundedCornerShape(16.dp), color = violet.accent.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                            Text(ww.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = violet.onContainer,
                                style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
                        }
                        WindowFooter(ww, fails, violet)
                    }
                    PairStage.Pin -> {
                        val ww = w ?: return@Column
                        Overline(stringResource(R.string.w3u_pair_type_pin, Wfas3Settings.deviceName(Wfas3Android.appSettings)), color = violet.onContainer)
                        CodeTiles(ww.text, 4, violet.accent, violet.container, Modifier.fillMaxWidth())
                        Text(stringResource(R.string.w3u_pair_pin_compare), style = MaterialTheme.typography.bodySmall, color = violet.onContainer.copy(alpha = 0.85f))
                        WindowFooter(ww, fails, violet)
                    }
                    PairStage.Paired -> {
                        val ww = w ?: return@Column
                        InfoRow(Icons.Rounded.CheckCircle, stringResource(R.string.w3_window_paired, ww.paired ?: ""), stringResource(R.string.w3u_pair_paired_desc),
                            container = green.accent, content = green.container)
                        BigButton(
                            text = stringResource(R.string.w3_close),
                            icon = Icons.Rounded.Close,
                            container = green.accent,
                            content = green.container,
                            onClick = { touch.tap(); Wfas3Android.bg { Wfas3Android.windowDismissed() } }
                        )
                    }
                }
            }
        }
    }
}

/** The time left, the failed attempts, the privacy line and Close, under an open window. */
@Composable
private fun WindowFooter(w: Wfas3Android.WindowState, fails: Int, tone: Tone) {
    val touch = rememberAppHaptics()
    Countdown(
        closesAtMs = w.openedAtMs + 120_000L,
        totalMs = 120_000L,
        tone = tone,
        label = { s -> if (s > 0) stringResource(R.string.w3_window_expires, s.toInt()) else stringResource(R.string.w3_window_expired) }
    )
    if (fails > 0) NotePanel(stringResource(R.string.w3_window_failures, fails), Icons.Rounded.Warning, Hue.Red)
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = tone.onContainer, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.w3_window_private), style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.85f))
    }
    TextButton(onClick = { touch.tap(); Wfas3Android.bg { Wfas3Android.closeWindow() } }) {
        Icon(Icons.Rounded.Close, contentDescription = null, tint = tone.onContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.w3u_pair_close), color = tone.onContainer)
    }
}

/** Ask before pairing, and whether new connections are taken while streaming: two tiles. */
@Composable
private fun WhoCanConnect(a: AppSettings, onChange: (ask: Boolean, acceptNew: Boolean) -> Unit) {
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline(stringResource(R.string.w3u_who_title), Modifier.padding(start = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleTile(
                    icon = Icons.Rounded.HowToReg,
                    title = stringResource(R.string.w3u_ask_tile),
                    detail = stringResource(if (a.v3PairAsk) R.string.w3u_ask_tile_on else R.string.w3u_ask_tile_off),
                    checked = a.v3PairAsk,
                    onChange = { onChange(it, a.v3AcceptNew) },
                    modifier = Modifier.weight(1f)
                )
                ToggleTile(
                    icon = Icons.Rounded.PersonAdd,
                    title = stringResource(R.string.w3u_new_tile),
                    detail = stringResource(if (a.v3AcceptNew) R.string.w3u_new_tile_on else R.string.w3u_new_tile_off),
                    checked = a.v3AcceptNew,
                    onChange = { onChange(a.v3PairAsk, it) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(stringResource(R.string.w3u_who_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** How the stream is sent: compression, its bitrate and packets, latency, synchronisation. */
@Composable
private fun StreamPanel(
    a: AppSettings,
    isMulticast: Boolean,
    onChange: (codec: String, kbps: Int, frame: Int, lat: Int, sync: Boolean) -> Unit
) {
    val opus = a.v3Codec == Wfas3Settings.CODEC_OPUS
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline(stringResource(R.string.w3_stream_section), Modifier.padding(start = 4.dp))
            SegmentedChoice(
                options = listOf(
                    Option(Wfas3Settings.CODEC_PCM, stringResource(R.string.w3_codec_pcm), Icons.Rounded.GraphicEq),
                    Option(Wfas3Settings.CODEC_OPUS, stringResource(R.string.w3_codec_opus), Icons.Rounded.Compress)
                ),
                selected = if (opus) Wfas3Settings.CODEC_OPUS else Wfas3Settings.CODEC_PCM,
                onChange = { onChange(it, a.v3OpusKbps, a.v3OpusFrameSamples, a.v3LatencyMs, a.v3Sync) }
            )
            val rate = a.sampleRate.takeIf { it in Wfas3Settings.PCM_RATES } ?: 48000
            val ch = if (a.channelConfig == "STEREO") 2 else 1
            val hint = if (opus) stringResource(R.string.w3_codec_opus_desc)
            else stringResource(R.string.w3_codec_pcm_desc, rate, rate * ch * 16 / 1000)
            AnimatedContent(
                targetState = hint,
                transitionSpec = { fadeIn(Motion.defaultEffects()) togetherWith fadeOut(Motion.fastEffects()) using SizeTransform(clip = false) },
                label = "codecHint"
            ) { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            AnimatedVisibility(
                visible = opus,
                enter = expandVertically(Motion.defaultSpatial()) + fadeIn(),
                exit = shrinkVertically(Motion.defaultSpatial()) + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepPicker(
                        title = stringResource(R.string.w3_opus_bitrate),
                        icon = Icons.Rounded.GraphicEq,
                        values = Wfas3Settings.OPUS_KBPS_STEPS,
                        selected = a.v3OpusKbps,
                        label = { stringResource(R.string.w3_opus_bitrate_value, it) },
                        onPick = { onChange(a.v3Codec, it, a.v3OpusFrameSamples, a.v3LatencyMs, a.v3Sync) }
                    )
                    Text(stringResource(R.string.w3_opus_frame), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp))
                    SegmentedChoice(
                        options = Wfas3Settings.OPUS_FRAMES.map { f ->
                            Option(f, if (f % 48 == 0) "${f / 48}" else "%.1f".format(f / 48.0))
                        },
                        selected = a.v3OpusFrameSamples,
                        onChange = { onChange(a.v3Codec, a.v3OpusKbps, it, a.v3LatencyMs, a.v3Sync) }
                    )
                    Text(stringResource(R.string.w3u_frame_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            ToggleTile(
                icon = Icons.Rounded.Sync,
                title = stringResource(R.string.w3_sync),
                detail = stringResource(R.string.w3_sync_desc),
                checked = a.v3Sync,
                onChange = { onChange(a.v3Codec, a.v3OpusKbps, a.v3OpusFrameSamples, a.v3LatencyMs, it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The latency, next to the presets: one slider, quicker on the left, steadier on the right. */
@Composable
private fun LatencyPanel(latencyMs: Int, onPick: (Int) -> Unit) {
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            StepPicker(
                title = stringResource(R.string.w3_latency),
                icon = Icons.Rounded.Timer,
                values = Wfas3Settings.LATENCIES,
                selected = latencyMs,
                label = { stringResource(R.string.w3_latency_value, it) },
                onPick = onPick
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(stringResource(R.string.w3u_latency_quick), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.w3u_latency_steady), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The devices paired with this server, at a glance, before it starts. */
@Composable
private fun PairedQuickList() {
    val pairs by Wfas3Android.pairs.collectAsState()
    if (pairs.isEmpty()) return
    var removing by remember { mutableStateOf<Wfas3PairInfo?>(null) }
    val touch = rememberAppHaptics()
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline(stringResource(R.string.w3_paired_title), Modifier.padding(start = 4.dp))
            pairs.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeviceBadge(p.name, live = false, size = 40)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name.ifBlank { "?" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        PairChips(p)
                    }
                    IconButton(onClick = { touch.tap(); removing = p }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w3_paired_remove) + " " + p.name)
                    }
                }
            }
        }
    }
    removing?.let { p -> RemoveDialog(p) { removing = null } }
}

// -- client -----------------------------------------------------------------------------------

/** The paired servers, as cards with their own shape; one large button pairs a new one. */
@Composable
fun Wfas3ServersSection(
    appSettings: AppSettings,
    onConnect: (Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val touch = rememberAppHaptics()
    val pairs by Wfas3Android.pairs.collectAsState()
    val state by Wfas3Android.clientState.collectAsState()
    val failure by Wfas3Android.failure.collectAsState()
    var removing by remember { mutableStateOf<Wfas3PairInfo?>(null) }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Wfas3Android.ensure(context, appSettings); Wfas3Android.refreshPairs() } }
    // A server paired a moment ago is played at once: pairing means wanting to listen.
    val request by Wfas3UiState.connectRequest.collectAsState()
    LaunchedEffect(request) {
        val slot = request ?: return@LaunchedEffect
        Wfas3UiState.connectRequest.value = null
        if (slot >= 0) onConnect(slot)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
            Overline(stringResource(R.string.w3_servers_title), Modifier.weight(1f))
            if (pairs.isNotEmpty()) Text("${pairs.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        failure?.let { NotePanel(Wfas3Android.failureText(context), Icons.Rounded.Warning, Hue.Red) }
        if (pairs.isEmpty()) EmptyServers()
        pairs.forEachIndexed { index, p ->
            key(p.slot) {
                ServerCard(
                    p = p,
                    state = state,
                    pairs = pairs,
                    index = index,
                    onConnect = { onConnect(p.slot) },
                    onDisconnect = onDisconnect,
                    onRemove = { removing = p }
                )
            }
        }
        BigButton(
            text = stringResource(R.string.w3_pair_server_button),
            icon = Icons.Rounded.PersonAdd,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { touch.confirm(); Wfas3UiState.openClientPair() }
        )
    }
    removing?.let { p -> RemoveDialog(p) { removing = null } }
}

/** Nothing paired yet: what to do, in three steps. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyServers() {
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.w3u_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Step(1, Icons.Rounded.QrCode2, stringResource(R.string.w3u_step1_title), stringResource(R.string.w3u_step1_desc))
            Step(2, Icons.Rounded.QrCodeScanner, stringResource(R.string.w3u_step2_title), stringResource(R.string.w3u_step2_desc))
            Step(3, Icons.Rounded.Password, stringResource(R.string.w3u_step3_title), stringResource(R.string.w3u_step3_desc))
        }
    }
}

/** One numbered step: the number sits on a cookie, the icon tells what it is about. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Step(number: Int, icon: ImageVector, title: String, detail: String) {
    val appear = remember { Animatable(0f) }
    val spring = Motion.defaultSpatial<Float>()
    LaunchedEffect(Unit) {
        delay(70L * number)
        appear.animateTo(1f, spring)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            alpha = appear.value.coerceIn(0f, 1f)
            translationX = (1f - appear.value) * 40f
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            ShapeIcon(icon, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialShapes.Cookie6Sided, size = 44.dp)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondary)
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A paired server: tap to listen; while it plays, its badge turns and a dot beats. */
@Composable
private fun ServerCard(
    p: Wfas3PairInfo,
    state: Wfas3ClientState,
    pairs: List<Wfas3PairInfo>,
    index: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val mine = when (state) {
        is Wfas3ClientState.Searching -> state.pair == p.slot
        is Wfas3ClientState.Connecting -> state.pair == p.slot
        is Wfas3ClientState.Connected -> state.pair == p.slot
        is Wfas3ClientState.Waiting -> state.pair == p.slot
        is Wfas3ClientState.Stopped -> state.pair == p.slot
        else -> false
    }
    val active = mine && state !is Wfas3ClientState.Stopped
    val live = mine && state is Wfas3ClientState.Connected

    val entrance = remember { Animatable(0f) }
    val arrive = Motion.defaultSpatial<Float>()
    LaunchedEffect(p.slot) {
        delay(40L * index.coerceAtMost(8))
        entrance.animateTo(1f, arrive)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val feel = rememberAppHaptics()
    LaunchedEffect(pressed) { if (pressed) feel.press() }
    val squeeze by rememberPressScale(interaction, 0.96f)
    val corner by animateFloatAsState(if (pressed) 40f else 28f, Motion.fastSpatial(), label = "corner")
    val scheme = MaterialTheme.colorScheme
    val back by animateColorAsState(
        when {
            p.suspect -> scheme.errorContainer
            live -> scheme.secondaryContainer
            active -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainer
        },
        Motion.defaultEffects(),
        label = "serverBack"
    )
    val nudge by animateFloatAsState(if (pressed) 6f else 0f, Motion.fastSpatial(), label = "nudge")

    Surface(
        onClick = {
            when {
                p.suspect -> { feel.attention(); Wfas3UiState.openClientPair(p) }
                active -> Unit
                else -> { feel.confirm(); onConnect() }
            }
        },
        interactionSource = interaction,
        shape = RoundedCornerShape(corner.dp),
        color = back,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
                alpha = entrance.value.coerceIn(0f, 1f)
                translationY = (1f - entrance.value) * 60f
            }
    ) {
        Column(Modifier.padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceBadge(p.name, live)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(p.name.ifBlank { "?" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val line = when {
                        p.suspect -> stringResource(R.string.w3u_server_suspect)
                        mine -> wfas3ClientStateText(context, state, pairs)
                        else -> stringResource(R.string.w3u_server_tap)
                    }
                    AnimatedContent(
                        targetState = line,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) using SizeTransform(clip = false) },
                        label = "serverLine"
                    ) { text ->
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PairChips(p)
                }
                if (live) {
                    LiveDot(scheme.primary)
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = { feel.tap(); onRemove() }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w3_paired_remove) + " " + p.name)
                }
                if (!active) {
                    Icon(
                        if (p.suspect) Icons.Rounded.Key else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { translationX = nudge }
                    )
                }
            }
            AnimatedVisibility(
                visible = active,
                enter = expandVertically(Motion.defaultSpatial()) + fadeIn(),
                exit = shrinkVertically(Motion.defaultSpatial()) + fadeOut()
            ) {
                BigButton(
                    text = stringResource(R.string.w3_disconnect),
                    icon = Icons.Rounded.LinkOff,
                    container = scheme.errorContainer,
                    content = scheme.onErrorContainer,
                    onClick = { feel.reject(); onDisconnect() },
                    modifier = Modifier.padding(top = 12.dp, end = 8.dp)
                )
            }
        }
    }
}

/** Before connecting to a server that still speaks v2: what it means, Cancel first. */
@Composable
fun V2ConnectWarningDialog(name: String, onCancel: () -> Unit, onConnect: () -> Unit) {
    val touch = rememberAppHaptics()
    AskDialog(
        status = W3Look.Legacy,
        title = stringResource(R.string.w3_v2_connect_title, name),
        detail = null,
        onDismiss = onCancel
    ) {
        Text(stringResource(R.string.w3_v2_connect_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnswerRow(
            key = name,
            no = stringResource(R.string.w3_v2_connect_cancel),
            yes = stringResource(R.string.w3_v2_connect_go),
            yesColor = MaterialTheme.colorScheme.tertiary,
            onYesColor = MaterialTheme.colorScheme.onTertiary,
            onNo = { touch.tap(); onCancel() },
            onYes = { touch.confirm(); onConnect() }
        )
    }
}

// -- dialogs and overlays, hosted once by the activity ------------------------------------------

/**
 * The requests the server receives, the client pairing dialog with its
 * scanner, and the notices. [onRequestScan] checks the camera and its
 * permission, then sets [Wfas3UiState.scanning]. The pairing window of the
 * server is drawn in its own card, on the home screen.
 */
@Composable
fun Wfas3Host(appSettings: AppSettings, onRequestScan: () -> Unit) {
    val asks by Wfas3Android.asks.collectAsState()
    val clientPairOpen by Wfas3UiState.clientPairOpen.collectAsState()
    val scanning by Wfas3UiState.scanning.collectAsState()
    val notices by Wfas3Android.notices.collectAsState()

    // One request at a time; the next one shows when this one is answered.
    asks.firstOrNull()?.let { a -> key(a.ask) { PairRequestDialog(a) } }
    if (scanning) {
        SecureActivityWindow()
        QrScannerScreen(
            onScanned = { raw ->
                Wfas3UiState.scannedCode.value = raw
                Wfas3UiState.scanning.value = false
            },
            onClose = { Wfas3UiState.scanning.value = false },
            accept = { raw -> raw.startsWith("WFAS3:") && Wfas3Android.codeValid(raw) }
        )
    } else if (clientPairOpen) {
        ClientPairDialog(appSettings, onRequestScan)
    }
    notices.firstOrNull()?.let { n -> key(n.id) { NoticeDialog(n) } }
}

/** A device asks the server to pair: who, the code to compare (PIN), guest or not. No first. */
@Composable
private fun PairRequestDialog(a: Wfas3Android.AskState) {
    val touch = rememberAppHaptics()
    var guest by remember(a.ask) { mutableStateOf(a.wantsGuest) }
    val violet = toneOf(Hue.Violet)
    LaunchedEffect(a.ask) { touch.attention() }
    AskDialog(
        status = W3Look.Asked,
        title = stringResource(R.string.w3_ask_title, a.name.ifBlank { "?" }),
        detail = stringResource(R.string.w3_ask_unknown),
        onDismiss = { touch.reject(); Wfas3Android.bg { Wfas3Android.answer(a, false, false) } },
        secure = a.pin,
        dismissable = false
    ) {
        if (a.pin) {
            Text(stringResource(R.string.w3_ask_compare), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            CodeTiles(a.code, 3, violet.accent, violet.container, Modifier.fillMaxWidth(), tile = 40.dp)
        }
        if (a.wantsGuest) NotePanel(stringResource(R.string.w3_ask_wants_guest), Icons.Rounded.PersonOff)
        ToggleTile(
            icon = Icons.Rounded.PersonOff,
            title = stringResource(R.string.w3_ask_as_guest),
            detail = stringResource(if (guest) R.string.w3u_guest_on else R.string.w3u_guest_off),
            checked = guest,
            onChange = { guest = it },
            modifier = Modifier.fillMaxWidth()
        )
        AnswerRow(
            key = a.ask,
            no = stringResource(R.string.w3_ask_no),
            yes = stringResource(R.string.w3_ask_yes),
            onNo = { touch.reject(); Wfas3Android.bg { Wfas3Android.answer(a, false, false) } },
            onYes = { touch.success(); Wfas3Android.bg { Wfas3Android.answer(a, true, guest) } }
        )
    }
}

/**
 * Pairing with a server, in one dialog whose shape follows the step: the
 * method (QR or code, or PIN), waiting for the server, the codes to compare,
 * the result. Secure: the code, the PIN and the comparison stay out of
 * screenshots.
 */
@Composable
private fun ClientPairDialog(appSettings: AppSettings, onRequestScan: () -> Unit) {
    val context = LocalContext.current
    val touch = rememberAppHaptics()
    val pairing by Wfas3Android.pairing.collectAsState()
    val replace by Wfas3UiState.replace.collectAsState()
    val slot = replace?.slot ?: -1
    var usePin by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Wfas3Beacon?>(null) }
    var mic by remember { mutableStateOf(appSettings.sendClientMicrophone) }
    var guest by remember { mutableStateOf(false) }
    val scanned by Wfas3UiState.scannedCode.collectAsState()

    val beacons by Wfas3Android.beacons.collectAsState()
    var started by remember { mutableStateOf(false) }

    fun close() {
        Wfas3Android.bg {
            if (pairing is Wfas3Android.ClientPairing.Waiting || pairing is Wfas3Android.ClientPairing.Compare) Wfas3Android.cancelPairing()
            Wfas3Android.stopPinSearch()
            Wfas3Android.resetPairing()
        }
        Wfas3UiState.scannedCode.value = null
        Wfas3UiState.clientPairOpen.value = false
    }

    fun go() {
        if (started) return
        started = true
        touch.confirm()
        val c = code; val b = chosen; val pn = pin; val pinMode = usePin; val g = guest; val m = mic
        Wfas3Android.bg {
            if (!pinMode) Wfas3Android.pairWithCode(context, c, g, m, slot)
            else if (b != null) Wfas3Android.pairWithPin(b, pn, g, m, slot)
        }
    }

    LaunchedEffect(usePin) {
        if (usePin) Wfas3Android.bg { Wfas3Android.startPinSearch(context) } else Wfas3Android.bg { Wfas3Android.stopPinSearch() }
    }
    val p = pairing
    LaunchedEffect(p) {
        when (p) {
            is Wfas3Android.ClientPairing.Idle -> started = false
            is Wfas3Android.ClientPairing.Compare -> touch.attention()
            is Wfas3Android.ClientPairing.Done -> {
                touch.success()
                // The result stays a moment; then the dialog closes and the server plays.
                delay(1800)
                if (p.slot >= 0) Wfas3UiState.connectRequest.value = p.slot
                close()
            }
            is Wfas3Android.ClientPairing.Failed -> touch.failure()
            else -> Unit
        }
    }
    // Automatic steps, so nothing has to be pressed twice:
    // a scanned QR starts the pairing at once;
    LaunchedEffect(scanned) {
        val s = scanned ?: return@LaunchedEffect
        usePin = false
        code = s
        if (Wfas3Android.codeValid(s) && pairing is Wfas3Android.ClientPairing.Idle) go()
    }
    // a typed or pasted code starts it as soon as it is complete and checks out;
    LaunchedEffect(code, usePin) {
        if (usePin || scanned != null || !Wfas3Android.codeValid(code)) return@LaunchedEffect
        delay(700)
        if (pairing is Wfas3Android.ClientPairing.Idle) go()
    }
    // the only server with a PIN window nearby is chosen by itself;
    LaunchedEffect(beacons, usePin) {
        if (usePin && chosen == null && beacons.size == 1) chosen = beacons.first()
        if (chosen != null && beacons.none { it.key == chosen?.key }) { chosen = null; pin = "" }
    }
    // and the eighth digit of the PIN starts it.
    LaunchedEffect(pin, chosen) {
        if (usePin && chosen != null && pin.length == 8 && pairing is Wfas3Android.ClientPairing.Idle) {
            delay(250)
            go()
        }
    }


    // The code checked out here: the server's user still has to say yes.
    var confirmed by remember { mutableStateOf<String?>(null) }
    val sentYes = p is Wfas3Android.ClientPairing.Compare && confirmed == p.code
    LaunchedEffect(p) { if (p !is Wfas3Android.ClientPairing.Compare) confirmed = null }

    val status = when (p) {
        is Wfas3Android.ClientPairing.Compare -> if (sentYes) W3Look.Waiting else W3Look.Compare
        is Wfas3Android.ClientPairing.Idle -> W3Look.Method
        is Wfas3Android.ClientPairing.Waiting -> W3Look.Waiting
        is Wfas3Android.ClientPairing.Compare -> W3Look.Compare
        is Wfas3Android.ClientPairing.Done -> W3Look.Done
        is Wfas3Android.ClientPairing.Failed -> W3Look.Failed
    }
    val title = when (p) {
        is Wfas3Android.ClientPairing.Idle -> stringResource(R.string.w3_cpair_title)
        is Wfas3Android.ClientPairing.Waiting -> stringResource(R.string.w3u_cpair_waiting_title)
        is Wfas3Android.ClientPairing.Compare -> stringResource(if (sentYes) R.string.w3u_cpair_confirm_server_title else R.string.w3u_cpair_compare_title)
        is Wfas3Android.ClientPairing.Done -> stringResource(R.string.w3u_cpair_done_title)
        is Wfas3Android.ClientPairing.Failed -> stringResource(R.string.w3u_cpair_failed_title)
    }
    val detail = when (p) {
        is Wfas3Android.ClientPairing.Idle -> replace?.let { stringResource(R.string.w3_cpair_replace, it.name) }
            ?: stringResource(R.string.w3u_cpair_idle_desc)
        is Wfas3Android.ClientPairing.Waiting -> stringResource(R.string.w3_cpair_waiting)
        is Wfas3Android.ClientPairing.Compare -> stringResource(if (sentYes) R.string.w3u_cpair_confirm_server_desc else R.string.w3_cpair_compare)
        is Wfas3Android.ClientPairing.Done -> if (p.guest) stringResource(R.string.w3_cpair_done_guest, p.name) else stringResource(R.string.w3_cpair_done, p.name)
        is Wfas3Android.ClientPairing.Failed -> if (p.noNetwork) Wfas3Android.failureText(context) else wfas3PairFailText(context, p.reason, p.race)
    }

    AskDialog(
        status = status,
        title = title,
        detail = detail,
        onDismiss = { close() },
        secure = true,
        dismissable = p is Wfas3Android.ClientPairing.Idle || p is Wfas3Android.ClientPairing.Done || p is Wfas3Android.ClientPairing.Failed
    ) {
        when (p) {
            is Wfas3Android.ClientPairing.Idle -> MethodBody(
                usePin = usePin, onUsePin = { usePin = it },
                code = code, onCode = { code = it },
                pin = pin, onPin = { pin = it },
                chosen = chosen, onChosen = { chosen = it; pin = "" },
                mic = mic, onMic = { mic = it },
                guest = guest, onGuest = { guest = it },
                onRequestScan = onRequestScan,
                onCancel = { touch.tap(); close() },
                onGo = { go() }
            )
            is Wfas3Android.ClientPairing.Waiting -> {
                LoadingIndicator(modifier = Modifier.size(56.dp), color = toneOf(Hue.Amber).accent)
                BigButton(
                    text = stringResource(R.string.w3_cancel),
                    icon = Icons.Rounded.Close,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { touch.tap(); close() }
                )
            }
            is Wfas3Android.ClientPairing.Compare -> {
                val violet = toneOf(Hue.Violet)
                CodeTiles(p.code, 3, violet.accent, violet.container, Modifier.fillMaxWidth(), tile = 40.dp)
                if (!sentYes) {
                    AnswerRow(
                        key = p.code,
                        no = stringResource(R.string.w3_cpair_different),
                        yes = stringResource(R.string.w3_cpair_same),
                        onNo = { touch.reject(); Wfas3Android.bg { Wfas3Android.confirmCode(false) } },
                        onYes = {
                            touch.success()
                            confirmed = p.code
                            Wfas3Android.bg { Wfas3Android.confirmCode(true) }
                        }
                    )
                } else {
                    // Waiting for the other side; a different code can still stop it here.
                    LoadingIndicator(modifier = Modifier.size(48.dp), color = violet.accent)
                    BigButton(
                        text = stringResource(R.string.w3u_cpair_abort),
                        icon = Icons.Rounded.Close,
                        container = MaterialTheme.colorScheme.error,
                        content = MaterialTheme.colorScheme.onError,
                        onClick = { touch.reject(); close() }
                    )
                }
            }
            is Wfas3Android.ClientPairing.Done -> {
                val green = toneOf(Hue.Green)
                BigButton(
                    text = stringResource(R.string.w3u_cpair_done_button),
                    icon = Icons.Rounded.CheckCircle,
                    container = green.accent,
                    content = green.container,
                    onClick = {
                        touch.tap()
                        if (p.slot >= 0) Wfas3UiState.connectRequest.value = p.slot
                        close()
                    }
                )
            }
            is Wfas3Android.ClientPairing.Failed -> {
                BigButton(
                    text = stringResource(R.string.w3_cpair_again),
                    icon = Icons.Rounded.Sync,
                    onClick = { touch.tap(); started = false; code = ""; pin = ""; Wfas3UiState.scannedCode.value = null; Wfas3Android.resetPairing() }
                )
                TextButton(onClick = { touch.tap(); close() }) { Text(stringResource(R.string.w3_close)) }
            }
        }
    }
}

/** The first step of pairing a server: how, the code or the PIN, and what to ask for. */
@Composable
private fun MethodBody(
    usePin: Boolean, onUsePin: (Boolean) -> Unit,
    code: String, onCode: (String) -> Unit,
    pin: String, onPin: (String) -> Unit,
    chosen: Wfas3Beacon?, onChosen: (Wfas3Beacon?) -> Unit,
    mic: Boolean, onMic: (Boolean) -> Unit,
    guest: Boolean, onGuest: (Boolean) -> Unit,
    onRequestScan: () -> Unit,
    onCancel: () -> Unit,
    onGo: () -> Unit
) {
    val failure by Wfas3Android.failure.collectAsState()
    val noCamera by Wfas3UiState.noCamera.collectAsState()
    val beacons by Wfas3Android.beacons.collectAsState()
    val context = LocalContext.current
    val touch = rememberAppHaptics()

    // Searching cannot work while the port is closed: say why.
    failure?.let { NotePanel(Wfas3Android.failureText(context), Icons.Rounded.Warning, Hue.Red) }

    SegmentedChoice(
        options = listOf(
            Option(false, stringResource(R.string.w3_cpair_tab_code), Icons.Rounded.QrCode2),
            Option(true, stringResource(R.string.w3_cpair_tab_pin), Icons.Rounded.Dialpad)
        ),
        selected = usePin,
        onChange = onUsePin
    )

    // What to ask for comes before the code, folded: the defaults suit most,
    // and the pairing starts by itself once the code is in.
    Disclosure(
        key = "w3.cpair.options",
        icon = Icons.Rounded.Tune,
        title = stringResource(R.string.w3u_cpair_options),
        detail = listOf(
            stringResource(if (mic) R.string.w3u_mic_tile_on else R.string.w3u_mic_tile_off),
            stringResource(if (guest) R.string.w3u_guest_on else R.string.w3u_guest_off)
        ).joinToString(" ")
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleTile(
                icon = Icons.Rounded.Mic,
                title = stringResource(R.string.w3u_mic_tile),
                detail = stringResource(if (mic) R.string.w3u_mic_tile_on else R.string.w3u_mic_tile_off),
                checked = mic,
                onChange = onMic,
                modifier = Modifier.weight(1f)
            )
            ToggleTile(
                icon = Icons.Rounded.PersonOff,
                title = stringResource(R.string.w3u_guest_tile),
                detail = stringResource(if (guest) R.string.w3u_guest_on else R.string.w3u_guest_off),
                checked = guest,
                onChange = onGuest,
                modifier = Modifier.weight(1f)
            )
        }
    }

    AnimatedContent(
        targetState = usePin,
        transitionSpec = { fadeIn(Motion.defaultEffects()) togetherWith fadeOut(Motion.fastEffects()) using SizeTransform(clip = false) },
        label = "method"
    ) { pinMode ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!pinMode) {
                Text(stringResource(R.string.w3_cpair_code_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigButton(
                    text = stringResource(R.string.w3_cpair_scan),
                    icon = Icons.Rounded.QrCodeScanner,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { touch.tap(); onRequestScan() }
                )
                if (noCamera) NotePanel(stringResource(R.string.w3_cpair_no_camera), Icons.Rounded.Info)
                val bad = code.isNotBlank() && !Wfas3Android.codeValid(code)
                OutlinedTextField(
                    value = code,
                    onValueChange = onCode,
                    label = { Text(stringResource(R.string.w3_cpair_code_label)) },
                    isError = bad,
                    supportingText = { if (bad) Text(stringResource(R.string.w3_cpair_code_invalid)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(stringResource(R.string.w3_cpair_pin_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (beacons.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        LoadingIndicator(modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.w3_cpair_pin_searching), style = MaterialTheme.typography.bodySmall)
                    }
                }
                beacons.forEach { b ->
                    key(b.key) { BeaconRow(b, chosen?.key == b.key) { touch.segment(); onChosen(b) } }
                }
                if (beacons.isNotEmpty()) {
                    Text(stringResource(R.string.w3_cpair_pin_names_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(
                    visible = chosen != null,
                    enter = expandVertically(Motion.defaultSpatial()) + fadeIn(),
                    exit = shrinkVertically(Motion.defaultSpatial()) + fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.w3_cpair_pin_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        PinField(pin = pin, length = 8, onPin = onPin)
                    }
                }
            }
        }
    }

    val ready = if (!usePin) Wfas3Android.codeValid(code) else (chosen != null && pin.length == 8)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigButton(
            text = stringResource(R.string.w3_cancel),
            icon = Icons.Rounded.Close,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        BigButton(
            text = stringResource(R.string.w3_cpair_go),
            icon = Icons.Rounded.Link,
            enabled = ready,
            onClick = onGo,
            modifier = Modifier.weight(1f)
        )
    }
}

/** A server with an open PIN window, nearby. Its name is not verified until the codes are compared. */
@Composable
private fun BeaconRow(b: Wfas3Beacon, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val back by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest, Motion.fastEffects(), label = "beaconBack")
    val corner by animateFloatAsState(if (selected) 28f else 16f, Motion.fastSpatial(), label = "beaconCorner")
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(corner.dp),
        color = back,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceBadge(b.name, live = selected, size = 40)
            Text(b.name.ifBlank { "?" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 12.dp))
            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = scheme.primary)
            else Icon(Icons.Rounded.WifiFind, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

/** What happened by itself: a pairing to renew, a server that forgot this device, and the like. */
@Composable
private fun NoticeDialog(n: Wfas3Android.Notice) {
    val context = LocalContext.current
    val touch = rememberAppHaptics()
    LaunchedEffect(n.id) { touch.attention() }
    when (n) {
        is Wfas3Android.Notice.Suspect -> AskDialog(
            status = W3Look.Warning,
            title = stringResource(R.string.w3_notice_suspect_title, n.name),
            detail = stringResource(R.string.w3_notice_suspect_body),
            onDismiss = { Wfas3Android.dismiss(n) }
        ) {
            BigButton(text = stringResource(R.string.w3_close), icon = Icons.Rounded.Close, onClick = { touch.tap(); Wfas3Android.dismiss(n) })
        }
        is Wfas3Android.Notice.ForgetAsked -> AskDialog(
            status = W3Look.Gone,
            title = stringResource(R.string.w3_notice_forget_title, n.name),
            detail = stringResource(R.string.w3_notice_forget_body),
            onDismiss = { Wfas3Android.dismiss(n) }
        ) {
            AnswerRow(
                key = n.id,
                no = stringResource(R.string.w3_notice_keep),
                yes = stringResource(R.string.w3_paired_remove),
                onNo = { touch.tap(); Wfas3Android.dismiss(n) },
                onYes = { touch.confirm(); Wfas3Android.dismiss(n); Wfas3Android.bg { Wfas3Android.removePair(n.pair) } }
            )
        }
        is Wfas3Android.Notice.MicRefused -> AskDialog(
            status = W3Look.Info,
            title = stringResource(R.string.w3u_notice_mic_title),
            detail = stringResource(R.string.w3_notice_mic_refused),
            onDismiss = { Wfas3Android.dismiss(n) }
        ) {
            BigButton(text = stringResource(R.string.w3_close), icon = Icons.Rounded.Close, onClick = { touch.tap(); Wfas3Android.dismiss(n) })
        }
        is Wfas3Android.Notice.Storage -> AskDialog(
            status = W3Look.Failed,
            title = stringResource(R.string.w3u_notice_storage_title),
            detail = context.getString(R.string.w3_notice_storage),
            onDismiss = { Wfas3Android.dismiss(n) }
        ) {
            BigButton(text = stringResource(R.string.w3_close), icon = Icons.Rounded.Close, onClick = { touch.tap(); Wfas3Android.dismiss(n) })
        }
    }
}
