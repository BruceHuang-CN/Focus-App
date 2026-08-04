# Focus v0.1 预览验证报告（模板）

更新时间：2026-08-04

> 本文件是 Task 11 的可复现验证模板。代码与测试已提交到 `feature/focus-v0.1-preview`，
> 构建与真机验证由开发者在 Android Studio / 真机完成，结果请填写到下面的章节。

## 1. 构建与单元测试

在 `Focusapp-v01` 根目录执行（或在 Android Studio 中直接运行）：

```powershell
./gradlew testDebugUnitTest assembleDebug
```

预期：`BUILD SUCCESSFUL`。

填写区：

- [ ] 单元测试通过，数量：______，失败：0
- [ ] Debug APK 已生成：`app/build/outputs/apk/debug/app-debug.apk`
- [ ] AndroidTest 编译通过：`./gradlew compileDebugAndroidTestKotlin`

## 2. 单元测试清单（重点）

- 提醒链路：`ReminderSchedulerTest`（触发验证、配额、取消、同一会话不重复提醒）
- 会话去重与协调：`AppSessionCoordinatorTest`
- DeepSeek 解析与兜底：`DeepSeekReminderProviderTest`、`PromptBuilderTest`
- 缓存与并发：`ReminderBatchCoordinatorTest`、`ReminderCacheRepositoryTest`
- 安全存储：`EncryptedApiKeyStoreTest`、`MigratingApiKeyStoreTest`
- 任务调度：`TaskRepositoryTest`、`ActiveTaskResolverTest`
- 设置映射与并发：`SettingsMappingTest`、`SettingsRepositoryConcurrencyTest`
- 统计聚合：`StatsAggregatorTest`（补零、跨午夜拆分、各应用占比）
- 养成系统：`StreakCalculatorTest`、`StreakStatusUseCaseTest`
- AI 连接测试：`AiRepositoryConnectionTest`（成功/401/404/空列表/网络失败）
- UI：`PresetSelectorTest`、`TaskEditorScreenTest`、`StatsChartsTest`（androidTest）

## 3. 真机验证清单

准备：安装 debug APK 到 Android 13+ 真机，配置一个目标 App 与一个带时间段的当前任务，
设置 DeepSeek API Key（真实 Key，仅保存在 Keystore）。

### 3.1 抖音 / 哔哩哔哩 / 小红书

- [ ] 打开目标 App 后，在设置的提醒延迟后弹出提醒（悬浮窗或通知）
- [ ] 提醒文案与当前任务、口吻一致（AI 在线生成）
- [ ] 点击「不刷了，回到 Focus / 桌面」返回正确位置，会话记为主动退出
- [ ] 点击「仍要使用」不返回，会话记为继续使用
- [ ] 同一会话只提醒一次，离开后不再弹出旧提醒
- [ ] 切换任务/时间段后，新提醒内容生效，统计窗口内次数不重置

### 3.2 检测方式

- [ ] 实时模式：开启无障碍服务后，打开目标 App 即时检测
- [ ] 兼容模式：仅使用情况访问 + 通知权限，前台服务通知显示
- [ ] 两种模式不会同时运行

### 3.3 离线兜底

- [ ] 断网或 API Key 无效时，仍展示本地兜底提醒，不崩溃

### 3.4 统计一致性

- [ ] 今天/7 天/30 天切换后，打开次数、提醒、主动退出、时长与真机操作一致
- [ ] 跨午夜会话的时长拆分正确
- [ ] 连续达标天数与今日完成任务数一致

## 4. 性能证据采集命令

连接真机后执行并记录输出：

```powershell
adb shell dumpsys meminfo com.example.focus_app
adb shell dumpsys batterystats com.example.focus_app
adb shell dumpsys alarm | Select-String -Pattern "focus"
adb shell dumpsys jobscheduler | Select-String -Pattern "focus"
adb shell dumpsys activity services | Select-String -Pattern "focus"
adb shell dumpsys package com.example.focus_app | Select-String -Pattern "permission"
```

填写区：

- [ ] 内存占用（PSS）：______ MB
- [ ] 唤醒锁 / 闹钟：______
- [ ] 后台任务 / JobScheduler：______
- [ ] 网络请求记录：仅提醒生成时调用 API，无固定轮询网络请求

## 5. 仓库审计结果（2026-08-04 已执行）

- [x] 主源码无真实 API Key（仅测试中出现 `sk-test` 假值）
- [x] 无 `killBackgroundProcesses` / `forceStopPackage` 等强制结束应用 API
- [x] `deepseek-chat` 仅存在于旧模型名迁移逻辑（`SettingsMapper.kt`），默认模型为 `deepseek-v4-flash`
- [x] 实时模式使用无障碍事件零轮询；兼容模式轮询间隔为 10 秒（非 5 秒）
- [x] `local.properties`、`*.apk`、`*.jks`、`*.keystore` 均被 `.gitignore` 排除且未入库

## 6. 已知限制

- APK 被 `.gitignore` 排除，不随源码提交；由开发者本地构建产出。
- 真机 connected tests 与耗电证据需要真机环境，本模板不替代真实测量。
- 完成本清单全部项之前，不应把 v0.1 描述为“已经过真机验证”或“已达到最终耗电目标”。
