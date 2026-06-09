---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 05
subsystem: gbkt-genre-platformer
tags:
  - dsl
  - platformer-physics
  - d-12
  - d-14
  - wave-1
requires:
  - 12-03 (PlatformerExtensions.kt + platformerPhysics extension wiring scaffolding)
provides:
  - PlatformerPhysicsConfig.solidThreshold: Int? = null (D-12 field, codegen-ready)
  - PlatformerPhysicsConfig.jumpHoldMaxFrames: Int = 0 (D-14 field, codegen-ready)
  - PlatformerPhysicsBuilder.solidThreshold(value: Int) (D-12 DSL method)
  - PlatformerPhysicsBuilder.jumpHold(maxFrames: Int) (D-14 DSL method)
  - PlatformerPhysicsBuilder.buildConfig(): PlatformerPhysicsConfig (public helper for tests + codegen)
affects:
  - 12-08 (lowers solidThreshold to GBDK tilemap-collision codegen branch)
  - 12-11 (TilemapCollisionEmissionTest exercises solidThreshold-driven C emission)
  - 12-13 (lowers jumpHoldMaxFrames to GBDK jump-hold codegen branch)
  - 12-07 (top-level platformerPhysics { } DSL extension; consumes the new builder methods)
tech-stack:
  added: []
  patterns:
    - additive optional field on data class with null/zero sentinel = feature disabled
    - public buildConfig() helper alongside build(): GenericSystem (round-trip-testable inner config)
key-files:
  created:
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerPhysicsBuilderTest.kt
  modified:
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/domain/PlatformerTypes.kt
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerBuilders.kt
decisions:
  - solidThreshold uses Int? (null = no tilemap collision) instead of Int = 0, so existing tests that
    construct PlatformerPhysicsConfig() default-instances remain semantically equivalent — null is the
    "feature off" sentinel; 0 would ambiguously mean "every tile is solid"
  - jumpHoldMaxFrames uses Int = 0 (0 = disabled) — the abstract variableHeightJump Boolean keeps owning
    the abstract-path semantics; the new field opts into the frame-counted hold window when > 0
  - Extracted a public buildConfig(): PlatformerPhysicsConfig helper alongside build() so JVM tests can
    assert against the raw config without unwrapping GenericSystem.config["physicsConfig"]; build() now
    delegates to buildConfig() (the existing GenericSystem contract is preserved bit-for-bit)
  - All 8 pre-existing fields (gravity, jumpForce, terminalVelocity, coyoteFrames, jumpBufferFrames,
    airControlFactor, variableHeightJump, wallJump) and their defaults are unchanged — Test #4 locks
    each default so any drift is caught at JVM tier
  - No top-level platformerPhysics extension function modified (that wiring is owned by Plan 12-07)
  - No codegen wired (Plan 12-08 / 12-11 / 12-13 do the lowering)
metrics:
  duration: 4 minutes
  completed_date: 2026-05-21
---

# Phase 12 Plan 05: Add solidThreshold + jumpHold to PlatformerPhysicsBuilder Summary

Pure-DSL plan: extends `PlatformerPhysicsConfig` and `PlatformerPhysicsBuilder` with the two new
fields (`solidThreshold: Int?` for D-12 / tilemap collision, `jumpHoldMaxFrames: Int` for D-14 /
variable-height jump-hold) that the Wave 2 codegen plans need to read. No codegen branch
touched — Plans 12-08 / 12-11 / 12-13 lower these fields downstream.

## What shipped

Three files, zero codegen, zero generated C:

1. **`PlatformerTypes.kt`** — `PlatformerPhysicsConfig` data class gains two additive fields:
   - `val solidThreshold: Int? = null` (null = no tilemap-collision codegen path)
   - `val jumpHoldMaxFrames: Int = 0` (0 = frame-counted jump-hold disabled)
   - KDoc on each field cites the downstream plan that lowers it (12-08/11 and 12-13).

2. **`PlatformerBuilders.kt`** — `PlatformerPhysicsBuilder` gains:
   - Two private backing fields mirroring the existing pattern.
   - Two public DSL methods: `solidThreshold(value: Int)` and `jumpHold(maxFrames: Int)`.
   - A new public `buildConfig(): PlatformerPhysicsConfig` helper that returns the inner
     config without the `GenericSystem` wrapper. `build()` now delegates to `buildConfig()`,
     so the existing `GenericSystem` contract (config keys `"type"` + `"physicsConfig"`)
     is bit-for-bit unchanged.

