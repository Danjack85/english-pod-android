package com.englishpod.learning.player

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.englishpod.learning.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Media3 session service wrapping the shared [AudioEngine] player.
 *
 * The platform media notification (lock screen seek bar, resumption card) and media button
 * routing (Bluetooth / wired headset) come for free; the sentence-skip buttons are exposed
 * through the session's custom layout so they appear in the media notification.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player: ExoPlayer = AudioEngine.currentExoPlayer() ?: run {
            stopSelf()
            return
        }

        val prevCommand = SessionCommand(COMMAND_PREV_SENTENCE, Bundle.EMPTY)
        val nextCommand = SessionCommand(COMMAND_NEXT_SENTENCE, Bundle.EMPTY)

        val callback = object : MediaSession.Callback {
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    COMMAND_PREV_SENTENCE -> AudioEngine.previousSentence()
                    COMMAND_NEXT_SENTENCE -> AudioEngine.nextSentence()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            // Headset / Bluetooth buttons: sentence skip is far more useful than item skip
            // because the app plays a single media item per course.
            override fun onMediaButtonEvent(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                intent: Intent,
            ): Boolean {
                val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                when (event?.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        AudioEngine.previousSentence()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        AudioEngine.nextSentence()
                        return true
                    }
                }
                return super.onMediaButtonEvent(mediaSession, controller, intent)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setCustomLayout(
                ImmutableList.of(
                    androidx.media3.session.CommandButton.Builder()
                        .setDisplayName("上一句")
                        .setIconResId(com.englishpod.learning.R.drawable.ic_skip_previous)
                        .setSessionCommand(prevCommand)
                        .build(),
                    androidx.media3.session.CommandButton.Builder()
                        .setDisplayName("下一句")
                        .setIconResId(com.englishpod.learning.R.drawable.ic_skip_next)
                        .setSessionCommand(nextCommand)
                        .build(),
                ),
            )
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val COMMAND_PREV_SENTENCE = "com.englishpod.learning.PREV_SENTENCE"
        const val COMMAND_NEXT_SENTENCE = "com.englishpod.learning.NEXT_SENTENCE"

        /** Starts the service so the platform media notification and session come up with playback. */
        fun ensureRunning(context: android.content.Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    android.content.Intent(context, PlaybackService::class.java),
                )
            }
        }

        fun stopPlaybackService(context: android.content.Context) {
            runCatching { context.stopService(android.content.Intent(context, PlaybackService::class.java)) }
        }
    }
}
