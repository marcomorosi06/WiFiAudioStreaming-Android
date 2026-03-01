package com.cuscus.wifiaudiostreaming

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuscus.wifiaudiostreaming.ui.theme.WiFiAudioStreamingTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = Intent(this, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_START
                    putExtra(AudioCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(AudioCaptureService.EXTRA_DATA, result.data)

                    viewModel.appSettings.value?.let { settings ->
                        putExtra(AudioCaptureService.EXTRA_STREAM_INTERNAL, settings.streamInternal)
                        putExtra(AudioCaptureService.EXTRA_STREAM_MIC, settings.streamMic)
                        putExtra("sample_rate", settings.sampleRate)
                        putExtra("channel_config", settings.channelConfig)
                        putExtra("buffer_size", settings.bufferSize)
                        putExtra(AudioCaptureService.EXTRA_IS_MULTICAST, viewModel.isMulticastMode.value)
                        putExtra("streaming_port", settings.streamingPort)
                    }
                }
                startForegroundService(intent)
                viewModel.setIsStreaming(true)
            } else {
                viewModel.setIsStreaming(false)
                viewModel.updateStatus(getString(R.string.capture_permission_denied))
            }
        }

    private var onMicPermissionGranted: (() -> Unit)? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                onMicPermissionGranted?.invoke()
            } else {
                viewModel.updateStatus(getString(R.string.mic_permission_denied))
                Toast.makeText(this, getString(R.string.mic_permission_denied), Toast.LENGTH_LONG).show()
            }
            onMicPermissionGranted = null
        }

    // ## NUOVO: LAUNCHER PER IL PERMESSO DELLE NOTIFICHE ##
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, R.string.notification_permission_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WiFiAudioStreamingTheme {
                val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

                if (appSettings == null) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background))
                } else {
                    if (appSettings!!.onboardingCompleted) {
                        MainAppContent()

                        // ## NUOVO: LOGICA DI GESTIONE DEL DIALOGO ##
                        var showNotificationPermissionDialog by remember { mutableStateOf(false) }

                        // Questo `LaunchedEffect` viene eseguito solo una volta all'avvio dell'app.
                        LaunchedEffect(Unit) {
                            // Controlla il permesso solo su Android 13 (TIRAMISU) o superiori.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                                showNotificationPermissionDialog = true
                            }
                        }

                        if (showNotificationPermissionDialog) {
                            NotificationPermissionDialog(
                                onConfirm = {
                                    showNotificationPermissionDialog = false
                                    // Lancia la richiesta di sistema per il permesso
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onDismiss = {
                                    showNotificationPermissionDialog = false
                                }
                            )
                        }

                    } else {
                        OnboardingScreen(
                            onOnboardingFinished = {
                                viewModel.setOnboardingCompleted()
                            }
                        )
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    private fun MainAppContent() {
        val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
        val currentSettings = appSettings!!

        val showSettingsScreen = remember { mutableStateOf(false) }

        val isServer by viewModel.isServer.collectAsStateWithLifecycle()
        val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
        val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
        val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
        val isMulticastMode by viewModel.isMulticastMode.collectAsStateWithLifecycle()
        val context = androidx.compose.ui.platform.LocalContext.current
        val localIp = remember { NetworkManager.getLocalIpAddress(context) }

        ClientDiscoveryHandler()

        BackHandler(enabled = showSettingsScreen.value) {
            showSettingsScreen.value = false
        }

        WiFiAudioStreamingApp(
            appSettings = currentSettings,
            isServer = isServer,
            isStreaming = isStreaming,
            connectionStatus = connectionStatus,
            discoveredDevices = discoveredDevices,
            isMulticastMode = isMulticastMode,
            localIp = localIp,
            onToggleMode = viewModel::toggleMode,
            onStartServer = {
                startMediaProjectionRequest()
            },
            onStopServer = {
                val intent = Intent(this, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_STOP
                }
                startService(intent)
                viewModel.setIsStreaming(false)
            },
            onConnect = { serverInfo ->
                viewModel.startClient(serverInfo)
            },
            onConnectManual = { ip ->
                viewModel.startClientManually(ip)
            },
            onRefresh = viewModel::clearDiscoveredDevices,
            onMulticastModeChange = viewModel::setMulticastMode,
            onStreamInternalChange = viewModel::setStreamInternal,
            onStreamMicChange = { enabled ->
                if (enabled) {
                    if (hasRecordAudioPermission()) {
                        viewModel.setStreamMic(true)
                    } else {
                        onMicPermissionGranted = { viewModel.setStreamMic(true) }
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    viewModel.setStreamMic(false)
                }
            },
            onSampleRateChange = viewModel::setSampleRate,
            onChannelConfigChange = viewModel::setChannelConfig,
            onBufferSizeChange = viewModel::setBufferSize,
            onSendClientMicrophoneChange = { enabled ->
                if (enabled) {
                    if (hasRecordAudioPermission()) {
                        viewModel.setSendClientMicrophone(true)
                    } else {
                        onMicPermissionGranted = { viewModel.setSendClientMicrophone(true) }
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    viewModel.setSendClientMicrophone(false)
                }
            },
            onOpenSettings = { showSettingsScreen.value = true }
        )

        ExpressiveSettingsScreen(
            isVisible = showSettingsScreen.value,
            appSettings = currentSettings,
            onStreamInternalChange = viewModel::setStreamInternal,
            onStreamMicChange = viewModel::setStreamMic,
            onSampleRateChange = viewModel::setSampleRate,
            onChannelConfigChange = viewModel::setChannelConfig,
            onBufferSizeChange = viewModel::setBufferSize,
            onStreamingPortChange = viewModel::setStreamingPort,
            onMicPortChange = viewModel::setMicPort,
            onExperimentalFeaturesChange = viewModel::setExperimentalFeatures,
            onClose = { showSettingsScreen.value = false },
            onShowOnboarding = {
                showSettingsScreen.value = false
                viewModel.resetOnboarding()
            }
        )
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ## NUOVO: HELPER PER CONTROLLARE IL PERMESSO DELLE NOTIFICHE ##
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startMediaProjectionRequest() {
        val settings = viewModel.appSettings.value ?: return

        if (!settings.streamInternal && settings.streamMic) {
            val intent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_STREAM_INTERNAL, false)
                putExtra(AudioCaptureService.EXTRA_STREAM_MIC, true)
                putExtra("sample_rate", settings.sampleRate)
                putExtra("channel_config", settings.channelConfig)
                putExtra("buffer_size", settings.bufferSize)
                putExtra(AudioCaptureService.EXTRA_IS_MULTICAST, viewModel.isMulticastMode.value)
                putExtra("streaming_port", settings.streamingPort)
            }
            startForegroundService(intent)
            viewModel.setIsStreaming(true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            viewModel.updateStatus(getString(R.string.internal_audio_android_version_required))
        }
    }
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (NetworkManager.isServerStreaming) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    NetworkManager.serverVolume.value = (NetworkManager.serverVolume.value + 0.1f).coerceAtMost(2.0f)
                    return true // Consuma l'evento: impedisce al sistema di alzare il suo audio!
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    NetworkManager.serverVolume.value = (NetworkManager.serverVolume.value - 0.1f).coerceAtLeast(0.0f)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (NetworkManager.isServerStreaming &&
            (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true // Consuma l'evento: previene il classico "BEEP" di sistema al rilascio del tasto
        }
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
fun ClientDiscoveryHandler() {
    val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val isServer by viewModel.isServer.collectAsStateWithLifecycle()

    LaunchedEffect(isServer) {
        if (!isServer) {
            viewModel.startListening()
        } else {
            NetworkManager.stopListeningForDevices()
        }
    }
}

// ## NUOVO: COMPOSABLE PER L'ALERT DIALOG PERSONALIZZATO ##
@Composable
fun NotificationPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
        title = { Text(text = stringResource(R.string.notification_permission_title)) },
        text = { Text(text = stringResource(R.string.notification_permission_description)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.grant_permission_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.later_button))
            }
        }
    )
}