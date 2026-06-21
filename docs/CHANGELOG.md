# CHANGELOG（开发进度与每次操作总结）

> 规则：每个 Git 增量提交都在此追加一条「三件套」——
> **① 做了什么** ｜ **② AI 提示词与我的修改**（详见 `prompts.md` 对应轮次）｜ **③ 遇到的问题与解决**。

---

## 阶段 0｜工程 + Git 起步

### `chore: 初始化工程骨架（包名 com.qiu.aischedule，配置依赖）`

- **① 做了什么**：
  - 基于 Android Studio 生成的空工程（"No Activity" 模板）进行改造。
  - 包名/应用 ID 由 `com.example.myapplication` 改为 `com.qiu.aischedule`；`rootProject.name` 改为 `AISchedule`。
  - 在 `gradle/libs.versions.toml` 与 `app/build.gradle.kts` 中配置依赖：RecyclerView、ConstraintLayout、CardView、Lifecycle(LiveData/ViewModel)、Room(runtime+compiler)、OkHttp、Gson、security-crypto。
  - 移除模板自带、包名已失效的 `ExampleUnitTest` / `ExampleInstrumentedTest`（断言引用了旧包名）。
  - 新增启动 Activity：`MainActivity`（首页占位）+ `res/layout/activity_main.xml` + Manifest 注册 LAUNCHER；`app_name` 改为"AI日程转换助手"。
  - 新建 `docs/`：`prompts.md`、`CHANGELOG.md`、`设计文档.md`；补充 `.gitignore`（`/app/build`、`.kotlin` 等）。
  - `git init`（分支 `main`），首次提交。

- **② AI 提示词与修改**：见 `prompts.md` 第 0 轮。关键修改：保留模板的 compileSdk 36/minSdk 24 而非方案里的 34/26（避免与 AGP 9.1.1 冲突）；不照搬 AI 的依赖版本，自己核对坐标与版本号。

- **③ 问题与解决**：
  - 问题：模板主源码集 `app/src/main/java` 下没有 MainActivity（No Activity 模板），Manifest 也无启动入口。解决：自行创建 `MainActivity` 与 intent-filter。
  - 问题：模板测试文件包名为 `com.example.myapplication`，重命名后会编译/断言失败。解决：删除模板测试桩（作业不要求单元测试），后续如需测试再按新包名添加。

- **验证（✅ 通过）**：命令行 `./gradlew :app:assembleDebug` 成功（2m36s，BUILD SUCCESSFUL，无编译错误）；`adb install -r` + `am start` 在 Pixel 7（emulator-5554）成功安装并启动，首页占位界面正常显示。阶段 0 结束，可进入阶段 1。

---

## 阶段 1｜Room 数据层

### `feat(room): 三表实体 + DAO + AppDatabase + Repository + 线程池`

- **① 做了什么**：
  - `data/local/entity/`：`EventRecord`（日程）、`ParseHistory`（解析历史）、`ApiConfig`（配置，固定单行 id=1），字段与 PPT 一致；时间统一存 epoch 毫秒。
  - `data/local/dao/`：`EventDao`/`HistoryDao`/`ApiConfigDao`，增查改删齐全（满足"CRUD≥3"）；读返回 `LiveData` 用于列表自动刷新；`getByIdSync` 供详情/通知后台预取。
  - `data/local/AppDatabase`：Room 单例，`@Database(version=1, exportSchema=false)`。
  - `data/repository/ScheduleRepository`：UI 唯一数据入口；读返回 LiveData，写经 `AppExecutors.diskIO()` 切后台。
  - `util/AppExecutors`：单线程磁盘 IO + 主线程切换。
- **② AI 提示词与修改**：见 `prompts.md`「Room 数据库」。要点：实体用 public 字段；`exportSchema=false`；ApiConfig 固定单行；自定义 `Callback<T>` 不依赖 `java.util.function`；写操作全部后台线程。
- **③ 问题与解决**：主线程访问 Room 会抛 `IllegalStateException` → Repository 所有写操作包进 `executors.diskIO().execute(...)`；同步读方法注明"须在后台线程调用"。
- **验证（✅ 通过）**：`./gradlew :app:assembleDebug` BUILD SUCCESSFUL（48s），Room 注解处理器成功生成 DAO/Database 实现类，无编译/SQL 错误。阶段 1 结束，可进入阶段 2（界面 + RecyclerView + CRUD）。

---

## 阶段 2｜界面 + RecyclerView + CRUD（2a 核心闭环）

### `feat(ui): 首页→确认页→看板页→详情页 核心 CRUD 闭环`

- **① 做了什么**：
  - 新增 4 个 Activity：`MainActivity`（首页输入）、`AiConfirmActivity`（确认/手动填写保存）、`ScheduleListActivity`（CalendarView + RecyclerView 看板）、`DetailActivity`（改/删）。
  - `EventAdapter`（ListAdapter + DiffUtil）+ `item_event.xml`，每项展示 标题/时间/地点/提醒 共 4 字段。
  - `util/DateUtils`：epoch 毫秒格式化/解析、按日区间。
  - 配套布局、strings、Manifest 注册；首页"我的日程"跳转、看板页 FAB 新增。
  - 数据流：确认页 → Repository.insertEvent → 看板页 LiveData 自动刷新；详情页 observeEvent 填充，更新/删除后台执行后看板自动刷新。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计 / RecyclerView」。要点：换日内存过滤（避免多 observer）；确认页字段全可编辑（降级）；ListAdapter + DiffUtil。
