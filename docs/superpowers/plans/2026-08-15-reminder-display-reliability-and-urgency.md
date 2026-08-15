# Focus 再次提醒可靠性与紧急弹窗实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 让实时模式下 1～60 分钟的再次提醒可靠执行，只在原目标应用仍处前台时显示，并以实际显示的 Focus 弹窗准确扣减额度，最后按剩余额度逐次放大弹窗。

**Architecture:** 保留协程、WorkManager、AlarmManager 三路调度和现有会话去重；为前台快照增加新鲜度，过期后查询 UsageEvents。Room 新增 reminder_display_events，ReminderActivity 在 onResume 时原子确认额度并记录实际显示，首页、Gate、AI 上下文和紧急程度统一读取该事件源。

**Tech Stack:** Kotlin、Android 11+、AlarmManager、UsageStatsManager、Room 6、Hilt、Coroutines、Jetpack Compose、JUnit4、AndroidX MigrationTestHelper、ADB。

**Spec:** docs/superpowers/specs/2026-08-15-reminder-display-reliability-and-urgency-design.md

## Global Constraints

- 只修改现有 Focusapp-v01 工作区和 feature/focus-v0.1-preview 分支，不创建新工作树。
- 保留并忽略用户的 .idea/compiler.xml 和 .idea/misc.xml 修改。
- 自定义稍后提醒仅接受整数 1～60；预设保持 1、5、10 分钟。
- 只使用 Focus ReminderActivity 和现有 AI 文案，不使用 setAlarmClock、系统闹钟界面或系统闹铃音。
- 到期时确认处于其他应用即终止旧提醒；前台未知进入 15 秒重试，最多 20 次。
- 仅 ReminderActivity 实际恢复并通过数据库确认后扣额度；仅通知不扣额度。
- 先完成基本提醒和额度一致性，再接入紧急弹窗视觉。
- 每个生产行为都先写失败测试并观察正确失败，再写最小实现。
- 完成声明必须包含完整 JVM 回归、Debug APK 构建以及 RMX3350 实时模式真机证据；兼容模式明确标记未验证。

---

### Task 1: 统一 1～60 分钟稍后提醒规则

**Files:**
- Create: app/src/main/java/com/example/focus_app/domain/reminder/SnoozeDurationPolicy.kt
- Modify: app/src/main/java/com/example/focus_app/data/followup/FollowUpReminderStore.kt
- Modify: app/src/main/java/com/example/focus_app/ui/reminder/ReminderActionOptions.kt
- Modify: app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt
- Modify: app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt
- Test: app/src/test/java/com/example/focus_app/domain/reminder/SnoozeDurationPolicyTest.kt
- Test: app/src/test/java/com/example/focus_app/ui/reminder/ReminderActionOptionsTest.kt
- Test: app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt

**Interfaces:**
- Produces: SnoozeDurationPolicy.MIN_MINUTES = 1、MAX_MINUTES = 60、isValid(minutes)、normalizeStored(minutes)。
- Consumes: ReminderViewModel.snooze(sessionId, minutes) 和 SharedPrefsFollowUpReminderStore。

- [ ] **Step 1: 写规则与界面解析失败测试**

~~~kotlin
@Test fun accepts_boundaries_and_rejects_out_of_range_values() {
    assertTrue(SnoozeDurationPolicy.isValid(1))
    assertTrue(SnoozeDurationPolicy.isValid(60))
    assertFalse(SnoozeDurationPolicy.isValid(0))
    assertFalse(SnoozeDurationPolicy.isValid(61))
    assertEquals(60, SnoozeDurationPolicy.normalizeStored(120))
}

@Test fun custom_snooze_accepts_only_one_to_60_minutes() {
    assertEquals(1, parseCustomSnoozeMinutes("1"))
    assertEquals(60, parseCustomSnoozeMinutes("60"))
    assertNull(parseCustomSnoozeMinutes("0"))
    assertNull(parseCustomSnoozeMinutes("61"))
    assertNull(parseCustomSnoozeMinutes("abc"))
}
~~~

- [ ] **Step 2: 写 ViewModel 拒绝非法时长的失败测试**

~~~kotlin
@Test fun snooze_rejects_more_than_60_minutes_without_persisting_or_scheduling() = runTest(dispatcher) {
    val fixture = fixture()
    fixture.viewModel.init(LAUNCH_DATA)
    fixture.viewModel.snooze(LAUNCH_DATA.sessionId, 61)
    advanceUntilIdle()
    assertEquals(null, fixture.repository.action)
    assertEquals(null, fixture.repository.snoozeUntil)
    assertEquals(null, fixture.scheduler.followUpSessionId)
}
~~~

