# Focus Snooze Alarm and Home Quota Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 ColorOS 冻结 Focus 进程时以一次性系统唤醒恢复再次提醒，并在首页守护卡片显示当前滚动窗口的提醒额度。

**Architecture:** 保留 Room、进程内协程和 WorkManager，在 `HybridFollowUpScheduler` 中增加 `AlarmManager` 一次性唤醒。非导出的广播接收器只负责唤醒后调用现有 `FollowUpReminderExecutor`，所有业务门控和 `claimSnooze()` 去重保持单一来源；首页复用 `countShownRemindersSince()` 展示实际滚动额度。

**Tech Stack:** Kotlin、Android AlarmManager、BroadcastReceiver、Hilt、Coroutines、WorkManager、Room、Jetpack Compose、JUnit4、kotlinx-coroutines-test。

**Spec:** `docs/superpowers/specs/2026-08-14-snooze-alarm-and-home-quota-design.md`

## Global Constraints

- 只修改 `C:\Users\6\Documents\Codex\2026-08-04\brucehuang-cn-focus-app-https-github\work\Focusapp-v01`；不创建或切换其他工作树。
- 保留并忽略用户现有 `.idea/compiler.xml` 与 `.idea/misc.xml` 修改。
- 不修改 DeepSeek 请求、提示词、AI 缓存和提醒弹窗视觉样式。
- 系统调度不得展示系统闹钟界面、闹钟图标或播放系统闹铃；禁止使用 `setAlarmClock()`。
- Android 11 及以下使用精确 idle 唤醒；Android 12 及以上无精确能力时必须降级，不能崩溃或强制弹出权限页。
- 离开目标 App、关闭守护或替换稍后时间必须取消三条调度路径。
- 1 分钟验收范围为 1～3 分钟，5 分钟验收范围为 5～8 分钟；真正 force-stop 不声明可恢复。
- 每项先运行聚焦测试；最终只执行一次完整 JVM 回归和一次 Debug APK 构建。

---

### Task 1: 一次性系统唤醒组件

**Files:**
- Create: `app/src/main/java/com/example/focus_app/service/FollowUpAlarmScheduler.kt`
- Create: `app/src/main/java/com/example/focus_app/service/FollowUpAlarmReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/example/focus_app/service/FollowUpAlarmPolicyTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/FollowUpAlarmHandlerTest.kt`

**Interfaces:**
- Consumes: `FollowUpExecutor.execute(sessionId: Long): FollowUpDecision` 与现有 `FollowUpScheduler`。
- Produces: `FollowUpAlarmScheduler.schedule(sessionId: Long, delayMillis: Long, retryAttempt: Int = 0)`、`cancel(sessionId: Long)`。
- Produces: `selectFollowUpAlarmMode(sdkInt: Int, canScheduleExact: Boolean): FollowUpAlarmMode`。
- Produces: `FollowUpAlarmHandler.handle(sessionId: Long)`，供广播接收器和 JVM 测试调用。

- [ ] **Step 1: 写 Alarm 模式选择失败测试**

```kotlin
class FollowUpAlarmPolicyTest {
    @Test fun android_11_uses_exact_idle_alarm() {
        assertEquals(
            FollowUpAlarmMode.EXACT_ALLOW_IDLE,
            selectFollowUpAlarmMode(sdkInt = 30, canScheduleExact = false)
        )
    }

    @Test fun android_12_without_exact_access_uses_inexact_idle_alarm() {
        assertEquals(
            FollowUpAlarmMode.ALLOW_IDLE,
            selectFollowUpAlarmMode(sdkInt = 31, canScheduleExact = false)
        )
    }

    @Test fun android_12_with_exact_access_uses_exact_idle_alarm() {
        assertEquals(
            FollowUpAlarmMode.EXACT_ALLOW_IDLE,
            selectFollowUpAlarmMode(sdkInt = 31, canScheduleExact = true)
        )
    }

    @Test fun alarm_identity_is_stable_and_session_scoped() {
        assertEquals("focus://follow-up/7", followUpAlarmData(7L))
        assertEquals("focus://follow-up/8", followUpAlarmData(8L))
    }
}
```

- [ ] **Step 2: 写 Alarm 到期处理失败测试**

