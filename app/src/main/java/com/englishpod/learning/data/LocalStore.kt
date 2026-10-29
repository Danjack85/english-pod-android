package com.englishpod.learning.data

import android.content.Context
import android.content.SharedPreferences
import com.englishpod.learning.core.Fmt
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ReviewGrade { AGAIN, HARD, GOOD, EASY }

/** A saved vocabulary word plus its spaced repetition state. */
data class WordEntry(
    val term: String,
    val definition: String,
    val courseId: String,
    val courseTitle: String,
    val level: String = "",
    val start: Double? = null,
    val end: Double? = null,
    val createdAt: Long = 0L,
    val box: Int = 0,
    val dueAt: Long = 0L,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastGrade: String? = null,
) {
    val key: String get() = term.trim().lowercase()
    val mastered: Boolean get() = box >= MASTERED_BOX

    /** Anki-style leech: keeps slipping back to box 0, deserves focused attention. */
    val isLeech: Boolean get() = lapses >= LEECH_THRESHOLD

    companion object {
        const val MASTERED_BOX = 6

        /** Times a word has been forgotten (graded AGAIN); beyond this it becomes a leech. */
        const val LEECH_THRESHOLD = 4
    }
}

/** Where the learner stopped inside a course. */
data class ProgressRecord(
    val courseId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val playedSeconds: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val finished: Boolean get() = durationMs > 0L && fraction >= 0.97f
}

data class DayStat(
    val day: String,
    val seconds: Long = 0L,
    val wordsAdded: Int = 0,
    val reviews: Int = 0,
    val courses: Set<String> = emptySet(),
    val dictations: Int = 0,
    val gradeCounts: Map<String, Int> = emptyMap(),
)

data class StudyStats(val days: Map<String, DayStat> = emptyMap()) {

    val totalSeconds: Long get() = days.values.sumOf { it.seconds }
    val totalWordsAdded: Int get() = days.values.sumOf { it.wordsAdded }
    val totalReviews: Int get() = days.values.sumOf { it.reviews }
    val totalDictations: Int get() = days.values.sumOf { it.dictations }

    fun today(): DayStat = days[Fmt.today()] ?: DayStat(Fmt.today())

    /**
     * Consecutive days with study activity, counting back from today. A day that has not been
     * studied yet does not break the run until it is over.
     */
    fun streak(): Int {
        var count = 0
        var offset = if ((days[Fmt.today()]?.seconds ?: 0L) > 0L) 0 else 1
        while (true) {
            val stat = days[Fmt.dayKey(offset)] ?: break
            if (stat.seconds <= 0L && stat.courses.isEmpty()) break
            count++
            offset++
        }
        return count
    }

    /** Oldest-to-newest window of [length] days ending today. */
    fun series(length: Int): List<DayStat> =
        (length - 1 downTo 0).map { offset ->
            val key = Fmt.dayKey(offset)
            days[key] ?: DayStat(key)
        }

    fun bestDaySeconds(): Long = days.values.maxOfOrNull { it.seconds } ?: 0L
}

data class Settings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val themeMode: Int = 0,
    val speed: Float = 1f,
    val dailyGoalMinutes: Int = 20,
    val autoPlayNext: Boolean = true,
    val sentencePauseMs: Int = 1200,
    val dynamicColor: Boolean = false,
    val subtitleScale: Float = 1f,
    val skipSilence: Boolean = false,
    val rewindOnResume: Boolean = false,
    val sentenceRepeatCount: Int = 0,
    val volumeBoost: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderSlot: Int = 2,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://english.52131415.xyz"

        /** Subtitle size presets offered in settings. */
        val SUBTITLE_SCALES = listOf(0.85f, 1f, 1.2f, 1.4f)

        /** Sentence loop repeats: 0 means loop forever. */
        val REPEAT_COUNTS = listOf(0, 1, 2, 3, 5)

        /** Daily reminder time slots as hour/minute pairs. */
        val REMINDER_SLOTS = listOf(
            8 to 0,
            12 to 30,
            20 to 0,
            21 to 30,
        )
    }
}

/** One saved result of the vocabulary size estimation test. */
data class VocabTestResult(val day: String, val estimated: Int)

