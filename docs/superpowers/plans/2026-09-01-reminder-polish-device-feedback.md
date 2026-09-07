# 提醒交互收尾 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让每次 Focus 提醒全屏覆盖、每个定时按钮就地展开菜单、稍后提醒可见倒计时，并把「回到任务」带到任务标签页。

**Architecture:** 继续以现有 `ReminderOverlay`、`ReminderViewModel`、`FollowUpScheduler` 和 `AlarmManager` 为边界：界面只改变呈现和交互，不接管真正调度。把短暂的菜单展开状态下沉到单个 Compose 按钮，将任务页启动意图作为 `MainActivity` 的一次导航请求传给 `NavGraph`。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Navigation Compose、Android 通知频道、JUnit 4、Compose UI Test、Android instrumentation test。

**Spec:** `docs/superpowers/specs/2026-09-01-reminder-polish-device-feedback-design.md`

## Global Constraints

- 不新增依赖、不新增 Activity，不更改数据库或提醒额度语义。
- 保留现有三个动作、六种稳定排列、1/5/10 分钟及 1～60 分钟自定义输入。
- 不删除旧通知频道；用新频道 ID 承载正常重要性。
- 通知权限未授予时不得影响真正的稍后提醒调度。
- 「回到任务」始终进入 `tasks` 标签页；旧 `ReturnDestination` 仍可为未改动的旧调用方保留。

---

### Task 1: 让所有提醒均为全屏不透明内容

**Files:**
- Modify: `app/src/test/java/com/example/focus_app/ui/reminder/ReminderUrgencyTest.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderUrgency.kt`

**Interfaces:**
- Consumes: `reminderUrgency(windowReminderCount: Int, windowLimit: Int): ReminderUrgency`
- Produces: 对任意窗口次数返回 `widthFraction == 1f`、`heightFraction == 1f`，并只用 `isFinalReminder` 标识最后一次的文案/颜色升级。

- [ ] **Step 1: 写入失败的尺寸策略测试**

将 `ReminderUrgencyTest.kt` 替换为以下三个行为断言：

```kotlin
@Test
fun every_available_reminder_fills_the_screen() {
    listOf(1, 2, 4).forEach { count ->
        val urgency = reminderUrgency(windowReminderCount = count, windowLimit = 5)
        assertEquals(1f, urgency.widthFraction, 0.0001f)
        assertEquals(1f, urgency.heightFraction, 0.0001f)
    }
}

@Test
fun final_reminder_still_has_final_flag() {
    assertTrue(reminderUrgency(5, 5).isFinalReminder)
}

@Test
fun missing_quota_is_still_full_screen_without_final_flag() {
    val urgency = reminderUrgency(0, 0)
    assertEquals(1f, urgency.widthFraction, 0.0001f)
    assertEquals(1f, urgency.heightFraction, 0.0001f)
    assertFalse(urgency.isFinalReminder)
}
```

- [ ] **Step 2: 运行测试确认其因旧的缩小卡片策略失败**

Run: `./gradlew testDebugUnitTest --tests com.example.focus_app.ui.reminder.ReminderUrgencyTest`

Expected: `every_available_reminder_fills_the_screen` 和 `missing_quota_is_still_full_screen_without_final_flag` 断言失败，实际值为 0.82/0.50 或中间插值。

- [ ] **Step 3: 写入最小实现**

将 `ReminderUrgency.kt` 中 `reminderUrgency` 的返回值收敛为：

```kotlin
internal fun reminderUrgency(windowReminderCount: Int, windowLimit: Int): ReminderUrgency {
    val safeCount = windowReminderCount.coerceAtLeast(1)
    return ReminderUrgency(
        widthFraction = 1f,
        heightFraction = 1f,
        isFinalReminder = windowLimit > 0 && safeCount >= windowLimit
    )
}
```

