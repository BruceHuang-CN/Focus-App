# 预设应用组与首页守护开关 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户管理非空的自定义应用组、一次启用一组，并通过首页开关暂停或恢复 Focus 守护。

**Architecture:** 应用组保存在独立的 SharedPreferences JSON 存储中，现有 Room `settings.targetApps` 始终镜像启用组，因此原有检测与 AI 缓存链路继续工作。守护开关保存于 Room 设置；切换组或关闭守护都通过一个用例先关闭旧会话和待提醒，再更新状态，避免旧组提醒穿透。

**Tech Stack:** Kotlin、Jetpack Compose Material3、Room、SharedPreferences、Hilt、Kotlin Flow、JUnit。

## Global Constraints

- 不新增常驻服务、定时轮询或支付/反馈后端。
- 应用组不得为空；最后一个应用组不得删除。
- 守护关闭时不调用无障碍服务 `disableSelf()`，不删除历史统计。
- 每项逻辑先写会失败的单元测试，再写最小实现。
- 每个可独立验证的任务单独提交；不提交 `.idea` 文件。

---

### Task 1: 持久化守护开关并迁移已有安装

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/data/local/entity/SettingsEntity.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/example/focus_app/data/local/migration/Migration2To3.kt`
- Modify: `app/src/main/java/com/example/focus_app/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/SettingsMapper.kt`
- Test: `app/src/test/java/com/example/focus_app/data/local/migration/Migration2To3Test.kt`
- Test: `app/src/test/java/com/example/focus_app/data/repository/SettingsMappingTest.kt`

**Interfaces:**
- Produces `AppSettings.guardianEnabled: Boolean`，默认 `true`。
- Produces `SettingsRepository.setGuardianEnabled(enabled: Boolean): suspend Unit`。

- [x] **Step 1: 写失败的映射与迁移测试**

```kotlin
@Test fun version_2_database_migrates_guardian_enabled_to_true() {
    migration.migrate(database)
    assertEquals(1, database.query("SELECT guardianEnabled FROM settings").singleInt())
}

@Test fun guardian_enabled_round_trips_through_settings_entity() {
    assertFalse(AppSettings(guardianEnabled = false).toEntity(existing).toAppSettings().guardianEnabled)
}
```

- [x] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests *Migration2To3Test --tests *SettingsMappingTest`

- [x] **Step 3: 最小实现持久化**

```kotlin
// SettingsEntity
@ColumnInfo(defaultValue = "1") val guardianEnabled: Boolean = true

// MIGRATION_2_3
database.execSQL("ALTER TABLE settings ADD COLUMN guardianEnabled INTEGER NOT NULL DEFAULT 1")
```

将 Room 版本改为 3，注册 `MIGRATION_2_3`，并在 `toAppSettings` / `toEntity` 传递该字段；仓库方法只更新此字段。

- [x] **Step 4: 运行测试确认通过并提交**

Run: `./gradlew.bat testDebugUnitTest --tests *Migration2To3Test --tests *SettingsMappingTest`

Commit: `feat: persist guardian enabled state`

### Task 2: 实现可迁移的非空应用组存储

**Files:**
- Create: `app/src/main/java/com/example/focus_app/data/appgroup/AppGroup.kt`
- Create: `app/src/main/java/com/example/focus_app/data/appgroup/AppGroupStore.kt`
- Create: `app/src/main/java/com/example/focus_app/data/appgroup/AppGroupRepository.kt`
- Modify: `app/src/main/java/com/example/focus_app/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/example/focus_app/data/appgroup/AppGroupRepositoryTest.kt`

**Interfaces:**
- Produces `data class AppGroup(val id: String, val name: String, val apps: List<AppInfo>)`。
- Produces `AppGroupRepository.groups: StateFlow<List<AppGroup>>` 和 `activeGroupId: StateFlow<String>`。
- Consumes `SettingsRepository.getSettings()` 只用于首次迁移。
- `create(name, apps)`, `update(id, name, apps)`, `delete(id)`, `activate(id)` 均拒绝空 App 列表；`delete` 在仅剩一组时拒绝。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test fun first_read_migrates_current_target_apps_into_one_active_group()
@Test fun create_rejects_empty_app_list()
@Test fun delete_rejects_the_last_group()
@Test fun update_removes_duplicate_packages_preserving_first_app_name()
```

测试用内存型 `SharedPreferences` fake；期望迁移组名为“当前应用组”，并且只执行一次。

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests *AppGroupRepositoryTest`

- [ ] **Step 3: 最小实现 JSON 存储**

使用 Gson 序列化一个包含 `groups` 与 `activeGroupId` 的数据对象。所有写入先执行 `normalizeApps(apps)`，按包名去重；名称使用 `trim().take(24)`，为空时拒绝。通过 `@ApplicationContext` 创建名为 `focus_app_groups` 的 SharedPreferences，不增加后台监听或服务。

- [ ] **Step 4: 运行测试确认通过并提交**

Run: `./gradlew.bat testDebugUnitTest --tests *AppGroupRepositoryTest`

Commit: `feat: add persistent app groups`

### Task 3: 原子切换应用组与守护状态

**Files:**
- Create: `app/src/main/java/com/example/focus_app/domain/usecase/UpdateGuardianStateUseCase.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AppSessionCoordinator.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/example/focus_app/domain/usecase/UpdateGuardianStateUseCaseTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/AppSessionCoordinatorTest.kt`

