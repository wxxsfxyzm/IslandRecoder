package com.flux.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.flux.recorder.R
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.RecordingState
import com.flux.recorder.service.RecorderService
import com.flux.recorder.service.QuickTileService
import com.flux.recorder.ui.screens.HomeScreen
import com.flux.recorder.ui.screens.RecordingsScreen
import com.flux.recorder.ui.screens.SettingsScreen
import com.flux.recorder.ui.theme.FluxRecorderTheme
import com.flux.recorder.utils.FileManager
import com.flux.recorder.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import top.yukonga.miuix.kmp.basic.Surface
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    @Inject
    lateinit var fileManager: FileManager
    
    private var recorderService: RecorderService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecorderService.RecorderBinder
            recorderService = binder.getService()
            serviceBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            recorderService = null
            serviceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check if launched from Quick Settings Tile
        val shouldStartRecording = intent?.action == QuickTileService.ACTION_TOGGLE_RECORDING
        
        setContent {
            // Make recorderService observable
            var service by remember { mutableStateOf<RecorderService?>(null) }
            var autoStartRecording by remember { mutableStateOf(shouldStartRecording) }
            val context = LocalContext.current
            
            // Update service when connection changes
            DisposableEffect(Unit) {
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val serviceBinder = binder as RecorderService.RecorderBinder
                        service = serviceBinder.getService()
                    }
                    
                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                    }
                }
                
                // Bind to service
                val intent = Intent(context, RecorderService::class.java)
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                
                onDispose {
                    context.unbindService(connection)
                }
            }
            
            FluxRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Collect recording state reactively
                    val recordingState by service?.recordingState?.collectAsState() 
                        ?: remember { mutableStateOf(RecordingState.Idle) }
                    
                    FluxRecorderApp(
                        preferencesManager = preferencesManager,
                        fileManager = fileManager,
                        onStartRecording = { resultCode, data, settings ->
                            startRecordingService(resultCode, data, settings)
                            autoStartRecording = false
                        },
                        onStopRecording = {
                            stopRecordingService()
                        },
                        onPauseRecording = {
                            pauseRecordingService()
                        },
                        onResumeRecording = {
                            resumeRecordingService()
                        },
                        recordingState = recordingState,
                        onPlayRecording = { file ->
                            playRecording(file)
                        },
                        onShareRecording = { file ->
                            shareRecording(file)
                        },
                        autoStartRecording = autoStartRecording
                    )
                }
            }
        }
    }
    
    private fun startRecordingService(resultCode: Int, data: Intent, settings: RecordingSettings) {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START_RECORDING
            putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecorderService.EXTRA_RESULT_DATA, data)
            putExtra(RecorderService.EXTRA_SETTINGS, settings)
        }
        
        startService(intent)
    }
    
    private fun stopRecordingService() {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_STOP_RECORDING
        }
        startService(intent)
    }
    
    private fun playRecording(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun shareRecording(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun pauseRecordingService() {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_PAUSE_RECORDING
        }
        startService(intent)
    }
    
    private fun resumeRecordingService() {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_RESUME_RECORDING
        }
        startService(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun FluxRecorderApp(
    preferencesManager: PreferencesManager,
    fileManager: FileManager,
    onStartRecording: (Int, Intent, RecordingSettings) -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    recordingState: RecordingState,
    onPlayRecording: (File) -> Unit,
    onShareRecording: (File) -> Unit,
    autoStartRecording: Boolean = false
) {
    var currentScreen by remember { mutableStateOf("home") }
    var settings by remember { mutableStateOf(preferencesManager.getRecordingSettings()) }
    var recordings by remember { mutableStateOf(fileManager.getAllRecordings()) }
    
    when (currentScreen) {
        "home" -> {
            HomeScreen(
                recordingState = recordingState,
                settings = settings,
                onStartRecording = { resultCode, data ->
                    onStartRecording(resultCode, data, settings)
                },
                onStopRecording = onStopRecording,
                onPauseRecording = onPauseRecording,
                onResumeRecording = onResumeRecording,
                onNavigateToSettings = { currentScreen = "settings" },
                onNavigateToRecordings = {
                    recordings = fileManager.getAllRecordings()
                    currentScreen = "recordings"
                },
                autoStartRecording = autoStartRecording
            )
        }
        "settings" -> {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    settings = newSettings
                    preferencesManager.saveRecordingSettings(newSettings)
                },
                onNavigateBack = { currentScreen = "home" }
            )
        }
        "recordings" -> {
            RecordingsScreen(
                recordings = recordings,
                onNavigateBack = { currentScreen = "home" },
                onDeleteRecording = { file ->
                    fileManager.deleteRecording(file)
                    recordings = fileManager.getAllRecordings()
                },
                onShareRecording = onShareRecording,
                onPlayRecording = onPlayRecording
            )
        }
    }
}