- **③ 问题与解决**：`Calendar.set` 没有 7 参数重载导致编译失败 → 改为逐字段 set + 单独置 MILLISECOND=0（详见 `prompts.md`「调试错误」）。
- **验证**：`./gradlew :app:assembleDebug` BUILD SUCCESSFUL（43s）。运行时 adb 冒烟测试因模拟器已关闭未执行；CRUD 流程待你在 Android Studio 中验证。阶段 2a 结束，下一步 2b（解析历史页 + 设置页）。

### `feat(ui): 解析历史页 + 设置页 + 菜单导航 + API Key 加密存储（阶段2b）`

- **① 做了什么**：
  - `HistoryActivity` + `HistoryAdapter` + `item_history.xml`：第 2 个 RecyclerView，展示 原句 / AI 返回 JSON / 模型·可信度·时间（≥3 字段），observe `getHistory()` 自动刷新；空态。
  - `SettingsActivity` + `activity_settings.xml`：配置 provider/baseUrl/model（存 Room `api_config`）+ API Key（`EncryptedSharedPreferences` 加密存储）；默认 DeepSeek。
  - `security/SecretStore`：EncryptedSharedPreferences（AES256_GCM + Keystore）封装，明文 Key 不入库不入日志。
  - `res/menu/menu_main.xml`：溢出菜单"解析历史/设置"；接入 `MainActivity` 与 `ScheduleListActivity`。
  - Manifest 注册两个新 Activity。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」2b。要点：Key 不入 Room 表，单独加密存储（设计文档"第三方库"已说明 security-crypto）。
- **③ 问题与解决**：无（一次编译通过）。历史页目前为空，将在阶段 4 接入 DeepSeek 后产生记录。
- **验证（✅ 通过）**：`./gradlew :app:assembleDebug` BUILD SUCCESSFUL（31s）。阶段 2（2a+2b）完成，6 页齐全；进入阶段 3（ContentProvider）。

---

## 阶段 3｜ContentProvider

### `feat(provider): ScheduleProvider 全4项 + 看板页改经 ContentProvider 读取`

- **① 做了什么**：
  - `provider/ScheduleProvider`：暴露 events 表，URI `content://com.qiu.aischedule.provider/events`（列表）与 `/events/{id}`（单条）；实现 query/insert/update/delete 全 4 项 + `getType`；`UriMatcher` 区分列表/单条。
  - `EventDao` 增加 `getAllCursor/getByIdCursor`（返回 Cursor），`update/delete` 改返回 `int`。
  - `ScheduleRepository`：事件写入后 `notifyChange`。
  - `ScheduleListActivity`：改为经 `ContentResolver.query` 读取 + `ContentObserver` 自动刷新（直接在 UI 体现 CP 用法）。
  - Manifest 注册 provider（`exported=true`，便于 adb 验证与视频演示）。
- **② AI 提示词与修改**：见 `prompts.md`「ContentProvider」。
- **③ 问题与解决**：CP 方法运行在调用方线程，需在非主线程访问 Room → 看板页查询放在 `AppExecutors.diskIO()`。
- **验证（✅ 通过）**：`assembleDebug` 成功（37s）；运行时 `adb content query / insert / delete` 全部成功（插入→查到→删除→清空），无崩溃。阶段 3 结束，进入阶段 4（DeepSeek 网络）。

---

## 阶段 4｜网络（DeepSeek）

### `feat(network): DeepSeek 异步解析 + 回填确认页 + ParseHistory`

- **① 做了什么**：
  - `network/LlmClient`（OkHttp 异步 + Gson）：调用 OpenAI 兼容 `/chat/completions`（默认 DeepSeek），解析为 `ParsedSchedule`；不在主线程；超时 30/60s。
  - `network/ParsedSchedule`：标题/日期/时间/地点/提醒/needClarification/question。
  - System Prompt 注入今天日期，要求只输出 JSON；解析时截取 `{...}` 容错。
  - 首页新增「AI 解析」按钮 + ProgressBar：解析后字段自动回填确认页；needClarification 时 toast 追问。
  - `AiConfirmActivity` 支持 AI 字段回填；保存时标记对应 ParseHistory 为 `isApplied=true`。
  - 无 API Key / 网络失败 → toast + 降级手动填写。
  - Manifest 加 `INTERNET` 权限。
- **② AI 提示词与修改**：见 `prompts.md`「网络访问」。
- **③ 问题与解决**：模型偶尔输出代码块/说明文字 → 截取首个 JSON 对象再解析；字段类型不稳 → optStr/optInt/optBool 容错。
- **验证**：`assembleDebug` 成功（39s）；启动无崩溃。真实 AI 解析需在设置页填入 DeepSeek API Key 后由你在 AS 中验证。阶段 4 结束，进入阶段 5（通知）。

---

## 阶段 5｜通知（Notification）

### `feat(notify): AlarmManager 到点提醒 + NotificationChannel + 点击进详情`

- **① 做了什么**：
  - `notify/Notifier`：创建通知渠道(IMPORTANCE_HIGH)并弹通知；点击跳详情页（测试通知跳首页）；读日程在后台线程。
  - `notify/ReminderScheduler`：`AlarmManager.setAndAllowWhileIdle` 在 `startTime-reminderMinutes` 定时；按 eventId 作 requestCode，支持 schedule/cancel。
  - `notify/AlarmReceiver`：到点接收 → 弹通知。
  - 确认页保存（reminderMinutes>0 且在未来）设置提醒；详情页更新重设、删除取消。
  - 菜单"测试通知(5秒)"演示整条链路；MainActivity 请求 POST_NOTIFICATIONS（Android 13+）。
  - Manifest 注册 `AlarmReceiver`(exported=false) + POST_NOTIFICATIONS 权限。
