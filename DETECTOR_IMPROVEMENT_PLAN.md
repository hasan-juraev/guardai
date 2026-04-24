# GuardAI Detector Improvement Plan

## Purpose

This document breaks down how to improve the detection pipeline so GuardAI reacts more reliably to explicit video frames and does not depend on isolated single-label triggers.

This plan covers:

- detector aggregation improvements,
- model strategy,
- temporal/video strategy,
- thresholding,
- evaluation,
- practical next steps.

## Current Problem

The current detector path is still too brittle for production use because:

- it reacts to the strongest per-frame label too directly,
- it relies on a single public detector converted from ONNX to TFLite,
- it uses simple frame-level thresholds,
- video behavior is much harder than image behavior,
- scene changes and compression create unstable detections.

This causes:

- missed explicit frames,
- delayed reactions on video,
- occasional false positives,
- inconsistent lock vs blur decisions.

## Core Principle

Do not treat porn blocking as:

- "one frame in, one label out, one action"

Treat it as:

- "a stream of imperfect visual evidence aggregated over time"

That means the right design is a **decision engine**, not just a model call.

## Recommended Detection Stack

## Stage 1: Fast Unsafe Gating Model

### Role

Quickly decide whether a frame is suspicious enough to escalate.

### Requirements

- very low latency,
- high recall,
- acceptable false positive rate,
- stable on compressed video frames.

### Output

- `safe`
- `suspicious`
- optional unsafe confidence score

### Why

A fast gating model lets you sample more frames and reserve heavier analysis for suspicious ones.

This is often better than running a detector on every sampled frame.

## Stage 2: Explicit-Content Confirmation Model

### Role

Confirm whether the suspicious frame likely contains explicit sexual content.

### Options

1. detection model
2. stronger classifier
3. both

### Recommended

Use a detector only after gating, or use a hybrid stack:

- classifier -> detector confirmation

This is usually more stable than detector-only.

## Stage 3: Temporal Decision Engine

### Role

Convert noisy per-frame signals into a reliable block/eject decision.

### This is mandatory for video.

Without temporal logic, video detection will always feel weak.

## Detection Aggregation Improvements

## 1. Aggregate Across Labels, Not Just One Label

Current behavior is too label-fragmented.

Instead of:

- "top label is `BELLY_EXPOSED`, so do X"

Use grouped semantic categories:

- `hard_explicit`
  - genital exposed
  - anus exposed
  - sexual act indicators if available

- `soft_explicit`
  - exposed breasts
  - exposed buttocks
  - exposed belly

- `weak_context`
  - armpits
  - feet
  - skin-only ambiguous content

### Rule

Sum or max evidence across a group, not just the top label.

Example:

- one moderate genital detection -> strong evidence
- several moderate soft-explicit detections over consecutive frames -> strong evidence
- isolated weak-context detections -> insufficient

## 2. Use Confidence + Area + Persistence Together

A detection should not trigger only because score is high.

Use:

- confidence,
- normalized box area,
- temporal persistence,
- class severity.

### Example scoring model

`frame_score = class_weight * confidence * area_weight`

Where:

- explicit genital classes get higher class weight
- tiny boxes are discounted
- repeated detections accumulate across frames

## 3. Use a Rolling Window

For each frame, keep the last `N` results.

Recommended window:

- `8-16` recent frames depending on FPS

Then compute:

- max hard-explicit score in window
- sum of soft-explicit scores in window
- consecutive explicit-frame count
- number of suspicious frames in last `N`

### Example policy

- blur if:
  - any hard-explicit frame score exceeds threshold once
  - or soft-explicit score accumulates over `2-3` frames

- lock/eject if:
  - hard-explicit appears in `2` consecutive frames
  - or accumulated explicit score over rolling window exceeds threshold

## 4. Add Hysteresis

Avoid flicker and unstable block/unblock transitions.

### Example

- trigger blur at score `>= 0.55`
- release blur only after score stays below `0.35` for `1.5s`

This prevents:

- rapid toggling,
- flickering overlay,
- unstable video behavior.

## 5. Add Scene-Level State

Treat video detection as a state machine.

States:

- `SAFE`
- `SUSPICIOUS`
- `BLURRED`
- `LOCKING`
- `LOCKED`

### Benefits

- more predictable behavior
- easier tuning
- clearer diagnostics

### Example transitions

- `SAFE -> SUSPICIOUS`
  when a suspicious frame appears

- `SUSPICIOUS -> BLURRED`
  when short-term evidence crosses blur threshold

- `BLURRED -> LOCKING`
  when hard-explicit evidence persists

- `LOCKING -> LOCKED`
  when the app is ejected and package lock is recorded

## Model Strategy

## Option A: Keep Current NudeNet 320n Detector

### Pros

- already integrated
- label-level information
- can distinguish body-part classes

### Cons

- conversion risk from ONNX -> TFLite
- detector-only pipeline is noisy
- likely slower and less stable on video than a dedicated classifier

### Recommendation

Only keep this if:

- you validate that the converted model behaves reliably on-device,
- and you pair it with better temporal aggregation.