删除不再使用的 `lerp`、最小尺寸常量；不要修改 `ReminderOverlay` 的颜色或文案分支。

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew testDebugUnitTest --tests com.example.focus_app.ui.reminder.ReminderUrgencyTest`

Expected: 3 个测试通过。

### Task 2: 将时长菜单锚定在被点击的按钮旁

**Files:**
- Modify: `app/src/androidTest/java/com/example/focus_app/ui/reminder/ReminderDecisionButtonsTest.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderDecisionButtons.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt`

**Interfaces:**
- Consumes: `List<ReminderDecisionAction>` 与 `presetSnoozeMinutes`。
- Produces: `ReminderDecisionButtons(actions, onReturnClick, onTimedDecision, onCustomTimedAction, modifier)`；`onTimedDecision` 的签名为 `(ReminderDecisionAction, Int) -> Unit`，`onCustomTimedAction` 的签名为 `(ReminderDecisionAction) -> Unit`。

- [ ] **Step 1: 写入失败的 Compose 交互测试**

将现有测试改为：点击「有目的使用」后先显示「5 分钟后提醒」且不派发动作；点击该时长后才记录 `INTENTIONAL, 5`。再添加一项测试：点击「休息一下」后点击「自定义分钟数」，只记录 `REST` 自定义入口。测试核心如下：

```kotlin
composeRule.onNodeWithText("有目的使用").performClick()
composeRule.onNodeWithText("5 分钟后提醒").assertIsDisplayed().performClick()
composeRule.runOnIdle {
    assertEquals(listOf(ReminderDecisionAction.INTENTIONAL to 5), timedSelections)
}

composeRule.onNodeWithText("休息一下").performClick()
composeRule.onNodeWithText("自定义分钟数").performClick()
composeRule.runOnIdle {
    assertEquals(listOf(ReminderDecisionAction.REST), customSelections)
}
```

在此失败阶段保留现有 `onTimedActionClick` 调用，使旧组件可编译；点击后同步派发动作而没有弹出菜单，因此测试会因找不到菜单或错误派发而失败。

- [ ] **Step 2: 编译并运行失败测试**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.ui.reminder.ReminderDecisionButtonsTest`

Expected: 测试失败，原因是旧组件点击后立即调用 `onTimedActionClick`，并没有在该按钮的容器中显示时长菜单。

- [ ] **Step 3: 写入最小组件实现**

在 `ReminderDecisionButtons.kt` 中：

```kotlin
var expandedAction by rememberSaveable { mutableStateOf<ReminderDecisionAction?>(null) }

Box(modifier = Modifier.fillMaxWidth()) {
    FilledTonalButton(
        onClick = { expandedAction = ReminderDecisionAction.INTENTIONAL },
        modifier = Modifier.fillMaxWidth()
    ) { Text("有目的使用") }
    DropdownMenu(
        expanded = expandedAction == ReminderDecisionAction.INTENTIONAL,
        onDismissRequest = { expandedAction = null }
    ) { /* 1/5/10 分钟与自定义入口；回调后关闭 */ }
}
```

为 `REST` 使用同样的独立 `Box` 与 `DropdownMenu`。根 `Column` 只使用调用方给出的 `modifier`，由 `ReminderOverlay` 传入 `Modifier.fillMaxWidth()`；不要让共享外层 `Box` 再承载菜单。

在 `ReminderOverlay.kt` 删除 `timedMenuAction`、外层 `DropdownMenu` 和相关 Material imports，改为：

```kotlin
ReminderDecisionButtons(
    actions = actionOrder,
    onReturnClick = { viewModel.returnToFocus(data.sessionId, onDismiss) },
    onTimedDecision = { action, minutes ->
        applyTimedDecision(viewModel, action, data.sessionId, minutes, onDismiss)
    },
    onCustomTimedAction = { action -> customTimedAction = action },
    modifier = Modifier.fillMaxWidth()
)
```

- [ ] **Step 4: 将测试改到新回调并确认通过**

