package com.flux.recorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.flux.recorder.data.AudioSource
import com.flux.recorder.data.RecordingState
import com.flux.recorder.service.RecorderService
import com.flux.recorder.ui.theme.FluxRecorderTheme
import com.flux.recorder.utils.PreferencesManager
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

class RecordingShortcutActivity : ComponentActivity() {

    companion object {
        private const val TAG = "RecordingShortcut"
        private const val PREFS_NAME = "recording_shortcut_prefs"
        private const val KEY_SYSTEM_AUDIO = "system_audio"
        private const val KEY_MICROPHONE = "microphone"
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val prefsManager = PreferencesManager(this)
            val settings = prefsManager.getRecordingSettings().copy(
                audioSource = getAudioSource()
            )
            val intent = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_START_RECORDING
                putExtra(RecorderService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(RecorderService.EXTRA_RESULT_DATA, result.data)
                putExtra(RecorderService.EXTRA_SETTINGS, settings)
            }
            startService(intent)
        } else {
            Log.w(TAG, "MediaProjection permission denied")
        }
        finish()
    }

    private fun getAudioSource(): AudioSource {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val systemAudio = prefs.getBoolean(KEY_SYSTEM_AUDIO, true)
        val microphone = prefs.getBoolean(KEY_MICROPHONE, false)
        return when {
            systemAudio && microphone -> AudioSource.BOTH
            systemAudio -> AudioSource.INTERNAL
            microphone -> AudioSource.MICROPHONE
            else -> AudioSource.NONE
        }
    }

    private fun startRecording() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (RecorderService.recordingState.value !is RecordingState.Idle) {
            finish()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            FluxRecorderTheme {
                val showDialog = remember { mutableStateOf(true) }
                var systemAudio by remember { mutableStateOf(prefs.getBoolean(KEY_SYSTEM_AUDIO, true)) }
                var microphone by remember { mutableStateOf(prefs.getBoolean(KEY_MICROPHONE, false)) }
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                Scaffold(containerColor = Color.Transparent) { _ ->
                    SuperDialog(
                        title = if (!isLandscape) getString(R.string.dialog_record_title) else null,
                        summary = if (!isLandscape) getString(R.string.dialog_record_summary) else null,
                        show = showDialog,
                        onDismissRequest = { showDialog.value = false },
                        onDismissFinished = { finish() }
                    ) {
                        if (isLandscape) {
                            // Landscape: left(title+switches) | divider | right(buttons)
                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                // Left: title + switches
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = getString(R.string.dialog_record_title),
                                        style = MiuixTheme.textStyles.title4,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = getString(R.string.dialog_record_summary),
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SwitchRow(
                                        label = getString(R.string.dialog_record_system_audio),
                                        checked = systemAudio,
                                        onToggle = {
                                            systemAudio = it
                                            prefs.edit().putBoolean(KEY_SYSTEM_AUDIO, it).apply()
                                        }
                                    )
                                    SwitchRow(
                                        label = getString(R.string.dialog_record_microphone),
                                        checked = microphone,
                                        onToggle = {
                                            microphone = it
                                            prefs.edit().putBoolean(KEY_MICROPHONE, it).apply()
                                        }
                                    )
                                }

                                // Divider
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp)
                                )

                                // Right: buttons stacked vertically
                                Column(
                                    modifier = Modifier.width(210.dp).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                                ) {
                                    TextButton(
                                        text = getString(R.string.cancel),
                                        onClick = { showDialog.value = false },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    TextButton(
                                        text = getString(R.string.dialog_record_start),
                                        onClick = {
                                            showDialog.value = false
                                            startRecording()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColorsPrimary()
                                    )
                                }
                            }
                        } else {
                            // Portrait: original layout
                            SwitchRow(
                                label = getString(R.string.dialog_record_system_audio),
                                checked = systemAudio,
                                onToggle = {
                                    systemAudio = it
                                    prefs.edit().putBoolean(KEY_SYSTEM_AUDIO, it).apply()
                                }
                            )
                            SwitchRow(
                                label = getString(R.string.dialog_record_microphone),
                                checked = microphone,
                                onToggle = {
                                    microphone = it
                                    prefs.edit().putBoolean(KEY_MICROPHONE, it).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    text = getString(R.string.cancel),
                                    onClick = { showDialog.value = false },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                TextButton(
                                    text = getString(R.string.dialog_record_start),
                                    onClick = {
                                        showDialog.value = false
                                        startRecording()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggle(!checked) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackground
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle
        )
    }
}
