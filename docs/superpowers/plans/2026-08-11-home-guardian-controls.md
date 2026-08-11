# 首页守护控制与额度重置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在首页提供守护开关、当前应用组信息和不调用 AI 的提醒额度重置，并移除设置页的重复入口。

**Architecture:** 新增一个只负责“结束旧会话并重置提醒额度”的用例；首页 ViewModel 组合设置和应用组状态后调用已有守护状态用例或新重置用例。Compose 首页仅渲染状态和转发动作，设置页不再保留额度重置入口。

**Tech Stack:** Kotlin、Jetpack Compose Material3、Hilt、Kotlin Flow、JUnit、Room 仓库。

## Global Constraints

- 重置必须先结束当前会话并取消待提醒，再恢复当前窗口提醒额度。
- 重置不得调用 DeepSeek、`ReminderCacheRepository.requestRegeneration()` 或删除使用统计。
- 守护开关继续使用 `UpdateGuardianStateUseCase.setGuardianEnabled`；关闭守护不关闭无障碍授权。
- 首页显示当前活动应用组；没有应用组时显示“未设置应用组”。
- 移除设置页“重置当前窗口额度并重新生成提醒”按钮与对应 ViewModel 方法。
- 不实现强制选择提醒、应用组编辑 UI、服务覆盖层或真机安装；这些在后续路线图任务中完成。
- 不提交 `.idea`、Gradle 缓存、`local.properties` 或构建产物。

---

### Task 1: 无 AI 的额度重置用例

**Files:**
- Create: `app/src/main/java/com/example/focus_app/domain/usecase/ResetReminderQuotaUseCase.kt`
- Create: `app/src/test/java/com/example/focus_app/domain/usecase/ResetReminderQuotaUseCaseTest.kt`

**Interfaces:**
- Consumes `SettingsRepository.getSettings()`, `AppSessionRepository.resetReminderQuota(since)`, `AppSessionCoordinator.stopCurrentSession()` 和 `Clock`。
- Produces `suspend operator fun invoke(): Unit`。

- [ ] **Step 1: 写失败的用例测试**

```kotlin
@Test
fun reset_stops_current_session_before_resetting_current_window_quota() = runTest {
    useCase()

    assertEquals(listOf("stop", "reset:${now - 60 * 60_000L}"), events)
}

@Test
fun reset_uses_configured_window_without_requesting_ai_regeneration() = runTest {
    settings = AppSettings(reminderWindowMinutes = 30)
    useCase()

    assertEquals(now - 30 * 60_000L, sessions.lastResetSince)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.example.focus_app.domain.usecase.ResetReminderQuotaUseCaseTest --no-daemon`

Expected: 编译失败，因为 `ResetReminderQuotaUseCase` 尚不存在。

- [ ] **Step 3: 写最小实现**

```kotlin
class ResetReminderQuotaUseCase private constructor(
    private val settings: SettingsRepository,
    private val sessions: AppSessionRepository,
    private val coordinator: AppSessionCoordinator,
    private val nowMillis: () -> Long
) {
    @Inject
    constructor(
        settings: SettingsRepository,
        sessions: AppSessionRepository,
        coordinator: AppSessionCoordinator
    ) : this(settings, sessions, coordinator, SystemClock::nowMillis)

    constructor(
        settings: SettingsRepository,
        sessions: AppSessionRepository,
        coordinator: AppSessionCoordinator,
        clock: Clock
    ) : this(settings, sessions, coordinator, clock::nowMillis)

    suspend operator fun invoke() {
        coordinator.stopCurrentSession()
        val current = settings.getSettings()
        sessions.resetReminderQuota(
            nowMillis() - current.reminderWindowMinutes * 60_000L
        )
    }
}
```

- [ ] **Step 4: 运行测试确认通过并提交**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.example.focus_app.domain.usecase.ResetReminderQuotaUseCaseTest --no-daemon`

Commit: `feat: add homepage reminder quota reset`

### Task 2: 首页状态和操作连接

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/ui/home/HomeViewModel.kt`
- Create: `app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt`

