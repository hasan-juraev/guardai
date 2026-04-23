# GuardAI Personal APK Implementation Plan

## Objective

Build a personal-use Android APK that:

- captures the screen with `MediaProjection`,
- runs on-device NSFW detection on sampled frames,
- shows an immediate blur/block overlay when explicit content is detected,
- uses `AccessibilityService` to send the device to Home,
- records the current foreground package as blocked for a lock window,
- re-blocks and ejects the user if the blocked app is reopened during the lock window.

This plan intentionally excludes:

- Google Play distribution,
- VPN/DNS filtering,
- keyword detection,
- remote dashboard,
- multilingual text analysis,
- cross-device management.

## Product Scope

### In Scope

- Personal sideloaded APK
- Real-time screen frame sampling
- On-device NSFW classification
- Full-screen blur/block overlay
- Accessibility-based foreground package tracking
- App ejection via `GLOBAL_ACTION_HOME`
- Temporary per-package lock window
- Re-entry blocking for locked packages
- Basic settings for threshold and lock duration

### Out of Scope

- Guaranteed zero-exposure blocking before first frame
- Force-stopping third-party apps
- Play policy compliance work
- Cloud sync or server APIs
- Browser URL filtering
- Telegram/Instagram-specific integrations
- Background boot persistence beyond what Android naturally permits

## Technical Principles

- Keep the first version minimal and testable.
- Prefer deterministic package-lock enforcement over clever heuristics.
- Treat detection as best-effort mitigation, not perfect prevention.
- Optimize for low latency before optimizing for UI polish.
- Keep runtime permissions and moving parts to the minimum needed.

## High-Level Architecture

### Core Components

1. `MainActivity`
   - entry point
   - setup/status UI
   - requests overlay permission
   - requests screen capture permission
   - guides user to accessibility settings

2. `ScreenCaptureService`
   - foreground service
   - owns `MediaProjection`, virtual display, image reader
   - samples frames
   - passes frames to classifier
   - sends detection events to coordinator

3. `GuardAccessibilityService`
   - tracks foreground package
   - intercepts entry into locked apps
   - executes `GLOBAL_ACTION_HOME`

4. `NsfwClassifier`
   - loads TFLite model
   - preprocesses bitmaps
   - returns label/confidence

5. `DetectionCoordinator`
   - central decision layer
   - debounces repeated detections
   - triggers blur
   - locks package
   - requests app ejection

6. `OverlayManager`
   - displays full-screen blur/block overlay
   - hides overlay when safe

7. `LockManager`
   - stores blocked package names and expiry timestamps
   - answers whether a package is currently locked

8. `CurrentAppTracker`
   - source of truth for current foreground package
   - updated by accessibility service

9. `SettingsStore`
   - threshold
   - lock duration
   - blur-only vs blur-and-eject mode

## Milestones

## Milestone 0: Project Bootstrap

Status: In progress

### Goal

Create a clean Android project skeleton with the required permissions, dependencies, and placeholder services.

### Steps

1. Initialize Android project with Kotlin and minimum SDK target.
2. Add dependencies for:
   - TensorFlow Lite
   - coroutines
   - DataStore or encrypted preferences
   - Compose or simple XML UI
3. Create app package structure.
4. Add manifest declarations for:
   - foreground service
   - media projection foreground service type
   - accessibility service
   - overlay permission usage
5. Add accessibility service config XML.
6. Add placeholder main screen and service classes.

### Exit Criteria

- Project builds successfully.
- App installs on test device.
- All core classes compile, even if stubbed.

### Checklist

- [ ] Android project created
- [x] Manifest compiles
- [x] Foreground service declared correctly
- [x] Accessibility service declared correctly
- [x] Overlay permission flow stubbed
- [ ] Build succeeds on device target

## Milestone 1: Permission and Session Setup

Status: In progress

### Goal

Make the app capable of entering a valid runtime state with overlay permission, accessibility enabled, and an active screen-capture session.

### Steps

1. Implement setup/status screen.
2. Add checks for:
   - overlay permission
   - accessibility enabled
   - active projection session state
3. Implement projection consent flow from `MainActivity`.
4. Pass projection result into `ScreenCaptureService`.
5. Start service as foreground service with proper notification.
6. Add clear status indicators for enabled/disabled subsystems.

### Exit Criteria

- User can enable all required capabilities from the app.
- Foreground service starts successfully after projection consent.
- App shows current readiness state.

### Checklist

- [x] Overlay permission request works
- [x] Accessibility settings navigation works
- [x] Projection consent launches successfully
- [x] Projection result reaches service
- [x] Foreground notification appears
- [x] Ready/not-ready state updates correctly

## Milestone 2: Screen Capture Pipeline

Status: In progress

### Goal

Capture frames reliably from the screen and make them available for inference with acceptable memory behavior.

### Steps

1. Create `MediaProjection` session management in service.
2. Create virtual display and image reader.
3. Convert captured frames into processable bitmaps.
4. Add frame sampling logic with configurable FPS.
5. Add lifecycle cleanup for:
   - projection
   - virtual display
   - image reader
   - service stop
