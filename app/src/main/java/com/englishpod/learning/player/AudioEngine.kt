package com.englishpod.learning.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.data.AudioSource
import com.englishpod.learning.data.CourseDetail
import com.englishpod.learning.data.Cue
import com.englishpod.learning.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoopMode { OFF, SENTENCE, AB }

data class PlayerState(
    val courseId: String? = null,
    val title: String = "",
    val level: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val cueIndex: Int = -1,
    val loopMode: LoopMode = LoopMode.OFF,
    val sentenceLoopIndex: Int = -1,
    val abStartMs: Long = -1L,
    val abEndMs: Long = -1L,
    val sleepRemainingSec: Int = -1,
    val sentenceMode: Boolean = false,
    val skipSilence: Boolean = false,
    val error: String? = null,
    val ready: Boolean = false,
    val finished: Boolean = false,
) {
    val cueLabel: String
        get() = when {
            loopMode == LoopMode.SENTENCE && sentenceLoopIndex >= 0 -> "单句循环 · 第 ${sentenceLoopIndex + 1} 句"
            loopMode == LoopMode.AB && abEndMs > abStartMs -> "A-B 复读 ${Fmt.clock(abStartMs)} - ${Fmt.clock(abEndMs)}"
            sentenceMode -> "逐句精听"
            else -> "连续播放"
        }
}

/**
 * Holds the single ExoPlayer instance for the app and implements the learning specific playback
 * behaviours: per-sentence looping, A-B repeat, sentence-by-sentence practice and a sleep timer.
 */
object AudioEngine {

    private var player: ExoPlayer? = null
    private var store: LocalStore? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private const val RESUME_REWIND_MS = 3_000L
    private const val RESUME_REWIND_MIN_PAUSE_MS = 5_000L

    private var cues: List<Cue> = emptyList()
    private var ticker: Job? = null
    private var autoAdvance: Job? = null
    private var sleepJob: Job? = null
    private var lastTickAt = 0L
    private var unreportedSeconds = 0L
    private var lastSavedPositionMs = 0L
    private var pausedBySentence = false
    private var autoAdvanceSuppressed = false
    private var lastPauseAt = 0L

