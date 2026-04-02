package com.cuscus.wifiaudiostreaming

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cuscus.wifiaudiostreaming.data.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// --- CLASSE BASE PER FUNZIONI COMUNI ---
abstract class BaseStreamingTileService : TileService() {

    protected fun isAppStreaming(): Boolean {
        val prefs = getSharedPreferences("TileState", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_streaming", false)
    }

    protected fun isAppServer(): Boolean {
        val prefs = getSharedPreferences("TileState", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_server", false)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    protected fun startActivitySafely(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

// --- TILE SERVER ---
class ServerTileService : BaseStreamingTileService() {

    override fun onStartListening() {
        val active = isAppStreaming() && isAppServer()
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (active) getString(R.string.widget_status_streaming) else getString(R.string.widget_status_idle)
            }
            updateTile()
        }
    }

    override fun onClick() {
        val active = isAppStreaming() && isAppServer()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = if (active) "com.cuscus.wifiaudiostreaming.STOP_STREAMING" else "com.cuscus.wifiaudiostreaming.START_SERVER"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivitySafely(intent)
    }
}

// --- TILE CLIENT ---
class ClientTileService : BaseStreamingTileService() {

    override fun onStartListening() {
        val active = isAppStreaming() && !isAppServer()
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (active) getString(R.string.widget_status_connected) else getString(R.string.widget_status_idle)
            }
            updateTile()
        }
    }

    override fun onClick() {
        val active = isAppStreaming() && !isAppServer()

        if (active) {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.cuscus.wifiaudiostreaming.STOP_STREAMING"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivitySafely(intent)
        } else {
            // Leggiamo l'IP fisso dal DataStore in modo sincrono (sicuro in questo thread)
            val ip = runBlocking {
                applicationContext.settingsDataStore.data.first()[stringPreferencesKey("client_tile_ip")] ?: ""
            }

            if (ip.isNotBlank()) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = "com.cuscus.wifiaudiostreaming.CONNECT_CLIENT"
                    putExtra("CONNECT_CLIENT_IP", ip)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivitySafely(intent)
            } else {
                // Se non c'è nessun IP configurato, apriamo semplicemente l'app
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivitySafely(intent)
            }
        }
    }
}