- **② AI 提示词与修改**：见 `prompts.md`「通知 Notification」。要点：用 `setAndAllowWhileIdle` 免额外权限；Receiver 读 DB 走后台线程。
- **③ 问题与解决**：
  - API26+ 不建 channel 通知不显示 → 创建 NotificationChannel（minSdk24 用 `SDK_INT>=O` 兼容）。
  - Android 13+ 通知不出 → 运行时请求 POST_NOTIFICATIONS。
  - 验证时 `am broadcast` 无法触达 `exported=false` 的 Receiver（系统安全限制）→ 临时导出验证成功后还原。
- **验证（✅ 通过）**：`assembleDebug` 成功；临时导出 Receiver 后 `am broadcast` 触发，`AlarmReceiver→Notifier→通知` 成功发出（dumpsys 见 `schedule_reminder` 通道、importance=4/HIGH），随后还原 `exported=false`。**8 项硬性技术要求全部覆盖**，阶段 5 结束。

---

## 阶段 6（收尾）｜UI/UX 优化

### `fix(ui): 玻璃拟态配色偏淡化 + 霜层加厚 + ActionBar 改白霜自适应`

- **① 做了什么**（纯资源层，零 Java、零 drawable 改动）：
  - **渐变底偏淡化**：白天由高饱和品牌紫蓝（`#6A11CB→#3F6EF5→#2575FC`）改为低饱和高明度粉彩雾色（`#EEF2FF` 淡薰衣草 → `#E0F2FE` 淡天蓝 → `#FDF4FF` 淡粉白），呼应"色系偏淡"诉求；夜间深邃靛蓝渐变保留。
  - **玻璃霜加厚**：白天面板霜 18%→65%（`#2EFFFFFF→#A6FFFFFF`），描边 40%→纯白高光（`#66FFFFFF→#FFFFFFFF`），让面板真正"浮"于淡彩底之上，解决原先"霜太薄、看起来像廉价浅蓝色块"的问题；夜间同步霜 24%→50%、描边 30%→60%。
  - **聚焦态着色**：输入框聚焦描边由"更亮的白"改为靛蓝（白天 `#4F46E5` / 夜间 `#A5B4FC`），聚焦反馈更明确。
  - **ActionBar 改白霜自适应**：白天由半透明黑条（`#33000000` + 白字）改为白霜条（`#B3FFFFFF`）+ 深色标题/图标，并 `windowLightStatusBar=true`（状态栏图标深色）；夜间保持深色条 + 白字（`windowLightStatusBar=false`，由 `values-night/themes.xml` 覆盖）。标题与图标色改用 `glass_text_primary` token，昼夜自动切换。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 6 轮。要点：背景是纯渐变而非图片，故不需要真 backdrop blur——把底做淡、霜做厚，glassmorphism 观感即达成，仍保持纯资源层、不增依赖。
- **③ 问题与解决**：ActionBar 由"黑底白字"翻转为"白霜底深字"时，标题、返回/溢出图标、状态栏图标三处颜色须联动（否则白字落在白霜上看不见）——通过 `glass_text_primary` token + `windowLightStatusBar` 昼夜分离一次性解决。
- **验证**：本机无 java/gradle，构建由你在 Android Studio 中 Sync + Run 确认；改动仅涉及 `values/colors.xml`、`values-night/colors.xml`、`values/themes.xml`、`values-night/themes.xml`、`values/styles.xml` 五个资源文件，无 Java/XML 结构变更，风险面小。

### `fix(ui): 状态栏实色化（修复顶部与标题重叠）+ 玻璃面板加顶部光泽层`

- **① 做了什么**（纯资源层，零 Java）：
  - **修复顶部重叠**：`statusBarColor` 由 `transparent` 改为实色 token `glass_status_bar`（白天 `#EEF2FF` = 渐变顶端淡薰衣草 / 夜间 `#1B1538` = 深靛），强制系统将状态栏视为不透明区域，ActionBar 标题物理上落在其下方，根治"状态栏时间/电量与标题在多页重叠"。
  - **玻璃质感增强**：`bg_glass_card.xml` 由单一半透明 shape 改为 `layer-list`——主体霜层 + 顶部高光渐变（白→透明，模拟玻璃顶光）+ 纯白描边 + 大圆角；新增 `glass_highlight_top` token（白天 `#66FFFFFF` / 夜间 `#33FFFFFF`）。所有用 `GlassContainer` 的卡片（首页 Hero、设置表单、看板/历史列表项）全局自动生效。
  - **霜透明度微调**：白天 65%→60%（`#A6FFFFFF`→`#99FFFFFF`），让淡彩渐变更透出，增强通透感；文字对比仍 >10:1。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 7 轮。调用了 ui-ux-pro-max skill，依据其 `safe-area-awareness`、`system-bar-clearance`、`effects-match-style` 规则。
- **③ 问题与解决**：视觉分析/OCR 工具均报告"状态栏与标题未重叠"，但实机用户明确看到重叠——以实机为准；定位为"透明状态栏 + edge-to-edge opt-out 在 targetSdk 36 下不稳定"的已知脆弱组合，以"状态栏实色化"根治。
- **验证**：本机无法编译，需你在 Android Studio Sync + Run；重点确认设置页/首页/看板顶部状态栏与标题不再重叠，卡片有明显玻璃光泽。

### `fix(ui): 表单页顶部安全区间距（设置/详情/确认页）`

