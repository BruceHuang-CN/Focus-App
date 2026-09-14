# 回神 App：最小可用问题反馈机制实施清单

日期：2026-09-14。状态：Git 基线已准备，计划已写入；功能尚未实施。

## 1. 工作目录与版本基线

- 实施目录：`D:\Project\Focus-App-feedback`。
- 起点：`origin/codex/ui-optimization`，已执行 fetch 核对远端。
- 基线 SHA：`e1e6f97c61df07ef6beb63d1e93678dcc61be95b`。
- 新建本地分支：`huishenfeedback`，按用户指定名称，不跟踪旧 UI 分支。
- 本轮保留 3 个原有 `.idea` 配置改动及未跟踪的 `Focus-App-feature-focus-v0.1-preview.zip`；不纳入反馈功能交付。
- 不改 App 版本号、不调整全局 Git 配置、不运行 Gradle 或设备操作；用户随后已授权将规划文档提交并推送到新分支。
- 本文在项目内的位置：`docs/superpowers/plans/2026-09-14-issue-feedback-plan.md`；总入口为 `docs/PROJECT_TASKS.md`。

## 2. 目标与本期范围

用户路径：设置 → 反馈与支持 → 问题反馈 → 填写描述 → 查看诊断信息／完整反馈预览 → 系统分享或复制。

只开发 Android 端。保留问卷调查和微信／支付宝支持功能；采用当前绿色 Material 3、日夜主题与中英文资源。使用 Compose、Hilt、Repository、ViewModel、StateFlow。

不搭建服务器，不新增反馈数据库、表或迁移，不接邮件 API，不自动创建 GitHub Issue，不接 Crashlytics、Sentry 等服务。反馈草稿不新增持久化存储，不增加定时采集、后台任务或权限请求。

“不接数据库”在此指反馈功能不新增存储；按用户指定复用 SettingsRepository，只读已有本地设置。问卷原有外部链接继续保留。

本期导出有固定分节的纯文本，不做附件、ZIP、截图、完整日志或自定义分享目标列表。用户自己选择收件人，本期没有开发者收件地址配置依赖。

## 3. 已核对的现有代码

下列路径相对于项目根目录；Kotlin 源码根为 `app/src/main/java/com/example/focus_app/`。

| 现有文件／接口 | 当前作用 | 复用方式 |
| --- | --- | --- |
| `ui/settings/FeedbackAndSupportScreen.kt` | 问卷卡片、问卷打开失败提示、赞助二维码切换 | 顶部增加明显的问题反馈卡片和导航回调，保留既有功能 |
| `ui/settings/FeedbackAndSupportContent.kt` | 问卷 URL 常量和赞助展示数据映射；不是表单 Composable | 继续复用，不将它改造成另一套反馈状态系统 |
| `service/AccessibilityDiagnosticsStore.kt` | 已有 Hilt 单例、StateFlow、生命周期记录及历史时间持久化 | Collector 只读 `state.value`，不调用记录事件方法 |
| `service/AccessibilityDiagnostics.kt` | `AccessibilityDiagnosticsState` 及事件归约 | 取用户指定的 4 个字段，不新增无障碍状态存储 |
| `data/repository/SettingsRepository.kt` | `AppSettings`、`getSettings()`、`getSettingsFlow()` | 只读必要设置，逐字段映射，禁止整体序列化 |
| `ui/settings/SettingsViewModel.kt` | 已有 Hilt／StateFlow 约定 | 沿用架构，新增独立反馈 ViewModel，避免塞入大型设置 ViewModel |
| `ui/navigation/NavGraph.kt` | 已有 `Screen.FeedbackAndSupport` 路由 | 增加 `Screen.ReportIssue` 并接回退 |
| `data/language/AppLanguage.kt` | 应用语言可独立于系统语言 | 系统语言必须单独准确读取，不把应用语言误标为系统语言 |
| `app/src/test/.../ui/settings/FeedbackAndSupportContentTest.kt` | 问卷 URL 和二维码映射测试 | 保留并在实施验证时回归 |

