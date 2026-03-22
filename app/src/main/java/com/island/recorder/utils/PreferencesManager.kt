package com.island.recorder.utils

import android.content.Context
import android.content.SharedPreferences
import com.island.recorder.data.AudioSource
import com.island.recorder.data.FrameRate
import com.island.recorder.data.RecordingSettings
import com.island.recorder.data.ScreenOrientation
import com.island.recorder.data.VideoBitrate
import com.island.recorder.data.VideoCodec
import com.island.recorder.data.VideoQuality

/**
 * Manages user preferences using SharedPreferences
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "flux_recorder_prefs"

        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_VIDEO_BITRATE = "video_bitrate"
        private const val KEY_SCREEN_ORIENTATION = "screen_orientation"
        private const val KEY_FRAME_RATE = "frame_rate"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_VIDEO_CODEC = "video_codec"
        private const val KEY_SHOW_TOUCHES = "show_touches"
        private const val KEY_STORAGE_PATH = "storage_path"

        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    fun getRecordingSettings(): RecordingSettings {
        return RecordingSettings(
            videoQuality = try {
                VideoQuality.valueOf(
                    prefs.getString(KEY_VIDEO_QUALITY, VideoQuality.FHD.name)
                        ?: VideoQuality.FHD.name
                )
            } catch (_: IllegalArgumentException) {
                VideoQuality.FHD
            },
            videoBitrate = try {
                VideoBitrate.valueOf(
                    prefs.getString(KEY_VIDEO_BITRATE, VideoBitrate.AUTO.name)
                        ?: VideoBitrate.AUTO.name
                )
            } catch (_: IllegalArgumentException) {
                VideoBitrate.AUTO
            },
            screenOrientation = try {
                ScreenOrientation.valueOf(
                    prefs.getString(KEY_SCREEN_ORIENTATION, ScreenOrientation.AUTO.name)
                        ?: ScreenOrientation.AUTO.name
                )
            } catch (_: IllegalArgumentException) {
                ScreenOrientation.AUTO
            },
            frameRate = try {
                FrameRate.valueOf(
                    prefs.getString(KEY_FRAME_RATE, FrameRate.FPS_60.name)
                        ?: FrameRate.FPS_60.name
                )
            } catch (_: IllegalArgumentException) {
                FrameRate.FPS_60
            },
            audioSource = try {
                AudioSource.valueOf(
                    prefs.getString(KEY_AUDIO_SOURCE, AudioSource.BOTH.name)
                        ?: AudioSource.BOTH.name
                )
            } catch (_: IllegalArgumentException) {
                AudioSource.BOTH
            },
            videoCodec = try {
                VideoCodec.valueOf(
                    prefs.getString(KEY_VIDEO_CODEC, VideoCodec.H264.name)
                        ?: VideoCodec.H264.name
                )
            } catch (_: IllegalArgumentException) {
                VideoCodec.H264
            },
            showTouches = prefs.getBoolean(KEY_SHOW_TOUCHES, false)
        )
    }

    fun saveRecordingSettings(settings: RecordingSettings) {
        prefs.edit().apply {
            putString(KEY_VIDEO_QUALITY, settings.videoQuality.name)
            putString(KEY_VIDEO_BITRATE, settings.videoBitrate.name)
            putString(KEY_SCREEN_ORIENTATION, settings.screenOrientation.name)
            putString(KEY_FRAME_RATE, settings.frameRate.name)
            putString(KEY_AUDIO_SOURCE, settings.audioSource.name)
            putString(KEY_VIDEO_CODEC, settings.videoCodec.name)
            putBoolean(KEY_SHOW_TOUCHES, settings.showTouches)
            apply()
        }
    }

    fun getStoragePath(): String {
        return prefs.getString(KEY_STORAGE_PATH, FileManager.DEFAULT_STORAGE_PATH)
            ?: FileManager.DEFAULT_STORAGE_PATH
    }

    fun setStoragePath(path: String) {
        prefs.edit().putString(KEY_STORAGE_PATH, path).apply()
    }

    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirst) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
        return isFirst
    }
}
