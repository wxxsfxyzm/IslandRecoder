package com.flux.recorder

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.flux.recorder.R
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.service.RecorderService
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val darkMode = isSystemInDarkTheme()

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }

            FluxRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    FluxRecorderApp(
                        preferencesManager = preferencesManager,
                        fileManager = fileManager,
                        onStartRecording = { resultCode, data, settings ->
                            startRecordingService(resultCode, data, settings)
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
                        onPlayRecording = { file ->
                            playRecording(file)
                        },
                        onShareRecording = { file ->
                            shareRecording(file)
                        }
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
}

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Recordings : Screen
}

@Composable
fun FluxRecorderApp(
    preferencesManager: PreferencesManager,
    fileManager: FileManager,
    onStartRecording: (Int, Intent, RecordingSettings) -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onPlayRecording: (File) -> Unit,
    onShareRecording: (File) -> Unit
) {
    var settings by remember { mutableStateOf(preferencesManager.getRecordingSettings()) }
    var recordings by remember { mutableStateOf(fileManager.getAllRecordings()) }

    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }

    NavDisplay(
        backStack = backStack,
        entryProvider = { key ->
            NavEntry(key) {
                when (key) {
                    Screen.Home -> {
                        // Collect state INSIDE NavEntry so recomposition is triggered here
                        val recordingState by RecorderService.recordingState.collectAsState()
                        HomeScreen(
                            recordingState = recordingState,
                            settings = settings,
                            onStartRecording = { resultCode, data ->
                                onStartRecording(resultCode, data, settings)
                            },
                            onStopRecording = onStopRecording,
                            onPauseRecording = onPauseRecording,
                            onResumeRecording = onResumeRecording,
                            onNavigateToSettings = { backStack.add(Screen.Settings) },
                            onNavigateToRecordings = {
                                recordings = fileManager.getAllRecordings()
                                backStack.add(Screen.Recordings)
                            }
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        onSettingsChanged = { newSettings ->
                            settings = newSettings
                            preferencesManager.saveRecordingSettings(newSettings)
                        },
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                    Screen.Recordings -> RecordingsScreen(
                        recordings = recordings,
                        onNavigateBack = { backStack.removeLastOrNull() },
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
    )
}