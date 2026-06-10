---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 08
subsystem: codegen
tags: [metasprite, flip, gbdk, pipeline, tdd]

# Dependency graph
requires:
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    plan: 03
    provides: "MetaspriteRef.flipX / .flipY return ActorPropertyRef for DSL operator use"
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    plan: 04
    provides: "ScriptBuilder assignment wiring for ActorPropertyRef (moveMetasprite, DSL context)"

provides:
  - "MetaspriteRef.flipX set Expr / .flipY set Expr lower to _<id>_flipX = <val>u; in scene frame body"
  - "UINT8 _<id>_flipX and _<id>_flipY global declarations emitted in main.c for every MetaspriteIR"
  - "FlipAccessorEmissionTest.kt (6 tests, all GREEN) validating unit + pipeline emission"

affects:
  - 10-09  # subPalette accessor follows same pattern
  - 10-13  # port assembly uses flipX/flipY DSL surface
  - 10-12  # tier-1 emission invariants reference this delivery

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-metasprite runtime attribute vars declared in GBDKPipelineV2.buildHomeFile() alongside actorVars"
    - "Assignment lowering reuses existing ExprVisitor.sanitizeVarName dot-to-underscore conversion"
    - "Brace-walk extractFunctionBody() used in FlipAccessorEmissionTest to scope play_frame assertions"

key-files:
  created:
    - "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/FlipAccessorEmissionTest.kt"
  modified:
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt"

key-decisions:
  - "Assignment lowering already worked via sanitizeVarName ('elephant.flipX' → '_elephant_flipX'); only the global UINT8 declaration was missing"
  - "Declare _<id>_flipX and _<id>_flipY for EVERY MetaspriteIR unconditionally (not just when used in scripts) — matches how actor x/y vars are always declared"
  - "metaspriteRuntimeVars added after actorVars in allVariablesRaw; distinctBy ensures no duplicate if user manually declares same name"
  - "TDD RED: tests 1-4 (assignment emission) already passed; tests 5-6 (global declaration) failed → RED gate confirmed before implementing fix"

patterns-established:
  - "Flip-accessor DSL surface: metaspriteRef.flipX set Bool → Assign('id.flipX', Literal(0|1), SET) → _id_flipX = 0u|1u; via sanitizeVarName"
  - "Runtime attribute var lifecycle: declared in buildHomeFile() as UINT8 = 0, written by DSL assignments in scene frame ops"

requirements-completed: []

# Metrics
duration: 25min
completed: 2026-05-18
---

# Phase 10 Plan 08: Flip Accessor Emission Summary

**MetaspriteRef.flipX/flipY DSL accessors lower to per-flag UINT8 global vars (_<id>_flipX/_<id>_flipY) declared in main.c and assigned in scene frame functions**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-05-18T15:36:00Z
- **Completed:** 2026-05-18T16:01:11Z
- **Tasks:** 1 (TDD: RED → GREEN)
- **Files modified:** 2

## Accomplishments

- Discovered that assignment lowering already worked via `ExprVisitor.sanitizeVarName` (dot → underscore), so no changes to `ScriptOpVisitor.visitAssign` were needed
- Added `metaspriteRuntimeVars` generation in `GBDKPipelineV2.buildHomeFile()`: emits `UINT8 _<id>_flipX = 0u` and `UINT8 _<id>_flipY = 0u` for every `MetaspriteIR` in the game
- Created `FlipAccessorEmissionTest.kt` with 6 tests covering unit-level lowering and end-to-end pipeline emission

## Task Commits

TDD RED + GREEN commits for the single plan task:

1. **TDD RED: FlipAccessorEmissionTest.kt** - `18ae5a79` (test)
2. **TDD GREEN: GBDKPipelineV2 metaspriteRuntimeVars** - `8e59fb65` (feat)

## Files Created/Modified

- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/FlipAccessorEmissionTest.kt` — 6 tests: 2 unit (visitAssign), 2 pipeline play_frame body, 2 global declaration presence
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — added `metaspriteRuntimeVars` block + added to `allVariablesRaw`

## Decisions Made

- Assignment lowering reused `sanitizeVarName` (already converts `id.property` → `_id_property`) rather than adding special-case detection in `visitAssign`. Plan 08 scope was minimal.
- `_<id>_flipX` and `_<id>_flipY` are always declared (not conditionally based on script usage) — consistent with how actor `_x` and `_y` are always declared regardless of whether the script writes them.

## Deviations from Plan

### Minor Scope Reduction (Rule 1 — not a bug, pre-existing behavior)

**ScriptOpVisitor.visitAssign had no changes needed**
- **Found during:** Task 1 investigation
- **Issue:** Plan proposed "detect when target is a metasprite property and lower to `_${objectId}_${property}`" — but `ExprVisitor.sanitizeVarName` already converts any `objectId.property` to `_objectId_property` unconditionally. No special metasprite detection was needed.
- **Fix:** Skipped the `visitAssign` changes; implemented only the global declaration in the pipeline.
- **Impact:** Cleaner implementation, same outcome. The dot-to-underscore conversion is a general mechanism that works for actor properties, metasprite properties, and any future dot-notation assignments.

## TDD Gate Compliance

- RED gate: `18ae5a79` — test commit with 2 failing tests (Tests 5+6: global declaration missing)
- GREEN gate: `8e59fb65` — feat commit making all 6 tests pass

## Issues Encountered

None — plan executed cleanly after discovering the minimal scope of actual changes needed.

## Next Phase Readiness

- Plan 09 (subPalette accessor) can follow the identical pattern: `_<id>_subPalette` global UINT8 declaration + assignment lowering already works via sanitizeVarName
- Plan 13 (port assembly) can use `metaspriteRef.flipX set true/false` in DSL scenes; the generated C is valid for SDCC

---
*Phase: 10-port-metasprites-gbdk-example-to-gbkt*
*Completed: 2026-05-18*
