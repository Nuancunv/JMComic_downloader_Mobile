# Third-party notices

## 第三方说明

本项目的来源协议与图片分割行为根据 [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) 独立移植，首次审查基于提交 `f2adb337881d96de65c6862935c9f1c32ce847f4`。上游项目由 hect0x7 维护并采用 MIT License。Android 应用没有打包或执行上游 Python 运行时、插件或工作流。

API 兼容更新参考上游 v2.7.9 / [`303904252f586ef0123f468feac2b890c9cba8d8`](https://github.com/hect0x7/JMComic-Crawler-Python/commit/303904252f586ef0123f468feac2b890c9cba8d8)，包括 UTF-8 BOM 解析兼容和客户端协议版本 2.1.7。详情、章节的业务校验与解密失败切线由本项目独立实现。

应用使用 AndroidX、Jetpack Compose、Room、WorkManager、OkHttp 和 PDFBox-Android。精确依赖记录在 `app/gradle.lockfile`，构件及元数据摘要记录在 `gradle/verification-metadata.xml`。各依赖仍受其自身许可证约束；PDFBox-Android 基于 Apache PDFBox 并采用 Apache License 2.0。

## JMComic-Crawler-Python

The source protocol and image segmentation behavior were independently ported from [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python), originally reviewed at commit `f2adb337881d96de65c6862935c9f1c32ce847f4`.

The API compatibility update on 2026-10-17 was checked against v2.7.9 / `303904252f586ef0123f468feac2b890c9cba8d8`: v2.7.8 BOM handling and client protocol version 2.1.7 were independently ported. Failover on metadata business-validation and decryption errors is additional Android-side hardening.

The project is distributed under the MIT License:

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

## OpenCC character dictionaries

Search includes unmodified `TSCharacters.txt` and `STCharacters.txt` from [OpenCC](https://github.com/BYVoid/OpenCC), pinned to commit `8cf737a4e193db27d675b2ce9867d84aace33745`. These dictionaries are licensed under Apache License 2.0. Their original headers are retained, and the complete license is bundled at `app/src/main/resources/search/OpenCC-LICENSE.txt`. No OpenCC engine or phrase conversion code is included; the app independently reads the first character-conversion candidate as a search variant, preserving the original query.

## Android and JVM libraries

The application uses AndroidX libraries, Jetpack Compose, Room, WorkManager, OkHttp and PDFBox-Android. Exact resolved artifacts are locked in `app/gradle.lockfile`; artifact and metadata checksums are recorded in `gradle/verification-metadata.xml`. Their respective license texts and notices remain governed by the upstream packages. PDFBox-Android is based on Apache PDFBox and is licensed under Apache License 2.0.

## Noto Serif SC

Chinese serif headings bundle a static Medium 500 instance of Noto Serif SC from [Google Fonts](https://github.com/google/fonts/tree/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/notoserifsc), licensed under the SIL Open Font License 1.1. Copyright 2012 Google Inc. All Rights Reserved. The instance was generated with fontTools 4.67.0 from `NotoSerifSC[wght].ttf` using wght=500, without glyph subsetting. The complete license is bundled at `fonts/NotoSerifSC-OFL.txt`, and source/derivative SHA-256 records at `fonts/NotoSerifSC-SOURCE.txt`. These files live under `app/src/main/resources/fonts/` in the source tree; the font is `app/src/main/res/font/noto_serif_sc_medium.ttf`.
