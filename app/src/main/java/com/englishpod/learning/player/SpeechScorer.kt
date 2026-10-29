package com.englishpod.learning.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.englishpod.learning.core.TextDiff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PronunciationScore(
    val accuracy: Float,
    val tokens: List<TextDiff.Token>,
    val heard: String,
)

/**
 * Scores a repeated sentence by recognising it with the platform speech recognizer and comparing
 * the heard words against the original line with [TextDiff].
 *
 * Recognition quality depends on the device (a Google-services recognizer works best); the class
 * never relies on the engine's confidence score, only on the transcript. Devices without any
 * recognizer report [isAvailable] == false and the UI hides the feature.
 */
class SpeechScorer(private val context: Context) {

    data class ScoringState(
        val phase: Phase = Phase.IDLE,
        val partial: String = "",
        val score: PronunciationScore? = null,
        val error: String? = null,
    ) {
        enum class Phase { IDLE, LISTENING, DONE, FAILED }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ScoringState())
    val state: StateFlow<ScoringState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var expected: String = ""
    private var watchdog: kotlinx.coroutines.Job? = null

    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /** Starts one scoring pass; the caller should show a "please repeat" hint. */
    fun start(expectedText: String) {
        if (!isAvailable()) {
            _state.value = ScoringState(
                phase = ScoringState.Phase.FAILED,
                error = "此手机没有可用的语音识别服务",
            )
            return
        }
        stop()
        expected = expectedText
        _state.value = ScoringState(phase = ScoringState.Phase.LISTENING)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "没有听清，靠近一点再试一次"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
                    else -> "识别失败（错误码 $error）"
                }
                _state.value = ScoringState(phase = ScoringState.Phase.FAILED, error = message)
            }

            override fun onResults(results: Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (heard.isBlank()) {
                    _state.value = ScoringState(
                        phase = ScoringState.Phase.FAILED,
                        error = "没有听清，再试一次",
                    )
                } else {
                    _state.value = ScoringState(
                        phase = ScoringState.Phase.DONE,
                        score = PronunciationScore(
                            accuracy = TextDiff.accuracy(expected, heard),
                            tokens = TextDiff.compare(expected, heard),
                            heard = heard,
                        ),
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _state.value = _state.value.copy(partial = partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        sr.setRecognitionListener(listener)
        sr.startListening(intent)
        recognizer = sr

        // Some engines never call back when the room is silent; bail out after a generous wait.
        watchdog = scope.launch {
            delay(15_000L)
            if (_state.value.phase == ScoringState.Phase.LISTENING && isActive) {
                stopListening()
                _state.value = ScoringState(
                    phase = ScoringState.Phase.FAILED,
                    error = "等待超时，请再试一次",
                )
            }
        }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun reset() {
        stop()
        _state.value = ScoringState()
    }

    fun release() {
        watchdog?.cancel()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun stop() {
        watchdog?.cancel()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}
