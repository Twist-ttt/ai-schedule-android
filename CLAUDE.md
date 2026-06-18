# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

「AI日程转换助手」(AISchedule) — a final-year Android course project that turns one spoken sentence into a structured, editable, reminder-enabled calendar event via an LLM. Source is **Java** (the `*.gradle.kts` files are Kotlin DSL for build config only). Package: `com.qiu.aischedule`. Course context and grading rubric live in `docs/设计文档.md`.

## Build & verify

```bash
./gradlew :app:assembleDebug        # Windows: gradlew.bat :app:assembleDebug
./gradlew :app:installDebug         # install on connected emulator/device
adb shell am start -n com.qiu.aischedule/.ui.MainActivity
```

There is **no test suite** — the template test stubs were deleted (作业不要求单元测试). `assembleDebug` succeeding is the only build gate. This machine generally **cannot build locally**; verify via the gradlew commands above, and confirm XML resource references / Manifest registrations / Gradle dependency coordinates match (the prior phases caught several errors this way rather than at runtime).

Runtime smoke tests the author used: `adb content query --uri content://com.qiu.aischedule.provider/events`, and a temp-exported `AlarmReceiver` + `am broadcast` to exercise the notification chain (then restored `exported=false`).

## Architecture

Layered Java app under `com.qiu.aischedule`:

- `ui/` — Activities (`MainActivity`, `AiConfirmActivity`, `ScheduleListActivity`, `DetailActivity`, `HistoryActivity`, `SettingsActivity`) + `ui/adapter/`. Plain XML layouts, `ListAdapter` + `DiffUtil`. No Fragments, no Jetpack Compose.
- `data/repository/ScheduleRepository` — **the single data gateway**. UI never touches DAOs/DB/ContentProvider directly.
- `data/local/` — Room: `entity/` (EventRecord, ParseHistory, ApiConfig), `dao/`, `AppDatabase` (singleton, `version=1, exportSchema=false`).
- `provider/ScheduleProvider` — ContentProvider exposing the `events` table over a URI.
- `network/LlmClient` — OkHttp async call to an OpenAI-compatible `/chat/completions` endpoint (default DeepSeek).
- `notify/` — AlarmManager + Notification reminder chain.
- `security/SecretStore` — EncryptedSharedPreferences wrapper for the API key.
- `util/AppExecutors` — shared disk-IO (single thread) + main-thread executors.

### Threading contract (important — Room throws on main-thread access)

All writes go through `AppExecutors.diskIO()`. Methods suffixed **`*Sync`** (`getEventSync`, `insertEventSync`, `getByIdSync`, …) run on the calling thread and **must be called off the main thread** — the Repository Javadoc and callers enforce this. The LLM call, API-key read, and history write all happen inside `diskIO().execute(...)`. When a result is needed on the UI thread, post via `AppExecutors.mainThread()` or use the Repository's `Callback<T>` (a hand-rolled interface, intentionally **not** `java.util.function`).

### Two read paths for events — both are required by the rubric

1. **LiveData** — `EventDao.getAll()` / `observeById()` → Repository → `observe()` in Activities (detail page).
2. **ContentProvider** — `ScheduleListActivity` reads via `ContentResolver.query()` and refreshes through a `ContentObserver`.

These coexist on purpose. Every event write (insert/update/delete) calls `getContentResolver().notifyChange(CONTENT_URI_EVENTS, null)` so the ContentProvider-backed list auto-refreshes. DAOs provide both `LiveData<…>` returns and `Cursor` returns (`getAllCursor`/`getByIdCursor`) for the ContentProvider. `ScheduleProvider` itself runs on the caller's thread, so ContentResolver access from the UI is wrapped in `diskIO()`.

### API key handling

The key is **never stored in Room**. `SecretStore` (EncryptedSharedPreferences, AES256 + Keystore) holds the actual key; the `ApiConfig` table stores only `provider`/`baseUrl`/`modelName`/`keyAlias`. The plaintext key never goes to Logcat. `LlmClient.parse()` falls back to manual entry on missing key or network failure.

### Notification contract

`ReminderScheduler` schedules one alarm per event (requestCode = eventId, so each is independently cancelable) via `AlarmManager.setAndAllowWhileIdle` at `startTime - reminderMinutes`. `AlarmReceiver` → `Notifier` (channel `IMPORTANCE_HIGH`, built with `SDK_INT >= O` guard for minSdk 24). Save → schedule; update → reschedule; delete → cancel. Android 13+ requests `POST_NOTIFICATIONS` at runtime. Uses the inexact variant by design (no `SCHEDULE_EXACT_ALARM` permission) — see the "已知取舍" section of the design doc before changing this.

## Conventions

- **Singletons**: `AppDatabase`, `ScheduleRepository`, `LlmClient`, `AppExecutors` all use the same double-checked-locking pattern (`private static volatile INSTANCE` + `getInstance(Context)`). Match it for any new process-wide state.
- **Entity fields are `public`** (Room accesses them directly, no getters/setters) — consistent across all three entities.
- **Times are epoch millis** (`long`) everywhere; `DateUtils` is the only place that formats/parses them.
- **AI-generated code is the norm, but every generated block is cross-checked** against package name, view IDs, Manifest registration, dependency coordinates, and DB column names before commit. The `Calendar.set` 7-arg overload compile failure (now fixed) came from AI code; verify against the actual API, not the suggestion.

## AI-prompt journaling is a graded deliverable

The course requires logging **all** AI prompts and edits, worth 15% of the grade. Keep three places in sync on every change:

- `docs/prompts.md` — full prompt + modification log, one entry per round.
- `docs/CHANGELOG.md` — a three-part summary per commit: ① what was done ｜ ② prompts/edits (cross-ref `prompts.md`) ｜ ③ problems & fixes, plus the verification result.
- `parse_history` table — every live LLM call writes a row (`LlmClient` does this automatically); rows surface in `HistoryActivity`.

Each work increment is **commit + push + summary** in that order. See `docs/设计文档.md` §8 for the rubric → implementation mapping.
