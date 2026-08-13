# 稍后提醒会话稳定性实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox - [ ] syntax for tracking.

**Goal:** 让短暂 Focus/系统窗口不再误取消稍后提醒，同时在用户真正离开目标 App 1.5 秒后关闭会话并取消提醒。

**Architecture:** AppSessionCoordinator 继续作为会话状态的唯一协调入口，但将目标 App 到非目标 App 的切换改成可取消的单次延迟确认。实时模式通过无障碍当前活动窗口复核目标包名，兼容模式复用现有 UsageStats 验证器；确认失败后才执行现有关闭会话和取消调度逻辑。

**Tech Stack:** Kotlin、Kotlin Coroutines、Android AccessibilityService、UsageStats、WorkManager、JUnit4、kotlinx-coroutines-test。

## Global Constraints

- 稍后提醒只在用户持续使用原目标 App 时有效。
- 真正离开目标 App 后不发通知，也不弹出稍后提醒。
- 固定使用 1,500 ms 单次离开确认，不新增周期轮询、AlarmManager 或常驻服务。
- stopCurrentSession() 继续立即停止，不等待 1,500 ms。
- 不修改提醒额度数据模型、DeepSeek 文案、统计图表、养成系统或 UI。
- 不提交 .idea、.gradle-user-home、构建产物或本地配置。
- 只运行聚焦单元测试的 RED 与 GREEN，最后运行一次完整 JVM 单元测试；不执行 APK 构建。

---

### Task 1: 用回归测试定义短暂窗口与真实离开

**Files:**

- Modify: app/src/test/java/com/example/focus_app/service/AppSessionCoordinatorTest.kt

**Interfaces:**

- Consumes: AppSessionCoordinator.onPackageChanged(packageName, foregroundVerifier, isReminderPresentation)
- Produces: 使用 TestScope.backgroundScope 和虚拟时间构造协调器的测试夹具；测试依赖构造参数 scope: CoroutineScope 与 departureConfirmationDelayMillis: Long。

- [ ] **Step 1: 先写会失败的行为测试**

把测试夹具改为向协调器注入 backgroundScope 和字面量 1_500L，并增加以下核心测试：

    @Test
    fun target_return_before_confirmation_keeps_session_and_reminder() = runTest {
        val fixture = fixture(scope = backgroundScope)

        fixture.coordinator.onPackageChanged(TARGET_A)
        val sessionId = fixture.repository.sessions.single().id
        fixture.coordinator.onPackageChanged(LAUNCHER)
        advanceTimeBy(1_000L)
        fixture.coordinator.onPackageChanged(TARGET_A)
        advanceTimeBy(501L)
        runCurrent()

        assertNull(fixture.repository.sessions.single().endedAt)
        assertEquals(emptyList<Long>(), fixture.reminderScheduler.cancelledSessionIds)
        assertEquals(sessionId, fixture.repository.currentOpenSession()?.id)
    }

    @Test
    fun confirmed_leave_closes_session_and_cancels_reminder_after_1500_ms() = runTest {
        val fixture = fixture(scope = backgroundScope)

        fixture.coordinator.onPackageChanged(TARGET_A)
        val sessionId = fixture.repository.sessions.single().id
        fixture.coordinator.onPackageChanged(
            packageName = LAUNCHER,
            foregroundVerifier = { false }
        )

        advanceTimeBy(1_499L)
        runCurrent()
        assertNull(fixture.repository.sessions.single().endedAt)

        fixture.clock.epochMillis += 1_500L
        advanceTimeBy(1L)
        runCurrent()

        assertEquals(STARTED_AT + 1_500L, fixture.repository.sessions.single().endedAt)
        assertEquals(listOf(sessionId), fixture.reminderScheduler.cancelledSessionIds)
    }

再增加两项保护：

- 相同非目标包名在确认窗口内重复出现时，不重新开始 1,500 ms 倒计时。
- 旧确认任务被目标 App 返回事件取消后，不能关闭之后创建的新会话。

测试分别防止立即关闭、事件风暴无限延长计时和旧协程关闭新会话。

- [ ] **Step 2: 调整既有会话测试的时间语义**

对原有“离开关闭”“目标 App 切换”“取消提醒”测试，在非目标事件后使用 advanceTimeBy(1_500L) 与 runCurrent()。保留 stopCurrentSession() 测试的立即断言，确保显式停止没有被延迟。

- [ ] **Step 3: 运行聚焦测试确认 RED**

运行：

    $env:GRADLE_USER_HOME='C:\Users\6\.gradle'
    .\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.service.AppSessionCoordinatorTest"

预期：compileDebugUnitTestKotlin 因 AppSessionCoordinator 尚无 scope 和 departureConfirmationDelayMillis 参数而失败，或新行为测试因当前实现立即关闭会话而失败。失败必须来自缺少新行为，而不是测试语法、夹具或依赖错误。

---

### Task 2: 实现可取消的单次离开确认

**Files:**

- Modify: app/src/main/java/com/example/focus_app/service/AppSessionCoordinator.kt
- Modify: app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt
- Modify: app/src/test/java/com/example/focus_app/service/AppSessionCoordinatorTest.kt

**Interfaces:**

