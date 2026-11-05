# Development guide

How to set up, build, run, debug and test GuardAI.

## Environment setup

1. Install **Android Studio** (Koala 2024.1 or newer works with AGP 8.5) or the command-line tools.
2. Install **JDK 17**. Android Studio ships one; on the command line, set `JAVA_HOME` to it.
3. Install **Android SDK Platform 34** and matching build tools through the SDK Manager.
4. Tell Gradle where the SDK is, either with `ANDROID_HOME` or with a `local.properties` file (git-ignored) at the repo root:

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

5. On the test device, enable **Developer options → USB debugging** and connect it. Check that it shows up:

   ```bash
   adb devices
   ```

## Building

| Task | Command | Output |
| --- | --- | --- |
| Debug APK | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| Install on device | `./gradlew installDebug` | — |
| Release APK (unsigned) | `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Lint | `./gradlew lint` | `app/build/reports/lint-results-debug.html` |
| Clean | `./gradlew clean` | — |

No release signing config exists. To install a release build, sign it yourself with `apksigner`, or add a `signingConfigs` block that reads its keystore from `local.properties` or environment variables, never from committed files.

The APK includes both model files (~18 MB together). Only `320n_float32.tflite` is used; see [detection.md](detection.md#model).

## Running

1. Install and launch the app.
2. Grant permissions from the setup screen: overlay, then accessibility, then screen capture. See the [README quick start](../README.md#quick-start).
3. Watch logs in a second terminal:

   ```bash
   adb logcat -s GUARDAI_METRIC
   ```

You can also enable the accessibility service from the command line, which saves time after reinstalls:

```bash
adb shell settings put secure enabled_accessibility_services com.guardai/com.guardai.service.GuardAccessibilityService
```

```bash
adb shell settings put secure accessibility_enabled 1
```

> The first command **replaces** the list of enabled accessibility services. If you use other services, read the current value first with `adb shell settings get secure enabled_accessibility_services` and append GuardAI to it, separated by `:`.

Screen-capture consent can't be granted from adb. Tap through the dialog on the device.

## Debugging

| What you want to see | How |
| --- | --- |
| Live decisions | `adb logcat -s GUARDAI_METRIC` and watch for `decision_*` |
| Crashes | `adb logcat '*:E' AndroidRuntime:E` |
| Stored locks | `adb shell run-as com.guardai cat shared_prefs/guardai_locks.xml` (debug builds only) |
| Whether the accessibility service is bound | `adb shell dumpsys accessibility \| grep -i guardai` |
| Whether the capture service is in the foreground | `adb shell dumpsys activity services com.guardai` |
| Overlay windows | `adb shell dumpsys window windows \| grep -i guardai` |

The setup screen also shows the latest diagnostics string and eject result, so you can often debug without a computer.

### Testing ejection without explicit content

Use the **Debug actions** card:

1. Open the app you want to test (for example, a browser), then switch back to GuardAI.
2. Check that **Ready to lock** shows the app you expect, **not** the launcher or `com.android.systemui`.
3. Tap **Lock Current App**.
4. Reopen the target app. You should be sent Home, with a `locked_app_reopened` event and a `relock_eject_result` event in logcat.
5. Tap **Unlock All** when you're done.

## Manual test plan

There are no automated tests yet. Run these checks on a real device before merging changes to capture, detection or enforcement. Record the device model and Android version with the results.

| # | Scenario | Steps | Expected result |
| --- | --- | --- | --- |
| 1 | Permission flow | Fresh install; grant each permission from the setup screen. | Each card shows **Ready** after you return to the app. |
| 2 | Session start | Tap **Start capture session**, accept. | Notification appears; frame count rises; `session_begin` and `projection_started` are logged. |
| 3 | Session stop (app) | Tap **Stop protection**. | Notification disappears; `session_end reason=service_destroyed`. |
| 4 | Session stop (system) | Stop sharing from the status bar or the system cast chip. | `session_end reason=projection_callback_stop`; UI says *Projection session ended*. |
| 5 | Safe content | Browse ordinary pages and video for 2 minutes. | Almost entirely `decision_none`; no overlay. Note any false positives. |
| 6 | BLUR | Show an image with a BLUR-group label. | Black overlay appears; `decision_blur`; no lock stored. |
| 7 | LOCK | Show LOCK-group test content for at least 1 s. | Overlay, `package_locked`, `eject_result success=true`; device goes Home. |
| 8 | Re-entry | Reopen the locked app within 10 minutes. | Sent Home again; `locked_app_reopened`. |
| 9 | Lock expiry | Wait 10 minutes (or temporarily lower `DEFAULT_LOCK_DURATION_MS`). | App opens normally. |
| 10 | Locks survive capture stop | Lock an app, stop capture, reopen the app. | Still ejected. |
| 11 | Unlock All | Tap **Unlock All**, reopen the app. | App opens normally. |
| 12 | Missing model | Temporarily change `MODEL_PATH` to a missing file. | Capture runs; UI says *Model file not loaded yet*; `inference_skipped`. |
| 13 | Performance | Run a 10-minute session on video. | Record p50/p95 `latency_ms`, effective FPS and battery drop (see [metrics.md](metrics.md#analyzing-a-session)). |

Only use test content that is legal and appropriate for your setting.

## Code conventions

- Kotlin official code style (`kotlin.code.style=official` in `gradle.properties`); trailing commas in multi-line argument lists.
- Keep tunable values in `LockConfig` or companion objects so they're easy to find. Document new ones in [configuration.md](configuration.md).
- Log new behavior through `MetricsLogger.logEvent` with a `snake_case` event name, and add it to [metrics.md](metrics.md).
- Run window and global-action work on the main thread, and keep inference off it.

See [CONTRIBUTING.md](../CONTRIBUTING.md) for the review and documentation workflow.
