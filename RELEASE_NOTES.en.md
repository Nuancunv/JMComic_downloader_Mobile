# v1.2.0 Release Notes

[简体中文](RELEASE_NOTES.md)

JMComic Downloader Mobile v1.2.0 is the first public, release-signed build for phones running Android 8.0 (API 26) or later.

## Features

- Perform online search, image download and descrambling, whole-work PDF creation, and reading entirely on the phone.
- Rank keyword results by title, ID, author, and tag relevance.
- Show live download and PDF-generation progress in a circular result action.
- Search, archive, and delete local works with a pull-down archived section.
- Keep downloads and PDF exports in one task history; removing history never removes content.
- Read with horizontal pages, vertical pages, or continuous scrolling, tap-to-hide controls, per-window brightness, warm overlays, and multiple backgrounds.
- Close overlays and navigate up with Android Back and full-screen back gestures.

## Installation notice

This version uses a new release key. A test build signed with another key cannot be updated in place. Export PDFs that must be retained, uninstall the test build, and then install this version. Uninstalling removes app-private library data that has not been exported. Future versions signed with this release key can update v1.2.0 in place.

After downloading `JMComic_downloader_Mobile-v1.2.0.apk`, compare it with the SHA-256 value on the release page.

```text
1124b8ce5fe17b8318e69fea69eb3b25c48ebfe930672db6d48a04ca012f68e2
```

## Security and privacy

The app contains no advertising, analytics, account login, WebView, plugin system, shell, or dynamic code loading. It connects only to a fixed HTTPS host allowlist, keeps data in app-private storage, and exports PDFs through Android's system document picker.

The project does not host or bundle comic content. Users must comply with applicable law, third-party service terms, and copyright requirements.

## AI assistance

Under human requirements, decisions, and review, OpenAI Codex assisted with design, implementation, static review, testing, and documentation. Final publication and maintenance remain the responsibility of [@Nuancunv](https://github.com/Nuancunv).