**Interfaces:**
- Produces `AppSessionCoordinator.stopCurrentSession(): suspend Unit`。
- Produces `UpdateGuardianStateUseCase.activateGroup(groupId: String): Result<Unit>` 与 `setGuardianEnabled(enabled: Boolean): Unit`。
- Consumes `AppGroupRepository`, `SettingsRepository`, `AppSessionCoordinator`, `AppSessionRepository`, `ReminderCacheRepository`。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test fun activating_group_closes_old_session_then_mirrors_its_apps_and_resets_quota()
@Test fun activating_group_requests_cache_regeneration()
@Test fun disabling_guardian_closes_current_session_without_disabling_accessibility()
```

断言顺序为 `stopCurrentSession` → 写入 `targetApps` / `guardianEnabled` → `resetReminderQuota` → `requestRegeneration`；额度起点为 `now - reminderWindowMinutes * 60_000L`。

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests *UpdateGuardianStateUseCaseTest --tests *AppSessionCoordinatorTest`

- [ ] **Step 3: 最小实现切换用例**

`stopCurrentSession()` 复用协调器现有的互斥锁，取消 scheduler、关闭打开会话并清空前台包。启用组时从仓库读取并校验非空组，停止旧会话后调用 `settingsRepository.update { it.copy(targetApps = group.apps) }`；再重置当前窗口额度并请求缓存刷新。关闭守护同样先停止会话，再写 `guardianEnabled = false`。

- [ ] **Step 4: 运行测试确认通过并提交**

Run: `./gradlew.bat testDebugUnitTest --tests *UpdateGuardianStateUseCaseTest --tests *AppSessionCoordinatorTest`

Commit: `feat: switch app groups safely`

### Task 4: 在检测服务中尊重守护开关并节省资源

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/MainActivity.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AppDetectionService.kt`
- Test: `app/src/test/java/com/example/focus_app/service/AccessibilityMonitoringPolicyTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/AppDetectionServicePolicyTest.kt`

**Interfaces:**
- `shouldProcessAccessibilityEvents(settings)` 必须额外要求 `settings.guardianEnabled`。
- `KeepAliveState` 包含 `guardianEnabled`；关闭时停止兼容检测和 keep-alive 服务。

- [ ] **Step 1: 写失败策略测试**

```kotlin
@Test fun realtime_events_are_ignored_when_guardian_is_disabled()
@Test fun compatibility_service_stops_when_guardian_is_disabled()
@Test fun guardian_toggle_does_not_change_enable_accessibility_setting()
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests *AccessibilityMonitoringPolicyTest --tests *AppDetectionServicePolicyTest`

- [ ] **Step 3: 最小实现服务策略**

将守护字段纳入 MainActivity 的 settings flow，并只有 `guardianEnabled == true` 时启动兼容/保活服务。无障碍入口只在守护开启时处理窗口事件。兼容服务的 flow 同时观察模式与守护字段，关闭时调用 `onPackageChanged(null)` 后 `stopSelf`；不改动系统无障碍服务本身。

- [ ] **Step 4: 运行测试确认通过并提交**

Run: `./gradlew.bat testDebugUnitTest --tests *AccessibilityMonitoringPolicyTest --tests *AppDetectionServicePolicyTest`

Commit: `feat: pause monitoring with guardian toggle`

### Task 5: 增加应用组管理页与首页守护开关

**Files:**
- Create: `app/src/main/java/com/example/focus_app/ui/settings/AppGroupsScreen.kt`
- Create: `app/src/main/java/com/example/focus_app/ui/settings/AppGroupEditorScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/settings/AppGroupEditorPolicyTest.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt`

**Interfaces:**
- `HomeUiState` 增加 `guardianEnabled: Boolean` 与 `activeGroupName: String`。
- `SettingsViewModel` 暴露 groups/activeGroup 并转发 `createGroup`, `renameGroup`, `saveGroup`, `deleteGroup`, `activateGroup`。
- 编辑页在保存前调用 `AppGroupEditorPolicy.canSave(name, apps)`。

- [ ] **Step 1: 写失败 UI 策略与状态测试**

```kotlin
@Test fun editor_cannot_save_without_any_selected_app()
@Test fun home_state_reflects_disabled_guardian_and_active_group_name()
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests *AppGroupEditorPolicyTest --tests *HomeViewModelGuardianTest`

- [ ] **Step 3: 最小 UI 实现**

设置页以“预设应用组 · 当前组名（N 个 App）”替代原“目标应用”入口。组管理页显示当前组“正在守护”徽标，并提供新增、编辑、删除和点击启用；最后一组的删除操作禁用。编辑页复用 `loadInstalledApps`、搜索和复选列表，空选择时显示错误且保存按钮禁用。首页状态卡增加 Switch：开时“守护已开启”，关时“已暂停检测和提醒”，切换调用 `setGuardianEnabled`。

- [ ] **Step 4: 编译、真机验证并提交**

Run: `./gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug --no-daemon`

真机：新建包含一个短视频 App 的组并启用；确认额度归零、旧弹窗消失；关闭守护后打开该 App 不提醒，重新开启后按延迟提醒；确认无障碍服务仍处于系统启用状态。

Commit: `feat: manage app groups from settings`

### Task 6: 更新日志与发布前验证

**Files:**
- Modify: `DEVELOPMENT_LOG.md`
- Test: existing full unit suite

- [ ] **Step 1: 记录实际实现与真机验证结果**

在 `DEVELOPMENT_LOG.md` 的 2026-08-10 条目中补充已实现功能、迁移版本和真机验证结果；不要把反馈二维码或赞助入口标记为完成。

- [ ] **Step 2: 运行全量验证**

Run: `./gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug --no-daemon`

- [ ] **Step 3: 只提交本次功能文件**

```powershell
git add app/src DEVELOPMENT_LOG.md
git commit -m "feat: add app groups and guardian toggle"
git status --short
```

确认 `.idea` 文件未被暂存；经用户要求后再执行 `git push github feature/focus-v0.1-preview`。
