package com.englishpod.learning.data

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for course content.
 *
 * Lookup order is always memory -> disk cache -> network, so a downloaded course keeps working
 * with no connection at all.
 */
class Repository(
    private val api: CourseApi,
    private val store: LocalStore,
    context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioDir = File(context.filesDir, "audio")
    private val metaDir = File(context.filesDir, "meta")
    private val listCache = File(context.filesDir, "courses-cache.json")

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _syncAt = MutableStateFlow(0L)
    val syncAt: StateFlow<Long> = _syncAt.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val detailMemory = ConcurrentHashMap<String, CourseDetail>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    init {
        readListCache()
    }

    val baseUrl: String get() = store.settings.value.baseUrl

    // ------------------------------------------------------------------- catalog

    fun refresh(force: Boolean = false) {
        if (_loading.value) return
        if (!force && _courses.value.isNotEmpty()) return
        _loading.value = true
        _error.value = null
        scope.launch {
            runCatching { api.listCourses() }
                .onSuccess { list ->
                    if (list.isNotEmpty()) {
                        _courses.value = list
                        writeListCache(list)
                        _syncAt.value = System.currentTimeMillis()
                    }
                }
                .onFailure { failure ->
                    _error.value = failure.message ?: "无法连接课程服务器"
                }
            _loading.value = false
        }
    }

    /** Re-reads the on-disk catalog; used after the server address changes. */
    fun invalidate() {
        detailMemory.clear()
        _courses.value = emptyList()
        _error.value = null
        readListCache()
    }

    private fun readListCache() {
        scope.launch {
            val list = runCatching {
                if (!listCache.exists()) return@runCatching emptyList<Course>()
                val root = JSONObject(listCache.readText())
                val array = root.optJSONArray("courses") ?: JSONArray()
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        add(api.parseCourse(item))
                    }
                }
            }.getOrDefault(emptyList())
            if (list.isNotEmpty() && _courses.value.isEmpty()) {
                _courses.value = list
                runCatching { _syncAt.value = JSONObject(listCache.readText()).optLong("syncedAt") }
            }
            refresh()
        }
    }

    private fun writeListCache(list: List<Course>) = runCatching {
        val array = JSONArray()
        list.forEach { course ->
            array.put(JSONObject().apply {
                put("id", course.id)
                put("level", course.level)
                put("number", course.number)
                put("fileNumber", course.fileNumber)
                put("title", course.title)
                put("originalTitle", course.originalTitle)
                put("duration", course.duration)
                put("available", course.available)
                put("noteCount", course.noteCount)
            })
        }
        listCache.writeText(JSONObject().apply {
            put("syncedAt", System.currentTimeMillis())
            put("courses", array)
        }.toString())
    }

    fun courseById(courseId: String): Course? = _courses.value.firstOrNull { it.id == courseId }

    fun search(query: String, level: String?): List<Course> {
        val keyword = query.trim().lowercase()
        return _courses.value.filter { course ->
            (level == null || course.level == level) &&
                (keyword.isEmpty() ||
                    course.title.lowercase().contains(keyword) ||
                    course.originalTitle.lowercase().contains(keyword) ||
                    course.id.contains(keyword))
        }
    }

    // -------------------------------------------------------------------- detail

    /** Memory -> downloaded metadata -> network. */
    suspend fun detail(courseId: String): CourseDetail {
        detailMemory[courseId]?.let { return it }
        val cached = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(metaDir, "$courseId.json")
                if (file.exists()) api.parseDetail(file.readText()) else null
            }.getOrNull()
        }
        if (cached != null) {
            detailMemory[courseId] = cached
            return cached
        }
        val fetched = api.courseDetail(courseId)
        detailMemory[courseId] = fetched
        return fetched
    }

    // ----------------------------------------------------------------- downloads

    fun isDownloaded(courseId: String): Boolean = audioFile(courseId).exists()

    private fun audioFile(courseId: String) = File(audioDir, "$courseId.m4a")

    private fun metaFile(courseId: String) = File(metaDir, "$courseId.json")

    fun downloadSize(courseId: String): Long {
        var size = 0L
        if (audioFile(courseId).exists()) size += audioFile(courseId).length()
        if (metaFile(courseId).exists()) size += metaFile(courseId).length()
        return size
    }

    fun downloadedIds(): List<String> {
        val files = audioDir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(".m4a") }
            .map { it.nameWithoutExtension }
            .sorted()
    }

    fun totalDownloadBytes(): Long = downloadedIds().sumOf { downloadSize(it) }

    fun downloadState(courseId: String): DownloadState? = _downloads.value[courseId]

    fun download(course: Course) {
        val courseId = course.id
        if (downloadJobs[courseId]?.isActive == true) return
        setState(DownloadState(courseId, DownloadState.Status.QUEUED))
        val job = scope.launch {
            try {
                if (!metaFile(courseId).exists()) {
                    val raw = api.courseDetailRaw(courseId)
                    withContext(Dispatchers.IO) {
                        metaDir.mkdirs()
                        metaFile(courseId).writeText(raw)
                    }
                    detailMemory[courseId] = api.parseDetail(raw)
                }
                val target = audioFile(courseId)
                if (!target.exists()) {
                    api.downloadAudio(courseId, target) { downloaded, total ->
                        setState(
                            DownloadState(
                                courseId = courseId,
                                status = DownloadState.Status.DOWNLOADING,
                                downloadedBytes = downloaded,
                                totalBytes = total,
                            )
                        )
                    }
                }
                setState(
                    DownloadState(
                        courseId = courseId,
                        status = DownloadState.Status.DONE,
                        downloadedBytes = downloadSize(courseId),
                        totalBytes = downloadSize(courseId),
                    )
                )
            } catch (failure: Throwable) {
                setState(
                    DownloadState(
                        courseId = courseId,
                        status = DownloadState.Status.FAILED,
                        message = failure.message ?: "下载失败",
                    )
                )
            } finally {
                downloadJobs.remove(courseId)
            }
        }
        downloadJobs[courseId] = job
    }

    fun cancellingDownload(courseId: String) = downloadJobs[courseId]?.cancel()

    fun deleteDownload(courseId: String) {
        downloadJobs.remove(courseId)?.cancel()
        audioFile(courseId).delete()
        metaFile(courseId).delete()
        detailMemory.remove(courseId)
        _downloads.value = _downloads.value - courseId
    }

    fun clearAllDownloads() {
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        audioDir.listFiles()?.forEach { it.delete() }
        metaDir.listFiles()?.forEach { it.delete() }
        detailMemory.clear()
        _downloads.value = emptyMap()
    }

    private fun setState(state: DownloadState) {
        _downloads.value = _downloads.value + (state.courseId to state)
    }

    // -------------------------------------------------------------- audio source

    /** Prefers the downloaded file so playback keeps working without a connection. */
    suspend fun audioSource(courseId: String): AudioSource {
        val local = audioFile(courseId)
        if (local.exists() && local.length() > 0L) return AudioSource.Local(local.absolutePath)
        return AudioSource.Remote(api.mediaUrl(courseId))
    }

    fun localAudioFile(courseId: String): File = audioFile(courseId)
}
