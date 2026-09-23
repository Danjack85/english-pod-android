package com.englishpod.learning.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.englishpod.learning.MainActivity
import com.englishpod.learning.R
import com.englishpod.learning.core.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps audio playing while the app is in the background and mirrors the
 * player state into a notification with transport controls.
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> AudioEngine.togglePlay()
            ACTION_NEXT -> AudioEngine.nextSentence()
            ACTION_PREV -> AudioEngine.previousSentence()
            ACTION_STOP -> {
                AudioEngine.pause()
                stopPlaybackService(this)
                return START_NOT_STICKY
            }
            else -> Unit
        }

        if (AudioEngine.state.value.courseId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundSafely(AudioEngine.state.value)
        if (watcher?.isActive != true) {
            watcher = scope.launch {
                AudioEngine.state.collect { state ->
                    if (state.courseId == null) {
                        stopPlaybackService(this@PlaybackService)
                        return@collect
                    }
                    if (foreground) notify(build(state))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(state: PlayerState) {
        val notification = build(state)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, foregroundServiceType())
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        } catch (_: Exception) {
            // Ignore: the service will simply not be promoted to foreground.
        }
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun build(state: PlayerState): Notification {
        val subtitle = buildString {
            append(state.level.ifBlank { "English Pod" })
            if (state.positionMs > 0L || state.durationMs > 0L) {
                append(" · ")
                append(Fmt.clock(state.positionMs))
                if (state.durationMs > 0L) append(" / ").append(Fmt.clock(state.durationMs))
            }
        }
        val text = if (state.isPlaying || state.isBuffering) {
            state.cueLabel
        } else {
            "已暂停 · 点击继续"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.title.ifBlank { "English Pod" })
            .setContentText(text)
            .setSubText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$subtitle"))
            .setContentIntent(contentIntent)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_skip_previous,
                "上一句",
                serviceIntent(ACTION_PREV, 1),
            )
            .addAction(
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (state.isPlaying) "暂停" else "播放",
                serviceIntent(ACTION_TOGGLE, 2),
            )
            .addAction(
                R.drawable.ic_skip_next,
                "下一句",
                serviceIntent(ACTION_NEXT, 3),
            )
            .addAction(
                R.drawable.ic_close,
                "关闭",
                serviceIntent(ACTION_STOP, 4),
            )
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.playback_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "english_pod_playback"
        const val NOTIFICATION_ID = 4201
        const val ACTION_TOGGLE = "com.englishpod.learning.TOGGLE"
        const val ACTION_NEXT = "com.englishpod.learning.NEXT"
        const val ACTION_PREV = "com.englishpod.learning.PREV"
        const val ACTION_STOP = "com.englishpod.learning.STOP"

        /** Starts the service when playback begins, so audio survives backgrounding. */
        fun ensureRunning(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stopPlaybackService(context: Context) {
            runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
        }
    }
}
