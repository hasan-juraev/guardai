# GuardAI System-Level Architecture

## Purpose

This document describes a realistic architecture for turning GuardAI from a normal Android app into a system-level product embedded into a phone, custom Android build, or managed device stack.

This architecture is for the problem statement:

- detect explicit visual content on-device,
- block it immediately with a black screen,
- prevent the user from continuing in the offending app,
- persist across app switches and device restarts,
- avoid fragile dependence on `MediaProjection`, user-space overlays, and accessibility hacks.

## Executive Summary

The correct long-term direction is:

1. start with an Android **system app** on controlled devices,
2. move to a **Device Owner / managed-device** model where possible,
3. if stricter guarantees are required, move detection and enforcement into the **framework / system service layer** of a custom AOSP build,
4. only move into custom hardware after the OS-level design is proven.

Do **not** start by modifying SurfaceFlinger or building phone hardware from scratch unless you already have a strong Android systems team.

The recommended sequence is:

- Phase 1: Privileged system app on existing Android hardware
- Phase 2: Custom AOSP ROM with framework hooks
- Phase 3: Dedicated child-safe or accountability-focused device

## Product Modes

### Mode A: Managed Device / Device Owner

Use for:

- supervised phones,
- family devices,
- enterprise-style provisioning,
- pilot deployments.

Strengths:

- stronger app control,
- better policy enforcement,
- easier operational model than full ROM work.

Weaknesses:

- still not the deepest possible visual pipeline control,
- still depends on Android management constraints.

### Mode B: System App on Controlled Firmware

Use for:

- phones where you control the OS image,
- a signed system package with privileged permissions,
- custom launcher + privileged services.

Strengths:

- much better than a Play app,
- can survive reboot,
- can suspend/disable apps more cleanly,
- can integrate more deeply into the system.

Weaknesses:

- still less powerful than framework-level render interception.

### Mode C: Custom AOSP Build

Use for:

- strong guarantees,
- persistent protection,
- productized phone experience.

Strengths:

- best control of render, app launch, and policy,
- no dependency on Play policy,
- no fragile accessibility/media-projection setup.

Weaknesses:

- much higher engineering cost,
- OTA/update burden,
- device compatibility burden.

## Recommended Architecture

## Layer 1: User Experience Layer

### Components

- `GuardLauncher`
- `GuardSettings`
- `GuardianSetupWizard`
- `GuardianAdminConsole` (local on-device parent/admin settings)

### Responsibilities

- device setup and pairing,
- parent/admin configuration,
- whitelists / blocked apps,
- lock duration and policy settings,
- local diagnostics,
- recovery / emergency unlock flows.

### Notes

This layer should not contain core enforcement logic. It is only the control surface.

## Layer 2: Policy and Enforcement Layer

### Components

- `ContentPolicyService`
- `AppEnforcementService`
- `SessionStateService`
- `RecoveryService`

### Responsibilities

- decide when to blur, lock, suspend, or eject,
- maintain current policy state,
- track which app/user/session is active,
- trigger app exit, task removal, launcher redirect, or screen block,
- recover from service restarts.

### Core Data Model

- `AppPolicy`
  - package name
  - allowlist status
  - restricted status
  - suspend/block behavior

- `DetectionEvent`
  - timestamp
  - source app
  - model confidence
  - matched labels
  - action taken

- `EnforcementState`
  - current foreground package
  - current blocked package
  - lock expiry
  - projection/render state
  - service health state

## Layer 3: Visual Detection Layer

### Components

- `FrameCaptureService`
- `InferenceRuntime`
- `DetectionPostProcessor`
- `TemporalDecisionEngine`

### Responsibilities

- capture candidate frames before or during display flow,
- preprocess frames,
- run NSFW model,
- post-process detector/classifier outputs,
- smooth decisions over time,
- publish explicit-content events to policy layer.

### Strong Recommendation

Use a **two-stage detection stack** in production:

1. a fast lightweight **classifier** for frame-level unsafe gating
2. a detector for confirmation or localization on suspicious frames

Why:

- detector-only pipelines are slower and noisier,
- classifier-only pipelines are fast but miss context,
- hybrid pipelines are more practical for video.

