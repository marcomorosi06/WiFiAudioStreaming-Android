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

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

fun ImageVector.toBitmap(context: Context, sizeDp: Float, tintColor: Color = Color.White): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor.toArgb()
        style = Paint.Style.FILL
    }

    val scaleX = sizePx / viewportWidth
    val scaleY = sizePx / viewportHeight
    canvas.save()
    canvas.scale(scaleX, scaleY)
    renderGroup(canvas, root, paint)
    canvas.restore()

    return bitmap
}

private fun renderGroup(canvas: Canvas, group: VectorGroup, paint: Paint) {
    canvas.save()
    for (node in group) {
        when (node) {
            is VectorPath -> {
                val composePath = buildComposePath(node.pathData)
                val androidPath = composePath.asAndroidPath()
                canvas.drawPath(androidPath, paint)
            }
            is VectorGroup -> renderGroup(canvas, node, paint)
        }
    }
    canvas.restore()
}

private fun buildComposePath(nodes: List<PathNode>): androidx.compose.ui.graphics.Path {
    return PathParser().addPathNodes(nodes).toPath()
}

object WidgetKeys {
    val IS_STREAMING = booleanPreferencesKey("is_streaming")
    val IS_SERVER = booleanPreferencesKey("is_server")
    val DISCOVERED_DEVICES = stringPreferencesKey("discovered_devices")
}

