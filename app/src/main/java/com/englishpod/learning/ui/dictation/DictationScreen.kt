package com.englishpod.learning.ui.dictation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.core.TextDiff
import com.englishpod.learning.data.AudioSource
import com.englishpod.learning.data.Course
import com.englishpod.learning.player.AudioEngine
import com.englishpod.learning.player.ClipPlayer
import com.englishpod.learning.ui.Routes
import com.englishpod.learning.ui.components.EmptyState
import com.englishpod.learning.ui.components.LevelChip
import kotlinx.coroutines.launch

/** One question of a dictation drill. */
private data class DrillCard(
    val id: String,
    val courseId: String,
    val courseTitle: String,
    val level: String,
    val expected: String,
    val startMs: Long,
    val endMs: Long?,
)

private enum class Source { FOCUS, WHOLE }

private const val MAX_SENTENCES = 15
private const val MAX_WORDS = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationScreen(navController: NavHostController, modeArg: String?) {
    val store = EnglishPodApp.store
    val repository = EnglishPodApp.repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val courses by repository.courses.collectAsStateWithLifecycle()
    val words by store.wordbook.collectAsStateWithLifecycle()
    val playerState by AudioEngine.state.collectAsStateWithLifecycle()

    var mode by remember { mutableIntStateOf(if (modeArg == "word") 1 else 0) }
    var loading by remember { mutableStateOf(false) }
    var cards by remember { mutableStateOf<List<DrillCard>?>(null) }
    var sessionTitle by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf<List<TextDiff.Token>?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var hinted by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf(Source.FOCUS) }

    val clipPlayer = remember { ClipPlayer(context) }
    val audioCache = remember { mutableStateMapOf<String, AudioSource>() }

    DisposableEffect(Unit) {
        onDispose { clipPlayer.release() }
    }

    suspend fun sourceFor(courseId: String): AudioSource? {
        audioCache[courseId]?.let { return it }
        val resolved = runCatching { repository.audioSource(courseId) }.getOrNull() ?: return null
        audioCache[courseId] = resolved
        return resolved
    }

    fun stopSession() {
        clipPlayer.stop()
        cards = null
        index = 0
        typed = ""
        checked = null
        revealed = false
        hinted = false
        correctCount = 0
        attempts = 0
    }

    fun startSentences(course: Course) {
        val wantsFocus = source == Source.FOCUS
        loading = true
        cards = null
        scope.launch {
            runCatching { repository.detail(course.id) }
                .onSuccess { detail ->
                    val dialogue = detail.chapters.firstOrNull { it.kind == "dialogue" }
                    val review = detail.chapters.firstOrNull { it.kind == "review" }
                    val range = when {
                        wantsFocus && dialogue != null -> dialogue.start to (dialogue.end ?: detail.course.duration)
                        wantsFocus && review != null -> review.start to (review.end ?: detail.course.duration)
                        else -> 0.0 to detail.course.duration
                    }
                    val pool = detail.cues.filter { cue ->
                        cue.end > cue.start && cue.start >= range.first && cue.start <= range.second
                    }.ifEmpty { detail.cues }

                    val built = pool.take(MAX_SENTENCES).map { cue ->
                        DrillCard(
                            id = "${detail.id}-${cue.start}",
                            courseId = detail.id,
                            courseTitle = detail.course.title,
                            level = detail.course.level,
                            expected = cue.text,
                            startMs = (cue.start * 1000).toLong(),
                            endMs = (cue.end * 1000).toLong(),
                        )
                    }
                    if (built.isEmpty()) {
                        sessionTitle = ""
                    } else {
                        sessionTitle = course.title
                        index = 0
                        typed = ""
                        checked = null
                        revealed = false
                        hinted = false
                        correctCount = 0
                        attempts = 0
                        cards = built
                    }
                }
            loading = false
        }
    }

    fun startWords() {
        val pool = words.filter { it.courseId != "manual" && it.start != null }.take(MAX_WORDS)
        if (pool.isEmpty()) return
        sessionTitle = "生词本"
        index = 0
        typed = ""
        checked = null
        revealed = false
        hinted = false
        correctCount = 0
        attempts = 0
        cards = pool.map { word ->
            val start = ((word.start ?: 0.0) * 1000).toLong()
            val end = word.end?.let { (it * 1000).toLong() } ?: (start + 2_600L)
            DrillCard(
                id = word.key,
                courseId = word.courseId,
                courseTitle = word.courseTitle,
                level = word.level,
                expected = word.term,
                startMs = start,
                endMs = end,
            )
        }
    }

    val session = cards
    val current = session?.getOrNull(index)

    // Play the clip whenever a new question appears.
    LaunchedEffect(current?.id) {
        val card = current ?: return@LaunchedEffect
        val source = sourceFor(card.courseId) ?: return@LaunchedEffect
        clipPlayer.play(source, card.startMs, card.endMs)
    }

    fun playAgain(speed: Float) {
        val card = current ?: return
        scope.launch {
            val source = sourceFor(card.courseId) ?: return@launch
            if (audioCache.containsKey(card.courseId) && clipPlayer.playing.value) {
                clipPlayer.replay(speed)
            } else {
                clipPlayer.play(source, card.startMs, card.endMs, speed)
            }
        }
    }

    fun submit() {
        val card = current ?: return
        val result = TextDiff.compare(card.expected, typed)
        checked = result
        attempts += 1
        val accuracy = TextDiff.accuracy(card.expected, typed)
        if (TextDiff.isAcceptable(card.expected, typed)) correctCount += 1
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            session == null -> "听写训练"
                            index >= session.size -> "本轮成绩"
                            else -> sessionTitle.ifBlank { "听写训练" }
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (session != null) stopSession() else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                session == null -> DictationPicker(
                    mode = mode,
                    source = source,
                    onModeChange = { mode = it },
                    onSourceChange = { source = it },
                    courses = courses,
                    currentCourseId = playerState.courseId,
                    currentCourseTitle = playerState.title,
                    wordCount = words.count { it.courseId != "manual" && it.start != null },
                    onWordbook = { startWords() },
                    onCourse = { startSentences(it) },
                    onResumeCurrent = {
                        val id = playerState.courseId ?: return@DictationPicker
                        val course = courses.firstOrNull { it.id == id } ?: return@DictationPicker
                        startSentences(course)
                    },
                )

                index >= session.size -> ResultPane(
                    correct = correctCount,
                    total = session.size,
                    attempts = attempts,
                    onRetry = { stopSession() },
                )

                current != null -> DrillPane(
                    card = current,
                    position = index + 1,
                    total = session.size,
                    correctCount = correctCount,
                    mode = mode,
                    typed = typed,
                    checked = checked,
                    revealed = revealed,
                    hinted = hinted,
                    playing = clipPlayer.playing.collectAsStateWithLifecycle().value,
                    onTypedChange = { typed = it },
                    onReplay = { playAgain(1f) },
                    onSlowReplay = { playAgain(0.7f) },
                    onHint = { hinted = true },
                    onReveal = { revealed = true },
                    onSubmit = { submit() },
                    onNext = {
                        index += 1
                        typed = ""
                        checked = null
                        revealed = false
                        hinted = false
                        if (index >= session.size) {
                            clipPlayer.stop()
                            scope.launch { store.recordDictation(session.size) }
                        }
                    },
                    onSnack = { },
                )
            }
        }
    }
}