- [ ] **Step 3: 运行 RED**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.reminder.SnoozeDurationPolicyTest" --tests "com.example.focus_app.ui.reminder.ReminderActionOptionsTest" --tests "com.example.focus_app.ui.reminder.ReminderViewModelTest" --offline --console=plain
~~~

Expected: 新策略尚不存在，61 分钟仍可能被压缩或接受，测试因目标行为缺失而失败。

- [ ] **Step 4: 写最小实现**

~~~kotlin
object SnoozeDurationPolicy {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 60
    fun isValid(minutes: Int): Boolean = minutes in MIN_MINUTES..MAX_MINUTES
    fun normalizeStored(minutes: Int): Int =
        minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
}
~~~

解析器使用 isValid；ViewModel 对非法值直接返回而不是静默压缩；SharedPreferences 读取旧值时使用 normalizeStored；弹窗标签改为“请输入 1-60 分钟”。

- [ ] **Step 5: 运行 GREEN 并提交**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.reminder.SnoozeDurationPolicyTest" --tests "com.example.focus_app.ui.reminder.ReminderActionOptionsTest" --tests "com.example.focus_app.ui.reminder.ReminderViewModelTest" --offline --console=plain
git add app/src/main/java/com/example/focus_app/domain/reminder/SnoozeDurationPolicy.kt app/src/main/java/com/example/focus_app/data/followup/FollowUpReminderStore.kt app/src/main/java/com/example/focus_app/ui/reminder/ReminderActionOptions.kt app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt app/src/test/java/com/example/focus_app/domain/reminder/SnoozeDurationPolicyTest.kt app/src/test/java/com/example/focus_app/ui/reminder/ReminderActionOptionsTest.kt app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt
git commit -m "fix: limit snooze reminders to sixty minutes"
~~~

### Task 2: 修复过期前台缓存与到期决策

**Files:**
- Modify: app/src/main/java/com/example/focus_app/service/FollowUpReminderEnvironment.kt
- Modify: app/src/main/java/com/example/focus_app/service/FollowUpReminderGate.kt
- Modify: app/src/main/java/com/example/focus_app/service/FollowUpReminderExecutor.kt
- Modify: app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt
- Test: app/src/test/java/com/example/focus_app/service/FollowUpForegroundSelectionTest.kt
- Test: app/src/test/java/com/example/focus_app/service/FollowUpReminderGateTest.kt
- Test: app/src/test/java/com/example/focus_app/service/FollowUpReminderExecutorTest.kt

**Interfaces:**
- Produces: sealed interface ForegroundSnapshot，包含 Confirmed(packageName, source) 与 Unknown。
- Produces: TimedForegroundPackage(packageName, observedAtElapsedRealtime)。
- Produces: selectForegroundSnapshot(cached, nowElapsedRealtime, maxCacheAgeMillis, usagePackage)。
- Consumes: FollowUpReminderGate 的前台判断和既有 RETRY/SKIP 调度语义。

- [ ] **Step 1: 写前台来源选择失败测试**

~~~kotlin
@Test fun fresh_realtime_snapshot_wins() {
    assertEquals(
        ForegroundSnapshot.Confirmed("target", ForegroundSource.REALTIME),
        selectForegroundSnapshot(
            cached = TimedForegroundPackage("target", 9_000L),
            nowElapsedRealtime = 10_000L,
            maxCacheAgeMillis = 3_000L,
            usagePackage = { "settings" }
        )
    )
}

@Test fun stale_realtime_snapshot_queries_usage_events() {
    assertEquals(
        ForegroundSnapshot.Confirmed("settings", ForegroundSource.USAGE_EVENTS),
        selectForegroundSnapshot(
            cached = TimedForegroundPackage("target", 1_000L),
            nowElapsedRealtime = 10_000L,
            maxCacheAgeMillis = 3_000L,
            usagePackage = { "settings" }
        )
    )
}

@Test fun stale_cache_and_missing_usage_event_is_unknown() {
    assertEquals(
        ForegroundSnapshot.Unknown,
        selectForegroundSnapshot(
            cached = TimedForegroundPackage("target", 1_000L),
            nowElapsedRealtime = 10_000L,
            maxCacheAgeMillis = 3_000L,
            usagePackage = { null }
        )
    )
}
~~~

