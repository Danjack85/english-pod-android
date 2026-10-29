package com.englishpod.learning.ui.vocabtest

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.data.DictEntry
import com.englishpod.learning.data.VocabTestResult
import kotlinx.coroutines.launch

private const val WORDS_PER_BAND = 4
private const val BANDS = 12

/**
 * Vocabulary size estimation in the style of testyourvocab.com: sample words from frequency
 * bands (1k..12k) out of the offline dictionary, ask the learner to self-report familiarity and
 * extrapolate a total from per-band hit rates. The run stops early once a band is mostly unknown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabTestScreen(navController: NavHostController) {
    val store = EnglishPodApp.store
    val scope = rememberCoroutineScope()
    val history by store.vocabTests.collectAsStateWithLifecycle()

    var phase by remember { mutableStateOf("intro") } // intro | testing | result
    var bands by remember { mutableStateOf<List<Pair<Int, List<DictEntry>>>>(emptyList()) }
    var bandIndex by remember { mutableIntStateOf(0) }
    var wordIndex by remember { mutableIntStateOf(0) }
    var knownPerBand by remember { mutableStateOf<List<Int>>(emptyList()) }

    val progress: Float =
        ((knownPerBand.size * WORDS_PER_BAND + wordIndex).toFloat() /
            (bands.size * WORDS_PER_BAND).coerceAtLeast(1))

    fun startTest() {
        bands = (0 until BANDS).mapNotNull { band ->
            val samples = EnglishPodApp.dict.sampleByFrequency(
                from = band * 1000 + 1,
                to = (band + 1) * 1000,
                limit = WORDS_PER_BAND * 3,
            ).shuffled().take(WORDS_PER_BAND)
            if (samples.size >= WORDS_PER_BAND) band to samples else null
        }
        bandIndex = 0
        wordIndex = 0
        knownPerBand = emptyList()
        phase = "testing"
    }

    fun finishTest() {
        val estimate = knownPerBand
            .map { known -> (known.toFloat() / WORDS_PER_BAND * 1000).toInt() }
            .sum()
            .coerceAtLeast(100)
        scope.launch { store.addVocabTest((estimate / 100) * 100) }
        phase = "result"
    }

    fun answer(known: Boolean) {
        val updated = knownPerBand.getOrElse(bandIndex) { 0 } + if (known) 1 else 0
        val next = knownPerBand.toMutableList()
        while (next.size <= bandIndex) next.add(0)
        next[bandIndex] = updated
        knownPerBand = next.toList()

        if (wordIndex + 1 >= WORDS_PER_BAND) {
            wordIndex = 0
            bandIndex += 1
            // A band where most words are unfamiliar marks the frontier; stop there.
            if (bandIndex >= bands.size || updated <= 1) finishTest()
        } else {
            wordIndex += 1
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("词汇量测试", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (phase == "testing") {
                            phase = "intro"
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                phase == "intro" -> IntroPane(
                    history = history,
                    onStart = ::startTest,
                )

                phase == "testing" && bands.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "词典未就绪，请稍候重试",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                phase == "testing" -> {
                    val band = bands.getOrNull(bandIndex)
                    val word = band?.second?.getOrNull(wordIndex)
                    if (band == null || word == null) {
                        finishTest()
                    } else {
                        QuestionPane(
                            word = word,
                            band = band.first + 1,
                            position = knownPerBand.size * WORDS_PER_BAND + wordIndex + 1,
                            total = bands.size * WORDS_PER_BAND,
                            progress = progress,
                            onAnswer = ::answer,
                        )
                    }
                }

                phase == "result" -> ResultPane(
                    history = history,
                    onRetake = ::startTest,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun IntroPane(history: List<VocabTestResult>, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Insights,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "估测你的英语词汇量",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "从常用到生僻共 12 个频段抽样，凭第一感觉判断认识与否。" +
                "遇到整段都不认识的频段会提前结束。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "上次测试：${history.last().estimated} 词（${history.last().day}）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(26.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(if (history.isEmpty()) "开始测试" else "再测一次")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "词汇样本来自内置离线词典（ECDICT，MIT 许可）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuestionPane(
    word: DictEntry,
    band: Int,
    position: Int,
    total: Int,
    progress: Float,
    onAnswer: (known: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "第 $position / $total 词",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "频段 ${band}k",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = word.word,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!word.phonetic.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "/${word.phonetic}/",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onAnswer(false) },
                modifier = Modifier.weight(1f),
            ) { Text("不认识 / 模糊") }
            Button(
                onClick = { onAnswer(true) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("认识")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "凭第一感觉即可，不确定就算不认识",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultPane(
    history: List<VocabTestResult>,
    onRetake: () -> Unit,
    onBack: () -> Unit,
) {
    val latest = history.lastOrNull()
    val previous = history.dropLast(1).lastOrNull()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "估计词汇量",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${latest?.estimated ?: 0} 词",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (previous != null && latest != null) {
            val delta = latest.estimated - previous.estimated
            Text(
                text = if (delta >= 0) "比上次 +$delta" else "比上次 $delta",
                style = MaterialTheme.typography.labelLarge,
                color = if (delta >= 0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "历史记录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                history.takeLast(5).forEach { result ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = result.day,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${result.estimated} 词",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("再测一次")
            }
            Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("完成")
            }
        }
    }
}
