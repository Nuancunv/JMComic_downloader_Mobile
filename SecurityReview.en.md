# Static Security Review

[简体中文完整报告](SecurityReview.md)

Upstream review: 2026-09-05. Android release review: 2026-09-09.

## Conclusion

No confirmed evidence of a hidden malicious implant, cryptomining, ransomware, persistence, or credential theft to a fixed attacker endpoint was found in the reviewed upstream snapshot. The review did find dangerous optional behavior and vulnerabilities, so this conclusion must not be read as “vulnerability-free” or as a security guarantee.

The upstream Python package is not bundled or executed by this Android app. The required protocol and image-segmentation behavior were independently implemented in Kotlin with a narrower permission, network, and file boundary.

## Reviewed upstream snapshot

| Field | Value |
| --- | --- |
| User-specified repository | [theabab23333/JMComic-Crawler-Python](https://github.com/theabab23333/JMComic-Crawler-Python) |
| Commit | [`f2adb337881d96de65c6862935c9f1c32ce847f4`](https://github.com/theabab23333/JMComic-Crawler-Python/commit/f2adb337881d96de65c6862935c9f1c32ce847f4) |
| Upstream version | 2.6.9 |
| Cross-check | The same commit was retrieved from the original [hect0x7 repository](https://github.com/hect0x7/JMComic-Crawler-Python) |
| License | MIT, copyright 2023 hect0x7 |

The review covered all 77 tracked files in that snapshot, including 20 Python files, installation and import paths, CLI, networking, plugins, configuration, image processing, filesystem operations, workflows, examples, and tests. A `commonx==0.6.38` wheel was downloaded without installation or import and selected configuration, path, request, and email paths were inspected.

The code, tests, and workflows under review were not executed. The review did not cover all Git history, other branches, release binaries, the PyPI `jmcomic` distribution, every transitive dependency, or the present-day trustworthiness of remote service infrastructure.

## Material upstream findings

1. **Unsafe configuration deserialization:** `JmOption.from_file()` can reach `pickle.load()` through `commonx` when a user opens an attacker-controlled pickle configuration.
2. **Shell command injection:** an optional favorites-compression plugin builds a shell command from paths and a password, executes it with `shell=True`, and logs the command.
3. **Path traversal:** a specially formed chapter name can escape the expected root under the reviewed default path and dependency behavior.
4. **Over-broad deletion:** the optional duplicate-removal plugin can delete every matching file group and may operate on a broader download root than users expect.
5. **Supply-chain and workflow exposure:** versions were not fully locked; workflows could install a different package than the reviewed source, expose job secrets to installation steps, and build shell commands from release metadata.
6. **Dynamic network trust:** upstream automatic domain discovery can replace built-in service hosts without an independent signature or strict application allowlist.
7. **Sensitive optional plugins and logging:** browser-cookie, email, environment-template, response-dump, and artifact-upload features create conditional data-exposure paths.
8. **Server plugin boundary:** the optional server listens broadly and delegates authentication to an external package that was outside review scope.

These findings show exploitable or hazardous paths, but they do not establish that the repository is malware or that those paths run by default.

## Android 1.2.0 controls

The Android release contains no arbitrary configuration import, pickle, Python runtime, plugin execution, browser-cookie reader, email sender, embedded server, shell/process execution, WebView JavaScript bridge, or dynamic code loading.

- Manifest permissions are limited to internet access, notifications, and foreground data-sync work.
- Android rejects cleartext traffic; OkHttp rejects redirects and uses fixed HTTPS API and image host lists.
- API and image responses have explicit size limits, images are decoded and dimension-checked before publication, and in-memory session cookies are isolated by host.
- Work, chapter, and image paths use validated numeric IDs or restricted filenames.
- Delete and export operations verify canonical paths against the app-private root.
- Images and PDFs use temporary files before replacement; failed PDF verification does not report success.
- App data stays in private storage and Android backup/device transfer is disabled.
- PDF export uses the system document picker rather than broad storage permission.
- Gradle dependency versions are locked and artifact hashes are covered by dependency-verification metadata.
- Public CI has read-only repository permission and no access to the release signing key.
- The release key and passwords remain outside the repository and enter Gradle only through environment variables.

## Release decision and limitations

The reviewed upstream Python package should not be shipped unchanged inside the mobile app. Android 1.2.0 removes those high-risk extension paths and meets the static security threshold for publishing source and a release-signed APK.

This assessment is based on source review, unit tests, Android Lint, and build verification. It is not a formal proof or independent certification and does not guarantee the current operator, infrastructure, legality, or content of third-party services. Re-review is required after material changes to hosts, dependencies, permissions, signing, or filesystem behavior.
