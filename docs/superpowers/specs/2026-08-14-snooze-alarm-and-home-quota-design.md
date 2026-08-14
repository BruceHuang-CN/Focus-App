# Focus 再次提醒系统唤醒与首页额度设计

状态：用户已确认方向，待书面复核后实施。

## 目标

在 realme/ColorOS 将 Focus 进程冻结时，用户选择的再次提醒仍能在合理时间范围内唤醒 Focus，
并继续显示现有的 AI 个性化提醒。首页守护卡片同步展示当前滚动提醒窗口的已用次数和上限，
让用户和测试人员能够直接判断提醒是否真正执行。

## 已确认验收标准

- 选择 1 分钟后提醒时，正常目标为 1～3 分钟内再次显示 Focus AI 提醒。
- 选择 5 分钟后提醒时，正常目标为 5～8 分钟内再次显示 Focus AI 提醒。
- 锁屏或厂商冻结允许少量额外延迟，但任务不能仅停留在通知栏守护状态而永久不执行。
- 再次提醒必须继续使用 Focus 的 ReminderActivity、DeepSeek/本地缓存文案和现有交互，
  不展示系统闹钟界面，不播放系统闹铃。
- 到期前真正离开目标 App 时取消任务，不弹窗、不增加额度计数。
- 首页守护卡片展示「本时段（N 分钟）已提醒 X/Y 次」，其中 N、X、Y 均来自实际滚动窗口规则。

## 现场根因

真机中前台服务和无障碍服务仍存在，但 Focus 主进程位于 `freezer:/frozen`。会话已经持久化
`snoozeUntil`，而协程计时器无法获得 CPU；WorkManager 在 Android 11 上委托给 JobScheduler，
同一条已超过最早执行时间的任务仍被 ColorOS 动态约束拦截。现有「进程内协程 + WorkManager」
双路径共享了厂商冻结这一失败条件，因此需要独立的系统唤醒入口。

## 方案比较

### 方案 A：一次性系统唤醒 + 现有双路径（采用）

每次稍后提醒同时安排 AlarmManager、进程内协程和 WorkManager。AlarmManager 只负责在到期时向
Focus 发送 PendingIntent；AI 内容、额度、前台应用和会话判断仍由现有统一执行器处理。

优点：针对已确认的冻结根因；没有持续轮询；沿用现有业务门控和原子去重；Android 11 可直接使用
精确唤醒。缺点：厂商仍可能造成少量延迟；Android 12 以上没有精确能力时需要降级为非精确唤醒。

### 方案 B：前台服务配合持续唤醒锁（不采用）

会明显增加耗电，而且现场已经证明前台服务仍可能被 ColorOS 放入 frozen，不能消除共同失败条件。

### 方案 C：继续只用 WorkManager（不采用）

改动最少，但无法解决已观察到的过期 Job 仍不执行问题。

## 架构与数据流

### 1. 系统唤醒抽象

新增单一职责接口 `FollowUpWakeScheduler`：

- `schedule(sessionId, delayMillis)`：为会话设置或替换一次性系统唤醒。
- `cancel(sessionId)`：取消该会话的系统唤醒。

Android 实现使用稳定的 action、会话 ID 和稳定 request code 创建不可变 PendingIntent，接收器必须
`exported=false`，外部应用不能伪造再次提醒。

调度规则：

- Android 11 及以下：`setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, ...)`。
- Android 12 及以上：`canScheduleExactAlarms()` 为真时使用精确唤醒，否则使用
  `setAndAllowWhileIdle()`；不因权限缺失抛异常，也不在本轮强制弹出权限申请页。
- 不使用 `setAlarmClock()`，因此不会出现系统闹钟界面或闹钟图标。

### 2. 接入现有混合调度

`HybridFollowUpScheduler.schedule()` 在持久化 `snoozeUntil` 后安排三条路径：

1. 进程存活且未被冻结时的协程 delay；
2. 进程被回收后的 WorkManager；
3. 进程被 ColorOS 冻结时的 AlarmManager 唤醒。

`cancel()` 必须同时取消三条路径。重新选择时间时，稳定 PendingIntent 与 unique work 名称替换旧任务，
不能留下两个不同到期时间。

### 3. Alarm 接收与统一执行

新增非导出的 `FollowUpAlarmReceiver`。收到系统唤醒后使用 `goAsync()` 在受控应用协程中：