@Composable
private fun DictationPicker(
    mode: Int,
    source: Source,
    onModeChange: (Int) -> Unit,
    onSourceChange: (Source) -> Unit,
    courses: List<Course>,
    currentCourseId: String?,
    currentCourseTitle: String,
    wordCount: Int,
    onWordbook: () -> Unit,
    onCourse: (Course) -> Unit,
    onResumeCurrent: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TabRow(selectedTabIndex = mode) {
                Tab(
                    selected = mode == 0,
                    onClick = { onModeChange(0) },
                    text = { Text("句子听写") },
                )
                Tab(
                    selected = mode == 1,
                    onClick = { onModeChange(1) },
                    text = { Text("单词听写") },
                )
            }
        }

        if (mode == 0) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "选句范围",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = source == Source.FOCUS,
                                onClick = { onSourceChange(Source.FOCUS) },
                                label = { Text("重点句") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                            FilterChip(
                                selected = source == Source.WHOLE,
                                onClick = { onSourceChange(Source.WHOLE) },
                                label = { Text("从头开始") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "重点句优先取「原速 Dialogue」或「词汇复习」段落，每轮最多 $MAX_SENTENCES 句。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (currentCourseId != null) {
                item {
                    Card(
                        onClick = onResumeCurrent,
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = "用当前正在听的课程",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = currentCourseTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "或选择一节课",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (courses.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "课程列表为空",
                        subtitle = "请先联网加载课程列表，再回来做听写。",
                    )
                }
            } else {
                items(courses, key = { it.id }) { course ->
                    Card(
                        onClick = { onCourse(course) },
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
                                Text(
                                    text = course.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LevelChip(course.level)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = Fmt.shortDuration(course.duration),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Icon(
                            Icons.Filled.Spellcheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "听发音，拼出单词",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "题目来自生词本中带有音频位置、且来自课程的单词，每轮最多 $MAX_WORDS 个。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "可出题单词：$wordCount 个",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onWordbook,
                            enabled = wordCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始听写")
                        }
                        if (wordCount == 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "生词本还没有可从课程取音的单词。先去课程「字幕」里点词收藏，或在「词汇」标签收藏带发音的词。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrillPane(
    card: DrillCard,
    position: Int,
    total: Int,
    correctCount: Int,
    mode: Int,
    typed: String,
    checked: List<TextDiff.Token>?,
    revealed: Boolean,
    hinted: Boolean,
    playing: Boolean,
    onTypedChange: (String) -> Unit,
    onReplay: () -> Unit,
    onSlowReplay: () -> Unit,
    onHint: () -> Unit,
    onReveal: () -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onSnack: (String) -> Unit,
) {
    val acceptable = checked != null && TextDiff.isAcceptable(card.expected, typed)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "第 $position / $total 题",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "正确 $correctCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { position.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
        )

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    if (mode == 0) Icons.Filled.Headphones else Icons.Filled.Spellcheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (mode == 0) "听音频，写下你听到的句子" else "听发音，拼出这个单词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onReplay) {
                        Icon(
                            Icons.Filled.Replay,
                            contentDescription = "重播",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(58.dp),
                        onClick = onReplay,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "播放",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    IconButton(onClick = onSlowReplay) {
                        Icon(
                            Icons.Filled.SlowMotionVideo,
                            contentDescription = "慢速重播",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (playing) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "播放中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = hinted,
                onClick = onHint,
                label = { Text("提示首字母") },
                leadingIcon = {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
            FilterChip(
                selected = revealed,
                onClick = onReveal,
                label = { Text("看原文") },
                leadingIcon = {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }

        if (hinted) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            ) {
                Text(
                    text = TextDiff.hint(card.expected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (revealed) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(12.dp),
            ) {
                Text(
                    text = card.expected,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = typed,
            onValueChange = onTypedChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
            label = { Text(if (mode == 0) "输入你听到的句子" else "输入单词") },
            enabled = checked == null,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (checked == null) onSubmit() }),
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (checked == null) {
            Button(
                onClick = onSubmit,
                enabled = typed.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("检查") }
        } else {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (acceptable) {
                        Color(0xFF0F766E).copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (acceptable) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            tint = if (acceptable) Color(0xFF0F766E) else MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (acceptable) {
                                "正确！"
                            } else {
                                "准确率 ${(TextDiff.accuracy(card.expected, typed) * 100).toInt()}%"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        checked.forEach { token ->
                            Text(
                                text = token.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (token.kind == TextDiff.Kind.MATCH) {
                                    FontWeight.Normal
                                } else {
                                    FontWeight.SemiBold
                                },
                                textDecoration = if (token.kind == TextDiff.Kind.MISSING) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                },
                                color = when (token.kind) {
                                    TextDiff.Kind.MATCH -> MaterialTheme.colorScheme.onSurface
                                    TextDiff.Kind.MISSING -> MaterialTheme.colorScheme.error
                                    TextDiff.Kind.EXTRA -> Color(0xFFB45309)
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "原文：${card.expected}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(if (position >= total) "查看成绩" else "下一题")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ResultPane(
    correct: Int,
    total: Int,
    attempts: Int,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "$correct / $total",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                correct == total -> "全部听对，耳朵已经打开了！"
                correct >= total * 0.8 -> "很稳，继续保持。"
                correct >= total * 0.5 -> "有一半以上听对了，错句再循环几遍。"
                else -> "别急，先降速听，再逐句跟读几遍。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "本轮听写已记入学习统计",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("再来一轮") }
    }
}
