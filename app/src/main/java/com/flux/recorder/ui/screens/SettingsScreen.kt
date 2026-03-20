package com.flux.recorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.util.DisplayMetrics
import android.view.WindowManager
import com.flux.recorder.R
import com.flux.recorder.data.AudioSource
import com.flux.recorder.data.FrameRate
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.ScreenOrientation
import com.flux.recorder.data.VideoBitrate
import com.flux.recorder.data.VideoQuality
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsScreen(
    settings: RecordingSettings,
    onSettingsChanged: (RecordingSettings) -> Unit,
    onNavigateBack: () -> Unit
) {
    var currentSettings by remember { mutableStateOf(settings) }

    val context = LocalContext.current
    val (screenW, screenH) = remember {
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        Pair(metrics.widthPixels, metrics.heightPixels)
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings),
                largeTitle = stringResource(R.string.settings),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            SmallTitle(text = stringResource(R.string.section_recording))
            Card(
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                // Resolution
                val qualityItems = VideoQuality.entries.map { quality ->
                    val (w, h) = quality.computeDimensions(screenW, screenH)
                    stringResource(R.string.quality_label_format, stringResource(quality.tierLabelResId), w, h)
                }
                SuperDropdown(
                    title = stringResource(R.string.video_quality),
                    summary = stringResource(R.string.video_quality_summary),
                    items = qualityItems,
                    selectedIndex = VideoQuality.entries.indexOf(currentSettings.videoQuality),
                    onSelectedIndexChange = {
                        currentSettings = currentSettings.copy(videoQuality = VideoQuality.entries[it])
                        onSettingsChanged(currentSettings)
                    }
                )

                // Bitrate
                val bitrateItems = VideoBitrate.entries.map { stringResource(it.labelResId) }
                SuperDropdown(
                    title = stringResource(R.string.video_bitrate),
                    summary = stringResource(R.string.video_bitrate_summary),
                    items = bitrateItems,
                    selectedIndex = VideoBitrate.entries.indexOf(currentSettings.videoBitrate),
                    onSelectedIndexChange = {
                        currentSettings = currentSettings.copy(videoBitrate = VideoBitrate.entries[it])
                        onSettingsChanged(currentSettings)
                    }
                )

                // Screen Orientation
                val orientationItems = ScreenOrientation.entries.map { stringResource(it.labelResId) }
                SuperDropdown(
                    title = stringResource(R.string.screen_orientation),
                    summary = stringResource(R.string.screen_orientation_summary),
                    items = orientationItems,
                    selectedIndex = ScreenOrientation.entries.indexOf(currentSettings.screenOrientation),
                    onSelectedIndexChange = {
                        currentSettings = currentSettings.copy(screenOrientation = ScreenOrientation.entries[it])
                        onSettingsChanged(currentSettings)
                    }
                )

                // Audio Source
                val audioItems = AudioSource.entries.map { stringResource(it.labelResId) }
                SuperDropdown(
                    title = stringResource(R.string.audio_source),
                    summary = stringResource(R.string.audio_source_summary),
                    items = audioItems,
                    selectedIndex = AudioSource.entries.indexOf(currentSettings.audioSource),
                    onSelectedIndexChange = {
                        currentSettings = currentSettings.copy(audioSource = AudioSource.entries[it])
                        onSettingsChanged(currentSettings)
                    }
                )

                // Frame Rate
                val fpsItems = FrameRate.entries.map { stringResource(it.labelResId) }
                SuperDropdown(
                    title = stringResource(R.string.frame_rate),
                    summary = stringResource(R.string.frame_rate_summary),
                    items = fpsItems,
                    selectedIndex = FrameRate.entries.indexOf(currentSettings.frameRate),
                    onSelectedIndexChange = {
                        currentSettings = currentSettings.copy(frameRate = FrameRate.entries[it])
                        onSettingsChanged(currentSettings)
                    }
                )
            }
        }
    }
}
