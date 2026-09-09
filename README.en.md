# JMComic Downloader Mobile

[简体中文](README.md) · [User guide](UserManual.en.md) · [Security review](SecurityReview.en.md) · [Privacy](PRIVACY.en.md)

JMComic Downloader Mobile is a local Android downloader, library, and PDF reader. The complete workflow runs on the phone: online search, image download and descrambling, PDF creation, chapter indexing, and reading. It does not require a PC, NAS, self-hosted server, or cloud relay.

> This project does not host, distribute, or bundle comic content. Content exposed by third-party services may be subject to copyright, age, and regional restrictions. Users are responsible for following applicable law, service terms, and content-owner requirements.

## Features

- Search by work ID, `JM` number, supported work URL, or keyword, with relevance ranking across title, ID, author, and tags.
- Run long downloads through WorkManager and show live circular progress on search results.
- Keep processed images in app-private storage and combine each work into one PDF with chapter bookmarks.
- Search the local library by title, author, tags, work ID, or chapter name.
- Swipe a library card left to archive or delete it; reveal the archived section by pulling down again at the top.
- Show downloads and PDF exports together in Tasks. Removing a task record never removes the work or exported file.
- Read PDFs with horizontal paging, vertical paging, or continuous scrolling and saved reading position.
- Tap the page to hide or reveal reading controls; page gestures preserve the current control visibility.
- Apply per-window brightness, warm eye-comfort overlays, and background colors without changing system settings.
- Use Android Back and full-screen back gestures to close overlays and navigate to the previous app screen.

## Privacy and security boundary

The app has no account login, ads, analytics, remote scripts, plugin runtime, WebView, shell access, or dynamic code loading. Its Room database, images, PDFs, and reader preferences stay on the phone, and Android cloud backup is disabled. Network access is required only for online search and new downloads.

The network client uses a source-maintained HTTPS allowlist, rejects redirects and cleartext traffic, and limits response sizes. Internal paths are derived from validated numeric work and chapter IDs, with canonical-path checks before export and deletion. See [PRIVACY.en.md](PRIVACY.en.md) and [SecurityReview.en.md](SecurityReview.en.md) for details.

| Permission | Purpose |
| --- | --- |
| `INTERNET` | Online search and image download |
| `POST_NOTIFICATIONS` | Background-task progress on Android 13+ |
| `FOREGROUND_SERVICE` | Keep downloads and exports running |
| `FOREGROUND_SERVICE_DATA_SYNC` | Declare foreground data-sync work |

PDF export uses Android's system document picker and does not require broad storage access.

## Architecture

| Layer | Technology | Responsibility |
| --- | --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3 | Library, search, tasks, details, and reader interactions |
| State | ViewModel, Kotlin Coroutines, Flow | UI state, searches, and task coordination |
| Storage | Room | Works, chapters, pages, downloads, and exports |
| Background work | WorkManager | Resumable downloads, PDF creation, export, and notifications |
| Network | OkHttp | HTTPS allowlist, response limits, and redirect rejection |
| PDF | PdfBox-Android, Android PdfRenderer | Creation, bookmarks, validation, and local rendering |

The Android protocol flow is independently implemented in Kotlin. The upstream Python runtime is neither bundled nor executed. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Requirements and installation

- Android 8.0 (API 26) or later
- Internet access for search and new downloads
- Enough local storage for both processed images and PDFs

Download `JMComic_downloader_Mobile-v1.2.0.apk` from [Releases](https://github.com/Nuancunv/JMComic_downloader_Mobile/releases), compare its SHA-256 value with the release notes, and open it on the phone. The first official build may not update an older debug-signed build; export important PDFs before uninstalling a test build. See the [user guide](UserManual.en.md) for complete instructions.

## Build from source

JDK 17, Android SDK Platform 35, and Build Tools 35.0.0 are required.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Release signing is injected through `JMCOMIC_STORE_FILE`, `JMCOMIC_STORE_PASSWORD`, `JMCOMIC_KEY_ALIAS`, and `JMCOMIC_KEY_PASSWORD`. Never commit a keystore or credentials. Without all four variables, `assembleRelease` produces an unsigned build. CI runs tests, lint, and a debug build without release secrets.

## License, attribution, and AI assistance

This project is available under the MIT License and maintained by [@Nuancunv](https://github.com/Nuancunv). Its protocol implementation references [theabab23333/JMComic-Crawler-Python](https://github.com/theabab23333/JMComic-Crawler-Python) and the original [hect0x7/JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python), both under the MIT License.

Under human requirements, decisions, and review, OpenAI Codex assisted with architecture, implementation, static review, testing, and documentation. AI is neither a maintainer nor a copyright holder; the project maintainer is responsible for publication and maintenance.

Report reproducible bugs through [GitHub Issues](https://github.com/Nuancunv/JMComic_downloader_Mobile/issues). Follow [SECURITY.md](SECURITY.md) for security reports and avoid posting exploit details or personal data in a public issue.