```kotlin
@Test fun show_cancels_remaining_paths() = runTest {
    val scheduler = RecordingFollowUpScheduler()
    val alarm = RecordingAlarmScheduler()
    val handler = FollowUpAlarmHandler(
        executor = FixedAlarmExecutor(FollowUpDecision.SHOW),
        scheduler = scheduler,
        alarmScheduler = alarm
    )

    handler.handle(7L)

    assertEquals(listOf(7L), scheduler.cancelled)
    assertTrue(scheduler.scheduled.isEmpty())
}

@Test fun retry_rearms_system_alarm_with_incremented_attempt() = runTest {
    val scheduler = RecordingFollowUpScheduler()
    val alarm = RecordingAlarmScheduler()
    val handler = FollowUpAlarmHandler(
        executor = FixedAlarmExecutor(FollowUpDecision.RETRY),
        scheduler = scheduler,
        alarmScheduler = alarm
    )

    handler.handle(7L)

    assertEquals(
        listOf(Triple(7L, HybridFollowUpScheduler.RETRY_INTERVAL_MS, 1)),
        alarm.scheduled
    )
}

@Test fun invalid_session_id_does_not_execute_or_schedule() = runTest {
    val executor = FixedAlarmExecutor(FollowUpDecision.SHOW)
    val scheduler = RecordingFollowUpScheduler()
    val alarm = RecordingAlarmScheduler()

    FollowUpAlarmHandler(executor, scheduler, alarm).handle(0L)

    assertTrue(executor.executed.isEmpty())
    assertTrue(scheduler.cancelled.isEmpty())
    assertTrue(alarm.scheduled.isEmpty())
}

private class RecordingAlarmScheduler : FollowUpAlarmScheduler {
    val scheduled = mutableListOf<Triple<Long, Long, Int>>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(sessionId: Long, delayMillis: Long, retryAttempt: Int) {
        scheduled += Triple(sessionId, delayMillis, retryAttempt)
    }
    override fun cancel(sessionId: Long) { cancelled += sessionId }
}

private class RecordingFollowUpScheduler : FollowUpScheduler {
    val scheduled = mutableListOf<Pair<Long, Long>>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(sessionId: Long, delayMillis: Long) {
        scheduled += sessionId to delayMillis
    }
    override fun cancel(sessionId: Long) { cancelled += sessionId }
}

private class FixedAlarmExecutor(private val decision: FollowUpDecision) : FollowUpExecutor {
    val executed = mutableListOf<Long>()
    override suspend fun execute(sessionId: Long): FollowUpDecision {
        executed += sessionId
        return decision
    }
}
```

- [ ] **Step 3: 运行 RED**

Run:

```powershell
$env:GRADLE_USER_HOME='C:\Users\6\.gradle'
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.FollowUpAlarmPolicyTest" --tests "com.example.focus_app.service.FollowUpAlarmHandlerTest" --no-daemon --no-configuration-cache
```

Expected: 新类型和处理器尚未定义，测试编译失败；不得同时出现旧测试失败。

- [ ] **Step 4: 实现 Alarm 调度接口与 Android 实现**

核心结构：

```kotlin
interface FollowUpAlarmScheduler {
    fun schedule(sessionId: Long, delayMillis: Long, retryAttempt: Int = 0)
    fun cancel(sessionId: Long)
}

internal enum class FollowUpAlarmMode { EXACT_ALLOW_IDLE, ALLOW_IDLE }

internal fun selectFollowUpAlarmMode(
    sdkInt: Int,
    canScheduleExact: Boolean
): FollowUpAlarmMode = if (sdkInt <= Build.VERSION_CODES.R || canScheduleExact) {
    FollowUpAlarmMode.EXACT_ALLOW_IDLE
} else {
    FollowUpAlarmMode.ALLOW_IDLE
}

internal fun followUpAlarmData(sessionId: Long): String = "focus://follow-up/$sessionId"
```

`AndroidFollowUpAlarmScheduler` 使用 `ELAPSED_REALTIME_WAKEUP` 与
`SystemClock.elapsedRealtime() + delayMillis.coerceAtLeast(0L)`。PendingIntent 使用固定 action、
`focus://follow-up/<sessionId>` data 和 `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`；相同 session 再次 schedule
必须替换旧闹钟。API 31+ 在调用精确 API 前检查 `canScheduleExactAlarms()`，精确调用遇到
`SecurityException` 时立即降级为 `setAndAllowWhileIdle()`。

- [ ] **Step 5: 实现非导出 Receiver 与可测 Handler**

