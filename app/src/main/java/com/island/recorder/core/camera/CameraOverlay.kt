package com.island.recorder.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.island.recorder.R
import com.island.recorder.service.FloatingControlService
import kotlin.math.abs

/**
 * Manages the floating camera overlay using CameraX
 */
class CameraOverlay(private val context: Context) : LifecycleOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lifecycleRegistry = LifecycleRegistry(this)
    
    private var overlayView: View? = null
    private var previewView: PreviewView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    private var cameraProvider: ProcessCameraProvider? = null
    
    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (overlayView != null) return

        // Create layout params
        layoutParams = WindowManager.LayoutParams(
            300, 400, // Default size
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        // Inflate view (Using a simple FrameLayout + PreviewView programmatically for now to avoid XML dep if missing)
        val container = FrameLayout(context)
        container.background = ContextCompat.getDrawable(context, android.R.drawable.dialog_holo_light_frame)
        
        previewView = PreviewView(context)
        container.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // Add close button (Top Right)
        val closeButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            setOnClickListener {
                stop()
                // Stop the service as well if the user closes the camera manually
                context.stopService(Intent(context, FloatingControlService::class.java))
            }
        }
        val closeParams = FrameLayout.LayoutParams(60, 60).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, 10, 10, 0)
        }
        container.addView(closeButton, closeParams)

        overlayView = container

        // Add touch listener for dragging
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams!!.x
                        initialY = layoutParams!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        // Add to window
        try {
            windowManager.addView(overlayView, layoutParams)
            startCamera()
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView?.surfaceProvider)

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
            previewView = null
        }
    }
}
