package com.englishpod.learning.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.englishpod.learning.data.AudioSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays a single excerpt (one sentence or one vocabulary item) for the training screens.
 *
 * It owns its own player so that drills never disturb the main [AudioEngine] session, and it uses
 * Media3's clipping configuration so a clip simply ends at its stop position.
 */
class ClipPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        .build()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private var onFinished: (() -> Unit)? = null
    private var lastSource: AudioSource? = null
    private var lastStart = 0L
    private var lastEnd: Long? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playing.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _playing.value = false
                    onFinished?.invoke()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playing.value = false
            }
        })
    }

    fun play(
        source: AudioSource,
        startMs: Long,
        endMs: Long?,
        speed: Float = 1f,
    ) {
        lastSource = source
        lastStart = startMs.coerceAtLeast(0L)
        lastEnd = endMs?.takeIf { it > lastStart }

        val builder = MediaItem.Builder().setUri(source.uri)
        val clipping = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(lastStart)
        lastEnd?.let { clipping.setEndPositionMs(it) }

        player.setMediaItem(builder.setClippingConfiguration(clipping.build()).build())
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
    }

    /** Replays the current clip, optionally at a slower pace. */
    fun replay(speed: Float = 1f) {
        player.setPlaybackSpeed(speed)
        player.seekTo(0L)
        player.play()
    }

    /** Repeats the clip from the beginning; safe to call before the first play. */
    fun replayOrRestart(speed: Float = 1f) {
        val source = lastSource
        if (source == null) return
        if (player.mediaItemCount == 0) {
            play(source, lastStart, lastEnd, speed)
        } else {
            replay(speed)
        }
    }

    fun stop() {
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
        _playing.value = false
    }

    fun setOnFinished(block: (() -> Unit)?) {
        onFinished = block
    }

    fun release() {
        runCatching { player.release() }
    }
}