suspend fun updateWidgetState(context: Context, isStreaming: Boolean, isServer: Boolean) {
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(ServerWidget::class.java).forEach { id ->
        updateAppWidgetState(context, id) { prefs ->
            prefs[WidgetKeys.IS_STREAMING] = isStreaming
            prefs[WidgetKeys.IS_SERVER] = isServer
        }
        ServerWidget().update(context, id)
    }
    manager.getGlanceIds(ClientWidget::class.java).forEach { id ->
        updateAppWidgetState(context, id) { prefs ->
            prefs[WidgetKeys.IS_STREAMING] = isStreaming
            prefs[WidgetKeys.IS_SERVER] = isServer
        }
        ClientWidget().update(context, id)
    }

    val prefs = context.getSharedPreferences("TileState", Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean("is_streaming", isStreaming)
        .putBoolean("is_server", isServer)
        .apply()

    try {
        android.service.quicksettings.TileService.requestListeningState(context, android.content.ComponentName(context, ServerTileService::class.java))
        android.service.quicksettings.TileService.requestListeningState(context, android.content.ComponentName(context, ClientTileService::class.java))
    } catch (e: Exception) {
        // Ignora: capita se le Tile non sono ancora state aggiunte dall'utente
    }
}

class ServerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val bmpTethering = Icons.Rounded.WifiTethering.toBitmap(context, 22f)
        val bmpGraphicEq = Icons.Rounded.GraphicEq.toBitmap(context, 14f)
        val bmpRadioOff = Icons.Rounded.RadioButtonUnchecked.toBitmap(context, 14f)

        provideContent {
            GlanceTheme {
                val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
                val isStreaming = prefs[WidgetKeys.IS_STREAMING] ?: false
                val isServer = prefs[WidgetKeys.IS_SERVER] ?: false
                val active = isStreaming && isServer
                val localIp = NetworkManager.getLocalIpAddress(context)

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(if (active) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surface)
                        .cornerRadius(28.dp)
                        .padding(20.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(actionStartActivity<MainActivity>()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(44.dp)
                                    .background(if (active) GlanceTheme.colors.primary else GlanceTheme.colors.primaryContainer)
                                    .cornerRadius(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(bmpTethering),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(22.dp),
                                    colorFilter = ColorFilter.tint(if (active) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onPrimaryContainer)
                                )
                            }
                            Spacer(modifier = GlanceModifier.width(10.dp))
                            Column {
                                Text(
                                    text = context.getString(R.string.widget_server_title),
                                    style = TextStyle(
                                        color = if (active) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                Text(
                                    text = localIp,
                                    style = TextStyle(
                                        color = if (active) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = GlanceModifier
                            .wrapContentWidth()
                            .background(if (active) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant)
                            .cornerRadius(99.dp)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(if (active) bmpGraphicEq else bmpRadioOff),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(if (active) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant)
                        )
                        Spacer(modifier = GlanceModifier.width(5.dp))
                        Text(
                            text = if (active) context.getString(R.string.widget_status_streaming)
                            else context.getString(R.string.widget_status_idle),
                            style = TextStyle(
                                color = if (active) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(14.dp))
                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Button(
                        text = if (active) context.getString(R.string.widget_stop_transmission)
                        else context.getString(R.string.widget_start_transmission),
                        onClick = actionStartActivity(
                            Intent(context, MainActivity::class.java).apply {
                                action = if (active) "com.cuscus.wifiaudiostreaming.STOP_STREAMING"
                                else "com.cuscus.wifiaudiostreaming.START_SERVER"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        ),
                        modifier = GlanceModifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (active) GlanceTheme.colors.primary else GlanceTheme.colors.primaryContainer,
                            contentColor = if (active) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

class ServerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ServerWidget()
}

class ClientWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val bmpHeadphones = Icons.Rounded.Headphones.toBitmap(context, 22f)
        val bmpRefresh = Icons.Rounded.Refresh.toBitmap(context, 18f)
        val bmpVolume = Icons.Rounded.VolumeUp.toBitmap(context, 16f)
        val bmpNoDevices = Icons.Rounded.DevicesOther.toBitmap(context, 32f)

        provideContent {
            GlanceTheme {
                val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
                val isStreaming = prefs[WidgetKeys.IS_STREAMING] ?: false
                val isServer = prefs[WidgetKeys.IS_SERVER] ?: false
                val devices = parseDevices(prefs[WidgetKeys.DISCOVERED_DEVICES] ?: "")
                val active = isStreaming && !isServer

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(if (active) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.surface)
                        .cornerRadius(28.dp)
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(actionStartActivity<MainActivity>()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(44.dp)
                                    .background(if (active) GlanceTheme.colors.secondary else GlanceTheme.colors.secondaryContainer)
                                    .cornerRadius(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(bmpHeadphones),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(22.dp),
                                    colorFilter = ColorFilter.tint(if (active) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer)
                                )
                            }
                            Spacer(modifier = GlanceModifier.width(10.dp))
                            Column {
                                Text(
                                    text = context.getString(R.string.widget_client_title),
                                    style = TextStyle(
                                        color = if (active) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                Text(
                                    text = if (active) context.getString(R.string.widget_status_connected)
                                    else context.getString(R.string.widget_search_hint),
                                    style = TextStyle(
                                        color = if (active) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        if (!active) {
                            Box(
                                modifier = GlanceModifier
                                    .size(36.dp)
                                    .background(GlanceTheme.colors.secondaryContainer)
                                    .cornerRadius(12.dp)
                                    .clickable(actionRunCallback<RefreshDiscoveryAction>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(bmpRefresh),
                                    contentDescription = context.getString(R.string.widget_refresh),
                                    modifier = GlanceModifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                                )
                            }
                        }
                    }

                    when {
                        active -> ConnectedBody(context, bmpVolume)
                        devices.isEmpty() -> EmptyDevicesBody(context, bmpNoDevices)
                        else -> {
                            LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                                items(devices) { device ->
                                    DeviceItemRow(context, device)
                                    Spacer(modifier = GlanceModifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseDevices(data: String): List<ServerInfoData> {
        if (data.isBlank()) return emptyList()
        return data.split(";;").mapNotNull {
            val parts = it.split("::")
            if (parts.size == 4) ServerInfoData(
                hostname = parts[0],
                ip = parts[1],
                isMulticast = parts[2].toBoolean(),
                port = parts[3].toIntOrNull() ?: 9090
            ) else null
        }
    }
}

@Composable
private fun ConnectedBody(context: Context, bmpVolume: Bitmap) {
    Column(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(bmpVolume),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(4.dp)
                    .background(GlanceTheme.colors.secondary)
                    .cornerRadius(2.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.onSecondary)
                        .cornerRadius(2.dp)
                ) {}
            }
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Button(
            text = context.getString(R.string.widget_disconnect),
            onClick = actionStartActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = "com.cuscus.wifiaudiostreaming.STOP_STREAMING"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            ),
            modifier = GlanceModifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = GlanceTheme.colors.secondary,
                contentColor = GlanceTheme.colors.onSecondary
            )
        )
    }
}

@Composable
private fun EmptyDevicesBody(context: Context, bmpNoDevices: Bitmap) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(bmpNoDevices),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp).padding(bottom = 8.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
        )
        Text(
            text = context.getString(R.string.widget_no_devices),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
        )
    }
}

@Composable
fun DeviceItemRow(context: Context, device: ServerInfoData) {
    val deviceVector = when {
        device.hostname.contains("mac", ignoreCase = true) -> Icons.Rounded.LaptopMac
        device.hostname.contains("desktop", ignoreCase = true) ||
                device.hostname.contains("pc", ignoreCase = true) -> Icons.Rounded.DesktopWindows
        device.hostname.contains("tablet", ignoreCase = true) -> Icons.Rounded.Tablet
        else -> Icons.Rounded.Smartphone
    }
    val bmpDevice = deviceVector.toBitmap(context, 18f)
    val bmpArrow = Icons.Rounded.ArrowForwardIos.toBitmap(context, 12f)

    val intent = Intent(context, MainActivity::class.java).apply {
        action = "com.cuscus.wifiaudiostreaming.CONNECT_CLIENT"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("CONNECT_CLIENT_IP", device.ip)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(intent))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(36.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(11.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(bmpDevice),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
            )
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = device.hostname,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
            Text(
                text = device.ip,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)
            )
        }
        Image(
            provider = ImageProvider(bmpArrow),
            contentDescription = null,
            modifier = GlanceModifier.size(12.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
        )
    }
}

class ClientWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ClientWidget()
}

class RefreshDiscoveryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val wasActive = NetworkManager.isListeningActive()

        if (!wasActive) {
            NetworkManager.startListeningForDevices(context, "Auto")
        }

        delay(3500)

        val devices = NetworkManager.discoveredDevices.value

        if (!wasActive) {
            NetworkManager.stopListeningForDevices()
        }

        val serialized = devices.entries.joinToString(";;") {
            "${it.key}::${it.value.ip}::${it.value.isMulticast}::${it.value.port}"
        }

        androidx.glance.appwidget.state.updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetKeys.DISCOVERED_DEVICES] = serialized
        }
        ClientWidget().update(context, glanceId)
    }
}

data class ServerInfoData(
    val hostname: String,
    val ip: String,
    val isMulticast: Boolean,
    val port: Int
)