- **① 做了什么**（纯资源层，零 Java）：
  - 设置页、详情页、AI 确认页三个 ScrollView 表单页，把首个内容元素（玻璃面板 / 首个字段 label）的 `layout_marginTop` 由 `12dp` 统一加到 `20dp`，让表单与顶部 ActionBar 标题之间留出明确呼吸距离（根容器 20dp padding + 卡片 20dp marginTop = ActionBar 下方约 40dp），根治"provider 这一行像是被顶栏压住、内容太靠上"的观感。三页结构一致，统一间距避免页面间不一致。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 8 轮。用户给出完整 glassmorphism 重设计规格，本轮先落地其中优先级最高的"设置页顶部安全区间距"。
- **③ 问题与解决**：主题为 `DarkActionBar`，框架本就把内容放在 ActionBar 下方；但 ActionBar 为 70% 白霜半透明条，与渐变底色相近，视觉上标题与首张卡片界限模糊，显得"贴在一起"。解决方式是单纯加大顶部留白（不改 ActionBar、不加 fitsSystemWindows，避免不可编译验证的 inset 行为）。
- **验证**：本机无法编译，需你在 Android Studio Sync + Run；重点确认设置页 provider 字段不再贴近标题栏。

### `feat(ui): 背景柔光斑 + 玻璃面板/输入框/按钮层级质感`

- **① 做了什么**（纯资源层，零 Java）：
  - **背景柔光斑**（玻璃感的关键前提）：`bg_app_gradient.xml` 由单一线性渐变改为 `layer-list`——粉彩线性渐变 + 3 处柔和 radial 光斑（左上淡薰衣草 / 右中淡天蓝 / 左下淡薄荷，昼夜各一套 `glass_blob_*` token）。半透明玻璃面板"被磨砂"的彩色底终于存在，玻璃质感得以显现。
  - **玻璃面板**：圆角 24dp→30dp；`GlassContainer` elevation 3dp→8dp，并设 `outlineSpotShadowColor`/`outlineAmbientShadowColor`（API28+，靛蓝软阴影 token `glass_shadow_*`，近似 glassmorphism 彩色投影，pre-28 忽略）；霜透明度 60%→55% 让光斑更透出。
  - **玻璃输入区**：圆角 16dp→20dp；新增独立 `glass_input_fill`(40%)/`glass_input_stroke`(55%) token，比面板更透，落在面板内呈"内陷"层次；聚焦描边改 `#6C63FF`@70%（`glass_card_stroke_focused`）。
  - **按钮**：主按钮圆角 24→26、elevation 2→4（保留实色靛蓝，不冒险换渐变背景以保编译稳妥）；二级玻璃按钮圆角 24→26、改用独立 `glass_btn_glass_fill`(38%)/`glass_btn_glass_stroke`(65% 白)，与卡片拉开"卡片>按钮>输入"三级透明度层次。
  - 所有列表项（看板/历史）走 `GlassContainer`，全局自动生效；昼夜 token 配齐。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 9 轮。依据用户给出的完整 glassmorphism 规格逐条落地优先级 ②（背景光斑）与 ③（卡片/输入/按钮统一玻璃组件）。
- **③ 问题与解决**：纯 XML 无 backdrop blur——继续沿用"霜+顶光+描边+柔和投影"近似，不引入 BlurView/RenderEffect；用 radial gradient 近似模糊光斑，`gradientRadius` 用 dp 维度以适配不同密度。主按钮渐变需自定义 background drawable 且会破坏 Material 涟漪/圆角，本机无法编译验证，故保留实色仅精修圆角与投影。
- **验证**：本机无法编译，需你在 Android Studio Sync + Run；重点确认：首页/设置页背景能看到三处彩色光晕、玻璃面板有柔和靛蓝投影与明显圆角、输入框聚焦时靛蓝描边、二级按钮比卡片更通透。已自检所有 `@color/` token 在 day/night 双套齐全、无悬空引用。

### `fix(ui): 顶部安全区——运行时显式 setDecorFitsSystemWindows(true)`

- **① 做了什么**：
  - 新增 `ui/BaseActivity`（抽象基类，继承 AppCompatActivity）：在 `onCreate` 运行时调用 `WindowCompat.setDecorFitsSystemWindows(getWindow(), true)`，显式要求内容不侵入系统栏。
  - 6 个 Activity（MainActivity / SettingsActivity / ScheduleListActivity / HistoryActivity / AiConfirmActivity / DetailActivity）改为继承 `BaseActivity`（同包，免 import）；各自 `onCreate` 首行 `super.onCreate` 即触发基类的 inset 处理，在 `setContentView` 前生效。
  - **根因修复**：targetSdk 36（API 35+）默认强制 edge-to-edge，且**忽略主题里的 `windowOptOutEdgeToEdgeEnforcement`**——所以上一轮"状态栏实色 + marginTop"没根治：内容（设置页 provider、看板页 "June 2026"）仍画到状态栏 / 刘海区域下方。运行时 `setDecorFitsSystemWindows(true)` 是 SDK35+ 下覆盖该默认值的权威方式，让系统栏保持不透明占位，ActionBar 标题与页面内容都落在状态栏下方。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 10 轮。用户明确指出这是"布局没吃到 Scaffold innerPadding / 没加 statusBarsPadding"的 inset 问题，非配色问题；翻译到本项目 Java+View 技术栈即 setDecorFitsSystemWindows。
- **③ 问题与解决**：用户提示词里用 Compose（Scaffold / statusBarsPadding）描述——本项目是 AppCompatActivity+XML，不能直接用；以运行时 setDecorFitsSystemWindows 等价实现"内容避开系统栏"。
- **验证**：本机无法编译，需你在 Android Studio Sync + Run；重点确认 ① 设置页 provider 在"设置"标题下方、不进状态栏；② 看板页 "June 2026" 月份栏在日历卡片内、远离摄像头打孔。若个别 API35+ 设备仍重叠，则需进一步改 NoActionBar+Toolbar+WindowInsetsListener（本轮先用最小风险的运行时调用）。

