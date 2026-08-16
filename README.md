# Focus App

Focus App 是一款 Android 专注辅助应用。当用户打开抖音、哔哩哔哩、小红书等目标应用后，它会按用户设置的延迟和频率，结合当前任务、状态及提醒口吻给出提示，并支持返回 Focus 或桌面。

当前开发分支：`feature/focus-v0.1-preview`

## 当前状态

**基本功能已完善（预览版）**。核心检测、任务、提醒、DeepSeek 兼容 API、额度、统计、养成基础、权限诊断和反馈入口已经整理到同一分支；下一步重点是 Android Studio 真机回归和不同厂商后台行为验证。

## 项目进度

详细任务状态请查看 [docs/PROJECT_TASKS.md](docs/PROJECT_TASKS.md)。后续开发请以该清单和 [docs/FOCUS_V0.1_HANDOFF.md](docs/FOCUS_V0.1_HANDOFF.md) 为入口；文档会区分“代码已实现”和“真机已验证”。

## 技术概况

- Kotlin、Jetpack Compose、Room、DataStore
- 无障碍事件用于实时检测，UsageStats 作为可选兼容模式
- DeepSeek API 批量生成短提醒，本地缓存和轮换，避免每次打开都请求网络
- API Key 使用 Android Keystore 加密保存
- 提醒延迟、统计窗口、窗口内次数和每日上限均支持预设与自定义

## 问卷星反馈系统

设置页的“反馈与支持”入口已经整理完成：

- 展示问卷星反馈海报，点击“填写问卷”打开外部问卷星链接；
- 当前链接和海报资源集中在 `FeedbackAndSupportContent.kt` 与 `questionnaire_poster.png`，后续更换问卷时只需替换这两处；
- 无法打开浏览器时显示明确错误提示，不影响主功能；
- 问卷答案由问卷星负责收集，App 不保存问卷内容，也不新增自建后端；
- 微信公众号二维码和赞助二维码作为独立入口保留，不与反馈提交流程耦合。

## 本地构建

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Android 真机上的权限、提醒流程、内存和耗电仍需在 Task 11 验证。
