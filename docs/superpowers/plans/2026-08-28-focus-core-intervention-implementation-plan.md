# Focus 核心监控与任务干预实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` (recommended) or `subagent-driven-development` to implement this plan task-by-task. Each step uses checkbox (`- [ ]`) syntax and must complete its own test cycle before the next step.

**Goal:** 在现有 Focus-App Kotlin/Compose/Room 框架上，完成无障碍单模式 Guardian、可恢复的三选项任务干预、准确的结果证据、任务级 AI 文案缓存和合并重置操作。

**Architecture:** 保留 `FocusAccessibilityService`、现有 Room 数据库、`ReminderActivity`、三路调度器和 Hilt。新增不依赖 Android 的 Guardian/Intervention 领域状态机，由 `InterventionCoordinator` 负责事务、调度和展示编排；Service 只适配无障碍事件，Compose 只渲染持久化 UI 状态。数据库采用“干预主记录 + 追加式观测证据 + quota epoch”模型，所有外部唤醒通过事件 ID 和 deadline revision 幂等领取。

**Tech Stack:** Kotlin 2.0.20、Android SDK 35、minSdk 26、Jetpack Compose Material3、Room 2.6.1、Hilt 2.52、Kotlin Coroutines/Flow 1.9.0、JUnit4、Compose UI Test、Room `MigrationTestHelper`。

**Spec:** `docs/superpowers/specs/2026-08-28-focus-core-intervention-design.md`

## Global Constraints

- 任何新增或修改生产代码前，必须先写出针对该行为的失败测试并运行确认失败；每个测试再由最小实现变绿。
- 用户可见的监控模式只有无障碍事件驱动；`UsageStats` 兼容模式、轮询服务和模式选择 UI 必须移除，旧值迁移为实时模式。
- 新安装和旧版本升级的 `randomizeReminderActions` 默认均为 `true`；关闭后固定为 `RETURN → INTENTIONAL → REST`。
- 一次干预保存一个排列；重组、旋转、Activity 重建和重复 Intent 不得重新洗牌。
- 原始后台结果只允许 `NO_RESPONSE`、`BYPASSED`、`IMPLICIT_RETURN`、`DISPLAY_FAILED`、`UNKNOWN`、`INTERRUPTED`；只有前两项汇总为 Ignore。
- 双开实例按同一应用族处理；AI 文案包按任务快照、语气、Prompt 版本和语言缓存，不按包名或双开实例生成。
- “重置提醒窗口和 AI 文案”必须是一个本地原子操作；AI 刷新失败不得回滚额度重置，也不得并发发起多个刷新请求。
- 计时事实保存在 Room；运行中优先单调时钟，重启后使用 wall deadline，并用 `bootId` 防止跨开机比较 elapsed time。
- 不保存 API Key、完整 AI 文案、页面正文、聊天内容或通知内容到诊断事件；诊断导出必须由用户主动触发。
- 不复制反编译代码、资源、文案、标识、自动点击、反卸载、包名伪装、页面采集或激进保活行为。
- 不把源码测试、浏览器原型或 APK 构建结果当作真机后台可靠性证据；真机结论必须建立在新 APK 来源核对和设备回归之上。
- 修改和测试集中在当前 checkout；保留未跟踪的 `.superpowers/`，不将它加入任何 commit。

## 文件与责任总览

| 区域 | 新增或修改文件 | 单一责任 |
| --- | --- | --- |
| 领域 | `domain/intervention/*`、`domain/guardian/*`、`domain/time/*` | 纯 Kotlin 状态、排列、期限、结果规则 |
| 持久化 | `data/local/entity/*`、`data/local/dao/*`、`data/local/migration/Migration6To7.kt`、`data/repository/*`、`AppDatabase.kt` | Room v7、事务和事件证据 |
| 检测 | `FocusAccessibilityService.kt`、`AppSessionCoordinator.kt`、`AccessibilityDiagnostics*.kt`、`MainActivity.kt` | 无障碍事件适配、健康度和生命周期 |
| 编排 | 新增 `InterventionCoordinator.kt`、`InterventionDeadlineScheduler.kt`，收敛现有 `ReminderScheduler.kt`、`FollowUp*` | 创建、展示、决定、期限、恢复 |
| UI | `ReminderActivity.kt`、`ReminderLaunchData.kt`、`ReminderViewModel.kt`、`ReminderOverlay.kt`、`ui/components/*`、`SettingsScreen.kt` | 只渲染 UI 状态，回调显式用户意图 |
| 导航 | 新增 `TaskReturnNavigator.kt`，修改 `ReminderLauncher.kt`、`MainActivity.kt`、`NavGraph.kt` | 任务 ID、Deep Link 和逐级兜底 |
| AI | `PromptBuilder.kt`、`ReminderBatchCoordinator.kt`、`ReminderCacheRepository.kt`、新增 `TaskCopyPack*` | 任务级一次生成、槽位替换、本地 fallback |
| 验证 | `app/src/test/...`、`app/src/androidTest/...`、必要时 `docs/testing.md` | 红绿重构、Room migration、Compose 和设备门槛 |

---

### Task 1: 建立纯 Kotlin 干预领域模型和排列规则

**Files:**
- Create: `app/src/main/java/com/example/focus_app/domain/intervention/InterventionAction.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/intervention/ActionOrder.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/intervention/CurrentTaskSnapshot.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/intervention/InterventionOutcome.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/intervention/InterventionState.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/guardian/GuardianModels.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/intervention/ActionOrderTest.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/intervention/InterventionOutcomeTest.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/guardian/GuardianModelsTest.kt`

**Interfaces:**
- Produces `enum class InterventionAction { RETURN, INTENTIONAL, REST }`.
- Produces `data class ActionOrder(val actions: List<InterventionAction>)`，以及 `ActionOrder.choose(randomize: Boolean, previous: ActionOrder?, random: Random): ActionOrder`。
- Produces `enum class ReturnTargetType { FOCUS_TASK, APP, DEEPLINK, HOME }`，用于区分任务级返回目标和旧版 `ReturnDestination` UI 选项。
- Produces `data class CurrentTaskSnapshot(taskId: Long?, title: String?, returnTargetType: ReturnTargetType, returnPackageName: String?, returnDeepLink: String?, capturedAt: Long, taskRevision: Long)`。
- Produces `enum class InterventionOutcome { NO_RESPONSE, BYPASSED, IMPLICIT_RETURN, DISPLAY_FAILED, UNKNOWN, INTERRUPTED }`。
- Produces `enum class InterventionDecision { RETURN, INTENTIONAL, REST }` and immutable `InterventionState` containing event ID, phase, action order, decision, deadline revision and evidence quality。
- Produces `enum class InterventionPhase { DETECTED, DISPLAY_REQUESTED, DISPLAYED, DECIDED, TIMED, OBSERVING, RESOLVED, DISPLAY_FAILED }`。

- [ ] **Step 1: 写排列规则失败测试**

```kotlin
@Test
fun randomized_order_has_all_three_actions_and_differs_from_previous() {
    val previous = ActionOrder(listOf(RETURN, INTENTIONAL, REST))
    val next = ActionOrder.choose(true, previous, Random(7))

    assertEquals(3, next.actions.toSet().size)
    assertNotEquals(previous, next)
}

@Test
fun disabled_randomization_uses_fixed_product_order() {
    assertEquals(
        listOf(RETURN, INTENTIONAL, REST),
        ActionOrder.choose(false, null, Random(1)).actions
    )
}
```

- [ ] **Step 2: 运行指定测试确认按预期失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.intervention.ActionOrderTest"`

预期：FAIL，原因是 `ActionOrder` 和相关枚举尚不存在；若出现编译路径或测试命名错误，先修正测试本身，不能先写生产实现。

