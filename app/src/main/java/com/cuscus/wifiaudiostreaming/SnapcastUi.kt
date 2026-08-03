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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.data.SettingsDataStore
import com.cuscus.wifiaudiostreaming.snapcast.SnapcastClientView
import com.cuscus.wifiaudiostreaming.snapcast.SnapcastCodecs
import com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults
import kotlinx.coroutines.launch

@Composable
fun SnapcastSettingsSection(appSettings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SettingsDataStore(context.applicationContext) }
    val session by NetworkManager.snapcastSession.collectAsState()

    fun persist(
        port: Int = appSettings.snapcastPort,
        controlPort: Int = appSettings.snapcastControlPort,
        codec: String = appSettings.snapcastCodec,
        chunkMs: Int = appSettings.snapcastChunkMs,
        bufferMs: Int = appSettings.snapcastBufferMs,
        streamName: String = appSettings.snapcastStreamName
    ) {
        scope.launch { store.saveSnapcastSettings(port, controlPort, codec, chunkMs, bufferMs, streamName) }
    }

    SettingsSwitchItem(
        title = stringResource(R.string.settings_item_snapcast_title),
        description = stringResource(R.string.settings_item_snapcast_desc),
        icon = Icons.Outlined.Speaker,
        isChecked = appSettings.snapcastEnabled,
        onCheckedChange = { enabled ->
            scope.launch {
                store.saveSnapcastEnabled(enabled)
                if (enabled) DlnaMulticastLock.install(context.applicationContext)
            }
        }
    )

    AnimatedVisibility(visible = appSettings.snapcastEnabled) {
        Column {
            SettingsTextFieldItem(
                title = stringResource(R.string.settings_item_snapcast_port_title),
                description = stringResource(R.string.settings_item_snapcast_port_desc),
                icon = Icons.Outlined.VpnKey,
                value = appSettings.snapcastPort.toString(),
                onValueChange = { text -> text.toIntOrNull()?.let { persist(port = it) } }
            )

            SettingsTextFieldItem(
                title = stringResource(R.string.settings_item_snapcast_control_port_title),
                description = stringResource(R.string.settings_item_snapcast_control_port_desc),
                icon = Icons.Outlined.VpnKey,
                value = appSettings.snapcastControlPort.toString(),
                onValueChange = { text -> text.toIntOrNull()?.let { persist(controlPort = it) } }
            )

            SettingsChoiceItem(
                title = stringResource(R.string.settings_item_snapcast_codec_title),
                description = stringResource(R.string.settings_item_snapcast_codec_desc),
                icon = Icons.Outlined.GraphicEq,
                options = SnapcastCodecs.ALL.map { codec ->
                    ChoiceOption(Icons.Outlined.GraphicEq, codec.uppercase(), codec)
                },
                selectedValue = appSettings.snapcastCodec,
                selectedDescription = when (appSettings.snapcastCodec) {
                    SnapcastCodecs.FLAC -> stringResource(R.string.snapcast_codec_flac_desc)
                    SnapcastCodecs.OPUS -> stringResource(R.string.snapcast_codec_opus_desc)
                    else -> stringResource(R.string.snapcast_codec_pcm_desc)
                },
                onSelect = { persist(codec = it) }
            )

            if (appSettings.snapcastCodec == SnapcastCodecs.OPUS &&
                (appSettings.sampleRate != 48000 || appSettings.channelConfig != "STEREO")
            ) {
                SettingsInfoItem(
                    title = stringResource(R.string.snapcast_opus_requirement_title),
                    description = stringResource(R.string.snapcast_opus_requirement),
                    icon = Icons.Outlined.WarningAmber
                )
            }

            SettingsChoiceItem(
                title = stringResource(R.string.settings_item_snapcast_chunk_title),
                description = stringResource(R.string.settings_item_snapcast_chunk_desc),
                icon = Icons.Outlined.Timer,
                options = SnapcastDefaults.CHUNK_CHOICES.map { value ->
                    ChoiceOption(Icons.Outlined.Timer, "$value ms", value.toString())
                },
                selectedValue = appSettings.snapcastChunkMs.toString(),
                selectedDescription = stringResource(
                    R.string.snapcast_chunk_selected,
                    appSettings.snapcastChunkMs
                ),
                onSelect = { value -> value.toIntOrNull()?.let { persist(chunkMs = it) } }
            )

            SettingsTextFieldItem(
                title = stringResource(R.string.settings_item_snapcast_buffer_title),
                description = stringResource(R.string.settings_item_snapcast_buffer_desc),
                icon = Icons.Outlined.Timer,
                value = appSettings.snapcastBufferMs.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let {
                        persist(
                            bufferMs = it.coerceIn(
                                SnapcastDefaults.MIN_BUFFER_MS,
                                SnapcastDefaults.MAX_BUFFER_MS
                            )
                        )
                    }
                }
            )

            SettingsTextFieldItem(
                title = stringResource(R.string.settings_item_snapcast_name_title),
                description = stringResource(R.string.settings_item_snapcast_name_desc),
                icon = Icons.Outlined.Speaker,
                value = appSettings.snapcastStreamName,
                onValueChange = { text -> persist(streamName = text.trim().ifBlank { SnapcastDefaults.STREAM_NAME }) }
            )

            if (session.running) {
                if (session.lastError.isNotBlank()) {
                    SettingsInfoItem(
                        title = stringResource(R.string.snapcast_codec_fallback_title),
                        description = stringResource(
                            R.string.snapcast_codec_fallback,
                            session.lastError,
                            session.codec
                        ),
                        icon = Icons.Outlined.WarningAmber
                    )
                }
                Text(
                    text = stringResource(
                        R.string.snapcast_live_summary,
                        session.codec,
                        session.sampleFormat,
                        session.clients.count { it.connected },
                        session.controlConnections
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                session.clients.forEach { client -> SnapcastClientRow(client) }
            } else {
                SettingsInfoItem(
                    title = stringResource(R.string.snapcast_hint_title),
                    description = stringResource(R.string.snapcast_hint),
                    icon = Icons.Outlined.Speaker
                )
            }
        }
    }
}

