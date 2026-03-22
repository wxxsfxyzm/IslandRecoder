package com.island.recorder.data

import java.io.File

/**
 * Represents a saved recording
 */
data class Recording(
    val id: String,
    val file: File,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val timestamp: Long,
    val thumbnailPath: String? = null,
    val resolution: String,
    val frameRate: Int
) {
    /**
     * Format file size for display
     */
    fun getFormattedSize(): String {
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            else -> String.format("%.2f KB", kb)
        }
    }
    
    /**
     * Format duration for display (HH:MM:SS or MM:SS)
     */
    fun getFormattedDuration(): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
