# Follow-up Countdown Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有到点提醒调度语义的情况下，为稍后提醒增加由 Android System UI 自行更新的通知栏倒计时。

**Architecture:** 在调度器和 Android 通知框架之间增加 `FollowUpCountdownNotifier` 边界。调度时发布倒计时，取消或进入到期执行时删除倒计时；真正到点仍完全由现有混合调度链路负责。

**Tech Stack:** Kotlin、AndroidX NotificationCompat、Hilt、JUnit4、kotlinx-coroutines-test

**Spec:** `docs/superpowers/specs/2026-09-01-follow-up-countdown-notification-design.md`

## Global Constraints

- 继续使用 `minSdk=26`、`compileSdk=35`、`targetSdk=35`。
- 不增加依赖，不使用 Android 16 Live Update，不增加每秒后台任务。
- 通知权限失败不能中断提醒调度。
- 不改变现有 1/5/10/自定义时长、前台复核、锁屏重试或额度逻辑。

---

### Task 1: 定义倒计时通知生命周期边界

**Files:**
- Create: `app/src/main/java/com/example/focus_app/service/FollowUpCountdownNotifier.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpScheduler.kt`
- Test: `app/src/test/java/com/example/focus_app/service/HybridFollowUpSchedulerTest.kt`

**Interfaces:**
- Produces: `FollowUpCountdownNotifier.show(sessionId: Long, delayMillis: Long)` 和 `cancel(sessionId: Long)`。
- Consumes: 现有 `FollowUpScheduler.schedule()`、`cancel()` 及协程到期执行路径。

- [ ] **Step 1: 写失败测试**

  增加 Recording notifier，断言 `schedule(100L, 300_000L)` 发布相同 session 与延迟；断言显式取消和到期执行都会删除 session 100 的倒计时。

- [ ] **Step 2: 验证测试因接口或行为缺失而失败**

  Run: `./gradlew.bat testDebugUnitTest --tests "com.example.focus_app.service.HybridFollowUpSchedulerTest"`

- [ ] **Step 3: 写最小实现**

  新增通知边界，在 `HybridFollowUpScheduler.schedule()` 调用 `show()`，在 `cancel()` 与到期执行前调用 `cancel()`；提供 NoOp 默认实现保持纯单元测试构造兼容。

- [ ] **Step 4: 验证定向测试通过**

  Run: `./gradlew.bat testDebugUnitTest --tests "com.example.focus_app.service.HybridFollowUpSchedulerTest"`

### Task 2: 覆盖所有持久化兜底执行入口

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpReminderExecutor.kt`
- Test: `app/src/test/java/com/example/focus_app/service/FollowUpReminderExecutorTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `FollowUpCountdownNotifier.cancel(sessionId)`。
- Produces: 无论 gate 返回 SHOW、SKIP 或 RETRY，进入到期执行时倒计时都已删除。

- [ ] **Step 1: 写失败测试**

  在 RETRY 场景注入 Recording notifier，并断言执行器在返回 RETRY 前取消该 session 的倒计时。

- [ ] **Step 2: 验证测试失败，原因是执行器尚未调用 notifier**

  Run: `./gradlew.bat testDebugUnitTest --tests "com.example.focus_app.service.FollowUpReminderExecutorTest"`

- [ ] **Step 3: 写最小实现**

  将 notifier 注入执行器并在 `execute()` 起始处调用 `cancel(sessionId)`；Hilt 构造器显式传入生产实现。

- [ ] **Step 4: 验证定向测试通过**

  Run: `./gradlew.bat testDebugUnitTest --tests "com.example.focus_app.service.FollowUpReminderExecutorTest"`

### Task 3: 实现 Android 原生倒计时通知

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpCountdownNotifier.kt`
- Create: `app/src/main/java/com/example/focus_app/di/FollowUpCountdownNotificationModule.kt`
- Test: `app/src/test/java/com/example/focus_app/service/FollowUpCountdownDeadlineTest.kt`

**Interfaces:**
- Consumes: `show(sessionId, delayMillis)` 与 `cancel(sessionId)`。
- Produces: 独立低重要度通知渠道、系统 chronometer 倒计时、按 session 隔离的通知 tag/id。

- [ ] **Step 1: 写失败测试**

  对纯 Kotlin 的截止时间计算断言：`now=1_000L, delay=300_000L` 得到 `301_000L`，负延迟按零处理。

- [ ] **Step 2: 验证测试因截止时间函数缺失而失败**

  Run: `./gradlew.bat testDebugUnitTest --tests "com.example.focus_app.service.FollowUpCountdownDeadlineTest"`

- [ ] **Step 3: 写最小 Android 实现**

  使用 `setWhen(dueAt)`、`setUsesChronometer(true)`、`setChronometerCountDown(true)`、`setOnlyAlertOnce(true)` 和独立低重要度渠道。发布前检查通知权限；捕获 `SecurityException`，不得传播到调度器。

- [ ] **Step 4: 验证截止时间与生命周期测试通过**

  Run: `./gradlew.bat testDebugUnitTest --tests "com.example.focus_app.service.FollowUpCountdownDeadlineTest" --tests "com.example.focus_app.service.HybridFollowUpSchedulerTest" --tests "com.example.focus_app.service.FollowUpReminderExecutorTest"`

### Task 4: 回归验证与交付检查

**Files:**
- Modify: `docs/DEVELOPMENT_LOG.md`

**Interfaces:**
- Consumes: Tasks 1-3 的完整实现。
- Produces: 可复现的测试和构建证据；不宣称未经真机验证的 ColorOS 通知样式。

- [ ] **Step 1: 运行提醒链路定向回归**

  Run: `./gradlew.bat testDebugUnitTest --tests "com.example.focus_app.service.*FollowUp*Test" --tests "com.example.focus_app.ui.reminder.ReminderViewModelTest"`

- [ ] **Step 2: 运行完整单元测试**

  Run: `./gradlew.bat testDebugUnitTest`

- [ ] **Step 3: 构建 Debug APK**

  Run: `./gradlew.bat assembleDebug`

- [ ] **Step 4: 审查工作区差异**

  Run: `git status --short` and `git diff --check`

- [ ] **Step 5: 记录验证边界**

  在开发日志记录源码测试与构建结果，并明确真实通知样式、通知权限关闭和 ColorOS 到点行为仍需真机验证。
