# Release notes

[简体中文](RELEASE_NOTES.md)

## v1.3.0 — Search, interface, and downloads get a refresh

This update brings the search, UI, download, and PDF improvements together as **v1.3.0**.

### Search

- Reworked query normalization and relevance ranking for IDs, links, titles, authors, tags, and chapter names, including full-width characters, punctuation, whitespace, and simplified/traditional Chinese.
- Merge duplicate online results, retain author/tag metadata, append later pages without reshuffling existing results, cancel superseded requests, and retain useful partial results.
- Add match reasons, highlights, and local title suggestions; avoid repeated full-list scans for the latest download task.

### Interface

- Rebuild the library, search, tasks, details, and reader around warm surfaces and glass controls, with system, light, and dark themes.
- Tighten headers, add page transitions, retain list position, group active/finished tasks, and show live progress on search cards.
- Pause some ambient effects while scrolling or transitioning, and move PDF opening and image work off the main thread.

### Downloads and reading

- Concurrent image transfers and dynamic queues share a process-wide network budget; prioritize the work being read and adapt to failures or rate limits.
- Bound chapter metadata prefetch so the first chapter can start before the complete book manifest is fetched. Finished chapters can be read while later ones download.
- Reuse validated local images on retry and coordinate cancellation, deletion, and file writes.
- Store chapter-aware bookmarks while retaining old page positions; explicitly migrate Room schema 3 to 4 without clearing the library.

### PDF and API reliability

- Validate PDF pages in one pass, and bound compressed-image stream memory with disk scratch for larger books.
- Embed processed JPEG streams directly and retain chapter bookmarks. Save/validation can be cancelled; clean partial files and preserve the previous PDF when publication fails.
- Rebuild a PDF from a complete, validated local snapshot without fetching metadata again, including retries started offline.
- Adopt upstream UTF-8 BOM handling and protocol version 2.1.7, with failover for business errors, invalid ciphertext, and invalid chapter templates.

### Upgrade and verification

The official package uses `com.nuancun.jmcomic.mobile` and the **original v1.2.0 release certificate**, increasing versionCode from 5 to 16 for an in-place update. The separate `.searchtest` app keeps its own data and is not merged automatically.

## v1.2.0 — First public release-signed build

JMComic Downloader Mobile v1.2.0 is the first public, release-signed build for phones running Android 8.0 (API 26) or later.

### Features

- Perform online search, image download and descrambling, whole-work PDF creation, and reading entirely on the phone.
- Rank keyword results by title, ID, author, and tag relevance.
- Show live download and PDF-generation progress in a circular result action.
- Search, archive, and delete local works with a pull-down archived section.
- Keep downloads and PDF exports in one task history; removing history never removes content.
- Read with horizontal pages, vertical pages, or continuous scrolling, tap-to-hide controls, per-window brightness, warm overlays, and multiple backgrounds.
- Close overlays and navigate up with Android Back and full-screen back gestures.

### Installation notice

This version uses a new release key. A test build signed with another key cannot be updated in place. Export PDFs that must be retained, uninstall the test build, and then install this version. Uninstalling removes app-private library data that has not been exported. Future versions signed with this release key can update v1.2.0 in place.

After downloading `JMComic_downloader_Mobile-v1.2.0.apk`, compare it with the SHA-256 value on the release page.

```text
1124b8ce5fe17b8318e69fea69eb3b25c48ebfe930672db6d48a04ca012f68e2
```

### Security and privacy

The app contains no advertising, analytics, account login, WebView, plugin system, shell, or dynamic code loading. It connects only to a fixed HTTPS host allowlist, keeps data in app-private storage, and exports PDFs through Android's system document picker.

The project does not host or bundle comic content. Users must comply with applicable law, third-party service terms, and copyright requirements.

### AI assistance

Under human requirements, decisions, and review, OpenAI Codex and Deepseek Harness helped with design, implementation, static review, testing, and documentation. Final publication and maintenance remain the responsibility of [@Nuancunv](https://github.com/Nuancunv).