- [ ] **Step 2: 写 Gate 与 Executor 失败测试**

~~~kotlin
@Test fun unknown_foreground_retries_without_claiming_snooze() = runTest {
    val fixture = fixture(environment = FakeEnvironment(ForegroundSnapshot.Unknown))
    assertEquals(FollowUpDecision.RETRY, fixture.executor.execute(session.id))
    assertEquals(20_000L, fixture.repository.session(session.id)?.snoozeUntil)
    assertTrue(fixture.launcher.shown.isEmpty())
}

@Test fun confirmed_other_app_skips_and_clears_snooze() = runTest {
    val fixture = fixture(
        environment = FakeEnvironment(
            ForegroundSnapshot.Confirmed("com.android.settings", ForegroundSource.USAGE_EVENTS)
        )
    )
    assertEquals(FollowUpDecision.SKIP, fixture.executor.execute(session.id))
    assertNull(fixture.repository.session(session.id)?.snoozeUntil)
}
~~~

- [ ] **Step 3: 运行 RED**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.FollowUpForegroundSelectionTest" --tests "com.example.focus_app.service.FollowUpReminderGateTest" --tests "com.example.focus_app.service.FollowUpReminderExecutorTest" --offline --console=plain
~~~

Expected: 旧接口只有 String?，未知状态仍返回 SHOW。

- [ ] **Step 4: 实现带时间戳的快照和新 Gate**

~~~kotlin
sealed interface ForegroundSnapshot {
    data class Confirmed(
        val packageName: String,
        val source: ForegroundSource
    ) : ForegroundSnapshot
    data object Unknown : ForegroundSnapshot
}

internal const val REALTIME_CACHE_MAX_AGE_MS = 3_000L
~~~

RealtimeForegroundProvider 保存 elapsedRealtime；缓存新鲜才使用，否则查询 UsageEvents。Gate 对 Unknown 返回 RETRY，对 Confirmed 且包名不同返回 SKIP。Executor 的 SKIP 路径继续清空 snoozeUntil，RETRY 保留。

- [ ] **Step 5: 运行 GREEN 并提交**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.FollowUpForegroundSelectionTest" --tests "com.example.focus_app.service.FollowUpReminderGateTest" --tests "com.example.focus_app.service.FollowUpReminderExecutorTest" --offline --console=plain
git add app/src/main/java/com/example/focus_app/service/FollowUpReminderEnvironment.kt app/src/main/java/com/example/focus_app/service/FollowUpReminderGate.kt app/src/main/java/com/example/focus_app/service/FollowUpReminderExecutor.kt app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt app/src/test/java/com/example/focus_app/service/FollowUpForegroundSelectionTest.kt app/src/test/java/com/example/focus_app/service/FollowUpReminderGateTest.kt app/src/test/java/com/example/focus_app/service/FollowUpReminderExecutorTest.kt
git commit -m "fix: reject stale snooze foreground state"
~~~

### Task 3: 建立实际弹窗显示事件与 Room 5→6 迁移

**Files:**
- Create: app/src/main/java/com/example/focus_app/data/local/entity/ReminderDisplayEventEntity.kt
- Create: app/src/main/java/com/example/focus_app/data/local/dao/ReminderDisplayEventDao.kt
- Create: app/src/main/java/com/example/focus_app/data/repository/ReminderDisplayRepository.kt
- Create: app/src/main/java/com/example/focus_app/data/local/migration/Migration5To6.kt
- Modify: app/src/main/java/com/example/focus_app/data/local/AppDatabase.kt
- Modify: app/src/main/java/com/example/focus_app/di/DatabaseModule.kt
- Modify: app/src/main/java/com/example/focus_app/data/local/dao/AppUsageSessionDao.kt
- Test: app/src/androidTest/java/com/example/focus_app/data/local/Migration5To6Test.kt
- Test: app/src/androidTest/java/com/example/focus_app/data/repository/ReminderDisplayRepositoryTest.kt

**Interfaces:**
- Produces: ReminderDisplayKind.INITIAL / FOLLOW_UP。
- Produces: ReminderDisplayResult.Displayed(windowCount) / QuotaExceeded。
- Produces: ReminderDisplayRepository.recordDisplay(attemptId, sessionId, displayedAt, kind, windowStart, limit)。
- Produces: countSince、timesSince、resetSince。

- [ ] **Step 1: 写迁移失败测试**