- [ ] **Step 3: 写最小领域实现**

实现 `ActionOrder.choose`：使用标准库 `Random` 生成全部 6 种排列；上一排列非空时从其余 5 种取一个；随机关闭直接返回固定顺序。构造函数拒绝重复或缺失动作，确保非法排列不能进入 UI。

- [ ] **Step 4: 运行测试确认变绿并补结果枚举测试**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.intervention.ActionOrderTest" --tests "com.example.focus_app.domain.intervention.InterventionOutcomeTest"`

必须覆盖 6 个排列可达、第一条无历史可任选、结果枚举只能包含 6 项，以及显式 `InterventionDecision` 不属于 Ignore 结果。

- [ ] **Step 5: 提交独立领域模型**

```powershell
git add app/src/main/java/com/example/focus_app/domain/intervention app/src/main/java/com/example/focus_app/domain/guardian app/src/test/java/com/example/focus_app/domain/intervention app/src/test/java/com/example/focus_app/domain/guardian
git commit -m "feat: add intervention domain models"
```

### Task 2: 实现 Guardian 纯状态机和可测试时间输入

**Files:**
- Create: `app/src/main/java/com/example/focus_app/domain/guardian/GuardianEngine.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/guardian/ForegroundObservation.kt`
- Modify: `app/src/main/java/com/example/focus_app/domain/time/Clock.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/guardian/GuardianEngineTest.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/guardian/GuardianEngineConcurrencyTest.kt`

**Interfaces:**
- `ForegroundObservation(packageName: String?, observedAt: Long, source: ObservationSource, isSystemSurface: Boolean)`。
- `enum class ObservationSource { ACCESSIBILITY, SERVICE_LIFECYCLE }`。
- `GuardianHealth` 取 `ACTIVE`、`DEGRADED`、`PERMISSION_REQUIRED`、`STOPPED`。
- `GuardianState` 取 `IDLE`、`ENTER_PENDING`、`TARGET_ACTIVE`、`INTERVENTION_ACTIVE`、`SUPPRESSED_INTENTIONAL`、`SUPPRESSED_REST`、`EXIT_PENDING`。
- `data class GuardianSnapshot(state: GuardianState, health: GuardianHealth, foregroundPackage: String?, activeAppFamilyId: String?, activeTaskId: Long?, suppressionDeadline: Long?)`。
- `sealed interface GuardianInput { data class ForegroundChanged(val observation: ForegroundObservation): GuardianInput; data class HealthChanged(val health: GuardianHealth): GuardianInput; data class DeadlineReached(val interventionId: Long): GuardianInput; data object ServiceStopped: GuardianInput }`。
- `sealed interface GuardianCommand { data class StartSession(val packageName: String, val appFamilyId: String, val taskId: Long?): GuardianCommand; data class RequestIntervention(val packageName: String, val appFamilyId: String, val taskId: Long?): GuardianCommand; data class CloseSession(val reason: String): GuardianCommand; data object AwaitTrustedObservation: GuardianCommand }`。
- `data class GuardianTransition(val snapshot: GuardianSnapshot, val commands: List<GuardianCommand>)`。
- `GuardianHealthPolicy.evaluate(accessibilityEnabled: Boolean, guardianEnabled: Boolean, millisSinceLastEvent: Long): GuardianHealth`。
- `GuardianEngine.reduce(state: GuardianSnapshot, input: GuardianInput): GuardianTransition` 无 Android、数据库和协程依赖；同输入必须得到同输出。

- [ ] **Step 1: 写状态机失败测试**

测试 `target_enter_starts_pending_then_active_after_debounce`、`system_surface_does_not_close_target_session`、`stopped_health_never_requests_intervention`、`rest_suppresses_other_target_apps`、`missing_evidence_produces_unknown_command`。测试输入使用固定 `FakeClock` 和明确时间戳，不使用 `delay`。

```kotlin
val result = engine.reduce(
    initial,
    GuardianInput.ForegroundChanged(
        ForegroundObservation("com.target", 1_000L, ObservationSource.ACCESSIBILITY, false)
    )
)
assertEquals(GuardianState.ENTER_PENDING, result.snapshot.state)
assertTrue(result.commands.isEmpty())
```

- [ ] **Step 2: 运行状态机测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.guardian.GuardianEngineTest" --tests "com.example.focus_app.domain.guardian.GuardianEngineConcurrencyTest"`

预期：FAIL，原因是 `GuardianEngine` 和输入/输出类型尚未定义。

- [ ] **Step 3: 实现最小 reducer 和健康边界**

实现目标应用族匹配、防抖、系统浮层过滤、有效暂停、服务停止/权限缺失和事件停滞降级。不要在领域层查询 UsageStats；未知观测只能输出 `UNKNOWN` 或等待可信事件。

```kotlin
fun reduce(state: GuardianSnapshot, input: GuardianInput): GuardianTransition {
    return when (input) {
        is GuardianInput.ForegroundChanged -> reduceForeground(state, input.observation)
        is GuardianInput.HealthChanged -> reduceHealth(state, input.health)
        is GuardianInput.DeadlineReached -> reduceDeadline(state, input.interventionId)
        GuardianInput.ServiceStopped -> stoppedTransition(state)
    }
}
```

- [ ] **Step 4: 验证恢复和取消语义**

运行同一组测试并额外验证：取消一个待离开确认不会关闭新会话；健康度从 `DEGRADED` 恢复到 `ACTIVE` 后只接受新观测，不补造缺失事件；相同输入重复 reduce 不产生第二条创建命令。

- [ ] **Step 5: 提交状态机**

```powershell
git add app/src/main/java/com/example/focus_app/domain/guardian app/src/main/java/com/example/focus_app/domain/time/Clock.kt app/src/test/java/com/example/focus_app/domain/guardian
git commit -m "feat: add accessibility guardian state machine"
```

### Task 3: Room v7 干预事件、观测证据和额度 epoch

**Files:**
- Create: `app/src/main/java/com/example/focus_app/data/local/entity/InterventionEntity.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/entity/InterventionObservationEntity.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/entity/ReminderQuotaEpochEntity.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/entity/AiReminderPackEntity.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/entity/PurposePresetEntity.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/dao/InterventionDao.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/dao/InterventionObservationDao.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/dao/ReminderQuotaEpochDao.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/dao/AiReminderPackDao.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/dao/PurposePresetDao.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/migration/Migration6To7.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/focus_app/di/DatabaseModule.kt`
- Create: `app/src/main/java/com/example/focus_app/data/repository/InterventionRepository.kt`
- Create: `app/src/main/java/com/example/focus_app/data/repository/ReminderQuotaRepository.kt`
- Test: `app/src/androidTest/java/com/example/focus_app/data/local/Migration6To7Test.kt`
- Test: `app/src/androidTest/java/com/example/focus_app/data/repository/InterventionRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/example/focus_app/data/repository/ReminderQuotaRepositoryTest.kt`

**Interfaces:**
- `InterventionRepository.begin(snapshot: CurrentTaskSnapshot, appFamilyId: String, observedPackageName: String, actionOrder: ActionOrder, requestedAt: Long): Long?`：已有活跃干预时返回已有 ID，不重复创建。
- `InterventionRepository.confirmDisplayed(interventionId: Long, attemptId: String, displayedAt: Long, windowStart: Long, limit: Int): DisplayConfirmation`：按 attempt ID 幂等插入显示事件，只有首次实际展示才扣额度。
- `InterventionRepository.recordObservation(event: InterventionObservation)` 只追加证据；`recordDecision`、`advanceDeadline`、`resolve` 均按事件 ID/revision 条件更新。
- `ReminderQuotaRepository.reset(now: Long): Long` 推进 epoch；`windowStart(now, windowMinutes)` 返回 `max(now-window, epoch.startedAt)`。
- `data class DisplayConfirmation(val displayed: Boolean, val windowCount: Int, val quotaExceeded: Boolean)`。
- `data class InterventionObservation(val interventionId: Long, val kind: String, val observedAt: Long, val packageName: String?, val health: GuardianHealth, val sourceGeneration: Long, val detailsCode: String?)`。

