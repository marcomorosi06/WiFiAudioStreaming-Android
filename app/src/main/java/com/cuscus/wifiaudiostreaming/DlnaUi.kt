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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.data.SettingsDataStore
import kotlinx.coroutines.launch

@Composable
fun DlnaSettingsSection(appSettings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SettingsDataStore(context.applicationContext) }
    val discovery by DlnaDiscoveryService.flow.collectAsState()
    val targets by NetworkManager.dlnaTargets.collectAsState()

    LaunchedEffect(appSettings.dlnaEnabled) {
        if (appSettings.dlnaEnabled) {
            DlnaMulticastLock.install(context.applicationContext)
            if (discovery.renderers.isEmpty()) {
                DlnaDiscoveryService.scan(NetworkManager.getWifiNetworkInterface(appSettings.networkInterface))
            }
        }
    }

    SettingsSwitchItem(
        title = stringResource(R.string.settings_item_dlna_title),
        description = stringResource(R.string.settings_item_dlna_desc),
        icon = Icons.Outlined.Cast,
        isChecked = appSettings.dlnaEnabled,
        onCheckedChange = { enabled -> scope.launch { store.saveDlnaEnabled(enabled) } }
    )

    AnimatedVisibility(visible = appSettings.dlnaEnabled) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsTextFieldItem(
                title = stringResource(R.string.settings_item_dlna_port_title),
                description = stringResource(R.string.settings_item_dlna_port_desc),
                icon = Icons.Outlined.VpnKey,
                value = appSettings.dlnaPort.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { port ->
                        scope.launch { store.saveDlnaSettings(port, appSettings.dlnaFormat) }
                    }
                }
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.dlna_format_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                val availableFormats = remember(appSettings.sampleRate, appSettings.channelConfig) {
                    val channels = if (appSettings.channelConfig == "STEREO") 2 else 1
                    DlnaCodecSupport.available(appSettings.sampleRate, channels)
                }
                val options = remember(availableFormats) {
                    listOf(DlnaFormatPreference.AUTO) +
                            DlnaFormatPreference.entries.filter { it.codec()?.let(availableFormats::contains) == true }
                }
                options.forEach { option ->
                    val selected = DlnaFormatPreference.fromId(appSettings.dlnaFormat) == option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clickable {
                                scope.launch { store.saveDlnaSettings(appSettings.dlnaPort, option.id) }
                            }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = dlnaFormatLabel(option),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (selected) {
                                Text(
                                    text = dlnaFormatDescription(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dlna_devices_label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (discovery.scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                DlnaDiscoveryService.scan(
                                    NetworkManager.getWifiNetworkInterface(appSettings.networkInterface)
                                )
                            }
                        },
                        enabled = !discovery.scanning
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.dlna_rescan),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                val entries = dlnaVisibleEntries(
                    appSettings.dlnaDevices,
                    discovery.renderers,
                    stringResource(R.string.dlna_offline)
                )

                if (entries.isEmpty()) {
                    Text(
                        text = if (discovery.scanning) stringResource(R.string.dlna_scanning)
                        else stringResource(R.string.dlna_no_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    entries.forEach { entry ->
                        DlnaDeviceRow(
                            entry = entry,
                            checked = DlnaSelection.contains(appSettings.dlnaDevices, entry.udn),
                            status = targets.firstOrNull { it.udn == entry.udn },
                            onToggle = {
                                val updated = if (DlnaSelection.contains(appSettings.dlnaDevices, entry.udn)) {
                                    appSettings.dlnaDevices.filterNot { DlnaSelection.udnOf(it) == entry.udn }
                                } else {
                                    appSettings.dlnaDevices + DlnaSelection.encode(entry.udn, entry.name)
                                }
                                scope.launch { store.saveDlnaDevices(updated) }
                            }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.dlna_selection_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { copyDlnaDiagnostics(context) }) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.dlna_copy_diagnostics),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    if (appSettings.dlnaDevices.isNotEmpty()) {
                        TextButton(onClick = { scope.launch { store.saveDlnaDevices(emptyList()) } }) {
                            Text(stringResource(R.string.dlna_clear_selection))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveDlnaBanner(
    ip: String,
    port: Int,
    formatPreference: DlnaFormatPreference,
    hasSelection: Boolean
) {
    val targets by NetworkManager.dlnaTargets.collectAsState()
    val playing = targets.count { it.status == DlnaTargetStatus.PLAYING }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Cast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dlna_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.dlna_card_connections, playing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }
            }

            DlnaBannerRow(
                label = stringResource(R.string.dlna_card_endpoint),
                value = "http://$ip:$port"
            )
            DlnaBannerRow(
                label = stringResource(R.string.dlna_card_format),
                value = targets.firstOrNull { it.codec != null }?.codec?.label
                    ?: dlnaFormatLabel(formatPreference)
            )

            if (!hasSelection) {
                Text(
                    text = stringResource(R.string.dlna_card_no_targets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            } else {
                targets.forEach { target ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (target.status) {
                                            DlnaTargetStatus.PLAYING -> MaterialTheme.colorScheme.primary
                                            DlnaTargetStatus.ERROR, DlnaTargetStatus.OFFLINE ->
                                                MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = target.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = dlnaBannerStatusLabel(target),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DlnaBannerRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun dlnaBannerStatusLabel(target: DlnaTargetState): String {
    val label = when (target.status) {
        DlnaTargetStatus.PLAYING -> stringResource(R.string.dlna_state_playing)
        DlnaTargetStatus.CONNECTING -> stringResource(R.string.dlna_state_connecting)
        DlnaTargetStatus.RETRYING -> stringResource(R.string.dlna_state_retrying)
        DlnaTargetStatus.ERROR -> stringResource(R.string.dlna_state_error)
        DlnaTargetStatus.OFFLINE -> stringResource(R.string.dlna_offline)
        DlnaTargetStatus.IDLE -> ""
    }
    val codec = target.codec?.label ?: return label
    return "$label · $codec"
}

data class DlnaUiEntry(
    val udn: String,
    val name: String,
    val subtitle: String,
    val online: Boolean
)

private fun dlnaVisibleEntries(
    saved: List<String>,
    discovered: List<DlnaRenderer>,
    offlineLabel: String
): List<DlnaUiEntry> {
    val online = discovered.map { DlnaUiEntry(it.udn, it.displayName, it.subtitle, true) }
    val onlineUdns = online.map { it.udn }.toSet()
    val offline = saved
        .map { DlnaSelection.udnOf(it) to DlnaSelection.nameOf(it) }
        .filter { it.first.isNotBlank() && it.first !in onlineUdns }
        .map { DlnaUiEntry(it.first, it.second, offlineLabel, false) }
    return online + offline
}

private fun copyDlnaDiagnostics(context: Context) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("WFAS DLNA diagnostics", DlnaDiagnostics.report()))
        Toast.makeText(context, R.string.dlna_diagnostics_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DlnaDeviceRow(
    entry: DlnaUiEntry,
    checked: Boolean,
    status: DlnaTargetState?,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = dlnaRowSubtitle(entry, status),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        status?.status == DlnaTargetStatus.PLAYING -> MaterialTheme.colorScheme.primary
                        status?.status == DlnaTargetStatus.ERROR -> MaterialTheme.colorScheme.error
                        !entry.online -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun dlnaRowSubtitle(entry: DlnaUiEntry, status: DlnaTargetState?): String {
    if (status == null) return entry.subtitle
    val label = when (status.status) {
        DlnaTargetStatus.PLAYING -> stringResource(R.string.dlna_state_playing)
        DlnaTargetStatus.CONNECTING -> stringResource(R.string.dlna_state_connecting)
        DlnaTargetStatus.RETRYING -> stringResource(R.string.dlna_state_retrying)
        DlnaTargetStatus.ERROR -> stringResource(R.string.dlna_state_error)
        DlnaTargetStatus.OFFLINE -> stringResource(R.string.dlna_offline)
        DlnaTargetStatus.IDLE -> entry.subtitle
    }
    val codec = status.codec?.label ?: return label
    val suffix = if (!status.negotiated) " (${stringResource(R.string.dlna_fallback)})" else ""
    return "$label · $codec$suffix"
}

@Composable
private fun dlnaFormatLabel(preference: DlnaFormatPreference): String = when (preference) {
    DlnaFormatPreference.AUTO -> stringResource(R.string.dlna_format_auto)
    DlnaFormatPreference.LPCM -> "LPCM 16 bit"
    DlnaFormatPreference.WAV -> "WAV"
    DlnaFormatPreference.MP3 -> "MP3 320 kbps"
    DlnaFormatPreference.ADTS -> "AAC ADTS"
}

@Composable
private fun dlnaFormatDescription(preference: DlnaFormatPreference): String = when (preference) {
    DlnaFormatPreference.AUTO -> stringResource(R.string.dlna_format_auto_desc)
    DlnaFormatPreference.LPCM -> stringResource(R.string.dlna_format_lpcm_desc)
    DlnaFormatPreference.WAV -> stringResource(R.string.dlna_format_wav_desc)
    DlnaFormatPreference.MP3 -> stringResource(R.string.dlna_format_mp3_desc)
    DlnaFormatPreference.ADTS -> stringResource(R.string.dlna_format_adts_desc)
}
