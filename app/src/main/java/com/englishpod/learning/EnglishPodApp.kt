package com.englishpod.learning

import android.app.Application
import android.content.ComponentCallbacks2
import com.englishpod.learning.data.CourseApi
import com.englishpod.learning.data.LocalStore
import com.englishpod.learning.data.Repository
import com.englishpod.learning.player.AudioEngine

class EnglishPodApp : Application() {

    lateinit var store: LocalStore
        private set
    lateinit var repository: Repository
        private set

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
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) store.flushNow()
    }

    companion object {
        lateinit var instance: EnglishPodApp
            private set

        val store: LocalStore get() = instance.store
        val repository: Repository get() = instance.repository
    }
}
