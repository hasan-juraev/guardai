# GuardAI System-Level Roadmap

## Purpose

This roadmap turns the system-level architecture into an execution plan.

It is designed for the scenario where GuardAI evolves from a prototype Android app into a controlled-device, system-level product.

## Summary

Recommended path:

1. keep the current APK as a research prototype only
2. validate demand and core behavior on a small pilot
3. move to a controlled-device privileged-app build
4. move key enforcement into a custom system service on a custom Android build
5. consider dedicated hardware only after the software product proves real demand

## Decision Frame

Do not ask:

- "Can we build the final ideal version now?"

Ask:

- "What is the smallest next version that reduces technical uncertainty?"

## Phase 0: Prototype Stabilization

### Goal

Use the current APK to learn:

- whether users actually want this
- which environments matter most
- whether the model can be tuned enough to be useful
- what Android breaks in practice

### Deliverables

- stable prototype APK
- detection telemetry
- labeled test scenarios
- real-device bug list
- basic usage feedback

### Must-Have Work

- improve logging and diagnostics
- gather examples of:
  - Telegram images
  - Telegram videos
  - browser pages
  - gallery content
  - false positives
- record:
  - latency
  - session-drop frequency
  - detection misses
  - enforcement failures

### Success Criteria

- prototype runs on target phone models
- top failure modes are known
- at least one user finds it materially useful

### Main Risks

- model accuracy remains too weak
- MediaProjection remains too fragile
- device-specific failures dominate

### Exit Decision

- continue only if the prototype is useful enough to justify a controlled-device version

## Phase 1: Product Validation

### Goal

Answer whether this should become a real product.

### Questions To Resolve

- is the need real beyond your own use case?
- is the user willing to accept setup friction?
- is "best-effort blocking" still valuable even without perfection?
- which market is primary?
  - parental control
  - accountability / recovery
  - managed safe-phone product

### Deliverables

- product hypothesis document
- target user definition
- value proposition
- usage interviews or pilot feedback
- prioritized requirements

### Recommended Pilot

- 5 to 20 users
- controlled device set
- private APK only
- no Play Store effort yet

### Metrics

- activation rate
- daily/weekly usage
- session survival
- perceived usefulness
- false positive complaints
- false negative complaints

### Success Criteria

- users say it is useful even with current limitations
- one target segment emerges clearly

### Exit Decision

- if no strong product pull exists, stop
- if strong pull exists, move to controlled-device architecture

## Phase 2: Controlled-Device Privileged Build

### Goal

Move from user-space app constraints toward a device you control.

### Technical Direction

- signed privileged app
- controlled Android image
- limited device support matrix
- custom launcher
- persistent services
- stronger app/task enforcement

### Deliverables

- privileged system app build
- managed setup flow
- custom launcher or safe launcher mode
- persistent protection state across reboot
- stronger package/task blocking

### Engineering Focus

- app suspension or stronger task redirection
- persistent services on boot
- better recovery when protection stops
- fewer permission/setup steps
- more robust diagnostics

### Success Criteria

- no fragile manual setup each session
- enforcement survives reboot reliably
- reopening blocked apps is consistently handled

### Main Risks

- OEM-specific differences
- privileged-permission complexity
- maintenance burden across devices

### Exit Decision

- continue only if controlled-device version substantially improves reliability

## Phase 3: Detection Stack Upgrade

### Goal

Turn the model side into a product-grade detection stack.

### Technical Direction

- fast classifier for broad unsafe gating
- detector for confirmation/localization
- temporal smoothing
- real-world threshold tuning
- performance profiling

### Deliverables

- evaluated classifier model
- evaluated detector model
- hybrid decision engine
- labeled validation dataset
- benchmark report

### Required Datasets

- safe skin exposure
- explicit still images
- explicit video frames
- anime / hentai
- cropped body parts
- low-light content
- collage / meme / screenshot content
- Telegram and browser captures

### Metrics

- frame-level recall
- frame-level precision
- time-to-block
- false-negative rate on explicit clips
- false-positive rate on safe clips

### Success Criteria

- blocking latency acceptable for target use case
- false positives low enough to be tolerable
- explicit video performance materially better than prototype

### Main Risks

- public models are not good enough
- evaluation data is hard to gather safely
- conversion/runtime differences damage model quality

### Exit Decision

- if accuracy is not good enough even on controlled devices, stop before deeper OS work

## Phase 4: System Service / Custom ROM

### Goal

Move enforcement into the OS and stop depending on app-level workarounds.

### Technical Direction

