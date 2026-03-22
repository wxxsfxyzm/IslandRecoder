package com.island.recorder.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import com.island.recorder.R
import com.island.recorder.core.camera.CameraOverlay
import kotlin.math.abs

/**
 * Service for floating control overlay with pause/stop/camera controls
 */
class FloatingControlService : Service() {
    
    private var cameraOverlay: CameraOverlay? = null
    private var controlOverlay: View? = null
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    
    companion object {
        private const val TAG = "FloatingControlService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingControlService created")
        cameraOverlay = CameraOverlay(this)
        createControlOverlay()
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun createControlOverlay() {
        // Create layout params for overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 100
        }
        
        // Create control panel with buttons
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
        }
        
        // Pause button
        val pauseButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                Log.d(TAG, "Pause clicked")
                sendBroadcast(Intent(RecorderService.ACTION_PAUSE_RECORDING))
            }
        }
        container.addView(pauseButton)
        
        // Stop button
        val stopButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_delete)
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                Log.d(TAG, "Stop clicked")
                sendBroadcast(Intent(RecorderService.ACTION_STOP_RECORDING))
            }
        }
        container.addView(stopButton)
        
        // Close button
        val closeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                stopSelf()
            }
        }
        container.addView(closeButton)
        
        // Make overlay draggable
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (initialTouchX - event.rawX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        
                        if (abs(deltaX) > 5 || abs(deltaY) > 5) {
                            params.x = initialX + deltaX
                            params.y = initialY + deltaY
                            windowManager.updateViewLayout(container, params)
                        }
                        return true
                    }
                }
                return false
            }
        })
        
        controlOverlay = container
        windowManager.addView(container, params)
        Log.d(TAG, "Control overlay created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingControlService started")
        cameraOverlay?.show()
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingControlService destroyed")
        
        cameraOverlay?.stop()
        cameraOverlay = null
        
        controlOverlay?.let { windowManager.removeView(it) }
        controlOverlay = null
    }
}
