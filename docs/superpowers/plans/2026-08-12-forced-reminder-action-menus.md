# 强制提醒与双下拉操作实施计划

> **供代理执行：** 按任务逐项使用 `superpowers:executing-plans` 实施。每个任务均以复选框记录进度，并遵循测试先行。

**目标：** 让提醒弹窗支持全局强制模式、退出目标应用下拉菜单，以及 1/5/10 分钟和自定义分钟数的稍后提醒。

**架构：** 在 Room 设置中新增 `forceReminder` 布尔值，并让调度器把该值带入 `ReminderLaunchData`。提醒界面由一个纯 Kotlin 选项策略提供菜单数据；ViewModel 只接收已选目的地或已选分钟数。活动和展示注册表根据强制标记决定返回、Home 和失焦后的待处理提醒是否继续保留。

**技术栈：** Kotlin、Jetpack Compose Material 3、Room、Hilt、Kotlin Coroutines、JUnit 4、AndroidX Room MigrationTestHelper。

## 全局约束

- 强制提醒默认关闭；数据库从版本 3 升级时旧用户必须得到 `false`。
- 提醒界面只有两个下拉入口：`退出目标应用`、`稍后提醒`。
- 退出菜单固定包含返回桌面、返回 Focus；仅在已指定返回应用时显示第三项“打开指定应用”。
- 稍后提醒固定提供 1、5、10 分钟和 1–120 分钟的自定义输入。
- 不新增前台服务、轮询、AI 请求或新的网络依赖；继续复用单次协程调度和会话校验。
- 不提交 `.idea/`、`.gradle-user-home/`、`build/`、`local.properties` 或 `.superpowers/`。

---

### 任务 1：持久化强制提醒设置和数据库迁移

**文件：**
- 新建：`app/src/main/java/com/example/focus_app/data/local/migration/Migration3To4.kt`
- 新建：`app/src/androidTest/java/com/example/focus_app/data/local/Migration3To4Test.kt`
- 修改：`app/src/main/java/com/example/focus_app/data/local/AppDatabase.kt`
- 修改：`app/src/main/java/com/example/focus_app/di/DatabaseModule.kt`
- 修改：`app/src/main/java/com/example/focus_app/data/local/entity/SettingsEntity.kt`
- 修改：`app/src/main/java/com/example/focus_app/data/repository/SettingsRepository.kt`
- 修改：`app/src/main/java/com/example/focus_app/data/repository/SettingsMapper.kt`
- 测试：`app/src/test/java/com/example/focus_app/data/repository/SettingsMappingTest.kt`

**接口：**
- 消费：现有 `SettingsRepository.update(transform: (AppSettings) -> AppSettings)`。
- 产出：`AppSettings.forceReminder: Boolean`、`SettingsEntity.forceReminder: Boolean`、`MIGRATION_3_4`。

- [ ] **步骤 1：先写失败的映射与迁移测试**

```kotlin
@Test
fun force_reminder_round_trips_through_settings_mapping() {
    val mapped = settings.copy(forceReminder = true).toEntity(entity).toAppSettings()
    assertTrue(mapped.forceReminder)
}

@Test
fun migration_3_to_4_sets_force_reminder_off_for_existing_settings() {
    // 创建第 3 版 settings 行，执行 MIGRATION_3_4，断言 forceReminder 为 0。
}
```

- [ ] **步骤 2：确认测试因字段和迁移不存在而失败**

运行：

```powershell
cd C:\Users\6\Documents\Codex\2026-08-04\brucehuang-cn-focus-app-https-github\work\Focusapp-v01\.worktrees\guardian-persistence
.\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.data.repository.SettingsMappingTest"
```

预期：`forceReminder` 或 `MIGRATION_3_4` 未定义导致失败。

- [ ] **步骤 3：最小实现持久化链路**

```kotlin
// SettingsEntity.kt
@ColumnInfo(defaultValue = "0") val forceReminder: Boolean = false

// AppSettings
val forceReminder: Boolean = false

// Migration3To4.kt
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN forceReminder INTEGER NOT NULL DEFAULT 0")
    }
}
```