~~~kotlin
@Test fun migration_converts_each_legacy_reminded_session_once() {
    helper.createDatabase("migration-reminder-display", 5).apply {
        execSQL("INSERT INTO app_usage_sessions (id,packageName,appName,startedAt,endedAt,taskId,remindedAt,userAction,toneKey,snoozeUntil) VALUES (1,'target','Target',1000,NULL,NULL,2000,NULL,'gentle',NULL)")
        execSQL("INSERT INTO app_usage_sessions (id,packageName,appName,startedAt,endedAt,taskId,remindedAt,userAction,toneKey,snoozeUntil) VALUES (2,'other','Other',1000,NULL,NULL,NULL,NULL,'gentle',NULL)")
        close()
    }
    helper.runMigrationsAndValidate(
        "migration-reminder-display", 6, true, MIGRATION_5_6
    ).use { db ->
        db.query("SELECT attemptId,sessionId,displayedAt,kind FROM reminder_display_events").use {
            assertTrue(it.moveToFirst())
            assertEquals("legacy-1", it.getString(0))
            assertEquals(1L, it.getLong(1))
            assertEquals(2000L, it.getLong(2))
            assertEquals("initial", it.getString(3))
            assertFalse(it.moveToNext())
        }
    }
}
~~~

- [ ] **Step 2: 写真实 Room 原子确认失败测试**

~~~kotlin
@Test fun same_attempt_is_idempotent_and_quota_never_exceeds_limit() = runTest {
    val first = repository.recordDisplay("a", 1L, 2_000L, ReminderDisplayKind.INITIAL, 0L, 1)
    val duplicate = repository.recordDisplay("a", 1L, 2_000L, ReminderDisplayKind.INITIAL, 0L, 1)
    val blocked = repository.recordDisplay("b", 1L, 3_000L, ReminderDisplayKind.FOLLOW_UP, 0L, 1)
    assertEquals(ReminderDisplayResult.Displayed(1), first)
    assertEquals(ReminderDisplayResult.Displayed(1), duplicate)
    assertEquals(ReminderDisplayResult.QuotaExceeded, blocked)
    assertEquals(1, repository.countSince(0L))
}
~~~

- [ ] **Step 3: 运行 RED**

~~~powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.data.local.Migration5To6Test,com.example.focus_app.data.repository.ReminderDisplayRepositoryTest --console=plain
~~~

Expected: 新表、迁移和 Repository 尚不存在，androidTest 编译失败。

- [ ] **Step 4: 实现实体、DAO、事务 Repository 与迁移**

~~~kotlin
@Entity(
    tableName = "reminder_display_events",
    indices = [
        Index(value = ["attemptId"], unique = true),
        Index(value = ["displayedAt"])
    ]
)
data class ReminderDisplayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: String,
    val sessionId: Long,
    val displayedAt: Long,
    val kind: String
)
~~~

MIGRATION_5_6 创建表和两个索引，再执行：

~~~sql
INSERT INTO reminder_display_events (attemptId, sessionId, displayedAt, kind)
SELECT 'legacy-' || id, id, remindedAt, 'initial'
FROM app_usage_sessions
WHERE remindedAt IS NOT NULL
~~~

RoomReminderDisplayRepository 使用 AppDatabase.withTransaction：先查 attemptId；再检查 countSince；使用 INSERT OR IGNORE；成功后更新 app_usage_sessions.remindedAt；最后返回包含当前显示的窗口计数。

- [ ] **Step 5: 运行 GREEN、生成 schema 并提交**

~~~powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.data.local.Migration5To6Test,com.example.focus_app.data.repository.ReminderDisplayRepositoryTest --console=plain
git add app/src/main/java/com/example/focus_app/data/local/entity/ReminderDisplayEventEntity.kt app/src/main/java/com/example/focus_app/data/local/dao/ReminderDisplayEventDao.kt app/src/main/java/com/example/focus_app/data/repository/ReminderDisplayRepository.kt app/src/main/java/com/example/focus_app/data/local/migration/Migration5To6.kt app/src/main/java/com/example/focus_app/data/local/AppDatabase.kt app/src/main/java/com/example/focus_app/di/DatabaseModule.kt app/src/main/java/com/example/focus_app/data/local/dao/AppUsageSessionDao.kt app/src/androidTest/java/com/example/focus_app/data/local/Migration5To6Test.kt app/src/androidTest/java/com/example/focus_app/data/repository/ReminderDisplayRepositoryTest.kt app/schemas
git commit -m "feat: track actual reminder displays"
~~~