### Temporal Decision Engine

This is mandatory for video.

Recommended behavior:

- `blur` on 1 suspicious frame
- `lock/eject` on 2-3 consecutive strong frames
- hold blur for `1-2s` after last hit
- maintain rolling confidence window

### Model Pipeline

Recommended sequence:

1. downscale frame
2. run fast classifier
3. if classifier is above suspicious threshold:
   - run detector
4. aggregate detector classes:
   - genital / anus exposed -> hard block
   - exposed breasts / buttocks / belly -> configurable block
5. emit detection decision

## Layer 4: System Integration Layer

This is the most important architectural difference from the APK version.

### Option 1: Privileged System App Integration

Preferred first system-level step.

Use:

- privileged permissions,
- system UID integrations where allowed,
- app suspension / task control,
- custom launcher fallback,
- Device Owner APIs if supervised.

Key abilities:

- suspend packages,
- redirect to launcher,
- disable components,
- control kiosk-style experience,
- start persistent services on boot.

### Option 2: Framework Service Hook

For stronger enforcement, create a dedicated framework service:

- `GuardSystemService`

Responsibilities:

- expose binder interface to UI/admin app,
- manage detection runtime,
- own enforcement state,
- coordinate with ActivityTaskManager / PackageManager / WindowManager.

This service lives in the system image and starts during boot.

### Option 3: Display / Composition Hook

For the strongest visual guarantees, intercept closer to rendering.

Potential integration points:

- WindowManager / display pipeline hooks,
- screenshot/readback pipeline in framework,
- SurfaceFlinger-adjacent capture path,
- vendor compositor hook,
- hardware composer assist path.

Important:

Do not make this the first implementation step.

It is the most powerful, but also the riskiest and hardest to maintain.

## Layer 5: App Control Layer

### Components

- `ForegroundAppTracker`
- `AppSuspendController`
- `TaskEjectController`
- `LauncherRedirectController`

### Responsibilities

- determine current top activity/package,
- suspend or block offending apps,
- remove offending task stack,
- send user to safe launcher/home screen,
- prevent re-entry during lock window.

### Enforcement Ladder

Recommended order:

1. black-screen block immediately
2. redirect to safe launcher/home
3. suspend offending app for configured duration
4. if reopen attempted, deny and redirect again

This is stronger and cleaner than accessibility-only Home actions.

## Layer 6: Storage and Telemetry Layer

### Components

- `EncryptedPolicyStore`
- `DetectionEventStore`
- `HealthTelemetryStore`

### Responsibilities

- persist settings,
- persist lock state,
- store local event history,
- record diagnostics:
  - model load state
  - inference timing
  - service restarts
  - render/session failures

### Privacy Principle

Do not store raw frames unless explicitly enabled in an engineering build.

Store only:

- metadata,
- label/confidence,
- app/package,
- timestamps,
- service health state.

## Layer 7: Update and Recovery Layer

### Components

- `ModelUpdateService`
- `OTACompatibilityService`
- `CrashRecoveryService`

### Responsibilities

- update models safely,
- roll back bad models,
- preserve compatibility across OTA updates,
- restart critical services after crashes,
- detect stale/inactive protection state.

### Model Rollout Strategy

Keep:

- `current model`
- `previous stable model`
- `candidate model`

Switch only after health checks pass.

## Deployment Architecture

## Phase 1: Controlled Device Build

### Goal

Ship on a small set of known Android models.

### Characteristics

- privileged system app
- Device Owner if possible
- custom launcher
- persistent background services
- no Play Store dependence

### Why

This is the fastest path to a real usable product without modifying the full Android framework immediately.

## Phase 2: Custom ROM

### Goal

Move core detection and enforcement into the OS.

### Characteristics

- custom AOSP image
- `GuardSystemService`
- stronger task/package suspension
- boot-time persistence
- integrated settings and diagnostics

### Why

This is the real product-grade version if you need strong guarantees.

## Phase 3: Dedicated Device

### Goal

Deliver a dedicated safe phone or accountability device.

### Characteristics