- [ ] **Step 1: 写 migration 和事务失败测试**

先新增 `Migration6To7Test.migration_sets_random_order_on_existing_settings_and_preserves_display_history`，建 v6 数据库，插入 `detectionMode='compatibility'`、一个旧显示事件来源、空/非空 `remindedAt` 和旧 `snoozeUntil`，迁移后断言：schema 为 7、随机字段为 1、检测模式为 realtime、历史显示记录仍在、旧 `snoozeUntil` 原值仍在且新的 pack 表为空。旧 snooze 的取消属于首次启动恢复器，不在 SQL migration 中猜测用户意图。

再写 `InterventionRepositoryTest.same_attempt_is_idempotent_and_quota_never_exceeds_limit` 和 `ReminderQuotaRepositoryTest.reset_advances_epoch_without_deleting_history`。

```kotlin
val first = repository.confirmDisplayed(7L, "attempt-a", 2_000L, 0L, 1)
val duplicate = repository.confirmDisplayed(7L, "attempt-a", 2_100L, 0L, 1)
assertEquals(DisplayConfirmation(true, 1, false), first)
assertEquals(first, duplicate)
assertEquals(1, repository.countDisplays(0L))
```

- [ ] **Step 2: 运行 Android migration/repository 测试确认失败**

运行：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.example.focus_app.data`

预期：FAIL，原因是 v7 表、migration 和 Repository 尚未存在；测试必须在真实 SQLite in-memory/ migration helper 上运行，不用普通 fake 代替 SQL 约束。

- [ ] **Step 3: 添加实体、DAO、v6→v7 migration 和数据库注册**

把 `AppDatabase.version` 改为 7，注册干预、观测、epoch、目的预设和 AI pack 实体/DAO，在 `DatabaseModule` 加入 `MIGRATION_6_7`。`InterventionEntity` 保存任务快照、排列、决定、双时钟期限、deadline revision、结果和效果状态；`InterventionObservationEntity` 使用事件 ID、时间、健康代次、包名和 details code；epoch 表保持单行，pack 表以任务快照 hash、语气、Prompt 版本和 locale 建唯一键。migration 同时插入 4～5 条 `appFamilyId IS NULL` 的全局目的预设，重复执行不增加副本。

- [ ] **Step 4: 实现事务和幂等条件**

在 Room transaction 内依次检查活跃干预、显示 attempt、epoch 下界和额度上限；只有插入成功才返回新的 `windowCount`。`attemptId` 唯一索引、`deadlineRevision` 条件更新和重复观测 ID 保证 Activity、Alarm、WorkManager 竞争不会超过一次。

```kotlin
database.withTransaction {
    if (displayDao.byAttemptId(attemptId) != null) return@withTransaction existingConfirmation()
    if (displayDao.countSince(windowStart) >= limit) return@withTransaction DisplayConfirmation(false, limit, true)
    displayDao.insert(displayEntity)
    DisplayConfirmation(true, displayDao.countSince(windowStart), false)
}
```

- [ ] **Step 5: 运行测试、检查 schema 和提交**

运行：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.example.focus_app.data`

确认 `app/schemas/7.json` 已生成且未包含 API Key/完整文案；然后提交：

```powershell
git add app/src/main/java/com/example/focus_app/data/local app/src/main/java/com/example/focus_app/data/repository/InterventionRepository.kt app/src/main/java/com/example/focus_app/data/repository/ReminderQuotaRepository.kt app/src/main/java/com/example/focus_app/di/DatabaseModule.kt app/src/androidTest/java/com/example/focus_app/data
git commit -m "feat: persist intervention evidence and quota epochs"
```

### Task 4: 设置迁移、随机开关和合并重置入口

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/data/local/entity/SettingsEntity.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/SettingsMapper.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/domain/usecase/ResetReminderQuotaUseCase.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/usecase/ResetReminderAndCopyUseCase.kt`
- Test: `app/src/test/java/com/example/focus_app/data/repository/RandomActionSettingsMappingTest.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/usecase/ResetReminderAndCopyUseCaseTest.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/settings/SettingsViewModelRandomActionTest.kt`
- Test: `app/src/androidTest/java/com/example/focus_app/ui/settings/SettingsRandomActionTest.kt`

**Interfaces:**
- `AppSettings.randomizeReminderActions: Boolean = true`，映射和 Room 默认值都为 true。
- `SettingsViewModel.setRandomizeReminderActions(enabled: Boolean)`。
- `data class ResetAndCopyResult(val quotaEpochId: Long, val copyRefreshStarted: Boolean)`；`ResetReminderAndCopyUseCase.invoke(): ResetAndCopyResult` 无论当前是否有任务都推进 quota epoch；有当前任务时清除文案轮换游标并请求一次 AI 刷新，无任务时只返回 `copyRefreshStarted=false`。

- [ ] **Step 1: 先写失败测试**

覆盖 `SettingsEntity()` 新用户默认 true、旧实体读取 true、旧 compatibility 读取 realtime、开关关闭后保存 false、重复重置只产生一个 in-flight copy request、AI 刷新失败仍保留新 epoch。Compose 测试只断言开关语义和一个合并按钮文本，不通过实现细节查找节点。

```kotlin
val settings = SettingsEntity(targetApps = "[]").toAppSettings()
assertTrue(settings.randomizeReminderActions)
settingsRepository.update { it.copy(randomizeReminderActions = false) }
assertFalse(settingsRepository.getSettings().randomizeReminderActions)
```

- [ ] **Step 2: 运行单元测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.data.repository.RandomActionSettingsMappingTest" --tests "com.example.focus_app.domain.usecase.ResetReminderAndCopyUseCaseTest" --tests "com.example.focus_app.ui.settings.SettingsViewModelRandomActionTest"`

预期：FAIL，原因是新字段、UseCase 和 ViewModel API 尚不存在。

- [ ] **Step 3: 添加设置字段并接入 mapper/ViewModel**

新增带 `@ColumnInfo(defaultValue = "1")` 的字段；`SettingsMapper` 强制兼容旧行，`SettingsViewModel` 只暴露一个切换方法。删除 Settings 中 `DetectionMode.entries` 的用户选择，但保留权限状态说明。

```kotlin
fun setRandomizeReminderActions(enabled: Boolean) {
    update { it.copy(randomizeReminderActions = enabled) }
}
```

- [ ] **Step 4: 实现合并重置 UseCase 和界面**

重置使用 quota epoch，不删除干预/显示历史；通过现有 `ReminderCacheRepository.requestRegeneration()` 触发一次刷新，Task 10 将该刷新实现替换为任务级 pack coordinator。SettingsScreen 在“提醒行为”区域显示开关和唯一的“重置提醒窗口和 AI 文案”按钮，使用 `LaunchedEffect` 监听一次性反馈，不在 Composable 中启动无主协程。

- [ ] **Step 5: 运行测试并提交设置改动**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.data.repository.RandomActionSettingsMappingTest" --tests "com.example.focus_app.domain.usecase.ResetReminderAndCopyUseCaseTest" --tests "com.example.focus_app.ui.settings.SettingsViewModelRandomActionTest"`，再运行：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.ui.settings.SettingsRandomActionTest`。

提交：