把测试的回调替换为 `onTimedDecision` 与 `onCustomTimedAction`，并运行：

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.ui.reminder.ReminderDecisionButtonsTest`

Expected: 两个测试通过；每次菜单由各自按钮的 `Box` 锚定。

### Task 3: 以新的正常重要性频道显示倒计时

**Files:**
- Create: `app/src/androidTest/java/com/example/focus_app/service/FollowUpCountdownNotifierTest.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FollowUpCountdownNotifier.kt`
- Verify: `app/src/test/java/com/example/focus_app/service/FollowUpCountdownDeadlineTest.kt`

**Interfaces:**
- Consumes: `AndroidFollowUpCountdownNotifier.show(sessionId: Long, delayMillis: Long)`。
- Produces: 新频道 `focus_follow_up_countdown_v2`，名称仍为「稍后提醒倒计时」，重要性为 `IMPORTANCE_DEFAULT`，无声音、无振动；通知继续使用 `setUsesChronometer(true)` 和倒计时模式。

- [ ] **Step 1: 写入失败的真机通知频道测试**

创建 Android instrumentation test，在授予通知权限的真机上调用 notifier 后检查新频道：

```kotlin
val notifier = AndroidFollowUpCountdownNotifier(context)
notifier.show(sessionId = 937L, delayMillis = 300_000L)
val channel = manager.getNotificationChannel("focus_follow_up_countdown_v2")
assertNotNull(channel)
assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel!!.importance)
assertEquals(null, channel.sound)
assertFalse(channel.shouldVibrate())
notifier.cancel(937L)
```

- [ ] **Step 2: 运行测试确认其因新频道不存在失败**

先执行：`adb shell pm grant com.example.focus_app android.permission.POST_NOTIFICATIONS`

再运行：`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.service.FollowUpCountdownNotifierTest`

Expected: 新频道查询为 `null`，证明旧实现仍在创建低重要性 `focus_follow_up_countdown`。

- [ ] **Step 3: 写入最小通知频道改动**

将 notifier 的频道 ID 改为 `focus_follow_up_countdown_v2`，`NotificationChannel` 重要性改为 `NotificationManager.IMPORTANCE_DEFAULT`，通知 priority 改为 `NotificationCompat.PRIORITY_DEFAULT`，删除 `setSilent(true)`。保留频道的 `setSound(null, null)`、`enableVibration(false)`、`setOnlyAlertOnce(true)`、`setOngoing(true)` 和所有权限保护。

- [ ] **Step 4: 重新运行通知测试和既有 deadline 测试**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.service.FollowUpCountdownNotifierTest testDebugUnitTest --tests com.example.focus_app.service.FollowUpCountdownDeadlineTest`

Expected: 新频道存在且为正常重要性、无声音无振动；两个 deadline 测试也通过。

### Task 4: 将「回到任务」定向到任务标签页

**Files:**
- Create: `app/src/test/java/com/example/focus_app/ui/navigation/MainNavigationRequestTest.kt`
- Modify: `app/src/main/java/com/example/focus_app/MainActivity.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt`
- Modify: `app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt`

**Interfaces:**
- Consumes: `MainActivity.ACTION_OPEN_TASKS`、`NavGraph(openTasksRequestId: Int)`。
- Produces: `mainStartDestination(onboardingDone: Boolean, openTasksRequested: Boolean): String`，已完成引导且带任务请求时返回 `Screen.Tasks.route`；`AndroidReminderLauncher.returnToFocus` 使用 `ACTION_OPEN_TASKS`。

- [ ] **Step 1: 写入失败的导航起点测试**

创建 `MainNavigationRequestTest.kt`：

```kotlin
@Test
fun task_request_starts_on_tasks_after_onboarding() {
    assertEquals(Screen.Tasks.route, mainStartDestination(true, true))
}

@Test
fun task_request_does_not_bypass_onboarding() {
    assertEquals(Screen.Onboarding.route, mainStartDestination(false, true))
}

@Test
fun ordinary_launch_starts_on_home_after_onboarding() {
    assertEquals(Screen.Home.route, mainStartDestination(true, false))
}
```

- [ ] **Step 2: 运行测试确认函数尚不存在**