3. **`PlatformerPhysicsBuilderTest.kt` (new)** — 4 `@Test` methods:
   - `default PlatformerPhysicsConfig has solidThreshold null and jumpHoldMaxFrames zero`
   - `solidThreshold round-trips through buildConfig`
   - `jumpHold round-trips through buildConfig`
   - `solidThreshold and jumpHold coexist without disturbing existing field defaults` — locks
     all 8 pre-existing field defaults.

## Tasks executed

| Task | Name                                                                                    | Commit     | Files                                                                                                                                                                                                          |
| ---- | --------------------------------------------------------------------------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | Add solidThreshold + jumpHoldMaxFrames fields to PlatformerPhysicsConfig                | `51419cf4` | `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/domain/PlatformerTypes.kt`, `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerPhysicsBuilderTest.kt` |
| 2    | Add solidThreshold(value) and jumpHold(maxFrames) builder methods                       | `d4b3a23e` | `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerBuilders.kt`, `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerPhysicsBuilderTest.kt` |
| 3    | Unit test — both fields round-trip + existing defaults locked                           | `2b2198da` | `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerPhysicsBuilderTest.kt`                                                                                                    |

Each task followed the TDD RED→GREEN flow: a test was added that failed to compile (because the
referenced field or method did not yet exist), the production code was added to make it compile
and pass, then the next task layered on top.

## Verification

- `./gradlew :gbkt-genre-platformer:test --tests "PlatformerPhysicsBuilderTest" --quiet` → 0 (4 tests pass)
- `./gradlew :gbkt-genre-platformer:test --quiet` → 0 (full module suite stays GREEN, no regressions)
- Plan-level grep gates:
  - `grep -c 'solidThreshold' PlatformerTypes.kt` → **2** (KDoc + field decl) ≥ 1 ✓
  - `grep -c 'jumpHoldMaxFrames' PlatformerTypes.kt` → **2** (KDoc + field decl) ≥ 1 ✓
  - `grep -cE 'fun solidThreshold|fun jumpHold' PlatformerBuilders.kt` → **2** ≥ 2 ✓
- `grep -c '@Test' PlatformerPhysicsBuilderTest.kt` → **4** (matches plan Task 3 acceptance)

## Deviations from Plan

### Minor — naming clarification

**1. `buildConfig()` helper extracted from `build()` for testability.**
- **Found during:** Task 2 reading.
- **Issue:** The plan's behavior block + `must_haves.key_links` reference `buildConfig()` as the
  round-trip method. The actual builder only had `build(): GenericSystem` which wraps the config
  in a `GenericSystem`. Calling `build()` in tests would force the assertion to do
  `(system.config["physicsConfig"] as PlatformerPhysicsConfig).solidThreshold`, leaking the
  GenericSystem wrapper into every test.
- **Fix:** Extracted the inner `PlatformerPhysicsConfig` construction into a public
  `buildConfig(): PlatformerPhysicsConfig` helper. `build()` now calls `buildConfig()` and
  wraps the result, so the existing `GenericSystem` contract is byte-for-byte unchanged. This
  matches the plan's `must_haves.key_links.via: "buildConfig() copy"` literally — the field
  copies happen inside `buildConfig()`.
- **Rule:** Rule 2 (auto-add missing critical functionality — the test plan presumes
  `buildConfig()` exists; adding it is required to honor the plan's own contract).
- **Commit:** `d4b3a23e`.

No other deviations. The two new fields are pure additive with safe sentinel defaults; all
existing call sites compile and pass unchanged.

## Known Stubs

None. Both fields are deliberately "feature off" by default (null / 0) and remain so until the
downstream codegen plans (12-08, 12-11, 12-13) wire them — that staging is documented in the
field KDoc and is per the plan's stated "no codegen wiring yet" scope.

## Threat Flags

None. Pure-Kotlin data-class field additions and pure DSL builder method additions. No I/O,
no network, no auth path, no codegen output yet, no schema or trust-boundary change.

## Self-Check: PASSED

- File `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/domain/PlatformerTypes.kt`: FOUND
- File `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerBuilders.kt`: FOUND
- File `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerPhysicsBuilderTest.kt`: FOUND
- Commit `51419cf4`: FOUND
- Commit `d4b3a23e`: FOUND
- Commit `2b2198da`: FOUND
