package com.island.recorder.core.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.island.recorder.data.AudioSource

/**
 * Handles Audio capture (Mic, System Internal, or Both)
 * Supports mixing multiple audio sources when BOTH mode is selected
 */
class AudioRecorder {
    
    private var micRecorder: AudioRecord? = null
    private var internalRecorder: AudioRecord? = null
    private var bufferSize = 0
    private var isRecording = false
    private var currentAudioSource: AudioSource = AudioSource.NONE
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    /**
     * Start audio recording based on the specified audio source
     * @param mediaProjection Required for System Audio (Internal)
     * @param audioSource The audio source mode (NONE, INTERNAL, MICROPHONE, BOTH)
     */
    @SuppressLint("MissingPermission")
    fun start(mediaProjection: MediaProjection?, audioSource: AudioSource): Boolean {
        currentAudioSource = audioSource
        
        if (audioSource == AudioSource.NONE) {
            Log.d(TAG, "Audio source is NONE, skipping audio recording")
            return true // Not an error, just no audio
        }
        
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
        
        var micSuccess = false
        var internalSuccess = false
        
        try {
            // Initialize Microphone Recorder (for MICROPHONE or BOTH)
            if (audioSource == AudioSource.MICROPHONE || audioSource == AudioSource.BOTH) {
                micRecorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                
                if (micRecorder?.state == AudioRecord.STATE_INITIALIZED) {
                    micRecorder?.startRecording()
                    micSuccess = true
                    Log.d(TAG, "Initialized Microphone Recorder")
                } else {
                    Log.e(TAG, "Microphone AudioRecord not initialized")
                    micRecorder?.release()
                    micRecorder = null
                }
            }
            
            // Initialize Internal Audio Recorder (for INTERNAL or BOTH)
            if ((audioSource == AudioSource.INTERNAL || audioSource == AudioSource.BOTH) 
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q 
                && mediaProjection != null) {
                
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                
                internalRecorder = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build()
                    )
                    .setAudioPlaybackCaptureConfig(config)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
                
                if (internalRecorder?.state == AudioRecord.STATE_INITIALIZED) {
                    internalRecorder?.startRecording()
                    internalSuccess = true
                    Log.d(TAG, "Initialized System Audio Recorder")
                } else {
                    Log.e(TAG, "Internal AudioRecord not initialized")
                    internalRecorder?.release()
                    internalRecorder = null
                }
            } else if (audioSource == AudioSource.INTERNAL && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.e(TAG, "Internal audio capture requires Android 10 (Q) or higher")
            }
            
            // Determine overall success
            val success = when (audioSource) {
                AudioSource.MICROPHONE -> micSuccess
                AudioSource.INTERNAL -> internalSuccess
                AudioSource.BOTH -> micSuccess || internalSuccess // At least one should work
                AudioSource.NONE -> true
            }
            
            if (success) {
                isRecording = true
                Log.d(TAG, "Audio recording started: $audioSource (Mic: $micSuccess, Internal: $internalSuccess)")
            } else {
                // Clean up if failed
                stop()
                Log.e(TAG, "Failed to start any audio recorder")
            }
            
            return success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecorder", e)
            stop()
            return false
        }
    }
    
    /**
     * Read audio data into buffer
     * Handles mixing when both sources are active
     */
    fun read(buffer: ByteArray, size: Int): Int {
        if (!isRecording) return -1
        
        return when (currentAudioSource) {
            AudioSource.MICROPHONE -> {
                // Read from mic only
                micRecorder?.read(buffer, 0, size) ?: -1
            }
            AudioSource.INTERNAL -> {
                // Read from internal only
                internalRecorder?.read(buffer, 0, size) ?: -1
            }
            AudioSource.BOTH -> {
                // Mix both sources
                mixAudioSources(buffer, size)
            }
            AudioSource.NONE -> -1
        }
    }
    
    /**
     * Mix audio from both microphone and internal sources
     * Uses 50% gain for each source to prevent clipping
     */
    private fun mixAudioSources(outputBuffer: ByteArray, size: Int): Int {
        val micBuffer = ByteArray(size)
        val internalBuffer = ByteArray(size)
        
        val micRead = micRecorder?.read(micBuffer, 0, size) ?: 0
        val internalRead = internalRecorder?.read(internalBuffer, 0, size) ?: 0
        
        // If neither source has data, return -1
        if (micRead <= 0 && internalRead <= 0) {
            return -1
        }
        
        // Mix the audio samples (PCM 16-bit)
        // We process 2 bytes at a time (16-bit samples)
        val maxRead = maxOf(micRead, internalRead)
        
        for (i in 0 until maxRead step 2) {
            // Read 16-bit samples (little-endian)
            val micSample = if (i < micRead) {
                ((micBuffer[i + 1].toInt() shl 8) or (micBuffer[i].toInt() and 0xFF)).toShort()
            } else {
                0
            }
            
            val internalSample = if (i < internalRead) {
                ((internalBuffer[i + 1].toInt() shl 8) or (internalBuffer[i].toInt() and 0xFF)).toShort()
            } else {
                0
            }
            
            // Mix by addition with clamping to prevent overflow (preserves volume)
            var mixedInt = micSample.toInt() + internalSample.toInt()
            if (mixedInt > 32767) mixedInt = 32767
            else if (mixedInt < -32768) mixedInt = -32768
            
            val mixed = mixedInt.toShort()
            
            // Write back to output buffer (little-endian)
            outputBuffer[i] = (mixed.toInt() and 0xFF).toByte()
            outputBuffer[i + 1] = ((mixed.toInt() shr 8) and 0xFF).toByte()
        }
        
        return maxRead
    }
    
    fun getBufferSize(): Int = bufferSize
    
    fun stop() {
        isRecording = false
        
        try {
            micRecorder?.stop()
            micRecorder?.release()
            micRecorder = null
            Log.d(TAG, "Microphone recorder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping microphone recorder", e)
        }
        
        try {
            internalRecorder?.stop()
            internalRecorder?.release()
            internalRecorder = null
            Log.d(TAG, "Internal audio recorder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping internal recorder", e)
        }
        
        Log.d(TAG, "AudioRecorder stopped")
    }
}
