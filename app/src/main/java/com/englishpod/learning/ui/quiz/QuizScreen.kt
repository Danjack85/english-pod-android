package com.englishpod.learning.ui.quiz

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.data.WordEntry
import com.englishpod.learning.ui.components.EmptyState
import com.englishpod.learning.ui.components.LevelChip
import kotlinx.coroutines.launch

private data class Question(val prompt: String, val answer: String, val options: List<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(navController: NavHostController, modeArg: String?) {
    val store = EnglishPodApp.store
    val repository = EnglishPodApp.repository
    val courses by repository.courses.collectAsStateWithLifecycle()
    val words by store.wordbook.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(if (modeArg == "spelling") 1 else 0) }
    var loading by remember { mutableStateOf(false) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }
    var questions by remember { mutableStateOf<List<Question>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf<Boolean?>(null) }
    var typed by remember { mutableStateOf("") }

    fun buildQuestions(pool: List<Pair<String, String>>, label: String) {
        val clean = pool
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .distinctBy { it.first.lowercase() }
        if (clean.size < 4) return
        val picked = clean.shuffled()
        questions = picked.take(10).map { entry ->
            val distractors = clean
                .filter { it.first != entry.first }
                .shuffled()
                .take(3)
                .map { it.first }
            Question(
                prompt = entry.second,
                answer = entry.first,
                options = (distractors + entry.first).shuffled(),
            )
        }
        sourceLabel = label
        index = 0
        score = 0
        answered = null
        typed = ""
    }

    fun startFromWordbook() {
        buildQuestions(words.map { it.term to it.definition }, "生词本")
    }

    fun loadCourse(courseId: String, title: String) {
        loading = true
        questions = null
        scope.launch {
            runCatching { repository.detail(courseId) }
                .onSuccess { detail ->
                    buildQuestions(detail.vocabulary.map { it.term to it.definition }, title)
                }
                .onFailure { sourceLabel = null }
            loading = false
        }
    }

    val quiz = questions

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            quiz == null -> "词汇测验"
                            index >= quiz.size -> "本轮成绩"
                            else -> sourceLabel ?: "测验"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (quiz != null) {
                            questions = null
                        } else {
                            navController.popBackStack()
                        }
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                quiz == null -> QuizPicker(
                    mode = mode,
                    onModeChange = { mode = it },
                    wordCount = words.size,
                    courses = courses,
                    onWordbook = ::startFromWordbook,
                    onCourse = { id, title -> loadCourse(id, title) },
                )

                index >= quiz.size -> ResultPane(
                    score = score,
                    total = quiz.size,
                    onRetry = { questions = null },
                )

                else -> {
                    val question = quiz[index]
                    QuestionPane(
                        mode = mode,
                        question = question,
                        index = index,
                        total = quiz.size,
                        score = score,
                        answered = answered,
                        typed = typed,
                        onTypedChange = { typed = it },
                        onSubmitChoice = { option ->
                            if (answered == null) {
                                answered = option.equals(question.answer, ignoreCase = true)
                                if (answered == true) score += 1
                            }
                        },
                        onSubmitSpelling = {
                            if (answered == null) {
                                answered = typed.trim().equals(question.answer.trim(), ignoreCase = true)
                                if (answered == true) score += 1
                            }
                        },
                        onNext = {
                            index += 1
                            answered = null
                            typed = ""
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizPicker(
    mode: Int,
    onModeChange: (Int) -> Unit,
    wordCount: Int,
    courses: List<com.englishpod.learning.data.Course>,
    onWordbook: () -> Unit,
    onCourse: (String, String) -> Unit,
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
                    text = { Text("看释义选单词") },
                )
                Tab(
                    selected = mode == 1,
                    onClick = { onModeChange(1) },
                    text = { Text("看释义拼单词") },
                )
            }
        }

        item {
            Card(
                onClick = onWordbook,
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
                            Icons.Filled.Bookmarks,
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
                        Text(
                            text = "用生词本出题",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (wordCount >= 4) {
                                "生词本有 $wordCount 个单词，可以开始"
                            } else {
                                "至少需要 4 个生词（当前 $wordCount 个）"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "从课程词汇出题",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "选择一节课，用它的词汇表生成 10 道题",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (courses.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Quiz,
                    title = "课程列表为空",
                    subtitle = "请先联网加载课程列表，再回来做测验。",
                )
            }
        } else {
            items(courses, key = { it.id }) { course ->
                Card(
                    onClick = { onCourse(course.id, course.title) },
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
                                    text = "${course.noteCount} 词 · ${Fmt.shortDuration(course.duration)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            if (mode == 0) Icons.Filled.Translate else Icons.Filled.Spellcheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionPane(
    mode: Int,
    question: Question,
    index: Int,
    total: Int,
    score: Int,
    answered: Boolean?,
    typed: String,
    onTypedChange: (String) -> Unit,
    onSubmitChoice: (String) -> Unit,
    onSubmitSpelling: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "第 ${index + 1} / $total 题",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "得分 $score",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
        )
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (mode == 0) "选出对应的英文单词" else "根据释义拼写英文单词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = question.prompt,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (mode == 0) {
            question.options.forEach { option ->
                val isAnswer = option.equals(question.answer, ignoreCase = true)
                val background = when {
                    answered == null -> MaterialTheme.colorScheme.surfaceVariant
                    isAnswer -> Color(0xFF0F766E).copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
                Surface(
                    color = background,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable(enabled = answered == null) { onSubmitChoice(option) },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (answered != null && isAnswer) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                            )
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = typed,
                onValueChange = onTypedChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入英文单词") },
                singleLine = true,
                enabled = answered == null,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmitSpelling() }),
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (answered == null) {
                Button(onClick = onSubmitSpelling, modifier = Modifier.fillMaxWidth()) {
                    Text("提交答案")
                }
            }
        }

        if (answered != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (answered == true) {
                        Color(0xFF0F766E).copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (answered == true) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (answered == true) Color(0xFF0F766E) else MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (answered == true) "回答正确！" else "正确答案：${question.answer}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(if (index + 1 >= total) "查看成绩" else "下一题")
            }
        }
    }
}

@Composable
private fun ResultPane(score: Int, total: Int, onRetry: () -> Unit) {
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
            text = "$score / $total",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when {
                score == total -> "全部答对，太棒了！"
                score >= total * 0.8 -> "表现很好，继续保持。"
                score >= total * 0.5 -> "还不错，错题再复习一遍吧。"
                else -> "多听几遍课程，再来挑战一次。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("再来一轮") }
    }
}
