package com.englishpod.learning.data

import com.englishpod.learning.core.UrlGuard
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin client for the English Pod learning API.
 *
 * Every request goes through [resolve], which validates the base address and host before the
 * request leaves the device.
 */
class CourseApi(private val baseUrlProvider: () -> String) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun resolve(path: String): HttpUrl {
        val base = UrlGuard.parse(baseUrlProvider())
        val url = base.newBuilder().addPathSegments(path.trimStart('/')).build()
        UrlGuard.ensurePublicHost(url)
        return url
    }

    private suspend fun getText(path: String): String = withContext(Dispatchers.IO) {
        val url = resolve(path)
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("响应内容为空")
        }
    }

    suspend fun listCourses(): List<Course> {
        val array = JSONArray(getText("api/courses"))
        return buildList(array.length()) {
            for (i in 0 until array.length()) add(parseCourse(array.getJSONObject(i)))
        }
    }

    suspend fun courseDetail(courseId: String): CourseDetail =
        parseDetail(courseDetailRaw(courseId))

    /** Raw `GET /api/courses/{id}` payload, cached on disk for offline playback. */
    suspend fun courseDetailRaw(courseId: String): String = getText("api/courses/$courseId")

    suspend fun mediaUrl(courseId: String): String = resolve("media/$courseId").toString()

    /** Downloads the audio track to [target], reporting byte progress. */
    suspend fun downloadAudio(
        courseId: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val url = resolve("media/$courseId")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应内容为空")
            val total = body.contentLength()
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".part")
            body.byteStream().use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }

    /** Fast reachability probe used by the settings screen. */
    suspend fun probe(): Int = withContext(Dispatchers.IO) {
        val url = resolve("api/courses")
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { it.code }
    }

    fun parseCourse(json: JSONObject) = Course(
        id = json.optString("id"),
        level = json.optString("level"),
        number = json.optInt("number"),
        fileNumber = json.optInt("fileNumber"),
        title = json.optString("title"),
        originalTitle = json.optString("originalTitle"),
        duration = json.optDouble("duration", 0.0),
        available = json.optBoolean("available", true),
        noteCount = json.optInt("noteCount"),
    )

    fun parseDetail(raw: String): CourseDetail = parseDetail(JSONObject(raw))

    fun parseDetail(json: JSONObject): CourseDetail {
        val course = parseCourse(json)

        val cues = json.optJSONArray("cues")?.mapObjects { item ->
            Cue(
                start = item.optDouble("start", 0.0),
                end = item.optDouble("end", 0.0),
                text = item.optString("text").trim(),
            )
        }.orEmpty()

        val chapters = json.optJSONArray("chapters")?.mapObjects { item ->
            Chapter(
                start = item.optDouble("start", 0.0),
                end = if (item.isNull("end")) null else item.optDouble("end"),
                title = item.optString("title"),
                kind = item.optString("kind"),
                playable = item.optBoolean("playable", false),
            )
        }.orEmpty()

        val notes = json.optJSONArray("notes")?.mapObjects { item ->
            Note(
                id = item.optString("id"),
                title = item.optString("title"),
                section = item.optString("section"),
                start = if (item.isNull("start")) null else item.optDouble("start"),
                end = if (item.isNull("end")) null else item.optDouble("end"),
                text = item.optString("text"),
            )
        }.orEmpty()

        val vocabulary = json.optJSONArray("vocabulary")?.mapObjects { item ->
            VocabItem(
                id = item.optString("id"),
                term = item.optString("term"),
                definition = item.optString("definition"),
                start = if (item.isNull("start")) null else item.optDouble("start"),
                end = if (item.isNull("end")) null else item.optDouble("end"),
                example = if (item.isNull("example")) null else item.optString("example"),
            )
        }.orEmpty()

        val dialogue = json.optJSONArray("dialogueText")?.let { array ->
            buildList { for (i in 0 until array.length()) add(array.optString(i)) }
        }.orEmpty()

        return CourseDetail(
            course = course,
            cues = cues,
            chapters = chapters,
            notes = notes,
            vocabulary = vocabulary,
            dialogue = dialogue,
            curated = json.optBoolean("curated", false),
        )
    }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList(length()) {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                add(transform(item))
            }
        }
}
