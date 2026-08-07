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

import android.graphics.BlurMaskFilter
import android.graphics.Path
import android.graphics.PointF
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.cuscus.wifiaudiostreaming.dsp.AmbientSpectrumAnalyzer
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Background Audio Spectrogram visualizer for active streaming sessions.
 *
 * Appears ONLY during active streaming when [enabled] is true.
 * Supports two distinct user-selectable styles:
 *  - "BARS": Crisp Desktop-style vertical bars with falling peak caps & gravity physics
 *  - "WAVE": Material 3 Expressive multi-band cubic Bezier fluid waveform layers
 *
 * Supports [isOutlined] mode for drawing both styles with glowing stroke outlines during Blackout Mode.
 */
@Composable
fun AmbientSpectrumBackground(
    isStreaming: Boolean,
    enabled: Boolean = true,
    style: String = "BARS",
    groove: Int = 0,
    isOutlined: Boolean = LocalOutlinedSkin.current,
    modifier: Modifier = Modifier
) {
    if (!enabled || !isStreaming) return

    val numBars  = AmbientSpectrumAnalyzer.NUM_BARS
    val bars     = remember { FloatArray(numBars) }
    val peaks    = remember { FloatArray(numBars) }
    var tick     by remember { mutableIntStateOf(0) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }

    // 30fps polling during streaming
    LaunchedEffect(groove) {
        while (true) {
            rmsLevel = AmbientSpectrumAnalyzer.snapshot(bars, peaks, groove)
            tick++
            delay(33L)
        }
    }

    val infinite = rememberInfiniteTransition(label = "SpectrumPhaseTransition")
    val phaseDuration = (14000f - 8000f * rmsLevel).toInt().coerceIn(6000, 14000)
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            tween(phaseDuration, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "SpectrumPhase"
    )

    val breathe by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(3600, easing = EaseInOutSine),
            RepeatMode.Reverse
        ),
        label = "SpectrumBreathe"
    )

    val primary            = MaterialTheme.colorScheme.primary
    val secondary          = MaterialTheme.colorScheme.secondary
    val tertiary           = MaterialTheme.colorScheme.tertiary
    val primaryContainer   = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer  = MaterialTheme.colorScheme.tertiaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    Canvas(modifier = modifier) {
        val frame = tick // triggers recomposition on frame update

        if (style == "WAVE") {
            drawExpressiveMultiBandWaveform(
                bars               = bars,
                numBars            = numBars,
                phase              = phase,
                breathe            = breathe,
                rmsLevel           = rmsLevel,
                primary            = primary,
                tertiary           = tertiary,
                secondary          = secondary,
                primaryContainer   = primaryContainer,
                tertiaryContainer  = tertiaryContainer,
                secondaryContainer = secondaryContainer,
                isOutlined         = isOutlined
            )
        } else {
            drawCrispDesktopSpectrum(
                bars       = bars,
                peaks      = peaks,
                numBars    = numBars,
                primary    = primary,
                tertiary   = tertiary,
                isOutlined = isOutlined
            )
        }
    }
}

// ── Style 1: Crisp Desktop Spectrum Renderer ──────────────────────────────

