# AI 提示词使用记录（AI Prompt Log）

> 本文档按《移动应用设计期末项目作业要求》第四节"AI 提示词使用记录要求"维护，
> 与 Git 提交一一对应：每完成一个增量，记录本轮用到的**主要提示词**、**它解决的问题**、以及**自己对 AI 生成结果做了哪些修改**。
> 提示词允许有口语/中文，要求"真实使用过"。应用内的 `ParseHistory` 表也会保存每次"原句→JSON"作为运行时佐证。

分类索引（按作业要求）：
- [项目整体设计](#项目整体设计)
- [界面设计](#界面设计)
- [RecyclerView](#recyclerview)
- [Room 数据库](#room-数据库)
- [ContentProvider](#contentprovider)
- [网络访问](#网络访问)
- [通知 Notification](#通知-notification)
- [调试错误](#调试错误)

---

## 项目整体设计

### 第 0 轮（阶段 0，对应 commit `chore: 初始化工程骨架`）

- **提示词原文**：「阅读 作业要求.docx 与 前期设计汇报.pptx 两个文件，给我一个开发方案。一定要仔细阅读并遵守期末作业要求。」
- **解决的问题**：在动手前先把"作业硬性要求"与"自己的设计"逐条对齐，避免漏掉评分点（特别是 ContentProvider、网络、Notification、分层结构）。
- **AI 生成结果与我的修改**：
  - AI 产出了一份方案文档与"作业要求合规对照表"。我没有照搬，而是核对了评分表 9 项（共 100 分）是否每一项都有落地位置。
  - 我决定 LLM 提供方用 **DeepSeek**（国内访问稳定、OpenAI 兼容），并要求"先有脱离 AI 也能运行的最小版本"，把网络/AI 作为增强项而非单点。
  - 我把 AI 建议的 `compileSdk 34/minSdk 26` 改为沿用 Android Studio 模板实际的 `compileSdk 36/minSdk 24/targetSdk 36`（AGP 9.1.1 环境），并在通知代码里用 `Build.VERSION.SDK_INT >= O` 兼容 API 24–25。

### 第 0 轮续（流程）

- **提示词原文**：「用敏捷开发的方式，每一步要提交 git，并且同步到 github 上。每一次操作都要做好总结，一定一定要遵守期末作业文档要求。」
- **解决的问题**：确定开发节奏与提交规范。
- **我的修改**：约定每个增量 = 一次 `git commit + push`，采用 Conventional Commits；并建立了 `CHANGELOG.md`（每次操作三件套：做了什么 / AI 提示词 / 问题与解决）与本文件。

## 界面设计

（阶段 2 起，记录首页输入页、确认页、看板页、详情页、历史页、设置页的界面相关提示词。）

### 第 2 轮（阶段 2a，对应 commit `feat(ui): 核心 CRUD 闭环`）

- **提示词原文**：「按方案阶段 2 实现核心 CRUD 闭环：首页输入页(MainActivity) → 确认页(AiConfirmActivity，手动填写保存) → 看板页(ScheduleListActivity：CalendarView+RecyclerView) → 详情页(DetailActivity，改/删)。字段：标题/日期/时间/地点/提醒；日历选日筛选当天日程。」
- **解决的问题**：满足"≥3 主要页面""数据从输入页保存到本地数据库""库数据显示到 RecyclerView""修改/删除后界面刷新"等要求；并形成**脱离 AI 也能演示**的完整日程管理器。
- **AI 生成结果与我的修改**：
  - 换日筛选采用 `observe 全部日程 + 内存按日过滤`（而非每次换日重新 observe），避免多个 LiveData observer 泄漏。
  - 确认页字段**全部可编辑**（无 AI 也能手动填写）——这是"降级策略"的落地，演示不依赖网络。
  - 详情页用 `observeEvent(id)` LiveData 填充并保留 `current` 引用，便于更新/删除。

### 第 2b 轮（阶段 2b，对应 commit `feat(ui): 解析历史页 + 设置页`）

- **提示词原文**：「新增解析历史页(第2个RecyclerView，原句/JSON/可信度) 与设置页(provider/baseUrl/model/Key)，Key 用 EncryptedSharedPreferences 加密保存；首页与看板页加溢出菜单导航到这两个页面。」
- **解决的问题**：第 2 个 RecyclerView（再次满足列表要求并为 AI 辅助过程留证据）；设置页为阶段 4 DeepSeek 接入做准备。
- **AI 生成结果与我的修改**：API Key **不进 Room 表**，单独用 `EncryptedSharedPreferences` 加密存储（底层 Keystore），明文不落库、不入日志；设置页 provider/baseUrl/model 默认 DeepSeek，可随时切换；菜单用标准 `onCreateOptionsMenu`。

### 第 6 轮（阶段 6 收尾，对应 commit `fix(ui): 玻璃拟态配色偏淡化`）

- **提示词原文**：「从手机 UI/UX 设计的角度评估项目目前的问题，尤其是 [设置页截图] 出现的问题；要符合 Glassmorphism 风格，色系要偏淡。然后执行修改。」
- **解决的问题**：截图里设置页的玻璃面板在鲜艳紫蓝渐变上糊成"廉价浅蓝色块"，没有玻璃质感，且整体配色偏重、不符合"偏淡"诉求。
- **AI 生成结果与我的修改**：
  - 先做根因诊断：当前"玻璃"只是半透明白色 shape，**Android XML drawable 做不了 backdrop blur**，所以是"伪玻璃"；18% 的白霜盖在鲜艳渐变上必然发灰发蓝。
  - 关键取舍：背景是**纯渐变而非图片**，故不引入 `BlurView` 库、不写 `RenderEffect`——把底色做淡（粉彩雾色）、把霜做厚（65%），glassmorphism 观感自然达成，仍保持纯资源层、零 Java、不增依赖。
  - 让 AI 只改 5 个资源 token 文件（colors day/night、themes day/night、styles），所有 drawable 与布局零改动（已确认布局无硬编码颜色），全局自动生效。
  - 同步处理 ActionBar 翻转的连锁项（标题色、图标色、状态栏图标昼夜分离），避免"白字落在白霜上看不见"。

### 第 7 轮（阶段 6 收尾续，对应 commit `fix(ui): 状态栏实色化 + 玻璃面板顶部光泽`）

- **提示词原文**：「1、[设置页截图] 你看文字之间挡住了，而且多个页面都会有这个问题。2、没有 glassmorphism 的 UI 效果，我有专门的 SKILL 的。」
- **解决的问题**：①实机顶部状态栏（时间/电量）与 ActionBar 标题在多页重叠；②卡片仍像"浅蓝块"，缺少玻璃质感。
- **AI 生成结果与我的修改**：
  - 调用了 ui-ux-pro-max skill 取规范（其 `safe-area-awareness`/`system-bar-clearance`/`effects-match-style` 规则）。
  - **重叠根因**：`statusBarColor=transparent` + 渐变穿透 + `windowOptOutEdgeToEdgeEnforcement` 在 targetSdk 36 下不稳定（部分设备 opt-out 失效被强制 edge-to-edge，标题侵入状态栏）。修复：状态栏改实色（= 渐变顶端色 token `glass_status_bar`，昼夜各一套），强制系统把状态栏当不透明区域，内容自动在其下方。
  - **玻璃质感**：纯 XML 无 backdrop blur，改用 `layer-list` 在 `bg_glass_card.xml` 叠"霜 + 顶部高光渐变 + 描边"近似毛玻璃（新增 `glass_highlight_top` token，昼夜强弱不同）；霜透明度 65%→60% 让淡彩渐变更透出。
  - 保持纯资源层、零 Java、不增依赖；item 列表项本就走 `GlassContainer`，全局自动生效。

### 第 8 轮（阶段 6 收尾续，对应 commit `fix(ui): 表单页顶部安全区间距`）

- **提示词原文**：用户给出一份完整的"浅色 glassmorphism 重设计"诊断与规格（背景太平/卡片不够像玻璃/层级不清/设置页顶部被挤压），并列出三件优先级最高的事：① 修复设置页顶部 Safe Area / AppBar padding；② 给背景加渐变光斑，否则玻璃感出不来；③ 把卡片、输入框、二级按钮统一成真正的玻璃组件。
- **解决的问题**：设置页（及同结构的详情页、确认页）首个字段 label 离顶部 ActionBar 标题太近，像被顶栏压住。
- **AI 生成结果与我的修改**：
  - 定位：主题 `DarkActionBar` 已保证内容在 ActionBar 下方，并非真重叠；问题是 70% 白霜半透明 ActionBar 与渐变底色相近，标题与首张卡片视觉界限模糊。故只做"加大顶部留白"，不动 ActionBar、不加 `fitsSystemWindows`（后者会引入无法本地编译验证的 inset 双计行为）。
  - 修改：三个表单页首个内容元素 `layout_marginTop` 12dp → 20dp，统一间距。本轮只落地优先级 ①，光斑与玻璃质感留到下一轮。

### 第 9 轮（阶段 6 收尾续，对应 commit `feat(ui): 背景柔光斑 + 玻璃面板/输入框/按钮层级质感`）

- **提示词原文**：用户给出完整"浅色 glassmorphism 重设计"规格的三件优先级事项中的 ② 与 ③——给背景加渐变光斑（否则玻璃感出不来），并把卡片、输入框、二级按钮统一成真正的玻璃组件。具体参数：背景蓝紫渐变 + 2–3 处模糊光斑；卡片圆角 28–32dp / 半透明 42–58% / 白边 / 柔和阴影；输入框圆角 22dp / 36% / 聚焦边 #6C63FF@70%；主按钮圆角 26dp / 实色；二级按钮玻璃态 38% / 65% 白边。
- **解决的问题**：原 UI"背景太平、卡片像普通浅色块、层级不清"。
- **AI 生成结果与我的修改**：
  - **根因**：glassmorphism 必须有"被模糊的彩色背景"，原先只是纯线性渐变，半透明卡片后面没有彩色内容可磨砂，必然像浅色块。
  - **背景**：`bg_app_gradient.xml` 改 layer-list = 线性渐变 + 3 处 radial gradient 光斑（左上薰衣草/右中天蓝/左下薄荷），用 `gradientRadius`(dp) + `centerX/Y` 定位光晕中心；昼夜各一套 `glass_blob_*` token。
  - **三级透明度层次**：卡片 55% > 二级按钮 38% ≈ 输入 40%（落在卡片内呈内陷），各自独立 token（`glass_card_fill`/`glass_btn_glass_fill`/`glass_input_fill`），避免原来"都 60% 白糊在一起"。
  - **圆角统一放大**：卡片 24→30、输入 16→20、按钮 24→26；卡片 elevation 3→8 + API28+ 靛蓝软阴影 token（`outlineSpot/AmbientShadowColor`）。
  - **聚焦态**：输入框聚焦描边改 `#6C63FF`@70%（`glass_card_stroke_focused`），反馈更明确。
  - **取舍**：主按钮渐变需自定义 background drawable，会破坏 Material 涟漪/圆角、且本机无法编译验证——保留实色靛蓝，仅精修圆角与投影（用户也认可"可保留强主按钮"）。
  - 保持纯资源层、零 Java、不增依赖；列表项走 `GlassContainer` 全局自动生效。

### 第 10 轮（阶段 6 收尾续，对应 commit `fix(ui): 顶部安全区 setDecorFitsSystemWindows`）

- **提示词原文**：用户指出上一版"设置页顶部仍被状态栏 / 刘海压住（provider 钻进状态栏）、看板页 June 2026 贴近摄像头打孔"，明确这是"布局没吃到 Scaffold innerPadding / 没加 statusBarsPadding"的 inset 问题，而非配色；要求内容必须从 AppBar 下方开始，不能进状态栏。
- **解决的问题**：内容画到状态栏 / 刘海区域下方。
- **AI 生成结果与我的修改**：
  - **根因**：targetSdk 36（API35+）强制 edge-to-edge 且忽略主题 `windowOptOutEdgeToEdgeEnforcement`——上一轮"状态栏实色 + 加 marginTop"治标不治本，内容仍被推到系统栏下。
  - **翻译**：用户用 Compose 术语（Scaffold innerPadding / statusBarsPadding）描述，但本项目是 AppCompatActivity+XML（非 Compose）——等价方案是运行时 `WindowCompat.setDecorFitsSystemWindows(true)`。
  - **实现**：新增 `BaseActivity`，6 个 Activity 改继承它；运行时显式要求内容不侵入系统栏（权威覆盖 SDK35+ 默认 edge-to-edge）。保持 DarkActionBar 主题与实色状态栏，ActionBar 标题与表单 / 日历都落在状态栏下方。
  - **取舍**：先用最小风险的运行时调用；若个别 API35+ 设备仍重叠，再升级为 NoActionBar+Toolbar+WindowInsetsListener（需设备验证，故未一次到位）。

### 第 11 轮（阶段 6 收尾续，对应 commit `fix(ui): 玻璃卡片投影/高光加强 + 输入框/按钮层级拉开`）

- **提示词原文**：用户反馈第 4、5 点——首页"高光边框和阴影还可以再明确一点"；输入框与二级按钮（解析/手动填写、我的日程）"都是浅色玻璃块、层级太接近"，建议输入框更实（38–46%）、二级按钮更轻（28%）。
- **解决的问题**：玻璃质感偏弱、输入框与二级按钮层级不分。
- **AI 生成结果与我的修改**：
  - 卡片 elevation 8→12dp + 顶部高光 40%→55%，让阴影与顶光更明确。
  - 输入框霜 40%→45%、二级按钮霜 38%→28%（夜间 30%→20%），形成 卡片>输入>按钮 三级透明度层次。
  - 看板日历卡 marginTop 12→16dp（配合安全区修复留白）。
  - **取舍**：用户建议二级按钮高度 44–48dp 未改——MaterialButton 的 inset+minHeight 联动复杂、不可编译验证，避免改矮；层级改用透明度区分更稳妥。

### 第 12 轮（阶段 6 收尾续，对应 commit `fix(ui): ActionBar 底部分隔线`）

- **提示词原文**：用户报告"设置 API KEY 的页面，设置两个白底黑字与下面 API 配置框有重叠部分"（附截图）。
- **解决的问题**：标题栏与表单视觉糊成一块（被误判为重叠）。
- **AI 生成结果与我的修改**：
  - **先取证**：对截图做 OCR + 图像分析，两次独立分析均显示"设置"出现一次、"提供方"在其下方、文字无实际重叠——故判定非真重叠，而是半透明 ActionBar（70% 白）与白卡（55% 白）无分隔导致的视觉融合。
  - **修复**：新增 `bg_appbar.xml`（layer-list：白霜 + 底部 1dp 分隔线），ActionBar 改用它；分隔线强制标题栏与表单分层。
  - **取舍**：不用 elevation 阴影（经典 ActionBar 渲染不稳），用描边线更确定。
  - **提醒用户**：safe-area 修复在 Java 代码（commit `aff3fbe`），须 Rebuild + 重装才生效。

### 第 13 轮（阶段 6 收尾续，对应 commit `fix(ui): 内容根 paddingTop=actionBarSize`）

- **提示词原文**：用户按建议开了"显示布局边界"，诊断结果："（日程看板标题）在状态栏下，与 June 2026 重叠，但处于上层"。
- **解决的问题**：布局内容画到装饰层 ActionBar 后面，标题盖住内容顶部。
- **AI 生成结果与我的修改**：
  - **定位**：用户诊断确认——标题（ActionBar，装饰层）位置正确（状态栏下方），但布局内容（June 2026）画到了它后面，ActionBar 在上层 z 序。说明 edge-to-edge 下内容框架没有自动避开 ActionBar（`setDecorFitsSystemWindows` 只管系统栏，不管装饰层 ActionBar 偏移）。
  - **修复**：6 个布局根加 `paddingTop="?attr/actionBarSize"`，把内容精确推到 ActionBar 下方（actionBarSize 即 ActionBar 自身高度）。一行属性、零 Java、零主题改动。
  - **取舍**：未上 NoActionBar+Toolbar 大重构（文件多、本机不可编译、风险高），先用最小风险的 padding 精准修复。

### 第 14 轮（阶段 6 收尾续，对应 commit `fix(ui): 取消装饰层 ActionBar 改 MaterialToolbar`）

- **提示词原文**：用户质问"装饰层 ActionBar 和布局内容还是重叠了，这两不应该不重叠吗？就目前的情况，不应该有重叠部分，因为布局内容很少。你能理解我的意思吗"。
- **解决的问题**：装饰层 ActionBar 与内容层重叠（前几轮 padding 补丁治标不治本）。
- **AI 生成结果与我的修改**：
  - **确认用户判断**：两层本就不该重叠；edge-to-edge 让内容框架铺满全屏、装饰层 ActionBar 叠在内容之上，才是重叠根因。继续在两层架构上猜偏移无解。
  - **架构层面消除重叠**：主题改 `NoActionBar`（取消装饰层 ActionBar）；每页布局内放 `MaterialToolbar`（标题进入内容层）；`BaseActivity` 拥抱 edge-to-edge + 把系统栏 inset 作为内容根 padding。标题与内容同层纵向堆叠，重叠结构上不可能。
  - **取舍**：改动大、本机不可编译，做成原子提交便于回退；保留 `setSupportActionBar` 以沿用原溢出菜单（历史/设置/测试通知）。

### 第 15 轮（阶段 6 收尾续，对应 commit `fix(ui): 确认页区分 AI/手动两模式`）

- **提示词原文**：先问「确认日程中的"原始输入"的功能是什么？」；了解后指出「AI 解析和手动输入应该是两个入口对应两个页面，现在他们都能从 AI 解析和"解析/手动填写"按钮进入同一个页面，这太奇怪了，不符合操作直觉」。
- **解决的问题**：两个入口（首页「AI 解析」/「手动填写」）+ 看板页 FAB 共用同一确认页，但次按钮文案误导、手动模式下还显示语义尴尬的「原始输入」引用块，操作直觉混乱。
- **AI 生成结果与我的修改**：
  - **先诊断再动手**：确认问题真实存在，但根因不是"共用一个页面"——AI 预填 vs 手动空白复用同一表单是标准做法，拆成两个 Activity 会让表单+保存逻辑重复一遍且本质问题没解决。真正问题是 ① 次按钮文案「解析 / 手动填写」含糊（它不解析任何东西，只是带原句进确认页手动填）② 手动路径下「原始输入」引用块语义错位。
  - **方案**：给 `AiConfirmActivity` 加 `EXTRA_MODE`（AI / MANUAL），复用同一 Activity、按模式区分标题与引用块可见性；三处入口（`btnAiParse` / `btnToConfirm` / 看板 FAB）分别标对应模式。
  - **关键取舍**：① 手动模式默认值——`EXTRA_MODE` 漏传时退化 `MODE_MANUAL`，保证退化态也是自洽的新建表单，而非"引用块+空内容"。② 手动填写不再读首页输入框内容——手动=从零，避免"敲了字又点手动、原句被当原始输入显示"的歧义（原句只在 AI 模式有意义）。③ 不拆双 Activity，靠模式标记复用，代码不重复。
  - 用户确认方案后落地为 5 文件改动（`strings.xml` 文案+新标题串、`activity_ai_confirm.xml` 包裹 `sourceBlock`、`AiConfirmActivity` 模式逻辑、`MainActivity` 两入口、`ScheduleListActivity` FAB）。

## RecyclerView

（阶段 2 起，记录日程列表 Adapter、解析历史列表 Adapter 的提示词。）

### 第 2 轮（阶段 2a）

- **提示词原文**：「写 RecyclerView Adapter 展示日程，每项含 标题/时间/地点/提醒（≥3 字段），点击进详情页。」
- **解决的问题**：满足"RecyclerView 列表项≥3 字段"。
- **AI 生成结果与我的修改**：用 `ListAdapter<EventRecord, VH> + DiffUtil.ItemCallback`（而非 `notifyDataSetChanged`）；空地点/0 提醒用占位文案（未设置地点/不提醒）；item 用 `CardView`；点击回调用自定义 `OnEventClickListener` 接口。

## Room 数据库

### 第 1 轮（阶段 1，对应 commit `feat(room): 三表实体 + DAO + AppDatabase + Repository + 线程池`）

- **提示词原文（按方案执行）**：「按方案阶段 1 实现 Room 数据层：EventRecord / ParseHistory / ApiConfig 三实体（字段与 PPT 一致）、对应 DAO、AppDatabase 单例、ScheduleRepository 统一入口；后台线程写、LiveData 读。」
- **解决的问题**：满足作业"必须使用 Room（Entity/DAO/Database）"与"CRUD≥3"，并让后续列表页能通过 LiveData 自动刷新。
- **AI 生成结果与我的修改**：
  - 实体直接用 **public 字段**（Room 原生支持），省去样板 getter/setter；时间字段统一 **epoch 毫秒**，便于排序与到点提醒计算。
  - `exportSchema = false`（作业不做数据库迁移，避免多余 schema 目录）；`ApiConfig` 用固定 `id=1` 单行配置。
  - DAO 读方法返回 `LiveData<List<…>>` / `LiveData<…>`；额外提供 `getByIdSync` 供通知/详情在后台线程预取。
  - Repository 不依赖 `java.util.function.Consumer`（项目未启用 core library desugaring），改用自定义 `Callback<T>`。
  - 主线程访问 Room 会崩 → 所有写操作走 `AppExecutors.diskIO()`。

## ContentProvider

### 第 3 轮（阶段 3，对应 commit `feat(provider): ScheduleProvider 全4项 + 看板页改经 CP 读取`）

- **提示词原文**：「实现 ContentProvider 暴露 events 表，URI 形式 `content://com.qiu.aischedule.provider/events` 与 `/events/{id}`，实现 query/insert/update/delete 全部 4 项；看板页改为通过 `ContentResolver.query` 读取并用 ContentObserver 在数据变更时刷新；写入仍走 Repository 并 notifyChange。」
- **解决的问题**：满足"加入 ContentProvider，实现≥2 项数据访问"，并让 CP 在应用流程里真正被使用（便于视频演示）。
- **AI 生成结果与我的修改**：
  - DAO 增加 `getAllCursor()/getByIdCursor()` 返回 `Cursor`（Room 支持），CP 直接返回 Cursor；`update/delete` 改返回 `int`（受影响行数）。
  - Provider 用 `UriMatcher` 区分列表/单条 URI，`getType` 返回标准 MIME。
  - Repository 事件写入后 `notifyChange(CONTENT_URI_EVENTS)`；看板页用 `ContentObserver` 自动重新查询。
  - **运行时验证**：`adb shell content query / insert / delete` 全部成功（插入后查到 → 删除后清空），无崩溃。

## 网络访问

（阶段 4 起补充：DeepSeek 接口、OkHttp 异步、Gson 解析、降级处理。）

### 第 4 轮（阶段 4，对应 commit `feat(network): DeepSeek 异步解析 + 回填 + 历史`）

- **提示词原文**：「实现网络访问：用 OkHttp 异步调用 DeepSeek 的 `/chat/completions`（OpenAI 兼容），用 Gson 解析返回 JSON 为日程字段（标题/日期/时间/地点/提醒/needClarification），回填到确认页；不能在主线程；无 Key 或失败时降级手动填写；每次解析写入 ParseHistory。Prompt 要求模型只输出 JSON，并注入今天日期。设置页配 baseUrl/model/Key（Key 走 EncryptedSharedPreferences）。」
- **解决的问题**：满足"网络访问：异步请求+解析+显示，不在主线程"，并实现核心 AI 闭环与降级。
- **AI 生成结果与我的修改**：
  - OkHttp `enqueue` 异步（天然不在主线程）；超时 30/60s。
  - 解析先取 `choices[0].message.content`，再截取首个 `{...}` 用 Gson 解析——兼容模型偶尔加代码块/说明文字。
  - 字段读取用 `optStr/optInt/optBool` 容错（reminderMinutes 可能是数字或字符串）。
  - 无 Key / 失败 → 主线程 toast 并保留手动填写路径（演示零风险）。
  - 成功解析即写 ParseHistory（原句/JSON/可信度/时间），保存日程时标记 `isApplied=true`。
  - 默认 `https://api.deepseek.com` + `/chat/completions`，DeepSeek 两种 base 写法都兼容。

## 通知 Notification

（阶段 5 起补充。）

### 第 5 轮（阶段 5，对应 commit `feat(notify): AlarmManager 到点提醒 + 通知渠道`）

- **提示词原文**：「实现通知：到点用 Notification 提醒，点击进详情；用 AlarmManager 在 `startTime-reminderMinutes` 定时，App 退出后仍可提醒；保存设置提醒、更新重设、删除取消；加"测试通知(5秒)"菜单便于演示；处理 Android13+ 的 POST_NOTIFICATIONS 运行时权限与 API26+ 的 NotificationChannel。」
- **解决的问题**：满足"加入 Notification 通知功能，结合项目场景"。
- **AI 生成结果与我的修改**：
  - NotificationChannel（IMPORTANCE_HIGH）在首次发通知时创建；minSdk 24 故用 `SDK_INT>=O` 兼容。
  - AlarmManager 用 `setAndAllowWhileIdle`（无需 SCHEDULE_EXACT_ALARM 权限，Doze 也可唤醒）——如实记录：精确闹钟需额外 SCHEDULE_EXACT_ALARM，本作业未启用，属已知取舍。
  - Receiver 读取日程放在后台线程（Room 不能主线程）；通知 PendingIntent 跳详情页（测试通知跳首页）。
  - 保存/更新/删除时设置/重设/取消提醒（用 eventId 作 requestCode 区分）。
  - Android 13+ 在 MainActivity 请求 POST_NOTIFICATIONS。
  - **验证**：临时导出 Receiver 用 `am broadcast` 触发，`AlarmReceiver→Notifier→通知` 成功发出（dumpsys 可见 `schedule_reminder` 通道、importance HIGH）；随后还原 `exported=false`。

## 调试错误

（开发过程中遇到编译/运行错误时，在此记录提示词与解决办法。）

### 第 2 轮（阶段 2a）：Calendar.set 没有 7 参数重载，编译失败

- **现象/提示词**：`./gradlew assembleDebug` 报错 `对于 set(int,int,int,int,int,int,int), 找不到合适的方法`（ScheduleListActivity 选日处）。
- **原因**：`java.util.Calendar` 没有 7 参数（含毫秒）的 `set` 重载，最长的只有 6 参数（到秒）。
- **解决办法**：改为逐字段 `c.set(Calendar.YEAR, year)`… 并把 `Calendar.MILLISECOND` 单独置 0，确保"当天 0 点"毫秒归零（否则按日区间筛选会出错）。修复后 BUILD SUCCESSFUL。
