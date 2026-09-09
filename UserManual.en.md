# JMComic Downloader Mobile User Guide

[简体中文](UserManual.md)

Applies to version 1.2.0 on Android 8.0 (API 26) or later. The launcher name is **JMComic 本地书库**.

## 1. Install and upgrade

1. Download `JMComic_downloader_Mobile-v1.2.0.apk` from the GitHub Release page.
2. Compare the file's SHA-256 value with the value shown on that page.
3. Open the APK from Android's Files app. If Android blocks it, allow installation from that Files app only, complete installation, and then revoke the permission if desired.
4. On Android 13 or later, allow notifications if you want background download and export progress.

This is the first release-signed build. An older debug-signed build may not update in place. Export every PDF you need to keep before uninstalling a test build because uninstalling normally removes app-private library data. Future builds signed with the same release key can update v1.2.0 directly.

All search, download, image processing, PDF creation, library management, and reading work runs on the phone. Online search and new downloads need internet access; existing PDFs, chapter navigation, local search, and reading work offline.

## 2. Main navigation

The bottom navigation has three destinations:

| Destination | Purpose |
| --- | --- |
| Library | Search, open, archive, and delete downloaded works |
| Add | Search online by ID, URL, or keyword and start a download |
| Tasks | Track downloads and PDF exports, inspect export details, cancel work, and remove history |

Android's Back button or full-screen back gesture closes the topmost dialog or sheet first, then returns from the reader, work details, archived library, and non-library tabs in order.

## 3. Search and download

1. Open **Add**.
2. Enter a numeric ID, a value such as `JM123456`, a supported `/album/123456` or `/photo/123456` URL, or a keyword.
3. Tap the search icon or the keyboard search action.
4. Keyword results prioritize exact ID matches, exact and contiguous title matches, title overlap, and then author and tag matches. Equal-scoring results keep source order.
5. Use **Load more** when available. New results are de-duplicated and ranked together with the current list.
6. Confirm the displayed title, author, and tags, then tap **Download**.

The download action becomes a circular progress control with a stable width. The dark arc is completed work and the light arc is remaining work. An indeterminate animation is used while the total is unknown, `PDF` appears during PDF creation, and a check mark indicates completion. Tap a completed control to open the local work.

The app reads the chapter list, downloads and processes each image, and creates one PDF with chapter bookmarks. Both processed images and the PDF remain in app-private storage, so keep enough free space. Android vendor battery policies, force-stop, reboot, or low storage can still interrupt work.

After a failed or cancelled task, valid pages already written remain available for resume. Open the work details and tap **Continue download** to verify existing pages, retrieve missing pages, and rebuild the PDF.

## 4. Manage the library

Use the Library search field to match title, author, tags, JM ID, or chapter name. This search reads only the local Room database. Archived works stay separate from the normal library.

To archive a work, swipe its card left and tap the light-blue **Archive** action. Images, PDF, chapter data, and reading position remain intact. At the top of Library, pull down again and release to reveal **Archived books**. The refresh indicator fades away automatically. In the archived view, swipe left and choose **Restore** to return a work to the normal library.

To delete a work, swipe its card left, tap the light-red **Delete** action, read the confirmation, and confirm. The app cancels associated writing, then removes the work's app-private images, PDF, chapter index, and reading progress. A PDF previously exported through the system picker remains. Deletion cannot be undone.

## 5. Manage tasks

Tasks combines download and PDF-export records in update-time order. Running work provides a cancel action. A finished, failed, or cancelled task can be swiped left and removed after confirmation.

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
- Choose among seven backgrounds. Non-black choices use light colors; pure black uses light controls.

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
