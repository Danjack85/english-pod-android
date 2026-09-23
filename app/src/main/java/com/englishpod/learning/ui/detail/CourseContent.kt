package com.englishpod.learning.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.data.Bookmark
import com.englishpod.learning.data.CourseDetail
import com.englishpod.learning.data.Cue
import com.englishpod.learning.data.VocabItem
import com.englishpod.learning.player.PlayerState
import com.englishpod.learning.ui.components.EmptyState

private data class WordSelection(
    val word: String,
    val definition: String,
    val cue: Cue,
    val known: Boolean,
)

private val punctuation = ".,!?;:\"“”'’()[]{}<>…—–-"

private fun cleanWord(token: String): String =
    token.trim().trim { it in punctuation }.trim()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptPane(
    detail: CourseDetail,
    playerState: PlayerState,
    isCurrent: Boolean,
    onSeekCue: (Int, Cue) -> Unit,
    onSaveWord: (term: String, definition: String, startMs: Double?, endMs: Double?) -> Unit,
    onLoopRange: (startMs: Long, endMs: Long) -> Unit,
    onSnack: (String) -> Unit,
    textScale: Float = 1f,
) {
    val cueFontSize = 14.sp * textScale
    val cueLineHeight = 20.sp * textScale
    var autoScroll by remember { mutableStateOf(true) }
    var blindMode by remember { mutableStateOf(false) }
    var revealedIndex by remember { mutableIntStateOf(-1) }
    var selection by remember { mutableStateOf<WordSelection?>(null) }

    val listState = rememberLazyListState()
    val activeIndex = if (isCurrent) playerState.cueIndex else -1

    LaunchedEffect(activeIndex, autoScroll) {
        if (autoScroll && activeIndex >= 0 && activeIndex < detail.cues.size) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = autoScroll,
                onClick = { autoScroll = !autoScroll },
                label = { Text("自动滚动") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = blindMode,
                onClick = {
                    blindMode = !blindMode
                    revealedIndex = -1
                },
                label = { Text("遮蔽字幕") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "共 ${detail.cues.size} 句",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (detail.cues.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "暂无字幕",
                subtitle = "这节课还没有逐句字幕，可以先用「词汇」「讲义」标签学习。",
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(detail.cues) { index, cue ->
                    val active = index == activeIndex
                    val masked = blindMode && active && revealedIndex != index
                    val dimmed = blindMode && activeIndex >= 0 && index > activeIndex

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (active) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    )
                                    .clickable { onSeekCue(index, cue) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = Fmt.clock(cue.start),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            if (masked) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { revealedIndex = index }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "盲听中 · 点击显示本句字幕",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                FlowRow(
                                    modifier = Modifier
                                        .weight(1f)
                                        .alpha(if (dimmed) 0.45f else 1f),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    cue.text.split(Regex("\\s+")).forEach { token ->
                                        val word = cleanWord(token)
                                        Text(
                                            text = token,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = cueFontSize,
                                                lineHeight = cueLineHeight,
                                            ),
                                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (active) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            modifier = Modifier.clickable(enabled = word.isNotEmpty()) {
                                                val match = detail.vocabulary.firstOrNull {
                                                    it.term.trim().equals(word, ignoreCase = true)
                                                }
                                                selection = WordSelection(
                                                    word = word,
                                                    definition = match?.definition.orEmpty(),
                                                    cue = cue,
                                                    known = match != null,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val current = selection
    if (current != null) {
        WordDialog(
            selection = current,
            onDismiss = { selection = null },
            onSave = { definition ->
                onSaveWord(current.word, definition, current.cue.start, current.cue.end)
                selection = null
            },
            onLoop = {
                onLoopRange((current.cue.start * 1000).toLong(), (current.cue.end * 1000).toLong())
                onSnack("已开启 A-B 复读：${current.cue.text.take(18)}…")
                selection = null
            },
            onCopy = {
                onSnack("已复制：${current.word}")
                selection = null
            },
        )
    }
}

@Composable
private fun WordDialog(
    selection: WordSelection,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onLoop: () -> Unit,
    onCopy: () -> Unit,
) {
    var definition by remember(selection.word) { mutableStateOf(selection.definition) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(selection.word, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = if (selection.known) "课程词汇释义" else "课程词汇未收录，可补充自己的释义",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = definition,
                    onValueChange = { definition = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入释义，例如：超市") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "所在句子：${selection.cue.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(selection.word))
                        onCopy()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("复制")
                    }
                    TextButton(onClick = onLoop) {
                        Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("循环本句")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(definition.ifBlank { selection.definition }) }) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("加入生词本")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun VocabularyPane(
    detail: CourseDetail,
    onPlay: (Long) -> Unit,
    onLoop: (Long, Long) -> Unit,
    onSave: (VocabItem) -> Unit,
) {
    if (detail.vocabulary.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Translate,
            title = "暂无词汇表",
            subtitle = "这节课没有整理词汇，可以在「字幕」里点按单词自己添加到生词本。",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(detail.vocabulary, key = { it.id }) { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.term,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        val startMs = item.startMs
                        if (startMs != null) {
                            IconButton(onClick = { onPlay(startMs) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "播放这个词",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = {
                                onLoop(startMs, item.endMs ?: (startMs + 4000L))
                            }) {
                                Icon(
                                    Icons.Filled.Repeat,
                                    contentDescription = "循环这个词",
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        IconButton(onClick = { onSave(item) }) {
                            Icon(
                                Icons.Filled.BookmarkAdd,
                                contentDescription = "加入生词本",
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    Text(
                        text = item.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!item.example.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.example,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotesPane(detail: CourseDetail, onSeek: (Long) -> Unit) {
    if (detail.notes.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "暂无讲义",
            subtitle = "这节课没有配套讲义，可以先用「字幕」和「词汇」学习。",
        )
        return
    }

    val grouped = remember(detail.notes) { detail.notes.groupBy { it.section.ifBlank { "讲义" } } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.forEach { (section, notes) ->
            item(key = "header-$section") {
                Text(
                    text = section,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(notes, key = { it.id }) { note ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = note.text.ifBlank { note.title },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (note.start != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "音频位置 ${Fmt.clock(note.start)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val noteStartMs = note.startMs
                        if (noteStartMs != null) {
                            IconButton(onClick = { onSeek(noteStartMs) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "从这句开始播放",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DialoguePane(detail: CourseDetail, onSnack: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current

    if (detail.dialogue.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Forum,
            title = "暂无对话文本",
            subtitle = "这节课没有整理对话稿，可以回到「字幕」按完整话轮阅读。",
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "按完整话轮阅读对话 · 点击任意行可复制",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(detail.dialogue) { index, line ->
                val mine = index % 2 == 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (mine) 4.dp else 16.dp,
                                    bottomEnd = if (mine) 16.dp else 4.dp,
                                ),
                            )
                            .background(
                                if (mine) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .clickable {
                                clipboard.setText(AnnotatedString(line))
                                onSnack("已复制：${line.take(16)}…")
                            }
                            .padding(12.dp),
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mine) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarksPane(
    bookmarks: List<Bookmark>,
    onSeek: (Long) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Bookmark,
            title = "还没有笔记",
            subtitle = "播放时点「记笔记」，就能把重点句子和自己的想法钉在音频位置上，之后一键回听。",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(bookmarks, key = { it.id }) { mark ->
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onSeek(mark.positionMs) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = Fmt.clock(mark.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = mark.note,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (mark.cueText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mark.cueText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = Fmt.relativeTime(mark.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = { onSeek(mark.positionMs) }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "回到这个位置",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onDelete(mark) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除笔记",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
