# Privacy Notice

[简体中文](PRIVACY.md)

Effective for version 1.3.0; updated 2026-10-18.

JMComic Downloader Mobile has no account system and includes no advertising, analytics, crash-reporting, or third-party push SDK. It does not collect or upload device identifiers, contacts, messages, location, photos, microphone data, or camera data.

Work and chapter metadata, processed images, generated PDFs, download/export records, reading position, and reader preferences are stored in Android app-private storage. System backup is disabled. Uninstalling the app normally removes app data that has not been exported.

For online search and download, the app sends search terms, work or chapter IDs, protocol-required timestamp tokens, a User-Agent, and a host-specific in-memory session cookie to third-party HTTPS hosts listed in source. Cookies are isolated by host and are not persisted to the database. Those services may log IP addresses and requests under their own policies, which this project does not control.

PDF export uses Android's system document picker. The app receives write access only to the destination selected by the user, does not scan unrelated files, and does not request broad storage access.

Deleting a library item removes its images, internal PDF, chapter index, and reading position from app-private storage. PDFs previously exported to a user-selected location remain. Removing a task entry removes only its history.

Follow [SECURITY.md](SECURITY.md) to report privacy or security concerns.
