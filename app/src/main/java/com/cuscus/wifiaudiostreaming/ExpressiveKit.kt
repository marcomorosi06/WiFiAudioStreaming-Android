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

/*
 * Module: ExpressiveKit (Android)
 *
 * The building blocks of the Material 3 Expressive interface: motion tokens,
 * colour families, shapes, panels, tiles, choices, dialogs and the slats of
 * the settings. Adapted from the interface of WiFi Screen Streaming, by the
 * same author and under the same licence (EUPL 1.2).
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Module: expressive kit
 *
 * The pieces the WFAS v3 screens and the settings are built from, taken from
 * WiFi Screen Streaming so the two apps look and move the same way:
 *  - springs from the Material 3 Expressive motion tokens: spatial ones
 *    (position, size, shape) overshoot a little, effects ones (colour,
 *    opacity) never go past their end value;
 *  - a press makes things shrink a little and spring back;
 *  - every state has a shape, an icon and a colour family, so it reads at a
 *    glance and without colour too;
 *  - every question uses one dialog: the shape of its state on top, No never
 *    waits, yes is ready only after a moment on screen.
 */

// -- motion --------------------------------------------------------------------------

/** True when Android's animations are off or the person asked for calmer motion. */
val LocalCalmMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Android's own animations are off: calm motion is on and cannot be turned off here. */
val LocalSystemCalmMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

object Motion {
    // Expressive spatial tokens: damping ratio and stiffness.
    private const val FAST_DAMPING = 0.6f
    private const val FAST_STIFFNESS = 800f
    private const val DEFAULT_DAMPING = 0.8f
    private const val DEFAULT_STIFFNESS = 380f
    private const val SLOW_DAMPING = 0.8f
    private const val SLOW_STIFFNESS = 200f

    // Standard spatial tokens, for calm motion.
    private const val CALM_DAMPING = 0.9f
    private const val CALM_FAST_STIFFNESS = 1400f
    private const val CALM_DEFAULT_STIFFNESS = 700f
    private const val CALM_SLOW_STIFFNESS = 300f

    // Effects tokens: the same in both schemes.
    private const val EFFECTS_FAST_STIFFNESS = 3800f
    private const val EFFECTS_DEFAULT_STIFFNESS = 1600f
    private const val EFFECTS_SLOW_STIFFNESS = 800f

    @Composable
    @ReadOnlyComposable
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = if (LocalCalmMotion.current) {
        spring(dampingRatio = CALM_DAMPING, stiffness = CALM_FAST_STIFFNESS)
    } else {
        spring(dampingRatio = FAST_DAMPING, stiffness = FAST_STIFFNESS)
    }

    @Composable
    @ReadOnlyComposable
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = if (LocalCalmMotion.current) {
        spring(dampingRatio = CALM_DAMPING, stiffness = CALM_DEFAULT_STIFFNESS)
    } else {
        spring(dampingRatio = DEFAULT_DAMPING, stiffness = DEFAULT_STIFFNESS)
    }

    @Composable
    @ReadOnlyComposable
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = if (LocalCalmMotion.current) {
        spring(dampingRatio = CALM_DAMPING, stiffness = CALM_SLOW_STIFFNESS)
    } else {
        spring(dampingRatio = SLOW_DAMPING, stiffness = SLOW_STIFFNESS)
    }

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = EFFECTS_FAST_STIFFNESS)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = EFFECTS_DEFAULT_STIFFNESS)

    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = EFFECTS_SLOW_STIFFNESS)

    /** How much of their speed the endless decorations keep: all, or a tenth with calm motion. */
    @Composable
    @ReadOnlyComposable
    fun ambient(): Float = if (LocalCalmMotion.current) 0.1f else 1f
}

// -- colour families -------------------------------------------------------------------

/** A family of colours: the shape, what sits on it, and a strong accent for rings and dots. */
@Immutable
data class Tone(val container: Color, val onContainer: Color, val accent: Color)

