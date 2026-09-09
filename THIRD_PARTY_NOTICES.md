# Third-party notices

## 第三方说明

本项目的来源协议与图片分割行为根据 [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) 独立移植，首次审查基于提交 `f2adb337881d96de65c6862935c9f1c32ce847f4`。上游项目由 hect0x7 维护并采用 MIT License。Android 应用没有打包或执行上游 Python 运行时、插件或工作流。

应用使用 AndroidX、Jetpack Compose、Room、WorkManager、OkHttp 和 PDFBox-Android。精确依赖记录在 `app/gradle.lockfile`，构件及元数据摘要记录在 `gradle/verification-metadata.xml`。各依赖仍受其自身许可证约束；PDFBox-Android 基于 Apache PDFBox 并采用 Apache License 2.0。

## JMComic-Crawler-Python

The source protocol and image segmentation behavior were independently ported from [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python), originally reviewed at commit `f2adb337881d96de65c6862935c9f1c32ce847f4`. The project is distributed under the MIT License:

```text
MIT License

Copyright (c) 2023 hect0x7

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Android and JVM libraries

The application uses AndroidX libraries, Jetpack Compose, Room, WorkManager, OkHttp and PDFBox-Android. Exact resolved artifacts are locked in `app/gradle.lockfile`; artifact and metadata checksums are recorded in `gradle/verification-metadata.xml`. Their respective license texts and notices remain governed by the upstream packages. PDFBox-Android is based on Apache PDFBox and is licensed under Apache License 2.0.