    fun init(context: Context, localStore: LocalStore) {
        if (player != null) return
        store = localStore
        appContext = context.applicationContext
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val buffering = playbackState == Player.STATE_BUFFERING
                        _state.value = _state.value.copy(
                            isBuffering = buffering,
                            durationMs = duration.takeIf { it > 0 } ?: _state.value.durationMs,
                            finished = playbackState == Player.STATE_ENDED,
                        )
                        if (playbackState == Player.STATE_READY) {
                            _state.value = _state.value.copy(ready = true, durationMs = duration.takeIf { it > 0 } ?: _state.value.durationMs)
                        }
                        if (playbackState == Player.STATE_ENDED) savePosition(force = true)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = _state.value.copy(isPlaying = isPlaying)
                        if (isPlaying) startTicker() else stopTicker()
                        if (!isPlaying) savePosition(force = true)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _state.value = _state.value.copy(
                            error = "音频加载失败：${error.errorCodeName}",
                            isBuffering = false,
                        )
                    }
                })
            }
        player?.setSkipSilenceEnabled(localStore.settings.value.skipSilence)
    }

    private fun requirePlayer(): ExoPlayer? = player

    // -------------------------------------------------------------------- loading

    suspend fun open(
        detail: CourseDetail,
        source: AudioSource,
        startAtMs: Long = 0L,
        autoPlay: Boolean = true,
    ) {
        val exo = requirePlayer() ?: return
        savePosition(force = true)

        cues = detail.cues
        pausedBySentence = false
        autoAdvance?.cancel()
        unreportedSeconds = 0L

        _state.value = PlayerState(
            courseId = detail.id,
            title = detail.course.title,
            level = detail.course.level,
            durationMs = detail.course.durationMs,
            speed = store?.settings?.value?.speed ?: 1f,
            cueIndex = indexAt(startAtMs.toDouble() / 1000.0),
            ready = false,
        )

        withContext(Dispatchers.Main) {
            exo.setMediaItem(MediaItem.fromUri(source.uri))
            exo.setPlaybackSpeed(store?.settings?.value?.speed ?: 1f)
            exo.prepare()
            exo.seekTo(startAtMs.coerceAtLeast(0L))
            exo.playWhenReady = autoPlay
        }
        if (autoPlay) appContext?.let { PlaybackService.ensureRunning(it) }
        if (!autoPlay) _state.value = _state.value.copy(isPlaying = false)
    }

    // ---------------------------------------------------------------- transport

    fun togglePlay() {
        val exo = requirePlayer() ?: return
        if (exo.isPlaying) pause() else play()
    }

    fun play() {
        val exo = requirePlayer() ?: return
        autoAdvance?.cancel()
        pausedBySentence = false
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0L)

        // Optionally step back a little so a resumed session picks the thread up again.
        val pausedAt = lastPauseAt
        val settings = store?.settings?.value
        if (settings?.rewindOnResume == true && pausedAt > 0L &&
            System.currentTimeMillis() - pausedAt > RESUME_REWIND_MIN_PAUSE_MS &&
            exo.currentPosition > RESUME_REWIND_MS
        ) {
            exo.seekTo(exo.currentPosition - RESUME_REWIND_MS)
        }
        lastPauseAt = 0L

        exo.play()
        appContext?.let { PlaybackService.ensureRunning(it) }
    }

    fun pause() {
        val exo = requirePlayer() ?: return
        exo.pause()
        lastPauseAt = System.currentTimeMillis()
        savePosition(force = true)
    }

    /** Skips the silent gaps between sentences; persisted in settings. */
    fun setSkipSilence(enabled: Boolean) {
        player?.setSkipSilenceEnabled(enabled)
        store?.updateSettings { it.copy(skipSilence = enabled) }
        _state.value = _state.value.copy(skipSilence = enabled)
    }

    fun seekTo(positionMs: Long) {
        val exo = requirePlayer() ?: return
        val target = positionMs.coerceIn(0L, maxOf(0L, exo.duration))
        exo.seekTo(target)
        _state.value = _state.value.copy(
            positionMs = target,
            cueIndex = indexAt(target.toDouble() / 1000.0),
            finished = false,
        )
    }

    fun skipBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    fun setSpeed(speed: Float) {
        val exo = requirePlayer() ?: return
        exo.setPlaybackSpeed(speed)
        store?.updateSettings { it.copy(speed = speed) }
        _state.value = _state.value.copy(speed = speed)
    }

    // ------------------------------------------------------------------ sentences

    fun currentSentenceIndex(): Int = _state.value.cueIndex

    fun nextSentence() {
        val index = _state.value.cueIndex
        playSentence(index + 1)
    }

    fun previousSentence() {
        val index = _state.value.cueIndex
        // Restart the current sentence first, like most language players do.
        val active = cues.getOrNull(index)
        if (active != null && _state.value.positionMs - (active.start * 1000).toLong() > 1500L) {
            playSentence(index)
        } else {
            playSentence(index - 1)
        }
    }

    fun playSentence(index: Int, play: Boolean = true) {
        val cue = cues.getOrNull(index) ?: return
        autoAdvance?.cancel()
        pausedBySentence = false
        val target = (cue.start * 1000).toLong()
        seekTo(target)
        if (_state.value.loopMode == LoopMode.SENTENCE) {
            _state.value = _state.value.copy(sentenceLoopIndex = index)
        }
        if (play) play()
    }

    fun setLoopMode(mode: LoopMode) {
        val index = _state.value.cueIndex
        autoAdvance?.cancel()
        pausedBySentence = false
        _state.value = _state.value.copy(
            loopMode = mode,
            sentenceLoopIndex = if (mode == LoopMode.SENTENCE) maxOf(index, 0) else -1,
        )
        if (mode == LoopMode.SENTENCE) {
            playSentence(maxOf(index, 0))
        }
    }

    fun setSentenceLoop(index: Int) {
        _state.value = _state.value.copy(loopMode = LoopMode.SENTENCE, sentenceLoopIndex = index)
        playSentence(index)
    }

    fun setAbLoop(startMs: Long, endMs: Long) {
        if (endMs <= startMs) return
        _state.value = _state.value.copy(
            loopMode = LoopMode.AB,
            abStartMs = startMs,
            abEndMs = endMs,
        )
        seekTo(startMs)
    }

    /** Uses the current subtitle line as the A-B window. */
    fun loopCurrentSentence() {
        val cue = cues.getOrNull(_state.value.cueIndex) ?: return
        setAbLoop((cue.start * 1000).toLong(), (cue.end * 1000).toLong())
    }

    fun clearLoop() {
        _state.value = _state.value.copy(loopMode = LoopMode.OFF, sentenceLoopIndex = -1)
    }

    fun setSentenceMode(enabled: Boolean) {
        autoAdvance?.cancel()
        pausedBySentence = false
        _state.value = _state.value.copy(sentenceMode = enabled)
    }

    /** Keeps sentence mode parked on the current line, used while the learner is recording. */
    fun suppressAutoAdvance(suppress: Boolean) {
        autoAdvanceSuppressed = suppress
        if (suppress) autoAdvance?.cancel()
    }

    // ---------------------------------------------------------------- sleep timer

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepRemainingSec = -1)
            return
        }
        _state.value = _state.value.copy(sleepRemainingSec = minutes * 60)
        sleepJob = scope.launch {
            var remaining = minutes * 60
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining -= 1
                _state.value = _state.value.copy(sleepRemainingSec = remaining)
            }
            if (remaining <= 0) {
                pause()
                _state.value = _state.value.copy(sleepRemainingSec = -1)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _state.value = _state.value.copy(sleepRemainingSec = -1)
    }

    fun release() {
        ticker?.cancel()
        autoAdvance?.cancel()
        sleepJob?.cancel()
        player?.release()
        player = null
    }

    fun stopAndClear() {
        pause()
        _state.value = PlayerState()
        cues = emptyList()
        appContext?.let { PlaybackService.stopPlaybackService(it) }
    }

    // ------------------------------------------------------------------- internal

    private fun indexAt(seconds: Double): Int {
        if (cues.isEmpty()) return -1
        var result = -1
        for (i in cues.indices) {
            if (cues[i].start <= seconds) result = i else break
        }
        return result
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        lastTickAt = System.currentTimeMillis()
        ticker = scope.launch {
            while (isActive) {
                delay(120L)
                tick()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private suspend fun tick() {
        val exo = player ?: return
        val position = exo.currentPosition
        val state = _state.value
        val index = indexAt(position.toDouble() / 1000.0)

        val now = System.currentTimeMillis()
        if (state.isPlaying) {
            val elapsed = (now - lastTickAt).coerceIn(0L, 2000L)
            unreportedSeconds += elapsed
            if (unreportedSeconds >= 5000L) {
                reportStudy(unreportedSeconds / 1000L)
                unreportedSeconds = 0L
            }
        }
        lastTickAt = now

        if (state.isPlaying) enforceLearningRules(position, index)

        val fresh = _state.value
        if (fresh.positionMs != position || fresh.cueIndex != index) {
            _state.value = fresh.copy(positionMs = position, cueIndex = index)
        }
        if (position - lastSavedPositionMs > 15_000L) savePosition(force = false)
    }

    /** Applies the loop / sentence-practice behaviour for the current position. */
    private fun enforceLearningRules(position: Long, index: Int) {
        val exo = player ?: return
        val state = _state.value
        val cue = cues.getOrNull(index) ?: return
        val cueEndMs = (cue.end * 1000).toLong()

        when {
            state.loopMode == LoopMode.SENTENCE -> {
                val loopCue = cues.getOrNull(state.sentenceLoopIndex) ?: cue
                val end = (loopCue.end * 1000).toLong()
                if (position >= end) exo.seekTo((loopCue.start * 1000).toLong())
            }

            state.loopMode == LoopMode.AB && state.abEndMs > state.abStartMs -> {
                if (position >= state.abEndMs) exo.seekTo(state.abStartMs)
            }

            state.sentenceMode && !pausedBySentence -> {
                if (position >= cueEndMs) {
                    pausedBySentence = true
                    exo.pause()
                    scheduleAdvance(index)
                }
            }
        }
    }

    private fun scheduleAdvance(index: Int) {
        if (autoAdvanceSuppressed) return
        val settings = store?.settings?.value ?: return
        val gap = settings.sentencePauseMs.toLong()
        autoAdvance?.cancel()
        autoAdvance = scope.launch {
            if (gap > 0L) delay(gap)
            val current = _state.value
            if (!current.sentenceMode || current.isPlaying) return@launch
            if (settings.autoPlayNext) {
                pausedBySentence = false
                playSentence(index + 1)
            }
        }
    }

    private suspend fun reportStudy(seconds: Long) {
        val id = _state.value.courseId ?: return
        store?.addStudySeconds(id, seconds)
        store?.recordProgress(
            courseId = id,
            positionMs = _state.value.positionMs,
            durationMs = _state.value.durationMs,
            deltaSeconds = 0L,
        )
    }

    private fun savePosition(force: Boolean) {
        val id = _state.value.courseId ?: return
        val position = player?.currentPosition ?: _state.value.positionMs
        if (!force && kotlin.math.abs(position - lastSavedPositionMs) < 3000L) return
        lastSavedPositionMs = position
        val duration = player?.duration?.takeIf { it > 0 } ?: _state.value.durationMs
        val pending = unreportedSeconds / 1000L
        unreportedSeconds -= pending * 1000L
        scope.launch {
            store?.recordProgress(id, position, duration, pending)
        }
    }
}
