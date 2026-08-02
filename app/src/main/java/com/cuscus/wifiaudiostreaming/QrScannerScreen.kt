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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.*
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object QrCameraSupport {
    fun hasCamera(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
}

private enum class ScanFeedback { None, NotWfas, Expired }

private const val REJECT_COOLDOWN_MS = 2800L

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberAppHaptics()
    val currentOnScanned by rememberUpdatedState(onScanned)

    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    var accepted by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf(ScanFeedback.None) }

    val acceptedFlag = remember { AtomicBoolean(false) }
    val rejectUntil = remember { AtomicLong(0L) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val boundProvider = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val activeScanner = remember { AtomicReference<BarcodeScanner?>(null) }

    val handleRaw: (String) -> Unit = remember {
        { raw ->
            if (!acceptedFlag.get()) {
                if (WfasPairingUri.parse(raw) != null) {
                    acceptedFlag.set(true)
                    accepted = raw
                } else if (System.currentTimeMillis() >= rejectUntil.get()) {
                    rejectUntil.set(System.currentTimeMillis() + REJECT_COOLDOWN_MS)
                    feedback =
                        if (WfasPairingUri.isExpiredUri(raw)) ScanFeedback.Expired
                        else ScanFeedback.NotWfas
                }
            }
        }
    }

    LaunchedEffect(feedback) {
        if (feedback == ScanFeedback.None) return@LaunchedEffect
        haptics.reject()
        delay(130)
        haptics.tick()
        delay(REJECT_COOLDOWN_MS)
        feedback = ScanFeedback.None
    }

    val lockProgress = remember { Animatable(0f) }
    LaunchedEffect(accepted) {
        val raw = accepted ?: return@LaunchedEffect
        haptics.confirm()
        launch {
            delay(80); haptics.tick()
            delay(60); haptics.tick()
            delay(60); haptics.tick()
        }
        lockProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        delay(170)
        haptics.gestureEnd()
        currentOnScanned(raw)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { boundProvider.getAndSet(null)?.unbindAll() }
            runCatching { activeScanner.getAndSet(null)?.close() }
            analysisExecutor.shutdown()
        }
    }

    DisposableEffect(torchOn, cameraControl) {
        cameraControl?.enableTorch(torchOn)
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = runCatching { providerFuture.get() }.getOrNull()
                        ?: return@addListener

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    val scanner = BarcodeScanning.getClient(options)
                    activeScanner.set(scanner)

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analysisExecutor) { proxy ->
                        val mediaImage = proxy.image
                        if (mediaImage == null || acceptedFlag.get()) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            proxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                if (raw != null) {
                                    ContextCompat.getMainExecutor(ctx).execute { handleRaw(raw) }
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }

                    val selectors = listOf(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    )
                    provider.unbindAll()
                    for (selector in selectors) {
                        val bound = runCatching {
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                        }.getOrNull()
                        if (bound != null) {
                            boundProvider.set(provider)
                            cameraControl = bound.cameraControl
                            torchAvailable = bound.cameraInfo.hasFlashUnit()
                            break
                        }
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        ScannerReticle(
            lock = lockProgress.value,
            rejecting = feedback != ScanFeedback.None
        )

        SuccessBadge(progress = lockProgress.value)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    haptics.tap()
                    onClose()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.qr_scan_close))
            }

            Spacer(Modifier.weight(1f))

            if (torchAvailable) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.toggle(!torchOn)
                        torchOn = !torchOn
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (torchOn) Color.White.copy(alpha = 0.9f)
                        else Color.Black.copy(alpha = 0.45f),
                        contentColor = if (torchOn) Color.Black else Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Outlined.FlashlightOn
                        else Icons.Outlined.FlashlightOff,
                        contentDescription = stringResource(R.string.qr_scan_torch)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = feedback != ScanFeedback.None,
                enter = slideInVertically { it / 2 } + fadeIn(tween(180)),
                exit = slideOutVertically { it / 2 } + fadeOut(tween(220))
            ) {
                ScanFeedbackBanner(feedback)
            }

            AnimatedVisibility(
                visible = accepted == null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 22.dp, vertical = 16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.qr_scan_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.qr_scan_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanFeedbackBanner(feedback: ScanFeedback) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(feedback) {
        if (feedback == ScanFeedback.None) return@LaunchedEffect
        repeat(2) {
            shake.animateTo(10f, tween(55, easing = FastOutSlowInEasing))
            shake.animateTo(-10f, tween(55, easing = FastOutSlowInEasing))
        }
        shake.animateTo(0f, tween(55, easing = FastOutSlowInEasing))
    }

    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Clover4Leaf) }
    val wobble by rememberInfiniteTransition(label = "ScanBannerWobble").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScanBannerWobbleValue"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value }
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(MorphOutlineShape(morph, wobble))
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SentimentDissatisfied,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onError
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(
                if (feedback == ScanFeedback.Expired) R.string.qr_scan_expired_hint
                else R.string.qr_scan_not_wfas
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun SuccessBadge(progress: Float) {
    if (progress <= 0.01f) return

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        val s = 0.4f + 0.6f * progress
                        scaleX = s
                        scaleY = s
                        rotationZ = (1f - progress) * -70f
                        alpha = progress
                    },
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.qr_scan_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.graphicsLayer { alpha = progress }
            )
        }
    }
}

private fun shapePath(
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
private fun ScannerReticle(lock: Float, rejecting: Boolean) {
    val infinite = rememberInfiniteTransition(label = "Reticle")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ReticleSweep"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ReticleBreathe"
    )
    val drift by infinite.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ReticleDrift"
    )

    val accent = MaterialTheme.colorScheme.primary
    val alarm = MaterialTheme.colorScheme.error
    val frame = if (rejecting) alarm else accent

    val holeMorph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie12Sided) }
    val haloMorph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Flower) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val base = kotlin.math.min(size.width, size.height) * 0.70f
        val side = base * (if (lock > 0f) 1f - 0.08f * lock else breathe)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val stroke = (4f + 3f * lock).dp.toPx()

        val hole = shapePath(holeMorph, lock, cx, cy, side, drift * (1f - lock))

        val scrimPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addPath(hole)
        }
        drawPath(scrimPath, Color.Black.copy(alpha = 0.52f + 0.30f * lock))

        if (lock > 0.02f) {
            val halo = shapePath(haloMorph, lock, cx, cy, side * 1.16f, -drift)
            drawPath(halo, frame.copy(alpha = 0.14f * lock))
            drawPath(hole, frame.copy(alpha = 0.92f * lock))
        }

        if (lock < 0.98f && !rejecting) {
            clipPath(hole) {
                val bandH = side * 0.34f
                val y = (cy - side / 2f) + side * sweep - bandH / 2f
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to accent.copy(alpha = 0f),
                        0.5f to accent.copy(alpha = 0.30f * (1f - lock)),
                        1f to accent.copy(alpha = 0f)
                    ),
                    topLeft = Offset(cx - side / 2f, y),
                    size = Size(side, bandH)
                )
            }
        }

        drawPath(
            path = hole,
            color = frame.copy(alpha = if (rejecting) 1f else 0.85f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