### Task 4: 只在 ReminderActivity 实际恢复时扣额

**Files:**
- Create: app/src/main/java/com/example/focus_app/service/ReminderDisplayCoordinator.kt
- Create: app/src/test/java/com/example/focus_app/service/ReminderDisplayCoordinatorTest.kt
- Modify: app/src/main/java/com/example/focus_app/service/ReminderLaunchData.kt
- Modify: app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt
- Modify: app/src/main/java/com/example/focus_app/service/ReminderActivity.kt

**Interfaces:**
- ReminderLaunchData 新增 attemptId: String、displayKind: ReminderDisplayKind。
- Produces: ReminderDisplayCoordinator.confirm(data): ReminderLaunchData?。
- null 表示额度已满或数据库失败，Activity 关闭；非 null 带实际 windowReminderCount。

- [ ] **Step 1: 写协调器失败测试**

~~~kotlin
@Test fun first_resume_records_once_and_returns_actual_count() = runTest {
    val repository = FakeReminderDisplayRepository(ReminderDisplayResult.Displayed(2))
    val coordinator = ReminderDisplayCoordinator(repository, clock = { 10_000L })
    val confirmed = coordinator.confirm(LAUNCH_DATA)
    assertEquals(2, confirmed?.windowReminderCount)
    assertEquals(listOf("attempt-1"), repository.attemptIds)
}

@Test fun quota_rejection_returns_null() = runTest {
    val coordinator = ReminderDisplayCoordinator(
        FakeReminderDisplayRepository(ReminderDisplayResult.QuotaExceeded),
        clock = { 10_000L }
    )
    assertNull(coordinator.confirm(LAUNCH_DATA))
}
~~~

- [ ] **Step 2: 运行 RED**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.ReminderDisplayCoordinatorTest" --offline --console=plain
~~~

Expected: 协调器和新的启动字段尚不存在。

- [ ] **Step 3: 实现启动契约与 onResume 确认**

~~~kotlin
class ReminderDisplayCoordinator(
    private val displays: ReminderDisplayRepository,
    private val clock: () -> Long
) {
    suspend fun confirm(data: ReminderLaunchData): ReminderLaunchData? {
        val now = clock()
        val since = now - data.windowMinutes * 60_000L
        return when (val result = displays.recordDisplay(
            data.attemptId, data.sessionId, now, data.displayKind, since, data.windowLimit
        )) {
            is ReminderDisplayResult.Displayed ->
                data.copy(windowReminderCount = result.windowCount)
            ReminderDisplayResult.QuotaExceeded -> null
        }
    }
}
~~~

ReminderLauncher 只传递 attemptId 和 displayKind，不访问显示 Repository。ReminderActivity 从 onCreate/onStart 的挂载改为首次 onResume：先检查当前会话，再 confirm；成功才 setContent，失败 finishAndRemoveTask。overlayAttached 与 attemptId 唯一索引共同防止重建重复扣额。

- [ ] **Step 4: 运行 GREEN 与相关回归并提交**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.ReminderDisplayCoordinatorTest" --tests "com.example.focus_app.service.ReminderPresentationPolicyTest" --offline --console=plain
git add app/src/main/java/com/example/focus_app/service/ReminderDisplayCoordinator.kt app/src/test/java/com/example/focus_app/service/ReminderDisplayCoordinatorTest.kt app/src/main/java/com/example/focus_app/service/ReminderLaunchData.kt app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt app/src/main/java/com/example/focus_app/service/ReminderActivity.kt
git commit -m "fix: consume quota only for visible reminders"
~~~

### Task 5: 将首次提醒和 follow-up 接入显示事件

**Files:**
- Create: app/src/main/java/com/example/focus_app/service/ReminderAttemptIdGenerator.kt
- Modify: app/src/main/java/com/example/focus_app/service/ReminderScheduler.kt
- Modify: app/src/main/java/com/example/focus_app/service/FollowUpReminderExecutor.kt
- Test: app/src/test/java/com/example/focus_app/service/ReminderSchedulerTest.kt
- Test: app/src/test/java/com/example/focus_app/service/FollowUpReminderExecutorTest.kt

**Interfaces:**
- Produces: ReminderAttemptIdGenerator.newId(): String。
- Consumes: ReminderDisplayRepository.countSince/timesSince 和 ReminderLaunchData 的 attemptId/displayKind。

- [ ] **Step 1: 写首次提醒与连续 follow-up 失败测试**

