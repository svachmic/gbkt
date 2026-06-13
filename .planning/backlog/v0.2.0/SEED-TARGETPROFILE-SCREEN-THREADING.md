---
id: SEED-TARGETPROFILE-SCREEN-THREADING
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 multi-target support
scope: medium
triage_disposition: RE-DEFERRED
triage_date: 2026-06-12
---

# SEED-TARGETPROFILE-SCREEN-THREADING: Thread TargetProfile.screen Through Codegen Visitors

## Summary

Thread `TargetProfile.screen` (specifically `ScreenSpec.width` and `ScreenSpec.height`) through
the codegen visitors so that screen dimensions are not hard-bound to `GameBoyConstants`, enabling
non-Game-Boy targets.

## Background

**Planted during:** Phase 17, Plan 17-05 — the QUAL-02/QUAL-03 literal replacement.

In Phase 17-02, `TargetProfiles.GAME_BOY_SCREEN` was added as the single source of truth (SSoT)
for the 160×144 screen dimensions. `GameBoyConstants.SCREEN_WIDTH` and `SCREEN_HEIGHT` now derive
from this preset:

```kotlin
// GameBoyConstants.kt
val SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width   // 160
val SCREEN_HEIGHT = TargetProfiles.GAME_BOY_SCREEN.height  // 144
```

In Phase 17-05, the 8 in-scope magic literals in `ActorVisitor.kt`, `GBDKSystemVisitor.kt`, and
`PlatformerVisitor.kt` were replaced with `GameBoyConstants.SCREEN_WIDTH/HEIGHT`. This satisfies
QUAL-02 for v0.1.1 (no magic literals in framework codegen visitors).

**D-06 deferral decision:** The deeper refactor — passing a `TargetProfile` (or `ScreenSpec`)
into each visitor so that screen dimensions come from the profile rather than from a static
`GameBoyConstants` object — was deferred to v0.2.0. The static constants are the correct
v0.1.1 solution; threading them dynamically requires changing visitor constructor signatures
across the codegen pipeline, which is a v0.2.0 multi-target concern.

## Problem This Solves

Currently, `GameBoyConstants.SCREEN_WIDTH/HEIGHT` are derived from `TargetProfiles.GAME_BOY_SCREEN`
(the Game Boy preset). If a future target has different screen dimensions (e.g., a hypothetical
Sega Master System backend, or a 160×128 screen variant), the codegen visitors would need to be
updated to accept the profile from the pipeline rather than reading a static constant.

This is a prerequisite for any non-Game-Boy backend implementation.

## Scope Estimate

**Medium** — Visitor constructor signatures in 3+ visitors need `ScreenSpec` or `TargetProfile`
parameters. The codegen pipeline orchestrator (`GBDKPipeline`) must pass the active profile
into visitor construction. Test fixtures that construct visitors directly need updating.

Estimated work: 1 phase, 4–6 plans.

## Hook This Seed Builds On

`TargetProfiles.GAME_BOY_SCREEN` (added in Phase 17-02, `gbkt-core/.../constraints/TargetProfiles.kt`)
is the single hook this seed extends. The visitor wiring would replace:

```kotlin
// Current (static binding)
CLiteral(GameBoyConstants.SCREEN_WIDTH)

// Target (profile-threaded)
CLiteral(profile.screen.width)
```

where `profile` is passed into the visitor by `GBDKPipeline.build*File()`.

## Additional Scope: Test Fixture Migration

Several test files construct `ScreenSpec(width=160, height=144)` inline (identified in the
QUAL-LITERALS.md exemption table, rows #31, #33):

- `gbkt-analysis/.../TestFixtures.kt`
- `gbkt-backend-api/.../BackendRegistryTest.kt`

These should migrate to `TargetProfiles.GAME_BOY_SCREEN` when this seed is activated, as part
of the consistency cleanup.

## Trigger Conditions

Surface this seed when:
1. A v0.2.0 milestone is being planned with multi-target backend support as a goal, OR
2. A non-Game-Boy backend is being prototyped and hits the static-constant limitation

## Related

- Phase 17-02: Added `TargetProfiles.GAME_BOY_SCREEN` (SSoT, single-source hook)
- Phase 17-05: QUAL-02/QUAL-03 literal replacement (this plan)
- QUAL-LITERALS.md exemption table row for D-06 deferral
