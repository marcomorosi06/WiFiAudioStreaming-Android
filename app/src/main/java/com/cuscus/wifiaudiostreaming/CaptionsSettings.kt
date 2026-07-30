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

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AndroidCaptionSettings(
    val showOverlay: Boolean = true,
    val textSizeSp: Float = 22f,
    val bottomMarginDp: Float = 48f,
    val engine: AsrEngineKind = AsrEngineKind.SHERPA_ONNX,
    val modelId: String = ""
)

object AndroidCaptionPreferences {
    private const val FILE = "captions"
    private const val OVERLAY = "overlay_enabled"
    private const val SIZE = "overlay_text_size"
    private const val MARGIN = "overlay_margin"
    private const val ENGINE = "engine"
    private const val MODEL = "model"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): AndroidCaptionSettings {
        val p = prefs(context)
        return AndroidCaptionSettings(
            showOverlay = p.getBoolean(OVERLAY, true),
            textSizeSp = p.getFloat(SIZE, 22f),
            bottomMarginDp = p.getFloat(MARGIN, 48f),
            engine = runCatching {
                AsrEngineKind.valueOf(p.getString(ENGINE, null) ?: "SHERPA_ONNX")
            }.getOrDefault(AsrEngineKind.SHERPA_ONNX),
            modelId = p.getString(MODEL, "") ?: ""
        )
    }

    fun save(context: Context, s: AndroidCaptionSettings) {
        prefs(context).edit()
            .putBoolean(OVERLAY, s.showOverlay)
            .putFloat(SIZE, s.textSizeSp)
            .putFloat(MARGIN, s.bottomMarginDp)
            .putString(ENGINE, s.engine.name)
            .putString(MODEL, s.modelId)
            .apply()
        CaptionOverlayService.setStyle(s.textSizeSp, s.bottomMarginDp)
    }
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format("%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format("%.0f MB", bytes / 1_048_576.0)
    else -> String.format("%.0f kB", bytes / 1024.0)
}

@Composable
fun CaptionsSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(AndroidCaptionPreferences.load(context)) }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<CaptionModel?>(null) }

    val downloader = remember { CaptionDownloader(CaptionSupport.dataRoot(context)) }
    val engines = remember { CaptionSupport.availableEngines() }

    val canOverlay = remember(refreshTick) { CaptionOverlayService.canDrawOverlays(context) }

    fun persist(next: AndroidCaptionSettings) {
        settings = next
        AndroidCaptionPreferences.save(context, next)
    }

    SettingsGroupCard(
        title = stringResource(R.string.captions_title),
        icon = Icons.Outlined.Subtitles
    ) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text(
                stringResource(R.string.captions_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.captions_overlay_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.captions_overlay_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showOverlay && canOverlay,
                    enabled = canOverlay,
                    onCheckedChange = { persist(settings.copy(showOverlay = it)) }
                )
            }

            if (!canOverlay) {
                Text(
                    stringResource(R.string.captions_overlay_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = {
                    runCatching { context.startActivity(CaptionOverlayService.permissionIntent(context)) }
                    refreshTick++
                }) {
                    Text(stringResource(R.string.captions_overlay_grant))
                }
            }

            if (settings.showOverlay && canOverlay) {
                Text(
                    stringResource(R.string.captions_overlay_text_size),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.textSizeSp,
                    valueRange = 12f..48f,
                    onValueChange = { persist(settings.copy(textSizeSp = it)) }
                )
                Text(
                    stringResource(R.string.captions_overlay_margin),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.bottomMarginDp,
                    valueRange = 0f..300f,
                    onValueChange = { persist(settings.copy(bottomMarginDp = it)) }
                )
            }

            HorizontalDivider()

            if (engines.isEmpty()) {
                Text(
                    stringResource(R.string.captions_render_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text(
                stringResource(R.string.captions_engine_title),
                style = MaterialTheme.typography.titleSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (kind in engines) {
                    val labelRes = if (kind == AsrEngineKind.WHISPER_CPP)
                        R.string.captions_engine_whisper else R.string.captions_engine_sherpa
                    FilterChip(
                        selected = settings.engine == kind,
                        onClick = { persist(settings.copy(engine = kind, modelId = "")) },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }
            Text(
                stringResource(
                    if (settings.engine == AsrEngineKind.WHISPER_CPP)
                        R.string.captions_engine_whisper_desc
                    else R.string.captions_engine_sherpa_desc
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                stringResource(R.string.captions_model_title),
                style = MaterialTheme.typography.titleSmall
            )

            val models = remember(settings.engine) { CaptionSupport.availableModels(settings.engine) }
            if (models.isEmpty()) {
                Text(
                    stringResource(R.string.captions_model_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            for (model in models) {
                val installed = remember(model, refreshTick) { downloader.isModelInstalled(model) }
                val fits = remember(model) { CaptionSupport.fitsInMemory(context, model) }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            model.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (settings.modelId == model.id)
                                FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            stringResource(
                                R.string.captions_model_size,
                                humanBytes(model.sizeBytes),
                                model.approxRamMb
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    if (installed) {
                        OutlinedButton(
                            enabled = busy == null,
                            onClick = { pendingDelete = model }
                        ) { Text(stringResource(R.string.captions_model_delete)) }
                    } else {
                        Button(
                            enabled = busy == null,
                            onClick = {
                                scope.launch {
                                    busy = model.id
                                    message = null
                                    val res = withContext(Dispatchers.IO) {
                                        downloader.downloadModel(model) { progress = it }
                                    }
                                    busy = null
                                    progress = null
                                    refreshTick++
                                    when (res) {
                                        is DownloadResult.Done -> persist(settings.copy(modelId = model.id))
                                        is DownloadResult.Failed -> message =
                                            if (res.reason == CaptionDownloader.ERR_NO_PIN)
                                                context.getString(R.string.captions_download_unpinned)
                                            else
                                                context.getString(R.string.captions_download_failed, res.reason)
                                        DownloadResult.Cancelled -> Unit
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.captions_model_download)) }
                    }
                }
                HorizontalDivider()
            }

            if (busy != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(message ?: "", style = MaterialTheme.typography.bodySmall)
                }
                progress?.let { p ->
                    LinearProgressIndicator(
                        progress = { p.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(
                            R.string.captions_download_progress,
                            (p.fraction * 100).toInt(),
                            humanBytes(p.bytesTotal)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (message != null) {
                Text(
                    message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.captions_model_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.captions_model_delete_body,
                        target.displayName,
                        humanBytes(target.sizeBytes)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    message = if (downloader.removeModel(target)) {
                        if (settings.modelId == target.id) persist(settings.copy(modelId = ""))
                        null
                    } else {
                        context.getString(R.string.captions_delete_failed)
                    }
                    refreshTick++
                }) { Text(stringResource(R.string.captions_delete_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.captions_delete_cancel))
                }
            }
        )
    }
}