- custom hardware selection
- NPU / DSP acceleration if available
- optimized camera/display/inference stack
- managed OTA lifecycle

### Why

Only do this after proving the ROM/system-software version works and users want it.

## Detailed Runtime Flow

1. Device boots.
2. `GuardSystemService` starts.
3. `ContentPolicyService` loads settings.
4. `FrameCaptureService` attaches to system-approved render/capture path.
5. `ForegroundAppTracker` monitors active task.
6. Frames are sampled continuously or adaptively.
7. `InferenceRuntime` runs classifier.
8. Suspicious frames are escalated to detector.
9. `TemporalDecisionEngine` confirms sustained explicit content.
10. `ContentPolicyService` determines action.
11. `AppEnforcementService`:
    - shows black screen
    - redirects to safe launcher/home
    - suspends offending app if required
12. `SessionStateService` records lock and health state.
13. Reopen attempts are blocked until expiry or admin override.

## Performance Strategy

### Real-Time Goal

Target:

- frame sampling: `8-12 FPS` effective on active media
- lightweight classifier every sampled frame
- heavier detector only on suspicious frames
- decision latency under `200-400ms` in common cases

### Optimizations

- reuse buffers aggressively
- downscale before inference
- use NNAPI / GPU / vendor NPU where stable
- adaptive sampling:
  - higher FPS on video/fullscreen media
  - lower FPS on static/safe screens
- isolate inference on dedicated worker threads

## Accuracy Strategy

If accuracy is mission-critical, the system must be trained and evaluated as a product, not just wired to a public model.

### Required Work

- curated validation set from target environments
  - Telegram
  - browser
  - gallery
  - short video clips
  - anime/hentai
  - memes/collages
  - safe edge cases
- per-label threshold tuning
- false positive mitigation
- temporal logic tuning
- separate policies for:
  - partial nudity
  - explicit nudity
  - sexual activity

### Realistic Principle

No single public off-the-shelf model should be expected to solve this product perfectly.

## Safety and Abuse Considerations

### Abuse Risks

- overblocking medical/educational content
- coercive use against partners/family
- surveillance misuse
- app lockdown causing user harm if recovery path is missing

### Required Safeguards

- admin/guardian role separation
- emergency recovery path
- local visible protection state
- clear unlock/audit mechanism
- optional allowlist
- strict local-only privacy defaults

## Security Architecture

### Requirements

- encrypted settings store
- tamper-resistant policy state
- signed updates only
- integrity checks for models
- secure boot / verified boot compatibility if device-controlled

### Recommended Controls

- signed model artifacts
- hash verification before model load
- crash-safe rollback
- system service permission boundaries

## What Not To Do First

Do not start with:

- full custom hardware manufacturing
- direct SurfaceFlinger patching as the first milestone
- broad multi-device consumer launch
- Play Store-first packaging

Those are later-stage efforts.

## Recommended Build Order

1. Keep current APK as evaluation harness only.
2. Build a privileged system app on controlled hardware.
3. Add custom launcher + Device Owner flow.
4. Move policy/enforcement into a system service.
5. Add improved detection stack and evaluation framework.
6. Only then consider custom ROM rollout.
7. Only after ROM validation consider dedicated hardware.

## Team Requirements

To execute this seriously, you need:

- Android app engineer
- Android systems/AOSP engineer
- ML engineer for model evaluation/tuning
- QA on real devices
- security/release engineer for OTA and signing

Without AOSP/system expertise, stop at the controlled-device privileged-app phase.

## Go / No-Go Recommendation

### Go if

- you are willing to own a controlled device environment,
- you accept staged rollout,
- you treat the current APK as a prototype, not the end product,
- you are prepared to tune models on real-world data.

### No-Go if

- you want universal immediate blocking from a standard Play app,
- you cannot control firmware/device environment,
- you do not want ongoing device/software maintenance.

## Final Recommendation

The best realistic architecture is:

- **short term:** privileged system app on controlled Android devices
- **mid term:** custom AOSP ROM with system service enforcement
- **long term:** dedicated device only if the ROM/system product proves market demand

That path preserves technical realism and avoids wasting effort on a user-space Android app trying to act like an OS feature.
