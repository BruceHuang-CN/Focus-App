# 第二轮真机提醒反馈 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让倒计时通知显示顶部横幅、定时菜单相对按钮居中、设置页所有离开路径统一确认，并让未处理的强制提醒在返回目标 App 后继续重显与逐次计数。

**Architecture:** 保留现有调度与数据库结构。Compose 的菜单偏移保持为局部纯函数，设置修改标记提升到 `NavGraph`；提醒注册表把“可见”和“待选择”分开，由两个检测入口复用一个重显协调器，并由既有展示事件仓库为每个新 attempt 记账。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Navigation Compose、Android NotificationChannel、Room、JUnit 4。

**Spec:** `docs/superpowers/specs/2026-09-02-reminder-device-feedback-round-2-design.md`

## Global Constraints

- 不新增第三方依赖、不新增 Activity、不增加轮询频率。
- 不安装 APK、不执行 connected AndroidTest、不自动操作手机。
- 每次实际提醒显示使用唯一 attempt ID 并记录一次展示；强制重显可以超过普通额度。
- 只发通知不记录展示；用户明确选择后必须清除待选择状态。

---

### Task 1: 倒计时横幅与菜单居中

**Files:**
- Create: `app/src/test/java/com/example/focus_app/service/FollowUpCountdownAlertPolicyTest.kt`
- Create: `app/src/test/java/com/example/focus_app/ui/reminder/ReminderMenuPositionTest.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpCountdownNotifier.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderDecisionButtons.kt`

**Interfaces:**
- Produces: `followUpCountdownAlertPolicy(): FollowUpCountdownAlertPolicy`
- Produces: `centeredMenuOffset(anchorWidth: Dp, menuWidth: Dp): Dp`

- [ ] **Step 1: 写失败测试**

测试要求 `focus_follow_up_countdown_v3`、`IMPORTANCE_HIGH`、`PRIORITY_HIGH`、默认声音，并要求 `360.dp` 按钮与 `220.dp` 菜单得到 `70.dp` 偏移，窄屏得到 `0.dp`。

- [ ] **Step 2: 运行测试确认 RED**

Run: `./gradlew.bat testDebugUnitTest --tests com.example.focus_app.service.FollowUpCountdownAlertPolicyTest --tests com.example.focus_app.ui.reminder.ReminderMenuPositionTest --console=plain`

Expected: 编译失败，因为两个新接口尚不存在。

- [ ] **Step 3: 写最小实现**

让通知频道与 Builder 使用同一策略值；使用 `BoxWithConstraints`、固定 `220.dp` 菜单宽度和纯偏移函数：

```kotlin
internal fun centeredMenuOffset(anchorWidth: Dp, menuWidth: Dp): Dp =
    ((anchorWidth - menuWidth) / 2).coerceAtLeast(0.dp)
```

- [ ] **Step 4: 运行筛选测试确认 GREEN**

Run: 同 Step 2；Expected: 两个测试类通过。

### Task 2: 设置页统一离开确认

