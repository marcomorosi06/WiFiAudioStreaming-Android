package com.example.wifiaudiostreaming

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiaudiostreaming.ui.theme.WiFiAudioStreamingTheme

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

                    // Usiamo lo snapshot corrente delle impostazioni
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

    private var pendingServerConnection by mutableStateOf<ServerInfo?>(null)

    @RequiresApi(Build.VERSION_CODES.O)
    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingServerConnection?.let { serverInfo ->
                    viewModel.startClient(serverInfo)
                    pendingServerConnection = null
                } ?: run {
                    startMediaProjectionRequest()
                }
            } else {
                viewModel.updateStatus(getString(R.string.mic_permission_denied))
                pendingServerConnection = null
                Toast.makeText(this, getString(R.string.mic_permission_denied), Toast.LENGTH_LONG).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WiFiAudioStreamingTheme {
                val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

                // ## SOLUZIONE APPLICATA QUI ##
                // La logica ora è a prova di "race condition".

                // 1. Finché le impostazioni sono `null` (cioè in caricamento), non mostriamo nulla.
                //    Questo evita di mostrare la UI sbagliata per una frazione di secondo.
                if (appSettings == null) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background))
                } else {
                    // 2. Una volta che le impostazioni sono caricate, decidiamo cosa mostrare.
                    //    La decisione è semplice, diretta e corretta.
                    if (appSettings!!.onboardingCompleted) {
                        MainAppContent()
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

    /**
     * Composable che contiene la logica e la UI dell'applicazione principale.
     * Viene mostrato solo dopo che l'onboarding è stato completato.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    private fun MainAppContent() {
        val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
        // Qui possiamo usare l'operatore !! perché questa funzione è chiamata
        // solo quando appSettings non è null.
        val currentSettings = appSettings!!

        val showSettingsScreen = remember { mutableStateOf(false) }

        val isServer by viewModel.isServer.collectAsStateWithLifecycle()
        val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
        val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
        val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
        val isMulticastMode by viewModel.isMulticastMode.collectAsStateWithLifecycle()

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
            onToggleMode = viewModel::toggleMode,
            onStartServer = {
                if (hasRecordAudioPermission()) {
                    startMediaProjectionRequest()
                } else {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onStopServer = {
                val intent = Intent(this, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_STOP
                }
                startService(intent)
                viewModel.setIsStreaming(false)
            },
            onConnect = { serverInfo ->
                val sendMic = currentSettings.sendClientMicrophone
                if (sendMic) {
                    if (hasRecordAudioPermission()) {
                        viewModel.startClient(serverInfo)
                    } else {
                        pendingServerConnection = serverInfo
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    viewModel.startClient(serverInfo)
                }
            },
            onRefresh = viewModel::clearDiscoveredDevices,
            onMulticastModeChange = viewModel::setMulticastMode,
            onStreamInternalChange = viewModel::setStreamInternal,
            onStreamMicChange = viewModel::setStreamMic,
            onSampleRateChange = viewModel::setSampleRate,
            onChannelConfigChange = viewModel::setChannelConfig,
            onBufferSizeChange = viewModel::setBufferSize,
            onSendClientMicrophoneChange = viewModel::setSendClientMicrophone,
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
                viewModel.resetOnboarding() // Chiamiamo il nuovo metodo per mostrare di nuovo l'onboarding
            }
        )
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startMediaProjectionRequest() {
        val settings = viewModel.appSettings.value ?: return // Esci se le impostazioni non sono caricate

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