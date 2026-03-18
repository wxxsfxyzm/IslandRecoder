package com.flux.recorder.utils

import android.content.Context
import android.content.SharedPreferences
import com.flux.recorder.data.AudioSource
import com.flux.recorder.data.FrameRate
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.ScreenOrientation
import com.flux.recorder.data.VideoBitrate
import com.flux.recorder.data.VideoQuality

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
            }
        )
    }

    fun saveRecordingSettings(settings: RecordingSettings) {
        prefs.edit().apply {
            putString(KEY_VIDEO_QUALITY, settings.videoQuality.name)
            putString(KEY_VIDEO_BITRATE, settings.videoBitrate.name)
            putString(KEY_SCREEN_ORIENTATION, settings.screenOrientation.name)
            putString(KEY_FRAME_RATE, settings.frameRate.name)
            putString(KEY_AUDIO_SOURCE, settings.audioSource.name)
            apply()
        }
    }

    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirst) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
        return isFirst
    }
}
