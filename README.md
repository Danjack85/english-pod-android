# English Pod · 原生 Android 学习客户端

把 [english.52131415.xyz](https://english.52131415.xyz/) 这个英语播客学习站点做成了原生 Android 应用，
并在原站功能之上补充了一整套「精听 + 复习 + 统计」的学习功能。

- 包名：`com.englishpod.learning`（debug 构建为 `com.englishpod.learning.debug`，可与正式版共存）
- 版本：1.2（versionCode 3）
- 技术栈：Kotlin 2.0.21 + Jetpack Compose（Material 3）+ Media3（ExoPlayer/MediaSession）+ WorkManager + Glance + OkHttp
- 最低支持：Android 8.0（API 26），目标 API 35
- 产物：`EnglishPod-1.2-release.apk`（已签名，约 9 MB，内置 5.7 万词离线词典）、`EnglishPod-1.2-debug.apk`

---

## 一、原站功能 → App 对应实现

| 原站能力 | App 中的实现 |
| --- | --- |
| 课程列表（111 节 / 初级 中级 高级） | 首页课程列表，支持搜索 + 难度筛选 + 五种排序 |
| 音频播放、倍速 0.5×–2× | 播放器卡片，细档变速（0.5–2.0，0.05 步进，变速不变调）+ 自定义滑杆 |
| 逐句字幕高亮、自动滚动 | 「字幕」页签：当前句高亮 + 自动滚动（可关闭）+ 遮蔽盲听 + 四档字号 |
| 章节跳转 | 播放器下方章节胶囊，播放中自动高亮当前章节 |
| 词汇表 | 「词汇」页签，带发音位置，可单点播放 / 循环 / 收藏 |
| 讲义笔记 | 「讲义」页签，按小节分组，每条可跳到对应音频位置 |
| 对话文本 | 「对话」页签，话轮气泡排版，点击复制整行 |
| 收藏课程 | 课程页右上角书签，本地持久化 |
| 本地进度 | 自动记忆播放位置，首页「继续收听」卡片一键续播 |

## 二、新增功能（原站没有的）

**精听练习**
- **逐句精听**：句末自动暂停，停顿 0/0.8/1.2/2/3 秒可调，可自动续播下一句
- **单句循环 / A-B 复读**：任意句或区间反复听；**复读次数可设 1/2/3/5 遍**，读满自动进下一句
- **遮蔽字幕（盲听）**：先听再看，点一下才显示当前句
- **跟读三件套**：先听原句 → 自动录音 → **对比回放**（原声↔我的录音交替两轮）→ **发音评分**（系统语音识别 + 词级比对，逐词标出读对/读漏/多读；仅限带语音服务的手机，不支持时自动隐藏）
- **静音跳过 / 人声增强 / 暂停后回退 3 秒**：通勤场景三件套
- **睡眠定时**：5/15/30/60 分钟

**锁屏与蓝牙（Media3 MediaSession）**
- 锁屏媒体卡片（进度条 + 控制）、蓝牙/有线耳机线控（上一句/下一句映射为句间跳转）

**离线词典查词（ECDICT）**
- 内置 [ECDICT](https://github.com/skywind3000/ECDICT)（MIT 许可）裁剪版：56,992 个高频词，中文释义 + 音标 + 柯林斯/牛津/考试标签 + 词频段位
- 点字幕里**任意**单词都能查（不再限于课程词），存生词自动带出释义
- 生词本显示词频段位（1k/2k/…），难词单独标记

**词汇训练**
- **生词本**：点词查义收藏、手动添加、搜索、待复习/已掌握/难词筛选、整词发音回放（课程音频片段或 TTS）、导出剪贴板/**Anki 兼容 TSV**
- **间隔重复复习**：Leitner 八级调度 + 四档反馈；**难词（leech）** 连续遗忘 4 次自动标记
- **词汇测验三种**：看释义四选一（**形近词智能干扰项**）、看释义拼写、**听力填空**（听句子挖空选词，答错自动进复习队列）
- **听写训练**：句子听写 + 单词听写，词级 diff 标出漏词多词
- **词汇量测试**：12 个词频频段抽样 48 词自评，估测词汇规模并保留历史

**离线与后台**
- **离线下载**：音频 + 课程数据一起下载，飞行模式完整可用
- **后台播放**：前台服务 + 媒体通知，锁屏不中断

**习惯与留存**
- **每日复习提醒**：WorkManager 定时通知今日待复习数（时间可选，可能有几分钟系统调度偏差）
- **桌面小部件**：今日进度条 + 连击天数 + 待复习数 + 一键继续播放
- **学习统计**：时长趋势图、12 周打卡热力图、复习评级分布、未来 7 天到期预测、最近学习
- **今日学习计划**：按目标/待复习/未听完生成一句话计划 + 快捷入口
- **数据备份**：学习记录 JSON 导出/导入合并恢复

**体验**
- 深色模式 + Android 12 动态取色、全局迷你播放器、课程排序
- **TTS 朗读**：任意单词/例句可用系统语音朗读（无音频的手动生词也能听；缺语音包时给引导提示）
- 自定义服务器地址（带安全校验）、课程列表本地缓存

## 三、代码结构

```
app/src/main/java/com/englishpod/learning/
├── MainActivity.kt            入口，权限，主题，小部件刷新，自动续播入口
├── EnglishPodApp.kt           Application，单例容器，提醒频道
├── core/
│   ├── UrlGuard.kt            出站地址校验（协议 + 主机 + DNS 解析）
│   ├── TextDiff.kt            词级 LCS 比对（听写/发音评分共用）
│   ├── DistractorPicker.kt    形近词智能干扰项
│   └── Fmt.kt                 时间/时长格式化
├── data/
│   ├── Models.kt              Course / Cue / Bookmark / WordEntry…
│   ├── CourseApi.kt           REST 客户端（OkHttp + org.json）
│   ├── DictDatabase.kt        ECDICT 离线词典（assets/dict.db，只读 SQLite）
│   ├── LocalStore.kt          生词本+SRS、笔记、进度、统计、设置、导入导出
│   └── Repository.kt          内存 → 磁盘 → 网络取数、离线下载
├── player/
│   ├── AudioEngine.kt         播放引擎：逐句/循环/复读计数/增强/回退/睡眠/时长统计
│   ├── ClipPlayer.kt          单句/单词区间播放
│   ├── PlaybackService.kt     MediaSessionService：锁屏/蓝牙/媒体通知
│   ├── SpeechScorer.kt        跟读发音评分（SpeechRecognizer + TextDiff）
│   ├── TtsPlayer.kt           系统 TTS 封装（语音包检测）
│   └── ShadowingRecorder.kt   跟读录音与回放
├── work/ReminderWorker.kt     每日复习提醒（WorkManager）
├── widget/StatsWidget.kt      Glance 桌面小部件
└── ui/
    ├── AppNav.kt              底部导航 + NavHost + 迷你播放器
    ├── home/HomeScreen.kt     课程列表 / 搜索 / 排序 / 今日计划
    ├── detail/CourseScreen.kt    播放面板、章节、跟读评分、记笔记
    ├── detail/CourseContent.kt   字幕/词汇/讲义/对话/笔记 五页签 + 词典查词
    ├── dictation/DictationScreen.kt  句子 + 单词听写
    ├── wordbook/WordbookScreen.kt    生词本（难词/词频/TTS/Anki 导出）
    ├── review/ReviewScreen.kt    间隔重复复习
    ├── quiz/QuizScreen.kt        选词 / 拼写 / 听力填空
    ├── vocabtest/VocabTestScreen.kt  词汇量测试
    ├── stats/StatsScreen.kt      统计、热力图、评级分布、到期预测
    └── mine/MineScreen.kt        我的 / 收藏 / 下载 / 笔记 / 设置
```

> 词典资产由 `tools/build_dict.py` 从 ECDICT 生成（见 `tools/` 说明），单元测试覆盖
> TextDiff（7 用例）与 DistractorPicker（6 用例）。

## 四、逆推出的接口契约

| 接口 | 说明 |
| --- | --- |
| `GET /api/courses` | 课程目录：`id / level / number / fileNumber / title / originalTitle / duration / available / noteCount` |
| `GET /api/courses/{id}` | 课程详情：`cues[]`（逐句字幕 `start/end/text`）、`chapters[]`、`notes[]`、`vocabulary[]`、`dialogueText[]`、`curated` |
| `GET /media/{id}` | 音频（`audio/mp4`，支持 Range） |

课程 id 形如 `beginner-01` / `intermediate-01` / `advanced-01`，共 111 节。

## 五、构建与安装

```bash
cd EnglishPod

# 调试包
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 正式包（R8 + 签名）
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk

# 单元测试
./gradlew testDebugUnitTest
```

签名信息在 `keystore.properties`（已 gitignore），密钥库 `englishpod-release.jks`。
**发布前请换成你自己的密钥库**；文件缺失时 release 自动退回 debug 签名。

> 注意：① 项目路径含中文时需 `android.overridePathCheck=true`（已配置）；
> ② Gradle JVM 测试任务在非 ASCII 路径下会报 `ClassNotFoundException`，跑测试请把项目放到纯英文路径；
> ③ `assets/dict.db` 已随仓库提供，也可用 `tools/build_dict.py` 重新生成（需下载 ECDICT 原始库）。

## 六、安全与隐私

- 所有出站请求过 `core/UrlGuard.kt`：仅 http/https；拒绝本机/内网/保留地址
  （IPv4 环回、私网、链路本地、CGNAT、组播等 + IPv6 `::1`、`fc00::/7`、`fe80::/10`、
  IPv4-mapped、NAT64、6to4）；请求前做 DNS 解析核验
- 学习数据全部存本机私有目录；导出备份需用户经系统文件选择器亲自选择位置
- 跟读录音只存缓存目录且仅保留最近一次；不做云端同步
- **发音评分**使用系统 SpeechRecognizer：识别音频由系统语音服务处理，App 自身不采集、不上传；
  无识别服务的设备自动隐藏该入口
- 版权与署名：音频与课程内容版权归原站；离线词典基于
  [ECDICT](https://github.com/skywind3000/ECDICT)（MIT License）裁剪生成

## 七、已知限制

- 句级中文翻译依赖原站数据（无），故提供的是词级中文释义（离线词典）
- 发音评分与 TTS 依赖设备能力：无 GMS/语音包的设备自动降级或给安装引导
- 每日提醒走 WorkManager 周期任务，触发时刻可能有几分钟系统调度偏差
- 听力填空/单词听写需要课程词汇带音频位置；生词本少于 4 词时四选一不可用
- 低配模拟器上可能出现系统级 ANR 提示（传感器 HAL 高占用所致），真机未见
- 首次冷启动需联网拉课程目录；之后目录与看过的课程均为本地缓存
