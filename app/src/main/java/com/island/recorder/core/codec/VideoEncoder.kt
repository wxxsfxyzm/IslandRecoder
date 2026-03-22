package com.island.recorder.core.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Hardware-accelerated video encoder using MediaCodec
 * Supports H.264/AVC and H.265/HEVC with dynamic HDR support
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val frameRate: Int,
    private val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    private val isHdrEnabled: Boolean = false
) {
    private var mediaCodec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    // Track current HDR state for dynamic switching
    var isHdrActive: Boolean = false
        private set

    companion object {
        private const val TAG = "VideoEncoder"
        private const val I_FRAME_INTERVAL = 2
        private const val TIMEOUT_US = 10000L

        // HDR color space constants (matching MediaFormat values)
        private const val COLOR_STANDARD_BT709 = 1
        private const val COLOR_STANDARD_BT2020 = 6
        private const val COLOR_TRANSFER_SDR = 3
        private const val COLOR_TRANSFER_HLG = 7
        private const val COLOR_TRANSFER_PQ = 6
        private const val COLOR_RANGE_LIMITED = 2
        private const val COLOR_RANGE_FULL = 1
    }

    /**
     * HDR color space configuration
     */
    data class HdrConfig(
        val standard: Int,
        val transfer: Int,
        val range: Int
    ) {
        companion object {
            val SDR = HdrConfig(COLOR_STANDARD_BT709, COLOR_TRANSFER_SDR, COLOR_RANGE_LIMITED)
            val HDR_HLG = HdrConfig(COLOR_STANDARD_BT709, COLOR_TRANSFER_HLG, COLOR_RANGE_FULL)
            val HDR_PQ = HdrConfig(COLOR_STANDARD_BT2020, COLOR_TRANSFER_PQ, COLOR_RANGE_FULL)
        }
    }

    fun prepare(): Surface? {
        try {
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000L / frameRate)

                // H.265/HEVC: Use Main10 profile for 10-bit support if HDR is enabled
                if (mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                    if (isHdrEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
                        
                        // For Global HDR, we use HLG but with FULL range to match the screen's output.
                        applyHdrConfig(HdrConfig.HDR_HLG)
                        isHdrActive = true
                        
                        Log.d(TAG, "Global HDR mode: HEVC Main10 + BT.2020 + HLG + FULL RANGE")
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                        applyHdrConfig(HdrConfig.SDR)
                        Log.d(TAG, "HEVC Main profile enabled (8-bit)")
                    } else {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                        Log.d(TAG, "HEVC Main profile enabled (8-bit, API < 29)")
                    }
                }
            }

            mediaCodec = MediaCodec.createEncoderByType(mimeType).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            Log.d(TAG, "Video encoder initialized: ${width}x${height} @ ${frameRate}fps, ${bitrate}bps, codec=$mimeType")
            return inputSurface

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize video encoder", e)
            release()
            return null
        }
    }

    /**
     * Apply HDR color space configuration to the format
     */
    private fun MediaFormat.applyHdrConfig(config: HdrConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setInteger(MediaFormat.KEY_COLOR_STANDARD, config.standard)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, config.transfer)
            setInteger(MediaFormat.KEY_COLOR_RANGE, config.range)
        }
    }

    /**
     * Update color space dynamically during encoding
     * Call this when HDR state changes
     */
    fun updateColorSpace(hdrConfig: HdrConfig) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val newIsHdr = hdrConfig !== HdrConfig.SDR
        if (newIsHdr == isHdrActive) return // No change needed

        try {
            // Use Bundle for setParameters (API 29+)
            val params = Bundle()
            params.putInt(MediaFormat.KEY_COLOR_STANDARD, hdrConfig.standard)
            params.putInt(MediaFormat.KEY_COLOR_TRANSFER, hdrConfig.transfer)
            params.putInt(MediaFormat.KEY_COLOR_RANGE, hdrConfig.range)

            // Force an I-frame before changing color space
            val iFrameParams = Bundle()
            iFrameParams.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaCodec?.setParameters(iFrameParams)
                mediaCodec?.setParameters(params)
            }

            isHdrActive = newIsHdr
            Log.d(TAG, "Color space updated: HDR=$isHdrActive (standard=${hdrConfig.standard}, transfer=${hdrConfig.transfer})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update color space", e)
        }
    }

    /**
     * Switch to SDR color space
     */
    fun switchToSdr() {
        updateColorSpace(HdrConfig.SDR)
    }

    /**
     * Switch to HDR color space (HLG or PQ)
     */
    fun switchToHdr(isPq: Boolean = false) {
        updateColorSpace(if (isPq) HdrConfig.HDR_PQ else HdrConfig.HDR_HLG)
    }

    sealed interface EncoderOutput {
        data class Data(val buffer: ByteBuffer, val info: MediaCodec.BufferInfo, val index: Int) : EncoderOutput
        data class FormatChanged(val format: MediaFormat) : EncoderOutput
        object TryAgain : EncoderOutput
    }

    fun getEncodedData(): EncoderOutput {
        val codec = mediaCodec ?: return EncoderOutput.TryAgain

        val bufferInfo = MediaCodec.BufferInfo()
        val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

        return when {
            outputBufferIndex >= 0 -> {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null) {
                    EncoderOutput.Data(outputBuffer, bufferInfo, outputBufferIndex)
                } else {
                    EncoderOutput.TryAgain
                }
            }
            outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val newFormat = codec.outputFormat
                Log.d(TAG, "Output format changed: $newFormat")
                // Check if format contains HDR info
                if (newFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    val transfer = newFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                    isHdrActive = (transfer == COLOR_TRANSFER_HLG || transfer == COLOR_TRANSFER_PQ)
                    Log.d(TAG, "Format change indicates HDR=$isHdrActive")
                }
                EncoderOutput.FormatChanged(newFormat)
            }
            outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                EncoderOutput.TryAgain
            }
            else -> EncoderOutput.TryAgain
        }
    }

    fun releaseOutputBuffer(index: Int) {
        mediaCodec?.releaseOutputBuffer(index, false)
    }

    fun signalEndOfStream() {
        try {
            mediaCodec?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.e(TAG, "Error signaling end of stream", e)
        }
    }

    fun getOutputFormat(): MediaFormat? {
        return mediaCodec?.outputFormat
    }

    fun release() {
        try {
            inputSurface?.release()
            inputSurface = null

            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null

            Log.d(TAG, "Video encoder released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing video encoder", e)
        }
    }

    fun isActive(): Boolean {
        return mediaCodec != null
    }
}
