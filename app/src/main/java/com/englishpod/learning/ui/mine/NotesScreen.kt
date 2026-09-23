package com.englishpod.learning.ui.mine

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.data.Bookmark
import com.englishpod.learning.ui.Routes
import com.englishpod.learning.ui.components.EmptyState
import com.englishpod.learning.ui.components.LevelChip
import kotlinx.coroutines.launch

/** All notes the learner has taken, newest first, with a filter by course. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(navController: NavHostController) {
    val store = EnglishPodApp.store
    val bookmarks by store.bookmarks.collectAsStateWithLifecycle()
    val courses by EnglishPodApp.repository.courses.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var filterCourse by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Bookmark?>(null) }

    val courseIds = remember(bookmarks) { bookmarks.map { it.courseId }.distinct() }
    val visible = remember(bookmarks, filterCourse) {
        bookmarks
            .filter { filterCourse == null || it.courseId == filterCourse }
            .sortedByDescending { it.createdAt }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("学习笔记", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (bookmarks.isNotEmpty()) {
                        IconButton(onClick = {
                            val text = visible.joinToString("\n") { mark ->
                                "${mark.courseTitle} ${Fmt.clock(mark.positionMs)}  ${mark.note}"
                            }
                            clipboard.setText(AnnotatedString(text))
                            scope.launch { snackbarHostState.showSnackbar("已复制 ${visible.size} 条笔记") }
                        }) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "复制全部")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (courseIds.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filterCourse == null,
                            onClick = { filterCourse = null },
                            label = { Text("全部 ${bookmarks.size}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                    items(courseIds) { id ->
                        val title = bookmarks.firstOrNull { it.courseId == id }?.courseTitle ?: id
                        FilterChip(
                            selected = filterCourse == id,
                            onClick = { filterCourse = if (filterCourse == id) null else id },
                            label = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Bookmark,
                    title = "还没有学习笔记",
                    subtitle = "在课程播放页点「记笔记」，可以把重点句、没听清的地方钉在音频时间点上。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { mark ->
                        Card(
                            onClick = { navController.navigate(Routes.course(mark.courseId)) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LevelChip(mark.level.ifBlank { "课程" })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = mark.courseTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            text = Fmt.clock(mark.positionMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = mark.note,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (mark.cueText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = mark.cueText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = Fmt.relativeTime(mark.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = {
                                        navController.navigate(Routes.course(mark.courseId))
                                    }) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("回到课程")
                                    }
                                    IconButton(onClick = { pendingDelete = mark }) {
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
                    }
                }
            }
        }
    }

    pendingDelete?.let { mark ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除笔记") },
            text = { Text("确定要删除「${mark.note}」这条笔记吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { store.removeBookmark(mark.id) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}