```powershell
git add app/src/main/java/com/example/focus_app/data/local/entity/SettingsEntity.kt app/src/main/java/com/example/focus_app/data/repository/SettingsRepository.kt app/src/main/java/com/example/focus_app/data/repository/SettingsMapper.kt app/src/main/java/com/example/focus_app/ui/settings app/src/main/java/com/example/focus_app/domain/usecase/ResetReminderQuotaUseCase.kt app/src/main/java/com/example/focus_app/domain/usecase/ResetReminderAndCopyUseCase.kt app/src/test/java/com/example/focus_app/data/repository/RandomActionSettingsMappingTest.kt app/src/test/java/com/example/focus_app/domain/usecase/ResetReminderAndCopyUseCaseTest.kt app/src/test/java/com/example/focus_app/ui/settings/SettingsViewModelRandomActionTest.kt app/src/androidTest/java/com/example/focus_app/ui/settings/SettingsRandomActionTest.kt
git commit -m "feat: add randomized reminder setting and combined reset"
```

### Task 5: 删除 UsageStats 兼容模式并接入 Guardian 健康状态

**Files:**
- Delete: `app/src/main/java/com/example/focus_app/service/AppDetectionService.kt`
- Delete: `app/src/main/java/com/example/focus_app/service/ForegroundObservationCursor.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/focus_app/domain/model/DetectionMode.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AppSessionCoordinator.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AccessibilityDiagnostics.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AccessibilityDiagnosticsStore.kt`
- Modify: `app/src/main/java/com/example/focus_app/MainActivity.kt`
- Modify: `app/src/main/java/com/example/focus_app/domain/permission/PermissionCheck.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/PermissionCheckCard.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/onboarding/OnboardingScreen.kt`
- Test: replace compatibility assertions in `app/src/test/java/com/example/focus_app/service/AppDetectionServicePolicyTest.kt`
- Test: update `app/src/test/java/com/example/focus_app/ui/settings/SettingsViewModelDetectionTest.kt`
- Test: add `app/src/test/java/com/example/focus_app/service/GuardianHealthPolicyTest.kt`

**Interfaces:**
- `DetectionMode` 只保留 `REALTIME("realtime")`；`fromKey("compatibility")` 返回 REALTIME。
- `RealtimeForegroundProvider` 返回带 `packageName`、`observedAtElapsed`、`sourceGeneration` 的快照；无新鲜事件时返回 `Unknown`，不查询 UsageStats。
- `AccessibilityDiagnostics` 新增 health、lastEventAt、sourceGeneration 和 stopReason，不记录正文或 API Key。

- [ ] **Step 1: 写失败测试证明兼容模式已不再是产品状态**

把原有 compatibility policy 测试改成：旧 key 映射 REALTIME、设置页模型只有一个监控方式、无障碍事件停滞进入 DEGRADED、无权限进入 PERMISSION_REQUIRED。新增 `MainActivity` 服务决策测试，断言不会启动 `AppDetectionService`。

```kotlin
assertEquals(DetectionMode.REALTIME, DetectionMode.fromKey("compatibility"))
assertEquals(GuardianHealth.PERMISSION_REQUIRED, healthPolicy.evaluate(false, true, 0L))
```

- [ ] **Step 2: 运行测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.GuardianHealthPolicyTest" --tests "com.example.focus_app.service.AccessibilityMonitoringPolicyTest" --tests "com.example.focus_app.ui.settings.SettingsViewModelDetectionTest"`

预期：FAIL，原因是枚举、Service 生命周期和权限 UI 仍暴露兼容模式。

- [ ] **Step 3: 实现单模式和服务生命周期**

删除 manifest 中 `AppDetectionService` 声明和 MainActivity 的兼容服务启动/停止分支；`FocusAccessibilityService` 只把原始窗口事件交给 Guardian，使用由 Service 持有且在 `onDestroy` 取消的 scope。任何待处理事件必须在服务销毁时停止，不能留后台无主协程。

```kotlin
override fun onDestroy() {
    packageChanges.close()
    serviceScope.cancel()
    super.onDestroy()
}
```

- [ ] **Step 4: 更新权限和诊断 UI**

移除 UsageStats 权限动作和检测模式单选；保留无障碍、悬浮窗、通知和目标应用检查。首页/设置显示四种健康状态及用户可执行的恢复动作。

- [ ] **Step 5: 运行全量当前单元测试并提交**

运行：`./gradlew.bat :app:testDebugUnitTest`，确认历史 AppSessionCoordinator 的防抖、提醒浮层过滤和 stop 行为仍通过。

提交：

```powershell
git add app/src/main app/src/test/java/com/example/focus_app/service app/src/test/java/com/example/focus_app/ui/settings
git commit -m "refactor: make accessibility the only guardian mode"
```

### Task 6: InterventionCoordinator、实际展示确认和三按钮 Compose UI

**Files:**
- Create: `app/src/main/java/com/example/focus_app/service/InterventionCoordinator.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderLaunchData.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderActivity.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderDisplayCoordinator.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderActionOptions.kt`
- Create: `app/src/main/java/com/example/focus_app/ui/reminder/InterventionUiState.kt`
- Test: `app/src/test/java/com/example/focus_app/service/InterventionCoordinatorTest.kt`
- Test: replace dropdown expectations in `app/src/test/java/com/example/focus_app/ui/reminder/ReminderActionOptionsTest.kt`
- Test: update `app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt`
- Test: add `app/src/androidTest/java/com/example/focus_app/ui/reminder/ReminderOverlayTest.kt`

**Interfaces:**
- `InterventionCoordinator.onGuardianCommand(command: GuardianCommand)`。
- `ReminderLaunchData` 改为携带 `interventionId`、`actionOrder`、`stage`、任务快照和显示 attempt；不再把三按钮行为编码在 `ReturnDestination` 下拉菜单里。
- `ReminderViewModel.onAction(action: InterventionAction)`、`onPurposeSelected(presetId: String)`、`onExtendFiveMinutes()`、`onDismiss()`。
- `ReminderOverlay(state: InterventionUiState, onAction: (InterventionAction) -> Unit, onPurposeSelected: (String) -> Unit, onExtend: () -> Unit, modifier: Modifier = Modifier)`：modifier 应用在根节点，所有可变 UI 状态由 ViewModel/屏幕状态持有。

- [ ] **Step 1: 写三按钮和展示确认失败测试**

单元测试断言：创建干预保存排列、同一个 intervention 重复请求返回同一排列、只有 `onResume` 的展示确认扣一次额度、通知/Activity 启动失败不扣额度、显式动作只写一次。Compose 测试用固定 `InterventionUiState`，通过语义文本点击三个按钮并断言传出的 `InterventionAction`。

```kotlin
var selected: InterventionAction? = null
composeTestRule.setContent {
    ReminderOverlay(
        state = InterventionUiState.forTest(ActionOrder.fixed()),
        onAction = { selected = it },
        onPurposeSelected = {}, onExtend = {}, modifier = Modifier
    )
}
composeTestRule.onNodeWithText("有目的使用").performClick()
assertEquals(InterventionAction.INTENTIONAL, selected)
```

- [ ] **Step 2: 运行测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.InterventionCoordinatorTest" --tests "com.example.focus_app.ui.reminder.ReminderViewModelTest"`。

预期：FAIL，原因是 coordinator、action state 和三按钮 callback 尚不存在。

- [ ] **Step 3: 实现 coordinator 和展示确认**

创建干预时在一个 Repository transaction 中冻结 `CurrentTaskSnapshot`、app family、`ActionOrder` 和 `displayAttemptId`。Launcher 只发起 Activity/通知；`ReminderActivity.onResume` 调 `confirmDisplayed`，收到 quota exceeded 或数据库失败立即结束并记录诊断。`onCreate`/`onStart`/`onResume` 重复触发必须通过 ID 幂等。

