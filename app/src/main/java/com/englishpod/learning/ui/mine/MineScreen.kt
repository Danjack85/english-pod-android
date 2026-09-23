package com.englishpod.learning.ui.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.core.Fmt
import com.englishpod.learning.core.UrlGuard
import com.englishpod.learning.data.CourseApi
import com.englishpod.learning.data.Settings
import com.englishpod.learning.player.AudioEngine
import com.englishpod.learning.ui.Routes
import com.englishpod.learning.ui.components.CourseCard
import com.englishpod.learning.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(navController: NavHostController) {
    val store = EnglishPodApp.store
    val repository = EnglishPodApp.repository
    val stats by store.stats.collectAsStateWithLifecycle()
    val words by store.wordbook.collectAsStateWithLifecycle()
    val favorites by store.favorites.collectAsStateWithLifecycle()
    val bookmarks by store.bookmarks.collectAsStateWithLifecycle()
    val progress by store.progress.collectAsStateWithLifecycle()
    val downloads by repository.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var downloadedCount by remember { mutableStateOf(repository.downloadedIds().size) }
    var downloadBytes by remember { mutableStateOf(repository.totalDownloadBytes()) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("我的", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        downloadedCount = repository.downloadedIds().size
                        downloadBytes = repository.totalDownloadBytes()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "已学习 ${Fmt.studyMinutes(stats.totalSeconds)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = "连续打卡 ${stats.streak()} 天 · 生词 ${words.size} 个 · 收藏 ${favorites.size} 节",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }

            item {
                EntryRow(
                    icon = Icons.Filled.Bookmark,
                    title = "我的收藏",
                    subtitle = "${favorites.size} 节课程",
                    onClick = { navController.navigate(Routes.FAVORITES) },
                )
            }
            item {
                EntryRow(
                    icon = Icons.Filled.DownloadDone,
                    title = "离线下载",
                    subtitle = "$downloadedCount 节 · ${formatBytes(downloadBytes)}",
                    onClick = { navController.navigate(Routes.DOWNLOADS) },
                )
            }
            item {
                EntryRow(
                    icon = Icons.Filled.EditNote,
                    title = "学习笔记",
                    subtitle = if (bookmarks.isEmpty()) "还没有笔记" else "${bookmarks.size} 条笔记",
                    onClick = { navController.navigate(Routes.NOTES) },
                )
            }
            item {
                EntryRow(
                    icon = Icons.Filled.Insights,
                    title = "学习统计",
                    subtitle = "时长趋势、打卡与生词增长",
                    onClick = { navController.navigate(Routes.STATS) },
                )
            }
            item {
                EntryRow(
                    icon = Icons.Filled.Settings,
                    title = "设置",
                    subtitle = "服务器地址、播放与主题",
                    onClick = { navController.navigate(Routes.SETTINGS) },
                )
            }
            item {
                EntryRow(
                    icon = Icons.Filled.Info,
                    title = "关于 English Pod",
                    subtitle = "版本 $versionName · 数据来自 english.52131415.xyz",
                    onClick = { },
                )
            }

            item {
                Text(
                    text = "本应用是 english.52131415.xyz 的原生客户端，音频与课程数据版权归原站所有。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
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
                modifier = Modifier.size(40.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(navController: NavHostController) {
    val store = EnglishPodApp.store
    val repository = EnglishPodApp.repository
    val favorites by store.favorites.collectAsStateWithLifecycle()
    val courses by repository.courses.collectAsStateWithLifecycle()
    val progress by store.progress.collectAsStateWithLifecycle()

    val list = remember(favorites, courses) {
        courses.filter { it.id in favorites }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("我的收藏", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.Bookmark,
                    title = "还没有收藏课程",
                    subtitle = "在课程页右上角点书签图标，就能把课程加入收藏，方便随时回来听。",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { course ->
                    CourseCard(
                        course = course,
                        progressFraction = progress[course.id]?.fraction ?: 0f,
                        favorite = true,
                        downloaded = repository.isDownloaded(course.id),
                        onClick = { navController.navigate(Routes.course(course.id)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: NavHostController) {
    val repository = EnglishPodApp.repository
    val courses by repository.courses.collectAsStateWithLifecycle()
    val downloads by repository.downloads.collectAsStateWithLifecycle()
    val progress by EnglishPodApp.store.progress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var ids by remember { mutableStateOf(repository.downloadedIds()) }
    var confirmClear by remember { mutableStateOf(false) }

    val active = downloads.filter {
        it.value.status == com.englishpod.learning.data.DownloadState.Status.DOWNLOADING ||
            it.value.status == com.englishpod.learning.data.DownloadState.Status.QUEUED
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("离线下载", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (ids.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "清空全部下载")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "共 ${ids.size} 节 · ${formatBytes(ids.sumOf { repository.downloadSize(it) })}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (active.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "正在下载 ${active.size} 节课程",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            active.values.forEach { state ->
                                Spacer(modifier = Modifier.height(8.dp))
                                val label = courses.firstOrNull { it.id == state.courseId }?.title ?: state.courseId
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                LinearProgressIndicatorInline(state.progress)
                            }
                        }
                    }
                }
            }

            if (ids.isEmpty() && active.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.DownloadDone,
                        title = "还没有离线课程",
                        subtitle = "在课程页右上角点下载按钮，音频会保存到手机里，没网也能听。",
                    )
                }
            } else {
                items(ids, key = { it }) { id ->
                    val course = courses.firstOrNull { it.id == id }
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
                                Text(
                                    text = course?.title ?: id,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatBytes(repository.downloadSize(id)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                navController.navigate(Routes.course(id))
                            }) { Text("播放") }
                            IconButton(onClick = {
                                repository.deleteDownload(id)
                                ids = repository.downloadedIds()
                                scope.launch { snackbarHostState.showSnackbar("已删除离线文件") }
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部离线文件") },
            text = { Text("将删除 ${ids.size} 节课程的离线音频，之后需要联网才能播放。") },
            confirmButton = {
                TextButton(onClick = {
                    repository.clearAllDownloads()
                    ids = emptyList()
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LinearProgressIndicatorInline(progress: Float) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val store = EnglishPodApp.store
    val repository = EnglishPodApp.repository
    val settings by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var addressInput by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var testing by remember { mutableStateOf(false) }
    var addressMessage by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = store.exportJson()
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
        }.isSuccess
        scope.launch {
            snackbarHostState.showSnackbar(if (ok) "已导出学习数据" else "导出失败")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                snackbarHostState.showSnackbar("读取文件失败")
                return@launch
            }
            store.importJson(text)
                .onSuccess { counts ->
                    snackbarHostState.showSnackbar(
                        "已恢复：收藏 +${counts.favorites}，生词 +${counts.words}，笔记 +${counts.bookmarks}",
                    )
                }
                .onFailure { snackbarHostState.showSnackbar("文件格式不正确") }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(title = "课程服务器")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = {
                            addressInput = it
                            addressMessage = null
                            addressError = false
                        },
                        label = { Text("服务器地址") },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        singleLine = true,
                        isError = addressError,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                    )

                    if (addressMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = addressMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (addressError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val raw = addressInput
                                scope.launch {
                                    testing = true
                                    runCatching {
                                        val normalized = UrlGuard.parse(raw)
                                        val api = CourseApi { normalized.toString() }
                                        val count = api.listCourses().size
                                        normalized.toString() to count
                                    }.onSuccess { (normalized, count) ->
                                        store.updateSettings { it.copy(baseUrl = normalized) }
                                        repository.invalidate()
                                        repository.refresh(force = true)
                                        addressMessage = "连接成功，共 $count 节课程"
                                        addressError = false
                                        snackbarHostState.showSnackbar("服务器地址已更新")
                                    }.onFailure { failure ->
                                        addressMessage = failure.message ?: "连接失败"
                                        addressError = true
                                    }
                                    testing = false
                                }
                            },
                            enabled = !testing,
                        ) {
                            if (testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("保存并测试")
                        }
                        TextButton(
                            onClick = {
                                addressInput = Settings.DEFAULT_BASE_URL
                                addressMessage = null
                                addressError = false
                            },
                            enabled = !testing,
                        ) { Text("恢复默认") }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "出于安全考虑，只允许 http/https 公网地址；本机、内网与保留地址会被拒绝。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSection(title = "外观")

            SettingChips(
                title = "主题",
                options = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = settings.themeMode.coerceIn(0, 2),
                onSelect = { index -> store.updateSettings { it.copy(themeMode = index) } },
            )

            SettingChips(
                title = "字幕字号",
                options = listOf("小", "标准", "大", "特大"),
                selectedIndex = com.englishpod.learning.data.Settings.SUBTITLE_SCALES
                    .indexOfFirst { kotlin.math.abs(it - settings.subtitleScale) < 0.01f }
                    .coerceAtLeast(0),
                onSelect = { index ->
                    store.updateSettings {
                        it.copy(
                            subtitleScale = com.englishpod.learning.data.Settings.SUBTITLE_SCALES[index],
                        )
                    }
                },
            )

            SettingSwitch(
                title = "动态取色",
                subtitle = "使用系统壁纸配色（Android 12 及以上）",
                checked = settings.dynamicColor,
                onCheckedChange = { value -> store.updateSettings { it.copy(dynamicColor = value) } },
            )

            SettingsSection(title = "学习")

            SettingChips(
                title = "每日目标",
                options = listOf(10, 15, 20, 30, 45, 60).map { "$it 分" },
                selectedIndex = listOf(10, 15, 20, 30, 45, 60)
                    .indexOf(settings.dailyGoalMinutes)
                    .coerceAtLeast(0),
                onSelect = { index ->
                    store.updateSettings { it.copy(dailyGoalMinutes = listOf(10, 15, 20, 30, 45, 60)[index]) }
                },
            )

            SettingSwitch(
                title = "逐句精听自动继续",
                subtitle = "每句暂停后自动播放下一句，关闭则停在该句等你跟读",
                checked = settings.autoPlayNext,
                onCheckedChange = { value -> store.updateSettings { it.copy(autoPlayNext = value) } },
            )

            SettingChips(
                title = "逐句停顿",
                options = listOf(0, 800, 1200, 2000, 3000).map {
                    if (it == 0) "不停顿" else "${it / 1000.0}s"
                },
                selectedIndex = listOf(0, 800, 1200, 2000, 3000)
                    .indexOf(settings.sentencePauseMs)
                    .coerceAtLeast(0),
                onSelect = { index ->
                    store.updateSettings {
                        it.copy(sentencePauseMs = listOf(0, 800, 1200, 2000, 3000)[index])
                    }
                },
            )

            SettingChips(
                title = "默认倍速",
                options = listOf(0.75f, 1f, 1.25f, 1.5f).map { "${it}×" },
                selectedIndex = listOf(0.75f, 1f, 1.25f, 1.5f)
                    .indexOf(settings.speed)
                    .coerceAtLeast(0),
                onSelect = { index ->
                    store.updateSettings { it.copy(speed = listOf(0.75f, 1f, 1.25f, 1.5f)[index]) }
                },
            )

            SettingSwitch(
                title = "静音跳过",
                subtitle = "自动跳过长句之间的静音，泛听更连贯",
                checked = settings.skipSilence,
                onCheckedChange = { value -> AudioEngine.setSkipSilence(value) },
            )

            SettingSwitch(
                title = "暂停后回退 3 秒",
                subtitle = "长时间暂停再播放时，往回退一点接上下文",
                checked = settings.rewindOnResume,
                onCheckedChange = { value -> store.updateSettings { it.copy(rewindOnResume = value) } },
            )

            SettingsSection(title = "数据")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "学习记录包含收藏、生词本、笔记、播放进度与统计，全部保存在本机。可以导出成 JSON 文件备份，换机后导入即可恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            exportLauncher.launch("english-pod-backup.json")
                        }) {
                            Icon(
                                Icons.Filled.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("导出备份")
                        }
                        TextButton(onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }) {
                            Icon(
                                Icons.Filled.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("导入恢复")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            EnglishPodApp.store.flushNow()
                            scope.launch { snackbarHostState.showSnackbar("已写入本机存储") }
                        }) {
                            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即保存")
                        }
                        TextButton(onClick = { confirmReset = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("清空学习记录", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Text(
                text = "清空后无法恢复，请谨慎操作。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("清空学习记录") },
            text = { Text("将删除全部收藏、生词本、播放进度和学习统计，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val context = EnglishPodApp.instance
                    runCatching {
                        java.io.File(context.filesDir, "learner-data.json").delete()
                    }
                    confirmReset = false
                    scope.launch {
                        snackbarHostState.showSnackbar("已清空，重启应用后生效")
                    }
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SettingChips(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