把该值加入 `toAppSettings()` 与 `toEntity()`；把数据库版本改为 `4`，并在 `DatabaseModule` 注册 `MIGRATION_3_4`。

- [ ] **步骤 4：确认绿色并编译迁移测试**

运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache
```

预期：构建成功。

- [ ] **步骤 5：提交本任务**

```powershell
git add app/src/main/java/com/example/focus_app/data/local app/src/main/java/com/example/focus_app/data/repository app/src/main/java/com/example/focus_app/di/DatabaseModule.kt app/src/test/java/com/example/focus_app/data/repository/SettingsMappingTest.kt app/src/androidTest/java/com/example/focus_app/data/local/Migration3To4Test.kt
git commit -m "feat: persist forced reminder setting"
```

### 任务 2：建立提醒操作选项并改为显式稍后提醒

**文件：**
- 新建：`app/src/main/java/com/example/focus_app/ui/reminder/ReminderActionOptions.kt`
- 新建：`app/src/test/java/com/example/focus_app/ui/reminder/ReminderActionOptionsTest.kt`
- 修改：`app/src/main/java/com/example/focus_app/service/ReminderLaunchData.kt`
- 修改：`app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt`
- 修改：`app/src/main/java/com/example/focus_app/service/ReminderScheduler.kt`
- 修改：`app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt`
- 修改：`app/src/test/java/com/example/focus_app/service/ReminderSchedulerTest.kt`
- 修改：`app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt`
- 删除：`app/src/main/java/com/example/focus_app/data/followup/FollowUpReminderStore.kt`

**接口：**
- 消费：`ReminderLauncher.returnToFocus`、`returnHome`、`returnToCustom` 与 `SessionReminderScheduler.scheduleFollowUp`。
- 产出：`presetSnoozeMinutes`、`exitDestinations(customPackageName)`、`parseCustomSnoozeMinutes(raw)`、`ReminderViewModel.snooze(sessionId, minutes, onComplete)`。

- [ ] **步骤 1：写失败的选项与 ViewModel 测试**

```kotlin
@Test
fun custom_return_destination_is_offered_only_for_a_configured_package() {
    assertEquals(listOf(HOME, FOCUS), exitDestinations(""))
    assertEquals(listOf(HOME, FOCUS, CUSTOM), exitDestinations("com.tencent.mm"))
}

@Test
fun custom_snooze_accepts_only_one_to_120_minutes() {
    assertEquals(17, parseCustomSnoozeMinutes("17"))
    assertNull(parseCustomSnoozeMinutes("0"))
    assertNull(parseCustomSnoozeMinutes("121"))
}

@Test
fun snooze_uses_the_minutes_selected_in_the_overlay() = runTest {
    fixture.viewModel.snooze(LAUNCH_DATA.sessionId, 5)
    assertEquals(300_000L, fixture.scheduler.followUpDelay)
}
```

- [ ] **步骤 2：确认红灯**

运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.ui.reminder.ReminderActionOptionsTest" --tests "com.example.focus_app.ui.reminder.ReminderViewModelTest"
```

预期：选项策略和 `snooze` 方法不存在导致失败。

- [ ] **步骤 3：最小实现操作数据和调度参数**

```kotlin
internal val presetSnoozeMinutes = listOf(1, 5, 10)

internal fun exitDestinations(customPackageName: String): List<ReturnDestination> =
    buildList {
        add(ReturnDestination.HOME)
        add(ReturnDestination.FOCUS)
        if (customPackageName.isNotBlank()) add(ReturnDestination.CUSTOM)
    }

internal fun parseCustomSnoozeMinutes(raw: String): Int? =
    raw.trim().toIntOrNull()?.takeIf { it in 1..120 }
```

删除 `FollowUpReminderStore` 注入和设置读取；`snooze` 在成功范围内隐藏展示记录、记录 `snoozed_<minutes>m` 用户行动，并调用 `scheduleFollowUp(sessionId, minutes * 60_000L)`。在 `ReminderLaunchData` 增加 `forceReminder`，由 `ReminderScheduler` 从 `AppSettings.forceReminder` 填入，并由 `AndroidReminderLauncher` 通过 Intent Extra 传给活动。

- [ ] **步骤 4：确认绿色与既有会话保护**