最新交付说明是 `docs/DEVELOPMENT_LOG.md` 的 2026-09-13 章节。旧 `FOCUS_V0.1_HANDOFF.md` 和总清单含历史状态，不能据此切回旧分支，也不能将历史完成标记当作当前真机验收。已有 `2026-09-10-feedback-delivery.md` 记录的是此前用户反馈修复，不是本次问题反馈功能。

## 4. 页面、诊断与输出约定

### 页面

- “遇到了什么问题？”：多行必填；纯空格无效；示例“打开小红书以后没有出现提醒。”
- “这个问题是怎么出现的？”：多行选填；提示按操作顺序填写。
- “你原本希望发生什么？”：多行选填。
- “附带诊断信息（推荐）”：默认开启，用户可关闭。
- “查看诊断信息”：展示全部将附带的字段和值及采集时间，可滚动，无隐藏附加字段。
- 主按钮“预览反馈”：必填内容有效后才可用；预览完整正文，再显示“系统分享”和“复制反馈内容”。
- 本期暂定三个输入字段各最多 2,000 字符，展示计数和上限，禁止静默截断。此限制用于保证首版长文本可控，可在实施时调整并同步测试。
- 返回编辑保留草稿；旋转、语言切换等界面重建使用 ViewModel／SavedStateHandle 恢复必要表单状态。重新进入一个已退出的表单允许为空；不承诺卸载、强停后的草稿恢复。
- 已预览后编辑任意字段、改变附带诊断开关或主动刷新诊断，必须使旧完整预览失效；重新预览后才能分享。

### 自动诊断白名单

新增不可变 `DiagnosticSnapshot` 与统一 `DiagnosticsCollector`，只在用户进入反馈操作并需要预览时采集，不在 Compose 重组时采集。

| 分类 | 字段 | 来源／语义 |
| --- | --- | --- |
| 设备 | manufacturer、model | Android Build |
| 系统 | androidVersion、apiLevel | Build.VERSION |
| 版本 | versionName、versionCode | 本应用实际安装包元数据；不可硬编码为当前 1.0.0／1 |
| 语言 | systemLanguage | 系统首选语言的 BCP 47 标签，区分 App 自选语言 |
| 无障碍 | serviceBound | Store 当前记录；不是系统授权开关，也不证明提醒成功 |
| 无障碍 | lastConnectedAtMillis、lastDestroyedAtMillis、lastInterruptedAtMillis | Store 已有历史记录；null 显示“暂无记录”，不可变成 1970 年 |
| 元信息 | collectedAtMillis | 标明本次快照时间；时间显示注明时区，保留毫秒值便于核对 |

为落实 SettingsRepository 的复用，首版计划仅附带以下设置摘要：`guardianEnabled`、`detectionMode`、`reminderDelaySeconds`、`reminderWindowMinutes`、`maxRemindersPerWindow`。这是基于用户要求复用设置仓库作出的最小设计选择；它们与其他诊断一同预览、受同一个开关控制。

不导出 API Key、接口地址、AI 模型自定义内容、口吻文本、任务正文、目标应用名单、使用记录、设备唯一标识、联系人、位置、完整 Logcat 或无障碍节点文本。用户主动填写的描述原样呈现，不宣称系统会自动识别其中所有个人信息。

`serviceBound` 在进程启动时初始化为 false，历史连接／中断／销毁时间可来自此前进程；预览注明这是 App 的已记录状态，不推断“权限关闭”“发生崩溃”或“后台可靠”。保持现有 Store 的更新行为。

### 预览与分享一致性

建议数据流：ReportIssueViewModel → DiagnosticsCollector → 现有 Store／SettingsRepository／系统信息；表单和快照交给纯 Kotlin `IssueReportFormatter` 生成 `PreparedIssueReport`，页面读取 StateFlow。

