# 回神 App 反馈直传 Cloudflare：实施交接计划

核查日期：2026-09-24。供另一个模型在本机接手实施。

## 0. 给执行模型的任务说明

请基于下列两个现有项目，实现“Android App 内填写问题并直接提交，开发者在后台查看”的反馈流程。优先复用现有代码，不重建网站，不更换框架，不新租服务器。

本文件是计划，尚未实施这些新接口、迁移、上传或管理页面。本轮用户仅要求详细规划并交给其他模型执行；部署、生产数据库写入、设备安装、Git 提交／推送应按执行对话中的实际授权进行，不把本计划里的命令当成已执行记录。

推荐按 T0 → T1 → T2 → T3 → T4 → T5 → T6 顺序交付。T2 + T3 完成后，已经能在 App 内直接提交并由开发者通过 D1 控制台查看；T4 完成后增加登录后的网页反馈管理。不要因管理页暂未配置好而把已完成的上传部分重写。

## 1. 已核对的现状与证据边界

### 1.1 网站

- 用户给的外层路径：`D:\MyWebBuild`。
- 真正的工程根目录：`D:\MyWebBuild\outputs\brucehere`。所有 npm／Wrangler 命令都应在这里执行。
- 2026-09-24 本地 Git：分支 `main`，HEAD `a66b524`，本轮检查时工作区无未提交改动。实施前再次核对。
- 网站地址：`https://brucehere.com`；现有 Pages 项目 `brucehere`，直接上传生产分支 `main`。
- 技术栈：React 19 + Vite 7，JavaScript ES modules，Pages Functions，D1。不要迁移到 Next.js，也不需要再独立创建一个 Worker 项目。
- `wrangler.toml` 已有 `pages_build_output_dir = "./dist"`，绑定名 `DB`，数据库名 `brucehere-messages`；已有真实 database_id，直接核对使用，不复制创建一个同名库。
- 本机发现 Wrangler 入口 `C:\Users\6\AppData\Roaming\npm\wrangler.ps1`，Node 入口 `D:\nodejs\node.exe`。本轮未执行登录或检查当前云端账号权限。
- 已有 `functions/api/messages.js` → `server/messages.js`，处理网页匿名留言和网页反馈。
- 已有 `functions/api/message-config.js`，只返回服务开关及公开 Turnstile site key。
- `migrations/0001_messages.sql` 创建 `private_messages`、`message_limits`；没有独立 App 反馈表或管理员读取 API。
- `tests/messages.test.js` 使用 Node 内置测试框架、真实内存 SQLite 及假的 Turnstile 响应。可复用这种测试方式。
- `public/_routes.json` 当前只让 `/api/*` 进入 Functions。
- `src/template/adapters/navigation.jsx` 与 `src/main.jsx` 使用 hash 路由，例如 `#/projects/focus`。

当前部署说明：

- `D:\MyWebBuild\outputs\Brucehere-部署交接-20260923-v2.md` 记载网站、D1 和正式 Turnstile 已部署启用，但真人实际收信验证未完成。
- `SETUP.md` 开头也记载已启用，末尾仍有“尚未完成云端配置／发布”的旧段落，内容存在矛盾；实施者应更新这个过时段落。
- 本轮网页工具未能读取线上 `/api/message-config`，所以本计划只确认本地实现及配置，不宣称 2026-09-24 线上接口已经重新验证成功，也不能据此说网站故障。
- 旧交接里的 OAuth 到期时间不是当前登录状态的依据。后续用 `wrangler whoami` 核对，不先盲目重新登录，更不读取或输出凭据文件。
- 外层 `brucehere-website.zip` 被旧交接标为旧包，不以它覆盖当前源码。

### 1.2 Android

- 工程：`D:\Project\Focus-App-feedback`。
- 已知工作分支 `huishenfeedback`；2026-09-24 本地 HEAD `a434cb1`，有大量未提交功能及文档改动。实施前核对分支和差异。
- Compose + Hilt + Repository + ViewModel + StateFlow；已有 Retrofit／OkHttp 可复用网络设施。
- 反馈源码根：`app/src/main/java/com/example/focus_app/`。
- 已有 `ui/settings/FeedbackAndSupportScreen.kt`、`ReportIssueScreen.kt`、`ReportIssueViewModel.kt`、`IssueReportShare.kt`。
- 已有 `data/diagnostics/DiagnosticsCollector.kt` 与 `domain/feedback/DiagnosticSnapshot.kt`、`IssueReportFormatter.kt`、`PreparedIssueReport.kt`。
- 现有流程：填表 → 生成冻结的预览 → 分享或复制；尚无 App 反馈 HTTP 上传。
- 保留现有问卷、赞助、每日 AI 总结、首页优化、心情和动画功能。
- 保留 `.idea` 改动、ZIP、现有反馈未跟踪文件；禁止 `git reset --hard`、`git clean`、无选择的 `git add .`。
- 2026-09-19 的 376 项 JVM 测试成功、Debug 构建成功是历史证据，不代表本次新功能已验证。历史 Lint 有 47 errors／74 warnings，新一轮必须独立分类。