```kotlin
class FollowUpAlarmHandler @Inject constructor(
    private val executor: FollowUpExecutor,
    private val scheduler: FollowUpScheduler,
    private val alarmScheduler: FollowUpAlarmScheduler
) {
    suspend fun handle(sessionId: Long, retryAttempt: Int = 0) {
        if (sessionId <= 0L) return
        when (executor.execute(sessionId)) {
            FollowUpDecision.RETRY -> if (retryAttempt < HybridFollowUpScheduler.MAX_RETRY_ATTEMPTS) {
                alarmScheduler.schedule(
                    sessionId,
                    HybridFollowUpScheduler.RETRY_INTERVAL_MS,
                    retryAttempt + 1
                )
            }
            FollowUpDecision.SHOW,
            FollowUpDecision.SKIP -> scheduler.cancel(sessionId)
        }
    }
}
```

`MAX_RETRY_ATTEMPTS` 从 private 改为 `internal`，供处理器复用同一个 20 次上限。Receiver 从 Intent
读取 `retryAttempt`，并使用 `@AndroidEntryPoint`、`goAsync()` 和注入的应用级 `CoroutineScope`；协程必须在
`finally` 中调用 `PendingResult.finish()`，异常只记录 session ID 和异常类型，不输出 AI 文案或配置。

Manifest 注册：

```xml
<receiver
    android:name=".service.FollowUpAlarmReceiver"
    android:exported="false" />
```

- [ ] **Step 6: 运行 GREEN 与 Android 源码编译**

Run: 与 Step 3 相同。

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --no-configuration-cache
```

Expected: 两个 JVM 测试类 PASS，Debug Kotlin 编译成功。

- [ ] **Step 7: 提交系统唤醒组件**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/example/focus_app/service/FollowUpAlarmScheduler.kt app/src/main/java/com/example/focus_app/service/FollowUpAlarmReceiver.kt app/src/test/java/com/example/focus_app/service/FollowUpAlarmPolicyTest.kt app/src/test/java/com/example/focus_app/service/FollowUpAlarmHandlerTest.kt
git commit -m "feat: add snooze wakeup alarm"
```

### Task 2: 接入三路调度与取消

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpScheduler.kt`
- Modify: `app/src/main/java/com/example/focus_app/di/FollowUpReminderWorkModule.kt`
- Modify: `app/src/test/java/com/example/focus_app/service/HybridFollowUpSchedulerTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `FollowUpAlarmScheduler`。
- Produces: `HybridFollowUpScheduler` 同时管理 coroutine、WorkManager、AlarmManager。

- [ ] **Step 1: 扩展失败测试，要求 schedule/cancel 覆盖 Alarm**

```kotlin
@Test fun schedule_also_registers_system_wakeup() = runTest {
    val alarm = RecordingAlarmScheduler()
    val scheduler = HybridFollowUpScheduler(
        RecordingExecutor { FollowUpDecision.SHOW },
        RecordingWorkScheduler(),
        alarm,
        backgroundScope
    )

    scheduler.schedule(100L, 60_000L)

    assertEquals(listOf(Triple(100L, 60_000L, 0)), alarm.scheduled)
}

@Test fun cancel_removes_work_and_system_wakeup() = runTest {
    val work = RecordingWorkScheduler()
    val alarm = RecordingAlarmScheduler()
    val scheduler = HybridFollowUpScheduler(
        RecordingExecutor { FollowUpDecision.SHOW }, work, alarm, backgroundScope
    )

    scheduler.schedule(100L, 60_000L)
    scheduler.cancel(100L)

    assertEquals(listOf(100L), work.cancelledSessionIds)
    assertEquals(listOf(100L), alarm.cancelled)
}

@Test fun in_process_retry_rearms_alarm_with_incremented_attempt() = runTest {
    val decisions = ArrayDeque(listOf(FollowUpDecision.RETRY, FollowUpDecision.SHOW))
    val alarm = RecordingAlarmScheduler()
    val scheduler = HybridFollowUpScheduler(
        RecordingExecutor { decisions.removeFirst() },
        RecordingWorkScheduler(),
        alarm,
        backgroundScope
    )

    scheduler.schedule(100L, 60_000L)
    advanceTimeBy(60_000L)
    runCurrent()

    assertEquals(
        Triple(100L, HybridFollowUpScheduler.RETRY_INTERVAL_MS, 1),
        alarm.scheduled.last()
    )
}
```

