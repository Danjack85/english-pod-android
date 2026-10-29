package com.englishpod.learning.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** One dictionary entry resolved for a word. */
data class DictEntry(
    val word: String,
    val phonetic: String?,
    val translation: String?,
    val definition: String?,
    val pos: String?,
    val collins: Int,
    val oxford: Int,
    val tag: String?,
    val frq: Int,
    val bnc: Int,
) {
    /** Human-readable frequency band: 1k/2k/…/12k+, or null when the word is rare. */
    val band: String?
        get() {
            val rank = listOf(frq, bnc).filter { it > 0 }.minOrNull() ?: return null
            val thousands = ((rank - 1) / 1000) + 1
            return if (thousands <= 12) "${thousands}k" else null
        }

    /** Short tags like 考研/雅思/牛津 for the lookup card. */
    val badges: List<String>
        get() = buildList {
            if (oxford == 1) add("牛津3000")
            if (collins in 1..5) add("柯林斯${collins}星")
            tag?.split('\n')?.forEach { raw ->
                val name = TAG_NAMES[raw.trim()]
                if (name != null && name !in this) add(name)
            }
        }

    private companion object {
        val TAG_NAMES = mapOf(
            "zk" to "中考",
            "gk" to "高考",
            "cet4" to "四级",
            "cet6" to "六级",
            "ky" to "考研",
            "toefl" to "托福",
            "ielts" to "雅思",
            "gre" to "GRE",
        )
    }
}

/**
 * Read-only ECDICT subset bundled as assets/dict.db.
 *
 * The database is copied out of assets on first use and opened read-only; it is a plain SQLite
 * file built by tools/build_dict.py from the open ECDICT dataset (MIT licence, credited in the
 * about screen).
 */
class DictDatabase(private val context: Context) {

    private var db: SQLiteDatabase? = null

    @Synchronized
    fun ensureReady(): Boolean {
        db?.let { return true }
        return try {
            val target = File(context.filesDir, DICT_FILE)
            if (!target.exists() || target.length() < 1024L) {
                context.assets.open(DICT_FILE).use { input ->
                    val temp = File(context.filesDir, "$DICT_FILE.tmp")
                    temp.outputStream().use { output -> input.copyTo(output) }
                    if (target.exists()) target.delete()
                    temp.renameTo(target)
                }
            }
            db = SQLiteDatabase.openDatabase(
                target.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Case-insensitive lookup; tries the word as typed, lowercased, then trimmed of punctuation. */
    fun lookup(raw: String): DictEntry? {
        if (!ensureReady()) return null
        val database = db ?: return null
        val cleaned = raw.trim().trim('\'', '"', '.', ',', '!', '?', ';', ':', '(', ')').lowercase()
        if (cleaned.isEmpty()) return null

        val candidates = linkedSetOf(cleaned, raw.trim().lowercase())
        candidates += cleaned.removeSuffix("'s")
        candidates += SINGULARS.mapNotNull { transform ->
            transform(cleaned)?.let { it }
        }
        for (word in candidates) {
            if (word.isBlank()) continue
            val entry = query(database, word)
            if (entry != null) return entry
        }
        return null
    }

    /** Words whose frequency rank falls inside [from, to]; used by the vocabulary size test. */
    fun sampleByFrequency(from: Int, to: Int, limit: Int): List<DictEntry> {
        if (!ensureReady()) return emptyList()
        val database = db ?: return emptyList()
        val out = mutableListOf<DictEntry>()
        try {
            database.rawQuery(
                """
                SELECT word, phonetic, translation, definition, pos, collins, oxford, tag, frq, bnc
                FROM dict
                WHERE rank BETWEEN ? AND ?
                ORDER BY RANDOM() LIMIT ?
                """.trimIndent(),
                arrayOf(from.toString(), to.toString(), limit.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) out.add(cursor.toEntry())
            }
        } catch (_: Exception) {
            // Dictionary unavailable or an older schema; the test simply reports fewer bands.
        }
        return out
    }

    fun isReady(): Boolean = db != null

    private fun query(database: SQLiteDatabase, word: String): DictEntry? = try {
        database.rawQuery(
            """
            SELECT word, phonetic, translation, definition, pos, collins, oxford, tag, frq, bnc
            FROM dict WHERE word = ? COLLATE NOCASE LIMIT 1
            """.trimIndent(),
            arrayOf(word),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toEntry() else null
        }
    } catch (_: Exception) {
        null
    }

    private fun android.database.Cursor.toEntry(): DictEntry = DictEntry(
        word = getString(0) ?: "",
        phonetic = getString(1)?.takeIf { it.isNotBlank() },
        translation = getString(2)?.takeIf { it.isNotBlank() },
        definition = getString(3)?.takeIf { it.isNotBlank() },
        pos = getString(4)?.takeIf { it.isNotBlank() },
        collins = getInt(5),
        oxford = getInt(6),
        tag = getString(7)?.takeIf { it.isNotBlank() },
        frq = getInt(8),
        bnc = getInt(9),
    )

    fun close() {
        runCatching { db?.close() }
        db = null
    }

    private companion object {
        const val DICT_FILE = "dict.db"

        /** Naive inflection rollback so "supermarkets" finds "supermarket". */
        val SINGULARS = listOf<((String) -> String?)>(
            { w -> if (w.endsWith("ies") && w.length > 4) w.dropLast(3) + "y" else null },
            { w -> if (w.endsWith("es") && w.length > 3) w.dropLast(2) else null },
            { w -> if (w.endsWith("s") && w.length > 2) w.dropLast(1) else null },
            { w -> if (w.endsWith("ing") && w.length > 5) w.dropLast(3) else null },
            { w -> if (w.endsWith("ing") && w.length > 4) w.dropLast(3) + "e" else null },
            { w -> if (w.endsWith("ed") && w.length > 4) w.dropLast(2) else null },
            { w -> if (w.endsWith("ed") && w.length > 3) w.dropLast(1) else null },
            { w -> if (w.endsWith("ly") && w.length > 4) w.dropLast(2) else null },
        )
    }
}
