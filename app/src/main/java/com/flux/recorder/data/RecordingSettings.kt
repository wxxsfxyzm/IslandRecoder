package com.flux.recorder.data

import android.os.Parcelable
import androidx.annotation.StringRes
import com.flux.recorder.R
import kotlinx.parcelize.Parcelize

/**
 * Video quality tiers - dimensions computed at runtime based on device screen
 */
enum class VideoQuality(val targetShortSide: Int, @StringRes val tierLabelResId: Int) {
    NATIVE(0, R.string.quality_native),
    FHD(1080, R.string.quality_fhd),
    HIGH(720, R.string.quality_high),
    MEDIUM(480, R.string.quality_medium),
    LOW(360, R.string.quality_low);

    /**
     * Compute actual recording dimensions preserving device aspect ratio.
     * @param screenW device screen width in pixels
     * @param screenH device screen height in pixels
     * @return (width, height) in same orientation as input, even-aligned for encoder
     */
    fun computeDimensions(screenW: Int, screenH: Int): Pair<Int, Int> {
        val shortSide = minOf(screenW, screenH)
        val longSide = maxOf(screenW, screenH)

        if (targetShortSide == 0 || targetShortSide >= shortSide) {
            return Pair(screenW, screenH)
        }

        val scale = targetShortSide.toFloat() / shortSide
        val newShort = (targetShortSide / 2) * 2
        val newLong = ((longSide * scale).toInt() / 2) * 2

        return if (screenW < screenH) Pair(newShort, newLong) else Pair(newLong, newShort)
    }
}

/**
 * Frame rate options
 */
enum class FrameRate(val fps: Int, @StringRes val labelResId: Int) {
    FPS_30(30, R.string.fps_30),
    FPS_60(60, R.string.fps_60),
    FPS_90(90, R.string.fps_90)
}

/**
 * Audio source configuration
 */
enum class AudioSource(@StringRes val labelResId: Int) {
    NONE(R.string.audio_none),
    INTERNAL(R.string.audio_internal),
    MICROPHONE(R.string.audio_microphone),
    BOTH(R.string.audio_both)
}

/**
 * Recording configuration settings
 */
@Parcelize
data class RecordingSettings(
    val videoQuality: VideoQuality = VideoQuality.FHD,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val audioSource: AudioSource = AudioSource.BOTH,
    val enableFacecam: Boolean = false,
    val enableShakeToStop: Boolean = true,
    val shakeSensitivity: Float = 2.5f // Acceleration threshold in m/s²
) : Parcelable {
    /**
     * Calculate optimal bitrate based on actual recording dimensions and frame rate
     */
    fun calculateBitrate(width: Int, height: Int): Int {
        val pixels = width * height
        val motionFactor = 1.5f
        val qualityFactor = 0.12f
        return (pixels * frameRate.fps * motionFactor * qualityFactor).toInt()
    }
}
