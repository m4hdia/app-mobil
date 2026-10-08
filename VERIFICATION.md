# Verification status

Source implementation and test suites have been created, but **the app has not been compiled or run successfully in this environment**. There is no verified APK yet.

## Observed checks

| Check | Result |
| --- | --- |
| Repository inspection | Empty workspace; no existing Android project or installed Java/Gradle/Android SDK found. |
| Android XML | All six manifest/resource XML files parse successfully. |
| Privacy manifest | The app manifest requests no Internet permission. The merged dependency manifest still needs build-time inspection. |
| Gradle wrapper | Official wrapper JAR downloaded; ZIP integrity passed. SHA-256 `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`. |
| SDK command-line tools | Downloaded from Google, ZIP-tested, and extracted to `.tools/sdk`. SDK platform/build-tools packages are not installed yet. |
| JDK and Gradle distribution | Repeated normal and resumable downloads failed with network read/TLS timeouts. Partial segments remain in ignored `.tools` for resumption. |
| Build attempt | `gradlew.bat assembleDebug testDebugUnitTest lintDebug` exited 1 before Gradle started: `java.exe` was not recognized. |
| Optional Kotlin parser | Initial package download failed. Automatic approval review timed out on the retry and its one allowed repeat; no parser check ran. |
| Unit/Robolectric tests | Written; **not executed** because the JDK/Gradle toolchain is unavailable. |
| Lint, navigation, dark mode, recreation, notification delivery | **Not executed**. Source review and test coverage do not establish runtime correctness. |
| Phone/emulator validation | **Not performed**. Use TESTING.md after a successful build. |

## Resume verification

Install Android Studio with JDK 17, Android SDK Platform 35, Build Tools 35.0.0, and Platform Tools. Open this folder, configure the Gradle JDK, and run:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

Alternatively, resume the project-local downloads on a working network with `python scripts/download_toolchain.py`; then install the SDK platform packages using SDK Manager and run `scripts/build.ps1`. The GitHub Actions workflow performs build/test/lint when this project is placed in a GitHub repository; it has not been triggered here.

Compilation errors or runtime defects may still exist. Fix and rerun the checks before treating this as a finished, usable application.

## Continuation on October 7, 2026

- Rechecked the local environment: Java and Gradle are still unavailable.
- Retried resumable JDK/Gradle downloads using both Python TLS and Windows curl. Transfers made partial progress but failed again with TLS/read timeouts and incomplete range responses. Neither distribution was assembled or verified; downloaded fragments are not usable build tools.
- Replaced hash-only notification identities with unique intent URIs and notification tags, and suppressed review/form notifications for deleted destinations.
- Added a recoverable database-load error screen and retry action. Coroutine cancellation is no longer converted into a generic operation error.
- Added regression tests for notification hash collisions, stale review destinations, and persistence across closing/reopening a file database. There are now 36 declared tests, still unexecuted.
- XML parsing and the Python download helper's syntax checks passed. These do not verify Kotlin compilation or app behavior.

An installed JDK 17/Android Studio toolchain, or successful dependency downloads, remains necessary to complete build and runtime verification.

## Interactive design preview

At the user's request to see the application, a separately labeled design preview was created at `preview/daylight-preview.html`. This is not the Android application and is not evidence of an APK build. It uses sample data and does not schedule Android notifications or use the Room database.

The preview was rendered in Chrome and its navigation, checkbox progress, reflection feedback, literal-text entry, and theme switching were exercised successfully through Chrome DevTools. These browser checks are separate from the 36 unexecuted Android tests.

`scripts/run-android.ps1` provides a build/install/launch command for a connected Android device once the toolchain exists. Running it in this environment correctly stopped at the missing Java prerequisite.

## Data consistency continuation on October 7, 2026

- Replaced independently combined table flows with one observed, transactional database snapshot. Form/question and response/answer updates now reach editors together, avoiding partially initialized saveable drafts. Existing ordering and pending-occurrence filtering are preserved. The API used is Room's [InvalidationTracker.createFlow](https://developer.android.com/reference/androidx/room/InvalidationTracker).
- Review saves reject incomplete answer sets, changed question snapshots, answers belonging to other responses, and deleted responses. Valid answer values and reflections remain editable; saved response metadata is preserved.
- Form saves reject invalid question types, duplicate question IDs, and question IDs owned by another form before writing.
- Notification permission status refreshes when Settings resumes after visiting Android settings. Backup import propagates coroutine cancellation.
- Added four database regression tests covering coherent observed snapshots through updates/submission/deletion, rejected reviews and valid edits, deleted-entry protection, and question ownership. These tests have not run.
- Retried the official JDK download outside the network sandbox. It timed out after 45 seconds with only 524,288 of 71,939,211 bytes received. The partial archive is not usable.
- Retried `gradlew.bat assembleDebug testDebugUnitTest lintDebug`; it still exits before Gradle starts because `java.exe` is unavailable.
- Installed a small optional Kotlin syntax parser under ignored `.tools`, but Windows Application Control blocked loading its native DLL. No Kotlin syntax-check result was obtained; compilation, tests, and lint remain unverified.
- All six Android XML files still parse successfully. The three modified Kotlin files decode as UTF-8 without replacement characters. These are limited static checks, not Android runtime verification.

No verified APK is available. Completing build/test/lint still requires a working JDK 17, Gradle distribution, Android SDK packages, and dependency downloads.
