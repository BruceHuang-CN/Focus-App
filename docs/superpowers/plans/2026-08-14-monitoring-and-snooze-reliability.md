# Focus Monitoring and Snooze Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让兼容模式稳定识别目标应用切换、普通回收后恢复守护、稍后提醒在进程重建后继续执行，并让实时模式只使用系统真实无障碍状态。

**Architecture:** 兼容检测改为“重叠滚动窗口 + 已发布事件游标”，避免 UsageEvents 延迟写入造成永久漏报；业务会话继续由 AppSessionCoordinator 统一串行化。稍后提醒到期时间存入 Room 会话表，进程内调度负责准点体验，WorkManager 和应用启动恢复负责兜底；无障碍状态通过现有 PermissionStatusProvider 同步回设置仓储。

**Tech Stack:** Kotlin、Android Foreground Service、UsageStatsManager、Room 4→5 Migration、Coroutines、WorkManager、Hilt、JUnit4、kotlinx-coroutines-test。

## Global Constraints

- 只修改 `C:\Users\6\Documents\Codex\2026-08-04\brucehuang-cn-focus-app-https-github\work\Focusapp-v01`。
- 不修改 DeepSeek/API、统计图表、养成系统和提醒选项。
- 用户离开原目标应用后，原会话稍后提醒必须取消；切换到另一目标应用必须建立新会话。
- 兼容模式维持 10 秒低频轮询，使用 30 秒重叠查询窗口，不引入持续高频任务。
- 不能承诺绕过 Android/ColorOS 的 force-stop；只恢复普通系统回收。
- 每项只运行聚焦测试；最终由用户在 Android Studio 编译并进行整体验收。

---

### Task 1: 兼容模式重叠检测游标

**Files:**
- Create: `app/src/main/java/com/example/focus_app/service/ForegroundObservationCursor.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AppDetectionService.kt`
- Test: `app/src/test/java/com/example/focus_app/service/ForegroundObservationCursorTest.kt`

**Interfaces:**
- Produces: `ForegroundObservation(packageName: String, timestamp: Long)`。
- Produces: `ForegroundObservationCursor.queryStart(now: Long): Long` 和 `takeIfNew(observation): ForegroundObservation?`。
- AppDetectionService 每次查询 `now - 30_000L`，只向 coordinator 发布游标接受的新观察。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun delayed_event_inside_overlap_is_published_once() {
    val cursor = ForegroundObservationCursor(overlapMillis = 30_000L)
    val event = ForegroundObservation("com.xingin.xhs", 95_000L)

    assertEquals(70_000L, cursor.queryStart(100_000L))
    assertEquals(event, cursor.takeIfNew(event))
    assertNull(cursor.takeIfNew(event))
}

@Test
fun newer_package_switch_is_published() {
    val cursor = ForegroundObservationCursor(30_000L)
    cursor.takeIfNew(ForegroundObservation("com.xingin.xhs", 95_000L))

    assertEquals(
        "com.ss.android.ugc.aweme",
        cursor.takeIfNew(ForegroundObservation("com.ss.android.ugc.aweme", 101_000L))?.packageName
    )
}
```

- [ ] **Step 2: 运行 RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.ForegroundObservationCursorTest" --no-daemon --no-configuration-cache`

Expected: `ForegroundObservationCursor` 未定义导致编译失败。

- [ ] **Step 3: 最小实现并接入服务**

```kotlin
internal data class ForegroundObservation(val packageName: String, val timestamp: Long)

internal class ForegroundObservationCursor(private val overlapMillis: Long) {
    private var lastPublished: ForegroundObservation? = null

    fun queryStart(now: Long): Long = (now - overlapMillis).coerceAtLeast(0L)

    fun takeIfNew(observation: ForegroundObservation?): ForegroundObservation? {
        observation ?: return null
        val previous = lastPublished
        if (previous != null && observation.timestamp < previous.timestamp) return null
        if (observation == previous) return null
        lastPublished = observation
        return observation
    }
}
```

AppDetectionService 删除不断前移的 `queryStartedAt`，在每轮使用 `cursor.queryStart(now)` 查询，并只发布 `cursor.takeIfNew(...)` 的结果。

- [ ] **Step 4: 运行 GREEN**

Run: 与 Step 2 相同。

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add app/src/main/java/com/example/focus_app/service/ForegroundObservationCursor.kt app/src/main/java/com/example/focus_app/service/AppDetectionService.kt app/src/test/java/com/example/focus_app/service/ForegroundObservationCursorTest.kt
git commit -m "fix: avoid missing compatibility app switches"
```

### Task 2: 兼容服务普通回收恢复

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/service/AppDetectionService.kt`
- Modify: `app/src/test/java/com/example/focus_app/service/AppDetectionServicePolicyTest.kt`