### `fix(ui): 玻璃卡片投影/高光加强 + 输入框与二级按钮层级拉开 + 看板日历卡留白`

- **① 做了什么**（纯资源层，零 Java）：
  - **卡片质感更明确**：`GlassContainer` elevation 8dp→12dp，靛蓝软阴影更明显；顶部高光 `glass_highlight_top` 40%→55%，玻璃顶光更亮（响应"高光边框和阴影再明确一点"）。
  - **输入框 vs 二级按钮层级拉开**：输入框霜 40%→45%（更实，作主输入更突出）；二级玻璃按钮霜 38%→28%（更轻）；形成 卡片 55% > 输入 45% > 按钮 28% 三级层次，解决"输入框与二级按钮都是浅色玻璃块、层级太接近"。夜间按钮同步 30%→20%。
  - **看板页日历卡留白**：顶部 marginTop 12→16dp，安全区修复后给月份切换更多呼吸距离。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 11 轮。落地用户反馈的第 4、5 点（玻璃质感加强、输入/按钮层级区分）。
- **③ 问题与解决**：二级按钮高度差异化（用户建议 44–48dp）未做——MaterialButton 的 inset 与 minHeight 联动复杂、本机无法编译验证，避免把按钮改得过矮；层级改用透明度区分，更稳妥。
- **验证**：本机无法编译，需你在 Android Studio Sync + Run；重点确认卡片有更明显柔和投影与顶部高光、首页二级按钮明显比输入框更通透。

### `fix(ui): ActionBar 加底部分隔线，与表单玻璃卡明确分层`

- **① 做了什么**（纯资源层，零 Java）：
  - 新增 `bg_appbar.xml`（layer-list）：白霜填充 + 底部 1dp 分隔线（`glass_appbar_divider` token，昼夜各一套）。ActionBar 样式 `Widget.MyApp.ActionBar` 的 background 由单色 `glass_appbar_bg` 改为此 drawable。
  - **根因**：半透明白霜 ActionBar（70% 白）与紧贴其下的玻璃表单卡（55% 白）都是白色、无分隔，视觉糊成一块，被误判为"重叠"。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 12 轮。用户报告"设置页两个白底黑字与 API 配置框重叠"，但 OCR / 图像分析显示该截图文字无实际重叠（"设置"出现一次、"提供方"在其下方）——判定为半透明标题栏与白卡的视觉融合，以底部分隔线强制分层。
- **③ 问题与解决**：未用 elevation 阴影（经典 ActionBar 的 elevation 阴影渲染不稳定），改用 layer-list 底部 1dp 描边，确定性更强。
- **验证**：本机无法编译，需你在 Android Studio **Build → Rebuild Project 后重新安装运行**（上一轮 safe-area 修复在 Java 代码 commit `aff3fbe`，必须重新编译才生效）。重点确认 ActionBar 下方有一道细分隔线、标题栏与表单不再糊成一块。

### `fix(ui): 内容根加 paddingTop=actionBarSize，根治与 ActionBar 重叠`

- **① 做了什么**（纯资源层，零 Java）：6 个布局的根容器统一加 `android:paddingTop="?attr/actionBarSize"`（首页 LinearLayout / 设置·详情·确认 ScrollView / 历史 FrameLayout / 看板 CoordinatorLayout）。
- **根因（用户诊断确认）**：targetSdk 36 强制 edge-to-edge 下，内容框架虽落在状态栏下方，却**不自动避开装饰层 ActionBar**——布局内容画到半透明 ActionBar 后面，ActionBar 在上层，故"日程看板"标题与"June 2026"重叠。`setDecorFitsSystemWindows(true)` 只管系统栏（状态/导航），管不到装饰层 ActionBar 的偏移。
- **修复**：内容根顶部留出一个 ActionBar 高度（`?attr/actionBarSize`，即 ActionBar 自身高度），把内容精确推到 ActionBar 下方。无需改主题/布局结构/Java，每页一行属性。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 13 轮。用户用"显示布局边界"诊断后明确告知：标题在状态栏下、与 June 2026 重叠、处于上层——据此精确定位为"内容画到 ActionBar 后面"。
- **③ 问题与解决**：未用 NoActionBar+Toolbar 大重构（13 文件、本机不可编译验证、风险高）；先用最小风险的一行 padding 精准修复，等价于把内容手动推到 ActionBar 下方。
- **验证**：本机无法编译，需你在 Android Studio **Rebuild + 重装**；重点确认看板页"June 2026"落在"日程看板"标题下方、不再重叠，设置页 provider 落在标题下方。若个别页面仍有偏差（actionBarSize 与实机 ActionBar 高度不符），告诉我具体页面，微调即可。

### `fix(ui): 取消装饰层 ActionBar，标题改入内容层 MaterialToolbar——根治重叠`

- **① 做了什么**：
  - **主题** `Theme.MyApplication` 父主题 `DarkActionBar`→`NoActionBar`（values + values-night），取消会与内容重叠的装饰层 ActionBar。
  - **BaseActivity** 重写：① `setDecorFitsSystemWindows(false)` 拥抱 edge-to-edge；② 重写 `setContentView`，把布局里的 `MaterialToolbar` 设为 ActionBar（`setSupportActionBar`，标题/菜单由此承载）；③ 用 `WindowInsetsCompat` 把系统栏 inset 作为内容根 `padding`——Toolbar 落在状态栏下方、内容落在 Toolbar 下方。
  - **6 个布局**统一改造：根容器改为垂直 LinearLayout，首子视图为 `MaterialToolbar`（id=toolbar，背景沿用 bg_appbar 含底部分隔线），原内容作为第二子视图（ScrollView/FrameLayout/CoordinatorLayout 用 height=0dp+weight=1 填充）。所有控件 ID、文本、样式不变（已 grep 自检）。