```kotlin
suspend fun onResume(interventionId: Long, attemptId: String) {
    val confirmation = repository.confirmDisplayed(interventionId, attemptId, clock.wallNow(), windowStart, limit)
    if (!confirmation.displayed || confirmation.quotaExceeded) finishAndRemoveTask()
    else render(repository.observe(interventionId))
}
```

- [ ] **Step 4: 重写 Compose 提醒页**

移除当前“退出目标应用”和“稍后提醒”两个下拉按钮，改为三个按 `actionOrder.actions` 渲染的语义按钮。排列、文案、阶段和剩余时间从 `StateFlow` 收集；导航、写库和计时启动只能在 ViewModel/Coordinator 中执行。为按钮提供稳定的 content description 和必要 testTag，但优先按可见文本断言。

- [ ] **Step 5: 运行单元和 Compose 测试并提交**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.InterventionCoordinatorTest" --tests "com.example.focus_app.ui.reminder.ReminderViewModelTest"`，再运行：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.ui.reminder.ReminderOverlayTest`。

提交：

```powershell
git add app/src/main/java/com/example/focus_app/service/InterventionCoordinator.kt app/src/main/java/com/example/focus_app/service/ReminderLaunchData.kt app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt app/src/main/java/com/example/focus_app/service/ReminderActivity.kt app/src/main/java/com/example/focus_app/service/ReminderDisplayCoordinator.kt app/src/main/java/com/example/focus_app/ui/reminder app/src/test/java/com/example/focus_app/service/InterventionCoordinatorTest.kt app/src/test/java/com/example/focus_app/ui/reminder app/src/androidTest/java/com/example/focus_app/ui/reminder/ReminderOverlayTest.kt
git commit -m "feat: render persisted three-action interventions"
```

### Task 7: 任务级返回导航和 Deep Link 兜底

**Files:**
- Create: `app/src/main/java/com/example/focus_app/service/TaskReturnNavigator.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt`
- Modify: `app/src/main/java/com/example/focus_app/MainActivity.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/local/entity/FocusTaskEntity.kt`
- Modify: `app/src/main/java/com/example/focus_app/domain/model/FocusTask.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/TaskRepository.kt`
- Test: `app/src/test/java/com/example/focus_app/service/TaskReturnNavigatorTest.kt`
- Test: `app/src/test/java/com/example/focus_app/MainActivityTaskIntentTest.kt`
- Test: `app/src/androidTest/java/com/example/focus_app/ui/navigation/TaskDeepLinkNavigationTest.kt`

**Interfaces:**
- `CurrentTaskSnapshot` 保存 task ID、标题、目标类型、包名、Deep Link、任务 revision。
- `TaskReturnNavigator.navigate(snapshot: CurrentTaskSnapshot): NavigationResult`，结果为 `TASK_TARGET_OPENED`、`FOCUS_TASK_OPENED`、`CUSTOM_APP_OPENED`、`HOME_OPENED`、`FAILED`。
- `enum class NavigationResult { TASK_TARGET_OPENED, FOCUS_TASK_OPENED, CUSTOM_APP_OPENED, HOME_OPENED, FAILED }`。
- `MainActivity` 启动时消费 `ReminderLauncher.ACTIVE_TASK_ID`，向 `NavGraph(initialTaskId: Long?)` 传入任务 ID，Tasks 页面真正定位并展示该任务。

- [ ] **Step 1: 写导航失败测试**

覆盖任务目标成功、Deep Link scheme/package 校验失败后回退 Focus、指定应用不可用后回退桌面、Intent 只带 taskId 但导航实际打开对应任务上下文。所有失败都断言结构化 `NavigationResult`，不只断言 `startActivity` 被调用。

```kotlin
assertEquals(
    NavigationResult.FOCUS_TASK_OPENED,
    navigator.navigate(snapshot.copy(returnDeepLink = "not-a-safe-link"))
)
```

- [ ] **Step 2: 运行测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.TaskReturnNavigatorTest" --tests "com.example.focus_app.MainActivityTaskIntentTest"`

预期：FAIL，原因是现有 Launcher 只支持 Focus/Home/Custom，MainActivity 尚未消费 task ID。

- [ ] **Step 3: 实现快照、导航器和 MainActivity intent 消费**

在干预创建时冻结返回目标；导航器逐级验证并执行，禁止打开未配置或不可解析包。MainActivity 对旧 Intent 无 taskId 时保持原首页行为，对有效 taskId 使用单次导航事件，避免重组重复跳转。

```kotlin
private var pendingTaskId: Long? = null

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    pendingTaskId = intent.getLongExtra(ReminderLauncher.ACTIVE_TASK_ID, -1L)
        .takeIf { it > 0L }
}
```

- [ ] **Step 4: 运行导航单元与 Compose 导航测试并提交**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.service.TaskReturnNavigatorTest" --tests "com.example.focus_app.MainActivityTaskIntentTest"`；设备/模拟器可用后运行：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.ui.navigation.TaskDeepLinkNavigationTest`。

提交：

```powershell
git add app/src/main/java/com/example/focus_app/service/TaskReturnNavigator.kt app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt app/src/main/java/com/example/focus_app/MainActivity.kt app/src/main/java/com/example/focus_app/ui/navigation app/src/main/java/com/example/focus_app/data/local/entity/FocusTaskEntity.kt app/src/main/java/com/example/focus_app/domain/model/FocusTask.kt app/src/main/java/com/example/focus_app/data/repository/TaskRepository.kt app/src/test/java/com/example/focus_app/service/TaskReturnNavigatorTest.kt app/src/test/java/com/example/focus_app/MainActivityTaskIntentTest.kt app/src/androidTest/java/com/example/focus_app/ui/navigation/TaskDeepLinkNavigationTest.kt
git commit -m "feat: navigate back to the active focus task"
```

### Task 8: 有目的使用、休息、期限调度和重启恢复

**Files:**
- Create: `app/src/main/java/com/example/focus_app/data/repository/PurposePresetRepository.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/intervention/PurposePreset.kt`
- Create: `app/src/main/java/com/example/focus_app/domain/intervention/InterventionDeadline.kt`
- Create: `app/src/main/java/com/example/focus_app/service/InterventionDeadlineScheduler.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpScheduler.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpAlarmReceiver.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpReminderWork.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/PendingFollowUpRestorer.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt`
- Create: `app/src/main/java/com/example/focus_app/ui/reminder/PurposePicker.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/intervention/InterventionDeadlineTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/InterventionDeadlineSchedulerTest.kt`
- Test: `app/src/test/java/com/example/focus_app/data/repository/PurposePresetRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/example/focus_app/ui/reminder/PurposePickerTest.kt`

**Interfaces:**
- `PurposePreset(id, appFamilyId: String?, label: String, defaultDurationMinutes: Int, enabled: Boolean)`；空 app family 使用全局 4～5 个默认目的。
- `enum class TimerKind { INTENTIONAL_USE, REST }`。
- `InterventionDeadline(deadlineWall: Long, deadlineElapsed: Long, bootId: String, revision: Long, extensionCount: Int, timerKind: TimerKind)`。
- `InterventionDeadlineScheduler.schedule(interventionId: Long, deadline: InterventionDeadline)`、`claimDue(interventionId: Long, revision: Long): Boolean`。

- [ ] **Step 1: 写期限和目的选择失败测试**

覆盖同一 boot 以 elapsed deadline 为准、重启后以 wall deadline 恢复、时间回拨进入 UNKNOWN、休息全局抑制、有目的使用仅抑制当前 app family、前两次可加 5 分钟、第三次必须重新选择目的、应用族无配置使用默认目的。