**Interfaces:**
- Produces: `compatibilityServiceStartMode(): Int`，固定返回 `Service.START_STICKY`。
- Existing `shouldRunCompatibilityMonitoring(mode, guardianEnabled)` 仍决定服务恢复后是否继续工作。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun compatibility_service_requests_restart_after_normal_process_reclaim() {
    assertEquals(Service.START_STICKY, compatibilityServiceStartMode())
}
```

- [ ] **Step 2: 运行 RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.AppDetectionServicePolicyTest" --no-daemon --no-configuration-cache`

Expected: `compatibilityServiceStartMode` 未定义。

- [ ] **Step 3: 最小实现**

```kotlin
internal fun compatibilityServiceStartMode(): Int = Service.START_STICKY
```

`onStartCommand` 返回该函数；现有 settings flow 会在模式不匹配或守护关闭时调用 `stopSelf`，因此粘性重建不会错误开启守护。

- [ ] **Step 4: 运行 GREEN 并提交**

Run: 与 Step 2 相同。

```powershell
git add app/src/main/java/com/example/focus_app/service/AppDetectionService.kt app/src/test/java/com/example/focus_app/service/AppDetectionServicePolicyTest.kt
git commit -m "fix: restore compatibility monitoring after reclaim"
```

### Task 3: 持久化并恢复稍后提醒

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/migration/Migration4To5.kt`
- Modify: `app/src/main/java/com/example/focus_app/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/local/entity/AppUsageSessionEntity.kt`
- Modify: `app/src/main/java/com/example/focus_app/domain/model/AppUsageSession.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/local/dao/AppUsageSessionDao.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/AppSessionRepository.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpReminderExecutor.kt`
- Create: `app/src/main/java/com/example/focus_app/service/PendingFollowUpRestorer.kt`
- Modify: `app/src/main/java/com/example/focus_app/FocusApp.kt`
- Test: `app/src/test/java/com/example/focus_app/service/PendingFollowUpRestorerTest.kt`
- Test: `app/src/androidTest/java/com/example/focus_app/data/local/Migration4To5Test.kt`

**Interfaces:**
- `AppUsageSession.snoozeUntil: Long?` 与 Entity 同步。
- `AppSessionRepository.setSnoozeUntil(sessionId: Long, snoozeUntil: Long?)`。
- `AppSessionRepository.pendingSnoozes(): List<AppUsageSession>` 只返回开放且到期时间非空的会话。
- `PendingFollowUpRestorer.restore()` 以 `maxOf(0, snoozeUntil - now)` 重新调用 `FollowUpScheduler.schedule`。

- [ ] **Step 1: 写 Restorer 失败测试**

```kotlin
@Test
fun restore_reschedules_open_snooze_using_remaining_delay() = runTest {
    val scheduler = RecordingFollowUpScheduler()
    val restorer = PendingFollowUpRestorer(
        repository = FakeRepository(pending = listOf(session(id = 7L, snoozeUntil = 70_000L))),
        scheduler = scheduler,
        nowMillis = { 40_000L }
    )

    restorer.restore()

    assertEquals(listOf(7L to 30_000L), scheduler.scheduled)
}
```

- [ ] **Step 2: 运行 RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.PendingFollowUpRestorerTest" --no-daemon --no-configuration-cache`

Expected: 新字段、仓储接口和 Restorer 未定义。

- [ ] **Step 3: 增加 Room 4→5 字段与接口**

```kotlin
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `app_usage_sessions` ADD COLUMN `snoozeUntil` INTEGER")
    }
}
```

DAO 增加：

```kotlin
@Query("UPDATE app_usage_sessions SET snoozeUntil = :value WHERE id = :sessionId AND endedAt IS NULL")
suspend fun setSnoozeUntil(sessionId: Long, value: Long?): Int

@Query("SELECT * FROM app_usage_sessions WHERE endedAt IS NULL AND snoozeUntil IS NOT NULL")
suspend fun pendingSnoozes(): List<AppUsageSessionEntity>
```

关闭会话时同时 `SET endedAt = :endedAt, snoozeUntil = NULL`。

- [ ] **Step 4: 接入 snooze、执行清理与启动恢复**