/**
 * Everything the learner accumulates: favourites, wordbook, playback progress, study statistics
 * and settings. Settings live in SharedPreferences so they are available synchronously on cold
 * start; the rest is kept in a single JSON document in the app's private storage.
 */
class LocalStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("english_pod_settings", Context.MODE_PRIVATE)
    private val dataFile = File(appContext.filesDir, "learner-data.json")
    private val mutex = Mutex()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _wordbook = MutableStateFlow<List<WordEntry>>(emptyList())
    val wordbook: StateFlow<List<WordEntry>> = _wordbook.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, ProgressRecord>>(emptyMap())
    val progress: StateFlow<Map<String, ProgressRecord>> = _progress.asStateFlow()

    private val _stats = MutableStateFlow(StudyStats())
    val stats: StateFlow<StudyStats> = _stats.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _vocabTests = MutableStateFlow<List<VocabTestResult>>(emptyList())
    val vocabTests: StateFlow<List<VocabTestResult>> = _vocabTests.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var lastFlushAt = 0L

    init {
        load()
    }

    /** Runs [block] off the main thread while holding the store lock. */
    private suspend fun <T> locked(block: () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    // ---------------------------------------------------------------- settings

    private fun readSettings() = Settings(
        baseUrl = prefs.getString(KEY_BASE_URL, Settings.DEFAULT_BASE_URL)
            ?: Settings.DEFAULT_BASE_URL,
        themeMode = prefs.getInt(KEY_THEME, 0),
        speed = prefs.getFloat(KEY_SPEED, 1f),
        dailyGoalMinutes = prefs.getInt(KEY_GOAL, 20),
        autoPlayNext = prefs.getBoolean(KEY_AUTO_NEXT, true),
        sentencePauseMs = prefs.getInt(KEY_PAUSE, 1200),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        subtitleScale = prefs.getFloat(KEY_SUBTITLE_SCALE, 1f),
        skipSilence = prefs.getBoolean(KEY_SKIP_SILENCE, false),
        rewindOnResume = prefs.getBoolean(KEY_REWIND, false),
        sentenceRepeatCount = prefs.getInt(KEY_REPEAT_COUNT, 0),
        volumeBoost = prefs.getBoolean(KEY_VOLUME_BOOST, false),
        reminderEnabled = prefs.getBoolean(KEY_REMINDER, false),
        reminderSlot = prefs.getInt(KEY_REMINDER_SLOT, 2),
    )

    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString(KEY_BASE_URL, next.baseUrl)
            .putInt(KEY_THEME, next.themeMode)
            .putFloat(KEY_SPEED, next.speed)
            .putInt(KEY_GOAL, next.dailyGoalMinutes)
            .putBoolean(KEY_AUTO_NEXT, next.autoPlayNext)
            .putInt(KEY_PAUSE, next.sentencePauseMs)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putFloat(KEY_SUBTITLE_SCALE, next.subtitleScale)
            .putBoolean(KEY_SKIP_SILENCE, next.skipSilence)
            .putBoolean(KEY_REWIND, next.rewindOnResume)
            .putInt(KEY_REPEAT_COUNT, next.sentenceRepeatCount)
            .putBoolean(KEY_VOLUME_BOOST, next.volumeBoost)
            .putBoolean(KEY_REMINDER, next.reminderEnabled)
            .putInt(KEY_REMINDER_SLOT, next.reminderSlot)
            .apply()
    }

    // --------------------------------------------------------------- favourites

    suspend fun toggleFavorite(courseId: String): Boolean = locked {
        val current = _favorites.value
        val added = courseId !in current
        _favorites.value = if (added) current + courseId else current - courseId
        writeLocked()
        added
    }

    fun isFavorite(courseId: String): Boolean = courseId in _favorites.value

    // ----------------------------------------------------------------- wordbook

    suspend fun addWord(word: WordEntry): Boolean = locked {
        val key = word.key
        if (_wordbook.value.any { it.key == key }) return@locked false
        val now = System.currentTimeMillis()
        val entry = word.copy(
            term = word.term.trim(),
            createdAt = if (word.createdAt == 0L) now else word.createdAt,
            box = word.box.coerceAtLeast(0),
            dueAt = if (word.dueAt == 0L) now else word.dueAt,
        )
        _wordbook.value = _wordbook.value + entry
        touchDayLocked { it.copy(wordsAdded = it.wordsAdded + 1) }
        writeLocked()
        true
    }

    suspend fun removeWord(key: String): Unit = locked {
        _wordbook.value = _wordbook.value.filterNot { it.key == key }
        writeLocked()
    }

    suspend fun updateWord(key: String, transform: (WordEntry) -> WordEntry): Unit = locked {
        _wordbook.value = _wordbook.value.map { if (it.key == key) transform(it) else it }
        writeLocked()
    }

    suspend fun gradeWord(key: String, grade: ReviewGrade): Unit = locked {
        val now = System.currentTimeMillis()
        _wordbook.value = _wordbook.value.map { word ->
            if (word.key != key) return@map word
            val box = when (grade) {
                ReviewGrade.AGAIN -> 0
                ReviewGrade.HARD -> word.box
                ReviewGrade.GOOD -> (word.box + 1).coerceAtMost(INTERVALS.size - 1)
                ReviewGrade.EASY -> (word.box + 2).coerceAtMost(INTERVALS.size - 1)
            }
            val delay = when (grade) {
                ReviewGrade.AGAIN -> INTERVALS[0]
                ReviewGrade.HARD -> (INTERVALS[box] / 3).coerceAtLeast(2L)
                else -> INTERVALS[box]
            }
            word.copy(
                box = box,
                dueAt = now + delay * 60_000L,
                reps = word.reps + 1,
                lapses = if (grade == ReviewGrade.AGAIN) word.lapses + 1 else word.lapses,
                lastGrade = grade.name,
            )
        }
        touchDayLocked { day ->
            day.copy(
                reviews = day.reviews + 1,
                gradeCounts = day.gradeCounts + (grade.name to (day.gradeCounts[grade.name] ?: 0) + 1),
            )
        }
        writeLocked()
    }

    fun dueWords(now: Long = System.currentTimeMillis()): List<WordEntry> =
        _wordbook.value.filter { it.dueAt <= now && !it.mastered }

    // ----------------------------------------------------------------- bookmarks

    suspend fun addBookmark(bookmark: Bookmark): Unit = locked {
        val entry = bookmark.copy(
            note = bookmark.note.trim(),
            createdAt = if (bookmark.createdAt == 0L) System.currentTimeMillis() else bookmark.createdAt,
        )
        // Replace an existing note pinned to the same moment instead of duplicating it.
        _bookmarks.value = _bookmarks.value
            .filterNot { it.courseId == entry.courseId && kotlin.math.abs(it.positionMs - entry.positionMs) < 1200L } +
            entry
        writeLocked()
    }

    suspend fun removeBookmark(id: String): Unit = locked {
        _bookmarks.value = _bookmarks.value.filterNot { it.id == id }
        writeLocked()
    }

    suspend fun updateBookmark(id: String, note: String): Unit = locked {
        _bookmarks.value = _bookmarks.value.map { if (it.id == id) it.copy(note = note.trim()) else it }
        writeLocked()
    }

    fun bookmarksOf(courseId: String): List<Bookmark> =
        _bookmarks.value.filter { it.courseId == courseId }.sortedBy { it.positionMs }

    /** Records a finished dictation drill so it shows up in the study statistics. */
    suspend fun recordDictation(count: Int = 1): Unit = locked {
        if (count > 0) touchDayLocked { it.copy(dictations = it.dictations + count) }
        maybeWriteLocked()
    }

    // ------------------------------------------------------------- vocabulary test

    suspend fun addVocabTest(estimated: Int): Unit = locked {
        _vocabTests.value = _vocabTests.value + VocabTestResult(Fmt.today(), estimated)
        writeLocked()
    }

    // ------------------------------------------------------------ backup / restore

    /** Full learner document, used by the backup feature. */
    fun exportJson(): String = serialize().toString()

    /**
     * Merges a previously exported document back into the store. Course ids and word keys decide
     * identity, so restoring twice does not create duplicates.
     */
    suspend fun importJson(raw: String): Result<ImportedCounts> = runCatching {
        val root = JSONObject(raw)
        locked {
            var favorites = 0
            var words = 0
            var marks = 0

            root.optJSONArray("favorites")?.let { array ->
                val merged = _favorites.value.toMutableSet()
                for (i in 0 until array.length()) {
                    val value = array.optString(i, "")
                    if (value.isNotBlank() && merged.add(value)) favorites++
                }
                _favorites.value = merged
            }

            root.optJSONArray("wordbook")?.let { array ->
                val existing = _wordbook.value.associateBy { it.key }.toMutableMap()
                for (i in 0 until array.length()) {
                    val parsed = parseWord(array.optJSONObject(i) ?: continue) ?: continue
                    val current = existing[parsed.key]
                    if (current == null) {
                        existing[parsed.key] = parsed
                        words++
                    } else if (parsed.reps > current.reps) {
                        // Keep whichever copy has been reviewed more.
                        existing[parsed.key] = parsed
                    }
                }
                _wordbook.value = existing.values.toList()
            }

            root.optJSONArray("bookmarks")?.let { array ->
                val known = _bookmarks.value.map { it.courseId to it.positionMs }.toSet()
                val merged = _bookmarks.value.toMutableList()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val courseId = item.optString("courseId")
                    if (id.isBlank() || courseId.isBlank()) continue
                    val position = item.optLong("positionMs")
                    val key = courseId to position
                    if (key in known) continue
                    merged.add(
                        Bookmark(
                            id = id,
                            courseId = courseId,
                            courseTitle = item.optString("courseTitle"),
                            level = item.optString("level"),
                            positionMs = position,
                            note = item.optString("note"),
                            createdAt = item.optLong("createdAt"),
                            cueText = item.optString("cueText"),
                        ),
                    )
                    marks++
                }
                _bookmarks.value = merged
            }

            root.optJSONObject("stats")?.let { json ->
                val days = _stats.value.days.toMutableMap()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val day = keys.next()
                    val item = json.optJSONObject(day) ?: continue
                    val local = days[day] ?: DayStat(day)
                    val incomingCourses = mutableSetOf<String>()
                    item.optJSONArray("courses")?.let { array ->
                        for (i in 0 until array.length()) {
                            val value = array.optString(i, "")
                            if (value.isNotBlank()) incomingCourses.add(value)
                        }
                    }
                    days[day] = local.copy(
                        seconds = maxOf(local.seconds, item.optLong("seconds")),
                        wordsAdded = maxOf(local.wordsAdded, item.optInt("wordsAdded")),
                        reviews = maxOf(local.reviews, item.optInt("reviews")),
                        dictations = maxOf(local.dictations, item.optInt("dictations")),
                        courses = local.courses + incomingCourses,
                    )
                }
                _stats.value = StudyStats(days)
            }

            root.optJSONArray("vocabTests")?.let { array ->
                val known = _vocabTests.value.map { it.day to it.estimated }.toSet()
                val merged = _vocabTests.value.toMutableList()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val result = VocabTestResult(day = item.optString("day"), estimated = item.optInt("estimated"))
                    if (result.day.isNotBlank() && (result.day to result.estimated) !in known) {
                        merged.add(result)
                    }
                }
                _vocabTests.value = merged
            }

            writeLocked()
            ImportedCounts(favorites = favorites, words = words, bookmarks = marks)
        }
    }

    data class ImportedCounts(val favorites: Int, val words: Int, val bookmarks: Int)

    // ----------------------------------------------------------------- progress

    suspend fun recordProgress(
        courseId: String,
        positionMs: Long,
        durationMs: Long,
        deltaSeconds: Long,
    ): Unit = locked {
        val previous = _progress.value[courseId]
        _progress.value = _progress.value + (courseId to ProgressRecord(
            courseId = courseId,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
            playedSeconds = (previous?.playedSeconds ?: 0L) + deltaSeconds,
        ))
        touchDayLocked { it.copy(courses = it.courses + courseId) }
        maybeWriteLocked()
    }

    suspend fun addStudySeconds(courseId: String, seconds: Long): Unit {
        if (seconds <= 0L) return
        locked {
            touchDayLocked {
                it.copy(seconds = it.seconds + seconds, courses = it.courses + courseId)
            }
            maybeWriteLocked()
        }
    }

    suspend fun clearProgress(courseId: String): Unit = locked {
        _progress.value = _progress.value - courseId
        writeLocked()
    }

    // ---------------------------------------------------------------- lifecycle

    /** Best-effort synchronous write, used when the app goes to the background. */
    fun flushNow() {
        runCatching { dataFile.writeText(serialize().toString()) }
        lastFlushAt = System.currentTimeMillis()
    }

    // ------------------------------------------------------------------ private

    private fun touchDayLocked(transform: (DayStat) -> DayStat) {
        val key = Fmt.today()
        val current = _stats.value.days[key] ?: DayStat(key)
        _stats.value = StudyStats(_stats.value.days + (key to transform(current)))
    }

    private fun maybeWriteLocked() {
        val now = System.currentTimeMillis()
        if (now - lastFlushAt > FLUSH_INTERVAL_MS) writeLocked()
    }

    private fun writeLocked() {
        runCatching { dataFile.writeText(serialize().toString()) }
        lastFlushAt = System.currentTimeMillis()
    }

    private fun serialize(): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("favorites", JSONArray(_favorites.value.toList()))

        val words = JSONArray()
        _wordbook.value.forEach { word ->
            words.put(JSONObject().apply {
                put("term", word.term)
                put("definition", word.definition)
                put("courseId", word.courseId)
                put("courseTitle", word.courseTitle)
                put("level", word.level)
                put("start", word.start ?: JSONObject.NULL)
                put("end", word.end ?: JSONObject.NULL)
                put("createdAt", word.createdAt)
                put("box", word.box)
                put("dueAt", word.dueAt)
                put("reps", word.reps)
                put("lapses", word.lapses)
                put("lastGrade", word.lastGrade ?: JSONObject.NULL)
            })
        }
        root.put("wordbook", words)

        val marks = JSONArray()
        _bookmarks.value.forEach { mark ->
            marks.put(JSONObject().apply {
                put("id", mark.id)
                put("courseId", mark.courseId)
                put("courseTitle", mark.courseTitle)
                put("level", mark.level)
                put("positionMs", mark.positionMs)
                put("note", mark.note)
                put("createdAt", mark.createdAt)
                put("cueText", mark.cueText)
            })
        }
        root.put("bookmarks", marks)

        val progress = JSONObject()
        _progress.value.forEach { (id, record) ->
            progress.put(id, JSONObject().apply {
                put("positionMs", record.positionMs)
                put("durationMs", record.durationMs)
                put("updatedAt", record.updatedAt)
                put("playedSeconds", record.playedSeconds)
            })
        }
        root.put("progress", progress)

        val days = JSONObject()
        _stats.value.days.forEach { (day, stat) ->
            days.put(day, JSONObject().apply {
                put("seconds", stat.seconds)
                put("wordsAdded", stat.wordsAdded)
                put("reviews", stat.reviews)
                put("dictations", stat.dictations)
                val grades = JSONObject()
                stat.gradeCounts.forEach { (grade, count) -> grades.put(grade, count) }
                put("gradeCounts", grades)
                put("courses", JSONArray(stat.courses.toList()))
            })
        }
        root.put("stats", days)

        val tests = JSONArray()
        _vocabTests.value.forEach { result ->
            tests.put(JSONObject().apply {
                put("day", result.day)
                put("estimated", result.estimated)
            })
        }
        root.put("vocabTests", tests)
        return root
    }

    private fun load() {
        val root = runCatching {
            if (dataFile.exists()) JSONObject(dataFile.readText()) else null
        }.getOrNull() ?: return

        root.optJSONArray("favorites")?.let { array ->
            val set = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val value = array.optString(i, "")
                if (value.isNotBlank()) set.add(value)
            }
            _favorites.value = set
        }

        root.optJSONArray("wordbook")?.let { array ->
            val list = mutableListOf<WordEntry>()
            for (i in 0 until array.length()) {
                val parsed = parseWord(array.optJSONObject(i) ?: continue) ?: continue
                list.add(parsed)
            }
            _wordbook.value = list
        }

        root.optJSONArray("bookmarks")?.let { array ->
            val list = mutableListOf<Bookmark>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val courseId = item.optString("courseId")
                if (id.isBlank() || courseId.isBlank()) continue
                list.add(
                    Bookmark(
                        id = id,
                        courseId = courseId,
                        courseTitle = item.optString("courseTitle"),
                        level = item.optString("level"),
                        positionMs = item.optLong("positionMs"),
                        note = item.optString("note"),
                        createdAt = item.optLong("createdAt"),
                        cueText = item.optString("cueText"),
                    ),
                )
            }
            _bookmarks.value = list
        }

        root.optJSONObject("progress")?.let { json ->
            val map = mutableMapOf<String, ProgressRecord>()
            val ids = json.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                val item = json.optJSONObject(id) ?: continue
                map[id] = ProgressRecord(
                    courseId = id,
                    positionMs = item.optLong("positionMs"),
                    durationMs = item.optLong("durationMs"),
                    updatedAt = item.optLong("updatedAt"),
                    playedSeconds = item.optLong("playedSeconds"),
                )
            }
            _progress.value = map
        }

        root.optJSONObject("stats")?.let { json ->
            val map = mutableMapOf<String, DayStat>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val day = keys.next()
                val item = json.optJSONObject(day) ?: continue
                val courses = mutableSetOf<String>()
                item.optJSONArray("courses")?.let { array ->
                    for (i in 0 until array.length()) {
                        val value = array.optString(i, "")
                        if (value.isNotBlank()) courses.add(value)
                    }
                }
                val grades = mutableMapOf<String, Int>()
                item.optJSONObject("gradeCounts")?.let { gradesJson ->
                    val keys = gradesJson.keys()
                    while (keys.hasNext()) {
                        val grade = keys.next()
                        grades[grade] = gradesJson.optInt(grade)
                    }
                }
                map[day] = DayStat(
                    day = day,
                    seconds = item.optLong("seconds"),
                    wordsAdded = item.optInt("wordsAdded"),
                    reviews = item.optInt("reviews"),
                    dictations = item.optInt("dictations"),
                    courses = courses,
                    gradeCounts = grades,
                )
            }
            _stats.value = StudyStats(map)
        }

        root.optJSONArray("vocabTests")?.let { array ->
            val tests = mutableListOf<VocabTestResult>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                tests.add(VocabTestResult(day = item.optString("day"), estimated = item.optInt("estimated")))
            }
            _vocabTests.value = tests
        }
    }

    private fun parseWord(item: JSONObject): WordEntry? {
        val term = item.optString("term")
        if (term.isBlank()) return null
        return WordEntry(
            term = term,
            definition = item.optString("definition"),
            courseId = item.optString("courseId"),
            courseTitle = item.optString("courseTitle"),
            level = item.optString("level"),
            start = if (item.isNull("start")) null else item.optDouble("start"),
            end = if (item.isNull("end")) null else item.optDouble("end"),
            createdAt = item.optLong("createdAt"),
            box = item.optInt("box"),
            dueAt = item.optLong("dueAt"),
            reps = item.optInt("reps"),
            lapses = item.optInt("lapses"),
            lastGrade = if (item.isNull("lastGrade")) null else item.optString("lastGrade"),
        )
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_THEME = "theme_mode"
        const val KEY_SPEED = "speed"
        const val KEY_GOAL = "daily_goal_minutes"
        const val KEY_AUTO_NEXT = "auto_play_next"
        const val KEY_PAUSE = "sentence_pause_ms"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_SUBTITLE_SCALE = "subtitle_scale"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_REWIND = "rewind_on_resume"
        const val KEY_REPEAT_COUNT = "sentence_repeat_count"
        const val KEY_VOLUME_BOOST = "volume_boost"
        const val KEY_REMINDER = "reminder_enabled"
        const val KEY_REMINDER_SLOT = "reminder_slot"
        const val FLUSH_INTERVAL_MS = 10_000L

        /** Review intervals in minutes, indexed by Leitner box. */
        val INTERVALS = longArrayOf(1, 10, 1_440, 2_880, 5_760, 10_080, 21_600, 43_200, 86_400)
    }
}
