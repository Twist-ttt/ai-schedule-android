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
