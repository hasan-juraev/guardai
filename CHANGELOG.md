# Changelog

All notable changes to GuardAI are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/). Before 1.0.0, any release may contain breaking changes.

## [Unreleased]

### Added

- Project documentation: README, architecture, detection, configuration, development, metrics and troubleshooting guides, a contributing guide, and this changelog.

### Fixed

- `assets/models/README.md` now names the model file the code actually loads (`320n_float32.tflite`).

## [0.1.0] - 2026-05-29

First prototype.

### Added

- Screen capture with `MediaProjection` in a foreground service, sampling at most every 125 ms.
- On-device detection with the NudeNet 320n TFLite model, with per-label score and area thresholds and per-label NMS.
- `BLUR` and `LOCK` decisions, with two-frame confirmation for `LOCK`.
- Full-screen black overlay for blocked content.
- 10-minute app locks stored in `SharedPreferences`, enforced on re-entry by an accessibility service.
- Ejection through accessibility Home, falling back to Back + Home, with a 5 s retry window and a 1 s cooldown.
- Capture stall watchdog with up to 3 pipeline restarts.
- Structured `GUARDAI_METRIC` logcat events for latency, decisions, ejection and battery.
- Setup screen with permission status, diagnostics, eject results and debug lock/unlock actions.
- Planning documents: implementation plan, detector improvement plan, system-level architecture and roadmap.

[Unreleased]: https://github.com/hasan-juraev/guardai/compare/fd3ede4...HEAD
[0.1.0]: https://github.com/hasan-juraev/guardai/tree/fd3ede4
