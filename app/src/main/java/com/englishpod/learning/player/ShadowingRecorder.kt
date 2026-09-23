package com.englishpod.learning.player

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File

/**
 * Records the learner repeating a sentence so it can be compared with the original audio.
 * Files live in the app cache and are overwritten on every take.
 */
class ShadowingRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var output: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): File? {
        stopPlayback()
        releaseRecorder()
        val dir = File(context.cacheDir, "shadow").apply { mkdirs() }
        val target = File(dir, "take.m4a")
        return try {
            @Suppress("DEPRECATION")
            val mediaRecorder = MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(96_000)
            if (target.exists()) target.delete()
            mediaRecorder.setOutputFile(target.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            output = target
            target
        } catch (_: Exception) {
            releaseRecorder()
            null
        }
    }

    /** Returns the finished recording, or null when nothing usable was captured. */
    fun stop(): File? {
        val mediaRecorder = recorder ?: return null
        recorder = null
        return try {
            mediaRecorder.stop()
            mediaRecorder.release()
            output?.takeIf { it.exists() && it.length() > 0L }
        } catch (_: Exception) {
            runCatching { mediaRecorder.release() }
            output?.takeIf { it.exists() && it.length() > 0L }
        }
    }

    fun play(file: File, onFinished: () -> Unit = {}) {
        stopPlayback()
        runCatching {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnCompletionListener {
                it.release()
                player = null
                onFinished()
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
        }
    }

    fun stopPlayback() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }

    fun release() {
        releaseRecorder()
        stopPlayback()
    }

    private fun releaseRecorder() {
        runCatching {
            recorder?.let {
                it.reset()
                it.release()
            }
        }
        recorder = null
    }
}