~~~kotlin
@Test fun initial_launch_has_unique_attempt_without_consuming_display_quota() = runTest {
    val fixture = fixture(displayTimes = emptyList())
    fixture.scheduler.onSessionStarted(fixture.session) { true }
    advanceTimeBy(10_001L)
    runCurrent()
    assertEquals("attempt-1", fixture.launcher.shown.single().attemptId)
    assertEquals(ReminderDisplayKind.INITIAL, fixture.launcher.shown.single().displayKind)
    assertEquals(0, fixture.displayRepository.recordCalls)
}

@Test fun follow_up_launch_does_not_consume_quota_before_activity_resumes() = runTest {
    val fixture = fixture(displayTimes = listOf(NOW - 1_000L))
    assertEquals(FollowUpDecision.SHOW, fixture.executor.execute(session.id))
    assertEquals(ReminderDisplayKind.FOLLOW_UP, fixture.launcher.shown.single().displayKind)
    assertEquals(0, fixture.displayRepository.recordCalls)
}
~~~

- [ ] **Step 2: 运行 RED**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.ReminderSchedulerTest" --tests "com.example.focus_app.service.FollowUpReminderExecutorTest" --offline --console=plain
~~~

Expected: 启动数据没有 attemptId/displayKind，额度仍从会话 remindedAt 读取。

- [ ] **Step 3: 实现最小接入**

~~~kotlin
@Singleton
class ReminderAttemptIdGenerator @Inject constructor() {
    fun newId(): String = UUID.randomUUID().toString()
}
~~~

ReminderScheduler 仍用 markRemindedIfNeeded 防止首次请求重复，但滚动额度改读 ReminderDisplayRepository；构造 INITIAL attempt。FollowUpReminderExecutor 在 Gate 通过并 claimSnooze 成功后构造 FOLLOW_UP attempt，不再在 Launcher 前调用 updateRemindedAt。两者只请求启动，不直接插入显示事件。

- [ ] **Step 4: 运行 GREEN 并提交**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.ReminderSchedulerTest" --tests "com.example.focus_app.service.FollowUpReminderExecutorTest" --tests "com.example.focus_app.service.HybridFollowUpSchedulerTest" --offline --console=plain
git add app/src/main/java/com/example/focus_app/service/ReminderAttemptIdGenerator.kt app/src/main/java/com/example/focus_app/service/ReminderScheduler.kt app/src/main/java/com/example/focus_app/service/FollowUpReminderExecutor.kt app/src/test/java/com/example/focus_app/service/ReminderSchedulerTest.kt app/src/test/java/com/example/focus_app/service/FollowUpReminderExecutorTest.kt
git commit -m "fix: defer reminder quota to activity display"
~~~

### Task 6: 统一首页、额度重置和 AI 上下文

**Files:**
- Modify: app/src/main/java/com/example/focus_app/ui/home/HomeViewModel.kt
- Modify: app/src/main/java/com/example/focus_app/ui/home/HomeScreen.kt
- Modify: app/src/main/java/com/example/focus_app/domain/usecase/ResetReminderQuotaUseCase.kt
- Modify: app/src/main/java/com/example/focus_app/domain/usecase/BuildReminderContextUseCase.kt
- Test: app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt
- Test: app/src/test/java/com/example/focus_app/ui/home/HomeQuotaLabelTest.kt
- Test: app/src/test/java/com/example/focus_app/domain/usecase/ResetReminderQuotaUseCaseTest.kt
- Test: app/src/test/java/com/example/focus_app/domain/usecase/BuildReminderContextUseCaseTest.kt

**Interfaces:**
- Consumes: ReminderDisplayRepository。
- Produces: reminderQuotaLabel(minutes, count, limit) 包含已用和剩余次数。

- [ ] **Step 1: 写统一计数失败测试**

~~~kotlin
@Test fun quota_label_includes_remaining_count() {
    assertEquals(
        "本时间段（30 分钟）已提醒 2/5 次，剩余 3 次",
        reminderQuotaLabel(30, 2, 5)
    )
}

@Test fun reset_deletes_display_events_without_deleting_sessions() = runTest {
    useCase()
    assertEquals(listOf(NOW - 30 * 60_000L), displays.resetStarts)
    assertEquals(0, sessions.deletedSessionCount)
}

@Test fun ai_context_uses_actual_display_count() = runTest {
    val context = useCase(task, app, settings)
    assertEquals(3, context.remindersInWindow)
}
~~~

