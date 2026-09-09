# JMComic Downloader Mobile

[English](README.en.md) · [使用手册](UserManual.md) · [安全审查](SecurityReview.md) · [隐私说明](PRIVACY.md)

JMComic Downloader Mobile 是完全在 Android 手机上运行的本地下载、书库和 PDF 阅读应用。它不需要电脑、NAS、自建服务器或云端中转：手机直接完成在线检索、图片下载与重排、PDF 合成、目录建立和本地阅读。

> 本项目不托管、分发或附带任何漫画资源。第三方服务中的内容可能受到版权、年龄和地区限制；使用者应遵守所在地法律、服务条款及内容权利人的要求。

## 主要功能

- 支持作品 ID、`JM` 编号、作品链接和关键词检索；关键词结果按标题、ID、作者及标签相关性排序。
- 使用 WorkManager 执行后台下载，搜索结果卡片实时显示圆环进度。
- 下载的图片保存在应用私有目录，并按整部作品合成为带章节书签的 PDF。
- 本地书库可按标题、作者、标签、作品 ID 和章节名检索。
- 作品卡左滑可归档或删除；归档入口通过书库顶部继续下拉显示。
- 下载与 PDF 导出统一显示在任务页；删除任务记录不会删除作品或已导出的 PDF。
- PDF 阅读器支持左右分页、上下分页和连续滚动，并记忆阅读位置。
- 阅读时可点击画面收起或展开工具栏；翻页不会改变工具栏状态。
- 阅读亮度、暖色护眼等级和背景颜色只作用于当前应用窗口，不修改系统设置。
- 系统返回键和全面屏返回手势可依次关闭弹层、阅读器、作品详情和归档页。

## 隐私与安全边界

应用不包含账号登录、广告 SDK、统计 SDK、远程脚本、插件系统、WebView、shell 或动态代码加载。数据库、图片、PDF 和阅读设置都保存在手机本地；Android 系统备份已关闭。在线检索和下载需要网络，离线书库、目录与阅读不需要网络。

网络层只访问源码中维护的 HTTPS API 和图片主机列表，拒绝 HTTP 明文流量与重定向。文件目录由经过校验的数字作品 ID 和章节 ID 构造，删除和导出前均检查规范路径。完整说明见 [PRIVACY.md](PRIVACY.md) 和 [SecurityReview.md](SecurityReview.md)。

应用只申请以下权限：

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 在线检索和下载图片 |
| `POST_NOTIFICATIONS` | Android 13+ 显示后台任务进度 |
| `FOREGROUND_SERVICE` | 下载和导出期间保持任务运行 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 标记前台任务属于数据同步 |

导出 PDF 使用 Android 系统文件选择器，不申请全盘存储权限。

## 技术结构

| 层 | 实现 | 职责 |
| --- | --- | --- |
| 界面 | Kotlin、Jetpack Compose、Material 3 | 书库、检索、任务、详情和阅读交互 |
| 状态 | ViewModel、Kotlin Coroutines、Flow | 页面状态、检索与任务状态协调 |
| 本地数据 | Room | 作品、章节、页面、下载任务和导出任务 |
| 后台任务 | WorkManager | 可恢复下载、PDF 生成、文件导出和通知 |
| 网络 | OkHttp | 固定 HTTPS 主机访问、响应大小限制和重定向拒绝 |
| PDF | PdfBox-Android、Android PdfRenderer | PDF 合成、章节目录、校验和本地渲染 |

项目以 Kotlin 独立实现移动端所需的公开协议流程，不打包或执行上游 Python 运行时。来源与许可证说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 系统要求

- Android 8.0（API 26）或更新版本
- 在线检索和下载需要互联网
- 下载和 PDF 合成需要足够的手机存储空间
- 首次正式版可能与旧调试签名不兼容；升级前请先导出重要 PDF

## 安装

从 [Releases](https://github.com/Nuancunv/JMComic_downloader_Mobile/releases) 下载 `JMComic_downloader_Mobile-v1.2.0.apk`，核对发布页提供的 SHA-256 后在手机中打开安装。完整步骤、任务管理、归档、阅读与导出操作见 [使用手册](UserManual.md)。

## 从源码构建

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

正式签名通过以下环境变量注入，密钥和密码不能提交到仓库：

```text
JMCOMIC_STORE_FILE
JMCOMIC_STORE_PASSWORD
JMCOMIC_KEY_ALIAS
JMCOMIC_KEY_PASSWORD
```

未提供这些变量时，`assembleRelease` 只会生成未签名构建。持续集成只执行测试、Lint 和调试构建，不接触正式签名材料。

## 开源、归属与 AI 声明

本项目使用 MIT License。移动端项目由 [@Nuancunv](https://github.com/Nuancunv) 维护，协议实现参考了 [theabab23333/JMComic-Crawler-Python](https://github.com/theabab23333/JMComic-Crawler-Python) 及其原作者 [hect0x7/JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python)。上游项目同样采用 MIT License。

本项目在人工提出需求、选择方案并审核结果的前提下，由 OpenAI Codex 协助进行架构设计、代码实现、静态审查、测试和文档编写。AI 不作为维护者或版权所有者；最终发布和维护责任由项目维护者承担。

## 反馈

请通过 [GitHub Issues](https://github.com/Nuancunv/JMComic_downloader_Mobile/issues) 提交可复现的问题。安全问题请按 [SECURITY.md](SECURITY.md) 中的方式处理，避免在公开 Issue 中披露可利用细节或个人数据。