ReminderViewModel 在调度前计算并保存 `snoozeUntil = System.currentTimeMillis() + safeMinutes * 60_000L`。FollowUpReminderExecutor 对 SHOW/SKIP 清空 `snoozeUntil`，RETRY 保留。FocusApp 注入 PendingFollowUpRestorer，并在 applicationScope 启动一次 `restore()`。

- [ ] **Step 5: 增加迁移测试**

```kotlin
@Test
fun migrate4To5_preserves_sessions_and_adds_nullable_snooze_until() {
    helper.createDatabase(TEST_DB, 4).apply {
        execSQL("INSERT INTO app_usage_sessions (id, packageName, appName, startedAt, endedAt, taskId, remindedAt, userAction, toneKey) VALUES (1, 'pkg', 'App', 10, NULL, NULL, NULL, NULL, 'gentle')")
        close()
    }
    val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
    db.query("SELECT snoozeUntil FROM app_usage_sessions WHERE id = 1").use {
        assertTrue(it.moveToFirst())
        assertTrue(it.isNull(0))
    }
}
```

- [ ] **Step 6: 运行聚焦 GREEN 并提交**

Run JVM: 与 Step 2 相同。

Run Android test compile only: `gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache`

```powershell
git add app/src/main app/src/test/java/com/example/focus_app/service/PendingFollowUpRestorerTest.kt app/src/androidTest/java/com/example/focus_app/data/local/Migration4To5Test.kt
git commit -m "fix: restore persisted snooze reminders"
```

### Task 4: 同步系统真实无障碍状态

**Files:**
- Create: `app/src/main/java/com/example/focus_app/domain/usecase/SyncAccessibilityStateUseCase.kt`
- Modify: `app/src/main/java/com/example/focus_app/MainActivity.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/usecase/SyncAccessibilityStateUseCaseTest.kt`

**Interfaces:**
- Consumes: `PermissionStatusProvider.accessibilityEnabled()`。
- Produces: `suspend operator fun invoke()`，仅在真实状态与保存状态不同时更新 `AppSettings.enableAccessibility`。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun sync_replaces_stale_enabled_flag_with_system_state() = runTest {
    val repository = fakeSettingsRepository(enableAccessibility = true)
    val useCase = SyncAccessibilityStateUseCase(repository, permissionProvider(enabled = false))

    useCase()

    assertFalse(repository.getSettings().enableAccessibility)
}
```

- [ ] **Step 2: 运行 RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.usecase.SyncAccessibilityStateUseCaseTest" --no-daemon --no-configuration-cache`

Expected: UseCase 未定义。

- [ ] **Step 3: 最小实现并在 MainActivity.onResume 调用**

```kotlin
class SyncAccessibilityStateUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val permissionStatusProvider: PermissionStatusProvider
) {
    suspend operator fun invoke() {
        val actual = permissionStatusProvider.accessibilityEnabled()
        settingsRepository.update { current ->
            if (current.enableAccessibility == actual) current
            else current.copy(enableAccessibility = actual)
        }
    }
}
```

MainActivity 注入 use case，并在 `onResume()` 的 lifecycleScope 中调用。

- [ ] **Step 4: 运行 GREEN 并提交**

Run: 与 Step 2 相同。

```powershell
git add app/src/main/java/com/example/focus_app/domain/usecase/SyncAccessibilityStateUseCase.kt app/src/main/java/com/example/focus_app/MainActivity.kt app/src/test/java/com/example/focus_app/domain/usecase/SyncAccessibilityStateUseCaseTest.kt
git commit -m "fix: sync actual accessibility status"
```

### Task 5: 最终小范围检查与开发日志

**Files:**
- Modify: `docs/DEVELOPMENT_LOG.md`

**Interfaces:**
- 记录四项根因、代码提交和用户 Android Studio 验收步骤，不声称已完成未执行的手机验证。

- [ ] **Step 1: 运行聚焦组合测试**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.ForegroundObservationCursorTest" --tests "com.example.focus_app.service.AppDetectionServicePolicyTest" --tests "com.example.focus_app.service.PendingFollowUpRestorerTest" --tests "com.example.focus_app.domain.usecase.SyncAccessibilityStateUseCaseTest" --no-daemon --no-configuration-cache`

- [ ] **Step 2: 静态检查**

Run: `git diff --check`

Run: `git status --short`

- [ ] **Step 3: 更新日志并提交**

记录 Android Studio 验收：兼容模式 A→B→桌面、1 分钟留在目标、1 分钟中途离开、普通回收后重启、实时模式无障碍开/关各一次。

```powershell
git add docs/DEVELOPMENT_LOG.md
git commit -m "docs: record monitoring reliability repairs"
```
