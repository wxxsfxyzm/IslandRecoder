package com.flux.recorder.utils

import android.content.Context
import android.content.SharedPreferences
import com.flux.recorder.data.AudioSource
import com.flux.recorder.data.FrameRate
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.VideoQuality

/**
 * Manages user preferences using SharedPreferences
 */
class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "flux_recorder_prefs"
        
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_FRAME_RATE = "frame_rate"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_ENABLE_FACECAM = "enable_facecam"
        private const val KEY_SHAKE_TO_STOP = "shake_to_stop"
        private const val KEY_SHAKE_SENSITIVITY = "shake_sensitivity"
        
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }
    
    /**
     * Get current recording settings
     */
    fun getRecordingSettings(): RecordingSettings {
        return RecordingSettings(
            videoQuality = try {
                VideoQuality.valueOf(
                    prefs.getString(KEY_VIDEO_QUALITY, VideoQuality.FHD.name)
                        ?: VideoQuality.FHD.name
                )
            } catch (_: IllegalArgumentException) {
                VideoQuality.FHD // fallback for old enum values
            },
            frameRate = FrameRate.valueOf(
                prefs.getString(KEY_FRAME_RATE, FrameRate.FPS_60.name) 
                    ?: FrameRate.FPS_60.name
            ),
            audioSource = AudioSource.valueOf(
                prefs.getString(KEY_AUDIO_SOURCE, AudioSource.BOTH.name) 
                    ?: AudioSource.BOTH.name
            ),
            enableFacecam = prefs.getBoolean(KEY_ENABLE_FACECAM, false),
            enableShakeToStop = prefs.getBoolean(KEY_SHAKE_TO_STOP, true),
            shakeSensitivity = prefs.getFloat(KEY_SHAKE_SENSITIVITY, 2.5f)
        )
    }
    
    /**
     * Save recording settings
     */
    fun saveRecordingSettings(settings: RecordingSettings) {
        prefs.edit().apply {
            putString(KEY_VIDEO_QUALITY, settings.videoQuality.name)
            putString(KEY_FRAME_RATE, settings.frameRate.name)
            putString(KEY_AUDIO_SOURCE, settings.audioSource.name)
            putBoolean(KEY_ENABLE_FACECAM, settings.enableFacecam)
            putBoolean(KEY_SHAKE_TO_STOP, settings.enableShakeToStop)
            putFloat(KEY_SHAKE_SENSITIVITY, settings.shakeSensitivity)
            apply()
        }
    }
    
    /**
     * Check if this is the first launch
     */
    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirst) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
        return isFirst
    }
}
