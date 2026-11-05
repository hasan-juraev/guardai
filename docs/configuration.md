# Configuration reference

GuardAI has no settings screen or config file. Every behavior is controlled by a constant in the source, so changing one means editing the file and rebuilding.

Paths below are relative to `app/src/main/java/com/guardai/`.

## Timing and locks — `lock/LockConfig.kt`

| Constant | Default | Effect |
| --- | --- | --- |
| `DEFAULT_LOCK_DURATION_MS` | `600_000` (10 min) | How long an app stays locked after a `LOCK` decision or **Lock Current App**. |
| `EJECT_COOLDOWN_MS` | `1_000` | Minimum time between re-entry ejects from the accessibility service. Prevents a Home loop. |
| `EJECT_RETRY_WINDOW_MS` | `5_000` | How long a pending eject stays active after a `LOCK`. During this window, any event from the locked app retries the eject. |
| `FRAME_STALL_TIMEOUT_MS` | `3_000` | Gap without a processed frame that the watchdog treats as a stall. |
| `WATCHDOG_INTERVAL_MS` | `1_500` | How often the watchdog checks for a stall. |

## Capture — `service/ScreenCaptureService.kt` (companion object)

| Constant | Default | Effect |
| --- | --- | --- |
| `SAMPLE_INTERVAL_MS` | `125` | Minimum time between processed frames (max 8 fps). Lower values react faster but use more CPU and battery. |
| `BLUR_HOLD_MS` | `1_500` | Hold window after a `BLUR`/`LOCK` frame, used for streak bookkeeping. See [detection.md](detection.md#temporal-smoothing) for why it doesn't keep the overlay up. |
| `MAX_PIPELINE_RESTARTS` | `3` | Watchdog restarts allowed per session before it gives up. |
| `METRICS_UPDATE_INTERVAL` | `10` | Frames between UI status refreshes. Doesn't affect logcat metrics, which are logged for every frame. |

The two-frame `LOCK` confirmation is hard-coded in `toSmoothedResult` (`lockFrameStreak >= 2`), in the same file.

Capture always runs at the display's full real resolution (`getRealMetrics`). The `ImageReader` holds 2 buffers.

## Detection — `ai/NsfwClassifier.kt` (companion object)

| Constant | Default | Effect |
| --- | --- | --- |
| `MODEL_PATH` | `models/320n_float32.tflite` | Asset path of the model to load. |
| `INPUT_SIZE` | `320` | Model input width and height. Must match the model. |
| `OUTPUT_CHANNELS` | `22` | 4 box values plus one per label. Must match the model. |
| `OUTPUT_CANDIDATES` | `2100` | Candidate boxes per frame. Must match the model. |
| `BASE_SCORE_THRESHOLD` | `0.18` | Global score floor applied before the per-label minimums. |
| `DEFAULT_MIN_AREA` | `0.015` | Area floor for labels missing from `MIN_AREA_BY_LABEL`. Every enforced label currently has its own entry, so this is unused. |
| `NMS_IOU_THRESHOLD` | `0.45` | Overlap above which a lower-scoring box of the same label is removed. |
| `BLUR_TRIGGER_THRESHOLD` | `0.48` | Score a BLUR-group detection needs to make the frame `BLUR`. |
| `LOCK_TRIGGER_THRESHOLD` | `0.52` | Score a LOCK-group detection needs to make the frame `LOCK`. |
| `BLUR_LABELS` | armpits, feet exposed | Labels that can trigger `BLUR`. |
| `LOCK_LABELS` | belly, buttocks, breasts, genitalia, anus exposed | Labels that can trigger `LOCK`. |
| `MIN_SCORE_BY_LABEL` | see [detection.md](detection.md#labels) | Minimum score per label. |
| `MIN_AREA_BY_LABEL` | see [detection.md](detection.md#labels) | Minimum box area per label, as a fraction of the frame. |

The interpreter thread count (`setNumThreads(4)`) is set in `ensureInterpreter()`.

## Ejection — `accessibility/AccessibilityEjector.kt`

| Constant | Default | Effect |
| --- | --- | --- |
| `MAIN_THREAD_TIMEOUT_MS` | `300` | How long the capture thread waits for the main thread to perform Home or Back before reporting `TIMEOUT`. |
| `HOME_RETRY_DELAY_MS` | `80` | Pause between Back and the second Home attempt. |

## Accessibility service — `res/xml/accessibility_service_config.xml`

| Attribute | Value | Effect |
| --- | --- | --- |
| `accessibilityEventTypes` | `typeWindowStateChanged\|typeWindowContentChanged` | Events used to track the foreground app. |
| `notificationTimeout` | `100` | Minimum ms between events of the same type delivered to the service. |
| `packageNames` | empty | Listen to all packages. |
| `canRetrieveWindowContent` | `true` | Required by the flags below, though view content isn't read today. |

`GuardAccessibilityService.onServiceConnected()` sets the same values again at runtime. Keep both places in sync.

## Build — `app/build.gradle.kts`

| Setting | Value |
| --- | --- |
| `applicationId` / `namespace` | `com.guardai` |
| `minSdk` / `targetSdk` / `compileSdk` | 29 / 34 / 34 |
| `versionName` / `versionCode` | `0.1.0` / `1` |
| Java and Kotlin target | 17 |
| Release minification | off (`isMinifyEnabled = false`) |
| `noCompress` | `tflite` (required for memory-mapping the model) |
