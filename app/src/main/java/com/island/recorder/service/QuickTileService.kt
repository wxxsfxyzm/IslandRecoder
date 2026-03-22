package com.island.recorder.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.island.recorder.R
import com.island.recorder.RecordingShortcutActivity
import com.island.recorder.data.RecordingState

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
        qsTile?.apply {
            state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (isRecording) getString(R.string.tile_stop_recording) else getString(R.string.tile_start_recording)
            updateTile()
        }
    }
}