1. 读取 session ID；无效 ID 直接结束。
2. 取消该会话的其他待执行路径，减少重复唤醒。
3. 调用现有 `FollowUpReminderExecutor.execute(sessionId)`。
4. `SHOW` 和 `SKIP` 取消其余路径；`RETRY` 以 PendingIntent 中的重试次数安排下一次短间隔唤醒，
   最多沿用现有 20 次上限，避免锁屏期间无限唤醒。
5. 无论成功或异常都调用 PendingResult.finish()；异常保留持久化截止时间并留下可诊断日志。

执行器继续负责：守护开关、原会话仍开放、当前会话一致、目标 App 仍在前台、屏幕状态、滚动额度、
AI 缓存文案和 ReminderActivity 展示。Alarm 路径不得复制这些业务判断。

### 4. 去重与额度

Alarm、协程和 WorkManager 可能在相近时间到达。三者都复用 `claimSnooze(sessionId)`：只有第一个成功
领取的执行者可以更新 `remindedAt` 并显示弹窗；其他入口返回 SKIP。这样不会重复弹窗、重复消费
AI 文案或重复占用额度。

### 5. 首页额度显示

`HomeUiState` 增加：

- `reminderWindowMinutes`
- `windowReminderCount`
- `windowReminderLimit`

`HomeViewModel.refresh()` 使用设置中的 `reminderWindowMinutes` 计算滚动起点，并调用现有
`countShownRemindersSince()`。页面返回前台和重置额度后都刷新，因此再次提醒成功后回到首页即可看到
新数字。

`GuardianControlCard` 在「守护已开启/已暂停」文字下增加：

`本时段（30 分钟）已提醒 2/5 次`

这里统计实际 `remindedAt`，同时包含首次提醒和再次提醒；排队、取消、跳过、额度拦截均不计数。

## 失败处理与边界

- 系统唤醒晚到时仍走同一门控；用户已经离开目标 App 就跳过并清理任务。
- 锁屏返回 RETRY 时重新安排一次性唤醒，不能退回只依赖被冻结的协程。
- Android 12 以上没有精确能力时允许时间漂移，但不能崩溃或丢失 Room 状态。
- 设备重启会清除 AlarmManager；现有 WorkManager 的启动恢复和 Room 的 pending snooze 恢复继续兜底。
- 用户或 ColorOS 真正执行 force-stop 后，Android 会阻止应用组件和已安排任务自行运行；此场景只能在
  用户重新打开 Focus 后恢复可恢复状态，不能宣称应用能够绕过系统强制停止。

## 测试设计

### 自动化

- 先写失败测试，证明混合调度会同时安排并取消系统唤醒。
- 覆盖 Android 11 精确唤醒、Android 12+ 有权限精确唤醒、无权限降级唤醒。
- 覆盖稳定 PendingIntent 身份：重选时间替换旧任务，取消后无残留。
- 覆盖 Alarm Receiver 的 SHOW、SKIP、RETRY、无效 session ID 和异常 finish。
- 覆盖 Alarm、协程、Worker 竞态只允许一次 `claimSnooze()` 成功。
- 覆盖首页滚动窗口计数、窗口文案、重置后归零和返回首页刷新。

### RMX3350 真机

必须完整构建并安装，禁止用 Apply Changes 作为最终证据：

1. 验证安装包包含 Alarm Receiver 与最新 Room schema。
2. 选择 1 分钟，确认 `dumpsys alarm` 出现会话 PendingIntent。
3. 返回目标 App，等待 Focus 进入 frozen；在 1～3 分钟范围内确认出现 Focus AI 弹窗。
4. 对 5 分钟重复测试，目标范围为 5～8 分钟。
5. 验证弹窗一次、额度增加一次、`snoozeUntil` 清空、其他调度路径取消。
6. 选择稍后提醒后切到桌面或非目标 App，确认取消且首页额度不增加。
7. 覆盖锁屏/解锁、最近任务划掉、目标 App 间切换和真正 force-stop 的负向边界。

## 不在本轮范围

- 修改 DeepSeek 请求、提示词、模型或 AI 缓存策略。
- 重做提醒弹窗视觉样式。
- 绕过 Android/ColorOS 的真正 force-stop。
- 将首页额度显示扩展为新的统计图表或历史详情页。