- [ ] **Step 2: 运行 RED**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.ui.home.HomeViewModelGuardianTest" --tests "com.example.focus_app.ui.home.HomeQuotaLabelTest" --tests "com.example.focus_app.domain.usecase.ResetReminderQuotaUseCaseTest" --tests "com.example.focus_app.domain.usecase.BuildReminderContextUseCaseTest" --offline --console=plain
~~~

Expected: 消费者仍读取 AppSessionRepository，首页文案没有剩余次数。

- [ ] **Step 3: 实现统一事件源**

~~~kotlin
internal fun reminderQuotaLabel(minutes: Int, count: Int, limit: Int): String {
    val safeCount = count.coerceAtLeast(0)
    val remaining = (limit - safeCount).coerceAtLeast(0)
    return "本时间段（$minutes 分钟）已提醒 $safeCount/$limit 次，剩余 $remaining 次"
}
~~~

HomeViewModel、ResetReminderQuotaUseCase、BuildReminderContextUseCase 注入 ReminderDisplayRepository。HomeViewModel 的当日会话统计和最近提醒列表仍保留 AppSessionRepository；只有滚动提醒额度切换为显示事件。

- [ ] **Step 4: 运行 GREEN 并提交**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.ui.home.HomeViewModelGuardianTest" --tests "com.example.focus_app.ui.home.HomeQuotaLabelTest" --tests "com.example.focus_app.domain.usecase.ResetReminderQuotaUseCaseTest" --tests "com.example.focus_app.domain.usecase.BuildReminderContextUseCaseTest" --offline --console=plain
git add app/src/main/java/com/example/focus_app/ui/home/HomeViewModel.kt app/src/main/java/com/example/focus_app/ui/home/HomeScreen.kt app/src/main/java/com/example/focus_app/domain/usecase/ResetReminderQuotaUseCase.kt app/src/main/java/com/example/focus_app/domain/usecase/BuildReminderContextUseCase.kt app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt app/src/test/java/com/example/focus_app/ui/home/HomeQuotaLabelTest.kt app/src/test/java/com/example/focus_app/domain/usecase/ResetReminderQuotaUseCaseTest.kt app/src/test/java/com/example/focus_app/domain/usecase/BuildReminderContextUseCaseTest.kt
git commit -m "fix: unify reminder quota with visible displays"
~~~

### Task 7: 按实际额度逐次放大弹窗

**Files:**
- Create: app/src/main/java/com/example/focus_app/ui/reminder/ReminderUrgency.kt
- Create: app/src/test/java/com/example/focus_app/ui/reminder/ReminderUrgencyTest.kt
- Modify: app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt

**Interfaces:**
- Produces: ReminderSurfaceScale(widthFraction, heightFraction, fullScreen)。
- Produces: reminderSurfaceScale(count, limit)。

- [ ] **Step 1: 写紧急程度失败测试**

~~~kotlin
@Test fun size_grows_monotonically_and_last_reminder_is_full_screen() {
    val first = reminderSurfaceScale(1, 5)
    val second = reminderSurfaceScale(2, 5)
    val last = reminderSurfaceScale(5, 5)
    assertTrue(second.widthFraction > first.widthFraction)
    assertTrue(second.heightFraction > first.heightFraction)
    assertEquals(1f, last.widthFraction)
    assertEquals(1f, last.heightFraction)
    assertTrue(last.fullScreen)
}

@Test fun single_available_reminder_is_full_screen() {
    assertTrue(reminderSurfaceScale(1, 1).fullScreen)
}
~~~

- [ ] **Step 2: 运行 RED**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.ui.reminder.ReminderUrgencyTest" --offline --console=plain
~~~

Expected: 尺寸策略尚不存在。

- [ ] **Step 3: 实现纯函数与 Compose 布局**

~~~kotlin
internal fun reminderSurfaceScale(count: Int, limit: Int): ReminderSurfaceScale {
    val progress = if (limit <= 1) 1f
    else ((count.coerceIn(1, limit) - 1).toFloat() / (limit - 1).toFloat())
    return ReminderSurfaceScale(
        widthFraction = 0.82f + 0.18f * progress,
        heightFraction = 0.50f + 0.50f * progress,
        fullScreen = progress >= 1f
    )
}
~~~

ReminderOverlay 非最后一次使用 fillMaxWidth(widthFraction) 和 fillMaxHeight(heightFraction)；最后一次使用 fillMaxSize、RectangleShape。Column 增加 verticalScroll(rememberScrollState())，系统状态栏、导航栏和全部操作按钮保持可用。

