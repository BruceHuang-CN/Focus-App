# 稍后提醒通知栏倒计时设计

## 目标

在不改变现有稍后提醒可靠性链路的前提下，当用户选择 1、5、10 分钟或自定义时长后，显示一个通知栏倒计时；倒计时到期仍由现有 `HybridFollowUpScheduler`、`AlarmManager` 和 `WorkManager` 触发并经过前台应用复核后展示提醒。

## 行为

- 用户选择稍后提醒后，先沿用现有逻辑持久化 `snoozeUntil`，再调用现有调度入口。
- 调度入口同时发布低打扰倒计时通知，显示“稍后提醒倒计时”，并由 Android 系统计时器显示剩余时间。
- 通知只负责展示，不负责触发提醒；禁止每秒启动协程、Worker 或反复更新通知。
- 用户取消会话、离开目标应用导致会话关闭、关闭守护，或到期开始执行时，移除对应倒计时通知。
- 进程重启后，`PendingFollowUpRestorer` 继续从 `snoozeUntil` 恢复调度；恢复调度时也恢复倒计时通知。
- 通知权限关闭、通知渠道被用户禁用或系统拒绝发布时，保持静默失败，不能影响现有到点弹窗链路。
- 到期设备锁定或前台状态未知时，现有执行器继续重试；到期倒计时通知先移除，不显示负数或把通知本身当作重试调度器。

## 技术方案

- 新增 `FollowUpCountdownNotifier` 边界及 Android 实现。
- Android 实现使用 `NotificationCompat.Builder.setWhen(dueAt)`、`setUsesChronometer(true)` 和 `setChronometerCountDown(true)`，让 System UI 自行绘制倒计时。
- 使用独立、低重要度通知渠道；使用通知 tag 隔离倒计时通知与已有高优先级召回通知的 ID 空间。
- `HybridFollowUpScheduler.schedule()` 发布或更新通知，`cancel()` 删除通知。
- `FollowUpReminderExecutor.execute()` 在任何到期决策前删除通知，以覆盖协程、AlarmManager 与 WorkManager 三条执行入口。

## 非目标

- 不使用 Android 16 Live Update API，以保持当前 `minSdk=26` 的简单兼容行为。
- 不在通知中增加取消、延长或直接操作按钮。
- 不修改现有提醒额度、锁屏重试、前台应用判断和弹窗展示策略。
- 本阶段不修改提醒弹窗的三个行为按钮。

## 验收

- 调度 5 分钟稍后提醒时，通知边界收到相同的 5 分钟延迟。
- 取消、到期 SHOW、到期 SKIP、到期 RETRY 都不会留下过期倒计时通知。
- 应用重启恢复 `snoozeUntil` 时，会重新发布剩余时间的倒计时。
- 通知不可用时，现有调度仍继续执行。
- 相关单元测试、完整 `testDebugUnitTest` 和 Debug 构建通过。