运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.ui.reminder.ReminderActionOptionsTest" --tests "com.example.focus_app.ui.reminder.ReminderViewModelTest" --tests "com.example.focus_app.service.ReminderSchedulerTest"
```

预期：构建成功；关闭会话后的后续提醒仍不展示。

- [ ] **步骤 5：提交本任务**

```powershell
git add app/src/main/java/com/example/focus_app/ui/reminder app/src/main/java/com/example/focus_app/service app/src/test/java/com/example/focus_app/ui/reminder app/src/test/java/com/example/focus_app/service/ReminderSchedulerTest.kt
git rm app/src/main/java/com/example/focus_app/data/followup/FollowUpReminderStore.kt
git commit -m "feat: add reminder action menus"
```

### 任务 3：实现强制展示策略与两组弹窗下拉菜单

**文件：**
- 修改：`app/src/main/java/com/example/focus_app/service/ReminderPresentationRegistry.kt`
- 修改：`app/src/main/java/com/example/focus_app/service/ReminderPresentationPolicy.kt`
- 修改：`app/src/main/java/com/example/focus_app/service/ReminderActivity.kt`
- 修改：`app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt`
- 新建：`app/src/test/java/com/example/focus_app/service/ReminderPresentationRegistryTest.kt`
- 修改：`app/src/test/java/com/example/focus_app/service/ReminderPresentationPolicyTest.kt`

**接口：**
- 消费：`ReminderLaunchData.forceReminder`、`ReminderPresentationRegistry.hide(sessionId)`、`ReminderViewModel.returnToFocus`、`returnHome`、`returnToCustom`、`snooze`。
- 产出：`ReminderPresentationRegistry.onActivityDestroyed(sessionId)`，以及 `shouldKeepReminderPending(forceReminder, explicitAction)`。

- [ ] **步骤 1：写失败的强制展示策略测试**

```kotlin
@Test
fun forced_reminder_survives_activity_destruction_without_an_explicit_action() {
    registry.show(sessionId = 9L, forceReminder = true)
    registry.onActivityDestroyed(9L)
    assertTrue(registry.isShowing())
}

@Test
fun normal_reminder_is_cleared_when_its_activity_is_destroyed() {
    registry.show(sessionId = 9L, forceReminder = false)
    registry.onActivityDestroyed(9L)
    assertFalse(registry.isShowing())
}
```

- [ ] **步骤 2：确认红灯**

运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.service.ReminderPresentationRegistryTest" --tests "com.example.focus_app.service.ReminderPresentationPolicyTest"
```

预期：新的注册表方法和展示策略未定义导致失败。

- [ ] **步骤 3：最小实现强制待处理状态和界面**

```kotlin
internal fun shouldKeepReminderPending(forceReminder: Boolean, explicitAction: Boolean): Boolean =
    forceReminder && !explicitAction
```

注册表保存当前会话的强制标记；`onActivityDestroyed` 仅在普通模式清理。活动的返回回调在强制模式直接消费事件，普通模式则结束活动；会话结束广播始终先清理注册表再关闭活动。`onDestroy` 改为调用注册表的销毁处理，不直接无条件 `hide`。

在 `ReminderOverlay` 使用两个 `DropdownMenu`：退出菜单根据 `exitDestinations` 渲染桌面、Focus 和可选指定应用；稍后菜单渲染预设时间和“自定义分钟数”。自定义项打开数字输入 `AlertDialog`，只有 `parseCustomSnoozeMinutes` 成功才调用 `snooze`；取消或非法输入保持提醒活动打开。

- [ ] **步骤 4：确认绿色**