**Files:**
- Create: `app/src/test/java/com/example/focus_app/ui/navigation/SettingsExitPolicyTest.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Produces: `settingsExitNeedsConfirmation(currentRoute: String?, hasUnsavedChanges: Boolean): Boolean`
- `SettingsScreen` consumes `hasUnsavedChanges`, `onUnsavedChangesChanged` and `onExitRequested`.

- [ ] **Step 1: 写失败测试**

覆盖“设置页且有修改需要确认”“设置页无修改直接离开”“其他页面不拦截”三个分支。

- [ ] **Step 2: 运行测试确认 RED**

Run: `./gradlew.bat testDebugUnitTest --tests com.example.focus_app.ui.navigation.SettingsExitPolicyTest --console=plain`

Expected: 编译失败，因为策略函数尚不存在。

- [ ] **Step 3: 写最小实现**

在 `NavGraph` 持有 `settingsEdited` 和待执行的返回/目标标签请求；底栏点击和 `SettingsScreen` 的返回都经过同一判断。确认按钮先清除修改标记，再执行保存的导航请求；取消只清除待处理请求。

- [ ] **Step 4: 运行筛选测试确认 GREEN**

Run: 同 Step 2；Expected: 三个分支通过。

### Task 3: 强制提醒待选择重显与逐次计数

**Files:**
- Modify: `app/src/test/java/com/example/focus_app/service/ReminderPresentationRegistryTest.kt`
- Modify: `app/src/test/java/com/example/focus_app/service/ReminderDisplayCoordinatorTest.kt`
- Create: `app/src/test/java/com/example/focus_app/service/PendingReminderRedisplayerTest.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/ReminderDisplayRepository.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderLaunchData.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderPresentationRegistry.kt`
- Create: `app/src/main/java/com/example/focus_app/service/PendingReminderRedisplayer.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderActivity.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderDisplayCoordinator.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderScheduler.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpReminderExecutor.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AppDetectionService.kt`

**Interfaces:**
- `ReminderLaunchData.targetPackageName: String`
- `ReminderDisplayKind.FORCED_REDISPLAY`
- `ReminderPresentationRegistry.onActivityStopped(data)` stores an undecided forced reminder as pending.
- `ReminderPresentationRegistry.prepareRedisplay(packageName, attemptId)` atomically returns one matching re-display attempt.
- `PendingReminderRedisplayer.onForegroundPackage(packageName)` is shared by realtime and compatibility detection.

- [ ] **Step 1: 写失败测试**

覆盖：强制提醒停止后不再“可见”但仍“待选择”；非目标包不重显；目标包只取出一次且产生新 attempt；明确 hide 后不重显；`FORCED_REDISPLAY` 在仓库返回 `QuotaExceeded` 的普通上限场景下仍以越限策略记录并返回展示计数。

- [ ] **Step 2: 运行测试确认 RED**

Run: `./gradlew.bat testDebugUnitTest --tests com.example.focus_app.service.ReminderPresentationRegistryTest --tests com.example.focus_app.service.PendingReminderRedisplayerTest --tests com.example.focus_app.service.ReminderDisplayCoordinatorTest --console=plain`

Expected: 编译失败或新行为断言失败，因为注册表尚未区分状态，也没有重显类型与协调器。

- [ ] **Step 3: 写最小实现**

注册表保存当前 `ReminderLaunchData` 与 `LAUNCHING/VISIBLE/PENDING` 状态。Activity 未经明确操作停止时进入 PENDING；启动失败恢复 PENDING，启动中超时也不永久占用。所有 Activity 状态修改都同时匹配 session 与 attempt，`onStop`/新 Intent 取消旧确认任务，异步结果只在 Activity 与注册表仍指向同一 attempt 时提交。提醒先以不可操作状态完成首帧绘制，再执行幂等记账和原子 `confirmVisible`，成功后才开放按钮。两个检测入口先调用共享重显协调器，再把注册表的会话保护状态交给 `AppSessionCoordinator`。重显复制原数据并替换唯一 attempt ID 与 `FORCED_REDISPLAY` 类型；展示协调器只对此类型使用不封顶的记录上限。

- [ ] **Step 4: 运行筛选测试确认 GREEN**

Run: 同 Step 2；Expected: 所有相关测试通过。

### Task 4: 本地回归与交接

**Files:**
- Modify: `docs/DEVELOPMENT_LOG.md`

**Interfaces:**
- Produces: 可由用户在 Android Studio 手动验证的四项清单。

- [ ] **Step 1: 运行完整 JVM 回归**

Run: `./gradlew.bat testDebugUnitTest --console=plain`

Expected: exit code 0，零失败。

- [ ] **Step 2: 编译 AndroidTest 源码但不连接设备**

Run: `./gradlew.bat compileDebugAndroidTestKotlin --console=plain`

Expected: exit code 0；不得运行 `connectedDebugAndroidTest`。

- [ ] **Step 3: 复核差异与敏感文件**

Run: `git status --short` 与 `git diff --check`

Expected: 无空白错误；`.idea`、压缩包和其他用户已有改动不纳入本轮实现结论。

- [ ] **Step 4: 更新开发日志**

记录源码行为、本地测试证据、未运行的 APK/安装/真机项目，以及用户需手动验证的四条路径。