```kotlin
val deadline = deadlineFactory.create(nowWall = 10_000L, nowElapsed = 5_000L, minutes = 5, bootId = "boot-a")
assertTrue(deadlineEvaluator.isDue(deadline, nowWall = 10_001L, nowElapsed = 305_001L, bootId = "boot-a"))
assertTrue(deadlineEvaluator.isDue(deadline, nowWall = 310_000L, nowElapsed = 1L, bootId = "boot-b"))
```

- [ ] **Step 2: 运行测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.intervention.InterventionDeadlineTest" --tests "com.example.focus_app.service.InterventionDeadlineSchedulerTest" --tests "com.example.focus_app.data.repository.PurposePresetRepositoryTest"`，再运行：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.ui.reminder.PurposePickerTest`

预期：FAIL，原因是期限、目的预设和统一 scheduler 尚不存在。

- [ ] **Step 3: 实现双时钟期限和 revision 领取**

用现有进程内、AlarmManager、WorkManager 作为唤醒路径，但统一传 `interventionId + revision`；数据库 deadline 是事实来源，重复唤醒只能有一个领取者。协程由 scheduler/Service 持有并在销毁时取消，不在 Composable 中持有定时器。

```kotlin
fun onWake(interventionId: Long, revision: Long) {
    serviceScope.launch { if (scheduler.claimDue(interventionId, revision)) coordinator.onDeadline(interventionId) }
}
```

- [ ] **Step 4: 实现有目的使用和休息流程**

选择目的后立即关闭 UI 并保存标签快照；到期按当前可信前台应用决定静默结束、重新展示或保留通知。休息写全局暂停期限；进程冷启动、服务重连和 BOOT_COMPLETED 都调用同一恢复器。第三次延长必须回到 PurposePicker，不能自动续期。

- [ ] **Step 5: 运行测试并提交计时链路**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.intervention.InterventionDeadlineTest" --tests "com.example.focus_app.service.InterventionDeadlineSchedulerTest" --tests "com.example.focus_app.data.repository.PurposePresetRepositoryTest"`，再运行：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.ui.reminder.PurposePickerTest`。

提交：

```powershell
git add app/src/main/java/com/example/focus_app/data/repository/PurposePresetRepository.kt app/src/main/java/com/example/focus_app/domain/intervention/InterventionDeadline.kt app/src/main/java/com/example/focus_app/service/InterventionDeadlineScheduler.kt app/src/main/java/com/example/focus_app/service/FollowUpScheduler.kt app/src/main/java/com/example/focus_app/service/FollowUpAlarmReceiver.kt app/src/main/java/com/example/focus_app/service/FollowUpReminderWork.kt app/src/main/java/com/example/focus_app/service/PendingFollowUpRestorer.kt app/src/main/java/com/example/focus_app/ui/reminder app/src/test/java/com/example/focus_app/domain/intervention/InterventionDeadlineTest.kt app/src/test/java/com/example/focus_app/service/InterventionDeadlineSchedulerTest.kt app/src/test/java/com/example/focus_app/data/repository/PurposePresetRepositoryTest.kt app/src/androidTest/java/com/example/focus_app/ui/reminder/PurposePickerTest.kt
git commit -m "feat: add recoverable intentional and rest timers"
```

### Task 9: 30/120 秒效果观察和六种结果分类

**Files:**
- Create: `app/src/main/java/com/example/focus_app/domain/intervention/OutcomeClassifier.kt`
- Create: `app/src/main/java/com/example/focus_app/service/OutcomeObserver.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt`
- Modify (created in Task 6): `app/src/main/java/com/example/focus_app/service/InterventionCoordinator.kt`
- Modify (created in Task 3): `app/src/main/java/com/example/focus_app/data/repository/InterventionRepository.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/intervention/OutcomeClassifierTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/OutcomeObserverTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/InterventionOutcomeIntegrationTest.kt`

**Interfaces:**
- `OutcomeEvidence` 包含 displayed、decision、foreground transitions、system interruption、health、lock state、lifecycle end 和 observation checkpoint。
- `enum class ObservationCheckpoint { AT_30_SECONDS, AT_120_SECONDS }`。
- `enum class EvidenceQuality { HIGH, LIMITED, INSUFFICIENT }`。
- `sealed interface OutcomeClassification { data class Raw(val outcome: InterventionOutcome, val isIgnoreAggregate: Boolean, val evidenceQuality: EvidenceQuality, val classifierVersion: Int): OutcomeClassification; data class ExplicitDecision(val decision: InterventionDecision, val evidenceQuality: EvidenceQuality, val classifierVersion: Int): OutcomeClassification }`。
- `OutcomeClassifier.classify(evidence: OutcomeEvidence): OutcomeClassification`：输出 6 种原始结果或 `ExplicitDecision`，并携带 classifierVersion/evidenceQuality。
- `OutcomeObserver.observe(interventionId: Long, checkpoint: ObservationCheckpoint)`：30 秒和 120 秒分别写 `EFFECTIVE`、`INEFFECTIVE` 或 `UNKNOWN`。

- [ ] **Step 1: 写分类失败测试**

测试已显示且继续目标应用的 `NO_RESPONSE`、提醒丢失后重新进入目标应用的 `BYPASSED`、未点击但进入任务路径的 `IMPLICIT_RETURN`、Activity 没有展示的 `DISPLAY_FAILED`、锁屏/电话的 `INTERRUPTED`、无障碍断开/OEM 冻结的 `UNKNOWN`。断言只有 `NO_RESPONSE` 和 `BYPASSED` 的 `isIgnoreAggregate=true`。

```kotlin
val classification = classifier.classify(evidence)
val raw = classification as OutcomeClassification.Raw
assertTrue(raw.isIgnoreAggregate)
assertEquals(InterventionOutcome.BYPASSED, raw.outcome)
```

- [ ] **Step 2: 运行分类测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.intervention.OutcomeClassifierTest" --tests "com.example.focus_app.service.OutcomeObserverTest" --tests "com.example.focus_app.service.InterventionOutcomeIntegrationTest"`

预期：FAIL，原因是分类器和 observer 尚不存在。

- [ ] **Step 3: 实现保守分类器**

展示 30 秒无动作只追加“尚未响应”观测，不立即锁定结果；最终生命周期结束时仍无选择且目标应用继续使用才为 `NO_RESPONSE`。提醒失去前台后目标应用恢复且持续为 `BYPASSED`；去向不明、权限中断、锁屏和系统页面保持 `UNKNOWN/INTERRUPTED`。

```kotlin
if (evidence.displayed && evidence.lifecycleEnded && evidence.decision == null) {
    return if (evidence.targetAppContinued) raw(NO_RESPONSE) else raw(UNKNOWN)
}
```

- [ ] **Step 4: 接入效果观察时点**

`RETURN` 从决定/跳转发起开始观察；`INTENTIONAL`/`REST` 从期限结束或提前结束开始观察；无决定从展示生命周期结束开始观察。任务路径才可判定效果有效，离开分心应用但没有任务证据只能记录行为变化而保持效果 UNKNOWN。

- [ ] **Step 5: 运行测试、检查统计聚合并提交**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.domain.intervention.OutcomeClassifierTest" --tests "com.example.focus_app.service.OutcomeObserverTest" --tests "com.example.focus_app.service.InterventionOutcomeIntegrationTest"`，并运行现有 `StatsAggregatorTest` 确认 UNKNOWN 不进入 Ignore/有效率分母。

提交：

