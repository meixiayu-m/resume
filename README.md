# 简历 Agent

仅供个人在 Android 手机上使用的简历接单工作台。App 不需要电脑、局域网或自建服务器，简历任务保存在手机 App 私有目录。

## 当前流程

1. 新建任务，选择特定岗位、只有岗位方向或通用优化三种模式。
2. 导入 DOCX、PDF、TXT、JPG 或 PNG 简历，也可以直接粘贴简历文字。
3. 特定岗位模式可以粘贴 JD，也可以从 DOCX、PDF、TXT 或招聘截图导入。
4. 选择文本 AI，生成岗位适配诊断。诊断包含分项评分、原文证据、问题清单和待确认事实。
5. 系统先提取事实账本，补充客户确认过的事实后再生成逐段改写稿。
6. 逐段选择“采用改写、保留原文、不导出”。含有新增数字、职责升级或无依据关键词的段落会默认提示核实。
7. 每次生成或客户返修都会保留新版本，可以恢复历史版本继续修改并锁定最终版。
8. 导出只生成客户要用的 DOCX。PDF 或图片来源会生成应用模板版；DOCX 来源会保留原模板，并以已审核通过的章节内容生成逐段替换。只有诊断明确建议调整顺序时才显示调整操作。
9. 通过 Android 系统分享面板把交付文件发到网盘、微信或其他 App。

在接入页直接点删除，会先说明清理内容，再进行第二次永久删除确认。删除后会清理任务记录、原简历、JD 文件、全部版本和该任务的导出目录。

输入框和逐段编辑会在停笔约 0.6 秒后自动保存。诊断结果、问题清单、改写稿、核实清单和溯源表会在 AI 返回后立即写入本机任务文件。切到聊天 App 询问客户后再回来，进度不会丢失。

诊断页会生成可以直接发给客户的核实消息。默认最多复制最重要的 3 个问题，也可以查看完整版；内部核实清单保留 0 至 6 条。开场和结尾会结合本次具体问题生成，复制文本统一使用无缩进的编号格式。称呼可以按目标职级自动判断，也可以固定为同学、直接称呼或正式称呼。

审核页默认只显示原文、改写后文字和一到两句修改原因。修改收益、完整依据和逐句变化放在高级信息中；风险提示只在确实存在风险时出现。

以下内容仅保存在任务记录中，不会写进最终简历：

- 需客户核实清单
- 数据溯源表

## 支持的 AI

设置页可以同时保存多组 API Key，并分别选择当前文本 AI 和 OCR AI：

- 智谱
- Kimi
- DeepSeek
- 千问
- 自定义 OpenAI 兼容接口

接口地址、模型列表地址、文本模型和视觉模型都可以修改。设置页可以通过 `/models` 接口获取模型列表，再直接指定为文本模型或 OCR 模型。接口不返回列表时仍然可以手动填写模型 ID。

双模型审核默认关闭。开启后，主模型负责改写，第二个模型只审核数字、职责边界、项目归属、成果和无依据关键词，不自动修改正文。

DeepSeek 默认没有配置视觉模型，因此只适合文字诊断和改写。扫描 PDF 或图片 OCR 应选择智谱、千问、Kimi 或填写其他支持图片输入的兼容模型。

## 事实保真规则

系统提示词和本地 FactGuard 双层约束 AI：

- 禁止编造数字、职级、团队规模、项目归属、项目周期和成果。
- 禁止把“参与、协助、配合”升级为“主导、负责、牵头、独立完成”。
- 禁止添加原简历和客户补充中不存在的 JD 关键词。
- 只允许调整语序、删除空话和正式化表达。
- “动作加结果”中的结果必须有原文依据。
- 输出不使用 emoji 和感叹号。
- 每段改写需要提供原文依据。
- 新增数字、职责升级和无依据关键词会被本地规则拦截并加入核实清单。

AI 仍然可能理解错误，交付前必须人工逐项核实。

