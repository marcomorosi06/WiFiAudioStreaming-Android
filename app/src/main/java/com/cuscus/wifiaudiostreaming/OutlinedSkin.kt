package com.cuscus.wifiaudiostreaming

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val LocalOutlinedSkin: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

object OutlinedSkin {
    val stroke = Color.White.copy(alpha = 0.55f)
    val content = Color.White.copy(alpha = 0.78f)
    val width = 1.dp

    val colorScheme = darkColorScheme(
        primary = content,
        onPrimary = Color.Black,
        primaryContainer = Color.Transparent,
        onPrimaryContainer = content,
        secondary = content,
        onSecondary = Color.Black,
        secondaryContainer = Color.Transparent,
        onSecondaryContainer = content,
        tertiary = content,
        onTertiary = Color.Black,
        tertiaryContainer = Color.Transparent,
        onTertiaryContainer = content,
        background = Color.Black,
        onBackground = content,
        surface = Color.Black,
        onSurface = content,
        surfaceVariant = Color.Transparent,
        onSurfaceVariant = content,
        surfaceContainer = Color.Transparent,
        surfaceContainerLow = Color.Transparent,
        surfaceContainerLowest = Color.Transparent,
        surfaceContainerHigh = Color.Transparent,
        surfaceContainerHighest = Color.Transparent,
        error = content,
        onError = Color.Black,
        errorContainer = Color.Transparent,
        onErrorContainer = content,
        outline = stroke,
        outlineVariant = stroke
    )
}

@Composable
fun Modifier.skinnedSurface(color: Color, shape: Shape): Modifier =
    if (LocalOutlinedSkin.current) {
        this.border(OutlinedSkin.width, OutlinedSkin.stroke, shape)
    } else {
        this.clip(shape).background(color)
    }

@Composable
fun Modifier.skinnedSurface(brush: Brush, shape: Shape): Modifier =
    if (LocalOutlinedSkin.current) {
        this.border(OutlinedSkin.width, OutlinedSkin.stroke, shape)
    } else {
        this.clip(shape).background(brush)
    }

/**
 * Come [skinnedSurface] ma dipinge la forma invece di ritagliarla.
 *
 * Va usata solo dove il riquadro non ha figli: senza figli il ritaglio non serve a
 * niente e il risultato a schermo e' identico, ma costa molto meno. Con una forma
 * Generic (un Path arbitrario, come quelli dei morph) `clip` obbliga a un
 * clipPath, che perde la strada veloce delle forme arrotondate e puo' costringere
 * a un livello fuori schermo; riempire il path e' una singola geometria. Sull'orb
 * ce ne sono due sovrapposti che ruotano e pulsano a ogni frame.
 */
@Composable
fun Modifier.skinnedFill(color: Color, shape: Shape): Modifier =
    if (LocalOutlinedSkin.current) {
        this.border(OutlinedSkin.width, OutlinedSkin.stroke, shape)
    } else {
        this.drawBehind {
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Generic   -> drawPath(outline.path, color)
                is Outline.Rounded   -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, color)
                is Outline.Rectangle -> drawRect(color)
            }
        }
    }

@Composable
fun Modifier.skinnedFill(brush: Brush, shape: Shape): Modifier =
    if (LocalOutlinedSkin.current) {
        this.border(OutlinedSkin.width, OutlinedSkin.stroke, shape)
    } else {
        this.drawBehind {
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Generic   -> drawPath(outline.path, brush)
                is Outline.Rounded   -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, brush)
                is Outline.Rectangle -> drawRect(brush)
            }
        }
    }
