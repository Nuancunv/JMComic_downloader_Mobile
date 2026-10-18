# JMComic Local Library

[简体中文](README.md) · [What's new](docs/releases/RELEASE_NOTES.en.md) · [User guide](docs/user/UserManual.en.md)

A small Android app for downloading and reading. Find something you want to read, save it on your phone, and read it later—even offline. You can also export a PDF with chapter bookmarks.

No PC or server setup needed. Your phone handles it all. Android 8.0 or newer is enough.

## What's changed in v1.3.0

**v1.3.0 is finalized.** This update mainly improves search, the interface, and downloads:

- More accurate search by title, ID, author, or tag, with simplified/traditional Chinese matching where possible.
- An improved UI.
- Faster downloads.
- Improved PDF creation and content export.

The [release notes](docs/releases/RELEASE_NOTES.en.md) have the details, so we won't go through everything here.

## Installation

Download the APK from [Releases](https://github.com/Nuancunv/JMComic_downloader_Mobile/releases) and install it on your Android phone.

Downloads and PDF creation need storage space, so leave some room on your phone.

## A few other things

No ads or usage analytics. Your library, images, and reading progress stay on your phone. It's best to choose the export location yourself. See the [privacy notes](PRIVACY.en.md) for details. This project doesn't supply comic content; search for and download what you need yourself.

To build it yourself, use JDK 17 and Android SDK 35:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## Disclaimer

JMComic Downloader Mobile is a personal hobby project, intended only for learning and testing.

`This project does not host, distribute, or bundle comic content. Content from third-party services may be subject to copyright, age, and regional restrictions. Users should follow applicable local laws, service terms, and content owners' requirements.`

Maintained by [@Nuancunv](https://github.com/Nuancunv), with Codex helping during development. The protocol references [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python); see the [third-party notices](THIRD_PARTY_NOTICES.md) for details. The code is [MIT licensed](LICENSE).

Found a problem? => [Open an issue](https://github.com/Nuancunv/JMComic_downloader_Mobile/issues), ideally with the app version and steps to reproduce it.