**Interfaces:**
- `HomeUiState` 新增 `guardianEnabled: Boolean = true` 与 `activeGroupName: String = "未设置应用组"`。
- `HomeViewModel` 注入 `SettingsRepository`、`AppGroupRepository`、`UpdateGuardianStateUseCase`、`ResetReminderQuotaUseCase`。
- `fun setGuardianEnabled(enabled: Boolean)` 与 `fun resetReminderQuota()` 启动 ViewModel 协程调用对应 use case。

- [ ] **Step 1: 写失败的状态和动作测试**

```kotlin
@Test
fun home_state_reflects_disabled_guardian_and_active_group_name() = runTest {
    settings.emit(AppSettings(guardianEnabled = false))
    groups.emit(listOf(group(id = "study", name = "学习组")), activeId = "study")

    assertEquals(false, viewModel.uiState.value.guardianEnabled)
    assertEquals("学习组", viewModel.uiState.value.activeGroupName)
}

@Test
fun homepage_actions_delegate_guardian_and_quota_operations() = runTest {
    viewModel.setGuardianEnabled(false)
    viewModel.resetReminderQuota()

    assertEquals(listOf(false), guardianUseCase.values)
    assertEquals(1, quotaResetUseCase.invocations)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.example.focus_app.ui.home.HomeViewModelGuardianTest --no-daemon`

Expected: 失败，因为首页状态字段和操作尚不存在。

- [ ] **Step 3: 写最小状态连接实现**

使用 `combine(settingsRepository.getSettingsFlow(), groups.groups, groups.activeGroupId)` 更新首页的两个守护字段；活动 ID 无匹配组时写入“未设置应用组”。保留现有统计加载逻辑，只更新对应字段，避免引入轮询。

- [ ] **Step 4: 运行测试确认通过并提交**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.example.focus_app.ui.home.HomeViewModelGuardianTest --no-daemon`

Commit: `feat: expose guardian controls on home`

### Task 3: 首页控件与设置页入口迁移

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt`

**Interfaces:**
- `HomeContent` 新增 `onGuardianEnabledChange: (Boolean) -> Unit` 和 `onResetReminderQuota: () -> Unit`。
- 首页新增“守护控制”卡：Switch、活动组名、状态文案和“重置提醒额度”按钮。

- [ ] **Step 1: 扩展失败测试覆盖重置不触发 AI**

```kotlin
@Test
fun resetting_from_home_never_requests_ai_regeneration() = runTest {
    resetReminderQuotaUseCase()

    assertEquals(0, reminderCache.requestCount)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.example.focus_app.domain.usecase.ResetReminderQuotaUseCaseTest --no-daemon`

Expected: 若重置路径仍包含 AI 刷新，断言失败。

- [ ] **Step 3: 写最小 UI 实现**

在首页统计卡之前显示守护控制卡：

```kotlin
Switch(
    checked = uiState.guardianEnabled,
    onCheckedChange = onGuardianEnabledChange
)
Text(if (uiState.guardianEnabled) "守护已开启" else "已暂停检测和提醒")
Text("当前应用组：${uiState.activeGroupName}")
OutlinedButton(onClick = onResetReminderQuota) {
    Text("重置提醒额度")
}
```

删除 SettingsScreen 的额度重置按钮和 snackbar；删除 `SettingsViewModel.resetReminderWindow()` 及其 `ReminderCacheRepository` 依赖（若除此之外未使用）。

- [ ] **Step 4: 编译、完整单测与提交**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`

Commit: `feat: move reminder reset to home`

### Task 4: 任务级审查与完成验证

**Files:**
- Modify: `docs/superpowers/plans/2026-08-11-home-guardian-controls.md`

- [ ] **Step 1: 检查提交范围**

Run: `git status --short` 和 `git diff --check HEAD~3..HEAD`

Expected: 仅有首页控制、额度重置、测试与本计划文件；不包含 `.idea` 或缓存。

- [ ] **Step 2: 最终 JVM 验证**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`

- [ ] **Step 3: 手动验收清单（后续集中真机阶段执行）**

1. 首页显示活动组、守护状态和重置按钮。
2. 关闭守护后打开目标 App 不产生提醒，无障碍授权保持开启。
3. 重置不生成 AI 内容、不删除历史；重新进入目标 App 后按延迟重新提醒。
4. 设置页不再存在额度重置入口。