- [ ] **Step 2: 运行 RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.HybridFollowUpSchedulerTest" --no-daemon --no-configuration-cache
```

Expected: 构造函数尚无 Alarm 依赖或断言失败。

- [ ] **Step 3: 最小接入 Hybrid 调度器**

构造函数加入：

```kotlin
private val alarmScheduler: FollowUpAlarmScheduler
```

`schedule()` 在安排 coroutine 和 WorkManager 的同时调用：

```kotlin
alarmScheduler.schedule(sessionId, delayMillis)
```

`cancel()` 调用：

```kotlin
alarmScheduler.cancel(sessionId)
```

进程内协程到期准备执行前先取消同 session 的 Alarm，再取消 WorkManager；RETRY 继续使用现有
`scheduleOnce()` 处理进程内重试，同时以 `attempts + 1` 重新安排一次 Alarm，避免进程再次被冻结后
丢失重试，并与 Handler 共用 20 次上限。

在 `FollowUpReminderWorkModule` 绑定 `AndroidFollowUpAlarmScheduler` 到 `FollowUpAlarmScheduler`。

- [ ] **Step 4: 运行 GREEN 和相关回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.HybridFollowUpSchedulerTest" --tests "com.example.focus_app.service.FollowUpReminderExecutorTest" --tests "com.example.focus_app.service.PendingFollowUpRestorerTest" --no-daemon --no-configuration-cache
```

Expected: PASS；恢复器重新调度时同样产生系统唤醒。

- [ ] **Step 5: 提交三路调度接入**

```powershell
git add app/src/main/java/com/example/focus_app/service/FollowUpScheduler.kt app/src/main/java/com/example/focus_app/di/FollowUpReminderWorkModule.kt app/src/test/java/com/example/focus_app/service/HybridFollowUpSchedulerTest.kt
git commit -m "fix: wake frozen snooze reminders"
```

### Task 3: 首页滚动额度显示

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/home/HomeScreen.kt`
- Modify: `app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt`
- Create: `app/src/test/java/com/example/focus_app/ui/home/HomeQuotaLabelTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.getSettings()` 与 `AppSessionRepository.countShownRemindersSince(since)`。
- Produces: `HomeUiState.reminderWindowMinutes`、`windowReminderCount`、`windowReminderLimit`。
- Produces: `reminderQuotaLabel(minutes: Int, count: Int, limit: Int): String`。

- [ ] **Step 1: 写 ViewModel 与文案失败测试**

```kotlin
@Test fun current_window_quota_is_exposed_on_home() = runTest(dispatcher) {
    val fixture = fixture(windowMinutes = 30, windowLimit = 5, shownReminders = 2)

    val viewModel = fixture.homeViewModel()
    runCurrent()

    assertEquals(30, viewModel.uiState.value.reminderWindowMinutes)
    assertEquals(2, viewModel.uiState.value.windowReminderCount)
    assertEquals(5, viewModel.uiState.value.windowReminderLimit)
}

@Test fun quota_label_names_the_actual_rolling_window() {
    assertEquals(
        "本时段（30 分钟）已提醒 2/5 次",
        reminderQuotaLabel(minutes = 30, count = 2, limit = 5)
    )
}
```

扩展 `HomeSettingsDao` 接收 `reminderWindowMinutes` 与 `maxRemindersPerWindow`；扩展 `HomeSessions`
使 `countShownRemindersSince()` 返回 `shownReminders` 并记录传入的 since。

- [ ] **Step 2: 运行 RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.ui.home.HomeViewModelGuardianTest" --tests "com.example.focus_app.ui.home.HomeQuotaLabelTest" --no-daemon --no-configuration-cache
```

Expected: 新字段和文案函数未定义。

- [ ] **Step 3: 实现滚动额度状态**

`HomeUiState` 增加：

```kotlin
val reminderWindowMinutes: Int = 60,
val windowReminderCount: Int = 0,
val windowReminderLimit: Int = 3
```

`loadStats()` 读取一次设置，并计算：

```kotlin
val settings = settingsRepository.getSettings()
val windowStart = System.currentTimeMillis() - settings.reminderWindowMinutes * 60_000L
val windowReminderCount = appSessionRepository.countShownRemindersSince(windowStart)
```

把 settings 的窗口分钟数、上限和查询结果写入 state。`resetReminderQuota()` 完成 reset 后调用
`loadStats()`，保证首页归零；现有 ON_RESUME 继续刷新再次提醒后的数字。

- [ ] **Step 4: 在守护卡片显示实际额度**

```kotlin
internal fun reminderQuotaLabel(minutes: Int, count: Int, limit: Int): String =
    "本时段（$minutes 分钟）已提醒 $count/$limit 次"
```

`GuardianControlCard` 在守护状态文字后增加一行 `bodyMedium`，不得移除当前应用组和重置额度按钮；
Preview 使用 30、2、5 验证完整文案。

- [ ] **Step 5: 运行 GREEN 与首页相关回归**