- custom AOSP build
- `GuardSystemService`
- deeper app/task/display enforcement
- boot-time protection ownership

### Deliverables

- framework-integrated service
- stable boot lifecycle
- protection-state watchdog
- stronger launcher/task redirection
- cleaner recovery from crashes

### Engineering Focus

- ActivityTaskManager integration
- WindowManager integration
- package suspension / restriction policy
- service watchdogs
- update-safe architecture

### Success Criteria

- protection behaves like an OS feature, not a fragile app
- enforcement survives backgrounding, reboot, and normal user behavior

### Main Risks

- AOSP complexity
- OTA maintenance burden
- device compatibility costs

### Exit Decision

- continue only if software-only controlled device shows strong user demand

## Phase 5: Dedicated Device

### Goal

Ship a phone or dedicated device built around the protection model.

### Technical Direction

- curated hardware platform
- tuned inference acceleration
- controlled firmware
- controlled OTA path

### Deliverables

- hardware reference platform
- manufacturing/BOM assumptions
- support/update plan
- secure provisioning flow

### Main Risks

- capital requirements
- supply chain
- warranty/support load
- regulatory/commercial complexity

### Success Criteria

- strong proven demand
- software stack already validated
- clear reason hardware is needed beyond ROM/device-owner path

### Exit Decision

- do not do this unless the ROM/system product is already validated

## Cross-Cutting Workstreams

## Workstream A: Android Systems

### Scope

- privileged permissions
- service lifecycle
- task and package control
- custom ROM/system service

### Skills Needed

- Android platform engineer
- AOSP systems engineer

## Workstream B: ML and Evaluation

### Scope

- model selection
- conversion/runtime validation
- threshold tuning
- temporal smoothing
- evaluation tooling

### Skills Needed

- ML engineer
- CV engineer

## Workstream C: UX and Product

### Scope

- setup friction reduction
- recovery flows
- admin controls
- safe launcher / restricted UX

### Skills Needed

- product designer
- Android UI engineer

## Workstream D: Security and Release

### Scope

- signing
- OTA/update strategy
- secure model delivery
- rollback paths

### Skills Needed

- release/security engineer

## Team Recommendation

Minimum serious team:

- 1 Android app engineer
- 1 Android systems/AOSP engineer
- 1 ML/CV engineer
- 1 QA engineer with real device testing

Better team:

- 2 Android engineers
- 1 AOSP engineer
- 1 ML engineer
- 1 QA / automation engineer
- 1 product/UX owner

## Risk Register

## Risk 1: Accuracy never reaches acceptable level

Mitigation:

- use staged evaluation early
- do not overinvest in ROM/hardware before model quality is proven

## Risk 2: Android platform still disrupts controlled-device implementation

Mitigation:

- reduce supported device matrix
- use controlled firmware
- move into system service layer

## Risk 3: Product need is smaller than expected

Mitigation:

- validate with pilot before deeper engineering investment

## Risk 4: Play Store path becomes unusable

Mitigation:

- do not make Play the primary path early
- prioritize controlled-device and private distribution first

## Risk 5: Support burden is too high

Mitigation:

- minimize supported devices
- build diagnostics from day one
- design strong recovery flows

## Milestone Gates

## Gate 1: Continue Beyond Prototype?

Proceed only if:

- users find the prototype valuable
- core use case is clear
- misses and false positives seem improvable

## Gate 2: Continue Beyond Controlled Device?

Proceed only if:

- controlled-device version materially improves reliability
- users still want it
- the support burden is manageable

## Gate 3: Continue Into Custom ROM?

Proceed only if:

- product value is proven
- app-level architecture is the clear bottleneck
- you have AOSP engineering capacity

## Gate 4: Continue Into Hardware?

Proceed only if:

- ROM/system version is already working
- you have clear commercial justification
- you can fund device lifecycle support

## Suggested Timeline Shape

This is not a calendar estimate. It is a sequence estimate.

### Short Term

- Phase 0
- Phase 1

### Medium Term

- Phase 2
- Phase 3

### Long Term

- Phase 4
- optional Phase 5

## Recommended Next Steps

1. keep the current APK as the evaluation harness
2. run a small controlled pilot
3. gather explicit-content test corpus and diagnostics
4. decide whether the product is:
   - parental control
   - accountability tool
   - safe-phone platform
5. if still compelling, move to a controlled-device privileged build

## Final Recommendation

The best execution strategy is:

- validate product need first
- validate model quality second
- validate controlled-device reliability third
- only then move to AOSP/system work
- only after that consider hardware

That path keeps engineering proportional to evidence.
