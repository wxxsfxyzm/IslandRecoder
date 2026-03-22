package com.island.recorder.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Detects shake gestures using accelerometer
 */
class ShakeDetector(
    context: Context,
    private val sensitivity: Float = 2.5f, // m/s² threshold
    private val onShakeDetected: () -> Unit
) : SensorEventListener {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    
    companion object {
        private const val TAG = "ShakeDetector"
        private const val SHAKE_THRESHOLD_MULTIPLIER = 9.8f // Convert to m/s²
        private const val UPDATE_THRESHOLD_MS = 100 // Minimum time between shake detections
    }
    
    /**
     * Start listening for shake events
     */
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Shake detector started with sensitivity: $sensitivity m/s²")
        } ?: run {
            Log.w(TAG, "Accelerometer not available")
        }
    }
    
    /**
     * Stop listening for shake events
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Shake detector stopped")
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        
        val currentTime = System.currentTimeMillis()
        
        // Only check for shakes every UPDATE_THRESHOLD_MS
        if ((currentTime - lastUpdate) < UPDATE_THRESHOLD_MS) return
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        if (lastUpdate != 0L) {
            // Calculate acceleration change
            val deltaX = x - lastX
            val deltaY = y - lastY
            val deltaZ = z - lastZ
            
            val acceleration = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
            
            // Check if acceleration exceeds threshold
            if (acceleration > sensitivity * SHAKE_THRESHOLD_MULTIPLIER) {
                Log.d(TAG, "Shake detected! Acceleration: $acceleration m/s²")
                onShakeDetected()
            }
        }
        
        lastUpdate = currentTime
        lastX = x
        lastY = y
        lastZ = z
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for shake detection
    }
}
