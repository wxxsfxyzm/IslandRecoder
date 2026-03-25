package com.island.recorder.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.graphics.drawable.Icon
import com.island.recorder.R
import com.island.recorder.RecordingShortcutActivity
import com.island.recorder.data.RecordingState
import com.island.recorder.data.TileStyle
import com.island.recorder.utils.PreferencesManager

class QuickTileService : TileService() {

    companion object {
        private const val TAG = "QuickTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        val isRecording = RecorderService.recordingState.value.let {
            it is RecordingState.Recording || it is RecordingState.Paused
        }
        updateTile(isRecording)
    }

    override fun onClick() {
        super.onClick()

        val state = RecorderService.recordingState.value

        if (state is RecordingState.Recording || state is RecordingState.Paused) {
            // Stop directly via service intent — no Activity needed
            Log.d(TAG, "Quick tile clicked - stopping recording")
            val intent = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_STOP_RECORDING
            }
            startService(intent)
        } else {
            // Launch transparent activity for MediaProjection consent
            Log.d(TAG, "Quick tile clicked - launching shortcut for recording")
            val intent = Intent(this, RecordingShortcutActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile(isRecording: Boolean) {
        val prefs = PreferencesManager(this)
        val tileStyle = prefs.getRecordingSettings().tileStyle
        val iconRes = if (tileStyle == TileStyle.APP_ICON) {
            R.drawable.ic_launcher_foreground
        } else {
            R.drawable.ic_record
        }

        qsTile?.apply {
            state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (isRecording) getString(R.string.tile_stop_recording) else getString(R.string.tile_start_recording)
            
            // Create scaled icon for tile (250% larger for app icon style)
            val icon = if (tileStyle == TileStyle.APP_ICON) {
                val drawable = androidx.core.content.ContextCompat.getDrawable(this@QuickTileService, iconRes)
                if (drawable != null) {
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        drawable.intrinsicWidth,
                        drawable.intrinsicHeight,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    
                    // Scale to 250%
                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * 2.5f).toInt(),
                        (bitmap.height * 2.5f).toInt(),
                        true
                    )
                    Icon.createWithBitmap(scaledBitmap)
                } else {
                    Icon.createWithResource(this@QuickTileService, iconRes)
                }
            } else {
                Icon.createWithResource(this@QuickTileService, iconRes)
            }
            
            updateTile()
        }
    }
}
