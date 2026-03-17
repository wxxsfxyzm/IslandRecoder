package com.flux.recorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flux.recorder.R
import com.flux.recorder.data.AudioSource
import com.flux.recorder.data.FrameRate
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.VideoQuality
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    settings: RecordingSettings,
    onSettingsChanged: (RecordingSettings) -> Unit,
    onNavigateBack: () -> Unit
) {
    var currentSettings by remember { mutableStateOf(settings) }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings),
                largeTitle = stringResource(R.string.settings),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Video Quality
            item {
                SmallTitle(text = stringResource(R.string.video_quality))
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    val qualityItems = VideoQuality.entries.map { stringResource(it.labelResId) }
                    SuperDropdown(
                        title = stringResource(R.string.video_quality),
                        items = qualityItems,
                        selectedIndex = VideoQuality.entries.indexOf(currentSettings.videoQuality),
                        onSelectedIndexChange = {
                            currentSettings = currentSettings.copy(videoQuality = VideoQuality.entries[it])
                            onSettingsChanged(currentSettings)
                        }
                    )
                }
            }

            // Frame Rate
            item {
                SmallTitle(text = stringResource(R.string.frame_rate))
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    val fpsItems = FrameRate.entries.map { stringResource(it.labelResId) }
                    SuperDropdown(
                        title = stringResource(R.string.frame_rate),
                        items = fpsItems,
                        selectedIndex = FrameRate.entries.indexOf(currentSettings.frameRate),
                        onSelectedIndexChange = {
                            currentSettings = currentSettings.copy(frameRate = FrameRate.entries[it])
                            onSettingsChanged(currentSettings)
                        }
                    )
                }
            }

            // Audio Source
            item {
                SmallTitle(text = stringResource(R.string.audio_source))
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    val audioItems = AudioSource.entries.map { stringResource(it.labelResId) }
                    SuperDropdown(
                        title = stringResource(R.string.audio_source),
                        items = audioItems,
                        selectedIndex = AudioSource.entries.indexOf(currentSettings.audioSource),
                        onSelectedIndexChange = {
                            currentSettings = currentSettings.copy(audioSource = AudioSource.entries[it])
                            onSettingsChanged(currentSettings)
                        }
                    )
                }
            }

            // Toggles
            item {
                SmallTitle(text = stringResource(R.string.section_camera))
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    SuperSwitch(
                        title = stringResource(R.string.enable_facecam),
                        checked = currentSettings.enableFacecam,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(enableFacecam = it)
                            onSettingsChanged(currentSettings)
                        }
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.section_gestures))
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    SuperSwitch(
                        title = stringResource(R.string.shake_to_stop),
                        checked = currentSettings.enableShakeToStop,
                        onCheckedChange = {
                            currentSettings = currentSettings.copy(enableShakeToStop = it)
                            onSettingsChanged(currentSettings)
                        }
                    )
                }
            }
        }
    }
}
