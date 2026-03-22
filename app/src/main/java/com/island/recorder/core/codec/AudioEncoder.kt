package com.island.recorder.core.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

/**
 * Encodes PCM audio data to AAC
 */
class AudioEncoder(
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 2, // Stereo
    private val bitrate: Int = 128000 // 128kbps
) {
    
    private var mediaCodec: MediaCodec? = null
    
    companion object {
        private const val TAG = "AudioEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val TIMEOUT_US = 10000L
    }
    
    /**
     * Prepare the audio encoder
     */
    fun prepare() {
        try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384) // 16KB buffer
            }
            
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            
            Log.d(TAG, "Audio encoder initialized: ${sampleRate}Hz, $channelCount channels")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio encoder", e)
            release()
        }
    }
    
    /**
     * Submit raw PCM data for encoding
     */
    fun encode(pcmData: ByteArray, length: Int, timestampUs: Long) {
        val codec = mediaCodec ?: return
        
        try {
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmData, 0, length)
                
                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    length,
                    timestampUs,
                    0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding audio frame", e)
        }
    }
    
    sealed interface Output {
        data class Data(val buffer: ByteBuffer, val info: MediaCodec.BufferInfo, val index: Int) : Output
        object FormatChanged : Output
        object TryAgain : Output
    }
    
    /**
     * Get encoded AAC data
     */
    fun getEncodedData(): Output {
        val codec = mediaCodec ?: return Output.TryAgain
        
        val bufferInfo = MediaCodec.BufferInfo()
        val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        
        return when {
            outputBufferIndex >= 0 -> {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null) {
                    Output.Data(outputBuffer, bufferInfo, outputBufferIndex)
                } else {
                    Output.TryAgain
                }
            }
            outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                Log.d(TAG, "Audio output format changed: ${codec.outputFormat}")
                Output.FormatChanged
            }
            else -> Output.TryAgain
        }
    }
    
    fun releaseOutputBuffer(index: Int) {
        mediaCodec?.releaseOutputBuffer(index, false)
    }
    
    fun getOutputFormat(): MediaFormat? {
        return mediaCodec?.outputFormat
    }
    
    fun release() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio encoder", e)
        }
    }
}