private fun DrawScope.drawCrispDesktopSpectrum(
    bars: FloatArray,
    peaks: FloatArray,
    numBars: Int,
    primary: Color,
    tertiary: Color,
    isOutlined: Boolean
) {
    val gap = 4f
    val slot = size.width / numBars
    val barW = (slot - gap).coerceAtLeast(1f)

    val vizHeight = size.height * 0.48f
    val blurRadius = if (isOutlined) 3.5f else 6f

    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas

        // 1. Draw active bars
        for (b in 0 until numBars) {
            val v = bars[b].coerceIn(0f, 1f)
            if (v <= 0.001f) continue
            val h = vizHeight * v
            val x = b * slot + gap / 2f
            val blend = (v * 0.85f).coerceIn(0f, 1f)
            val barColor = lerp(primary, tertiary, blend).copy(alpha = if (isOutlined) 0.95f else 0.75f).toArgb()

            val barPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = barColor
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                if (isOutlined) {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 3.5f
                } else {
                    style = android.graphics.Paint.Style.FILL
                }
            }

            nativeCanvas.drawRoundRect(
                x, size.height - h, x + barW, size.height + blurRadius,
                barW / 2f, barW / 2f,
                barPaint
            )
        }

        // 2. Draw falling peak caps with gravity physics
        for (b in 0 until numBars) {
            val p = peaks[b].coerceIn(0f, 1f)
            if (p <= 0.02f) continue
            val y = size.height - vizHeight * p
            val x = b * slot + gap / 2f

            val peakPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = primary.copy(alpha = 0.95f).toArgb()
                maskFilter = BlurMaskFilter(if (isOutlined) 2.5f else 4f, BlurMaskFilter.Blur.NORMAL)
                if (isOutlined) {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 3f
                } else {
                    style = android.graphics.Paint.Style.FILL
                }
            }

            nativeCanvas.drawRoundRect(
                x, y, x + barW, y + 3.5f,
                2f, 2f,
                peakPaint
            )
        }
    }
}

// ── Style 2: Material 3 Expressive Multi-Band Waveform Renderer ──────────────