```powershell
git add app/src/main/java/com/example/focus_app/domain/intervention/OutcomeClassifier.kt app/src/main/java/com/example/focus_app/service/OutcomeObserver.kt app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt app/src/main/java/com/example/focus_app/service/InterventionCoordinator.kt app/src/main/java/com/example/focus_app/data/repository/InterventionRepository.kt app/src/test/java/com/example/focus_app/domain/intervention/OutcomeClassifierTest.kt app/src/test/java/com/example/focus_app/service/OutcomeObserverTest.kt app/src/test/java/com/example/focus_app/service/InterventionOutcomeIntegrationTest.kt
git commit -m "feat: classify intervention outcomes from evidence"
```

### Task 10: 任务级 AI 多阶段文案包和本地槽位替换

**Files:**
- Create: `app/src/main/java/com/example/focus_app/domain/model/TaskCopyPack.kt`
- Create: `app/src/main/java/com/example/focus_app/data/repository/TaskCopyPackRepository.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/remote/PromptBuilder.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/remote/ReminderBatchCoordinator.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/ReminderCacheRepository.kt`
- Test: `app/src/test/java/com/example/focus_app/data/remote/TaskCopyPackPromptTest.kt`
- Test: `app/src/test/java/com/example/focus_app/data/repository/TaskCopyPackRepositoryTest.kt`
- Test: `app/src/test/java/com/example/focus_app/data/remote/ReminderBatchCoordinatorTest.kt`

**Interfaces:**
- `TaskCopyPackKey(taskSnapshotHash: String, toneKey: String, promptVersion: Int, locale: String)`。
- `enum class CopyStage { INITIAL, INTENTIONAL_EXPIRY, EXTENSION_EXPIRY, REST_EXPIRY, RECALL_30_SECONDS, RECALL_120_SECONDS }`。
- `data class TaskCopyPack(val key: TaskCopyPackKey, val messagesByStage: Map<CopyStage, List<String>>, val createdAt: Long, val rotationCursor: Int)`。
- `TaskCopyPackRepository.getOrCreate(key, generate: suspend () -> TaskCopyPack): TaskCopyPack`、`getOrFallback(key): TaskCopyPack`、`invalidateForTask(taskId)`、`clearRotationCursor(taskId)`。
- `TaskCopyPack.render(stage: CopyStage, task: String, app: String, purpose: String?, minutes: Int, openCount: Int): String` 只允许白名单槽位 `{task}`、`{app}`、`{purpose}`、`{minutes}`、`{openCount}`。

- [ ] **Step 1: 写 AI 次数和渲染失败测试**

断言同一任务切换不同 app/双开实例/分钟数/打开次数不会产生第二次网络生成；切换任务、语气、Prompt 版本才改变 key；非法 JSON、重复短句、超长文本和未允许槽位落到本地模板；本地渲染能替换应用名、目的、分钟数和打开次数。

```kotlin
val first = packRepository.getOrCreate(key) { generator.generate(taskContext(app = "App A")) }
val second = packRepository.getOrCreate(key) { generator.generate(taskContext(app = "App B")) }
assertEquals(first.key, second.key)
assertEquals(1, fakeGenerator.callCount)
assertEquals("学习 5 分钟后再打开 App B", pack.render(INITIAL, "学习", "App B", null, 5, 2))
```

- [ ] **Step 2: 运行 AI 单元测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.data.remote.TaskCopyPackPromptTest" --tests "com.example.focus_app.data.repository.TaskCopyPackRepositoryTest" --tests "com.example.focus_app.data.remote.ReminderBatchCoordinatorTest" --tests "com.example.focus_app.domain.usecase.ResetReminderAndCopyUseCaseTest"`

预期：FAIL，原因是任务级 pack、cache key 和新 Prompt contract 尚不存在。

- [ ] **Step 3: 实现 JSON pack、Room cache 和本地 fallback**

新增阶段包：初次打断、目的到期、延长后到期、休息结束、30/120 秒召回。Coordinator 从“遍历每个 target app 生成”改为每个 active task/period 只生成一次；运行时本地选择候选并填槽位，不调用网络。

```kotlin
val key = TaskCopyPackKey(taskHash, settings.toneKey.key, PROMPT_VERSION, locale)
val copy = copyPackRepository.getOrFallback(key)
val message = copy.render(stage, taskTitle, appName, purpose, minutes, openCount)
```

- [ ] **Step 4: 接入合并重置并防止并发浪费**

将 `ResetReminderAndCopyUseCase` 与 pack repository 连接：epoch 立即推进、当前任务 rotation cursor 清除、只有一个 in-flight refresh；刷新失败继续旧 pack，再无旧 pack 才用 APK 本地模板。AI 协程由应用级 coordinator 的明确 scope 持有，取消时不影响 Guardian/Reminder。

- [ ] **Step 5: 运行 AI 测试和迁移测试并提交**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.data.remote.TaskCopyPackPromptTest" --tests "com.example.focus_app.data.repository.TaskCopyPackRepositoryTest" --tests "com.example.focus_app.data.remote.ReminderBatchCoordinatorTest" --tests "com.example.focus_app.domain.usecase.ResetReminderAndCopyUseCaseTest"`；再运行 v6→v7 migration 测试确认旧 app-keyed cache 不会被误当作新 pack。

提交：

```powershell
git add app/src/main/java/com/example/focus_app/domain/model/TaskCopyPack.kt app/src/main/java/com/example/focus_app/data/repository/TaskCopyPackRepository.kt app/src/main/java/com/example/focus_app/data/remote/PromptBuilder.kt app/src/main/java/com/example/focus_app/data/remote/ReminderBatchCoordinator.kt app/src/main/java/com/example/focus_app/data/repository/ReminderCacheRepository.kt app/src/test/java/com/example/focus_app/data/remote/TaskCopyPackPromptTest.kt app/src/test/java/com/example/focus_app/data/repository/TaskCopyPackRepositoryTest.kt app/src/test/java/com/example/focus_app/data/remote/ReminderBatchCoordinatorTest.kt
git commit -m "feat: cache task-level staged reminder copy"
```

### Task 11: 双开应用族校准和诊断可解释性

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/data/appgroup/AppGroup.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/appgroup/AppGroupStore.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/appgroup/AppGroupRepository.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/AppGroupEditorScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/AppGroupsScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AccessibilityDiagnostics.kt`
- Test: `app/src/test/java/com/example/focus_app/data/appgroup/AppFamilyResolutionTest.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/settings/AppGroupEditorPolicyTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/AccessibilityDiagnosticsTest.kt`

**Interfaces:**
- `AppFamilyResolver.resolve(packageName: String): AppFamilyMatch` 返回 `CONFIRMED`、`UNRESOLVED_CONTAINER` 或 `NOT_CONFIGURED`。
- `data class AppFamily(val id: String, val displayName: String, val packages: Set<String>, val aliases: Map<String, String>)`。
- `sealed interface AppFamilyMatch { data class Confirmed(val family: AppFamily, val alias: String?): AppFamilyMatch; data object UnresolvedContainer: AppFamilyMatch; data object NotConfigured: AppFamilyMatch }`。
- `AppGroup` 增加本地 family identity/alias 字段；别名不改变 AI pack key。

- [ ] **Step 1: 写双开边界失败测试**

同一应用族多个可靠包名映射到同一个 family；用户别名只改变显示名；同包名跨容器无法从普通无障碍事件区分时返回 `UNRESOLVED_CONTAINER`，UI 不显示“精确识别”。

```kotlin
val match = resolver.resolve("com.example.clone")
assertEquals(AppFamilyMatch.Confirmed(family, "工作副本"), match)
assertEquals(AppFamilyMatch.UnresolvedContainer, resolver.resolve("com.example.same-package"))
```

- [ ] **Step 2: 运行测试确认失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.data.appgroup.AppFamilyResolutionTest" --tests "com.example.focus_app.ui.settings.AppGroupEditorPolicyTest" --tests "com.example.focus_app.service.AccessibilityDiagnosticsTest"`