/** The colour families. Primary, Tertiary, Red and Neutral follow the theme; the others keep their hue. */
enum class Hue { Primary, Tertiary, Amber, Violet, Green, Pink, Red, Orange, Neutral, Teal }

/**
 * The colours of a family in the current theme. The fixed hues lean a tenth
 * of the way towards the primary colour, so they sit well with the wallpaper
 * palette too.
 */
@Composable
fun toneOf(hue: Hue): Tone {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    fun fixed(light: Tone, night: Tone): Tone {
        val base = if (dark) night else light
        return Tone(
            container = lerp(base.container, scheme.primary, 0.10f),
            onContainer = base.onContainer,
            accent = lerp(base.accent, scheme.primary, 0.10f)
        )
    }
    return when (hue) {
        Hue.Primary -> Tone(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        Hue.Tertiary -> Tone(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
        Hue.Red -> Tone(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
        Hue.Neutral -> Tone(scheme.surfaceContainerHighest, scheme.onSurface, scheme.outline)
        Hue.Amber -> fixed(
            Tone(Color(0xFFFFE08B), Color(0xFF3A2C00), Color(0xFF7A5900)),
            Tone(Color(0xFF5A4300), Color(0xFFFFE08B), Color(0xFFF2C14A))
        )
        Hue.Violet -> fixed(
            Tone(Color(0xFFEADDFF), Color(0xFF21005D), Color(0xFF6750A4)),
            Tone(Color(0xFF4F378B), Color(0xFFEADDFF), Color(0xFFD0BCFF))
        )
        Hue.Green -> fixed(
            Tone(Color(0xFFB7F2C2), Color(0xFF00210B), Color(0xFF1E6C35)),
            Tone(Color(0xFF005226), Color(0xFFB7F2C2), Color(0xFF8BD89A))
        )
        Hue.Pink -> fixed(
            Tone(Color(0xFFFFD8E8), Color(0xFF3B0727), Color(0xFF8E4A6D)),
            Tone(Color(0xFF71335A), Color(0xFFFFD8E8), Color(0xFFFFAFD6))
        )
        Hue.Orange -> fixed(
            Tone(Color(0xFFFFDBC9), Color(0xFF341100), Color(0xFF9A4500)),
            Tone(Color(0xFF763300), Color(0xFFFFDBC9), Color(0xFFFFB68E))
        )
        Hue.Teal -> fixed(
            Tone(Color(0xFFB8EAFF), Color(0xFF001F29), Color(0xFF00677F)),
            Tone(Color(0xFF004E60), Color(0xFFB8EAFF), Color(0xFF5FD4FD))
        )
    }
}

// -- shapes ----------------------------------------------------------------------------

/** Scales a path to fill [size] around its centre, turned by [turn] degrees. */
private fun fitPath(path: androidx.compose.ui.graphics.Path, size: Size, turn: Float): Outline {
    val bounds = path.getBounds()
    if (bounds.width <= 0f || bounds.height <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
    val scale = min(size.width / bounds.width, size.height / bounds.height)
    val matrix = Matrix()
    matrix.translate(size.width / 2f, size.height / 2f)
    if (turn != 0f) matrix.rotateZ(turn)
    matrix.scale(scale, scale)
    matrix.translate(-bounds.left - bounds.width / 2f, -bounds.top - bounds.height / 2f)
    path.transform(matrix)
    return Outline.Generic(path)
}

/** A Material shape (a RoundedPolygon) as a Compose shape. */
class PolygonShape(private val polygon: RoundedPolygon, private val turn: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        fitPath(polygon.toPath().asComposePath(), size, turn)

    override fun equals(other: Any?): Boolean = other is PolygonShape && other.polygon === polygon && other.turn == turn
    override fun hashCode(): Int = polygon.hashCode() * 31 + turn.hashCode()
}

/** A morph between two Material shapes, at some progress, as a Compose shape. */
class MorphShape(private val morph: Morph, private val progress: Float, private val turn: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        fitPath(morph.toPath(progress.coerceIn(0f, 1f)).asComposePath(), size, turn)

    override fun equals(other: Any?): Boolean =
        other is MorphShape && other.morph === morph && other.progress == progress && other.turn == turn
    override fun hashCode(): Int = morph.hashCode() * 31 + progress.hashCode() * 31 + turn.hashCode()
}

/** The same shape for the same name, every time: a device is recognised by its badge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun signatureShape(seed: String): RoundedPolygon {
    val shapes = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.Burst,
        MaterialShapes.Pill,
        MaterialShapes.Diamond,
        MaterialShapes.Square,
        MaterialShapes.Circle
    )
    val hash = seed.fold(7) { acc, char -> acc * 31 + char.code }
    return shapes[((hash % shapes.size) + shapes.size) % shapes.size]
}

// -- states ----------------------------------------------------------------------------

/** How a state moves while it is on screen. */
enum class Motif { Still, Turn, Spin, Breathe, Shake }

/** A state: its shape, its icon, its colour family and its motion. */
@Immutable
data class KitStatus(
    val key: String,
    val shape: RoundedPolygon,
    val icon: ImageVector,
    val hue: Hue,
    val motif: Motif
)

// -- blocks ----------------------------------------------------------------------------

/** A scale that dips while the element is held and springs back when let go. */
@Composable
fun rememberPressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): State<Float> {
    val down by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (down) pressed else 1f,
        animationSpec = Motion.fastSpatial(),
        label = "press"
    )
}

/** A rounded card. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    corner: Dp = 28.dp,
    content: @Composable () -> Unit
) {
    val shown by animateColorAsState(color, Motion.defaultEffects(), label = "panel")
    Surface(shape = RoundedCornerShape(corner), color = shown, modifier = modifier.fillMaxWidth()) {
        content()
    }
}

/** An icon sitting on an expressive shape, as the lead of a row or a section. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeIcon(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie4Sided,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    val back by animateColorAsState(container, Motion.defaultEffects(), label = "iconBack")
    val front by animateColorAsState(content, Motion.defaultEffects(), label = "iconFront")
    val polygon = remember(shape) { PolygonShape(shape) }
    Box(
        modifier = modifier
            .size(size)
            .background(back, polygon),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = front, modifier = Modifier.size(iconSize))
    }
}

/** A small capital label above a group. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.6.sp,
        color = color,
        modifier = modifier
    )
}

/** The large button of a screen: its corners tighten while it is held. */
@Composable
fun BigButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val down by interaction.collectIsPressedAsState()
    val corner by animateFloatAsState(if (down) 14f else 32f, Motion.fastSpatial(), label = "corner")
    val scale by rememberPressScale(interaction, 0.97f)
    val back by animateColorAsState(
        if (enabled) container else MaterialTheme.colorScheme.surfaceContainerHighest,
        Motion.fastEffects(),
        label = "bigBack"
    )
    val front by animateColorAsState(
        if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant,
        Motion.fastEffects(),
        label = "bigFront"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner.dp),
        color = back,
        contentColor = front,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

/**
 * A yes that only works once the question has been on screen for a moment,
 * so a tap meant for something else cannot answer it. The wait shows: the
 * button fills from the left and becomes solid when it can be pressed.
 */
@Composable
fun ReadyButton(
    text: String,
    key: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    waitMs: Int = 900,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary
) {
    val fill = remember(key) { Animatable(0f) }
    var ready by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        fill.animateTo(1f, tween(waitMs, easing = LinearEasing))
        ready = true
    }
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, 0.95f)
    val front by animateColorAsState(if (ready) content else MaterialTheme.colorScheme.onSurfaceVariant, Motion.fastEffects(), label = "readyFront")
    Surface(
        onClick = onClick,
        enabled = ready,
        interactionSource = interaction,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = front,
        modifier = modifier
            .heightIn(min = 60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The filling part: behind the label, as wide as the time already waited.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = fill.value
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(container)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

/** No first and always ready; yes after the reading time. */
@Composable
fun AnswerRow(
    key: Any,
    no: String,
    yes: String,
    onNo: () -> Unit,
    onYes: () -> Unit,
    yesColor: Color = MaterialTheme.colorScheme.primary,
    onYesColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigButton(
            text = no,
            icon = Icons.Rounded.Close,
            onClick = onNo,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        ReadyButton(
            text = yes,
            key = key,
            onClick = onYes,
            container = yesColor,
            content = onYesColor,
            modifier = Modifier.weight(1f)
        )
    }
}

/** A code or a PIN, one tile per character, in groups. The tiles come in one after the other. */
@Composable
fun CodeTiles(
    code: String,
    group: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    tile: Dp = 34.dp
) {
    val chars = code.filter { it.isLetterOrDigit() }
    val groups = chars.chunked(group.coerceAtLeast(1))
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var index = 0
        for (part in groups) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (char in part) {
                    CodeTile(char, index, chars, container, content, tile)
                    index++
                }
            }
        }
    }
}