## 2. 最终产品行为

用户流程：设置 → 反馈与支持 → 问题反馈 → 填写描述 → 点击“提交反馈” → 收到成功回执。

页面建议：

```text
遇到了什么问题？
[多行输入，必填]

补充复现过程和预期结果 ▾

☑ 附带基础诊断信息（推荐）    查看
☐ 附带详细诊断信息

反馈将发送至 brucehere.com，由回神开发者私下查看。
[提交反馈]

其他方式：复制／系统分享
```

- 复现过程和预期结果选填，折叠收纳。
- 默认基础诊断，详细诊断默认关闭；关闭基础诊断时同时关闭详细诊断。
- “查看”在当前页展开或弹出预览，不强制经过独立预览页才能提交。
- 提交时冻结表单、诊断级别、快照、请求 ID；预览与该次实际上传来自同一份数据。
- 提交中禁用重复提交和编辑；失败保留内容和同一次请求的数据，显示可重试提示。
- 后端明确确认保存后才显示“已收到，谢谢你”，可展示反馈编号及复制编号。
- 成功后清理本次草稿，提供“再反馈一个问题”；失败时绝不清空。
- 分享／复制作为备用操作，不再是主流程。首版不引入后台上传队列、自动邮件、GitHub Issue、截图上传或推送回复。

## 3. 诊断信息分级与一致性

在现有模型上增加明确的诊断级别或白名单投影，不建立第二套无障碍服务状态系统。

| 级别 | 上传内容 |
| --- | --- |
| none | diagnostics = null，不发送设备／App 版本／语言／服务／设置／采集时间 |
| basic（默认） | 厂商、型号、Android 版本、API Level、App versionName／versionCode、守护开关、检测模式、serviceBound、采集时间 |
| detailed（可选） | basic + 系统语言、lastConnectedAt／lastDestroyedAt／lastInterruptedAt、提醒延迟、频率窗口、窗口最大次数 |

- ID、协议版本、服务端收到时间属于反馈请求管理字段，不是设备标识。ID 为每条反馈随机 UUID v4，不使用安装 ID、Android ID、IMEI 或广告 ID。
- `serviceBound` 只表示现有 Store 记录的连接状态，不代表系统授权，也不证明提醒送达。
- 缺失普通值使用 null，不伪造 true／false。历史时间保留“有记录／无记录／读取失败”的三态，不把缺失写成 1970 年。
- `versionCode` 在线协议用十进制字符串，避免 JavaScript 对 Long 的精度问题；后台按字符串显示，勿直接转 Number。
- Android 模型可继续保留原有字段，但 HTTP DTO 必须按级别逐字段构造；不得序列化整个 AppSettings 或整个运行时对象。
- 当前 DiagnosticsCollector 使用 `LocaleList.getDefault()` 并声称不受 App 语言影响，这个注释与实现需要修正。详细诊断应使用项目兼容版本支持的 `LocaleManagerCompat.getSystemLocales(context)` 读取真正系统语言，并验证“系统中文、App 英文”。
- 用户已查看的有效快照用于提交；需刷新时显式刷新并使旧请求失效。采集中改变级别的迟到结果不得重新附加诊断。
- 不附带用户 AI Key、AI 接口地址、任务正文、心情备注、目标应用名单、使用历史、完整日志、无障碍节点、联系人或位置。
- 正文是用户主动填写的内容，不能承诺其中永远没有个人信息；后台私密展示，不写入日志。

## 4. 接入架构与现有网页接口的区别

```text
Android ReportIssueScreen / ViewModel
  → FeedbackRepository → FeedbackApi
  → POST https://brucehere.com/api/app-feedback
  → Pages Function → server/app-feedback.js
  → 同一个 DB 绑定下的新表 app_feedback

开发者 → 登录管理页
  → /api/admin/feedback（服务端认证）
  → D1 查询／更新状态
```

现有 `/api/messages` 不能直接作为原生 App 上传接口：