- 查看诊断时建立快照；完整预览复用该快照。没有查看过诊断则在完整预览前采集一次。
- 完整预览确认后冻结正文。分享与复制都使用同一份已预览的字符串，不在分享按钮中重新采集。
- 关闭诊断后使旧快照／预览失效；分享正文不含设备、版本、设置、服务记录或采集时间等诊断信息。
- 采集某一项失败，标注该项“无法读取”，保留其他字段并允许继续；协程取消正常传播，不吞掉取消异常。
- 设置摘要与服务 StateFlow 是相邻时刻读取，不声称跨来源原子快照；冻结的是最终展示数据。
- 输出固定分节：回神问题反馈、问题描述、复现过程、预期结果、可选诊断信息。未填选填项统一显示“未填写”。
- 系统分享采用 `ACTION_SEND`、`text/plain`、`EXTRA_TEXT` 与 `Intent.createChooser()`；可设置不含额外诊断的通用主题。无需附件存储权限或 FileProvider。
- 系统分享目标取决于手机已安装且支持文本接收的应用；不保证微信／邮件必然出现。预览内提供显式复制按钮兜底。
- 分享取消或返回时保留内容；启动失败显示可理解的提示并允许复制。不将打开分享面板标成“反馈已送达”。

## 5. 按依赖执行的任务清单

本节 `[x]` 只表示准备工作已核对；功能任务全部待实施。

- [x] T0：检查指定目录、Git 状态、敏感文件路径、远端及 SHA；fetch 后核对 UI 基线，创建 `huishenfeedback` 分支，保留原有改动。
- [x] T1：核对反馈页、导航、无障碍 Store、SettingsRepository／AppSettings、最新交付记录，并写入本计划和总清单入口。
- [ ] T2：新增 `domain/feedback/DiagnosticSnapshot.kt`、`PreparedIssueReport.kt`、`IssueReportFormatter.kt`。确定白名单、缺失值、固定输出格式与诊断关闭语义；先验证正文生成和不泄漏多余字段。
- [ ] T3：新增 `data/diagnostics/DiagnosticsCollector.kt`，通过 Hilt 注入 ApplicationContext、现有 Store 和 SettingsRepository。复用已有绑定，不创建第二套服务状态系统；处理 API 26 起的版本号和系统语言读取兼容、单项失败、时间语义。
- [ ] T4：新增 `ui/settings/ReportIssueViewModel.kt`。用 StateFlow 暴露表单、错误、采集中状态及冻结预览；处理必填、长度上限、开关、重试、草稿恢复及预览失效。UI 收集状态遵循 lifecycle；分享操作由显式点击发起，避免重组／重建重复启动。
- [ ] T5：新增 `ui/settings/ReportIssueScreen.kt`，必要时在同文件拆分纯内容 Composable。完成三项输入、默认勾选、完整诊断预览和反馈预览；复用当前主题；适配键盘、窄屏、大字体及日夜模式。
- [ ] T6：修改 `FeedbackAndSupportScreen.kt` 和 `NavGraph.kt`。新增明显入口并置于长问卷海报之前；保留问卷和赞助。检查从设置进入子页的回退与未保存设置策略；`FeedbackAndSupportContent.kt` 仅在实际需要时改动。
- [ ] T7：新增轻量 `ui/settings/IssueReportShare.kt`，集中系统分享与显式复制动作；添加 `res/values/strings_feedback.xml` 和 `res/values-en/strings_feedback.xml`。分享／复制与完整预览一致，失败可恢复。
- [ ] T8：补充并运行必要的本地回归测试和构建／静态检查，记录结果；不要用“测试文件已编写”代替“测试已通过”。
- [ ] T9：在另获设备操作授权后开展系统分享、邮件／微信接收、复制、取消、旋转及语言切换的设备验收，记录机型、系统、实际 APK 版本和结果；更新任务状态和开发日志。

依赖：T2 → T3 → T4 → T5；T6、T7 接入已有表单／预览后，执行 T8；T9 以可安装构建和明确设备授权为前提。本轮停在 T1，不开始实施。