@Composable
private fun CodeTile(char: Char, index: Int, code: String, container: Color, content: Color, tile: Dp) {
    val appear = remember(code) { Animatable(0f) }
    val spring = Motion.fastSpatial<Float>()
    LaunchedEffect(code) {
        delay(45L * index)
        appear.animateTo(1f, spring)
    }
    Box(
        modifier = Modifier
            .width(tile)
            .height(tile * 1.32f)
            .graphicsLayer {
                val v = appear.value
                scaleX = 0.6f + 0.4f * v
                scaleY = 0.6f + 0.4f * v
                alpha = v.coerceIn(0f, 1f)
                translationY = (1f - v) * 18f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char.toString(),
            color = content,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = (tile.value * 0.62f).sp
        )
    }
}

/** A row: lead icon on a shape, title, detail and an optional end. */
@Composable
fun InfoRow(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    end: (@Composable () -> Unit)? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ShapeIcon(icon, container, content, size = 40.dp, iconSize = 20.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (end != null) end()
    }
}

/** A short note in a tonal panel: what is going on, or why something cannot be done. */
@Composable
fun NotePanel(text: String, icon: ImageVector, hue: Hue = Hue.Neutral, modifier: Modifier = Modifier) {
    val tone = toneOf(hue)
    val back = if (hue == Hue.Neutral) MaterialTheme.colorScheme.surfaceContainerHigh else tone.container
    val front = if (hue == Hue.Neutral) MaterialTheme.colorScheme.onSurfaceVariant else tone.onContainer
    Panel(color = back, corner = 20.dp, modifier = modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (hue == Hue.Neutral) MaterialTheme.colorScheme.primary else front, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = front)
        }
    }
}

