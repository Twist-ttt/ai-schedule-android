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
