package com.flux.recorder.core.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Combines video and audio tracks into MP4 container with HDR metadata support
 */
class MediaMuxerWrapper(private val outputFile: File) {

    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private var videoFormatReceived = false
    private var audioFormatReceived = false
    private var videoFormat: MediaFormat? = null

    companion object {
        private const val TAG = "MediaMuxerWrapper"

        // HDR transfer functions (matching MediaFormat constants)
        private const val COLOR_TRANSFER_HLG = 7
        private const val COLOR_TRANSFER_PQ = 6
    }

    /**
     * Initialize the muxer
     */
    fun prepare() {
        try {
            mediaMuxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            Log.d(TAG, "MediaMuxer initialized: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaMuxer", e)
            throw e
        }
    }

    /**
     * Add video track with optional HDR metadata
     */
    fun addVideoTrack(format: MediaFormat): Int {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")

        // Store format for HDR metadata injection
        videoFormat = format

        // Add HDR metadata for H.265/HEVC if HDR is active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            injectHdrMetadata(format)
        }

        videoTrackIndex = muxer.addTrack(format)
        videoFormatReceived = true
        Log.d(TAG, "Video track added: $videoTrackIndex, HDR=${isHdrFormat(format)}")

        tryStartMuxer()
        return videoTrackIndex
    }

    /**
     * Inject HDR metadata into the video format for MP4 container
     */
    private fun injectHdrMetadata(format: MediaFormat) {
        if (!format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) return

        val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
        
        // Only inject static metadata for PQ (ST2084). 
        // HLG doesn't require static metadata and adding it can cause over-saturation in some players.
        if (transfer != COLOR_TRANSFER_PQ) return

        Log.d(TAG, "Injecting HDR metadata for PQ content")

        // HDR metadata using KEY_HDR_STATIC_INFO (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Mastering display color volume (Rec. ITU-R BT.2020)
            // G(8500, 39850), B(65535, 2300), R(35400, 14600), White(15635, 16450), L(10000000, 50)
            val masteringData = createMasteringDisplayData(
                rX = 35400, rY = 14600,
                gX = 8500, gY = 39850,
                bX = 65535, bY = 2300,
                whiteX = 15635, whiteY = 16450,
                maxLum = 10000000, minLum = 50
            )

            // Content light level (MaxCLL, MaxFALL)
            val contentLightData = createContentLightLevelData(maxCLL = 1000, maxFALL = 400)

            // Combine descriptors into one ByteBuffer
            val staticInfo = ByteBuffer.allocate(masteringData.limit() + contentLightData.limit())
            staticInfo.order(ByteOrder.LITTLE_ENDIAN)
            staticInfo.put(masteringData)
            staticInfo.put(contentLightData)
            staticInfo.flip()

            format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, staticInfo)
        }
    }

    /**
     * Create mastering display color volume data (25 bytes: 1 byte ID + 24 bytes data)
     */
    private fun createMasteringDisplayData(
        rX: Int, rY: Int, gX: Int, gY: Int, bX: Int, bY: Int,
        whiteX: Int, whiteY: Int, maxLum: Int, minLum: Int
    ): ByteBuffer {
        val data = ByteBuffer.allocate(25)
        data.order(ByteOrder.LITTLE_ENDIAN)
        data.put(0.toByte()) // Descriptor ID 0 (Mastering Display Color Volume)
        data.putShort(gX.toShort()); data.putShort(gY.toShort())
        data.putShort(bX.toShort()); data.putShort(bY.toShort())
        data.putShort(rX.toShort()); data.putShort(rY.toShort())
        data.putShort(whiteX.toShort()); data.putShort(whiteY.toShort())
        data.putInt(maxLum)
        data.putInt(minLum)
        data.flip()
        return data
    }

    /**
     * Create content light level data (5 bytes: 1 byte ID + 4 bytes data)
     */
    private fun createContentLightLevelData(maxCLL: Int, maxFALL: Int): ByteBuffer {
        val data = ByteBuffer.allocate(5)
        data.order(ByteOrder.LITTLE_ENDIAN)
        data.put(1.toByte()) // Descriptor ID 1 (Content Light Level)
        data.putShort(maxCLL.toShort())
        data.putShort(maxFALL.toShort())
        data.flip()
        return data
    }

    /**
     * Check if format indicates HDR content
     */
    private fun isHdrFormat(format: MediaFormat): Boolean {
        if (!format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) return false
        val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
        return transfer == COLOR_TRANSFER_HLG || transfer == COLOR_TRANSFER_PQ
    }
    
    /**
     * Add audio track
     */
    fun addAudioTrack(format: MediaFormat): Int {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")
        
        audioTrackIndex = muxer.addTrack(format)
        audioFormatReceived = true
        Log.d(TAG, "Audio track added: $audioTrackIndex")
        
        tryStartMuxer()
        return audioTrackIndex
    }
    
    private var expectAudio = false
    private var timestampOffsetUs = 0L
    private var pauseStartUs = 0L

    /**
     * Mark the start of a pause — samples between pause and resume will be dropped
     */
    fun onPause() {
        pauseStartUs = System.nanoTime() / 1000
    }

    /**
     * Mark the end of a pause — accumulate the gap into the timestamp offset
     */
    fun onResume() {
        if (pauseStartUs > 0) {
            timestampOffsetUs += (System.nanoTime() / 1000) - pauseStartUs
            pauseStartUs = 0
        }
    }

    /**
     * Set if audio track is expected
     */
    fun setAudioExpected(expected: Boolean) {
        expectAudio = expected
    }
    
    /**
     * Start muxer when all tracks are added
     */
    private fun tryStartMuxer() {
        if (!isMuxerStarted && videoFormatReceived && (!expectAudio || audioFormatReceived)) {
            // Start muxer when video track is ready (audio is optional)
            mediaMuxer?.start()
            isMuxerStarted = true
            Log.d(TAG, "MediaMuxer started")
        }
    }
    
    /**
     * Write video sample
     */
    fun writeVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isMuxerStarted) {
            Log.w(TAG, "Muxer not started, dropping video sample")
            return
        }
        
        if (videoTrackIndex < 0) {
            Log.w(TAG, "Video track not added, dropping sample")
            return
        }
        
        try {
            val adjusted = MediaCodec.BufferInfo()
            adjusted.set(bufferInfo.offset, bufferInfo.size,
                bufferInfo.presentationTimeUs - timestampOffsetUs, bufferInfo.flags)
            mediaMuxer?.writeSampleData(videoTrackIndex, buffer, adjusted)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing video sample", e)
        }
    }
    
    /**
     * Write audio sample
     */
    fun writeAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isMuxerStarted) {
            Log.w(TAG, "Muxer not started, dropping audio sample")
            return
        }
        
        if (audioTrackIndex < 0) {
            Log.w(TAG, "Audio track not added, dropping sample")
            return
        }
        
        try {
            val adjusted = MediaCodec.BufferInfo()
            adjusted.set(bufferInfo.offset, bufferInfo.size,
                bufferInfo.presentationTimeUs - timestampOffsetUs, bufferInfo.flags)
            mediaMuxer?.writeSampleData(audioTrackIndex, buffer, adjusted)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio sample", e)
        }
    }
    
    /**
     * Stop and release muxer
     */
    fun release() {
        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
                isMuxerStarted = false
            }
            
            mediaMuxer?.release()
            mediaMuxer = null
            
            Log.d(TAG, "MediaMuxer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaMuxer", e)
        }
    }
    
    /**
     * Check if muxer is started
     */
    fun isStarted(): Boolean {
        return isMuxerStarted
    }
}
