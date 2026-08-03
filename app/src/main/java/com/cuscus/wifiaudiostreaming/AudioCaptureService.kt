package com.cuscus.wifiaudiostreaming

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import com.cuscus.wifiaudiostreaming.NetworkManager.updateWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AudioCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val shuttingDown = AtomicBoolean(false)
    private val observingState = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                acquireLocks()
                startForegroundWithNotification()
                serviceScope.launch { updateWidgetState(this@AudioCaptureService, true, true) }

                val streamInternal = intent.getBooleanExtra(EXTRA_STREAM_INTERNAL, false)
                val streamMic = intent.getBooleanExtra(EXTRA_STREAM_MIC, false)
                val sampleRate = intent.getIntExtra("sample_rate", 48000)
                val channelConfig = intent.getStringExtra("channel_config") ?: "STEREO"
                val bufferSize = intent.getIntExtra("buffer_size", 6144)
                val isMulticast = intent.getBooleanExtra(EXTRA_IS_MULTICAST, true)

                val streamingPort = intent.getIntExtra("streaming_port", 9090)
                val networkInterfaceName = intent.getStringExtra("network_interface") ?: "Auto"
                val rtpEnabled = intent.getBooleanExtra("rtp_enabled", false)
                val rtpPort = intent.getIntExtra("rtp_port", 9094)
                val httpEnabled = intent.getBooleanExtra("http_enabled", false)
                val httpPort = intent.getIntExtra("http_port", 8080)
                val dlnaConfig = DlnaServerConfig(
                    enabled = intent.getBooleanExtra("dlna_enabled", false),
                    port = intent.getIntExtra("dlna_port", 8081),
                    preference = DlnaFormatPreference.fromId(intent.getStringExtra("dlna_format")),
                    selectedUdns = DlnaSelection.udns(
                        intent.getStringArrayExtra("dlna_devices")?.toList() ?: emptyList()
                    ),
                    title = getString(R.string.app_name)
                )
                val snapcastConfig = com.cuscus.wifiaudiostreaming.snapcast.SnapcastServerConfig(
                    enabled = intent.getBooleanExtra("snapcast_enabled", false),
                    streamPort = intent.getIntExtra(
                        "snapcast_port",
                        com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.STREAM_PORT
                    ),
                    controlPort = intent.getIntExtra(
                        "snapcast_control_port",
                        com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.CONTROL_PORT
                    ),
                    codec = com.cuscus.wifiaudiostreaming.snapcast.SnapcastCodecs.normalize(
                        intent.getStringExtra("snapcast_codec")
                    ),
                    chunkMs = intent.getIntExtra(
                        "snapcast_chunk_ms",
                        com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.CHUNK_MS
                    ),
                    bufferMs = intent.getIntExtra(
                        "snapcast_buffer_ms",
                        com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.BUFFER_MS
                    ),
                    streamName = intent.getStringExtra("snapcast_stream_name")
                        ?: com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.STREAM_NAME
                )
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

                if (streamInternal && data != null) {
                    val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                            override fun onStop() {
                                stopCapture()
                            }
                        }, android.os.Handler(android.os.Looper.getMainLooper()))
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    NetworkManager.startServerAudio(
                        context = this,
                        projection = mediaProjection,
                        streamInternal = streamInternal,
                        streamMic = streamMic,
                        sampleRate = sampleRate,
                        channelConfig = channelConfig,
                        bufferSize = bufferSize,
                        isMulticast = isMulticast,
                        streamingPort = streamingPort,
                        networkInterfaceName = networkInterfaceName,
                        rtpEnabled = rtpEnabled,
                        rtpPort = rtpPort,
                        httpEnabled = httpEnabled,
                        httpPort = httpPort,
                        dlnaConfig = dlnaConfig,
                        snapcastConfig = snapcastConfig,
                        onClientDisconnected = { stopCapture() }
                    )
                }
            }
            ACTION_STOP -> stopCapture()
            ACTION_YIELD -> yieldToClient()
        }
        return START_STICKY
    }

    private fun yieldToClient() {
        if (!shuttingDown.compareAndSet(false, true)) return

        mediaProjection?.stop()
        mediaProjection = null

        releaseLocks()
        CoroutineScope(Dispatchers.IO).launch {
            updateWidgetState(this@AudioCaptureService, false, true)
        }

        dismissNotification()
        stopSelf()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiFiAudioStreamer::ServerWakeLock").apply {
            acquire()
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifiManager.createWifiLock(lockType, "WiFiAudioStreamer::ServerWifiLock").apply {
            acquire()
        }
    }

    private fun releaseLocks() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wifiLock?.let {
            if (it.isHeld) it.release()
        }
    }

    private fun stopCapture() {
        if (!shuttingDown.compareAndSet(false, true)) return

        NetworkManager.stopStreaming(this)
        mediaProjection?.stop()
        mediaProjection = null

        releaseLocks()
        CoroutineScope(Dispatchers.IO).launch {
            updateWidgetState(this@AudioCaptureService, false, true)
        }

        dismissNotification()
        stopSelf()
    }

    private fun dismissNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        NotificationCenter.cancel(this, NotificationCenter.ID_SERVER)
    }

    @SuppressLint("MissingPermission")
    private fun startForegroundWithNotification() {
        NotificationCenter.ensureChannels(this)

        val initial = buildNotification(getString(R.string.notif_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationCenter.ID_SERVER,
                initial,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NotificationCenter.ID_SERVER, initial)
        }

        if (!observingState.compareAndSet(false, true)) return

        serviceScope.launch {
            combine(
                NetworkManager.connectionStatus,
                NetworkManager.serverVolume
            ) { status, volume -> status to volume }
                .distinctUntilChanged()
                .conflate()
                .collect { (status, volume) ->
                    if (shuttingDown.get()) return@collect
                    NotificationCenter.post(
                        this@AudioCaptureService,
                        NotificationCenter.ID_SERVER,
                        NotificationCenter.serverNotification(
                            this@AudioCaptureService,
                            status.ifBlank { getString(R.string.notif_starting) },
                            volume
                        )
                    )
                    delay(UPDATE_THROTTLE_MS)
                }
        }
    }

    private fun buildNotification(statusText: String) =
        NotificationCenter.serverNotification(
            this,
            statusText,
            NetworkManager.serverVolume.value
        )

    companion object {
        const val ACTION_START = "com.cuscus.wifiaudiostreamer.ACTION_START"
        const val ACTION_STOP = "com.cuscus.wifiaudiostreamer.ACTION_STOP"
        const val ACTION_YIELD = "com.cuscus.wifiaudiostreamer.ACTION_YIELD"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_STREAM_INTERNAL = "stream_internal"
        const val EXTRA_STREAM_MIC = "stream_mic"
        const val EXTRA_IS_MULTICAST = "is_multicast"
        private const val UPDATE_THROTTLE_MS = 350L
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }
}