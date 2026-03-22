package com.island.recorder.utils

import android.util.Log
import java.io.DataOutputStream

/**
 * Utility for root-related operations and system settings via shell
 */
object RootUtils {
    private const val TAG = "RootUtils"

    /**
     * Check if the device is rooted
     */
    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Set 'show touches' system setting (requires root)
     */
    fun setShowTouches(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("settings put system show_touches $value\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set show_touches via root", e)
            false
        }
    }
}
