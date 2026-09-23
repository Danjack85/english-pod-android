package com.englishpod.learning.ui.home

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.ui.Routes
import com.englishpod.learning.ui.components.CourseCard
import com.englishpod.learning.ui.components.EmptyState
import com.englishpod.learning.ui.components.LevelPalette

private val levelOptions = listOf("初级", "中级", "高级")

private val sortOptions = listOf("默认顺序", "最近学习", "时长从短到长", "时长从长到短", "进度从低到高")

@Composable
fun HomeScreen(navController: NavHostController) {
    val repository = EnglishPodApp.repository
    val store = EnglishPodApp.store

    val courses by repository.courses.collectAsStateWithLifecycle()
    val loading by repository.loading.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val favorites by store.favorites.collectAsStateWithLifecycle()
    val progress by store.progress.collectAsStateWithLifecycle()
    val stats by store.stats.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    val words by store.wordbook.collectAsStateWithLifecycle()
    val bookmarks by store.bookmarks.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var level by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    val visible = remember(courses, query, level, sort, progress) {
        val keyword = query.trim().lowercase()
        val filtered = courses.filter { course ->
            (level == null || course.level == level) &&
                (keyword.isEmpty() ||
                    course.title.lowercase().contains(keyword) ||
                    course.originalTitle.lowercase().contains(keyword) ||
                    course.id.contains(keyword))
        }
        when (sort) {
            1 -> filtered.sortedByDescending { progress[it.id]?.updatedAt ?: 0L }
            2 -> filtered.sortedBy { it.duration }
            3 -> filtered.sortedByDescending { it.duration }
            4 -> filtered.sortedBy { progress[it.id]?.fraction ?: 0f }
            else -> filtered
        }
    }

    val dueWords = remember(words) {
        store.dueWords().size
    }
    val notesCount = remember(bookmarks) { bookmarks.size }

    val continueTarget = remember(progress, courses) {
        val record = progress.values
            .filter { it.positionMs > 15_000L && !it.finished }
            .maxByOrNull { it.updatedAt }
            ?: return@remember null
        courses.firstOrNull { it.id == record.courseId }?.let { course -> course to record }
    }

    val todaySeconds = stats.today().seconds
    val goalSeconds = (settings.dailyGoalMinutes.coerceAtLeast(1) * 60).toLong()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "English Pod",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "听懂，再进一步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = { repository.refresh(force = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新课程")
                }
            }
        }

        item { TodayCard(todaySeconds, goalSeconds, stats.streak()) }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索课程、主题…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = level == null,
                        onClick = { level = null },
                        label = { Text("全部") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    levelOptions.forEach { option ->
                        FilterChip(
                            selected = level == option,
                            onClick = { level = if (level == option) null else option },
                            label = { Text(option) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LevelPalette.container(option),
                                selectedLabelColor = LevelPalette.accent(option),
                            ),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(
                            Icons.Filled.SwapVert,
                            contentDescription = "排序方式",
                            tint = if (sort == 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        sortOptions.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                trailingIcon = {
                                    if (sort == index) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    sort = index
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            StudyPlanCard(
                todaySeconds = todaySeconds,
                goalSeconds = goalSeconds,
                dueWords = dueWords,
                notes = notesCount,
                hasContinue = continueTarget != null,
                onReview = { navController.navigate(Routes.REVIEW) },
                onDictation = { navController.navigate(Routes.dictation("sentence")) },
                onNotes = { navController.navigate(Routes.NOTES) },
            )
        }

        if (continueTarget != null && query.isBlank() && level == null) {
            val (course, record) = continueTarget
            item {
                ContinueCard(
                    title = course.title,
                    level = course.level,
                    fraction = record.fraction,
                    positionMs = record.positionMs,
                    onClick = { navController.navigate(Routes.course(course.id)) },
                )
            }
        }

        if (error != null && courses.isEmpty()) {
            item {
                ErrorCard(message = error.orEmpty()) { repository.refresh(force = true) }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (level == null) "全部课程" else "$level 课程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${visible.size} 节",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Headphones,
                    title = if (courses.isEmpty()) "还没有课程数据" else "没有找到相关课程",
                    subtitle = if (courses.isEmpty()) {
                        "请检查网络连接，或在「我的 → 设置」中确认服务器地址后重试。"
                    } else {
                        "换个关键词试试，或切换到其它难度。"
                    },
                )
            }
        } else {
            items(visible, key = { it.id }) { course ->
                CourseCard(
                    course = course,
                    progressFraction = progress[course.id]?.fraction ?: 0f,
                    favorite = course.id in favorites,
                    downloaded = repository.isDownloaded(course.id),
                    onClick = { navController.navigate(Routes.course(course.id)) },
                )
            }
        }
    }
}

@Composable
private fun StudyPlanCard(
    todaySeconds: Long,
    goalSeconds: Long,
    dueWords: Int,
    notes: Int,
    hasContinue: Boolean,
    onReview: () -> Unit,
    onDictation: () -> Unit,
    onNotes: () -> Unit,
) {
    val remainingMinutes = ((goalSeconds - todaySeconds).coerceAtLeast(0L) + 59L) / 60L
    val headline = when {
        todaySeconds >= goalSeconds -> "今天的目标已经完成，继续加餐也很欢迎。"
        dueWords > 0 -> "今天还差 $remainingMinutes 分钟，先复习 $dueWords 个生词吧。"
        hasContinue -> "今天还差 $remainingMinutes 分钟，接着上次的课程听下去。"
        else -> "今天还差 $remainingMinutes 分钟，挑一节新课程开始。"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Style,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "今日学习计划",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReview) {
                    Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (dueWords > 0) "复习 $dueWords" else "复习")
                }
                OutlinedButton(onClick = onDictation) {
                    Icon(
                        Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("听写")
                }
                OutlinedButton(onClick = onNotes) {
                    Icon(Icons.Filled.Bookmarks, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (notes > 0) "笔记 $notes" else "笔记")
                }
            }
        }
    }
}

@Composable
private fun TodayCard(todaySeconds: Long, goalSeconds: Long, streak: Int) {
    val fraction = if (goalSeconds > 0L) {
        (todaySeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "今日学习",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "连续 $streak 天",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Fmt.studyMinutes(todaySeconds),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "今日目标 ${(goalSeconds / 60)} 分钟 · 已完成 ${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    title: String,
    level: String,
    fraction: Float,
    positionMs: Long,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "继续收听 · ${Fmt.clock(positionMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRetry) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重试",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