1. 它要求请求 Origin 与站点同源；原生 HTTP 客户端通常不携带网页 Origin。
2. 它要求 Turnstile token，并在服务端验证 hostname 和 action=`message`。
3. 原表单 body 仅 2000 字符、格式针对网页留言，不能把整个诊断报告硬塞进去后静默截断。

因此新增 `/api/app-feedback`，保留 `/api/messages` 的同源、Turnstile、蜜罐和现有类型行为。不要伪造 Origin、固定 token、关闭原接口验证，或声称 CORS、包名、User-Agent、APK 内固定密钥能证明请求来自正版 App。

首版 App 接口采取公开匿名投稿 + 严格校验 + 限流，无需登录或默认打开 WebView。这能满足一键提交，但不能完全防止伪造客户端或分布式刷请求。

建议沿用服务器 HMAC 时间窗口限流思路，每个网络标识每 10 分钟最多 5 次新反馈，单独命名空间及限流表；App 侧按钮防连点只是体验措施。HMAC 使用 Cloudflare 提供的 CF-Connecting-IP 与服务端 RATE_LIMIT_SECRET，不保存原始 IP，不信任请求 JSON 里的 IP；公共边缘之外不能随意相信同名头。

提供 `APP_FEEDBACK_ENABLED` 服务端开关，关闭时返回 503，可单独停用新接口，不影响网页信箱。评估现有账户是否能在边缘对该路径增加限流；不要假设所有 Worker binding 在 Pages 都可用，也不要未经确认升级套餐。

如实际流量出现明显滥用，再做按需真人验证。这是后续范围；Turnstile 通常涉及网页挑战，不能保证无交互，也不要第一版同时实现两套完整验证流程。

## 5. HTTP 协议 v1（先固定，再分别实现）

接口：`POST /api/app-feedback`，Content-Type 为 application/json，HTTPS。不要跟随到任意第三方域名的重定向并重新发送正文；采用固定开发者域名，与用户 AI 设置完全分离。

示例：

```json
{
  "schemaVersion": 1,
  "id": "71e29975-138d-49ab-b7ef-fb094cce4601",
  "problem": "打开目标应用后没有出现提醒。",
  "steps": "开启守护后进入目标应用，等待约十秒。",
  "expected": "出现回神提醒。",
  "diagnosticLevel": "basic",
  "diagnostics": {
    "collectedAtMillis": 1790200000000,
    "device": {
      "manufacturer": "realme",
      "model": "RMX3350",
      "androidVersion": "11",
      "apiLevel": 30,
      "versionName": "1.0.0",
      "versionCode": "1"
    },
    "accessibility": { "serviceBound": false },
    "settings": { "guardianEnabled": true, "detectionMode": "realtime" }
  }
}
```

详细诊断增加字段：device.systemLanguage；accessibility.lastConnectedAt／lastDestroyedAt／lastInterruptedAt；settings.reminderDelaySeconds／reminderWindowMinutes／maxRemindersPerWindow。历史时间采用以下形式之一：

```json
{ "state": "recorded", "epochMillis": 1790200000000 }
{ "state": "no_record" }
{ "state": "unavailable" }
```

固定约束：

- problem 去首尾空白后必填；problem／steps／expected 各最多 2000 个 UTF-16 code units，与 Kotlin String.length / JavaScript length 对齐，保留内部换行。
- 实际请求体最多 32 KiB，流式限制，不只信任 Content-Length。超长拒绝，客户端不静默截断。
- device 字符串各最多 128；versionCode 最多 19 位且非负；系统语言最多 64；数字／布尔／枚举做类型、整数和合理范围校验。
- 检测模式用稳定协议值 realtime／compatibility，不依赖中文展示名。未读取成功为 null。
- 顶层及各嵌套对象字段白名单；v1 未知字段和级别不匹配返回 400，none + 非 null diagnostics 必须拒绝，basic 不接受 detailed 字段。
- 所有请求使用固定顺序的规范化 DTO 计算 SHA-256 payload_hash。JSON 键排列不同不应导致同内容冲突；不包含服务器收到时间。协议文档明确 trim 和空值规则。
- created_at / updated_at 由后端生成，单位统一为 UTC epoch 秒；客户端 collectedAtMillis 仅作用户报告的采集时间，不能作为服务端排序或可信时钟。

返回约定：

