# Metrics and logging

GuardAI writes structured events to **logcat** with the tag `GUARDAI_METRIC`. There is no remote telemetry. The events are meant for measuring latency, debugging decisions and checking battery impact on a real device.

## Format

Each event is one `Log.i` line of comma-separated `key=value` pairs:

```
seq=412,t_ns=91827364500123,session_id=3f9a1c2e,event=inference_end,frame=137,latency_ms=41.27
```

Every event starts with these fields:

| Field | Meaning |
| --- | --- |
| `seq` | Counter that increases for every event in the process. Use it to spot dropped log lines. |
| `t_ns` | `SystemClock.elapsedRealtimeNanos()`: monotonic, and includes deep sleep. |
| `session_id` | First 8 characters of a random UUID per capture session, or `none` outside a session. Accessibility events logged outside a session use `none`. |
| `event` | Event name (see below). |

The logger escapes values so lines stay parseable: `,` becomes `;`, `=` becomes `:`, and newlines become spaces. List values, such as `labels`, are joined with `|`.

## Capturing logs

```bash
adb logcat -s GUARDAI_METRIC
```

To save a session to a file and keep only the payload:

```bash
adb logcat -v raw -s GUARDAI_METRIC > guardai-session.log
```

> Logs include foreground **package names** and detection labels. Handle saved logs as sensitive data.

## Event reference

### Session lifecycle

| Event | Source | Extra fields |
| --- | --- | --- |
| `session_begin` | `MetricsLogger` | `session_id` |
| `session_start_battery` | `ScreenCaptureService` | `battery_level`, `battery_scale`, `battery_pct` |
| `projection_started` | `ScreenCaptureService` | `width`, `height`, `density_dpi` |
| `projection_start_failed` | `ScreenCaptureService` | — (the consent payload was missing) |
| `projection_stopped` | `ScreenCaptureService` | `reason` |
| `session_stop_battery` | `ScreenCaptureService` | `battery_level`, `battery_scale`, `battery_pct` |
| `session_end` | `MetricsLogger` | `session_id`, `reason` |

`reason` values: `service_destroyed`, `projection_callback_stop`, `missing_projection_payload`.

### Frame pipeline

| Event | Extra fields | Notes |
| --- | --- | --- |
| `frame_acquired` | `frame`, `width`, `height` | One per processed (sampled) frame. |
| `inference_start` | `frame` | |
| `inference_skipped` | `frame`, `reason=model_not_ready` | The model failed to load. |
| `inference_end` | `frame`, `latency_ms` | Time spent in `classify()`, including scaling and preprocessing. |
| `decision_none` / `decision_blur` / `decision_lock` | `frame`, `package`, `confidence`, `top_label`, `labels`, `blur_streak`, `lock_streak` | The **smoothed** decision. |
| `capture_pipeline_restart` | `attempt`, `frame_gap_ms` | Watchdog rebuilt the pipeline. |
| `capture_stall_terminal` | `frame_gap_ms` | Watchdog hit `MAX_PIPELINE_RESTARTS`. |

### Enforcement

| Event | Source | Extra fields |
| --- | --- | --- |
| `overlay_show_requested` | `DetectionCoordinator` | `reason` (`BLUR`, `LOCK` or `lock_held`), `package`, `labels` (not set for `lock_held`) |
| `overlay_attached` | `OverlayManager` | `message` |
| `overlay_hidden` | `OverlayManager` | — |
| `package_locked` | `DetectionCoordinator` | `package`, `lock_duration_ms`, `confidence` |
| `eject_requested` | `AccessibilityEjector` | `package`, `retry_window_ms` |
| `eject_result` | `AccessibilityEjector` | `success`, `mode` |
| `eject_pending_expired` | `AccessibilityEjector` | `package` |
| `eject_pending_cleared` | `AccessibilityEjector` | `package` |
| `pending_eject_attempt` | `GuardAccessibilityService` | `package`, `success`, `mode` |
| `locked_app_reopened` | `GuardAccessibilityService` | `package` |
| `relock_eject_result` | `GuardAccessibilityService` | `success`, `mode` |

`mode` values are listed in [architecture.md](architecture.md#ejection-strategy).

## Analyzing a session

The `t_ns` field makes it easy to measure intervals between events.

**Inference latency** (median and p95, in ms):

```bash
grep -o 'event=inference_end,.*latency_ms=[0-9.]*' guardai-session.log \
  | sed 's/.*latency_ms=//' | sort -n \
  | awk '{a[NR]=$1} END {print "p50=" a[int(NR*0.5)+1], "p95=" a[int(NR*0.95)+1], "n=" NR}'
```

**Decision counts:**

```bash
grep -o 'event=decision_[a-z]*' guardai-session.log | sort | uniq -c
```

**Useful intervals to measure:**

| Metric | From → to |
| --- | --- |
| Detection-to-block latency | `frame_acquired` → `overlay_attached` for the same frame |
| Lock-to-eject latency | `package_locked` → `eject_result` |
| Effective frame rate | Count of `frame_acquired` ÷ session duration |
| Battery drain | `session_start_battery.battery_pct` − `session_stop_battery.battery_pct` |