6. Add lightweight logging or debug counters for capture health.

### Exit Criteria

- Service continuously receives frames while session is active.
- No obvious image reader leaks or repeated crashes.
- Sampling is stable on target device.

### Checklist

- [x] Virtual display created successfully
- [x] Frames arrive continuously
- [x] Bitmap conversion works
- [x] Sampling rate is controlled
- [ ] Rotation/config changes do not immediately break session
- [x] Cleanup works on stop

## Milestone 3: On-Device NSFW Detection

Status: In progress

### Goal

Integrate and validate the TFLite model for fast on-device explicit-content detection.

### Steps

1. Add `.tflite` model asset.
2. Implement classifier initialization and warm-up.
3. Implement bitmap preprocessing:
   - resize
   - normalize
   - tensor formatting
4. Implement inference method and result mapping.
5. Define detection thresholds:
   - suspicious threshold for immediate blur
   - lock threshold for blur + eject + lock
6. Create a simple offline validation flow with sample safe/unsafe images.

### Exit Criteria

- Classifier runs on device.
- Inference latency is measured.
- Safe and explicit test images produce reasonable outputs.

### Checklist

- [ ] Model loads successfully
- [ ] Inference returns stable results
- [ ] Thresholds configurable
- [x] Warm-up path implemented
- [ ] Sample test images evaluated
- [ ] Average inference time recorded

## Milestone 4: Blur Overlay Enforcement

### Goal

Show a fast full-screen blur/block overlay immediately after detection.

### Steps

1. Implement `OverlayManager`.
2. Create overlay view for full-screen block/blur.
3. Ensure overlay can be shown and hidden safely from service-driven flows.
4. Pre-create or reuse overlay to reduce latency.
5. Add message/state text if needed.
6. Prevent duplicate overlay stacking.

### Exit Criteria

- Overlay appears quickly and consistently.
- Overlay can be dismissed by internal app logic.
- No duplicated or stuck overlays during repeated detections.

### Checklist

- [ ] Overlay permission confirmed
- [ ] Full-screen overlay renders above target apps
- [ ] Show/hide calls are stable
- [ ] Repeated triggers do not create duplicate views
- [ ] Overlay latency feels acceptable

## Milestone 5: Foreground Package Tracking and App Ejection

### Goal

Track which app is in the foreground and eject the user to Home after a lock-worthy detection.

### Steps

1. Implement `GuardAccessibilityService`.
2. Track current foreground package from window events.
3. Maintain `CurrentAppTracker`.
4. Implement `AppEjector` wrapper around `performGlobalAction`.
5. Add ejection debounce to avoid action loops.
6. Wire detection coordinator to call:
   - blur
   - lock current package
   - go Home

### Exit Criteria

- Current foreground package is known reliably enough for target use.
- Detection can eject user to Home.
- Repeated ejections do not create uncontrolled loops.

### Checklist

- [ ] Accessibility service connects successfully
- [ ] Foreground package updates correctly
- [ ] `GLOBAL_ACTION_HOME` works
- [ ] Debounce prevents repeated rapid ejections
- [ ] Detection and ejection path works end-to-end

## Milestone 6: Temporary Package Locking and Re-entry Blocking

### Goal

Block re-entry into apps that triggered explicit-content detection for a configurable lock duration.

### Steps

1. Implement `LockManager`.
2. Persist lock state and expiry timestamps.
3. When lock-worthy detection fires:
   - lock current package
   - record expiry
4. On accessibility package-change events:
   - check if package is locked
   - if locked, show overlay immediately
   - eject to Home immediately
5. Add manual unlock-all control in app UI.
6. Add lock expiration cleanup.

### Exit Criteria

- Locked apps are unusable during active lock window.
- Reopening a locked app triggers immediate re-blocking.
- Expired locks stop interfering automatically.

### Checklist

- [ ] Package lock state stored correctly
- [ ] Locked package lookup is reliable
- [ ] Reopening locked app triggers overlay immediately
- [ ] Reopening locked app triggers Home action immediately
- [ ] Expired locks are removed
- [ ] Manual unlock-all works

## Milestone 7: Settings, Debouncing, and Safety Controls

### Goal

Stabilize user behavior with basic settings and guardrails.

### Steps

1. Add settings for:
   - suspicious threshold
   - lock threshold
   - lock duration
   - blur-only vs blur-and-eject
2. Add detection debounce window.
3. Add package ejection cooldown.
4. Add optional safe package allowlist.
5. Add service/session status indicators.
6. Add simple debug view for current package and last detection.

### Exit Criteria

- User can tune detection aggressiveness and lock behavior.
- System is stable under repeated content changes.

### Checklist

- [ ] Threshold settings persist
- [ ] Lock duration setting persists
- [ ] Blur-only mode works
- [ ] Ejection cooldown works
- [ ] Allowlist works if implemented
- [ ] Debug info visible in development build

## Milestone 8: Performance Tuning on Real Device

### Goal

Reduce latency and battery cost while keeping acceptable detection quality.