## Option B: Add a Fast NSFW Classifier

### Pros

- faster
- easier to tune
- better first-pass gating
- often more stable for video

### Cons

- lacks localization
- less explainable than detector classes

### Recommendation

This is the best next upgrade.

Use:

- lightweight classifier on every sampled frame
- detector only on suspicious frames

## Option C: Replace Detector with Better Native TFLite Model

### Pros

- avoids conversion artifacts
- easier Android deployment

### Cons

- may be harder to source
- may still require extensive tuning

### Recommendation

Worth exploring if the current converted model continues to underperform.

## Option D: Train / Fine-Tune Your Own Model

### Pros

- best alignment to your actual product
- can tune for Telegram/browser/gallery/video

### Cons

- highest cost
- requires dataset and ML pipeline

### Recommendation

Only after product need is validated.

## Best Practical Model Architecture

For this product, the best realistic stack is:

1. lightweight frame-level NSFW classifier
2. detector confirmation on suspicious frames
3. temporal aggregation over recent frames
4. action state machine with hysteresis

That is the best-practice production architecture.

## Video-Specific Best Practices

## 1. Increase Effective Sampling Rate

For video, `4 FPS` is too weak.

Target:

- `8-12 FPS` effective gating

But do not run the heavy detector at full rate if unnecessary.

### Suggested split

- classifier: `8-12 FPS`
- detector: `2-6 FPS`, only on suspicious windows

## 2. Keep Blur On Briefly After Trigger

Once triggered, keep blur visible for a short time.

Suggested:

- `1-2 seconds`

This avoids:

- exposure through transient misses
- flicker when the model drops a frame

## 3. Multi-Frame Confirmation For Lock

Do not lock on every single frame.

Suggested:

- lock after `2-3` consecutive hard-explicit frames
- or `window_score >= threshold`

## 4. Separate Image and Video Policies

One threshold set rarely works well for both.

Suggested:

- image mode:
  - more aggressive immediate lock

- video mode:
  - blur immediately
  - lock after short confirmation window

## Accuracy Improvement Roadmap

## Milestone 1: Better Aggregation

### Work

- grouped label scoring
- rolling window
- hysteresis
- state machine
- stronger diagnostics

### Expected Outcome

- more stable blocking
- fewer misses on short explicit clips
- fewer one-frame false triggers

## Milestone 2: Add Classifier Gating

### Work

- integrate fast classifier
- classifier runs on every sampled frame
- detector only runs on suspicious frames

### Expected Outcome

- better video recall
- lower detector load
- improved latency consistency

## Milestone 3: Real Evaluation Dataset

### Work

- collect or curate labeled frames/clips
- build evaluation harness
- measure precision/recall/latency

### Expected Outcome

- actual tuning signal
- evidence-based thresholds

## Milestone 4: Model Replacement or Fine-Tuning

### Work

- compare current detector with alternate models
- evaluate classifier candidates
- if needed, fine-tune or train custom model

### Expected Outcome

- better fit to target content

## Evaluation Plan

## Required Test Sets

Create separate sets for:

1. explicit still images
2. explicit video clips
3. Telegram content
4. browser porn pages
5. gallery local media
6. anime/hentai
7. safe false-positive cases
   - beach
   - medical
   - fitness
   - family photos
   - underwear / fashion

## Required Metrics

- frame-level recall
- frame-level precision
- clip-level recall
- time-to-blur
- time-to-lock
- false positive rate per session
- projection/session survival during long runs

## Tuning Guidelines

## Thresholds

Tune separately for:

- genital/anus
- breasts/buttocks
- ambiguous body areas

## Area

Small boxes should not count the same as large clear exposures.

## Temporal Persistence

Persistence should matter more than one isolated frame.

## Confidence Smoothing

Smooth scores across time instead of using raw frame spikes.

## Diagnostics To Add

Add these in the app:

- top 3 labels with scores
- grouped explicit score
- rolling window score
- blur state
- lock state
- reason for lock
- FPS / inference timing
- whether detector ran this frame or was skipped

These diagnostics should be shown only in debug/development builds.

## What To Change First

Highest-value order:

1. implement grouped scoring and rolling-window aggregation
2. implement blur/lock state machine with hysteresis
3. add classifier gating
4. add evaluation harness and dataset
5. compare with alternate models

## Recommended Immediate Implementation Plan

1. Replace direct label-trigger logic with grouped explicit scoring.
2. Track a rolling window of recent frame decisions.
3. Add `SAFE -> SUSPICIOUS -> BLURRED -> LOCKED` state machine.
4. Separate blur threshold from lock threshold using accumulated evidence.
5. Add debug telemetry for top labels and window scores.
6. Evaluate whether the current converted detector is good enough to keep.
7. If not, introduce a fast classifier and make the detector secondary.

## Final Recommendation

The biggest improvement will not come from a tiny threshold tweak.

It will come from moving from:

- single-frame label reactions

to:

- multi-frame evidence aggregation with a two-stage model pipeline.

That is the correct path if you want meaningful improvement on explicit video content.
