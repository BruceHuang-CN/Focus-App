# Focus App

Focus App 是一款 Android 专注辅助应用。当用户打开抖音、哔哩哔哩、小红书等目标应用后，它会按用户设置的延迟和频率，结合当前任务、状态及提醒口吻给出提示，并支持返回 Focus 或桌面。

当前开发分支：`feature/focus-v0.1-preview`

## 项目进度

当前已完成 Task 1–7，Task 8–11 尚未完成。后续开发请以 [docs/FOCUS_V0.1_HANDOFF.md](docs/FOCUS_V0.1_HANDOFF.md) 为唯一任务入口；该文档包含完成记录、剩余任务、验证结果和恢复步骤。

## 技术概况

- Kotlin、Jetpack Compose、Room、DataStore
- 无障碍事件用于实时检测，UsageStats 作为可选兼容模式
- DeepSeek API 批量生成短提醒，本地缓存和轮换，避免每次打开都请求网络
- API Key 使用 Android Keystore 加密保存
- 提醒延迟、统计窗口、窗口内次数和每日上限均支持预设与自定义

## 本地构建

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Android 真机上的权限、提醒流程、内存和耗电仍需在 Task 11 验证。