Run: 与 Step 2 相同。

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.usecase.ResetReminderQuotaUseCaseTest" --tests "com.example.focus_app.domain.usecase.UpdateGuardianStateUseCaseTest" --no-daemon --no-configuration-cache
```

Expected: PASS；重置和守护开关行为不变。

- [ ] **Step 6: 提交首页额度显示**

```powershell
git add app/src/main/java/com/example/focus_app/ui/home/HomeViewModel.kt app/src/main/java/com/example/focus_app/ui/home/HomeScreen.kt app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt app/src/test/java/com/example/focus_app/ui/home/HomeQuotaLabelTest.kt
git commit -m "feat: show reminder quota on home"
```

### Task 4: 完整验证、干净安装与真机取证

**Files:**
- Modify: `docs/DEVELOPMENT_LOG.md`
- Reference: `docs/superpowers/specs/2026-08-14-snooze-alarm-and-home-quota-design.md`

**Interfaces:**
- Consumes: Tasks 1～3 的最终代码和 Debug APK。
- Produces: 自动化结果、安装包来源、Alarm/数据库/弹窗真机证据和明确的 force-stop 边界。

- [ ] **Step 1: 运行聚焦组合测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.FollowUpAlarmPolicyTest" --tests "com.example.focus_app.service.FollowUpAlarmHandlerTest" --tests "com.example.focus_app.service.HybridFollowUpSchedulerTest" --tests "com.example.focus_app.service.FollowUpReminderExecutorTest" --tests "com.example.focus_app.service.PendingFollowUpRestorerTest" --tests "com.example.focus_app.ui.home.HomeViewModelGuardianTest" --tests "com.example.focus_app.ui.home.HomeQuotaLabelTest" --no-daemon --no-configuration-cache
```

- [ ] **Step 2: 运行一次完整 JVM 回归与一次 APK 构建**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --no-configuration-cache
```

记录 suites、tests、failures、errors、skipped 与 `BUILD SUCCESSFUL`；失败时停止安装并回到根因调查。

- [ ] **Step 3: 静态与仓库检查**

```powershell
git diff --check
git status --short
git diff --name-only HEAD~3..HEAD
```

确认 `.idea`、Gradle 缓存、APK、数据库快照和 API Key 均未暂存。

- [ ] **Step 4: 完整覆盖安装到 RMX3350**

使用 Android SDK 的 adb，禁止 Apply Changes：

```powershell
& 'D:\Program\Android\SDK\platform-tools\adb.exe' install -r -t 'app\build\outputs\apk\debug\app-debug.apk'
```

安装后读取 `dumpsys package com.example.focus_app` 的 `lastUpdateTime`，并检查基础 APK dex 中包含
`FollowUpAlarmReceiver`；不得以 `code_cache/.overlay` 作为新代码来源。

- [ ] **Step 5: 执行 1 分钟冻结测试**

用户在目标 App 中触发提醒并选择 1 分钟；立即记录：

- `app_usage_sessions` 的 session ID 与 `snoozeUntil`；
- `androidx.work.workdb` 的 unique work；
- `dumpsys alarm` 中 `focus://follow-up/<sessionId>`；
- Focus 进程 cgroup 是否进入 frozen。

在 1～3 分钟内确认 Focus AI 弹窗；随后确认只更新一次 `remindedAt`、`snoozeUntil` 为空、首页额度
只增加一次。若未弹，保留原始 Alarm、进程、日志和数据库证据，不叠加第二个修补。

- [ ] **Step 6: 执行 5 分钟与取消测试**

- 留在同一目标 App：5～8 分钟内再次弹出。
- 到期前切换桌面或非目标 App：Alarm、WorkManager 和内存任务取消，首页额度不增加。
- 锁屏/解锁：允许少量额外延迟，解锁后由 RETRY 唤醒路径继续。
- 真正 force-stop：记录为系统边界，不声称应用能自行恢复。

- [ ] **Step 7: 更新开发日志并提交验证记录**

只有完成的证据才从「待实施」改为「已实现」；真机未执行的项目继续标记未验证。

```powershell
git add docs/DEVELOPMENT_LOG.md
git commit -m "docs: record snooze wakeup verification"
```

## 完成定义

- 自动化测试和 Debug APK 构建通过。
- 手机安装的是包含本轮提交的完整 APK，不依赖 Android Studio overlay。
- 1 分钟和 5 分钟至少各完成一次冻结场景再次提醒，且每次只弹一次、额度只增加一次。
- 首页文案与设置中的滚动窗口和上限一致。
- 离开目标 App 的取消场景通过。
- 开发日志清楚区分代码验证、设备验证和 force-stop 限制。
