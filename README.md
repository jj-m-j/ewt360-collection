# Fuck Ewt（去你妈的e网通）

一个基于 **MIUIX（Miuix / HyperOS 风格）** 的 Android 原生应用：登录升学 e 网通（EWT360）后，自动获取登录态，扫描试卷与题目，查询并展示标准答案、解析、知识点与图片；支持一键刷卷、WebView 刷课助手。

> ⚠️ 本应用仅用于个人学习与查漏补缺，请遵守所在学校与 EWT360 平台的相关规定。

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
  - 选择题答案字母、填空题/主观题文本、公式图与附件图均支持
  - 并发限流（信号量 4），显示进度「正在获取答案 N / M」
  - 失败题目支持单题重试与批量重试，单题失败不影响整体

- **一键刷今日（opt.js 逻辑）**
  - 滚轮选择日期，批量打开 → 取答案 → 提交 → 交卷 → 自批（`submitCorrected`）
  - 客观题系统阅卷（`revision=false`），主观题满分自批（`revision=true`）

- **课程刷课助手（EWT360-Helper 逻辑）**
  - WebView 打开 site-study 学习页并注入刷课脚本：自动跳题 / 自动连播 / 自动过检 / 2倍速 / 刷课模式
  - 原生刷课待抓包视频课接口后实现

- **MIUIX / HyperOS 风格 UI**
  - 基于 Miuix（`top.yukonga.miuix.kmp`）原生组件
  - 三条杠筛选弹层：Circle → Capsule → Dialog 连续 Morph 动效
  - 液态玻璃顶栏 / 悬浮底栏（Liquid Glass）

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
  作业列表 getStudentHomeworkInfo → 日期统计 → pageHomeworkTasks / queryStudentLessonStudyGuideAndPractice
  ↓
打开试卷 initReport（205 作业 / 204 课后习题 / 201 查看态多候选）
  ↓
题目列表 getAnswerSheetSubGroup（题组）/ answerSheetInfo（非题组）
  ↓
逐题答案 simple/question/analysis
  ↓
提交 submitanswer（客观 revision=false / 主观 revision=true）→ submitpaper → submitCorrected
```

## 🛠️ 构建方法

### 环境要求

- JDK 21
- Android SDK（`platforms;android-37`、`build-tools;37.0.0`）
- Gradle 9.6.1（项目自带 wrapper）

### 本地构建

```bash
git clone https://github.com/jj-m-j/ewt360-collection.git
cd ewt360-collection

# 仓库不包含 gradle-wrapper.jar（二进制文件由 CI 自动恢复），本地先补一次：
gradle wrapper --gradle-version 9.6.1

# 调试包（可安装）
./gradlew assembleDebug
```

## ⚙️ GitHub Actions

仓库已配置 `.github/workflows/build.yml`：push / PR 到 `main` 触发 `assembleDebug` → 上传 APK Artifact；推送 `v*` 标签额外创建 Release。

## 📝 免责声明

本应用仅用于学习交流与个人查漏补缺，请遵守所在学校与 EWT360 平台的相关规定。开发者不对任何违规使用造成的后果负责。