| HTTP | JSON／语义 | Android 行为 |
| --- | --- | --- |
| 201 | `{ "ok": true, "receipt": "同请求id" }`，新保存 | 显示成功并清理草稿 |
| 200 | 同上，相同 ID、相同内容已保存 | 视为成功，不重复生成记录 |
| 400 / 413 / 415 | `{ "ok": false, "code": "INVALID_PAYLOAD / PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE" }` | 保留内容，说明检查输入，不自动重试 |
| 409 | code=`ID_CONFLICT`，相同 ID 对应不同内容 | 不覆盖原反馈，提示作为新反馈提交 |
| 429 | code=`RATE_LIMITED`，Retry-After 秒数 | 保留内容并显示等待时间 |
| 503 | code=`UNAVAILABLE`，未启用／配置或写入错误 | 不声称已收到，允许稍后重试 |

405 返回 Allow: POST，不提供公开 GET 列表或凭编号读取正文的接口。响应使用 Cache-Control: no-store 和 X-Content-Type-Options: nosniff，不泄露 SQL、凭据或服务内部错误。

客户端只有在 HTTP 成功、ok=true、receipt 与当前 id 一致时报告成功。收到 HTML、JSON 破损、代理挑战页、超时或连接中断均属于“暂未确认收到”，保留同一请求供重试。

幂等行为：同 ID 同 payload 只存一条；同 ID 不同 payload 返回 409。并发插入需数据库唯一键约束，冲突后比对已保存 hash；不允许先查后插导致覆盖。幂等重试不重复消耗新投稿配额，但仍可受边缘请求限流。请求失败占用配额／退款策略必须明确并测试，不能用限流表的 last_id 代替“数据库已保存”证据。

## 6. D1 数据设计

新增迁移 `migrations/0002_app_feedback.sql`，实施时核对编号是否已被其他修改占用。不要修改已上线的 0001，也不要删除／重建 private_messages。

建议新表 `app_feedback`：

- id TEXT PRIMARY KEY：随机反馈 ID。
- schema_version INTEGER NOT NULL。
- problem、steps、expected TEXT NOT NULL（选填值统一空串）。
- diagnostic_level TEXT NOT NULL，CHECK none／basic／detailed。
- diagnostics_json TEXT NULL：经过服务端白名单规范化后的 JSON；none 必须 SQL NULL，不是字符串 "null"。
- payload_hash TEXT NOT NULL。
- status TEXT NOT NULL DEFAULT 'new'，CHECK new／in_progress／resolved。
- created_at、updated_at INTEGER NOT NULL，UTC 秒。
- 索引 (created_at, id) 与 (status, created_at, id)，服务分页和筛选。

新表 `app_feedback_limits`：limiter_key、count、last_id、expires_at，思路复用现有原子 SQL，添加业务命名空间，避免占用网页留言的配额。

限流摘要到期即不再参与判断，但物理清理可随后续请求完成；不要写成“到点自动删除”，除非真的实现了定时清理。反馈正文首版不自动删除、不提供公开导出，后续由用户决定保留期限。备份／导出只能放非公开目录。

独立反馈表的原因：现有 private_messages 承载网页信箱，结构和安全入口不同；复用同一个数据库和共有工具比强行复用表更稳妥。首版管理页只管理 App 投稿，网页信箱继续按原方式查看，合并收件箱另列后续需求。

## 7. 可执行任务清单

### T0：确认工程与环境，建立基线

- [ ] 阅读两个工程及祖先目录适用的 AGENTS.md；遵循本轮执行用户的授权范围。
- [ ] 核对网站与 App 的 Git root、分支、HEAD、工作区差异；不覆盖历史未提交改动。
- [ ] 网站查看 package.json、wrangler.toml、SETUP.md、20260923 v2 交接及本文列出的服务器文件。
- [ ] 运行网站现有 npm test，记录当前结果；如需构建验证，用 npm run build，注意会更新 dist 和外层单文件预览。
- [ ] 实施前记录 Android 有关测试基线，不把历史 XML 当成新执行。
- [ ] 使用 Wrangler 只读命令核对当前账号／Pages 项目／D1 绑定和迁移状态；不读取真实用户留言。
- [ ] 将本文复制到项目约定的 docs/superpowers/plans 位置并维护完成状态；不要为保存计划自动提交。

### T1：协议、迁移和服务端可复用基础

文件：新增 `docs/app-feedback-api-v1.md`、`migrations/0002_app_feedback.sql`；必要时增加 `server/http.js`／`server/feedback-validation.js`。

- [ ] 固定第 5 节协议与跨端 JSON fixture，覆盖 none／basic／detailed。
- [ ] 新迁移本地执行，从已有 0001 数据开始，验证旧表数据不受影响。
- [ ] 最小提取 JSON 响应、按字节读取、HMAC 等工具；旧 messages 的行为、错误码和验证策略不得悄悄改变。
- [ ] 先写校验和 SQL 测试，包括非法类型、超长、未知字段、空诊断、三态时间和并发。