/**
 * A switch as a tile: the whole tile is the target, its colour and corners
 * change with it, and the icon turns a little when it goes on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToggleTile(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val touch = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, 0.95f)
    val corner by animateFloatAsState(if (checked) 30f else 18f, Motion.fastSpatial(), label = "tileCorner")
    val turn by animateFloatAsState(if (checked) 45f else 0f, Motion.fastSpatial(), label = "tileTurn")
    val scheme = MaterialTheme.colorScheme
    val back by animateColorAsState(
        when {
            !enabled -> scheme.surfaceContainer
            checked -> scheme.primaryContainer
            else -> scheme.surfaceContainerHigh
        },
        Motion.fastEffects(),
        label = "tileBack"
    )
    val front by animateColorAsState(
        when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
            checked -> scheme.onPrimaryContainer
            else -> scheme.onSurface
        },
        Motion.fastEffects(),
        label = "tileFront"
    )
    val cookie = remember { PolygonShape(MaterialShapes.Cookie4Sided) }
    Surface(
        checked = checked,
        onCheckedChange = { on ->
            touch.toggle(on)
            onChange(on)
        },
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner.dp),
        color = back,
        contentColor = front,
        modifier = modifier
            .semantics { role = Role.Switch }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = turn }
                    .background(if (checked) scheme.primary else scheme.surfaceContainerHighest, cookie),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (checked) scheme.onPrimary else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = -turn }
                )
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = front.copy(alpha = 0.8f))
        }
    }
}

/** One value of a choice: what is stored, what is shown, and why one would pick it. */
data class Option<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val detail: String? = null,
    /** Shown in the error colours when chosen: a choice that lowers protection. */
    val risky: Boolean = false
)

