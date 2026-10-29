package com.englishpod.learning

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import com.englishpod.learning.data.CourseApi
import com.englishpod.learning.data.DictDatabase
import com.englishpod.learning.data.LocalStore
import com.englishpod.learning.data.Repository
import com.englishpod.learning.data.Settings
import com.englishpod.learning.player.AudioEngine
import com.englishpod.learning.player.TtsPlayer

class EnglishPodApp : Application() {

    lateinit var store: LocalStore
        private set
    lateinit var repository: Repository
        private set

    /** Offline ECDICT subset used by word lookup and the vocabulary size test. */
    val dict: DictDatabase by lazy { DictDatabase(this) }

    /** Platform TTS for words and lines that have no course audio. */
    val tts: TtsPlayer by lazy { TtsPlayer(this) }

    /** Set when the home-screen widget asks for one-tap resume of the last course. */
    @Volatile
    var autoResumeRequested: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = LocalStore(this)
        repository = Repository(
            api = CourseApi { store.settings.value.baseUrl },
            store = store,
            context = this,
        )
        AudioEngine.init(this, store)
        repository.refresh()
        createReminderChannel()
        if (store.settings.value.reminderEnabled) {
            val slot = Settings.REMINDER_SLOTS[store.settings.value.reminderSlot.coerceIn(0, 3)]
            com.englishpod.learning.work.ReminderWorker.schedule(this, slot.first, slot.second)
        }
    }

    private fun createReminderChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "学习提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "每日复习与打卡提醒"
        }
        manager.createNotificationChannel(channel)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) store.flushNow()
    }

    companion object {
        lateinit var instance: EnglishPodApp
            private set

        const val REMINDER_CHANNEL_ID = "english_pod_reminder"

        val store: LocalStore get() = instance.store
        val repository: Repository get() = instance.repository
        val dict: DictDatabase get() = instance.dict
        val tts: TtsPlayer get() = instance.tts

        /** Consumed once by the course screen to auto-continue the last position. */
        fun consumeAutoResume(): Boolean {
            val requested = instance.autoResumeRequested
            instance.autoResumeRequested = false
            return requested
        }
    }
}