@Composable
private fun SnapcastClientRow(client: SnapcastClientView) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (client.connected) 0.6f else 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (client.muted) Icons.Outlined.VolumeOff else Icons.Outlined.Speaker,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.snapcast_client_detail,
                        client.ip,
                        client.volumePercent,
                        client.latency
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (client.connected) stringResource(R.string.snapcast_client_online)
                else stringResource(R.string.snapcast_client_offline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExpressiveSnapcastBanner(ip: String) {
    val session by NetworkManager.snapcastSession.collectAsState()
    val connected = session.clients.count { it.connected }

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
                        imageVector = Icons.Outlined.Speaker,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.snapcast_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.snapcast_card_connections, connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }
            }

            SnapcastBannerRow(
                label = stringResource(R.string.snapcast_card_endpoint),
                value = if (session.streamBound) "$ip:${session.streamPort}"
                else stringResource(R.string.snapcast_port_blocked, session.streamPort),
                ok = session.streamBound
            )
            SnapcastBannerRow(
                label = stringResource(R.string.snapcast_card_control),
                value = if (session.controlBound) "$ip:${session.controlPort}"
                else stringResource(R.string.snapcast_port_blocked, session.controlPort),
                ok = session.controlBound
            )
            SnapcastBannerRow(
                label = stringResource(R.string.snapcast_card_discovery),
                value = if (session.discoveryActive) stringResource(R.string.snapcast_discovery_on)
                else stringResource(R.string.snapcast_discovery_off),
                ok = session.discoveryActive
            )
            SnapcastBannerRow(
                label = stringResource(R.string.snapcast_card_codec),
                value = "${session.codec} ${session.sampleFormat}"
            )

            if (session.bindError.isNotBlank()) {
                Text(
                    text = stringResource(R.string.snapcast_bind_error, session.bindError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = stringResource(R.string.snapcast_how_to_connect),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = stringResource(
                    if (session.discoveryActive) R.string.snapcast_how_to_connect_auto
                    else R.string.snapcast_how_to_connect_manual
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "snapclient -h $ip -p ${session.streamPort}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            if (connected == 0) {
                Text(
                    text = stringResource(R.string.snapcast_card_no_clients),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            } else {
                session.clients.filter { it.connected }.forEach { client ->
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
                                        if (client.muted) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                            Text(
                                text = client.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${client.volumePercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapcastBannerRow(label: String, value: String, ok: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!ok) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
