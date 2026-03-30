package com.island.recorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.island.recorder.R
import com.island.recorder.data.AudioSource
import com.island.recorder.data.FrameRate
import com.island.recorder.data.RecordingSettings
import com.island.recorder.data.ScreenOrientation
import com.island.recorder.data.TileStyle
import com.island.recorder.data.VideoBitrate
import com.island.recorder.data.VideoCodec
import com.island.recorder.data.VideoQuality
import com.island.recorder.utils.FileManager
import com.island.recorder.utils.PreferencesManager
import com.island.recorder.utils.RootUtils
import com.island.recorder.shizuku.ShizukuHelper
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.extra.SuperSwitch
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
    val prefsManager = remember { PreferencesManager(context) }
    var storagePath by remember { mutableStateOf(prefsManager.getStoragePath()) }
    val isRooted = remember { RootUtils.isRooted() }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Convert content URI to real file path
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                uri, DocumentsContract.getTreeDocumentId(uri)
            )
            val path = uriToFilePath(docUri)
            if (path != null) {
                storagePath = path
                prefsManager.setStoragePath(path)
            }
        }
    }
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
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = 12.dp)
        ) {
            val context = LocalContext.current
            var isProjectMediaGranted by remember { mutableStateOf(false) }
            val isRooted = remember { RootUtils.isRooted() }
            val isShizukuAvailable = remember { ShizukuHelper.isAvailable() }

            LaunchedEffect(Unit) {
                // Check if already granted
                val result = if (isRooted) {
                    RootUtils.getAppOp(context.packageName, "PROJECT_MEDIA")
                } else {
                    "" // Shizuku checking stdout is complex, will rely on user click for now or assume not granted
                }
                if (result.contains("allow")) {
                    isProjectMediaGranted = true
                }
            }

            SmallTitle(text = stringResource(R.string.section_recording))
            Card(
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                // Grant PROJECT_MEDIA
                SuperSwitch(
                    title = stringResource(R.string.grant_project_media),
                    summary = if (isProjectMediaGranted) 
                        stringResource(R.string.project_media_already_granted)
                    else 
                        stringResource(R.string.grant_project_media_summary),
                    checked = isProjectMediaGranted,
                    enabled = !isProjectMediaGranted && (isRooted || isShizukuAvailable),
                    onCheckedChange = {
                        if (it) {
                            val cmd = "appops set ${context.packageName} PROJECT_MEDIA allow"
                            val success = if (isRooted) {
                                RootUtils.setAppOp(context.packageName, "PROJECT_MEDIA", "allow")
                            } else if (isShizukuAvailable) {
                                ShizukuHelper.execShell(cmd) == 0
                            } else {
                                false
                            }
                            if (success) isProjectMediaGranted = true
                        }
                    }
                )

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

                // Video Codec
                val codecItems = VideoCodec.entries.map { stringResource(it.labelResId) }
                SuperDropdown(
                    title = stringResource(R.string.video_codec),
                    summary = stringResource(R.string.video_codec_summary),
                    items = codecItems,
                    selectedIndex = VideoCodec.entries.indexOf(currentSettings.videoCodec),
                    onSelectedIndexChange = {
                        currentSettings = currentSettings.copy(videoCodec = VideoCodec.entries[it])
                        onSettingsChanged(currentSettings)
                    }
                )

                // Show Touches
                SuperSwitch(
                    title = stringResource(R.string.show_touches),
                    summary = stringResource(R.string.show_touches_summary),
                    checked = currentSettings.showTouches,
                    enabled = isRooted,
                    onCheckedChange = {
                        currentSettings = currentSettings.copy(showTouches = it)
                        onSettingsChanged(currentSettings)
                        // Touch visualization will be enabled/disabled during recording
                    }
                )

                var shizukuStatus by remember {
                    mutableStateOf(
                        when {
                            !ShizukuHelper.isAvailable() -> "shizuku_status_not_running"
                            !ShizukuHelper.hasPermission() -> "shizuku_status_not_authorized"
                            else -> "shizuku_status_authorized"
                        }
                    )
                }

                DisposableEffect(Unit) {
                    val listener = object : Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            shizukuStatus = if (ShizukuHelper.hasPermission()) {
                                "shizuku_status_authorized"
                            } else {
                                "shizuku_status_not_authorized"
                            }
                        }
                    }
                    ShizukuHelper.addPermissionResultListener(listener)
                    onDispose {
                        ShizukuHelper.removePermissionResultListener(listener)
                    }
                }

                val statusText = when (shizukuStatus) {
                    "shizuku_status_authorized" -> stringResource(R.string.shizuku_status_authorized)
                    "shizuku_status_not_authorized" -> stringResource(R.string.shizuku_status_not_authorized)
                    else -> stringResource(R.string.shizuku_status_not_running)
                }

                SuperSwitch(
                    title = stringResource(R.string.bypass_focus_island),
                    summary = stringResource(R.string.bypass_focus_island_summary, statusText),
                    checked = currentSettings.bypassFocusIsland,
                    enabled = true,
                    onCheckedChange = {
                        if (it) {
                            if (!ShizukuHelper.isAvailable()) {
                                android.util.Log.w("SettingsScreen", "Shizuku not running")
                            } else if (!ShizukuHelper.hasPermission()) {
                                ShizukuHelper.requestPermission(1001)
                            }
                        }
                        currentSettings = currentSettings.copy(bypassFocusIsland = it)
                        onSettingsChanged(currentSettings)
                    }
                )

                // Tile Style
                val tileStyleItems = TileStyle.entries.map { style ->
                    SpinnerEntry(
                        icon = { modifier ->
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(
                                    if (style == TileStyle.APP_ICON) R.drawable.ic_launcher_foreground_preview
                                    else R.drawable.ic_record
                                ),
                                contentDescription = null,
                                modifier = modifier.size(24.dp)
                            )
                        },
                        title = stringResource(style.labelResId)
                    )
                }
                SuperSpinner(
                    title = stringResource(R.string.tile_style),
                    summary = stringResource(R.string.tile_style_summary),
                    items = tileStyleItems,
                    selectedIndex = TileStyle.entries.indexOf(currentSettings.tileStyle),
                    onSelectedIndexChange = {
                        currentSettings = currentSettings.copy(tileStyle = TileStyle.entries[it])
                        onSettingsChanged(currentSettings)
                    }
                )
            }

            SmallTitle(text = stringResource(R.string.section_storage))
            Card(
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                SuperArrow(
                    title = stringResource(R.string.storage_path),
                    summary = storagePath,
                    onClick = { folderPicker.launch(null) }
                )
            }

            SmallTitle(text = stringResource(R.string.section_about))
            Card(
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                SuperArrow(
                    title = stringResource(R.string.project_url),
                    summary = stringResource(R.string.project_url_value),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Leaf-lsgtky/IslandRecoder"))
                        context.startActivity(intent)
                    }
                )
                SuperArrow(
                    title = stringResource(R.string.original_project),
                    summary = stringResource(R.string.original_project_value),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Icradle-Innovations-Ltd/FluxRecorder"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

private fun uriToFilePath(uri: Uri): String? {
    val docId = DocumentsContract.getDocumentId(uri)
    val split = docId.split(":")
    if (split.size >= 2 && split[0] == "primary") {
        return "/storage/emulated/0/${split[1]}"
    }
    return null
}