/**
 * A few values in a column of connected rows: the outer corners are large,
 * the inner ones small, and the chosen row fills with colour and rounds its
 * corners fully.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChoiceList(
    options: List<Option<T>>,
    selected: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val touch = rememberAppHaptics()
    val mark9 = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { index, option ->
            val chosen = option.value == selected
            val first = index == 0
            val last = index == options.lastIndex
            val top by animateFloatAsState(if (chosen || first) 24f else 6f, Motion.fastSpatial(), label = "choiceTop")
            val bottom by animateFloatAsState(if (chosen || last) 24f else 6f, Motion.fastSpatial(), label = "choiceBottom")
            val scheme = MaterialTheme.colorScheme
            val back by animateColorAsState(
                when {
                    chosen && option.risky -> scheme.errorContainer
                    chosen -> scheme.primaryContainer
                    else -> scheme.surfaceContainerHighest
                },
                Motion.fastEffects(),
                label = "choiceBack"
            )
            val front by animateColorAsState(
                when {
                    chosen && option.risky -> scheme.onErrorContainer
                    chosen -> scheme.onPrimaryContainer
                    else -> scheme.onSurface
                },
                Motion.fastEffects(),
                label = "choiceFront"
            )
            val mark by animateFloatAsState(if (chosen) 1f else 0f, Motion.fastSpatial(), label = "choiceMark")
            val interaction = remember { MutableInteractionSource() }
            val scale by rememberPressScale(interaction, 0.97f)
            Surface(
                selected = chosen,
                onClick = {
                    if (!chosen) touch.segment()
                    onChange(option.value)
                },
                enabled = enabled,
                interactionSource = interaction,
                shape = RoundedCornerShape(topStart = top.dp, topEnd = top.dp, bottomStart = bottom.dp, bottomEnd = bottom.dp),
                color = back,
                contentColor = front,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.RadioButton }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (enabled) 1f else 0.55f
                    }
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (option.icon != null) {
                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (option.detail != null) {
                            Text(option.detail, style = MaterialTheme.typography.bodySmall, color = front.copy(alpha = 0.78f))
                        }
                    }
                    // The mark of the chosen row: a cookie that pops in and turns.
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                scaleX = 0.4f + 0.6f * mark
                                scaleY = 0.4f + 0.6f * mark
                                rotationZ = 90f * mark
                                alpha = mark.coerceIn(0f, 1f)
                            }
                            .background(if (option.risky) scheme.error else scheme.primary, mark9),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (option.risky) scheme.onError else scheme.onPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = -90f * mark }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A few short values side by side, as a connected button group: the chosen
 * one becomes a full pill, the others keep their inner corners small.
 */
