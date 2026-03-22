package com.island.recorder.data

/**
 * Represents the current state of the recording process
 */
sealed class RecordingState {
    /**
     * No recording in progress
     */
    data object Idle : RecordingState()
    
    /**
     * Recording is in progress
     * @param durationMs Current recording duration in milliseconds
     */
    data class Recording(val durationMs: Long = 0) : RecordingState()
    
    /**
     * Recording is paused
     * @param durationMs Duration recorded before pause
     */
    data class Paused(val durationMs: Long) : RecordingState()
    
    /**
     * Processing/encoding the recorded video
     * @param progress Progress percentage (0-100)
     */
    data class Processing(val progress: Int = 0) : RecordingState()
    
    /**
     * Recording failed with error
     * @param error Error message
     */
    data class Error(val error: String) : RecordingState()
}