Run: `./gradlew testDebugUnitTest --tests com.example.focus_app.ui.navigation.MainNavigationRequestTest`

Expected: 编译失败，未解析 `mainStartDestination`；这定义新导航请求的最小 API。

- [ ] **Step 3: 写入最小导航实现**

在 `MainActivity` 增加公开常量和请求计数：

```kotlin
companion object { const val ACTION_OPEN_TASKS = "com.example.focus_app.action.OPEN_TASKS" }
private var openTasksRequestId by mutableIntStateOf(0)

private fun consumeNavigationIntent(intent: Intent) {
    if (intent.action == ACTION_OPEN_TASKS) openTasksRequestId += 1
}
```

在 `onCreate` 的 `setContent` 前调用 `consumeNavigationIntent(intent)`，实现 `onNewIntent` 调用 `setIntent(intent)` 与它，并将 `NavGraph()` 改为 `NavGraph(openTasksRequestId)`。

在 `NavGraph.kt` 定义 `mainStartDestination`，使用它创建 `NavHost` 起点；新增 `LaunchedEffect(openTasksRequestId)`，当已完成引导且请求次数大于零时，以 `launchSingleTop = true` 导航 `Screen.Tasks.route`，以处理已存在 `MainActivity` 的新 Intent。

在 `ReminderLauncher.returnToFocus` 的 MainActivity Intent 设定 `action = MainActivity.ACTION_OPEN_TASKS`，保留现有 flags 和 `ACTIVE_TASK_ID` extra。将 `ReminderOverlay` 的「回到任务」回调固定为 `viewModel.returnToFocus`。把 `ReminderViewModelTest` 的旧「配置返回桌面」测试改为覆盖本按钮路径不读取 `ReturnDestination`。

- [ ] **Step 4: 运行导航与 ViewModel 测试确认通过**

Run: `./gradlew testDebugUnitTest --tests com.example.focus_app.ui.navigation.MainNavigationRequestTest --tests com.example.focus_app.ui.reminder.ReminderViewModelTest`

Expected: 所有筛选测试通过；普通启动仍为首页，任务请求为任务页，引导未完成时保持引导页。

### Task 5: 集成构建与 RMX3350 真机回归

**Files:**
- Verify only: 上述生产与测试文件。

**Interfaces:**
- Consumes: Debug APK 和已连接 serial `IJCES4XO8XA6SGK7`。
- Produces: 不清除用户数据的覆盖安装和四项可观察验收结果。

- [ ] **Step 1: 运行完整的本轮 JVM 测试与 AndroidTest 编译**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin`

Expected: 命令以 exit code 0 完成。

- [ ] **Step 2: 构建 Debug APK**

Run: `./gradlew assembleDebug`

Expected: `app/build/outputs/apk/debug/app-debug.apk` 生成，命令 exit code 0。

- [ ] **Step 3: 覆盖安装且不清除用户数据**

Run: `D:\Program\Android\SDK\platform-tools\adb.exe -s IJCES4XO8XA6SGK7 install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: 输出 `Success`。不得使用 `-c`、`uninstall` 或任何清除数据命令。

- [ ] **Step 4: 在真机按四项验收脚本回归**

1. 从首个提醒和后续提醒各观察一次：卡片边缘与屏幕边缘重合，目标应用不可见。
2. 依次点击「有目的使用」和「休息一下」：菜单从被点击按钮位置展开；选择 5 分钟后各自关闭提醒并安排倒计时。
3. 下拉系统通知栏：存在「稍后提醒倒计时」通知，显示递减剩余时间，无声音/震动；确认新频道为正常重要性。
4. 触发提醒后点击「回到任务」：Focus 打开，底部「任务」标签为选中状态；不再停在首页。

- [ ] **Step 5: 收集新鲜证据后汇报**

记录每条 Gradle 命令的 exit code、`adb install -r` 的 `Success`、新频道的 `dumpsys notification` 属性，以及四项真机观察的实际结果。任何一项未通过时，保留该项为未完成并继续按复现证据修复。
