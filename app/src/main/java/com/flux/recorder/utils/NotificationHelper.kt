package com.flux.recorder.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.flux.recorder.MainActivity
import com.flux.recorder.R
import com.flux.recorder.service.RecorderService
import org.json.JSONArray
import org.json.JSONObject

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createRecordingNotification(
        durationMs: Long,
        isPaused: Boolean = false
    ): Notification {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timeStr = formatDuration(durationMs)
        val title = if (isPaused) {
            context.getString(R.string.notification_paused_title, timeStr)
        } else {
            context.getString(R.string.notification_recording_title, timeStr)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_record)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Pause / Resume action
        val pauseResumePending: PendingIntent
        if (isPaused) {
            val resumeIntent = Intent(context, RecorderService::class.java).apply {
                action = RecorderService.ACTION_RESUME_RECORDING
            }
            pauseResumePending = PendingIntent.getService(
                context, 1, resumeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_resume, context.getString(R.string.action_resume), pauseResumePending)
        } else {
            val pauseIntent = Intent(context, RecorderService::class.java).apply {
                action = RecorderService.ACTION_PAUSE_RECORDING
            }
            pauseResumePending = PendingIntent.getService(
                context, 1, pauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_pause, context.getString(R.string.action_pause), pauseResumePending)
        }

        // Stop action
        val stopIntent = Intent(context, RecorderService::class.java).apply {
            action = RecorderService.ACTION_STOP_RECORDING
        }
        val stopPending = PendingIntent.getService(
            context, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(R.drawable.ic_stop, context.getString(R.string.action_stop), stopPending)

        val notification = builder.build()

        // Add HyperOS Focus Island (超级岛) params
        addFocusIslandParams(notification, durationMs, isPaused, pauseResumePending, stopPending)

        return notification
    }

    private fun addFocusIslandParams(
        notification: Notification,
        durationMs: Long,
        isPaused: Boolean,
        pauseResumePending: PendingIntent,
        stopPending: PendingIntent
    ) {
        val now = System.currentTimeMillis()
        val timerWhen = now - durationMs
        val timerType = if (isPaused) 2 else 1

        val contentText = if (isPaused) {
            context.getString(R.string.notification_paused_message)
        } else {
            context.getString(R.string.notification_recording_message)
        }

        val timerInfo = JSONObject().apply {
            put("timerWhen", timerWhen)
            put("timerType", timerType)
            put("timerSystemCurrent", now)
        }

        val paramIsland = JSONObject().apply {
            put("islandPriority", 1)
            put("islandTimeout", 43200)
            put("islandProperty", 2)
            put("bigIslandArea", JSONObject().apply {
                put("imageTextInfoLeft", JSONObject().apply {
                    put("type", 1)
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_ticker")
                    })
                })
                put("sameWidthDigitInfo", JSONObject().apply {
                    put("timerInfo", timerInfo)
                })
            })
            put("smallIslandArea", JSONObject().apply {
                put("picInfo", JSONObject().apply {
                    put("type", 1)
                    put("pic", "miui.focus.pic_ticker")
                })
            })
        }

        // Determine icon keys based on pause state
        val action1IconKey = if (isPaused) "miui.focus.pic_resume" else "miui.focus.pic_pause"
        val action1IconDarkKey = if (isPaused) "miui.focus.pic_resume_dark" else "miui.focus.pic_pause_dark"

        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("updatable", true)
            put("enableFloat", false)
            put("business", "screen_recording")
            put("scene", "recorder")
            put("content", contentText)
            put("notifyId", "${context.packageName}$NOTIFICATION_ID")
            put("islandFirstFloat", false)
            put("ticker", contentText)
            put("tickerPic", "miui.focus.pic_ticker")
            put("tickerPicDark", "miui.focus.pic_ticker")
            put("param_island", paramIsland)
            put("animTextInfo", JSONObject().apply {
                put("timerInfo", timerInfo)
                put("animIconInfo", JSONObject().apply {
                    put("type", 1)
                    put("src", "voiceWaveBig")
                    put("number", 0)
                    put("loop", !isPaused)
                    put("autoplay", !isPaused)
                })
            })
            put("actions", JSONArray().apply {
                put(JSONObject().apply {
                    put("actionIntentType", 0)
                    put("action", "miui.focus.action_1")
                    put("type", 0)
                    put("actionIcon", action1IconKey)
                    put("actionIconDark", action1IconDarkKey)
                })
                put(JSONObject().apply {
                    put("actionIntentType", 0)
                    put("action", "miui.focus.action_2")
                    put("type", 0)
                    put("actionIcon", "miui.focus.pic_stop")
                    put("actionIconDark", "miui.focus.pic_stop_dark")
                })
            })
        }

        val focusParam = JSONObject().apply {
            put("protocol", 1)
            put("timerWhen", timerWhen)
            put("timerType", timerType)
            put("timerSystemCurrent", now)
            put("updatable", true)
            put("enableFloat", false)
            put("content", contentText)
            put("scene", "recorder")
            put("param_v2", paramV2)
        }

        // Notification.Action objects for PendingIntent
        val pauseResumeAction = Notification.Action.Builder(
            null,
            if (isPaused) context.getString(R.string.action_resume) else context.getString(R.string.action_pause),
            pauseResumePending
        ).build()
        val stopAction = Notification.Action.Builder(
            null, context.getString(R.string.action_stop), stopPending
        ).build()

        val actionsBundle = Bundle().apply {
            putParcelable("miui.focus.action_1", pauseResumeAction)
            putParcelable("miui.focus.action_2", stopAction)
        }

        // Icons bundle: light (for light bg) and dark (for dark bg) variants
        val picsBundle = Bundle().apply {
            // Ticker icon (camcorder in #FB382F)
            putParcelable("miui.focus.pic_ticker", Icon.createWithResource(context, R.drawable.ic_focus_ticker))
            // Light mode icons (black, for 焦点通知 light background)
            putParcelable("miui.focus.pic_pause", Icon.createWithResource(context, R.drawable.ic_focus_pause_light))
            putParcelable("miui.focus.pic_resume", Icon.createWithResource(context, R.drawable.ic_focus_resume_light))
            putParcelable("miui.focus.pic_stop", Icon.createWithResource(context, R.drawable.ic_focus_stop_light))
            // Dark mode icons (white, for 超级岛 dark background)
            putParcelable("miui.focus.pic_pause_dark", Icon.createWithResource(context, R.drawable.ic_focus_pause))
            putParcelable("miui.focus.pic_resume_dark", Icon.createWithResource(context, R.drawable.ic_focus_resume))
            putParcelable("miui.focus.pic_stop_dark", Icon.createWithResource(context, R.drawable.ic_focus_stop))
        }

        notification.extras.putString("miui.focus.param", focusParam.toString())
        notification.extras.putBundle("miui.focus.actions", actionsBundle)
        notification.extras.putBundle("miui.focus.pics", picsBundle)
    }

    fun updateNotification(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
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