## 6. 实施阶段验证标准

### 有意义的本地回归

- Formatter：空白描述被拒绝；中文和换行保留；选填为空可生成；诊断关闭时全部诊断字段均不存在；无记录和读取失败含义不同；输出稳定。
- Collector：只映射白名单；来自现有 Store 的 4 字段准确；设置读取失败时不伪造默认已开启状态；局部失败不阻塞其他字段；不更改设置或服务记录。
- ViewModel：旋转恢复文本及勾选；编辑、关闭诊断、刷新后旧预览失效；连续点击不重复采集；采集中关闭诊断后迟到结果不重新附加；分享／复制获取已预览正文，不获取实时新值。
- 现有回归：`FeedbackAndSupportContentTest`、`AccessibilityDiagnosticsTest` 及受导航签名变更影响的测试；保持原问卷 URL 与赞助资源映射。
- 优先沿用当前 JUnit／coroutines-test 和已有 Android 测试设施；本期不为简单测试额外引入大型框架。必要的平台读取适配使用小型可替换边界测试。
- 实施时从仓库执行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`；若基线已有错误，记录与本次改动的关系，不能把未通过报告为已完成。
- `git diff --check`，中英文字串键及格式参数一致性检查；确认没有新增 SDK、权限、数据库 schema 或后台工作。

### 人工／设备验收

- 从设置依次进入反馈与支持、问题反馈；问卷、微信和支付宝二维码功能仍在。
- 空白／纯空格描述不能进入有效预览；只有描述也能完成流程；长文本上限有提示。
- 默认附诊断、查看所有字段、关闭诊断、重新预览，各状态正确。
- 对比完整预览、复制结果、接收应用正文逐字一致；服务状态在预览后变化不影响本次分享文本。
- 在可用设备上检查邮件、微信接收和无合适接收应用场景；取消或分享失败仍可返回修改、复制。
- 系统中文而 App 英文时，systemLanguage 仍显示系统语言；同时验证日夜主题、大字体、键盘遮挡及页面重建。
- 不要求触发真实崩溃证明诊断功能；不将服务连接记录当作提醒送达或崩溃原因证明。

## 7. 难度与取舍

| 部分 | 难度 | 实施关注点 |
| --- | --- | --- |
| 入口、三项输入、绿色页面、固定纯文本 | 低 | 现有 Compose／Material 3 与导航可复用 |
| 设备型号、版本号等基本信息 | 低到中 | 实际包版本、API 26 兼容与系统语言区分 |
| 无障碍诊断语义、设置白名单 | 中 | 连接状态与系统权限不同；历史时间可能为空；避免整体导出 AppSettings |
| 预览与分享一致、开关竞态、草稿恢复 | 中，主要实现难点 | 冻结快照、修改后失效、采集中关闭诊断及界面重建必须保持一致 |
| 系统分享与厂商／接收应用差异 | 中，主要验收难点 | 目标列表及接收行为需实测；通过纯文本和显式复制减少依赖 |
| 整个项目构建与真机回归 | 有外部不确定性 | 最新 UI 交付记录未完成编译验收，可能遇到既有错误；反馈功能本身不负责修复原有提醒链路 |

按上述首版范围，整体难度为中等，没有必须先建设后端的阻塞项。暂缓附件导出、事件环形日志、自动崩溃捕获、自动归因、反馈历史和自动上传。

## 8. 官方接口依据与本轮验证边界

- Android 系统分享：[发送文本与使用系统分享面板](https://developer.android.com/develop/ui/compose/sharing/send)。
- 包版本读取：[PackageInfo](https://developer.android.com/reference/android/content/pm/PackageInfo)。

本轮只核对仓库、远端和源码，创建分支并写文档。没有实现上述功能，没有运行单元测试、Gradle 或设备测试，没有安装 APK。用户随后将分支名更正为 huishenfeedback，并授权提交及推送规划文档；实际提交标识与远端核对结果由交付消息记录。