- [ ] **Step 4: 运行 GREEN、Compose 编译并提交**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.ui.reminder.ReminderUrgencyTest" --offline --console=plain
.\gradlew.bat :app:compileDebugKotlin --offline --console=plain
git add app/src/main/java/com/example/focus_app/ui/reminder/ReminderUrgency.kt app/src/test/java/com/example/focus_app/ui/reminder/ReminderUrgencyTest.kt app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt
git commit -m "feat: escalate reminder popup size with quota"
~~~

### Task 8: 完整验证、USB 真机验收与日志

**Files:**
- Modify: docs/DEVELOPMENT_LOG.md
- Reference: docs/superpowers/specs/2026-08-15-reminder-display-reliability-and-urgency-design.md

**Interfaces:**
- Consumes: Tasks 1～7 的代码和 Debug APK。
- Produces: 自动化测试、APK 来源、实时模式真机时间线、额度与弹窗证据。

- [ ] **Step 1: 运行完整 JVM 回归**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --offline --console=plain
~~~

记录 suites、tests、failures、errors、skipped 和 BUILD SUCCESSFUL。任何失败先回到对应 TDD 任务，不继续安装。

- [ ] **Step 2: 运行数据库迁移测试和 Debug APK 构建**

~~~powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.data.local.Migration5To6Test,com.example.focus_app.data.repository.ReminderDisplayRepositoryTest --console=plain
.\gradlew.bat :app:assembleDebug --offline --console=plain
~~~

- [ ] **Step 3: 检查仓库边界与 APK 来源**

~~~powershell
git diff --check
git status --short
Get-FileHash -Algorithm SHA256 app\build\outputs\apk\debug\app-debug.apk
~~~

确认 .idea、Gradle 缓存、APK、数据库导出和敏感值均未暂存。

- [ ] **Step 4: 完整安装到 RMX3350**

~~~powershell
& 'D:\Program\Android\SDK\platform-tools\adb.exe' devices -l
& 'D:\Program\Android\SDK\platform-tools\adb.exe' install -r -t 'app\build\outputs\apk\debug\app-debug.apk'
& 'D:\Program\Android\SDK\platform-tools\adb.exe' shell dumpsys package com.example.focus_app
~~~

禁止使用 Apply Changes 作为最终证据。提取设备 APK 并比较 SHA256，或记录可证明完整安装的包更新时间与代码版本。

- [ ] **Step 5: 实时模式基本提醒矩阵**

依次测试 1 分钟、5 分钟和自定义 2 分钟：记录 sessionId、snoozeUntil、dumpsys alarm、前台包名、Activity resumed 时间和 reminder_display_events。验收：

- 原目标应用仍在前台时，在合理延迟内显示 Focus AI 弹窗。
- 每次真实弹窗只新增一条 attemptId 事件，首页恰好增加一次。
- 通知出现但 Activity 未恢复时不新增事件。
- 到期前切换到设置时不弹、不扣；重新进入原目标应用也不补弹。
- 锁屏到期先重试，解锁后按真实前台应用显示或终止。
- 连续稍后两次都生成不同 attemptId，额度逐次增加。

- [ ] **Step 6: 紧急弹窗矩阵**

把额度上限分别设为 1、3、5，验证第一次到最后一次尺寸单调增大；最后一次占满 Activity 可用内容区。检查返回 Focus、返回桌面、自定义稍后提醒、取消菜单、大字体与纵向滚动均可操作。

- [ ] **Step 7: 更新开发日志并提交验证证据**

~~~powershell
git add docs/DEVELOPMENT_LOG.md
git commit -m "docs: record reminder display verification"
~~~

日志必须区分：代码和自动化已验证、RMX3350 实时模式已验证、兼容模式未验证、真正 force-stop 属于 Android 平台限制。

## 完成定义

- 1、60 分钟有效，61 分钟和其他非法输入不调度；自定义 2 分钟真机通过。
- 前台未知只重试；已确认设置等其他应用时终止旧提醒且不补弹。
- 同一会话首次提醒和多次 follow-up 按实际弹窗次数分别计数。
- 通知栏出现但弹窗未显示时不扣额度。
- 首页、Gate、额度重置和 AI 上下文使用同一显示事件源。
- 弹窗随实际已用额度单调增大，最后一次全屏且操作可达。
- 完整 JVM、迁移测试、Debug APK 构建和 RMX3350 实时模式证据齐全。
- 兼容模式保持“未验证”标记，不作完成声明。
