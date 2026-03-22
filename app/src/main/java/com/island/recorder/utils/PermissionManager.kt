package com.island.recorder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Centralized permission management
 */
object PermissionManager {
    
    /**
     * All permissions required for full functionality
     */
    val ALL_PERMISSIONS = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()
    
    /**
     * Essential permissions required to start recording
     */
    val ESSENTIAL_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )
    
    /**
     * Check if a specific permission is granted
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == 
            PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if all essential permissions are granted
     */
    fun hasEssentialPermissions(context: Context): Boolean {
        return ESSENTIAL_PERMISSIONS.all { isPermissionGranted(context, it) }
    }
    
    /**
     * Check if all permissions are granted
     */
    fun hasAllPermissions(context: Context): Boolean {
        return ALL_PERMISSIONS.all { isPermissionGranted(context, it) }
    }
    
    /**
     * Get list of missing permissions
     */
    fun getMissingPermissions(context: Context): List<String> {
        return ALL_PERMISSIONS.filter { !isPermissionGranted(context, it) }
    }
    
    /**
     * Check if camera permission is granted (for facecam)
     */
    fun hasCameraPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.CAMERA)
    }
    
    /**
     * Check if audio permission is granted
     */
    fun hasAudioPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)
    }
    
    /**
     * Check if storage permissions are granted
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true // Scoped storage, no permission needed
        } else {
            isPermissionGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
