# Three Reminder Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将提醒弹窗改为可配置稳定随机顺序的“回到任务 / 有目的使用 / 休息一下”三个动作，并让两个计时动作复用现有稍后提醒与倒计时通知。

**Architecture:** 纯 Kotlin 顺序策略负责六种排列，SharedPreferences store 负责默认开启的用户设置，ViewModel 负责动作语义，Compose 只渲染顺序和管理菜单状态。所有计时行为进入同一个私有调度函数。

**Tech Stack:** Kotlin、Jetpack Compose Material3、StateFlow、SharedPreferences、Hilt、JUnit4

**Spec:** `docs/superpowers/specs/2026-09-01-three-reminder-actions-design.md`

## Global Constraints

- 三个可见动作只有 RETURN、INTENTIONAL、REST；Ignore 不可成为按钮。
- 完整六排列默认开启；同一 intervention 不可在重组、旋转或重建时换序。
- 计时动作继续使用 1/5/10/自定义 1..60 分钟。
- 不增加第二套调度器、Room 迁移或全局休息状态。

---

### Task 1: 稳定六排列与默认开启设置

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderActionOptions.kt`
- Create: `app/src/main/java/com/example/focus_app/data/reminder/ReminderActionOrderStore.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/reminder/ReminderActionOrderTest.kt`

**Interfaces:**
- Produces: `ReminderDecisionAction`、`reminderActionOrder(randomize, seed)`、`ReminderActionOrderStore.randomizeEnabled`。

- [ ] 写失败测试：固定顺序、相同 seed 稳定、输出仅为六种完整排列。
- [ ] 运行 `ReminderActionOrderTest` 并确认因类型/函数缺失失败。
- [ ] 写最小顺序策略和默认 true 的 SharedPreferences store。
- [ ] 重跑定向测试并确认通过。

### Task 2: 三项动作的 ViewModel 语义

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt`

**Interfaces:**
- Produces: `returnToConfiguredDestination()`、`useIntentionally()`、`takeBreak()`；`ReminderUiState.randomizeActions`。
- Consumes: Task 1 的设置 store 和现有 `SessionReminderScheduler.scheduleFollowUp()`。

- [ ] 写失败测试：5 分钟 intentional 记录 `intentional_5m`；rest 记录 `rest_5m`；两者均保存截止时间并调度 300000ms；配置 HOME 时 RETURN 分派到 home。
- [ ] 运行 `ReminderViewModelTest` 并确认缺少行为导致失败。
- [ ] 抽取单一计时函数并实现三个公开 intent 方法。
- [ ] 重跑定向测试并确认通过。

### Task 3: Compose 三按钮与设置开关

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: Tasks 1-2 的动作顺序、UI state 与 callbacks。
- Produces: 三个纵向按钮、两个时长菜单、动作相关自定义对话框、默认开启的设置开关。

- [ ] 将旧双按钮区域替换为按 `reminderActionOrder()` 渲染的三按钮区域。
- [ ] 用 `rememberSaveable` 保存当前计时菜单动作、自定义动作和输入值；顺序只从稳定 seed 派生。
- [ ] 在设置页加入“随机排列提醒按钮”开关，并连接 store。
- [ ] 编译定向单元测试，确认 Hilt 与 Compose 接口完整。

### Task 4: 回归与交付证据

**Files:**
- Modify: `docs/DEVELOPMENT_LOG.md`

**Interfaces:**
- Consumes: Tasks 1-3 的实现。
- Produces: 单元测试、构建、差异检查和真机验证边界。

- [ ] 运行提醒 UI 与设置映射定向测试。
- [ ] 运行 `testDebugUnitTest`。
- [ ] 运行 `assembleDebug`。
- [ ] 运行 `git diff --check` 并审查 `git status --short`。
- [ ] 记录尚未完成的真机三按钮布局、通知权限与 ColorOS 到点验证。
