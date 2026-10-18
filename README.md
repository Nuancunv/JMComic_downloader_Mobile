# JMComic 本地书库

[English](README.en.md) · [更新记录](docs/releases/RELEASE_NOTES.md) · [使用说明](docs/user/UserManual.md)

一个在安卓手机上用的下载和阅读小工具。搜到想看的作品，下载到本地，之后没网也能看；需要的话，还能导出成带章节目录的 PDF。

不用电脑，也不用搭服务器，手机自己搞定。Android 8.0 及以上可用。

## v1.3.0的改动内容

**v1.3.0 已定档。** 这一版主要把搜索、界面和下载重新优化了一遍：

- 搜索更精准了，书名、编号、作者、标签都能找，繁简写法也尽量兼容。
- 优化了UI界面。
- 优化了下载速度。
- 优化了PDF和内容导出。

具体改动放在[更新记录](docs/releases/RELEASE_NOTES.md)，这里就不细说了。

## 安装方法

从 [Releases](https://github.com/Nuancunv/JMComic_downloader_Mobile/releases) 下载 APK，在Android手机上安装。

下载和合成 PDF 都需要存储空间，需要给手机预留空间。

## 其他几句

没有广告，也不做使用统计。书库、图片和阅读进度留在手机里，导出位置最好自己选择。隐私细节见[这里](PRIVACY.md)。项目不提供漫画资源，需要什么请自行搜索下载。

想自己编译的话，需要 JDK 17 和 Android SDK 35：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```
## 声明

此项目（JMComic Downloader Mobile）是个人为了兴趣而开发，仅用于学习和测试。

`本项目不托管、分发或附带任何漫画资源。第三方服务中的内容可能受到版权、年龄和地区限制；使用者应遵守所在地法律、服务条款及内容权利人的要求。`


项目由 [@Nuancunv](https://github.com/Nuancunv) 维护，开发过程中使用了 Codex 协助。协议参考 [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python)，具体归属见[第三方说明](THIRD_PARTY_NOTICES.md)。代码按 [MIT](LICENSE) 开源。

遇到问题? => [Issue](https://github.com/Nuancunv/JMComic_downloader_Mobile/issues)，最好带上版本号和复现步骤。