@Composable
fun <T> SegmentedChoice(
    options: List<Option<T>>,
    selected: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val touch = rememberAppHaptics()
    Row(modifier = modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { index, option ->
            val chosen = option.value == selected
            val start by animateFloatAsState(if (chosen || index == 0) 50f else 10f, Motion.fastSpatial(), label = "segStart")
            val end by animateFloatAsState(if (chosen || index == options.lastIndex) 50f else 10f, Motion.fastSpatial(), label = "segEnd")
            val scheme = MaterialTheme.colorScheme
            val back by animateColorAsState(if (chosen) scheme.primary else scheme.surfaceContainerHighest, Motion.fastEffects(), label = "segBack")
            val front by animateColorAsState(if (chosen) scheme.onPrimary else scheme.onSurfaceVariant, Motion.fastEffects(), label = "segFront")
            val interaction = remember { MutableInteractionSource() }
            val scale by rememberPressScale(interaction, 0.94f)
            Surface(
                selected = chosen,
                onClick = {
                    if (!chosen) touch.segment()
                    onChange(option.value)
                },
                enabled = enabled,
                interactionSource = interaction,
                shape = RoundedCornerShape(
                    topStartPercent = start.roundToInt(),
                    bottomStartPercent = start.roundToInt(),
                    topEndPercent = end.roundToInt(),
                    bottomEndPercent = end.roundToInt()
                ),
                color = back,
                contentColor = front,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (enabled) 1f else 0.55f
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (option.icon != null) {
                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(option.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** A dot that beats: something is live. Still with calm motion. */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier) {
    val ambient = Motion.ambient()
    val beat = remember { Animatable(1f) }
    LaunchedEffect(ambient) {
        if (ambient < 1f) {
            beat.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            beat.animateTo(1.45f, tween(520))
            beat.animateTo(1f, tween(520))
        }
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .graphicsLayer {
                scaleX = beat.value
                scaleY = beat.value
            }
            .background(color, CircleShape)
    )
}

/** A short label on a pill, in the colours of a family. */
@Composable
fun KitChip(text: String, icon: ImageVector?, hue: Hue, modifier: Modifier = Modifier) {
    val tone = toneOf(hue)
    Surface(shape = CircleShape, color = tone.container, contentColor = tone.onContainer, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** How long something stays open: a bar that empties and the time left, once a second. */
@Composable
fun Countdown(closesAtMs: Long, totalMs: Long, tone: Tone, label: @Composable (Long) -> String) {
    if (closesAtMs <= 0L) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(closesAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= closesAtMs) break
            delay(1000)
        }
    }
    val left = (closesAtMs - now).coerceAtLeast(0L)
    val fraction by animateFloatAsState(
        (left.toFloat() / totalMs.coerceAtLeast(1L)).coerceIn(0f, 1f),
        tween(1000, easing = LinearEasing),
        label = "left"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(tone.onContainer.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .graphicsLayer {
                        scaleX = fraction
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(CircleShape)
                    .background(tone.accent)
            )
        }
        Text(label(left / 1000), style = MaterialTheme.typography.labelMedium, color = tone.onContainer)
    }
}

/** A PIN typed into boxes: the real field stays underneath, invisible, and owns the keyboard. */
@Composable
fun PinField(pin: String, length: Int, onPin: (String) -> Unit, modifier: Modifier = Modifier) {
    val touch = rememberAppHaptics()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
    BasicTextField(
        value = pin,
        onValueChange = { text ->
            val next = text.filter { it.isDigit() }.take(length)
            if (next.length > pin.length) {
                if (next.length == length) touch.confirm() else touch.tick()
            } else if (next.length < pin.length) {
                touch.tap()
            }
            onPin(next)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(1.dp)) { field() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val half = (length + 1) / 2
                    for (group in 0 until 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            val from = group * half
                            val to = minOf(length, from + half)
                            for (index in from until to) PinBox(pin.getOrNull(index), current = index == pin.length)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PinBox(digit: Char?, current: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val filled = digit != null
    val pop by animateFloatAsState(if (filled) 1f else 0f, Motion.fastSpatial(), label = "pinPop")
    val lift by animateFloatAsState(if (current) 1f else 0f, Motion.fastSpatial(), label = "pinLift")
    val back by animateColorAsState(if (filled) scheme.primaryContainer else scheme.surfaceContainerHighest, Motion.fastEffects(), label = "pinBack")
    val edge by animateColorAsState(if (current) scheme.primary else scheme.surfaceContainerHighest, Motion.fastEffects(), label = "pinEdge")
    Box(
        modifier = Modifier
            .width(30.dp)
            .height(44.dp)
            .graphicsLayer {
                translationY = -4f * lift
                val s = 1f + 0.08f * lift
                scaleX = s
                scaleY = s
            }
            .background(back, RoundedCornerShape(10.dp))
            .border(2.dp, edge, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Shown as dots: the PIN is a secret, the boxes only count the digits.
        Text(
            text = if (digit != null) "\u2022" else "",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = scheme.onPrimaryContainer,
            modifier = Modifier.graphicsLayer {
                scaleX = 0.4f + 0.6f * pop
                scaleY = 0.4f + 0.6f * pop
                alpha = pop.coerceIn(0f, 1f)
            }
        )
    }
}

// -- the dialog of every question --------------------------------------------------------

/**
 * The dialog every question uses: the shape of its state on top, which grows
 * out of a circle when the dialog opens and morphs when the state changes; a
 * title that says what is being decided; then the body. [secure] keeps it out
 * of screenshots and of the recents preview (codes, PINs, QR codes).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AskDialog(
    status: KitStatus,
    title: String,
    detail: String?,
    onDismiss: () -> Unit,
    secure: Boolean = false,
    dismissable: Boolean = true,
    body: @Composable ColumnScope.() -> Unit
) {
    val tone = toneOf(status.hue)
    val container by animateColorAsState(tone.container, Motion.defaultEffects(), label = "askBack")
    val onContainer by animateColorAsState(tone.onContainer, Motion.defaultEffects(), label = "askFront")
    val open = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    val openSpring = Motion.defaultSpatial<Float>()
    val growSpring = Motion.slowSpatial<Float>()
    LaunchedEffect(Unit) { open.animateTo(1f, openSpring) }
    LaunchedEffect(Unit) {
        delay(60)
        grow.animateTo(1f, growSpring)
    }

    // The morph: from a circle when the dialog opens, then from state to state.
    var from by remember { mutableStateOf(MaterialShapes.Circle) }
    var to by remember { mutableStateOf(status.shape) }
    val settle = remember { Animatable(0f) }
    val settleSpring = Motion.slowSpatial<Float>()
    LaunchedEffect(status.key) {
        if (to !== status.shape) {
            from = to
            to = status.shape
            settle.snapTo(0f)
        }
        settle.animateTo(1f, settleSpring)
    }
    val morph = remember(from, to) { Morph(from, to) }

    // A spinning state turns while it waits; the others settle.
    val ambient = Motion.ambient()
    val spin = remember { Animatable(0f) }
    LaunchedEffect(status.motif, ambient) {
        if (status.motif == Motif.Spin && ambient == 1f) {
            while (true) {
                spin.animateTo(spin.value + 360f, tween(2400, easing = LinearEasing))
            }
        } else {
            // Back to the nearest upright position, never a long turn backwards.
            spin.snapTo(spin.value % 360f)
            spin.animateTo(if (spin.value > 180f) 360f else 0f, Motion.defaultEffects())
        }
    }
    // A failure shakes once when it arrives.
    val shake = remember { Animatable(0f) }
    val shakeSpring = Motion.fastSpatial<Float>()
    LaunchedEffect(status.key) {
        if (status.motif == Motif.Shake && ambient == 1f) {
            for (x in floatArrayOf(-1f, 0.9f, -0.6f, 0.35f, 0f)) shake.animateTo(x, shakeSpring)
        } else {
            shake.snapTo(0f)
        }
    }

    Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
            securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit
        )
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.graphicsLayer {
                val v = open.value
                scaleX = 0.9f + 0.1f * v
                scaleY = 0.9f + 0.1f * v
                alpha = v.coerceIn(0f, 1f)
            }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                rotationZ = 120f * grow.value + spin.value
                                val s = 0.7f + 0.3f * grow.value
                                scaleX = s
                                scaleY = s
                                translationX = shake.value * 24f
                            }
                            .background(container, MorphShape(morph, settle.value))
                    )
                    AnimatedContent(
                        targetState = status.icon,
                        transitionSpec = { fadeIn(Motion.fastEffects()) togetherWith fadeOut(Motion.fastEffects()) using SizeTransform(clip = false) },
                        label = "askIcon",
                        modifier = Modifier.graphicsLayer { translationX = shake.value * 24f }
                    ) { icon ->
                        Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(34.dp))
                    }
                }
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { fadeIn(Motion.defaultEffects()) togetherWith fadeOut(Motion.fastEffects()) using SizeTransform(clip = false) },
                    label = "askTitle"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                body()
            }
        }
    }
}

// -- connected groups (settings) -----------------------------------------------------------

/** Corner of one slat, and the gap between two slats, in a settings group. */
val SETTINGS_SLAT_CORNER = 6.dp
val SETTINGS_SLAT_GAP = 3.dp

/**
 * The surface of one row of a connected group: small corners, so that the
 * group (which clips its outer corners large) reads as one block of slats.
 */
@Composable
fun GroupRow(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(SETTINGS_SLAT_CORNER),
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SETTINGS_SLAT_GAP / 2)
    ) {
        content()
    }
}

/**
 * A ready-made choice: a tile with its own shape and colour that turns and
 * fills when chosen. Used for presets, where one tap sets several values.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PresetTile(
    icon: ImageVector,
    title: String,
    detail: String,
    hue: Hue,
    shape: RoundedPolygon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val touch = rememberAppHaptics()
    val tone = toneOf(hue)
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, 0.94f)
    val corner by animateFloatAsState(if (selected) 32f else 20f, Motion.fastSpatial(), label = "presetCorner")
    val turn by animateFloatAsState(if (selected) 60f else 0f, Motion.defaultSpatial(), label = "presetTurn")
    val grow by animateFloatAsState(if (selected) 1.12f else 1f, Motion.fastSpatial(), label = "presetGrow")
    val scheme = MaterialTheme.colorScheme
    val back by animateColorAsState(if (selected) tone.container else scheme.surfaceContainerHigh, Motion.fastEffects(), label = "presetBack")
    val front by animateColorAsState(if (selected) tone.onContainer else scheme.onSurface, Motion.fastEffects(), label = "presetFront")
    val badge = remember(shape) { PolygonShape(shape) }
    Surface(
        selected = selected,
        onClick = { if (!selected) touch.segment(); onClick() },
        interactionSource = interaction,
        shape = RoundedCornerShape(corner.dp),
        color = back,
        contentColor = front,
        modifier = modifier
            .semantics { role = Role.RadioButton }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            rotationZ = turn
                            scaleX = grow
                            scaleY = grow
                        }
                        .background(if (selected) tone.accent else scheme.surfaceContainerHighest, badge),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selected) tone.container else scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = -turn }
                    )
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = selected, enter = fadeIn(), exit = fadeOut()) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = tone.accent, modifier = Modifier.size(18.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = front.copy(alpha = 0.78f), minLines = 2, maxLines = 3)
        }
    }
}

/**
 * Everything a curious user may tune, folded away by default: a header with
 * a short summary and a chevron that turns, and the content that opens with
 * a spring. [key] keeps the open state across rotation, per place.
 */
@Composable
fun Disclosure(
    key: String,
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val touch = rememberAppHaptics()
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.fastSpatial(), label = "moreTurn")
    val scheme = MaterialTheme.colorScheme
    val back by animateColorAsState(if (open) scheme.surfaceContainer else scheme.surfaceContainerLow, Motion.defaultEffects(), label = "moreBack")
    val corner by animateFloatAsState(if (open) 28f else 40f, Motion.defaultSpatial(), label = "moreCorner")
    Surface(shape = RoundedCornerShape(corner.dp), color = back, modifier = modifier.fillMaxWidth()) {
        Column {
            Surface(
                onClick = { touch.toggle(!open); open = !open },
                color = Color.Transparent,
                shape = RoundedCornerShape(corner.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeIcon(icon, scheme.tertiaryContainer, scheme.onTertiaryContainer, shape = MaterialShapes.Cookie6Sided, size = 40.dp, iconSize = 20.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (badge != null) {
                                Spacer(Modifier.width(8.dp))
                                KitChip(badge, null, Hue.Tertiary)
                            }
                        }
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2)
                    }
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = turn }
                    )
                }
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(Motion.defaultSpatial()) + fadeIn(Motion.defaultEffects()),
                exit = shrinkVertically(Motion.defaultSpatial()) + fadeOut(Motion.fastEffects())
            ) {
                Column(
                    Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}
