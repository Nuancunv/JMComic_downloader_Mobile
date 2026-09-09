# Contributing / 参与贡献

Bug reports and focused pull requests are welcome. Before opening an issue, search existing issues and reproduce the problem on the latest release. Include the Android version, app version, input category (ID, URL, or keyword), expected result, actual result, and non-sensitive logs when available.

欢迎提交缺陷报告和范围明确的 Pull Request。提交前请搜索现有 Issue，并在最新版本复现问题。请提供 Android 版本、应用版本、输入类型（ID、链接或关键词）、预期结果、实际结果及不含敏感信息的日志。

Code changes should keep the app fully local, avoid broad Android permissions, preserve canonical-path checks, use the HTTPS host allowlist, and include a focused test for non-trivial logic. Run the following before submitting:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Never submit comic content, credentials, cookies, signing files, generated APKs, local SDK paths, build outputs, or emulator artifacts.

请勿提交漫画内容、凭据、Cookie、签名文件、生成的 APK、本机 SDK 路径、构建产物或模拟器产物。
