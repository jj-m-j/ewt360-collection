# EWT360 答案查询（Android）

一个基于 **MIUIX（Miuix / HyperOS 风格）** 的 Android 原生应用：登录升学 e 网通（EWT360）后，自动获取登录态，扫描试卷与题目，**只查询并展示**标准答案、解析、知识点与图片。

> ⚠️ 本应用仅用于个人学习与查漏补缺，**绝不自动填写、不自动提交、不自动交卷**，不调用任何修改试卷答案的接口。

---

## ✨ 功能

- **WebView 登录 + 自动获取 token**
  - 应用内打开 EWT360 官方登录页，用户正常登录
  - 自动从 WebView Cookie（`CookieManager`）读取 `token` Cookie，支持 HttpOnly
  - 读取后调用官方 `baseinfo` 接口校验有效性，**无需手动复制 token**
  - token 使用 **Android Keystore（AES/GCM）加密**后本地保存，不落明文

- **试卷列表（EWT-TOOL-main 逻辑）**
  - 获取用户作业列表 → 按天扫描任务 → 过滤独立试卷（`contentTypeName` 含「试卷」）
  - 按作业分组展示，支持下拉刷新

- **粘贴链接直接查询**
  - 支持 `web.ewt360.com/answer-pc/exam/answer?paperId=…&platform=…&bizCode=…`
  - 自动解析 `paperId / platform / bizCode / homeworkId` 并查询对应试卷

- **题目列表（兼容题组 / 非题组）**
  - 题组接口 `getAnswerSheetSubGroup`（带 `groupName` 分组）
  - 失败自动回退非题组接口 `answerSheetInfo`
  - 统一保存：`questionId / questionNumber / questionType / groupName / cateId / subjective`

- **答案获取与展示（ewt-getanwser.js 逻辑）**
  - 逐题调用 `simple/question/analysis`，解析 `rightAnswer / analyse / knowledges / attachmentImages`
  - 选择题答案字母、填空题/主观题文本、公式图（Wirisformula）与附件图均支持
  - 并发限流（信号量 4），显示进度「正在获取答案 N / M」
  - 失败题目支持单题重试与批量重试，单题失败不影响整体

- **MIUIX / HyperOS 风格 UI**
  - 基于 Miuix（`top.yukonga.miuix.kmp`）原生组件：`TopAppBar`、`Scaffold`、`Card`、`Button`、`TextField`、`Badge`、`PullToRefresh`、`LinearProgressIndicator` 等
  - MIUI 动效节奏：TopAppBar 折叠、列表按压反馈、展开/收起动画、页面转场

---

## 🛡️ 安全与隐私

- 不硬编码任何 token / 账号 / 密码
- 不将登录信息上传到任何第三方服务器（所有请求直连 EWT360 官方接口）
- token 加密存储于 Android Keystore；应用卸载即失效
- `allowBackup=false`，禁止系统备份携带登录态
- 仓库中不包含任何隐私文件、密钥或 Cookie

---

## 🏗️ 技术架构

```
UI (Compose + Miuix)
      ↓
ViewModel (StateFlow)
      ↓
Repository (EwtRepository)
      ↓
EWT API Client (OkHttp + kotlinx.serialization)
      ↓
EWT360 官方接口
```

### 核心数据流

```
扫描试卷（EWT-TOOL-main 逻辑）
  作业列表 getStudentHomeworkInfo → studentHomeworkDistribution → pageHomeworkTasks
  ↓
打开试卷 getReportId（bizCode=201 视图态，不提交）
  ↓
题目列表 getAnswerSheetSubGroup（题组）/ answerSheetInfo（非题组）
  ↓
逐题答案 simple/question/analysis
  → rightAnswer / analyse / knowledges / attachmentImages
```

> 说明：`bizCode=201` 为查看态。参考脚本中涉及提交的接口（`submitAnswer` / `submitpaper` 等）**一律未实现**。

### 关键目录

```
app/src/main/kotlin/com/ewt/answer/
├── MainActivity.kt            # 入口 + Edge-to-Edge
├── EwtApplication.kt          # Coil3 图片加载初始化
├── data/
│   ├── EwtApi.kt              # 网络传输层 + 端点封装
│   ├── EwtRepository.kt       # 仓库：试卷/题目/答案
│   ├── Models.kt              # 数据模型 + 链接解析
│   ├── HtmlCleaner.kt         # HTML 清洗（移植自油猴脚本）
│   ├── SecureTokenStore.kt    # Keystore 加密存储
│   ├── TokenExtractor.kt      # Cookie → token
│   └── AppContainer.kt        # 轻量依赖注入
└── ui/
    ├── AppRoot.kt             # 主题 + 页面导航
    ├── LoginScreen.kt         # 登录 + 自动 token
    ├── HomeScreen.kt          # 试卷列表 + 链接查询
    ├── QuestionsScreen.kt     # 题目 + 答案展示
    └── components/            # RichHtmlText / AnswerDetail / 状态徽标
```

---

## 🛠️ 构建方法

### 环境要求

- JDK 21
- Android SDK（`platforms;android-37`、`build-tools;37.0.0`）
- Gradle 9.6.1（项目自带 wrapper）

### 本地构建

```bash
git clone https://github.com/jj-m-j/ewttest.git
cd ewttest

# 仓库不包含 gradle-wrapper.jar（二进制文件由 CI 自动恢复），本地先补一次：
gradle wrapper --gradle-version 9.6.1   # 或直接用已安装的 Gradle 9.6.1：gradle assembleDebug

# 调试包（可安装）
./gradlew assembleDebug

# 产物位置
# app/build/outputs/apk/debug/app-debug.apk
```

> 提示：Android Studio 打开工程时若提示 wrapper 缺失，选择本机 Gradle 发行版或执行上面的
> `gradle wrapper` 命令补全即可。

## ⚙️ GitHub Actions

仓库已配置 `.github/workflows/build.yml`：

| 触发方式 | 行为 |
| --- | --- |
| push / PR 到 `main` | `assembleDebug` → 上传 APK Artifact |
| 推送 `v*` 标签 | 额外自动创建 GitHub Release 并附带 APK |
| 手动 `workflow_dispatch` | 随时手动构建 |

流程：`push → Setup JDK 21 → Setup Gradle → 恢复 wrapper → ./gradlew assembleDebug → 上传 APK Artifact`

## 📦 APK 获取方式

1. **Actions Artifact**：进入仓库 `Actions` 页 → 选择最近一次构建 → 下载 `ewt360-answer-debug-apk`
2. **GitHub Release**：推送 `v*` 标签后，`Releases` 页面直接下载 APK
3. **本地构建**：见上文「本地构建」

---

## 📝 免责声明

本应用仅用于学习交流与个人查漏补缺，请遵守所在学校与 EWT360 平台的相关规定。开发者不对任何违规使用造成的后果负责。