完成标准：网站端可以正确规范化 Android fixture；现有 messages 测试仍通过。

### T2：实现 App 接收接口

文件：新增 `functions/api/app-feedback.js`、`server/app-feedback.js`、`tests/app-feedback.test.js`；按需更新 wrangler.toml 和 SETUP.md。

- [ ] 接收没有 Origin、没有 Turnstile 的合法原生请求；旧 messages 仍要求原有 Origin 和 Turnstile。
- [ ] 校验、限流、幂等、保存、回执按协议完成；本地使用合成测试数据。
- [ ] APP_FEEDBACK_ENABLED 缺省关闭；DB 或服务端限流密钥未就绪时 fail closed，不让接口降级成无限匿名写库。
- [ ] 并发安全，不回显正文，不记录 IP／token／密钥／诊断正文。
- [ ] 确认 `/api/*` 路由范围已包含新接口，不把源文件或数据库内容打包到 public／dist。
- [ ] 补全现有 API 回归，不把 App 接口加入全站管理员登录保护。

完成标准：本地 Functions + D1 收到一次投稿；相同投稿重试仍只一条；失败不会误报成功；网页信箱无回归。

### T3：Android 直接提交与诊断精简

现有修改点：

- `ui/settings/ReportIssueScreen.kt`、`ReportIssueViewModel.kt`、`IssueReportShare.kt`。
- `data/diagnostics/DiagnosticsCollector.kt`。
- `domain/feedback/DiagnosticSnapshot.kt`、`PreparedIssueReport.kt`、`IssueReportFormatter.kt`。
- `app/src/main/res/values/strings_feedback.xml`、`values-en/strings_feedback.xml`。
- 现有对应 JVM／UI 测试。

建议新增：

- `data/remote/FeedbackApi.kt`、显式的请求／响应 DTO。
- `data/repository/FeedbackRepository.kt`；通过 Hilt 注入独立开发者反馈 endpoint。
- 必要的本地反馈草稿／待重试请求存储（应用私有文件或现有存储能力）。

实施细节：

- [ ] 保留 Compose／Hilt／StateFlow 架构，UI 不直接发网络请求。
- [ ] 实现 none／basic／detailed 白名单，修正系统语言读取；系统分享和直接提交使用同一内容来源。
- [ ] 主按钮变成“提交反馈”；复现和预期折叠；诊断就地查看；增加中文和英文的上传目的地与隐私说明。
- [ ] 新的 PreparedSubmission 同时保存 id、typed payload、规范化指纹、供用户查看／备用分享的文本。不能只发送不易审查的原始 AppSettings 或凭据对象。
- [ ] 首次发送前将冻结的 payload + id 原子保存到 App 私有存储；同内容失败重试或进程恢复继续用同 ID。只依赖 SavedStateHandle 不能保证关闭重进或任务移除后的持久重试。
- [ ] 普通表单编辑保留草稿；修改正文或诊断后属于新投稿，生成新 ID，并提示前一次结果未知时新投稿可能形成另一条记录。不得每点一次重试重新采集时间／生成 ID。
- [ ] 无后台自动发送；进程恢复后提示用户手动重试，避免用户不知情时再上传。
- [ ] Coroutine 取消正常传播，忽略过期回调；网络超时设置有上限，提交时禁止双击及编辑。
- [ ] 不复用用户配置的 AI endpoint 或附带 AI Bearer Key；检查 OkHttp 拦截器，反馈正文不能进入调试／生产网络日志。
- [ ] 成功后删除待发送记录，失败保留；关闭诊断使草稿中的旧诊断失效，下一次新投稿也不能泄露它。
- [ ] 重建预览不触发上传；备用分享由用户显式点击。

完成标准：假后端验证请求字段，真实本地接收确认 POST 完整流程；不依赖分享／邮箱。设备测试按执行时授权进行。

### T4：开发者网页管理（登录后查看）

文件建议：`functions/api/admin/_middleware.js`、`functions/api/admin/feedback/index.js`、`functions/api/admin/feedback/[id].js`、`server/admin-feedback.js`、`tests/admin-feedback.test.js`、`src/admin/FeedbackAdmin.jsx`，修改 `src/main.jsx` 的入口分流。

