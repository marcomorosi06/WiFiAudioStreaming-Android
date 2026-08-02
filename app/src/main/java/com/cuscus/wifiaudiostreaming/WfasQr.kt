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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.graphics.shapes.*
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

class QrMatrix(val size: Int, private val cells: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean =
        if (x < 0 || y < 0 || x >= size || y >= size) false else cells[y * size + x]

    companion object {
        fun encode(content: String): QrMatrix? {
            if (content.isBlank()) return null
            return runCatching {
                val hints = mapOf(
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
                )
                val matrix = Encoder.encode(content, ErrorCorrectionLevel.M, hints).matrix
                    ?: return@runCatching null
                val n = matrix.width
                val cells = BooleanArray(n * n)
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        cells[y * n + x] = matrix.get(x, y).toInt() == 1
                    }
                }
                QrMatrix(n, cells)
            }.getOrNull()
        }
    }
}

private fun QrMatrix.isFinder(x: Int, y: Int): Boolean {
    val edge = size - 7
    return (x < 7 && y < 7) || (x >= edge && y < 7) || (x < 7 && y >= edge)
}

private fun QrMatrix.isTiming(x: Int, y: Int): Boolean = x == 6 || y == 6

private fun cellHash(x: Int, y: Int): Int =
    abs((x * 73856093) xor (y * 19349663) xor ((x + y) * 83492791))

fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = max(la, lb)
    val lo = kotlin.math.min(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

fun clampForContrast(color: Color, against: Color, minRatio: Float = 5f): Color {
    var out = color
    var guard = 0
    while (contrastRatio(out, against) < minRatio && guard < 40) {
        out = lerp(out, Color.Black, 0.07f)
        guard++
    }
    return out
}

private const val ROTATION_STEPS = 12

private fun spriteAtlas(shapes: List<RoundedPolygon>, module: Float): List<Path> {
    val out = ArrayList<Path>(shapes.size * ROTATION_STEPS)
    for (poly in shapes) {
        val base = Morph(poly, poly).toPath(0f).asComposePath()
        val b = base.getBounds()
        if (b.width <= 0f || b.height <= 0f) {
            repeat(ROTATION_STEPS) { out.add(Path()) }
            continue
        }
        val scale = module / max(b.width, b.height)
        for (step in 0 until ROTATION_STEPS) {
            val p = Path().apply { addPath(base) }
            val m = Matrix()
            m.translate(module / 2f, module / 2f)
            m.rotateZ(360f * step / ROTATION_STEPS)
            m.scale(scale, scale)
            m.translate(-b.left - b.width / 2f, -b.top - b.height / 2f)
            p.transform(m)
            out.add(p)
        }
    }
    return out
}

private fun DrawScope.drawAnchorPlain(
    originX: Float,
    originY: Float,
    module: Float,
    color: Color
) {
    drawRect(
        color = color,
        topLeft = Offset(originX + module / 2f, originY + module / 2f),
        size = Size(module * 6f, module * 6f),
        style = Stroke(width = module)
    )
    drawRect(
        color = color,
        topLeft = Offset(originX + module * 2f, originY + module * 2f),
        size = Size(module * 3f, module * 3f)
    )
}

private fun DrawScope.drawAnchorExpressive(
    originX: Float,
    originY: Float,
    module: Float,
    ringColor: Color,
    eyeColor: Color,
    eyePath: Path?
) {
    val outer = module * 7f
    drawRoundRect(
        color = ringColor,
        topLeft = Offset(originX + module / 2f, originY + module / 2f),
        size = Size(outer - module, outer - module),
        cornerRadius = CornerRadius(module * 1.9f, module * 1.9f),
        style = Stroke(width = module)
    )
    if (eyePath != null) {
        translate(originX + module * 2f, originY + module * 2f) {
            drawPath(eyePath, eyeColor)
        }
    } else {
        drawRoundRect(
            color = eyeColor,
            topLeft = Offset(originX + module * 2f, originY + module * 2f),
            size = Size(module * 3f, module * 3f),
            cornerRadius = CornerRadius(module * 0.9f, module * 0.9f)
        )
    }
}

@Composable
fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier,
    shapes: List<RoundedPolygon>,
    palette: List<Color>,
    ringColor: Color,
    eyeColor: Color,
    eyeShape: RoundedPolygon? = null,
    plateColor: Color = Color.White,
    plain: Boolean = false,
    minContrast: Float = 5f,
    animated: Boolean = true,
    quietZoneModules: Int = 0
) {
    val matrix = remember(content) { QrMatrix.encode(content) }

    val inks = remember(palette, plateColor, minContrast) {
        palette.map { clampForContrast(it, plateColor, minContrast) }
            .ifEmpty { listOf(Color.Black) }
    }
    val ring = remember(ringColor, plateColor, minContrast) {
        clampForContrast(ringColor, plateColor, minContrast + 1f)
    }
    val eye = remember(eyeColor, plateColor, minContrast) {
        clampForContrast(eyeColor, plateColor, minContrast + 1f)
    }

    val still = LocalInspectionMode.current || !animated || plain
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        while (true) {
            delay(90)
            phase = (phase + 1) % (ROTATION_STEPS * 64)
        }
    }

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        if (matrix == null) return@BoxWithConstraints

        val total = matrix.size + quietZoneModules * 2
        val sidePx = with(LocalDensity.current) {
            kotlin.math.min(maxWidth.toPx(), maxHeight.toPx())
        }
        val moduleRef = sidePx / total
        val atlas = remember(shapes, moduleRef) { spriteAtlas(shapes, moduleRef) }
        val eyeAtlas = remember(eyeShape, moduleRef) {
            if (eyeShape == null) null else spriteAtlas(listOf(eyeShape), moduleRef * 3f).firstOrNull()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val module = kotlin.math.min(size.width, size.height) / total
            val originX = (size.width - module * total) / 2f + module * quietZoneModules
            val originY = (size.height - module * total) / 2f + module * quietZoneModules
            val edge = (matrix.size - 7) * module

            if (plain) {
                drawRect(color = Color.White, topLeft = Offset.Zero, size = size)
                for (y in 0 until matrix.size) {
                    for (x in 0 until matrix.size) {
                        if (!matrix[x, y] || matrix.isFinder(x, y)) continue
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(originX + x * module, originY + y * module),
                            size = Size(module, module)
                        )
                    }
                }
                drawAnchorPlain(originX, originY, module, Color.Black)
                drawAnchorPlain(originX + edge, originY, module, Color.Black)
                drawAnchorPlain(originX, originY + edge, module, Color.Black)
                return@Canvas
            }

            val shapeCount = if (shapes.isEmpty() || atlas.isEmpty()) 0 else shapes.size

            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (!matrix[x, y]) continue
                    if (matrix.isFinder(x, y)) continue

                    val px = originX + x * module
                    val py = originY + y * module

                    if (matrix.isTiming(x, y) || shapeCount == 0) {
                        drawCircle(
                            color = ring,
                            radius = module * 0.46f,
                            center = Offset(px + module / 2f, py + module / 2f)
                        )
                        continue
                    }

                    val h = cellHash(x, y)
                    val shapeIndex = h % shapeCount
                    val step = ((h / 7) + phase + (x + y) * 2) % ROTATION_STEPS
                    val path = atlas[shapeIndex * ROTATION_STEPS + step]
                    val color = inks[(h / 13) % inks.size]

                    translate(px, py) { drawPath(path, color) }
                }
            }

            drawAnchorExpressive(originX, originY, module, ring, eye, eyeAtlas)
            drawAnchorExpressive(originX + edge, originY, module, ring, eye, eyeAtlas)
            drawAnchorExpressive(originX, originY + edge, module, ring, eye, eyeAtlas)
        }
    }
}
