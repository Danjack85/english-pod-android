package com.englishpod.learning.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.data.CourseDetail
import com.englishpod.learning.player.AudioEngine
import com.englishpod.learning.player.LoopMode
import com.englishpod.learning.player.ShadowingRecorder
import com.englishpod.learning.ui.components.EmptyState
import com.englishpod.learning.ui.components.LevelChip
import com.englishpod.learning.ui.components.LevelPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Steps of the shadowing drill. */
private enum class ShadowPhase { IDLE, LISTEN, RECORD, REVIEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(navController: NavHostController, courseId: String) {
    val store = EnglishPodApp.store
    val repository = EnglishPodApp.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val playerState by AudioEngine.state.collectAsStateWithLifecycle()
    val progressMap by store.progress.collectAsStateWithLifecycle()
    val favorites by store.favorites.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    val allBookmarks by store.bookmarks.collectAsStateWithLifecycle()
    val courseBookmarks = remember(allBookmarks, courseId) {
        allBookmarks.filter { it.courseId == courseId }.sortedBy { it.positionMs }
    }

    var detail by remember { mutableStateOf<CourseDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var noteDialog by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }

    LaunchedEffect(courseId) {
        loading = true
        error = null
        runCatching { repository.detail(courseId) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "课程加载失败，请检查网络" }
        loading = false
    }

    val isCurrent = playerState.courseId == courseId
    val course = detail?.course

    fun startPlayback(atMs: Long?) {
        val d = detail ?: return
        scope.launch {
            runCatching {
                val source = repository.audioSource(d.id)
                val resume = atMs ?: (progressMap[d.id]?.positionMs ?: 0L)
                AudioEngine.open(d, source, resume, autoPlay = true)
            }.onFailure {
                snackbarHostState.showSnackbar("音频加载失败：${it.message ?: "未知错误"}")
            }
        }
    }

