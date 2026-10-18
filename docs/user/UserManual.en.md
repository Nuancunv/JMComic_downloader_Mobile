# JMComic Downloader Mobile User Guide

[简体中文](UserManual.md)

Applies to official v1.3.0 (versionCode 16) on Android 8.0 (API 26) or later. The launcher name is **JMComic 本地书库**. See the [release notes](../releases/RELEASE_NOTES.en.md) for changes and publication status.

## 1. Install and upgrade

The official APK is `JMComic_downloader_Mobile-v1.3.0.apk`, using `com.nuancun.jmcomic.mobile` and the original v1.2.0 release certificate for an in-place update that retains the official app's library, tasks, PDFs, reading position, and preferences. The separate search/UI test app has a `.searchtest` package name and its data is not merged automatically. See the [official delivery notes](../releases/v1.3.0.md) for checksums and details.

1. Save the APK to your phone and open it from Android's Files app.
2. If required, temporarily allow installation from that Files app and choose Update. Keep the official v1.2.0 app installed; do not uninstall it first.
3. Open **JMComic 本地书库** and check the existing library, tasks, PDFs, archive, and reading position.
4. Revoke the Files app's installation permission when finished, if desired.

Room explicitly migrates schema 3 to 4, preserving the existing library, PDF paths, and reading positions. Check your data after updating.

All search, download, image processing, PDF creation, library management, and reading work runs on the phone. Online search and new downloads need internet access; existing PDFs, chapter navigation, local search, and reading work offline.

## 2. Main navigation

The bottom navigation has three destinations:

| Destination | Purpose |
| --- | --- |
| Library | Search, open, archive, and delete downloaded works |
| Search | Search online by ID, URL, or keyword and start a download |
| Tasks | Track downloads and PDF exports, inspect export details, cancel work, and remove history |

Android's Back button or full-screen back gesture closes the topmost dialog or sheet first, then returns from the reader, work details, archived library, and non-library tabs in order.

### Appearance

Use the main top bar's appearance button to choose System, Light, or Dark. The choice is applied and saved immediately; System is the default. Reading background, warmth, and brightness remain independent, and PDF image colors are preserved. Navigation and reading chrome use local backdrop blur on Android 12+, with a warm stable-surface fallback on older versions. Reader settings add a paper backing inside the glass shell so PDF brightness does not compromise control readability.

## 3. Search and download

1. Open **Search**.
2. Enter a numeric ID, a value such as `JM123456`, a supported `/album/123456` or `/photo/123456` URL, or a keyword.
3. Tap the Search button or the keyboard search action; use the clear button to remove the query.
4. Queries normalize full-width characters, case, whitespace, and punctuation, with simplified/traditional character compatibility. The first batch prioritizes ID, exact title, equivalent title, title phrase, all title keywords, and approximate title, followed by author and tag matches. Cards show match reasons and highlights. With an existing local library, typing offers up to five title suggestions; tapping one submits a search.
5. When title candidates are weak, the first batch adds a few query variants and conditionally prefetches the second page. Use **Load more** when available: results merge by work ID, existing cards keep their order, and new works append. Failed requests can be retried; changing the query cancels the old search.
6. Confirm the displayed title, author, and tags, then tap **Download**.

The download action becomes a circular progress control with a stable width. The dark arc is completed work and the light arc is remaining work. An indeterminate animation is used while the total is unknown, `PDF` appears during PDF creation, and a check mark indicates completion. Tap a completed control to open the local work.

The app reads the chapter list, downloads and processes each image, and creates one PDF with chapter bookmarks. Both processed images and the PDF remain in app-private storage, so keep enough free space. Android vendor battery policies, force-stop, reboot, or low storage can still interrupt work.

After a failed or cancelled task, valid pages already written remain available for resume. Open the work details and tap **Continue download** to verify existing pages, retrieve missing pages, and rebuild the PDF.

## 4. Manage the library

Use the Library search field to match title, author, tags, JM ID, or chapter name. This search reads only the local Room database. Archived works stay separate from the normal library.

Local search shares query normalization and simplified/traditional character compatibility, supports multiple keywords, and sorts by relevance before update time. Queries of four or more characters allow conservative approximate title matching; one-to-three-character queries and queries containing `%` or `_` use literal matching. Results show the matching field or chapter and highlight the original text.

To archive a work, swipe its card left and tap the warm terracotta **Archive** action. Images, PDF, chapter data, and reading position remain intact. At the top of Library, pull down again and release to reveal **Archived books**. The refresh indicator fades away automatically. In the archived view, swipe left and choose **Restore** to return a work to the normal library.

To delete a work, swipe its card left, tap the light-red **Delete** action, read the confirmation, and confirm. The app cancels associated writing, then removes the work's app-private images, PDF, chapter index, and reading progress. A PDF previously exported through the system picker remains. Deletion cannot be undone.

## 5. Manage tasks

Tasks groups download and PDF-export records into active and finished sections, each ordered by update time. Running work provides a cancel action. A finished, failed, or cancelled task can be swiped left and removed after confirmation.

Removing a download task record does not remove its work, images, PDF, chapter index, archive state, or reading position. Removing an export task record does not remove the exported PDF.

Tap a PDF export task to open its detail overlay. The compact view shows original work name, saved filename, destination, state, and time. Tap **Details** for author, tags, description, chapter/page counts, source size, and the document URI. Long content scrolls inside the overlay. Close it with the upper-right close button, the dimmed background, or Android Back.

## 6. Read and navigate chapters

Open a card from Library or Archived books. The work-detail page lists chapters and their starting PDF pages. Tap a chapter to open it directly, or tap **Read** to continue from the saved position.

In the reader:

- Tap the page to hide or reveal the top and bottom controls. Swiping or paging preserves their current visibility.
- Use the previous/next controls or page slider when controls are visible.
- Open reader settings to select horizontal paging, vertical paging, or continuous vertical scrolling.
- Select system brightness or move the app-only brightness slider. It changes only this window.
- Choose Off, Light, Medium, or Strong warmth. The overlay affects only the reader.
- Choose among seven warm backgrounds: ivory, paper, cream, sage, linen, sand, and ink. Ink uses light controls. Existing saved background identifiers are retained.

Leaving the reader restores normal window brightness and system-bar appearance. The reader stores the settled page, and continuous mode also stores its offset.

## 7. Export a PDF

1. Open a downloaded work and tap **Export PDF**.
2. Choose a folder and filename in Android's system document picker.
3. Follow progress in Tasks. You may leave the screen while WorkManager copies the file.
4. Open the export task to verify the actual saved name and destination.

The app writes only to the selected document URI and does not require broad storage permission. Avoid choosing an existing important file unless you intend to replace it. Cancelling during export may leave the destination incomplete depending on the selected document provider; delete that incomplete file with the provider's Files app if necessary.

## 8. Storage, permissions, and troubleshooting

Internal data lives under Android app-private storage and is unavailable to ordinary file managers. Export important PDFs before uninstalling or clearing app data. Android system backup and device transfer are disabled for the library.

If a download does not progress, verify connectivity, notification permission, free space, and the phone's battery restrictions for the app. If search or download fails on every item, the fixed third-party service hosts may be unavailable or changed; check the latest project release or issue tracker.

If PDF generation fails, free storage and resume the download so the app can validate images and rebuild it. If installation reports a signature conflict, export important data from the old build, uninstall it, and install the official release.

When reporting a bug, include the app version, Android version, action that triggered it, expected behavior, actual behavior, and non-sensitive error text. Never publish private content, cookies, credentials, or signing files.
