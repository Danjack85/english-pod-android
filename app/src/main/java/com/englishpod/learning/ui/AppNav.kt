package com.englishpod.learning.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.player.AudioEngine
import com.englishpod.learning.ui.detail.CourseScreen
import com.englishpod.learning.ui.dictation.DictationScreen
import com.englishpod.learning.ui.home.HomeScreen
import com.englishpod.learning.ui.mine.DownloadsScreen
import com.englishpod.learning.ui.mine.FavoritesScreen
import com.englishpod.learning.ui.mine.MineScreen
import com.englishpod.learning.ui.mine.NotesScreen
import com.englishpod.learning.ui.mine.SettingsScreen
import com.englishpod.learning.ui.quiz.QuizScreen
import com.englishpod.learning.ui.review.ReviewScreen
import com.englishpod.learning.ui.stats.StatsScreen
import com.englishpod.learning.ui.wordbook.WordbookScreen

object Routes {
    const val HOME = "home"
    const val WORDBOOK = "wordbook"
    const val REVIEW = "review"
    const val QUIZ = "quiz"
    const val STATS = "stats"
    const val MINE = "mine"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val NOTES = "notes"
    const val DICTATION = "dictation"

    /** Route patterns registered with the NavHost. */
    const val QUIZ_PATTERN = "quiz?mode={mode}"
    const val DICTATION_PATTERN = "dictation?mode={mode}"
    const val COURSE_PATTERN = "course/{courseId}"

    fun course(id: String) = "course/$id"
    fun quiz(mode: String) = "quiz?mode=$mode"
    fun dictation(mode: String) = "dictation?mode=$mode"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.HOME, "课程", Icons.Filled.Headphones),
    TabItem(Routes.WORDBOOK, "生词本", Icons.Filled.Bookmarks),
    TabItem(Routes.REVIEW, "复习", Icons.Filled.Style),
    TabItem(Routes.QUIZ, "测验", Icons.Filled.Quiz),
    TabItem(Routes.MINE, "我的", Icons.Filled.Person),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTabs = tabs.any { it.route == currentRoute }
    val playerState by AudioEngine.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = playerState.courseId != null && currentRoute != Routes.COURSE_PATTERN,
                ) {
                    MiniPlayer(
                        state = playerState,
                        onOpen = {
                            playerState.courseId?.let { navController.navigate(Routes.course(it)) }
                        },
                    )
                }
                if (showTabs) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.WORDBOOK) { WordbookScreen(navController) }
            composable(Routes.REVIEW) { ReviewScreen(navController) }
            composable(Routes.QUIZ_PATTERN) { entry ->
                QuizScreen(navController, entry.arguments?.getString("mode"))
            }
            composable(Routes.DICTATION_PATTERN) { entry ->
                DictationScreen(navController, entry.arguments?.getString("mode"))
            }
            composable(Routes.STATS) { StatsScreen(navController) }
            composable(Routes.MINE) { MineScreen(navController) }
            composable(Routes.FAVORITES) { FavoritesScreen(navController) }
            composable(Routes.DOWNLOADS) { DownloadsScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.NOTES) { NotesScreen(navController) }
            composable(Routes.COURSE_PATTERN) { entry ->
                CourseScreen(
                    navController = navController,
                    courseId = entry.arguments?.getString("courseId").orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    state: com.englishpod.learning.player.PlayerState,
    onOpen: () -> Unit,
) {
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.clickable(onClick = onOpen)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.title.ifBlank { "继续收听" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.cueLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { AudioEngine.previousSentence() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "上一句",
                        modifier = Modifier.rotate(180f),
                    )
                }
                IconButton(onClick = { AudioEngine.togglePlay() }) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = { AudioEngine.nextSentence() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一句")
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}
