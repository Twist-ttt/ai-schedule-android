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