- [ ] 优先使用 Cloudflare Access 限定开发者可登录的身份，不自己写永久共享密码，不将管理 Token 打包到前端。
- [ ] 页面使用真实路径 `/admin/feedback`，不要依赖 `#/admin` 做安全隔离。hash 不发送到服务器，Access 无法按 hash 配置保护。
- [ ] 在 main.jsx 最外层基于 pathname 挂载独立管理页；确认 Pages 静态回退能返回该页面壳，必要时增加精确 rewrite，不破坏 `/api/*`。
- [ ] 敏感数据只能通过 `/api/admin/*` 取得。服务端验证 Access JWT 签名、issuer、audience、有效期和适用身份；使用成熟库或官方集成，不只是检查某个请求头存在。
- [ ] Access 配置未完成时管理员 API 拒绝读取；不能先上线公开读取接口等以后补登录。
- [ ] 检查主域、brucehere.pages.dev、具体部署域名和 preview 域名都不能绕过管理鉴权。管理页的空壳是否公开不等同于数据是否公开。
- [ ] GET 列表支持 status 和基于 created_at + id 的游标，每页默认 20、最多 100；列表只含摘要、时间、状态、基本版本信息。
- [ ] GET 单条返回正文、复现／预期、分级诊断；PATCH 单条仅允许 new／in_progress／resolved 状态变更，并校验 Origin／CSRF 防护，禁止 GET 修改数据。
- [ ] 界面提供状态筛选、详情和标记处理；没有删除、批量导出、邮件回复、AI 自动分析等额外功能。
- [ ] React 文本输出，不执行用户 HTML／脚本；API no-store，未登录和过期会话正确处理，不把结果永久缓存到公共资源或浏览器持久存储。

完成标准：开发者能看反馈并修改状态；无权限、伪造／过期／错 audience JWT、绕过域名访问均得不到反馈正文。

若 Access 尚缺少账号侧配置，先保留管理员 API 关闭，并交付 T2 + T3；开发者通过 D1 控制台查看。后台登录身份和 Access team domain／audience 是这里可能需要补充的信息，不是实现上传的前置条件。

### T5：联调、兼容与失败路径验收

- [ ] 网站：npm test 全部通过，已有 messages 测试继续通过；新增迁移使用真实 SQLite 测试。
- [ ] Functions 本地运行，不把 Vite 页面能打开当成 API 已运行；本地 CF-Connecting-IP 仅测试环境模拟。
- [ ] Android：字段分级、采集竞态、冻结预览、同 ID 重试、进程恢复、取消、429、409、503、无 Key 依赖、非 JSON 响应等测试。
- [ ] 基础／详细／关闭诊断的实际 wire JSON 与网页显示一致；关闭诊断时 versionName／model 等不能从另一个顶层字段偷带回来。
- [ ] 断网保留草稿；数据库已保存但客户端没收到响应后重试只一条；数据库写入失败不能显示成功。
- [ ] 校验基本读取失败的 null 与历史时间无记录的区别。
- [ ] 验证 Android 系统中文＋App 英文；网站及 App 深浅主题、窄屏、键盘遮挡、TalkBack、错误状态和按钮状态。
- [ ] 登录失效、无权限和默认 Pages 域名的管理接口测试；提交接口不要求管理员登录。
- [ ] 保留首页／每日总结／现有反馈问卷和赞助功能；不引入用户 AI API 调用。
- [ ] 执行适当的 Gradle 测试／构建／Lint，记录真实命令、测试数量、失败项与新旧问题边界；未执行设备测试则明确只完成源码／编译验证。

### T6：部署、生产验收与交付

- [ ] 先提供本地可审查实现、迁移 SQL、测试结果和部署目标；依据执行对话授权再发布。
- [ ] 确认 DB 绑定、Secrets、APP_FEEDBACK_ENABLED、Access 配置在 Production／Preview 分别生效。现有文档称 Preview 未配置数据库和 Secrets，不假定继承生产，也不要让 preview 测试写进生产库。
- [ ] 需要远程预览时配置独立测试 D1；不方便配置时先完成本地验证，明确缺失云端预览证据。
- [ ] 生产迁移前核对库和迁移列表，私下备份／导出；新增表迁移先执行，保留旧表和数据。
- [ ] 从真正工程根执行 Pages 部署，仅 dist 作为静态产物，Functions 由 CLI 编译上传；不能上传外层 outputs、ZIP、源码、日志或凭据。
- [ ] 单独核对主域名与 API 响应，不用静态首页 200、message-config enabled 或 Pages 默认域可访问替代 App 反馈成功入库证据。
- [ ] 经授权用一条显著标记的合成反馈验证：App 提交 → D1 存在 → 管理页可读 → 状态可改；不遍历无关真实留言。
- [ ] 有关闭新接口的开关和前一个 Pages 部署回退方案。回退应用代码不自动回滚 D1；保留新增表，禁止靠 DROP TABLE 回退。
- [ ] 交付源文件清单、协议文档、测试结果、部署 ID、环境状态、APK 路径／SHA-256、未验收项和回退说明。
- [ ] 两个仓库分别核对差异和提交范围；只有获得提交／推送授权再操作，不夹带 `.idea`、ZIP、密钥或数据库导出。