## 隐私

- 项目 JSON、简历副本和导出文件保存在 App 内部存储。
- Android 自动备份已关闭。
- 每个 AI 平台的 API Key 使用 Android Keystore 加密后保存在本机。
- 删除任务会删除任务记录、原简历副本和该任务的中间文件。
- 删除任务连续确认两次后，会同时删除全部历史版本和导出文件。
- 需要询问客户的问题和核实清单可以一键复制到剪贴板。
- 分析、改写和 OCR 时，相关文字或图片会发送给你选择的第三方 AI 平台。接单前应取得客户对文件处理的同意。

## 文件格式

- DOCX：在手机本地解析正文 XML。
- TXT：在手机本地读取。
- PDF：使用 Android PdfRenderer 转成页面图片，再通过视觉模型 OCR。当前最多处理前 8 页。
- JPG、PNG：通过视觉模型 OCR。
- DOCX 导出：生成可编辑 Office Open XML 文档。
- DOCX 导出：保留原 DOCX 的照片、字体、表格和模板结构，只替换已审核的文字；文件名按目标岗位生成“修订版”。
- 调整顺序版：只有诊断确认当前顺序明显影响岗位重点时才提供建议。应用后尝试按原 DOCX 中可识别的标题移动完整内容块并保留原结构；无法一一对应时不应用并提示原因。
- PDF 或图片来源：生成应用模板的可编辑 DOCX，不额外生成 PDF。

DOCX 原版式修订会将用户已审核采用的章节内容映射到原文段落，并保留原顺序、照片和模板。应用模块顺序后，会同时提供原顺序版和尝试保留原模板的调整顺序版；无法安全对应段落或标题时会提示，不会静默导出未修改文件。扫描 PDF 和图片没有可编辑的 DOCX 结构，只能使用应用模板生成 DOCX。

## 通过 GitHub Actions 编译 APK

本地工作电脑不执行 Gradle 构建。把整个 `ResumeAgent` 项目上传到 GitHub 仓库后：

1. 打开仓库的 Actions 页面。
2. 选择 `Resume Agent Build`。
3. 点击 `Run workflow`，或向 `main` / `master` 推送代码。
4. 构建完成后，在该次运行的 Artifacts 中下载 `resume-agent-debug-apk`。
5. 解压后安装 `app-debug.apk`。

工作流会执行单元测试和 `:app:assembleDebug`。

工作流文件位于 [android-build.yml](.github/workflows/android-build.yml)。

第一次安装不要求签名 Secret。为了以后用新 APK 覆盖旧版本并保留 App 内任务，建议创建一份固定的 Android Keystore，并添加以下 GitHub Secrets：

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

四个 Secret 配齐后，工作流会使用同一签名。每次发布新 APK 前需要递增 [app/build.gradle.kts](app/build.gradle.kts) 中的 `versionCode`。

## 目录

```text
app/src/main/java/com/resumeagent/app/
├── ai/AiClient.kt                 多平台 OpenAI 兼容请求、诊断与改写
├── ai/AiResultParser.kt           JSON 清理与解析
├── ai/ModelCatalog.kt             OpenAI 兼容模型列表查询
├── data/DocumentImporter.kt       DOCX、PDF、图片和文本导入
├── data/ProjectJson.kt            本地任务序列化
├── data/ProjectStore.kt           本机任务与文件存储
├── data/SecureSettings.kt         多平台 Key 与模型配置
├── domain/FactGuard.kt            数字、职责边界和关键词风险拦截
├── domain/RuleChecker.kt          联系方式、重复、占位符等规则检查
├── domain/TextDiffEngine.kt       原文与修订稿逐句对照
├── domain/ResumeModels.kt         任务、诊断、改写和溯源数据结构
├── export/DocumentExporter.kt     保留模板或应用模板生成 DOCX
├── ui/                            Compose 界面与状态
└── MainActivity.kt
```