运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.service.ReminderPresentationRegistryTest" --tests "com.example.focus_app.service.ReminderPresentationPolicyTest" --tests "com.example.focus_app.ui.reminder.ReminderViewModelTest"
```

预期：构建成功；强制模式不会因非明确行动清理待处理提醒。

- [ ] **步骤 5：提交本任务**

```powershell
git add app/src/main/java/com/example/focus_app/service/ReminderPresentationRegistry.kt app/src/main/java/com/example/focus_app/service/ReminderPresentationPolicy.kt app/src/main/java/com/example/focus_app/service/ReminderActivity.kt app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt app/src/test/java/com/example/focus_app/service app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt
git commit -m "feat: enforce explicit reminder actions"
```

### 任务 4：清理设置页旧入口并接入强制提醒开关

**文件：**
- 修改：`app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt`
- 修改：`app/src/main/java/com/example/focus_app/ui/settings/SettingsScreen.kt`
- 修改：`app/src/test/java/com/example/focus_app/ui/settings/SettingsViewModelDetectionTest.kt`
- 修改：`app/src/test/java/com/example/focus_app/ui/settings/SettingsTestFixtures.kt`

**接口：**
- 消费：`SettingsRepository.update`、`AppSettings.forceReminder`、`CustomReturnAppStore`。
- 产出：`SettingsViewModel.setForceReminder(enabled: Boolean)`；设置页中的强制提醒开关和始终可见的指定返回应用入口。

- [ ] **步骤 1：写失败的设置 ViewModel 测试**

```kotlin
@Test
fun setting_force_reminder_persists_only_the_force_reminder_field() = runTest {
    viewModel.setForceReminder(true)
    advanceUntilIdle()
    assertTrue(repository.getSettings().forceReminder)
}
```

- [ ] **步骤 2：确认红灯**

运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.ui.settings.SettingsViewModelDetectionTest"
```

预期：`setForceReminder` 未定义，且旧的后续提醒测试将在依赖删除后提示需要迁移。

- [ ] **步骤 3：最小实现设置调整**

```kotlin
fun setForceReminder(enabled: Boolean) {
    update { it.copy(forceReminder = enabled) }
}
```

从 ViewModel 删除 `FollowUpReminderStore`、`followUpInterval`、`updateFollowUpInterval` 和 `updateReturnDestination`。设置页面删除“再次提醒间隔”和返回目的地单选组；新增“强制提醒”开关及说明，并始终展示“指定返回应用”入口。选择器未配置时提示“未指定；弹窗不显示第三个退出目的地”。

- [ ] **步骤 4：确认绿色**

运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache
```

预期：构建成功，旧设置入口已无生产引用。

- [ ] **步骤 5：提交本任务**

```powershell
git add app/src/main/java/com/example/focus_app/ui/settings app/src/test/java/com/example/focus_app/ui/settings
git commit -m "feat: configure forced reminder behavior"
```

### 任务 5：完整验证、真机测试说明和任务状态更新

**文件：**
- 修改：`docs/PROJECT_TASKS.md`
- 修改：`DEVELOPMENT_LOG.md`

**接口：**
- 消费：前四项提交的持久化、提醒操作、展示策略和设置界面。
- 产出：已完成的任务清单记录、真机回归步骤和完整构建证据。

- [ ] **步骤 1：完成自动化验证**

运行：

```powershell
$env:GRADLE_USER_HOME='C:\Users\6\.gradle'
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug --no-daemon --no-configuration-cache
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 2：在已连接真机上验证迁移**

运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --no-configuration-cache -Pandroid.testInstrumentationRunnerArguments.class=com.example.focus_app.data.local.Migration3To4Test
```

预期：`Migration3To4Test` 通过；若未连接设备，记录为待用户在 Android Studio 执行的验证项，不把它误报为完成。

- [ ] **步骤 3：在 Android Studio 中执行手动回归**

1. 关闭强制提醒，打开目标 App，确认返回键可按普通模式关闭提醒。
2. 开启强制提醒，打开目标 App，分别按返回键、Home 键和触发一条短暂系统窗口；确认待处理提醒未被清除或重复计费。
3. 在退出下拉菜单分别选择桌面、Focus；配置指定应用后确认第三项出现且跳转成功。
4. 在稍后提醒菜单选择 1、5、10 分钟和自定义 17 分钟；离开目标 App 后等待，确认不再错误弹窗。

- [ ] **步骤 4：更新文档并提交**

在 `docs/PROJECT_TASKS.md` 勾选自动化已验证项，在 `DEVELOPMENT_LOG.md` 写入变更与真实验证边界；只有成功完成的项才标为完成。

```powershell
git add docs/PROJECT_TASKS.md DEVELOPMENT_LOG.md
git commit -m "docs: record forced reminder verification"
```