## 8. 命令参考（执行时再核对，当前没有运行）

```powershell
Set-Location 'D:\MyWebBuild\outputs\brucehere'
git status --short
wrangler --version
wrangler whoami
wrangler pages project list
wrangler d1 list
npm test
npm run build
wrangler d1 migrations apply brucehere-messages --local
wrangler pages dev dist
```

本地开发密钥仅置于被忽略的 `.dev.vars`，使用测试值；本地不接触生产用户数据。Node 内存 SQLite 测试需要 Node 22.13+，具体以当前项目和运行环境为准。

生产操作（需要执行对话相应授权）：

```powershell
wrangler d1 migrations list brucehere-messages --remote
# 核对并备份后，才应用尚未执行的新增迁移。
wrangler d1 migrations apply brucehere-messages --remote
wrangler pages deploy dist --project-name brucehere --branch main
```

这些命令依赖执行时的 Wrangler 版本与配置；先通过 --help 核对用法，不能机械照抄造成错误环境写入。Secrets 交互设置，不放在命令参数、聊天或源码中。

Android 示例：

```powershell
Set-Location 'D:\Project\Focus-App-feedback'
git -c safe.directory=D:/Project/Focus-App-feedback status --short
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug --console=plain
.\gradlew.bat :app:lintDebug --console=plain
```

按改动范围先跑有关测试；依赖缓存充足可用 --offline。不要执行 connectedDebugAndroidTest 或自动安装 APK，除非用户授权设备操作。

## 9. 难点、可后移内容与实施顺序

| 工作 | 难度 | 主要原因 |
| --- | --- | --- |
| 页面精简、分级诊断 | 低到中 | 要保证显示、分享、实际上传字段一致，关闭诊断不能残留 |
| 基本 POST + D1 保存 | 中 | 现有服务器和绑定可复用，但原网页接口不能直接照搬 |
| 幂等和可靠重试 | 中到高 | 网络超时不等于没保存；进程恢复也不能生成重复反馈 |
| 匿名接口防刷 | 中到高 | APK 内没有可保密的通用密钥；要接受首版限流的边界 |
| 管理登录和多域名保护 | 中到高 | 主域加登录不够，Pages 默认／预览域也不能读到私密数据 |
| 云端联调与设备网络 | 中 | 依赖账号配置、生产／预览隔离以及手机实际网络可达性 |

最先完成 T1–T3，先让“App 直接提交、D1 控制台能审查”可用；接着做 T4 网页管理。不做截图上传、自动邮件、用户反馈追踪账号、消息通知、AI 分类或复杂工单系统。中国大陆等目标网络环境的实际可达性需用真实手机验证，不能因为 Cloudflare 已部署就承诺各网络都稳定。

## 10. 最终完成检查表

- [ ] 用户无需邮箱／分享即可提交。
- [ ] 一般只填问题描述；选填项不阻塞。
- [ ] 默认基础诊断；详细可选；完全关闭可用。
- [ ] 服务端保存确认后才显示成功，失败保留草稿。
- [ ] 同一次反馈在超时、重试及进程恢复后不重复保存。
- [ ] 原网页信箱的 Turnstile 和 Origin 保护保持有效。
- [ ] 管理数据不会从公开 GET、hash 路由、pages.dev 或 preview 绕过泄露。
- [ ] 新表迁移保留原留言；不泄露原始 IP、服务密钥或用户 AI 配置。
- [ ] 文档区分“实现／本地测试／云端上线／手机验收”，所有结论有证据。

## 11. 资料与本地证据入口

