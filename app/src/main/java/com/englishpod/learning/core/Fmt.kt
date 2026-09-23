package com.englishpod.learning.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {

    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** "03:07" or "1:02:03" for a millisecond position. */
    fun clock(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun clock(seconds: Double): String = clock((seconds * 1000).toLong())

    /** "398.9 秒" style short label used on course cards. */
    fun shortDuration(seconds: Double): String {
        val total = seconds.toLong()
        val minutes = total / 60
        if (minutes >= 60) {
            val hours = minutes / 60
            val rest = minutes % 60
            return if (rest == 0L) "${hours} 小时" else "${hours} 小时 ${rest} 分"
        }
        if (minutes >= 1) return "${minutes} 分钟"
        return "${total} 秒"
    }

    /** "3 分 24 秒" for study totals. */
    fun studyMinutes(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        if (minutes >= 60) {
            val hours = minutes / 60
            val rest = minutes % 60
            return if (rest == 0L) "${hours} 小时" else "${hours} 小时 ${rest} 分"
        }
        if (minutes >= 1) return "${minutes} 分钟"
        return "${totalSeconds} 秒"
    }

    fun today(): String = LocalDate.now().format(dayFormatter)

    fun dayKey(daysAgo: Int): String = LocalDate.now().minusDays(daysAgo.toLong()).format(dayFormatter)

    fun dayLabel(key: String): String = key.takeLast(5).replace('-', '/')

    fun relativeTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "未学习"
        val diff = System.currentTimeMillis() - epochMillis
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 60 * 24 -> "${minutes / 60} 小时前"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} 天前"
            else -> "${minutes / (60 * 24 * 30)} 个月前"
        }
    }
}
