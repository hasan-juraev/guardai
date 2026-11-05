# GuardAI

GuardAI is an Android prototype that watches the screen on-device, detects explicit imagery with a TensorFlow Lite model, and blocks it: it covers the screen with a black overlay, sends the user to the Home screen, and locks the offending app for 10 minutes.

> **Status: research prototype (v0.1.0).** GuardAI is a personal, sideloaded APK used to test whether this approach works. It is not production software, it isn't on Google Play, and its detection accuracy has not been formally measured. See [Known limitations](#known-limitations) before relying on it.

## Contents

- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Using the app](#using-the-app)
- [Project layout](#project-layout)
- [Documentation](#documentation)
- [Privacy](#privacy)
- [Known limitations](#known-limitations)
- [Contributing](#contributing)
- [License](#license)

## How it works

```
 MediaProjection ──► ImageReader ──► NsfwClassifier ──► temporal ──► DetectionCoordinator
 (screen mirror)     (≤ 8 fps)       (TFLite, 320×320)   smoothing     │
                                                                       ├─ BLUR → black overlay
                                                                       └─ LOCK → overlay + lock app (10 min)
                                                                                 + Accessibility "Home"

 AccessibilityService ──► tracks the foreground app ──► re-blocks and ejects a locked app if it is reopened
```

1. **Capture.** A foreground service mirrors the display into an `ImageReader` through `MediaProjection` and samples one frame at most every 125 ms.
2. **Detect.** Each sampled frame is scaled to 320×320 and run through a NudeNet-style object detector (`320n_float32.tflite`). Detections are filtered by per-label score and area thresholds, then de-duplicated with non-max suppression.
3. **Decide.** A frame becomes `BLUR` (lower-severity labels) or `LOCK` (explicit labels). `LOCK` needs two consecutive frames to fire; the first one is shown as `BLUR`.
4. **Enforce.** `BLUR` shows a full-screen black overlay. `LOCK` also stores a 10-minute lock for the foreground app and uses the accessibility service to press Home.
5. **Re-entry.** While a lock is active, reopening that app triggers the overlay and another Home action, even if screen capture is not running.

Everything runs on the device. No frames, detections or package names leave the phone.

For the full design, see [docs/architecture.md](docs/architecture.md).

## Requirements

| Requirement | Version |
| --- | --- |
| Android device | Android 10 (API 29) or newer. Built against API 34. |
| JDK | 17 |
| Android SDK | Platform 34 and build tools (Android Studio installs these) |
| Android Gradle Plugin | 8.5.2 (pinned in `build.gradle.kts`) |
| Kotlin | 1.9.24, Compose compiler 1.5.14 |

A physical device is strongly recommended. Emulators can run the app, but they don't show realistic inference speed or battery use.

## Quick start

1. **Clone the repo and open it in Android Studio** (or use the command line below).

2. **Check the model file.** The detector loads `app/src/main/assets/models/320n_float32.tflite`, which is already in the repository. See [the model notes](app/src/main/assets/models/README.md) if you want to swap it.

3. **Build and install a debug APK** on a connected device:

   ```bash
   ./gradlew installDebug
   ```

   > If Gradle fails to configure the project, see [Build fails with the bundled Gradle wrapper](docs/troubleshooting.md#build-fails-with-the-bundled-gradle-wrapper).

4. **Open GuardAI** on the device and grant the three permissions from the setup screen, in this order:
   1. **Grant overlay** → turn on *Display over other apps* for GuardAI.
   2. **Open accessibility settings** → turn on *GuardAI App Guard*.
   3. **Start capture session** → accept the screen-capture prompt. On Android 14 and later, choose to share the **entire screen**.

5. **Confirm it is running.** A persistent *GuardAI active* notification appears, and the status card shows a rising *Captured frames* count.

For a full development setup, including logcat filters and a manual test plan, see [docs/development.md](docs/development.md).

## Using the app

The single setup screen shows:

| Card | What it does |
| --- | --- |
| **Personal APK bootstrap** | Live status, diagnostics, capture resolution, frame count and the foreground app GuardAI is tracking. |
| **Overlay permission** | Opens the *Display over other apps* settings page. |
| **Accessibility service** | Opens Accessibility settings so you can enable *GuardAI App Guard*. |
| **Screen capture session** | Starts or stops protection. |
| **Debug actions** | **Lock Current App** locks the last tracked app for 10 minutes so you can test ejection without explicit content. **Unlock All** clears every lock. |

To stop protection, tap **Stop protection** on the setup screen or on the notification. Stopping capture does **not** clear locks: the accessibility service keeps enforcing existing locks until they expire or you tap **Unlock All**.

## Project layout

```
app/src/main/
├── AndroidManifest.xml                 Permissions, services, launcher activity
├── assets/models/                      TFLite model files (see models/README.md)
├── res/xml/accessibility_service_config.xml
└── java/com/guardai/
    ├── GuardAIApplication.kt           Creates the notification channel
    ├── MainActivity.kt                 Permission flow and debug actions
    ├── ScreenCaptureNotification.kt    Notification channel and ID constants
    ├── accessibility/AccessibilityEjector.kt   Home/Back global actions and eject retries
    ├── ai/NsfwClassifier.kt            TFLite inference, thresholds, NMS, BLUR/LOCK decision
    ├── detection/DetectionCoordinator.kt       Turns a decision into overlay, lock and eject
    ├── lock/LockConfig.kt              Timing constants
    ├── lock/LockManager.kt             Per-app lock expiry in SharedPreferences
    ├── metrics/MetricsLogger.kt        Structured logcat events (tag GUARDAI_METRIC)
    ├── overlay/OverlayManager.kt       Full-screen black overlay window
    ├── service/ScreenCaptureService.kt         MediaProjection capture, sampling, smoothing, watchdog
    ├── service/GuardAccessibilityService.kt    Foreground-app tracking and re-entry blocking
    ├── session/CaptureSessionState.kt  Process-wide UI state (StateFlow)
    ├── tracking/CurrentAppTracker.kt   Last known foreground package
    ├── ui/setup/SetupScreen.kt         Compose setup and diagnostics screen
    ├── ui/theme/Theme.kt               Material 3 theme
    └── util/PermissionHelper.kt        Overlay and accessibility permission checks
```

## Documentation

**Reference for the current code**

| Document | Read it to… |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | Understand the components, threads and the frame-to-enforcement flow. |
| [docs/detection.md](docs/detection.md) | Learn the model contract, labels, thresholds and smoothing rules. |
| [docs/configuration.md](docs/configuration.md) | Find every tunable constant and what changing it does. |
| [docs/development.md](docs/development.md) | Set up, build, debug and manually test the app. |
| [docs/metrics.md](docs/metrics.md) | Read and analyze the `GUARDAI_METRIC` log events. |
| [docs/troubleshooting.md](docs/troubleshooting.md) | Fix common setup, capture and ejection problems. |

**Plans and proposals** (describe future work, not current behavior)

| Document | Topic |
| --- | --- |
| [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | Milestone plan for the personal APK. |
| [DETECTOR_IMPROVEMENT_PLAN.md](DETECTOR_IMPROVEMENT_PLAN.md) | How to make detection more accurate and stable on video. |
| [SYSTEM_LEVEL_ARCHITECTURE.md](SYSTEM_LEVEL_ARCHITECTURE.md) | Target architecture for a system app, custom ROM or dedicated device. |
| [SYSTEM_LEVEL_ROADMAP.md](SYSTEM_LEVEL_ROADMAP.md) | Phased roadmap and go/no-go gates toward that architecture. |

## Privacy

- Frames are processed in memory and never saved to disk or sent over the network. The app doesn't request the `INTERNET` permission.
- Locks are stored in a private `SharedPreferences` file (`guardai_locks`), and `android:allowBackup="false"` keeps them out of device backups.
- Metrics go to logcat only. They include foreground **package names** and detection labels, so treat bug-report logs as sensitive.

## Known limitations

These come from the current code and Android platform rules. They are tracked here so nobody is surprised by them.

- **Overlay is black, not blurred.** Both `BLUR` and `LOCK` show the same opaque black overlay. The text passed to the overlay is only logged; it isn't drawn.
- **The overlay is captured too.** Full-screen capture mirrors the overlay window, so frames taken while it is up are black and classify as `NONE`. The capture path removes its overlay on the next `NONE` frame, so a `BLUR` block can last only one sampling cycle before it is re-evaluated.
- **The tracked app can be wrong.** The "current app" is whichever package last sent an accessibility event. This can be the launcher or System UI. Check the name on the **Debug actions** card before pressing **Lock Current App**. Recovering from a locked launcher is covered in [troubleshooting](docs/troubleshooting.md#the-screen-stays-black-or-home-keeps-firing).
- **Screen capture needs consent every session.** Android asks for screen-capture permission each time protection starts, and the session ends if the user stops sharing from the system UI or the process is killed.
- **Pipeline restarts may fail on Android 14+.** The stall watchdog rebuilds the `VirtualDisplay` on the same `MediaProjection`. Apps targeting API 34 aren't allowed to do that, so a restart can fail and you will need to start a new capture session.
- **Accuracy is unmeasured.** Thresholds were tuned by hand. The input frame is stretched to a square without letterboxing, which distorts tall phone screens. See [DETECTOR_IMPROVEMENT_PLAN.md](DETECTOR_IMPROVEMENT_PLAN.md).
- **No automated tests yet.** Verification is manual; see the [manual test plan](docs/development.md#manual-test-plan).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow, code style and doc rules. Notable changes are recorded in [CHANGELOG.md](CHANGELOG.md).

## License

No license has been chosen yet, so all rights are reserved by the author. Add a `LICENSE` file before sharing or accepting outside contributions. Note that the bundled model weights may come with their own license terms.
