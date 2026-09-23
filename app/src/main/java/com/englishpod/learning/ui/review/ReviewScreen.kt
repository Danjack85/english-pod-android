package com.englishpod.learning.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.data.ReviewGrade
import com.englishpod.learning.data.WordEntry
import com.englishpod.learning.ui.Routes
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(navController: NavHostController) {
    val store = EnglishPodApp.store
    val words by store.wordbook.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val now = System.currentTimeMillis()
    val due = remember(words) { words.filter { it.dueAt <= now && !it.mastered } }
    val mastered = remember(words) { words.count { it.mastered } }

    var session by remember { mutableStateOf<List<WordEntry>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var remembered by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    fun startSession(pool: List<WordEntry>) {
        session = pool
        index = 0
        flipped = false
        remembered = 0
        finished = false
    }

    val active = session

    if (active != null && !finished && index < active.size) {
        val word = active[index]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "复习中 ${index + 1} / ${active.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { session = null }) { Text("结束") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (index + 1).toFloat() / active.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
            )
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable { flipped = true },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = word.term,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (word.level.isNotBlank()) "${word.level} · ${word.courseTitle}" else word.courseTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        AnimatedVisibility(visible = flipped) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = word.definition.ifBlank { "暂无释义" },
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (!flipped) {
                            Text(
                                text = "点击卡片查看释义",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!flipped) {
                Button(
                    onClick = { flipped = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("显示释义") }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GradeButton("不认识", Color(0xFFB3261E), Modifier.weight(1f)) {
                        scope.launch { store.gradeWord(word.key, ReviewGrade.AGAIN) }
                        index += 1
                        flipped = false
                    }
                    GradeButton("模糊", Color(0xFFB45309), Modifier.weight(1f)) {
                        scope.launch { store.gradeWord(word.key, ReviewGrade.HARD) }
                        remembered += 1
                        index += 1
                        flipped = false
                    }
                    GradeButton("认识", Color(0xFF0F766E), Modifier.weight(1f)) {
                        scope.launch { store.gradeWord(word.key, ReviewGrade.GOOD) }
                        remembered += 1
                        index += 1
                        flipped = false
                    }
                    GradeButton("简单", Color(0xFF4338CA), Modifier.weight(1f)) {
                        scope.launch { store.gradeWord(word.key, ReviewGrade.EASY) }
                        remembered += 1
                        index += 1
                        flipped = false
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "复习",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "用间隔重复把生词变成长期记忆",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
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
                        Icon(
                            Icons.Filled.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${due.size} 个单词待复习",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = "生词本共 ${words.size} 个 · 已掌握 $mastered 个",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { startSession(due) },
                                enabled = due.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onPrimary,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) { Text("开始复习") }
                            OutlinedButton(
                                onClick = { startSession(words.shuffled().take(20)) },
                                enabled = words.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) { Text("随机复习 20 个") }
                        }
                    }
                }
            }
        }

        item {
            OptionCard(
                icon = Icons.Filled.Translate,
                title = "看释义选单词",
                subtitle = "四选一，适合快速回忆",
                onClick = { navController.navigate(Routes.quiz("choice")) },
            )
        }
        item {
            OptionCard(
                icon = Icons.Filled.Spellcheck,
                title = "看释义拼单词",
                subtitle = "手写拼写，检验真实掌握程度",
                onClick = { navController.navigate(Routes.quiz("spelling")) },
            )
        }
        item {
            OptionCard(
                icon = Icons.Filled.Headphones,
                title = "听写训练",
                subtitle = "听音频写句子、拼单词，练真实听力",
                onClick = { navController.navigate(Routes.dictation("sentence")) },
            )
        }
        item {
            OptionCard(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = "查看学习统计",
                subtitle = "学习时长、连续打卡与生词增长",
                onClick = { navController.navigate(Routes.STATS) },
            )
        }
    }

    if (finished || (active != null && index >= active.size)) {
        val total = active?.size ?: 0
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { session = null; finished = false },
            title = { Text("本轮复习完成") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("共复习 $total 个单词，记住 $remembered 个")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "不认识的词会很快再次出现，认识的可获得更长复习间隔。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { session = null; finished = false }) { Text("完成") }
            },
            dismissButton = {
                TextButton(onClick = {
                    startSession(due)
                }) { Text("再来一轮") }
            },
        )
    }
}

@Composable
private fun GradeButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun OptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
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
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
