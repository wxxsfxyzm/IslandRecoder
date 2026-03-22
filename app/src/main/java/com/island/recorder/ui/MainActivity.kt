package com.island.recorder.ui

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.island.recorder.data.RecordingSettings
import com.island.recorder.service.RecorderService
import com.island.recorder.ui.screens.HomeScreen
import com.island.recorder.ui.screens.SettingsScreen
import com.island.recorder.ui.theme.IslandRecorderTheme
import com.island.recorder.utils.FileManager
import com.island.recorder.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var fileManager: FileManager

    override fun onCreate(savedInstanceState: Bundle?) {
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

            IslandRecorderTheme {
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
                    }
                )
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
}

@Composable
fun FluxRecorderApp(
    preferencesManager: PreferencesManager,
    fileManager: FileManager,
    onStartRecording: (Int, Intent, RecordingSettings) -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit
) {
    var settings by remember { mutableStateOf(preferencesManager.getRecordingSettings()) }

    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }

    NavDisplay(
        backStack = backStack,
        entryProvider = { key ->
            NavEntry(key) {
                when (key) {
                    Screen.Home -> {
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
                            onNavigateToSettings = { backStack.add(Screen.Settings) }
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
                }
            }
        }
    )
}