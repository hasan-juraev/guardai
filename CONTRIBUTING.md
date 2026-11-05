# Contributing to GuardAI

Thanks for helping. GuardAI is an early prototype, so small, well-tested changes are much easier to review than large rewrites.

## Before you start

- Read the [README](README.md) and [docs/architecture.md](docs/architecture.md).
- For anything beyond a small fix, open an issue first and describe the problem and the approach you have in mind.
- Check the planning docs ([IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md), [DETECTOR_IMPROVEMENT_PLAN.md](DETECTOR_IMPROVEMENT_PLAN.md)) to see whether the work is already planned.

## Workflow

1. Create a branch from `main`, for example `fix/eject-timeout` or `feat/letterbox-input`.
2. Make your change, following [docs/development.md](docs/development.md#code-conventions).
3. Build and lint:

   ```bash
   ./gradlew assembleDebug lint
   ```

4. Run the relevant rows of the [manual test plan](docs/development.md#manual-test-plan) on a real device.
5. Update the docs (see below) and add an entry under **Unreleased** in [CHANGELOG.md](CHANGELOG.md).
6. Open a pull request.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <short summary in the imperative mood>

<optional body: what changed and why>
```

Common types: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `build`, `chore`.

Example: `fix: stop watchdog from rebuilding VirtualDisplay on Android 14`

## Pull requests

Include:

- **What and why:** the problem and how the change solves it.
- **Testing:** device model, Android version, which manual-test rows you ran, and the results.
- **Metrics:** for detection or performance changes, before-and-after numbers (decision counts, `latency_ms` p50/p95, battery drop). See [docs/metrics.md](docs/metrics.md#analyzing-a-session).
- **Risk:** anything that changes when the overlay, lock or eject fires.

## Keeping docs current

Outdated docs are worse than none. Update them in the same pull request as the code:

| If you change… | Update… |
| --- | --- |
| A constant or threshold | [docs/configuration.md](docs/configuration.md), and [docs/detection.md](docs/detection.md) if it affects detection |
| Labels, model or smoothing rules | [docs/detection.md](docs/detection.md) |
| Components, threads or flows | [docs/architecture.md](docs/architecture.md) |
| A `MetricsLogger` event | [docs/metrics.md](docs/metrics.md) |
| Setup, permissions or build steps | [README.md](README.md) and [docs/development.md](docs/development.md) |
| A new failure mode and its fix | [docs/troubleshooting.md](docs/troubleshooting.md) |

The four upper-case plan documents in the repo root describe **future** work. If you implement part of one, update the reference docs in `docs/` to describe the new behavior, and leave the plan as historical context.

## Content and safety rules

- **Never commit explicit test media**, screenshots of detections, or logs from personal devices. Keep test content local.
- **Don't add network access** without a design discussion. On-device-only processing is a core privacy property.
- **Don't log frame contents** or anything beyond package names and labels.
- **Model files:** record where any new model came from and its license in [the model notes](app/src/main/assets/models/README.md).
