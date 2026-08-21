# ewt360-collection · EWT360 工具聚合

> 一个 App，聚合多个 EWT360 开源项目的能力：**答案查询 + 一键刷卷 + 课程刷课**，全部打包进一个 MIUIX 风格的 Android 应用。

本项目是**多个开源 EWT360 工具的聚合产物**：把社区里分散的答案脚本、刷卷脚本、刷课脚本的能力整合进一个原生 App，并加上统一的 MIUIX / HyperOS 风格界面。

> ⚠️ 本项目仅用于个人学习与查漏补缺，请遵守所在学校与 EWT360 平台的相关规定。

---

## 🧩 聚合了什么

| 能力 | 来源项目 | 在本项目的形态 |
|---|---|---|
| 试卷扫描 / 答案查询 | [EWT-TOOL](https://github.com/ZZ0YY/EWT-TOOL) | 原生 Repository 实现（作业列表 → 题目 → 逐题答案） |
| 一键刷今日 / 交卷自批 | [GetEWTAnswers](https://github.com/zhicheng233/GetEWTAnswers/) | 选日期批量刷卷：取答案 → 提交 → 交卷 → 自批 |
| 课程刷课（竞态爆发） | [ewt360-brush](https://github.com/Zxxaq1478359473/ewt360-brush) | Chaquopy 内嵌 Python 脚本：N 路并行 + 竞态爆发 + WAF 冷却 |
| MIUIX 风格 UI | [miuix](https://github.com/compose-miuix-ui/miuix) | 全套 Compose 组件：液态玻璃底栏、三条杠 Morph 弹层 |

## ✨ 功能

- **WebView 登录 + 自动获取 token**：应用内登录 EWT360，自动从 Cookie 读取 token（支持 HttpOnly），Android Keystore 加密保存，无需手动复制
- **试卷列表**：作业列表 → 按天扫描 → 过滤独立试卷，下拉刷新
- **粘贴链接查询**：粘贴 `web.ewt360.com` 试卷链接直接查答案
- **题目 / 答案 / 解析**：题组与非题组兼容，公式图（SVG）与附件图全支持，失败单题重试
- **一键刷今日试卷**：滚轮选日期，批量取答案 → 提交 → 交卷 → 自批
- **课程刷课**：扫描未刷课程 → 指定课程队列 → 并行刷课（并发路数 / QPS / 爆发可调）→ 暂停 / 继续
- **强制刷**：课时显示已完成但实际没看完时，开关后强制重刷
- **调试模式**：App / 试卷日志 + 刷课日志双通道，一键导出

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

刷课部分：Kotlin 通过 **Chaquopy** 内嵌 Python（`app/src/main/python/`），复用社区刷课脚本的成熟逻辑。

## 📦 构建

```bash
git clone https://github.com/jj-m-j/ewt360-collection.git
cd ewt360-collection
gradle wrapper --gradle-version 9.6.1   # 本地需补 wrapper jar
./gradlew assembleDebug
```

GitHub Actions 已配置：push 到 `main` 自动构建并发布 nightly Release。

## ⬇️ 下载

前往 [Releases](https://github.com/jj-m-j/ewt360-collection/releases) 下载最新 APK。

## 🙏 致谢

本项目聚合了以下开源项目，衷心感谢原作者：

- [EWT-TOOL](https://github.com/ZZ0YY/EWT-TOOL) — 答案查询
- [GetEWTAnswers](https://github.com/zhicheng233/GetEWTAnswers/) — 一键刷卷
- [ewt360-brush](https://github.com/Zxxaq1478359473/ewt360-brush) — 课程刷课
- [miuix](https://github.com/compose-miuix-ui/miuix) — MIUIX 风格 UI

## 📝 免责声明

本应用仅用于学习交流与个人查漏补缺，请遵守所在学校与 EWT360 平台的相关规定。开发者不对任何违规使用造成的后果负责。