    val downloadState = repository.downloads.collectAsStateWithLifecycle().value[courseId]
    val downloaded = repository.isDownloaded(courseId)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = course?.title ?: "课程",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val added = store.toggleFavorite(courseId)
                            snackbarHostState.showSnackbar(if (added) "已加入收藏" else "已取消收藏")
                        }
                    }) {
                        Icon(
                            if (courseId in favorites) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = if (courseId in favorites) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = {
                        if (downloaded) {
                            repository.deleteDownload(courseId)
                            scope.launch { snackbarHostState.showSnackbar("已删除离线文件") }
                        } else if (course != null) {
                            repository.download(course)
                            scope.launch { snackbarHostState.showSnackbar("开始后台下载，可离线收听") }
                        }
                    }) {
                        Icon(
                            if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = "离线下载",
                            tint = if (downloaded) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && detail == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                detail == null -> EmptyState(
                    icon = Icons.Filled.GraphicEq,
                    title = "课程加载失败",
                    subtitle = error ?: "请检查网络后重试。",
                    action = {
                        TextButton(onClick = {
                            scope.launch {
                                loading = true
                                runCatching { repository.detail(courseId) }
                                    .onSuccess { detail = it }
                                    .onFailure { error = it.message }
                                loading = false
                            }
                        }) { Text("重试") }
                    },
                )

                else -> {
                    val loaded = detail!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        PlayerPanel(
                            detail = loaded,
                            isCurrent = isCurrent,
                            onPrimaryAction = {
                                if (isCurrent) AudioEngine.togglePlay() else startPlayback(null)
                            },
                            onStartAt = { ms -> startPlayback(ms) },
                            onSnack = { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            },
                            playSentence = { index ->
                                if (isCurrent) AudioEngine.playSentence(index) else {
                                    scope.launch {
                                        val source = repository.audioSource(loaded.id)
                                        AudioEngine.open(loaded, source, 0L, autoPlay = false)
                                        AudioEngine.playSentence(index)
                                    }
                                }
                            },
                            onAddNote = {
                                if (!isCurrent) {
                                    scope.launch { snackbarHostState.showSnackbar("先播放课程，再记录笔记") }
                                } else {
                                    noteDraft = ""
                                    noteDialog = true
                                }
                            },
                            noteCount = courseBookmarks.size,
                        )

                        TabRow(selectedTabIndex = tab) {
                            listOf("字幕", "词汇", "讲义", "对话", "笔记").forEachIndexed { index, label ->
                                Tab(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    },
                                )
                            }
                        }

                        when (tab) {
                            0 -> TranscriptPane(
                                detail = loaded,
                                playerState = playerState,
                                isCurrent = isCurrent,
                                onSeekCue = { index, cue ->
                                    if (isCurrent) AudioEngine.playSentence(index) else startPlayback((cue.start * 1000).toLong())
                                },
                                onSaveWord = { term, definition, startMs, endMs ->
                                    scope.launch {
                                        val added = store.addWord(
                                            com.englishpod.learning.data.WordEntry(
                                                term = term,
                                                definition = definition,
                                                courseId = loaded.id,
                                                courseTitle = loaded.course.title,
                                                level = loaded.course.level,
                                                start = startMs,
                                                end = endMs,
                                            ),
                                        )
                                        snackbarHostState.showSnackbar(
                                            if (added) "已加入生词本：$term" else "生词本中已有：$term",
                                        )
                                    }
                                },
                                onLoopRange = { startMs, endMs ->
                                    if (!isCurrent) {
                                        scope.launch {
                                            val source = repository.audioSource(loaded.id)
                                            AudioEngine.open(loaded, source, startMs, autoPlay = true)
                                            AudioEngine.setAbLoop(startMs, endMs)
                                        }
                                    } else {
                                        AudioEngine.setAbLoop(startMs, endMs)
                                    }
                                },
                                onSnack = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                                textScale = settings.subtitleScale,
                            )

                            1 -> VocabularyPane(
                                detail = loaded,
                                onPlay = { startMs -> startPlayback(startMs) },
                                onLoop = { startMs, endMs ->
                                    if (!isCurrent) {
                                        scope.launch {
                                            val source = repository.audioSource(loaded.id)
                                            AudioEngine.open(loaded, source, startMs, autoPlay = true)
                                            AudioEngine.setAbLoop(startMs, endMs)
                                        }
                                    } else {
                                        AudioEngine.setAbLoop(startMs, endMs)
                                    }
                                },
                                onSave = { item ->
                                    scope.launch {
                                        val added = store.addWord(
                                            com.englishpod.learning.data.WordEntry(
                                                term = item.term,
                                                definition = item.definition,
                                                courseId = loaded.id,
                                                courseTitle = loaded.course.title,
                                                level = loaded.course.level,
                                                start = item.start,
                                                end = item.end,
                                            ),
                                        )
                                        snackbarHostState.showSnackbar(
                                            if (added) "已加入生词本：${item.term}" else "生词本中已有：${item.term}",
                                        )
                                    }
                                },
                            )

                            2 -> NotesPane(
                                detail = loaded,
                                onSeek = { ms -> startPlayback(ms) },
                            )

                            3 -> DialoguePane(detail = loaded, onSnack = { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            })

                            else -> BookmarksPane(
                                bookmarks = courseBookmarks,
                                onSeek = { ms -> startPlayback(ms) },
                                onDelete = { mark ->
                                    scope.launch {
                                        store.removeBookmark(mark.id)
                                        snackbarHostState.showSnackbar("已删除笔记")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (noteDialog) {
        val cue = detail?.cues?.getOrNull(playerState.cueIndex)
        val position = if (playerState.courseId == courseId) playerState.positionMs else 0L
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text("记录笔记") },
            text = {
                Column {
                    Text(
                        text = "会记住当前播放位置 ${Fmt.clock(position)}，之后点笔记即可回到这里。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cue != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        ) {
                            Text(text = cue.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("笔记内容") },
                        placeholder = { Text("例如：这个连读没听清 / 记住这个表达") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = detail?.course?.title ?: courseId
                        val level = detail?.course?.level.orEmpty()
                        val cueText = cue?.text.orEmpty()
                        scope.launch {
                            store.addBookmark(
                                com.englishpod.learning.data.Bookmark(
                                    id = "bm-$courseId-${System.currentTimeMillis()}",
                                    courseId = courseId,
                                    courseTitle = title,
                                    level = level,
                                    positionMs = position,
                                    note = noteDraft.ifBlank { "标记位置" },
                                    createdAt = System.currentTimeMillis(),
                                    cueText = cueText,
                                ),
                            )
                            snackbarHostState.showSnackbar("已记录笔记")
                        }
                        noteDialog = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { noteDialog = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerPanel(
    detail: CourseDetail,
    isCurrent: Boolean,
    onPrimaryAction: () -> Unit,
    onStartAt: (Long) -> Unit,
    onSnack: (String) -> Unit,
    playSentence: (Int) -> Unit,
    onAddNote: () -> Unit,
    noteCount: Int,
) {
    val store = EnglishPodApp.store
    val playerState by AudioEngine.state.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val positionMs = if (isCurrent) playerState.positionMs else 0L
    val durationMs = if (isCurrent) {
        playerState.durationMs.takeIf { it > 0L } ?: detail.course.durationMs
    } else {
        detail.course.durationMs
    }
    val isPlaying = isCurrent && playerState.isPlaying
    val speed = if (isCurrent) playerState.speed else settings.speed

    var speedMenu by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }

    var shadowDialog by remember { mutableStateOf(false) }
    var shadowPhase by remember { mutableStateOf(ShadowPhase.IDLE) }
    var shadowFile by remember { mutableStateOf<java.io.File?>(null) }
    var shadowIndex by remember { mutableIntStateOf(-1) }
    var shadowBudgetMs by remember { mutableLongStateOf(0L) }
    val recorder = remember { ShadowingRecorder(context) }

    val shadowCue = detail.cues.getOrNull(shadowIndex)

    fun openShadow() {
        recorder.stopPlayback()
        shadowFile = null
        shadowPhase = ShadowPhase.IDLE
        shadowIndex = maxOf(playerState.cueIndex, 0)
        shadowDialog = true
    }

    fun beginShadow() {
        val index = maxOf(playerState.cueIndex, 0)
        val cue = detail.cues.getOrNull(index)
        if (cue == null) {
            onSnack("这节课没有字幕，暂时无法跟读")
            return
        }
        shadowIndex = index
        shadowFile = null
        recorder.stopPlayback()
        AudioEngine.setSentenceMode(true)
        AudioEngine.suppressAutoAdvance(true)
        AudioEngine.playSentence(index)
        shadowPhase = ShadowPhase.LISTEN
    }

    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) beginShadow() else onSnack("需要麦克风权限才能跟读")
    }

    DisposableEffect(Unit) {
        onDispose { recorder.release() }
    }

    // Drives the three practice steps: listen to the model sentence, record your repeat, review it.
    LaunchedEffect(shadowPhase) {
        when (shadowPhase) {
            ShadowPhase.LISTEN -> {
                val cue = shadowCue ?: return@LaunchedEffect
                val cueEndMs = (cue.end * 1000).toLong()
                val giveUpAt = System.currentTimeMillis() + 25_000L
                while (System.currentTimeMillis() < giveUpAt) {
                    val current = AudioEngine.state.value
                    if (current.positionMs >= cueEndMs) break
                    if (!current.isPlaying && current.positionMs >= cueEndMs - 800L) break
                    delay(80L)
                }
                val started = recorder.start()
                if (started == null) {
                    shadowPhase = ShadowPhase.IDLE
                    onSnack("无法启动录音，请检查麦克风权限")
                } else {
                    shadowBudgetMs = ((cue.end - cue.start) * 1000).toLong() + 2_500L
                    shadowPhase = ShadowPhase.RECORD
                }
            }

            ShadowPhase.RECORD -> {
                val budget = shadowBudgetMs
                if (budget > 0L) delay(budget)
                shadowFile = recorder.stop()
                shadowPhase = ShadowPhase.REVIEW
            }

            else -> Unit
        }
    }

    fun closeShadow() {
        if (shadowPhase == ShadowPhase.RECORD) {
            shadowFile = recorder.stop()
        }
        recorder.stopPlayback()
        AudioEngine.suppressAutoAdvance(false)
        shadowPhase = ShadowPhase.IDLE
        shadowDialog = false
    }

    val downloaded = EnglishPodApp.repository.isDownloaded(detail.id)
    val downloadState = EnglishPodApp.repository.downloads.collectAsStateWithLifecycle().value[detail.id]

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelChip(detail.course.level)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detail.course.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (downloaded) {
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = "已离线",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (downloadState?.status == com.englishpod.learning.data.DownloadState.Status.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                )
                Text(
                    text = "正在下载离线音频 ${(downloadState.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Slider(
                value = positionMs.toFloat(),
                onValueChange = { value ->
                    if (isCurrent) AudioEngine.seekTo(value.toLong()) else onStartAt(value.toLong())
                },
                valueRange = 0f..(durationMs.coerceAtLeast(1L)).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Fmt.clock(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isCurrent && playerState.isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = Fmt.clock(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { if (isCurrent) AudioEngine.skipBy(-10_000L) else onStartAt(0L) }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "后退 10 秒")
                }
                IconButton(onClick = {
                    val index = if (isCurrent) AudioEngine.currentSentenceIndex() else 0
                    playSentence((index - 1).coerceAtLeast(0))
                }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一句")
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .size(58.dp)
                        .padding(horizontal = 0.dp),
                    onClick = onPrimaryAction,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                IconButton(onClick = {
                    val index = if (isCurrent) AudioEngine.currentSentenceIndex() else -1
                    playSentence(index + 1)
                }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一句")
                }
                IconButton(onClick = { if (isCurrent) AudioEngine.skipBy(10_000L) else onStartAt(0L) }) {
                    Icon(Icons.Filled.Forward10, contentDescription = "前进 10 秒")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    FilterChip(
                        selected = speed != 1f,
                        onClick = { speedMenu = true },
                        label = { Text("${speed}×") },
                        leadingIcon = {
                            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        speedOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option}× 速度") },
                                onClick = {
                                    speedMenu = false
                                    if (isCurrent) {
                                        AudioEngine.setSpeed(option)
                                    } else {
                                        store.updateSettings { it.copy(speed = option) }
                                    }
                                },
                            )
                        }
                    }
                }

                FilterChip(
                    selected = isCurrent && playerState.loopMode == LoopMode.SENTENCE,
                    onClick = {
                        if (!isCurrent) {
                            onSnack("先播放课程，再开启单句循环")
                        } else if (playerState.loopMode == LoopMode.SENTENCE) {
                            AudioEngine.clearLoop()
                        } else {
                            AudioEngine.setLoopMode(LoopMode.SENTENCE)
                        }
                    },
                    label = { Text("单句循环") },
                    leadingIcon = {
                        Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )

                FilterChip(
                    selected = isCurrent && playerState.loopMode == LoopMode.AB,
                    onClick = {
                        if (!isCurrent) {
                            onSnack("先播放课程，再开启 A-B 复读")
                        } else if (playerState.loopMode == LoopMode.AB) {
                            AudioEngine.clearLoop()
                        } else {
                            AudioEngine.loopCurrentSentence()
                        }
                    },
                    label = { Text("A-B 复读") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )

                FilterChip(
                    selected = isCurrent && playerState.sentenceMode,
                    onClick = {
                        if (!isCurrent) {
                            onSnack("先播放课程，再开启逐句精听")
                            return@FilterChip
                        }
                        AudioEngine.setSentenceMode(!playerState.sentenceMode)
                    },
                    label = { Text("逐句精听") },
                    leadingIcon = {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )

                FilterChip(
                    selected = isCurrent && playerState.sleepRemainingSec > 0,
                    onClick = { sleepDialog = true },
                    label = {
                        val remaining = playerState.sleepRemainingSec
                        Text(if (remaining > 0) "定时 ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}" else "睡眠定时")
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )

                FilterChip(
                    selected = shadowDialog,
                    onClick = {
                        if (isCurrent) {
                            openShadow()
                        } else {
                            onSnack("先点播放开始课程，再使用跟读练习")
                        }
                    },
                    label = { Text("跟读练习") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )

                FilterChip(
                    selected = isCurrent && playerState.skipSilence,
                    onClick = {
                        if (!isCurrent) {
                            onSnack("先播放课程，再开启静音跳过")
                        } else {
                            AudioEngine.setSkipSilence(!playerState.skipSilence)
                        }
                    },
                    label = { Text("静音跳过") },
                    leadingIcon = {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )

                FilterChip(
                    selected = false,
                    onClick = onAddNote,
                    label = { Text(if (noteCount > 0) "记笔记 ($noteCount)" else "记笔记") },
                    leadingIcon = {
                        Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }

            if (detail.chapters.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.chapters.forEach { chapter ->
                        val chapterEnd = chapter.endMs
                        val active = isCurrent && playerState.positionMs >= chapter.startMs &&
                            (chapterEnd == null || playerState.positionMs < chapterEnd)
                        Surface(
                            color = if (active) {
                                LevelPalette.accent(detail.course.level)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(50),
                            onClick = { onStartAt(chapter.startMs) },
                        ) {
                            Text(
                                text = "${Fmt.clock(chapter.startMs)} ${chapter.title}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (shadowDialog) {
        AlertDialog(
            onDismissRequest = { closeShadow() },
            title = { Text("跟读练习") },
            text = {
                Column {
                    Text(
                        text = "流程：先听原句，再跟着复述一遍，最后对比试听。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val cue = shadowCue
                    if (cue != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        ) {
                            Text(text = cue.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (shadowPhase) {
                            ShadowPhase.IDLE -> "点「开始跟读」：原句会先播一遍"
                            ShadowPhase.LISTEN -> "① 正在播放原句，听完后自动开始录音"
                            ShadowPhase.RECORD -> "② ● 正在录音… 请跟着复述"
                            ShadowPhase.REVIEW -> "③ 已录好，可以试听对比或重录"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (shadowPhase == ShadowPhase.RECORD) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            confirmButton = {
                val file = shadowFile
                when (shadowPhase) {
                    ShadowPhase.IDLE -> TextButton(onClick = {
                        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }) {
                        Icon(
                            Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("开始跟读")
                    }

                    ShadowPhase.LISTEN -> TextButton(onClick = {
                        AudioEngine.pause()
                        shadowPhase = ShadowPhase.IDLE
                    }) { Text("取消") }

                    ShadowPhase.RECORD -> TextButton(onClick = {
                        shadowFile = recorder.stop()
                        shadowPhase = ShadowPhase.REVIEW
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("停止录音")
                    }

                    ShadowPhase.REVIEW -> TextButton(onClick = {
                        file?.let { recorder.play(it) { } }
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("试听我的")
                    }
                }
            },
            dismissButton = {
                Row {
                    if (shadowPhase == ShadowPhase.REVIEW) {
                        TextButton(onClick = {
                            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        }) { Text("重录") }
                    }
                    TextButton(onClick = { closeShadow() }) { Text("关闭") }
                }
            },
        )
    }

    if (sleepDialog) {
        AlertDialog(
            onDismissRequest = { sleepDialog = false },
            title = { Text("睡眠定时") },
            text = {
                Column {
                    Text(
                        text = "到时间后自动暂停播放，适合睡前泛听。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(5, 15, 30, 60).forEach { minutes ->
                        TextButton(
                            onClick = {
                                AudioEngine.startSleepTimer(minutes)
                                sleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("$minutes 分钟后停止播放")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AudioEngine.cancelSleepTimer()
                    sleepDialog = false
                }) { Text("关闭定时") }
            },
            dismissButton = {
                TextButton(onClick = { sleepDialog = false }) { Text("取消") }
            },
        )
    }
}