private fun DrawScope.drawExpressiveMultiBandWaveform(
    bars: FloatArray,
    numBars: Int,
    phase: Float,
    breathe: Float,
    rmsLevel: Float,
    primary: Color,
    tertiary: Color,
    secondary: Color,
    primaryContainer: Color,
    tertiaryContainer: Color,
    secondaryContainer: Color,
    isOutlined: Boolean
) {
    val w = size.width
    val h = size.height
    val blurPx = if (isOutlined) 4f else 76f

    val alpha = (0.25f + rmsLevel * 0.35f).coerceIn(0.25f, 0.70f)

    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas

        // 1. Low-End / Bass Layer
        val lowBandAvg = (0 until 8).map { bars[it] }.average().toFloat().coerceIn(0f, 1f)
        val pathLow = Path()
        val pointsLow = Array(numBars) { i ->
            val x = i.toFloat() / (numBars - 1) * w
            val barVal = bars[i % 8].coerceIn(0f, 1f)
            val phaseOffset = i.toFloat() / numBars * 2f * PI.toFloat()
            val waveOsc = 0.5f + 0.5f * sin(phase * 0.8f + phaseOffset)
            val amp = (0.08f + barVal * 0.92f) * (0.20f + 0.80f * lowBandAvg) * (0.85f + 0.30f * waveOsc) * breathe
            val y = h - (h * 0.08f + h * 0.62f * amp)
            PointF(x, y)
        }

        if (isOutlined) {
            buildCubicBezierCurveOnlyPath(pathLow, pointsLow)
        } else {
            buildCubicBezierPath(pathLow, pointsLow, w, h, blurPx)
        }

        val paintLow = android.graphics.Paint().apply {
            isAntiAlias = true
            color = if (isOutlined) primary.copy(alpha = 0.95f).toArgb() else primaryContainer.copy(alpha = alpha * 0.85f).toArgb()
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            if (isOutlined) {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 7f
            } else {
                style = android.graphics.Paint.Style.FILL
            }
        }
        nativeCanvas.drawPath(pathLow, paintLow)

        // 2. Mid-Range Vocal Layer
        val midBandAvg = (8 until 16).map { bars[it] }.average().toFloat().coerceIn(0f, 1f)
        val pathMid = Path()
        val pointsMid = Array(numBars) { i ->
            val x = i.toFloat() / (numBars - 1) * w
            val barVal = bars[8 + (i % 8)].coerceIn(0f, 1f)
            val phaseOffset = (i.toFloat() / numBars + 0.33f) * 2f * PI.toFloat()
            val waveOsc = 0.5f + 0.5f * sin(phase * 1.1f + phaseOffset)
            val amp = (0.06f + barVal * 0.94f) * (0.18f + 0.82f * midBandAvg) * (0.80f + 0.40f * waveOsc) * breathe
            val y = h - (h * 0.06f + h * 0.50f * amp)
            PointF(x, y)
        }

        if (isOutlined) {
            buildCubicBezierCurveOnlyPath(pathMid, pointsMid)
        } else {
            buildCubicBezierPath(pathMid, pointsMid, w, h, blurPx)
        }

        val paintMid = android.graphics.Paint().apply {
            isAntiAlias = true
            color = if (isOutlined) tertiary.copy(alpha = 0.90f).toArgb() else tertiaryContainer.copy(alpha = alpha * 0.75f).toArgb()
            maskFilter = BlurMaskFilter(if (isOutlined) 3.5f else blurPx * 0.9f, BlurMaskFilter.Blur.NORMAL)
            if (isOutlined) {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 6f
            } else {
                style = android.graphics.Paint.Style.FILL
            }
        }
        nativeCanvas.drawPath(pathMid, paintMid)

        // 3. High-End Shimmer Layer
        val highBandAvg = (16 until 24).map { bars[it] }.average().toFloat().coerceIn(0f, 1f)
        val pathHigh = Path()
        val pointsHigh = Array(numBars) { i ->
            val x = i.toFloat() / (numBars - 1) * w
            val barVal = bars[16 + (i % 8)].coerceIn(0f, 1f)
            val phaseOffset = (i.toFloat() / numBars + 0.66f) * 2f * PI.toFloat()
            val waveOsc = 0.5f + 0.5f * sin(phase * 1.4f + phaseOffset)
            val amp = (0.04f + barVal * 0.96f) * (0.15f + 0.85f * highBandAvg) * (0.75f + 0.50f * waveOsc) * breathe
            val y = h - (h * 0.04f + h * 0.38f * amp)
            PointF(x, y)
        }

        if (isOutlined) {
            buildCubicBezierCurveOnlyPath(pathHigh, pointsHigh)
        } else {
            buildCubicBezierPath(pathHigh, pointsHigh, w, h, blurPx)
        }

        val paintHigh = android.graphics.Paint().apply {
            isAntiAlias = true
            color = if (isOutlined) secondary.copy(alpha = 0.85f).toArgb() else secondaryContainer.copy(alpha = alpha * 0.65f).toArgb()
            maskFilter = BlurMaskFilter(if (isOutlined) 3f else blurPx * 0.8f, BlurMaskFilter.Blur.NORMAL)
            if (isOutlined) {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 5f
            } else {
                style = android.graphics.Paint.Style.FILL
            }
        }
        nativeCanvas.drawPath(pathHigh, paintHigh)
    }
}

private fun buildCubicBezierPath(
    path: Path,
    points: Array<PointF>,
    width: Float,
    height: Float,
    blurPx: Float
) {
    path.reset()
    if (points.isEmpty()) return

    path.moveTo(0f, height + blurPx)
    path.lineTo(points[0].x, points[0].y)

    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val controlX1 = p0.x + (p1.x - p0.x) / 2f
        val controlY1 = p0.y
        val controlX2 = p0.x + (p1.x - p0.x) / 2f
        val controlY2 = p1.y
        path.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
    }

    path.lineTo(width, points.last().y)
    path.lineTo(width, height + blurPx)
    path.close()
}

private fun buildCubicBezierCurveOnlyPath(
    path: Path,
    points: Array<PointF>
) {
    path.reset()
    if (points.isEmpty()) return

    path.moveTo(points[0].x, points[0].y)

    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val controlX1 = p0.x + (p1.x - p0.x) / 2f
        val controlY1 = p0.y
        val controlX2 = p0.x + (p1.x - p0.x) / 2f
        val controlY2 = p1.y
        path.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
    }
}
