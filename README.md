# English Pod · 原生 Android 学习客户端

把 [english.52131415.xyz](https://english.52131415.xyz/) 这个英语播客学习站点做成了原生 Android 应用，
并在原站功能之上补充了一整套「精听 + 复习 + 统计」的学习功能。

- 包名：`com.englishpod.learning`（debug 构建为 `com.englishpod.learning.debug`，可与正式版共存）
- 版本：1.1（versionCode 2）
- 技术栈：Kotlin 2.0.21 + Jetpack Compose（Material 3）+ Media3 ExoPlayer + OkHttp
- 最低支持：Android 8.0（API 26），目标 API 35
- 产物：`EnglishPod-1.1-release.apk`（已签名，2.1 MB）、`EnglishPod-1.1-debug.apk`（20 MB）

---

## 一、原站功能 → App 对应实现

| 原站能力 | App 中的实现 |
| --- | --- |
| 课程列表（111 节 / 初级 中级 高级） | 首页课程列表，支持搜索 + 难度筛选 + 课程数统计 |
| 音频播放、倍速 0.5×–2× | 播放器卡片，倍速菜单（0.5/0.75/1/1.25/1.5/2×） |
| 逐句字幕高亮、自动滚动 | 「字幕」页签：当前句高亮 + 自动滚动（可关闭） |
| 章节跳转（完整播客 / 原速 Dialogue / 最后词汇复习） | 播放器下方章节胶囊，点击跳转，播放中自动高亮当前章节 |
| 词汇表 | 「词汇」页签，带发音位置，可单点播放 / 循环 / 收藏 |
| 讲义笔记 | 「讲义」页签，按小节分组，每条可跳到对应音频位置 |
| 对话文本 | 「对话」页签，话轮气泡排版，点击复制整行 |
| 收藏课程 | 课程页右上角书签，本地持久化，可离线查看 |
| 本地进度 | 自动记忆播放位置，首页「继续收听」卡片一键续播 |

## 二、新增功能（原站没有的）

**精听练习**
- **逐句精听**：句末自动暂停，停顿时间 0/0.8/1.2/2/3 秒可调，可自动续播下一句
- **单句循环 / A-B 复读**：任意句或任意区间反复听
- **遮蔽字幕（盲听）**：先听再看，点一下才显示当前句
- **跟读练习**：先播原句 → 自动开始录音 → 对比试听，支持重录
- **听写训练**（v1.1）：听音频写句子 / 拼单词，词级 diff 标出漏词与多词，带准确率、首字母提示、看原文
- **静音跳过**（v1.1）：自动跳过长句之间的静音
- **睡眠定时**：5/15/30/60 分钟后自动暂停
- ● 相关设置全部在「我的 → 设置 → 学习」中

**学习笔记**（v1.1）
- 播放时一键「记笔记」，自动带上当前音频位置与该句字幕
- 课程页「笔记」页签按时间顺序列出，可一键回到那个位置重听
- 「我的 → 学习笔记」汇总所有课程的笔记，可按课程筛选、复制全部、删除

**播放体验**（v1.1）
- **暂停后回退 3 秒**：长时间暂停再继续时自动往回退一点，接上上下文
- **字幕字号**：小 / 标准 / 大 / 特大四档，实时生效

**词汇学习**
- **点词查义**：字幕里点任意单词，弹出释义、可补充自己的释义、一键加入生词本、循环该句
- **生词本**：本地持久化，支持搜索、待复习/已掌握筛选、手动添加、整词发音回放、整本导出到剪贴板
- **间隔重复复习**：Leitner 8 级调度（1 分钟 → 10 分钟 → 1 天 → … → 30 天），
  四档反馈（不认识 / 模糊 / 认识 / 简单）自动安排下次复习时间
- **词汇测验**：两种题型（看释义四选一、看释义拼写），题库可选生词本或任意课程词汇，每轮 10 题带即时判分

**离线与后台**
- **离线下载**：课程音频 + 课程数据（字幕/词汇/讲义/对话）一起下载，飞行模式下可完整使用
- **后台播放**：前台服务 + 通知栏控制（上一句 / 播放暂停 / 下一句 / 关闭），锁屏不中断

**学习数据**
- **学习统计**：累计时长、连续打卡天数、学习课程数、生词/掌握数、听写次数、笔记条数、7 天与 30 天时长趋势图（含每日目标虚线）、**12 周打卡热力图**、最近学习列表
- **今日学习计划**（v1.1）：首页按「今日目标 + 待复习生词 + 未听完的课程」生成一句话计划，并给出复习 / 听写 / 笔记快捷入口
- **每日目标**：10–60 分钟可调，首页顶部实时显示完成度
- **数据备份与恢复**（v1.1）：一键导出学习记录为 JSON（系统文件选择器，默认存到「下载」），换机后导入即可合并恢复

**体验**
- 深色模式（跟随系统 / 浅色 / 深色）+ Android 12 动态取色
- 全局迷你播放器：任何页面都能看到当前句、显示播放状态并控制
- **课程排序**（v1.1）：默认顺序 / 最近学习 / 时长从短到长 / 时长从长到短 / 进度从低到高
- **自定义服务器地址**：内置地址校验与连通性测试（见下方安全说明）
- 课程列表与课程内容本地缓存，无网络时仍可浏览

## 三、代码结构

```
app/src/main/java/com/englishpod/learning/
├── MainActivity.kt            入口，权限申请，主题装配
├── EnglishPodApp.kt           Application，单例容器
├── core/
│   ├── UrlGuard.kt            出站地址校验（协议 + 主机 + DNS 解析）
│   ├── TextDiff.kt            词级 LCS 比对（听写判分、提示、准确率）
│   └── Fmt.kt                 时间/时长格式化
├── data/
│   ├── Models.kt              Course / Cue / Chapter / Note / VocabItem / Bookmark …
│   ├── CourseApi.kt           REST 客户端（OkHttp + org.json）
│   ├── LocalStore.kt          收藏、生词本 + SRS、笔记、进度、统计、设置、导入导出
│   └── Repository.kt          内存 → 磁盘 → 网络的取数顺序、离线下载
├── player/
│   ├── AudioEngine.kt         单例播放引擎：逐句、循环、A-B、静音跳过、回退、睡眠定时、时长统计
│   ├── ClipPlayer.kt          单句/单词区间播放（听写、单词发音）
│   ├── PlaybackService.kt     前台服务 + 通知栏控制
│   └── ShadowingRecorder.kt   跟读录音与回放
└── ui/
    ├── AppNav.kt              底部导航 + NavHost + 迷你播放器
    ├── theme/Theme.kt         配色与主题
    ├── components/Common.kt   课程卡片、等级徽标、统计块等复用组件
    ├── home/HomeScreen.kt     课程列表 / 搜索 / 筛选 / 排序 / 今日计划
    ├── detail/CourseScreen.kt    播放器面板、章节、跟读、记笔记、睡眠定时
    ├── detail/CourseContent.kt   字幕(可调字号) / 词汇 / 讲义 / 对话 / 笔记 五个页签
    ├── dictation/DictationScreen.kt  句子听写 + 单词听写
    ├── wordbook/WordbookScreen.kt    生词本（含发音回放、听写入口）
    ├── review/ReviewScreen.kt    间隔重复复习
    ├── quiz/QuizScreen.kt        词汇测验
    ├── stats/StatsScreen.kt      学习统计、趋势图与打卡热力图
    └── mine/MineScreen.kt        我的 / 收藏 / 离线下载 / 笔记 / 设置
```

> 单元测试在 `app/src/test/java/.../core/TextDiffTest.kt`，覆盖听写判分的比对逻辑（7 个用例）。

## 四、逆推出的接口契约

原站是 Vite + React 单页应用，数据来自同源 REST 接口：

| 接口 | 说明 |
| --- | --- |
| `GET /api/courses` | 课程目录：`id / level / number / fileNumber / title / originalTitle / duration / available / noteCount` |
| `GET /api/courses/{id}` | 课程详情：`cues[]`（逐句字幕 `start/end/text`）、`chapters[]`、`notes[]`、`vocabulary[]`、`dialogueText[]`、`curated` |
| `GET /media/{id}` | 音频（`audio/mp4`，支持 Range，可续传），即 App 下载的离线文件来源 |

课程 id 形如 `beginner-01` / `intermediate-01` / `advanced-01`，共 111 节。

## 五、构建与安装

```bash
cd EnglishPod

# 调试包（包名 com.englishpod.learning.debug）
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 正式包（R8 压缩 + 签名，包名 com.englishpod.learning）
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk

# 单元测试
./gradlew testDebugUnitTest
```

签名信息放在 `keystore.properties`（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`），
对应密钥库 `englishpod-release.jks`。**发布前请换成你自己的密钥库**：删掉这两个文件后重新执行

```bash
keytool -genkeypair -keystore englishpod-release.jks -alias <你的别名> \
  -keyalg RSA -keysize 2048 -validity 10000
```

然后把新口令写进 `keystore.properties`。若该文件不存在，release 构建会退回到 debug 签名，不会失败。

> 项目路径含中文时已在 `gradle.properties` 中开启 `android.overridePathCheck=true`（AGP 默认会拒绝非 ASCII 路径）。
> 另需注意：Gradle 的 JVM 测试任务在**非 ASCII 路径**下会报 `ClassNotFoundException`（测试类加载失败），
> 如果 `./gradlew testDebugUnitTest` 失败但编译通过，把项目复制到纯英文路径再跑一次即可。

依赖版本（为保证离线可构建，全部选用本机 Gradle 缓存中已有的版本）：
AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 / Compose BOM 2024.12.01 /
Navigation 2.7.7 / Lifecycle 2.8.7 / Media3 1.4.1 / OkHttp 4.12.0 / JUnit 4.13.2。

## 六、安全说明

应用会向外部服务器发起请求，因此在 `core/UrlGuard.kt` 中集中做了出站地址校验，
所有请求（课程列表、课程详情、音频、设置页的连接测试）都会经过它：

- 只允许 `http` / `https` 协议；
- 拒绝 `localhost`、`*.local`、`*.internal`、`*.home.arpa` 等本机/内网主机名；
- 拒绝字面量形式的环回、私有、链路本地与保留地址
  （`127.0.0.0/8`、`10/8`、`172.16/12`、`192.168/16`、`169.254/16`、`100.64/10`、`0/8`、`224/4`、`240/4`
  以及 `::1`、`fc00::/7`、`fe80::/10`、`ff00::/8`、IPv4-mapped、NAT64、6to4 等）；
- 发请求前对主机名做 DNS 解析，只要解析结果里出现上述被禁地址即拒绝（成功结果按主机缓存，失败不缓存）。

因此「设置」里虽然可以自定义服务器地址，但无法把它指向本机或内网。

其他隐私相关：所有学习数据（收藏、生词本、笔记、进度、统计）只保存在应用私有目录，
导出的备份文件要通过系统文件选择器由用户亲自选择保存位置才会写出；
不采集设备标识、不上报任何数据；音频与课程内容版权归原站所有。

## 七、已知限制

- 跟读录音保存在缓存目录，每次只保留最近一次，不参与云同步；
- 「讲义」标签只在少数课程有内容（原站数据如此），其余课程显示空状态并引导到字幕/词汇；
- 词汇测验需要该课程词汇表至少有 4 个词；单词听写需要生词本中有「带音频位置且来自课程」的单词；
- 听写每轮最多 15 句（重点句）或 20 个单词，句子听写会自动跳过超短句；
- 首次冷启动需要联网拉取课程目录；之后目录与看过的课程均为本地缓存；
- 在配置较低的模拟器上（传感器 HAL 占满 CPU、JIT 编译期）可能触发系统级 ANR 提示，
  日志特征为 `sensors-service.multihal` 高占用 + `Load` 大于 7，属于模拟器资源问题，点「等待」即可恢复，
  真机或负载正常的模拟器上没有出现。