预期：FAIL，原因是应用族解析和诊断可信度字段尚不存在。

- [ ] **Step 3: 实现本地校准和隐私诊断**

复用现有应用组 UI，新增实例别名校准和可信度提示；不添加 `QUERY_ALL_PACKAGES`，不采集页面文本。诊断只展示最近无障碍事件、展示尝试、调度 revision、停止原因和容器识别限制。

```kotlin
fun resolve(packageName: String): AppFamilyMatch =
    configuredFamilies.firstOrNull { packageName in it.packages }
        ?.let { AppFamilyMatch.Confirmed(it, it.aliases[packageName]) }
        ?: AppFamilyMatch.NotConfigured
```

- [ ] **Step 4: 运行相关测试并提交**

运行：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.focus_app.data.appgroup.AppFamilyResolutionTest" --tests "com.example.focus_app.ui.settings.AppGroupEditorPolicyTest" --tests "com.example.focus_app.service.AccessibilityDiagnosticsTest"`。

提交：

```powershell
git add app/src/main/java/com/example/focus_app/data/appgroup app/src/main/java/com/example/focus_app/ui/settings/AppGroupEditorScreen.kt app/src/main/java/com/example/focus_app/ui/settings/AppGroupsScreen.kt app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt app/src/main/java/com/example/focus_app/service/AccessibilityDiagnostics.kt app/src/test/java/com/example/focus_app/data/appgroup/AppFamilyResolutionTest.kt app/src/test/java/com/example/focus_app/ui/settings/AppGroupEditorPolicyTest.kt app/src/test/java/com/example/focus_app/service/AccessibilityDiagnosticsTest.kt
git commit -m "feat: explain app-family and clone detection limits"
```

### Task 12: 全量回归、进程恢复和验证门槛

**Files:**
- Modify: `app/src/test/java/com/example/focus_app/service/*` 中与旧 snooze/compatibility 语义冲突的测试
- Modify: `app/src/test/java/com/example/focus_app/ui/home/*`、`app/src/test/java/com/example/focus_app/ui/stats/*` 中额度和 Ignore 统计测试
- Create: `app/src/androidTest/java/com/example/focus_app/service/InterventionProcessRecoveryTest.kt`
- Create: `app/src/androidTest/java/com/example/focus_app/service/InterventionNavigationRecoveryTest.kt`
- Create: `docs/testing.md`，记录已经存在的测试层、命令和真机证据格式

- [ ] **Step 1: 先运行当前基线，记录未修改前失败/通过**

运行：`./gradlew.bat :app:testDebugUnitTest`。保存命令输出和现有失败列表；不为了掩盖基线问题删除历史测试。

- [ ] **Step 2: 写进程/数据库恢复失败测试**

使用 in-memory Room 和 fake clock 构造：Activity 重建、Service 销毁重连、同一 deadline 的 Alarm/Work/进程内竞争、设备重启后 wall deadline、旧 snooze 清理、历史显示事件不被 reset 删除。然后运行：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.example.focus_app.service`，确认测试先红。

```kotlin
val first = scheduler.claimDue(interventionId = 9L, revision = 3L)
val second = scheduler.claimDue(interventionId = 9L, revision = 3L)
assertTrue(first)
assertFalse(second)
```

- [ ] **Step 3: 修复全量单元和 Compose 测试**

运行：`./gradlew.bat :app:testDebugUnitTest`；再运行：`./gradlew.bat :app:connectedDebugAndroidTest`。必须同时覆盖 Room migration、Guardian、展示/排列、三个动作、目的/休息、效果结果、Settings switch、合并重置和任务导航。

- [ ] **Step 4: 构建并核对 APK 来源**

仅在测试全绿后运行：`./gradlew.bat :app:assembleDebug`。记录 APK 的绝对路径、文件 hash、构建时间和 versionName/versionCode；安装前通过 `adb shell pm path com.example.focus_app` 核对设备路径与本地 APK，不以 Android Studio Apply Changes 作为部署证据。

```powershell
$apk = Resolve-Path .\app\build\outputs\apk\debug\app-debug.apk
Get-FileHash -Algorithm SHA256 $apk
adb shell pm path com.example.focus_app
```

- [ ] **Step 5: 执行设备回归清单**

在 realme/ColorOS 和至少一台较新 Android 设备执行：无障碍连接/失联、目标进入停留离开、1/5/10 分钟提醒、三按钮排列、任务返回、目的计时和两次延长、休息全局暂停、锁屏/电话、进程回收/重启、通知与悬浮窗限制、双开识别。每条记录设备型号、Android/OEM、APK hash、步骤、原始事件摘要和结果；未知证据不得填成 Ignore。

- [ ] **Step 6: 运行完成性检查并提交验证记录**

运行：`git diff --check`、`git status --short`、`git log --oneline -n 12`，确认没有 APK、keystore、API Key、build 输出或 `.superpowers/` 被加入。提交测试文档和必要的测试修正：

```powershell
git add app/src/test app/src/androidTest docs/testing.md
git commit -m "test: verify intervention recovery and device gates"
```

## 任务依赖与检查点

```text
Task 1 → Task 2 → Task 3 → Task 4
                    ↘ Task 5 → Task 6 → Task 7
                                      ↘ Task 8 → Task 9
Task 4 ───────────────────────────────────────→ Task 10
Task 5 ───────────────────────────────────────→ Task 11
Task 1..11 ───────────────────────────────────→ Task 12
```

- 检查点 A：Task 1～3 完成后，领域状态和 Room v7 可以独立测试，尚未改变用户可见监控。
- 检查点 B：Task 4～6 完成后，只有无障碍可触发新干预，三按钮和排列已经可见，旧兼容模式不再可选。
- 检查点 C：Task 7～9 完成后，任务跳转、两种计时、恢复和六种结果可从事件证据重建。
- 检查点 D：Task 10～11 完成后，AI 只按任务级缓存生成，双开限制和合并重置可解释。
- 检查点 E：Task 12 完成后才讨论新 APK/设备可靠性；任何一个设备证据缺失都只能报告为未验证。

## 计划自检结果

| 设计规范要求 | 覆盖任务 |
| --- | --- |
| 当前任务快照、任务级 Deep Link、MainActivity 消费 taskId | Task 3、Task 7 |
| 无障碍单模式、Guardian 状态和健康度 | Task 2、Task 5 |
| 三按钮、6 排列、上一排列排除、顺序持久化 | Task 1、Task 4、Task 6 |
| 实际展示扣额度、epoch 重置、历史不删除 | Task 3、Task 4、Task 6 |
| 有目的使用、4～5 本地目的、5 分钟延长、两次后重选 | Task 8 |
| 休息全局暂停、到期重判、进程/重启恢复 | Task 2、Task 8、Task 12 |
| 30/120 秒观察和六种结果、Ignore 只聚合两项 | Task 9、Task 12 |
| 任务级 AI pack、槽位替换、旧缓存/fallback、合并重置 | Task 4、Task 10 |
| 双开应用族、无法区分时不伪装精确 | Task 11 |
| Compose 状态归属、组件 Modifier、语义测试 | Task 6、Task 8、Task 12 |
| 并发取消、Flow replay/state、调度 revision | Task 2、Task 3、Task 8、Task 10 |
| 净室边界和真机验证门槛 | 全局约束、Task 12 |

计划完成检查：没有未定义的占位步骤或空泛的错误处理描述；测试路径、命令、接口名和后续任务依赖均已写明。实施时不得跳过任意红—绿—重构循环或把计划中的测试改成只验证 mock 调用次数。
