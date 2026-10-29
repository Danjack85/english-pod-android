package com.englishpod.learning.player

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Thin wrapper over the platform TextToSpeech used to read words and example lines that have no
 * course audio (manually added wordbook entries, arbitrary words looked up in the dictionary).
 *
 * Availability depends on the installed engine and whether its English voice data has been
 * downloaded; [state] surfaces that so the UI can guide the user to install the voice pack.
 */
class TtsPlayer(context: Context) {

    enum class Status { IDLE, READY, NEEDS_VOICE_DATA, UNAVAILABLE }

    data class TtsState(
        val status: Status = Status.IDLE,
        val speaking: Boolean = false,
        val rate: Float = 1f,
    )

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var pendingText: String? = null
    private var pendingRate: Float = 1f
    private var utteranceCounter = 0

    fun speak(text: String, rate: Float = 1f, onDone: () -> Unit = {}) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val engine = tts
        if (engine == null) {
            pendingText = trimmed
            pendingRate = rate
            tts = TextToSpeech(appContext) { status -> onInit(status, onDone) }
            return
        }
        utter(engine, trimmed, rate, onDone)
    }

    fun stop() {
        runCatching { tts?.stop() }
        _state.value = _state.value.copy(speaking = false)
    }

    fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        _state.value = TtsState()
    }

    private fun onInit(status: Int, onDone: () -> Unit) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            _state.value = TtsState(status = Status.UNAVAILABLE)
            return
        }
        val result = engine.setLanguage(Locale.US)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = true)
            }

            override fun onDone(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = false)
            }

            @Deprecated("Kept for older engines")
            override fun onError(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = false)
            }
        })
        when (result) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                _state.value = TtsState(status = Status.NEEDS_VOICE_DATA)
            }
            else -> {
                _state.value = TtsState(status = Status.READY)
                pendingText?.let { text ->
                    utter(engine, text, pendingRate, onDone)
                    pendingText = null
                }
            }
        }
    }

    private fun utter(engine: TextToSpeech, text: String, rate: Float, onDone: () -> Unit) {
        if (engine.setLanguage(Locale.US) !in intArrayOf(
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
            )
        ) {
            _state.value = TtsState(status = Status.NEEDS_VOICE_DATA)
            return
        }
        engine.setSpeechRate(rate)
        utteranceCounter += 1
        engine.setOnDone(onDone)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ep-$utteranceCounter")
        _state.value = TtsState(status = Status.READY, speaking = true, rate = rate)
    }

    private var onDoneCallback: (() -> Unit)? = null

    private fun TextToSpeech.setOnDone(callback: () -> Unit) {
        onDoneCallback = callback
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = true)
            }

            override fun onDone(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = false)
                onDoneCallback?.invoke()
            }

            @Deprecated("Kept for older engines")
            override fun onError(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = false)
            }
        })
    }

    companion object {
        /** System settings screen where the user can download missing English voice data. */
        fun voiceDataInstallIntent(): Intent =
            Intent("android.speech.tts.engine.INSTALL_TTS_DATA")
    }
}
