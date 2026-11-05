# Troubleshooting

Each entry lists the **symptom**, its likely **cause**, and the **fix**. For log details, see [metrics.md](metrics.md).

- [Build fails with the bundled Gradle wrapper](#build-fails-with-the-bundled-gradle-wrapper)
- [A permission card stays "Not ready"](#a-permission-card-stays-not-ready)
- ["Model file not loaded yet"](#model-file-not-loaded-yet)
- [Capture stops or "Capture stalled" appears](#capture-stops-or-capture-stalled-appears)
- [Eject result shows NO_SERVICE, TIMEOUT or FAILED](#eject-result-shows-no_service-timeout-or-failed)
- [The screen stays black or Home keeps firing](#the-screen-stays-black-or-home-keeps-firing)
- [The overlay flickers or disappears quickly](#the-overlay-flickers-or-disappears-quickly)
- [Explicit content isn't blocked](#explicit-content-isnt-blocked)
- [Safe content is blocked](#safe-content-is-blocked)

---

## Build fails with the bundled Gradle wrapper

**Symptom:** Gradle fails while configuring the project, often with errors about removed APIs or an unsupported Gradle version.

**Cause:** `gradle/wrapper/gradle-wrapper.properties` points at **Gradle 9.0.0**, but the project pins **Android Gradle Plugin 8.5.2**. That plugin version was built for Gradle 8.7+ and isn't expected to work with Gradle 9.

**Fix:** Use a Gradle 8.x release that AGP 8.5.2 supports, for example:

```bash
./gradlew wrapper --gradle-version 8.7
```

If the wrapper itself won't start, edit `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` to `gradle-8.7-bin.zip` by hand. Alternatively, upgrade AGP to a version that supports Gradle 9.

## A permission card stays "Not ready"

| Card | Check |
| --- | --- |
| Overlay permission | Settings → Apps → GuardAI → *Display over other apps* is on. |
| Accessibility service | Settings → Accessibility → *GuardAI App Guard* is on. On Android 13+, sideloaded apps may need **App info → ⋮ → Allow restricted settings** before the toggle can be enabled. |
| Screen capture session | The consent dialog was accepted, and on Android 14+ **Entire screen** was chosen. |

Permission states refresh when the app returns to the foreground (`onResume`). If a card is still wrong, close and reopen the app.

## "Model file not loaded yet"

**Cause:** `NsfwClassifier` couldn't open `assets/models/320n_float32.tflite`. The exception is swallowed, so the only signs are this message and `inference_skipped reason=model_not_ready` in logcat.

**Fix:**

1. Confirm the file exists at `app/src/main/assets/models/320n_float32.tflite` and isn't a Git LFS pointer (it should be about 12 MB).
2. Confirm `MODEL_PATH` in `NsfwClassifier.kt` matches the file name.
3. Confirm `noCompress += "tflite"` is still in `app/build.gradle.kts`. Memory-mapping fails on compressed assets.
4. Rebuild and reinstall.

## Capture stops or "Capture stalled" appears

| Diagnostic or event | Meaning | Fix |
| --- | --- | --- |
| *Projection session ended* / `projection_callback_stop` | The user or system stopped sharing. | Start a new capture session. |
| *Restarting capture pipeline attempt n/3* | No frame processed for 3 s. Can happen on a completely static screen. | Usually harmless; it recovers when the screen changes. |
| *Capture stalled… restart capture session* / `capture_stall_terminal` | The watchdog used all 3 restarts. | Stop and start protection. |
| Restart attempts fail on Android 14+ | Android 14 allows only one `VirtualDisplay` per `MediaProjection` for apps targeting API 34. | Stop and start protection. |
| Service disappears in the background | The OS killed the process (battery optimization). | Settings → Apps → GuardAI → Battery → **Unrestricted**. |

## Eject result shows NO_SERVICE, TIMEOUT or FAILED

| Mode | Cause | Fix |
| --- | --- | --- |
| `NO_SERVICE` | The accessibility service isn't connected. It may have been turned off or crashed. | Turn *GuardAI App Guard* off and on again. Check `adb shell dumpsys accessibility`. |
| `TIMEOUT` | The main thread didn't run the Home action within 300 ms. | Usually the device is under load. Check logcat for main-thread work or ANRs. |
| `FAILED` / `BACK_ONLY` | The system rejected `GLOBAL_ACTION_HOME`. Some OEM launchers or lock-task modes block it. | Test on another launcher or device and report the device model. |

## The screen stays black or Home keeps firing

**Cause:** A lock is active on an app you keep returning to. Most often this is the **launcher** or **System UI**, locked by mistake with **Lock Current App**, or because it was the tracked app when a `LOCK` decision fired.

**If an ordinary app is locked**, open GuardAI and tap **Unlock All**.

**If the launcher or System UI is locked**, the black overlay covers the screen and blocks taps. Opening GuardAI doesn't help, because GuardAI's own window events don't remove the overlay. Use one of these instead:

1. **Wait** for the lock to expire (10 minutes by default). Rebooting doesn't help, because locks are stored on disk.
2. **Clear GuardAI's data over adb**, if USB debugging is already authorized. This deletes all locks:

   ```bash
   adb shell pm clear com.guardai
   ```

3. **Boot into Safe Mode**, which turns off third-party accessibility services. Then go to Settings → Apps → GuardAI → Storage → **Clear storage**, and reboot normally. The way to enter Safe Mode differs by device; usually you long-press **Power off** in the power menu.

Before using **Lock Current App**, always check the package name on the **Debug actions** card.

## The overlay flickers or disappears quickly

**Cause:** This is expected with the current design. Full-screen capture mirrors the overlay, so the next frames are black and classify as `NONE`, and the capture path removes its overlay on the next `NONE` frame. See [detection.md](detection.md#temporal-smoothing) and the README's [known limitations](../README.md#known-limitations).

## Explicit content isn't blocked

Check, in order:

1. **Is inference running?** Look for `inference_end` events. If you see `inference_skipped`, see [Model file not loaded yet](#model-file-not-loaded-yet).
2. **What did the model see?** Check `confidence` and `labels` on the `decision_*` events.
3. **Was it under the trigger score?** LOCK-group labels need **≥ 0.52** (0.58 for `MALE_BREAST_EXPOSED`), even when their minimum score is lower. See the [labels table](detection.md#labels).
4. **Was the box too small?** Thumbnails can fall under the per-label min area.
5. **Did it last long enough?** LOCK needs two consecutive LOCK frames. Fast cuts in video may never produce two in a row.
6. **Is the content protected?** Apps that set `FLAG_SECURE` (many DRM video players and banking apps) show up as black in screen capture and can't be analyzed.

## Safe content is blocked

1. Find the `decision_blur` or `decision_lock` event and note `top_label` and `confidence`.
2. If one label causes most false positives, raise its value in `MIN_SCORE_BY_LABEL` or `MIN_AREA_BY_LABEL`.
3. Re-run the [safe-content test](development.md#manual-test-plan) and compare decision counts before and after.