- **根因（用户诊断确认 + 分析）**：targetSdk 36 强制 edge-to-edge，内容框架从窗口顶 y=0 铺满，装饰层 ActionBar 叠在内容之上——内容画到 ActionBar 后面，半透明时标题与内容重叠。在"装饰层 ActionBar + 内容"两层架构上猜偏移（前几轮 statusBar/actionBarSize padding）永远差一截。
- **为什么这次能根治**：标题与内容**同在内容层、纵向堆叠**（Toolbar 是布局首子视图，内容是其后续兄弟），重叠在结构上不可能。不再依赖任何猜出来的偏移量。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 14 轮。用户质问"装饰层 ActionBar 和布局内容这两不应该不重叠吗"——确认应从架构层面消除两层重叠，而非继续打 padding 补丁。
- **③ 问题与解决**：改动较大（主题+基类+6 布局）、本机不可编译，做成原子提交便于整笔 `git revert`；MaterialToolbar 走 `setSupportActionBar` 保留原溢出菜单（历史/设置/测试通知）。
- **验证**：本机无法编译，需你在 Android Studio **Rebuild + 重装**；重点确认：① 各页标题在状态栏下方、与内容不再重叠；② 首页/看板页右上角溢出菜单（⋮）仍可用。若启动崩溃，把 logcat 报错发我，或 `git revert HEAD` 整笔回退到上一版。

### `fix(ui): 确认页区分 AI 解析/手动填写两模式，消除"两入口同页"语义混淆`

- **① 做了什么**：
  - `AiConfirmActivity` 引入 `EXTRA_MODE`（`MODE_AI` / `MODE_MANUAL`），区分两种进入模式。
  - 首页次按钮文案「解析 / 手动填写」→「手动填写」；点击时**不再携带首页输入框原句**、显式标 `MODE_MANUAL`——手动填写即"从零新建"，不依赖也不引用首页那句话。
  - 看板页 FAB「新增日程」同样标 `MODE_MANUAL`（原先不带任何 extra 进同一页面，顶部显示一行尴尬的"（手动填写：直接编辑下方字段）"）。
  - 确认页按模式区分呈现：**AI 模式**——标题「确认日程」+ 显示「原始输入」引用块（对照原句核对解析结果）；**手动模式**——标题「新建日程」+ 隐藏引用块（普通新建表单）。布局把「原始输入」label 与 `tvSource` 包进 `sourceBlock` 容器，整块控可见性。
  - `EXTRA_MODE` 默认值取 `MODE_MANUAL`：任何漏传都退化为自洽的"新建"表单，绝不出现"引用块 + 空内容"的尴尬态。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 15 轮。要点：不拆成两个 Activity（表单+保存逻辑重复一遍、本质问题没解决）；用模式标记复用同一确认页，根治"两个入口进同一页面但文案/语义混淆"。
- **③ 问题与解决**：根因**不是**"共用一个页面"——AI 预填 vs 手动空白复用同一表单是标准做法；真正问题是 ① 次按钮文案「解析 / 手动填写」误导（它根本不解析任何东西）② 手动路径下「原始输入」引用块语义错位（没经 AI 却把输入框原句当"原始输入"展示）。两者都不是拆页面能解决的。
- **验证**：本机无法编译，需你在 Android Studio **Rebuild + 重装**；重点确认三条路径：① 首页点「手动填写」→ 标题"新建日程"、无引用块、字段空白可填；② 首页输一句话点「AI 解析」→ 标题"确认日程"、引用块显示原句、字段被预填；③ 看板页 FAB → 同手动模式。

### `feat(ui): 日期/时间改 Material 选择器 + 历史相对时间（日期时间结构化）`

- **① 做了什么**：
  - **`DateUtils` 扩展**：① 选择器时区换算 `utcMidnightToLocal`/`localMidnightToUtc`（MaterialDatePicker.selection 是 UTC 零点，须换算）；② `combine(localMidnight,hour,minute)` 合成当天时刻 millis；③ `hourMinuteOf`/`parseDateMillis`/`parseHourMinute` 拆分与解析；④ **本地化显示** `formatDateLocalized`/`formatTimeLocalized`（系统地区 + 12/24 小时自适应，不写死 `yyyy-MM-dd`）；⑤ **相对时间** `relative`（`getRelativeTimeSpanString`，"5 分钟前"/"昨天"）。
  - **确认页 + 详情页**：日期/时间字段改**只读**（`focusable=false`+`inputType=none`+禁长按），点击弹 `MaterialDatePicker`/`MaterialTimePicker`；内部以结构化状态（`dateMillis`/`hour`/`minute`）持有，**仅显示时格式化**为字符串；保存/更新用 `combine` 合成 epoch millis，**取代 `parseDateTime` 的字符串解析**。
  - **`MaterialTimePicker` 遵循系统 12/24 小时制**（`DateFormat.is24HourFormat`）。
  - **历史页**：解析时间由绝对字符串改为相对时间，并修掉 meta 行误用 `history_label_input`（"原句"）标签的 bug。
  - 字段 label 去掉写死的格式提示（"日期 (yyyy-MM-dd)"→"日期"），新增选择器标题串。
