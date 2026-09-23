package com.englishpod.learning.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.ui.components.StatTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavHostController) {
    val store = EnglishPodApp.store
    val stats by store.stats.collectAsStateWithLifecycle()
    val words by store.wordbook.collectAsStateWithLifecycle()
    val notes by store.bookmarks.collectAsStateWithLifecycle()
    val progress by store.progress.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    val courses by EnglishPodApp.repository.courses.collectAsStateWithLifecycle()

    var range by remember { mutableIntStateOf(7) }

    val series = remember(stats, range) { stats.series(range) }
    val goalSeconds = (settings.dailyGoalMinutes.coerceAtLeast(1) * 60).toLong()
    val studiedCourses = progress.size
    val mastered = words.count { it.mastered }

    val recent = remember(progress, courses) {
        progress.values
            .sortedByDescending { it.updatedAt }
            .take(8)
            .mapNotNull { record -> courses.firstOrNull { it.id == record.courseId }?.let { it to record } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("学习统计", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "累计学习",
                        value = Fmt.studyMinutes(stats.totalSeconds),
                        icon = Icons.Filled.Timer,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "连续打卡",
                        value = "${stats.streak()} 天",
                        icon = Icons.Filled.LocalFireDepartment,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "学习课程",
                        value = "$studiedCourses 节",
                        icon = Icons.Filled.School,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "生词 / 已掌握",
                        value = "${words.size} / $mastered",
                        icon = Icons.Filled.Bookmarks,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "听写次数",
                        value = "${stats.totalDictations} 句",
                        icon = Icons.Filled.RecordVoiceOver,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "学习笔记",
                        value = "${notes.size} 条",
                        icon = Icons.Filled.EditNote,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "打卡热力图",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "最近 12 周，每格一天；颜色越深当天学得越久。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val heatDays = remember(stats) { stats.series(84) }
                        val emptyColor = MaterialTheme.colorScheme.surfaceVariant
                        val lightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        val midColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        val fullColor = MaterialTheme.colorScheme.primary

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(126.dp),
                        ) {
                            val gap = 3.dp.toPx()
                            val cellW = (size.width - gap * 11) / 12f
                            val cellH = (size.height - gap * 6) / 7f
                            val cell = minOf(cellW, cellH)
                            val offsetX = (size.width - (cell * 12 + gap * 11)) / 2f

                            heatDays.forEachIndexed { index, day ->
                                val column = index / 7
                                val row = index % 7
                                val fraction = if (goalSeconds > 0L) {
                                    day.seconds.toFloat() / goalSeconds.toFloat()
                                } else {
                                    0f
                                }
                                val color = when {
                                    day.seconds <= 0L || day.courses.isEmpty() -> emptyColor
                                    fraction < 0.5f -> lightColor
                                    fraction < 1f -> midColor
                                    else -> fullColor
                                }
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(
                                        x = offsetX + column * (cell + gap),
                                        y = row * (cell + gap),
                                    ),
                                    size = Size(cell, cell),
                                    cornerRadius = CornerRadius(cell / 4f, cell / 4f),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "少",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            listOf(emptyColor, lightColor, midColor, fullColor).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(color),
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "多 · 共 ${stats.days.values.count { it.seconds > 0L || it.courses.isNotEmpty() }} 天有学习记录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "学习时长趋势",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            listOf(7 to "7 天", 30 to "30 天").forEach { (days, label) ->
                                FilterChip(
                                    selected = range == days,
                                    onClick = { range = days },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val barColor = MaterialTheme.colorScheme.primary
                        val emptyColor = MaterialTheme.colorScheme.surfaceVariant
                        val goalColor = MaterialTheme.colorScheme.tertiary
                        val maxSeconds = maxOf(
                            series.maxOfOrNull { it.seconds } ?: 0L,
                            goalSeconds,
                            60L,
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        ) {
                            val labelSpace = 18.dp.toPx()
                            val chartHeight = size.height - labelSpace
                            val slot = size.width / series.size
                            val barWidth = slot * 0.55f

                            series.forEachIndexed { index, day ->
                                val ratio = day.seconds.toFloat() / maxSeconds.toFloat()
                                val barHeight = (chartHeight * ratio).coerceAtLeast(3f)
                                val left = index * slot + (slot - barWidth) / 2f
                                drawRoundRect(
                                    color = if (day.seconds > 0L) barColor else emptyColor,
                                    topLeft = Offset(left, chartHeight - barHeight),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f),
                                )
                            }

                            val goalY = chartHeight - chartHeight * (goalSeconds.toFloat() / maxSeconds)
                            drawLine(
                                color = goalColor,
                                start = Offset(0f, goalY),
                                end = Offset(size.width, goalY),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            series.forEach { day ->
                                Text(
                                    text = Fmt.dayLabel(day.day),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "虚线为每日目标 ${settings.dailyGoalMinutes} 分钟 · 单日最长 ${Fmt.studyMinutes(stats.bestDaySeconds())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "今日进度",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val today = stats.today()
                        val fraction = (today.seconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${Fmt.studyMinutes(today.seconds)} / ${settings.dailyGoalMinutes} 分钟" +
                                " · 新词 ${today.wordsAdded} 个 · 复习 ${today.reviews} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "最近学习",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (recent.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "还没有学习记录，去课程里听一节吧。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(recent, key = { it.first.id }) { (course, record) ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = course.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${Fmt.relativeTime(record.updatedAt)} · 听到 ${Fmt.clock(record.positionMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${(record.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