- Produces: AppSessionCoordinator 新增 scope: CoroutineScope 和 departureConfirmationDelayMillis: Long 构造参数，生产默认值分别为 SupervisorJob + Dispatchers.Default 与 1_500L。
- Consumes: 实时模式传入 foregroundVerifier: suspend (String) -> Boolean，使用 rootInActiveWindow.packageName 与目标包名比较。
- Preserves: SessionReminderScheduler.cancel(sessionId) 仍同时取消首次延迟、WorkManager 稍后提醒、通知和已展示提醒。

- [ ] **Step 1: 增加最小的待确认状态**

在协调器中加入 PendingDeparture(sessionId, candidatePackage, revision, job)、departureRevision 和 pendingDeparture。锁内取消函数必须递增版本、取消旧 Job 并清空引用。

- [ ] **Step 2: 把目标会话的非目标事件改成单次延迟确认**

当 openSession 不为空且收到不同包名时：

1. isReminderPresentation 为 true 时，取消待确认并保留会话。
2. 包名等于原目标包名时，取消待确认并保留会话。
3. 待确认的 sessionId 和 candidatePackage 相同时直接返回，不重置倒计时。
4. 否则取消旧任务并用注入作用域启动新任务。
5. 任务延迟 departureConfirmationDelayMillis 后调用验证器检查原目标包名。
6. 验证器返回 true 时保留会话并恢复 foregroundPackage。
7. 验证器返回 false 或不存在，且版本和会话 ID 仍匹配时，执行现有取消与关闭逻辑；随后按候选包名创建新目标会话或记录普通前台包名。

延迟发生在 eventMutex 外；只在读取和提交状态时进入互斥锁，避免阻塞目标 App 返回事件。

- [ ] **Step 3: 保持显式停止立即生效**

stopCurrentSession() 先取消待确认，再执行调度取消、会话关闭和前台状态清空。旧确认任务醒来后必须通过版本和 sessionId 双重校验，不能影响新状态。

- [ ] **Step 4: 为实时无障碍模式提供真实前台复核**

FocusAccessibilityService 调用协调器时增加：

    foregroundVerifier = { expectedPackage ->
        rootInActiveWindow?.packageName?.toString() == expectedPackage
    }

兼容模式保持现有 UsageStats 验证器。该读取只发生在单次离开确认和既有首次提醒校验时。

- [ ] **Step 5: 运行聚焦测试确认 GREEN**

运行 Task 1 Step 3 的同一命令。

预期：AppSessionCoordinatorTest 全部通过；没有测试失败或 Kotlin 编译错误。

- [ ] **Step 6: 提交会话稳定性修复**

    git add -- app/src/main/java/com/example/focus_app/service/AppSessionCoordinator.kt app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt app/src/test/java/com/example/focus_app/service/AppSessionCoordinatorTest.kt
    git commit -m "fix: confirm target app departures"

---

### Task 3: 回归验证并更新记录

**Files:**

- Modify: DEVELOPMENT_LOG.md
- Modify: docs/PROJECT_TASKS.md
- Modify: docs/superpowers/plans/2026-08-13-snooze-session-stability.md

**Interfaces:**

- Consumes: Task 2 的协调器行为和聚焦测试结果。
- Produces: 一次完整 JVM 测试记录和仍需用户在 Android Studio 真机验证的清单。

- [ ] **Step 1: 运行一次完整 JVM 单元测试**

    $env:GRADLE_USER_HOME='C:\Users\6\.gradle'
    .\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache

预期：BUILD SUCCESSFUL，没有失败测试；不运行 assembleDebug 或安装 APK。

- [ ] **Step 2: 更新文档状态**

在开发日志的 2026-08-13 条目中把“待实现”改成“代码已实现，待真机验证”，记录实际聚焦测试与完整 JVM 测试结果。

在任务清单中仅勾选稳定稍后提醒绑定会话、1.5 秒单次复核和真正离开后取消提醒。保留“自定义 3 分钟真机回归”和不同厂商验证为未完成。

- [ ] **Step 3: 做提交前验证**

    git diff --check
    git status --short
    git diff -- app/src/main/java/com/example/focus_app/service/AppSessionCoordinator.kt app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt app/src/test/java/com/example/focus_app/service/AppSessionCoordinatorTest.kt DEVELOPMENT_LOG.md docs/PROJECT_TASKS.md docs/superpowers/plans/2026-08-13-snooze-session-stability.md

确认没有 .idea、.gradle-user-home、APK、数据库、API Key 或其他无关文件进入暂存区。

- [ ] **Step 4: 提交验证记录**

    git add -- DEVELOPMENT_LOG.md docs/PROJECT_TASKS.md docs/superpowers/plans/2026-08-13-snooze-session-stability.md
    git commit -m "docs: record snooze stability verification"

- [ ] **Step 5: 交给用户集中真机验证**

1. 抖音中出现提醒，选择自定义 3 分钟，继续停留抖音，3 分钟后再次提醒。
2. 选择稍后提醒后打开输入法、拉下通知栏或短暂出现系统窗口，再回到抖音，计时不丢失。
3. 选择稍后提醒后真正返回桌面或打开其他 App，等待超过所选时间，不出现通知或弹窗。
4. 快速在抖音、桌面和 Focus 间切换，旧任务不关闭新会话。
5. Android Studio Stop/重新安装属于系统强制停止路径，不作为后台稳定性测试。