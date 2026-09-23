package com.englishpod.learning.ui.wordbook

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.data.WordEntry
import com.englishpod.learning.player.ClipPlayer
import com.englishpod.learning.ui.Routes
import com.englishpod.learning.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordbookScreen(navController: NavHostController) {
    val store = EnglishPodApp.store
    val repository = EnglishPodApp.repository
    val words by store.wordbook.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var filter by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var addDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WordEntry?>(null) }
    var playingKey by remember { mutableStateOf<String?>(null) }

    val clipPlayer = remember { ClipPlayer(context) }
    DisposableEffect(Unit) {
        onDispose { clipPlayer.release() }
    }

    fun playWord(word: WordEntry) {
        val start = word.start ?: return
        val startMs = (start * 1000).toLong()
        val endMs = word.end?.let { (it * 1000).toLong() } ?: (startMs + 2_600L)
        playingKey = word.key
        scope.launch {
            val source = runCatching { repository.audioSource(word.courseId) }.getOrNull()
            if (source == null) {
                snackbarHostState.showSnackbar("暂时取不到这段音频，请检查网络")
                playingKey = null
                return@launch
            }
            clipPlayer.play(source, startMs, endMs)
        }
    }

    val now = System.currentTimeMillis()
    val due = words.filter { it.dueAt <= now && !it.mastered }
    val mastered = words.filter { it.mastered }

    val visible = remember(words, filter, query) {
        val keyword = query.trim().lowercase()
        words
            .filter {
                when (filter) {
                    1 -> it.dueAt <= now && !it.mastered
                    2 -> it.mastered
                    else -> true
                }
            }
            .filter {
                keyword.isEmpty() ||
                    it.term.lowercase().contains(keyword) ||
                    it.definition.lowercase().contains(keyword)
            }
            .sortedByDescending { it.createdAt }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("生词本", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.dictation("word")) }) {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = "单词听写")
                    }
                    IconButton(onClick = {
                        if (words.isEmpty()) {
                            scope.launch { snackbarHostState.showSnackbar("生词本还是空的") }
                        } else {
                            val text = words.joinToString("\n") { "${it.term}\t${it.definition}" }
                            clipboard.setText(AnnotatedString(text))
                            scope.launch { snackbarHostState.showSnackbar("已复制 ${words.size} 个单词") }
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "导出全部")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "手动添加生词")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryCell("全部", words.size)
                SummaryCell("待复习", due.size)
                SummaryCell("已掌握", mastered.size)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索单词或释义") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("全部", "待复习", "已掌握").forEachIndexed { index, label ->
                    FilterChip(
                        selected = filter == index,
                        onClick = { filter = index },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Bookmarks,
                    title = if (words.isEmpty()) "生词本还是空的" else "这里还没有单词",
                    subtitle = if (words.isEmpty()) {
                        "在课程「字幕」里点按任意单词，或在「词汇」标签点收藏按钮，就能把词存进生词本。"
                    } else {
                        "切换筛选条件看看，或者继续去课程里收集新词。"
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.key }) { word ->
                        WordCard(
                            word = word,
                            due = word.dueAt <= now && !word.mastered,
                            playing = playingKey == word.key && clipPlayer.playing.collectAsStateWithLifecycle().value,
                            onPlayAudio = if (word.start != null && word.courseId != "manual") {
                                { playWord(word) }
                            } else {
                                null
                            },
                            onPlayInCourse = {
                                navController.navigate(Routes.course(word.courseId))
                            },
                            onDelete = { pendingDelete = word },
                        )
                    }
                }
            }
        }
    }

    if (addDialog) {
        AddWordDialog(
            onDismiss = { addDialog = false },
            onConfirm = { term, definition ->
                scope.launch {
                    val added = store.addWord(
                        WordEntry(
                            term = term,
                            definition = definition,
                            courseId = "manual",
                            courseTitle = "手动添加",
                        ),
                    )
                    snackbarHostState.showSnackbar(
                        if (added) "已添加：$term" else "生词本中已有：$term",
                    )
                }
                addDialog = false
            },
        )
    }

    pendingDelete?.let { word ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除生词") },
            text = { Text("确定要把「${word.term}」从生词本中移除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { store.removeWord(word.key) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SummaryCell(label: String, value: Int) {
    Column {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WordCard(
    word: WordEntry,
    due: Boolean,
    playing: Boolean,
    onPlayAudio: (() -> Unit)?,
    onPlayInCourse: () -> Unit,
    onDelete: () -> Unit,
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.term,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (due) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "待复习",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    if (word.mastered) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "已掌握",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = word.definition.ifBlank { "暂无释义" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("等级 ${word.box + 1} · 复习 ${word.reps} 次")
                        if (word.courseTitle.isNotBlank()) {
                            append(" · ")
                            append(word.courseTitle)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (onPlayAudio != null) {
                IconButton(onClick = onPlayAudio) {
                    Icon(
                        if (playing) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
                        contentDescription = "播放这个词",
                        tint = if (playing) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            if (word.courseId != "manual") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onPlayInCourse)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "重听",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AddWordDialog(
    onDismiss: () -> Unit,
    onConfirm: (term: String, definition: String) -> Unit,
) {
    var term by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加生词") },
        text = {
            Column {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("单词 / 短语") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = definition,
                    onValueChange = { definition = it },
                    label = { Text("释义") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(term.trim(), definition.trim()) },
                enabled = term.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
