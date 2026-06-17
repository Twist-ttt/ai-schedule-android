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

（阶段 3 起补充。）

## 网络访问

（阶段 4 起补充：DeepSeek 接口、OkHttp 异步、Gson 解析、降级处理。）

## 通知 Notification

（阶段 5 起补充。）

## 调试错误

（开发过程中遇到编译/运行错误时，在此记录提示词与解决办法。）

### 第 2 轮（阶段 2a）：Calendar.set 没有 7 参数重载，编译失败

- **现象/提示词**：`./gradlew assembleDebug` 报错 `对于 set(int,int,int,int,int,int,int), 找不到合适的方法`（ScheduleListActivity 选日处）。
- **原因**：`java.util.Calendar` 没有 7 参数（含毫秒）的 `set` 重载，最长的只有 6 参数（到秒）。
- **解决办法**：改为逐字段 `c.set(Calendar.YEAR, year)`… 并把 `Calendar.MILLISECOND` 单独置 0，确保"当天 0 点"毫秒归零（否则按日区间筛选会出错）。修复后 BUILD SUCCESSFUL。