- **② AI 提示词与修改**：见 `prompts.md`「界面设计」第 16 轮。要点：不引入 java.time/desugaring——核对发现项目**存储/业务层已是 epoch long**（符合"四层"），真正短板只在 UI 输入边界，故用 Material 选择器 + `android.text.format.DateUtils`（系统级、零新依赖、自动本地化）补齐。
- **③ 问题与解决**：
  - 用户给的四层架构（java.time/选择器/Compose/desugaring）理论正确，但**对照代码发现项目存储层（`EventRecord.startTime` 等）和业务层都已是 epoch long**，字符串只出现在 UI 边界——故不做大重构，只补 UI 输入边界的选择器化。
  - `MaterialDatePicker.selection` 是 **UTC 零点** millis 的时区陷阱：用 UTC `Calendar` 提取年月日再合成本地 millis，避免跨时区日期偏移。
  - `MaterialTimePicker` 的 positive 监听器是 `View.OnClickListener`（`onClick(View v)` 有参），区别于 `MaterialDatePicker` 的 `OnSelectionListener`（`onSelection(S)` 有参 selection）——查 material-components-android 源码确认 `v -> {...}` 写法正确。
- **验证**：本机无法编译，需你在 Android Studio **Rebuild + 重装**；重点确认：① 确认页/详情页点日期弹日历、点时间弹时钟、选完回填、保存生效；② 历史页时间显示"X 分钟前"且无"原句"字样；③ 不同时区日期不错位。Material 选择器 API 已查源码（material-components-android master）确认无误。

### `feat(ui): 日期选择器改玻璃转轮(快捷选月) + 全局锁中文 Locale`

- **① 做了什么**：
  - **语言统一（问题 1）**：`BaseActivity.attachBaseContext` 强制 `Locale.SIMPLIFIED_CHINESE`，所有 Activity 及其内部系统组件（日期/时间选择器）统一中文。
  - **日期选择器改玻璃转轮（问题 2）**：新建 `GlassDatePickerDialog`（DialogFragment）——玻璃容器（`bg_glass_card`）+ 年/月/日 三个 `NumberPicker` 转轮并排，**均可独立滚动**，月份直达（不再只能箭头翻）。window 背景置透明让玻璃圆角显现；NumberPicker 文字色统一 `glass_text_primary`；年/月变化联动重算当月天数（防"2 月 30 日"）。确认页/详情页 `showDatePicker` 改用它，移除 `MaterialDatePicker`。
  - `DateUtils` 加 `ofDate(year,month0,day)`；`strings` 加 年/月/日 标签、确定/取消；新增 `dialog_date_picker.xml`。
- **② AI 提示词与修改**：见 `prompts.md` 第 17 轮。要点：spinner 与玻璃有张力（`DatePickerDialog` 的转轮/window 难玻璃化、theme 配置本机无法验证）→ 自定义 `DialogFragment`+`NumberPicker` 完全可控玻璃风格。
- **③ 问题与解决**：
  - 问题 1 根因：app `strings.xml` 是硬编码中文（界面中文与 locale 无关），而 `MaterialTimePicker`/`DatePicker` 依赖 `Locale.getDefault()`（设备若 en 则 picker 英文）→ 锁中文 Locale 统一。
  - 问题 2 根因：`MaterialDatePicker` 无 month picker（库设计限制，月份只能箭头翻）→ 自定义玻璃转轮对话框。
  - NumberPicker 文字色：遍历子 `EditText` 设色（避免反射设分割线的脆弱写法）；分割线接受默认。
  - DialogFragment 的 listener（lambda）在配置变化重建时会丢 → 回调判 null 防崩（演示场景可接受）。
- **验证**：本机无法编译，需 AS **Rebuild + 重装**；重点确认：① 日期/时间选择器全中文；② 日期对话框三个转轮可独立滚、月份直达、玻璃外观（圆角白霜 + 靛蓝确定按钮）；③ 选 2 月天数正确（闰年 29 天）；④ 确定后回填、保存生效。

### `feat(ui): 时间选择器改玻璃转轮(时/分)，与日期风格统一`

- **① 做了什么**：
  - 时间选择器从 `MaterialTimePicker` 改为自建 `GlassTimePickerDialog`（DialogFragment）：玻璃容器 + **时/分两个 NumberPicker 转轮**，时显示 00-23、分显示 00-59（`setDisplayedValues` 两位数对齐），均可循环滚动；与 `GlassDatePickerDialog` 风格统一。
  - 移除 `MaterialTimePicker`/`TimeFormat`/`DateFormat` 依赖；确认页/详情页 `showTimePicker` 改用 `GlassTimePickerDialog`。
  - `strings` 加 时/分 标签；新增 `dialog_time_picker.xml`。
- **② AI 提示词与修改**：见 `prompts.md` 第 18 轮。要点：时间选择器与日期统一为玻璃转轮；不再需要系统 12/24 制判断（转轮固定 00-23 显示）。
- **③ 问题与解决**：原 `MaterialTimePicker` 需 `DateFormat.is24HourFormat` + `TimeFormat` 判断 12/24 制，改转轮后固定 24 小时制（00-23）显示，逻辑简化；`setDisplayedValues` 让时/分两位数对齐。
- **验证**：本机无法编译，需 AS **Rebuild + 重装**；重点确认：点时间字段弹玻璃转轮（时/分），选完回填、保存生效。

---

## 阶段 7｜看板与卡片体验优化

> 来源：评审反馈四条——① 看板顶部「今日摘要」；② 日程卡片突出时间；③ 保存成功明确反馈；④ 统一圆角/间距/尺寸设计令牌。