### Steps

1. Measure end-to-end detection latency:
   - frame capture
   - preprocessing
   - inference
   - overlay show
   - Home action
2. Tune frame sampling rate.
3. Tune bitmap resolution.
4. Tune model thresholds.
5. Reuse buffers and avoid repeated bitmap allocations.
6. Profile battery and thermal behavior over extended sessions.

### Exit Criteria

- Detection latency is known and acceptable for personal use.
- App does not overheat device quickly under normal testing.

### Checklist

- [ ] Latency measured on target phone
- [ ] Sampling FPS tuned
- [ ] Bitmap size tuned
- [ ] Excess allocations reduced
- [ ] Battery impact observed
- [ ] Thermal behavior observed

## Milestone 9: Real-World Validation

### Goal

Validate behavior in actual target apps and media scenarios.

### Test Scenarios

1. Telegram explicit image
2. Telegram explicit video
3. Browser explicit image page
4. Browser explicit video page
5. Gallery local explicit image
6. Gallery local explicit video
7. Instagram explicit image/video if available for testing
8. Safe content with skin exposure but non-explicit context
9. Fast swiping between images/videos
10. Reopening a locked app repeatedly

### Exit Criteria

- App behaves consistently enough for the intended personal use case.
- Known weak cases are documented.

### Checklist

- [ ] Telegram image test completed
- [ ] Telegram video test completed
- [ ] Browser image test completed
- [ ] Browser video test completed
- [ ] Gallery image test completed
- [ ] Gallery video test completed
- [ ] Safe-content false positives reviewed
- [ ] Re-entry blocking reviewed
- [ ] Known failure modes documented

## Implementation Order

Build in this order:

1. Bootstrap project
2. Permission/setup flow
3. Screen capture service
4. TFLite classifier
5. Overlay manager
6. Accessibility package tracking
7. Detection coordinator
8. Lock manager
9. Re-entry enforcement
10. Settings and tuning

Do not start with Telegram- or browser-specific behavior. Build the generic pipeline first.

## Data Model

### Lock Record

- `packageName: String`
- `lockedAt: Long`
- `expiresAt: Long`
- `reasonConfidence: Float`

### Runtime Detection Event

- `timestamp: Long`
- `packageName: String?`
- `confidence: Float`
- `action: Blur | LockAndEject`

Persistent history is optional for first release.

## Decision Rules

### Initial Rule Set

- If confidence >= suspicious threshold:
  - show blur immediately

- If confidence >= lock threshold and current package is known:
  - show blur
  - lock package
  - eject to Home

- If locked package is reopened before expiry:
  - show blur immediately
  - eject to Home immediately

### Debounce Rules

- Ignore duplicate lock creation for same package within short cooldown
- Ignore repeated Home actions within short cooldown
- Avoid repeated overlay recreation while already visible

## Known Constraints

- Some content may be visible briefly before blur.
- Videos are harder than static images.
- Gallery/local media can render very quickly.
- `MediaProjection` consent is still required.
- If projection session ends, detection stops.
- If accessibility is disabled, package ejection and re-entry blocking stop working.
- You cannot force-stop third-party apps.

## Risks and Mitigations

### Risk: visible first-frame exposure

Mitigation:

- lower sampling interval
- preload model
- pre-create overlay
- trigger blur on lower threshold before lock threshold

### Risk: false positives

Mitigation:

- adjustable thresholds
- optional allowlist
- blur-first before lock if needed

### Risk: repeated action loops

Mitigation:

- debounce overlay
- debounce Home action
- centralize decision-making in coordinator

### Risk: battery drain and heat

Mitigation:

- tune FPS
- reduce bitmap size
- avoid needless allocations
- test on actual device early

### Risk: session drops

Mitigation:

- detect projection stop
- show clear UI state
- allow quick restart from main screen

## Definition of Done

The implementation is considered done when all of the following are true:

- [ ] App can be installed and launched on target phone
- [ ] Overlay permission can be granted and verified
- [ ] Accessibility can be enabled and verified
- [ ] Screen capture session can be started successfully
- [ ] Frames are sampled continuously while session is active
- [ ] NSFW model runs locally on sampled frames
- [ ] Explicit content triggers blur overlay
- [ ] Lock-worthy detection ejects user to Home
- [ ] Current app package is locked for configured duration
- [ ] Reopening locked app immediately re-triggers blur and Home
- [ ] Expired locks stop blocking automatically
- [ ] Settings persist across app restarts
- [ ] App remains stable during repeated detections and app reopen attempts
- [ ] Known limitations are documented

## Immediate Next Tasks

1. Validate the real frame pipeline on a physical device.
2. Add a lightweight debug preview path or frame telemetry if frame delivery is unstable.
3. Validate the `.tflite` model asset on a physical device.
4. Connect `DetectionCoordinator` to app ejection and locked-package re-entry handling.
5. Add locked-app re-entry enforcement in `GuardAccessibilityService`.
6. Add threshold and lock-duration settings storage.
7. Tune sampling interval and threshold defaults after device testing.