本地主要依据：网站 wrangler.toml、package.json、server/messages.js、functions/api/*、0001_messages.sql、tests/messages.test.js、SETUP.md、20260923 部署交接；Android 现有反馈 collector／model／ViewModel／Screen。

Cloudflare 官方参考（2026-09-24 查阅）：

- Pages Functions 的 D1 等资源绑定、Production／Preview 区分：https://developers.cloudflare.com/pages/functions/bindings/
- D1 预编译和绑定参数：https://developers.cloudflare.com/d1/worker-api/prepared-statements/
- Access JWT 服务端验证：https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json/

本文不包含凭据；数据库 ID、账号邮箱和旧 OAuth 内容无需复制给执行模型。真正需要补充的是执行时可用的云端权限，以及网页管理功能所需的允许登录身份、Access team domain 和 audience 配置。

---

## 实施状态（本机执行模型回填，2026-09-24）

本节只记录本轮实际执行过的命令与结果；不代表已部署、已推送或已做真机验收。

### T0 基线
- 网站工程 `D:\MyWebBuild\outputs\brucehere`：分支 `main`，HEAD `a66b524`，实施前工作区干净。
- Android 工程 `D:\Project\Focus-App-feedback`：分支 `huishenfeedback`，HEAD `a434cb1`，保留原有 `.idea`、ZIP 与反馈相关未跟踪文件；未执行 `git reset`／`clean`／无选择 `add`。
- 网站基线 `npm test`：12 项通过（Node v24.16.0）。

### T1／T2 网站实现与本地验证
- 新增 `docs/app-feedback-api-v1.md`、`migrations/0002_app_feedback.sql`、`server/http.js`、`server/feedback-validation.js`、`server/app-feedback.js`、`functions/api/app-feedback.js`、`tests/app-feedback.test.js`。
- `server/messages.js` 只提取共用工具，行为与错误码由原 12 项测试回归确认未变。
- `npm test`：32 项通过（原 12 项信箱回归 + 20 项 App 反馈／管理接口测试，均使用真实内存 SQLite）。
- `npm run build`：成功。
- `wrangler pages functions build functions --outdir .wrangler/functions-build --compatibility-date 2026-09-19`：`Compiled Worker successfully`。
- `wrangler d1 migrations apply brucehere-messages --local`：`0001` 与 `0002` 均 ✅。
- 本地 `wrangler pages dev dist --port 8788` + `.dev.vars`（忽略文件，测试值）实测：`POST /api/app-feedback` 返回 `201 {"ok":true,"receipt":"..."}`，本地 D1 `app_feedback` 出现一条 `status=new`、`diagnostic_level=basic` 的记录。

### T3 Android 实现与本地验证
- 新增／修改：`domain/feedback/FeedbackPayload.kt`（分级白名单、线协议 DTO、正反向映射）、`data/remote/FeedbackApi.kt`（独立 Retrofit endpoint + Hilt 模块）、`data/repository/FeedbackRepository.kt`、`data/feedback/FeedbackDraftStore.kt`、重写 `ReportIssueViewModel.kt` 与 `ReportIssueScreen.kt`、更新中英 `strings_feedback.xml`；`DiagnosticsCollector` 改用 `LocaleManagerCompat.getSystemLocales` 读取系统语言；`IssueReportFormatter` 按级别投影。
- 新增测试：`FeedbackPayloadTest`、`FeedbackRepositoryTest`、`FeedbackDraftStoreTest`，并重写 `ReportIssueViewModelTest`、更新 `IssueReportFormatterTest`。
- `./gradlew.bat :app:testDebugUnitTest`：397 项通过，0 失败。
- `./gradlew.bat :app:compileDebugAndroidTestKotlin :app:assembleDebug`：BUILD SUCCESSFUL。
- 未执行 `connectedDebugAndroidTest`、未安装 APK、未做真机／系统中文＋App 英文的设备验收。

### T4 开发者网页管理（代码已实现，等待 Access 配置）
- 新增 `server/access.js`（Access JWT 签名／issuer／audience／有效期／nbf 校验）、`server/admin-feedback.js`、`functions/api/admin/_middleware.js`、`functions/api/admin/feedback/index.js`、`functions/api/admin/feedback/[id].js`、`tests/admin-feedback.test.js`、`src/admin/FeedbackAdmin.jsx`，`src/main.jsx` 按真实路径 `/admin/feedback` 分流，`public/_redirects` 提供页面壳回退。
- 管理 API 在 Access 未配置时返回 503；测试覆盖合法／过期／错 audience／错 issuer／错签名的 JWT、列表分页筛选、详情、PATCH 状态与 Origin／Content-Type 校验。
- 生产需配置 `ACCESS_TEAM_DOMAIN`、`ACCESS_AUD`（可选 `ACCESS_JWKS_URL`）后才会启用；配置前管理页只显示未启用提示。

### T5／T6 未完成项
- 未部署：`wrangler pages deploy`、远程迁移、生产／Preview 环境开关与密钥核对均未执行，需执行对话授权。
- 未提交／推送：两个仓库均未执行 Git 提交或推送。
- 未做真机、Access 实登、主域与 pages.dev 绕过测试。
