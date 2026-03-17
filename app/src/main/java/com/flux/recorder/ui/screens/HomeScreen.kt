package com.flux.recorder.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flux.recorder.R
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.RecordingState
import com.flux.recorder.ui.theme.RecordingRed
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Recording
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    recordingState: RecordingState,
    settings: RecordingSettings,
    onStartRecording: (Int, Intent) -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecordings: () -> Unit,
    autoStartRecording: Boolean = false
) {
    val context = LocalContext.current

    val requiredPermissions = buildList {
        add(android.Manifest.permission.RECORD_AUDIO)
        if (settings.enableFacecam) {
            add(android.Manifest.permission.CAMERA)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(
        permissions = requiredPermissions
    )

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            onStartRecording(result.resultCode, result.data!!)
        }
    }

    LaunchedEffect(autoStartRecording) {
        if (autoStartRecording && recordingState is RecordingState.Idle) {
            if (multiplePermissionsState.allPermissionsGranted) {
                val intent = (context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                    as android.media.projection.MediaProjectionManager)
                    .createScreenCaptureIntent()
                mediaProjectionLauncher.launch(intent)
            } else {
                multiplePermissionsState.launchMultiplePermissionRequest()
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.home_title),
                actions = {
                    IconButton(onClick = onNavigateToRecordings) {
                        Icon(MiuixIcons.Recording, contentDescription = stringResource(R.string.cd_recordings))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(MiuixIcons.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Recording status
            when (recordingState) {
                is RecordingState.Idle -> {
                    Text(
                        stringResource(R.string.status_ready),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
                is RecordingState.Recording -> {
                    Text(
                        stringResource(R.string.status_recording),
                        style = MiuixTheme.textStyles.title2,
                        color = RecordingRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        formatDuration(recordingState.durationMs),
                        fontSize = 48.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
                is RecordingState.Paused -> {
                    Text(
                        stringResource(R.string.status_paused),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        formatDuration(recordingState.durationMs),
                        fontSize = 48.sp,
                        color = MiuixTheme.colorScheme.onBackground
                    )
                }
                is RecordingState.Processing -> {
                    Text(
                        stringResource(R.string.status_processing),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = recordingState.progress / 100f,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
                is RecordingState.Error -> {
                    Text(
                        stringResource(R.string.status_error),
                        style = MiuixTheme.textStyles.title2,
                        color = RecordingRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        recordingState.error,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Record button
            RecordButton(
                isRecording = recordingState is RecordingState.Recording,
                onClick = {
                    if (recordingState is RecordingState.Idle) {
                        if (multiplePermissionsState.allPermissionsGranted) {
                            val intent = (context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                                as android.media.projection.MediaProjectionManager)
                                .createScreenCaptureIntent()
                            mediaProjectionLauncher.launch(intent)
                        } else {
                            multiplePermissionsState.launchMultiplePermissionRequest()
                        }
                    } else {
                        onStopRecording()
                    }
                }
            )

            // Pause/Resume buttons when recording
            if (recordingState is RecordingState.Recording || recordingState is RecordingState.Paused) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (recordingState is RecordingState.Recording) {
                                onPauseRecording()
                            } else {
                                onResumeRecording()
                            }
                        }
                    ) {
                        Text(
                            text = if (recordingState is RecordingState.Recording) stringResource(R.string.action_pause) else stringResource(R.string.action_resume),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onStopRecording,
                        colors = ButtonDefaults.buttonColors(color = RecordingRed)
                    ) {
                        Text(
                            text = stringResource(R.string.action_stop),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Settings summary
            SettingsSummaryCard(settings)
        }
    }
}

@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .size(180.dp)
                .scale(if (isRecording) scale else 1f),
            cornerRadius = 90.dp,
            colors = ButtonDefaults.buttonColors(
                color = if (isRecording) RecordingRed else MiuixTheme.colorScheme.primary
            )
        ) {
            Text(
                if (isRecording) stringResource(R.string.action_stop_caps) else stringResource(R.string.action_record),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SettingsSummaryCard(settings: RecordingSettings) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                stringResource(R.string.current_settings),
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            BasicComponent(
                title = stringResource(R.string.label_quality),
                summary = stringResource(settings.videoQuality.labelResId)
            )
            BasicComponent(
                title = stringResource(R.string.label_frame_rate),
                summary = stringResource(settings.frameRate.labelResId)
            )
            BasicComponent(
                title = stringResource(R.string.label_audio),
                summary = stringResource(settings.audioSource.labelResId)
            )
            if (settings.enableFacecam) {
                BasicComponent(
                    title = stringResource(R.string.label_facecam),
                    summary = stringResource(R.string.label_enabled)
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