### `feat(ui): 设计令牌统一(dimens.xml) + 日程卡片改时间锚点布局`

- **① 做了什么**：
  - 新建 `res/values/dimens.xml`：圆角（卡片 20dp / 输入 16dp / 按钮 24dp）、间距（页面边距 20dp / 区块 16dp / 卡片间距 12dp）、尺寸（卡片内边距 16dp / 按钮最小高 52dp）统一收口为设计令牌。
  - `bg_glass_card`（30dp→20dp）、`bg_glass_input`（20dp→16dp）的 `<corners>` 改引用 `@dimen/corner_*`；`styles.xml` 按钮 `cornerRadius`（26dp→24dp）与 `minHeight` 改引用令牌。全 App 卡片/输入/按钮圆角从此一致。
  - 日程卡片 `item_event.xml` 重构：信息优先级 **时间 > 标题 > 地点 > 提醒**——左侧强调色条 + `HH:mm` 大字（强调色）作视觉锚点，右侧标题（主）+「地点 · 提前N分钟提醒」复合副行（次）。`EventAdapter` 相应改：`tvTime` 仅显时分、新增 `tvMeta` 合并地点与提醒（无提醒则省略）。
- **② AI 提示词与修改**：见 `prompts.md` 第 19 轮。要点：卡片由"四字段平均堆叠"改为"时间锚点 + 复合副行"，提升扫读效率；设计令牌以 `@dimen` 引用进 drawable（AAPT2 支持）。
- **③ 问题与解决**：`<corners android:radius>` 引用 `@dimen` 需 AAPT2 解析（现代 AGP 支持）；`bg_glass_card` 是 layer-list，两层 shape 的圆角必须同时改否则高光层与主体圆角不匹配——两层都引用同一令牌。
- **验证**：本机无法编译，需 AS **Sync + Run**；重点确认：① 全 App 卡片圆角统一为 20dp、无圆角错位；② 日程卡片左侧大字时间 + 右侧标题/地点·提醒；③ 地点为空时显"未设置地点"、无提醒时副行省略提醒段。

### `feat(ui): 看板页今日摘要（数量 + 最近一项）`

- **① 做了什么**：
  - `activity_schedule_list.xml` 日历与列表之间插入「今日摘要」玻璃面板（`tvSummaryCount` + `tvSummaryNext`）；页面左右边距改 `@dimen/spacing_page_h`(20dp)。
  - `ScheduleListActivity.updateSummary()`：选中日为今天时显「今天有 N 个日程 / 今天还没有日程」；今天且有未开始日程时副行显「最近：HH:mm 标题 · 地点」（按 startTime 升序找首个 ≥ now）。非今天只显「当日有 N 个日程 / 该日暂无日程」。
  - `strings.xml` 加 5 条摘要文案（`summary_today_count` 等，含 `%1$d` 数量占位）。
- **② AI 提示词与修改**：见 `prompts.md` 第 19 轮。要点：摘要只对"今天"显示"最近一项"（其他日期该语感不成立）；dayList 已按 startTime 升序，首个 ≥ now 即最近未开始。
- **③ 问题与解决**：跨天选 0 点比较用 `selectedDayStart == DateUtils.todayStart()`（long 基本类型 == 安全）。
- **验证**：需 AS Run；重点确认：① 今天有多条日程时显数量 + 最近一条；② 全部已过时只显数量（副行隐藏）；③ 切到其他日期副行隐藏、数量行文案变"当日…"。

### `feat(ui): 保存成功反馈对话框（提醒/未提醒/已过 三态）`

- **① 做了什么**：
  - `AiConfirmActivity` 保存后由裸 Toast 改为 `MaterialAlertDialogBuilder` 对话框：标题「日程已创建」，正文三态——已设提醒「将在开始前 N 分钟提醒你」/ 未设「未设置提醒」/ 提醒时间已过「提醒时间已过，本次不会提醒」；按钮「查看日程」点击跳看板页。
  - 提醒是否生效以"实际是否 schedule"为准（`willRemind = reminder>0 && trigger>now`），与 `ReminderScheduler` 实际行为一致。
  - `strings.xml` 加 5 条对话框文案（含 `%1$d` 分钟占位）。
- **② AI 提示词与修改**：见 `prompts.md` 第 19 轮。要点：反馈需明确"提醒已就绪"的安全感；三态覆盖「设了/没设/设了但已过」全部情形。
- **③ 问题与解决**：`willRemind` 在后台线程算好后 `final` 传回主线程弹框；`setCancelable(false)` 防误退丢失反馈。
- **验证**：需 AS Run；重点确认：① 设提醒且未来时间→「将在开始前N分钟提醒你」；② 提醒填0→「未设置提醒」；③ 日程时间已过但填了提醒→「提醒时间已过…」；④ 点「查看日程」进看板且新日程可见。

---

## 文档修订

### `docs: 设计文档首页按钮文案与代码对齐`

- **① 做了什么**：`docs/设计文档.md` 第 3 节「首页输入页」一行的次按钮文案，由「解析·手动填写」改为「手动填写」，与 `strings.xml` 实际文案（`home_to_confirm`）一致。
- **② AI 提示词与修改**：见 `prompts.md`「文档修订」。用户核对作业要求时发现文档与界面文案不一致；核对 `strings.xml:8-10` 实际文案后修正。
- **③ 问题与解决**：阶段 6（commit `7357636`）把首页次按钮语义从"解析+手动填写"改为纯"手动填写"（从零新建），设计文档未同步——本次补齐。纯文档修订，无编译影响。
- **验证**：仅 1 行 markdown 改动，无需构建。
