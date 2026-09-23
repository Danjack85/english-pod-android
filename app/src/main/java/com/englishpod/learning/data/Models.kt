package com.englishpod.learning.data

/** A podcast course as returned by `GET /api/courses`. */
data class Course(
    val id: String,
    val level: String,
    val number: Int,
    val fileNumber: Int,
    val title: String,
    val originalTitle: String,
    val duration: Double,
    val available: Boolean,
    val noteCount: Int,
) {
    val durationMs: Long get() = (duration * 1000).toLong()
}

/** One subtitle line with its timing inside the audio track. */
data class Cue(val start: Double, val end: Double, val text: String)

/** A jumpable section of the episode, e.g. the slow dialogue or the vocabulary review. */
data class Chapter(
    val start: Double,
    val end: Double?,
    val title: String,
    val kind: String,
    val playable: Boolean,
) {
    val startMs: Long get() = (start * 1000).toLong()
    val endMs: Long? get() = end?.let { (it * 1000).toLong() }
}

/** A teaching note (used by the audio review section). */
data class Note(
    val id: String,
    val title: String,
    val section: String,
    val start: Double?,
    val end: Double?,
    val text: String,
) {
    val startMs: Long? get() = start?.let { (it * 1000).toLong() }
}

/** A vocabulary item belonging to a course. */
data class VocabItem(
    val id: String,
    val term: String,
    val definition: String,
    val start: Double?,
    val end: Double?,
    val example: String?,
) {
    val startMs: Long? get() = start?.let { (it * 1000).toLong() }
    val endMs: Long? get() = end?.let { (it * 1000).toLong() }
}

data class CourseDetail(
    val course: Course,
    val cues: List<Cue>,
    val chapters: List<Chapter>,
    val notes: List<Note>,
    val vocabulary: List<VocabItem>,
    val dialogue: List<String>,
    val curated: Boolean,
) {
    val id: String get() = course.id
}

/** Where the audio of a course is served from. */
sealed interface AudioSource {
    data class Local(val path: String) : AudioSource
    data class Remote(val url: String) : AudioSource

    val uri: String
        get() = when (this) {
            is Local -> path
            is Remote -> url
        }
}

/** A learner-created note pinned to a moment of a course. */
data class Bookmark(
    val id: String,
    val courseId: String,
    val courseTitle: String,
    val level: String,
    val positionMs: Long,
    val note: String,
    val createdAt: Long,
    /** Subtitle line the bookmark was created on, when one was active. */
    val cueText: String = "",
)

/** Progress of a download job shown in the UI. */
data class DownloadState(
    val courseId: String,
    val status: Status,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
) {
    enum class Status { QUEUED, DOWNLOADING, DONE, FAILED }

    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}
