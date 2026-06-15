---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
plan: "01"
subsystem: gbkt-genre-platformer
tags: [platformer, codegen, dsl, refactor, seed-closure, fix]
dependency_graph:
  requires: []
  provides: [pivotAdjust-dsl-setter, snap-arithmetic-emission-test]
  affects: [gbkt-genre-platformer, gbkt-examples/platformer-template]
tech_stack:
  added: []
  patterns:
    - nullable-field-let-builder-pattern (TilemapCollisionBuilder.pivotAdjust)
    - config-driven-visitor-read (PlatformerVisitor pivot resolution)
    - inlined-brace-walk-extraction (PlatformerSnapArithmeticEmissionTest)
key_files:
  created:
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerSnapArithmeticEmissionTest.kt
  modified:
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt
    - gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt
decisions:
  - "Kept REFERENCE_FRAME_HEIGHT/REFERENCE_PIVOT_Y companion constants as documented fallback per RESEARCH open question 1"
  - "Used structural assertion (body.contains(1888)) for snap-arithmetic test per D-07 option (b)"
  - "Fallback diagnostic uses System.err.println per D-05 requirement"
metrics:
  duration: "3 min"
  completed: "2026-06-14"
  tasks: 2
  files: 4
---

# Phase 21 Plan 01: pivotAdjust DSL Lift + Snap-Arithmetic Emission Test Summary

**One-liner:** Lifted pivot_adjust resolution from a metasprite-lookup dance in PlatformerVisitor into a typed `pivotAdjust(Int)` DSL setter on TilemapCollisionBuilder, with config-driven visitor read + fallback diagnostic, closing SEED-021 per Project Rule #1.

## What Was Built

### Task 1: pivotAdjust(Int) setter in TilemapCollisionBuilder + platformer-template wiring

Added `private var pivotAdjust: Int? = null` field and `fun pivotAdjust(v: Int)` setter to `TilemapCollisionBuilder` in `PlatformerExtensions.kt`. The field is nullable so absence means "not set" — the `build()` method uses `pivotAdjust?.let { configBuilder["pivotAdjust"] = it }` (matching the existing `posXVar?.let { }` pattern). KDoc cites SEED-021 and Project Rule #1.

Updated `PlatformerTemplate.kt` to add `pivotAdjust(2)` to its `tilemapCollision { }` block, with a comment citing the derivation: `frameSize(24,32) + pivot(12,6) + hitboxH=24 → 32-6-24=2`.

### Task 2: Config-driven visitor read + snap-arithmetic emission test

Replaced the metasprite-lookup dance in `PlatformerVisitor.buildTilemapPhysicsUpdateFunction` (lines 626-651) with:

```kotlin
val pivotAdjust: Int =
    (tcSystem?.config?.get("pivotAdjust") as? Int)
        ?: run {
            System.err.println("WARNING: tilemapCollision bound but no pivotAdjust declared; ...")
            (REFERENCE_FRAME_HEIGHT - REFERENCE_PIVOT_Y - height).coerceAtLeast(0)
        }
```

Removed both SEED-021 deferred markers (call-site comment ~626-628 and buildVerticalFootProbe KDoc ~1294-1296). Kept companion constants `REFERENCE_FRAME_HEIGHT = 32` and `REFERENCE_PIVOT_Y = 6` as the documented fallback.

Created `PlatformerSnapArithmeticEmissionTest.kt` with two tests:
- Test 1 (config-driven): pivotAdjust=2/hitboxH=24 → posYSym literal `1888` in generated physics body
- Test 2 (fallback): no pivotAdjust key generates cleanly, companion constants produce same result

## Verification

- `grep -n "fun pivotAdjust"` returns line 671 in PlatformerExtensions.kt
- `grep -n "pivotAdjust(2)"` returns line 184 in PlatformerTemplate.kt
- `grep -c "Deferred (SEED-021)"` returns 0 in PlatformerVisitor.kt
- `:gbkt-genre-platformer:test` GREEN including both new PlatformerSnapArithmeticEmissionTest methods
- All 4 pre-existing emission tests (JumpHold, HorizontalScroll, TilemapCollision, PlatformerCodegen) GREEN
- `spotlessApply` + `detekt` clean

## Commits

| Task | Commit | Files | Description |
|------|--------|-------|-------------|
| 1 | 85c9c524 | 2 | feat(21-01): add pivotAdjust(Int) setter to TilemapCollisionBuilder + wire platformer-template |
| 2 | 009f4ec3 | 2 | feat(21-01): replace visitor metasprite lookup dance with config-driven pivotAdjust + add snap-arithmetic emission test |

## Deviations from Plan

None - plan executed exactly as written. The PATTERNS.md replacement shape was followed precisely.

## Threat Flags

None — offline build-time codegen only; no new runtime input surface, no network, no auth.

## Known Stubs

None — the pivotAdjust value is fully wired end-to-end from DSL setter to config map to visitor read to generated C literal.

## Self-Check: PASSED

- [x] `PlatformerExtensions.kt` modified (line 671: `fun pivotAdjust(v: Int)`)
- [x] `PlatformerVisitor.kt` modified (metasprite lookup dance removed; config read at line 615)
- [x] `PlatformerTemplate.kt` modified (`pivotAdjust(2)` at line 184)
- [x] `PlatformerSnapArithmeticEmissionTest.kt` created (2 tests, both GREEN)
- [x] Commits 85c9c524 and 009f4ec3 verified in git log
- [x] SEED-021 deferred markers count = 0
- [x] posYSym literal 1888 asserted in emission test
