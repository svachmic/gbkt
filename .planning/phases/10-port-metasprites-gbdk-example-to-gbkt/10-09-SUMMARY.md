---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 09
subsystem: codegen
tags: [metasprite, subpalette, gbdk, pipeline, tdd]

# Dependency graph
requires:
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    plan: 08
    provides: "MetaspriteRef.flipX / .flipY global UINT8 declaration pattern in GBDKPipelineV2"

provides:
  - "MetaspriteRef.subPalette set Expr lowers to _<id>_subPalette = <expr>u; in scene frame body"
  - "UINT8 _<id>_subPalette global declaration emitted in main.c for every MetaspriteIR"
  - "SubPaletteAccessorEmissionTest.kt (4 tests, all GREEN) validating unit + pipeline emission"

affects:
  - 10-13  # port assembly uses subPalette DSL surface for palette cycling behavior

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-metasprite runtime attribute vars declared in GBDKPipelineV2.buildHomeFile() alongside flipX/flipY"
    - "Assignment lowering reuses existing ExprVisitor.sanitizeVarName dot-to-underscore conversion"
    - "Brace-walk extractFunctionBody() used in SubPaletteAccessorEmissionTest to scope play_frame assertions"

key-files:
  created:
    - "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SubPaletteAccessorEmissionTest.kt"
  modified:
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt"

key-decisions:
  - "Assignment lowering already worked via sanitizeVarName ('elephant.subPalette' → '_elephant_subPalette'); only the global UINT8 declaration was missing — identical to Plan 08 pattern"
  - "No conditional codegen for DMG vs GBC (D-08): set_sprite_prop writes unconditionally, DMG hardware ignores CGB palette bits"
  - "_<id>_subPalette declared unconditionally for every MetaspriteIR alongside flipX/flipY"

requirements-completed: []

# Metrics
duration: ~2min
completed: 2026-05-18
---

# Phase 10 Plan 09: SubPalette Accessor Emission Summary

**MetaspriteRef.subPalette DSL accessor lowers to per-metasprite UINT8 global var (_<id>_subPalette) declared in main.c and assigned in scene frame functions — identical pattern to Plan 08 flip accessors**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-05-18T16:16:47Z
- **Completed:** 2026-05-18T16:18:00Z
- **Tasks:** 1 (TDD: RED → GREEN)
- **Files modified:** 2

## Accomplishments

- Discovered (as predicted by Plan 08 SUMMARY) that assignment lowering already worked via `ExprVisitor.sanitizeVarName` (dot → underscore), so no changes to `ScriptOpVisitor.visitAssign` were needed
- Extended `metaspriteRuntimeVars` in `GBDKPipelineV2.buildHomeFile()` to emit `UINT8 _<id>_subPalette = 0u` for every `MetaspriteIR`, alongside the existing `_<id>_flipX` and `_<id>_flipY` vars
- Created `SubPaletteAccessorEmissionTest.kt` with 4 tests covering unit-level lowering, end-to-end pipeline emission, no-conditional-codegen guard, and global declaration presence

## Task Commits

TDD RED + GREEN commits for the single plan task:

1. **TDD RED: SubPaletteAccessorEmissionTest.kt** — `b49a2f0d` (test)
2. **TDD GREEN: GBDKPipelineV2 _subPalette declaration** — `0a5088b9` (feat)

## Files Created/Modified

- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SubPaletteAccessorEmissionTest.kt` — 4 tests: 2 unit (visitAssign), 1 pipeline play_frame body with no-conditional guard, 1 global declaration presence
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — added `_${ms.id}_subPalette` to `metaspriteRuntimeVars` block; updated comment to include subPalette + D-08 rationale

## Decisions Made

- Assignment lowering reused `sanitizeVarName` (already converts `id.property` → `_id_property`) rather than adding special-case detection in `visitAssign`. No changes to `ScriptOpVisitor` needed.
- No conditional codegen for DMG vs GBC: per RESEARCH §4 (D-08), `set_sprite_prop()` writes the OAM attribute byte unconditionally and DMG hardware ignores CGB palette bits (bits 0-2). The emission is a plain `UINT8` write with no `#if` guard.
- `_<id>_subPalette` is always declared (not conditionally based on script usage) — consistent with the flipX/flipY pattern and actor `_x` / `_y` vars.

## Deviations from Plan

### Minor Scope Reduction (Rule 1 — not a bug, pre-existing behavior)

**ScriptOpVisitor.visitAssign had no changes needed**
- **Found during:** Task 1 investigation (mirroring Plan 08 discovery)
- **Issue:** Plan proposed extending "the metasprite-property detection branch" in `visitAssign` — but `ExprVisitor.sanitizeVarName` already converts any `objectId.property` to `_objectId_property` unconditionally. No special metasprite detection was needed.
- **Fix:** Skipped the `visitAssign` changes; implemented only the global declaration in the pipeline.
- **Impact:** Cleaner implementation, same outcome.

## TDD Gate Compliance

- RED gate: `b49a2f0d` — test commit with 1 failing test (Test 4: global declaration missing; Tests 1-3 already passed)
- GREEN gate: `0a5088b9` — feat commit making all 4 tests pass

## Issues Encountered

None — plan executed cleanly. Pattern established by Plan 08 applied directly.

## Next Phase Readiness

- All three OAM-attr accessors (flipX, flipY, subPalette) are now functional and covered by tests
- Plan 13 (port assembly) can use `metaspriteRef.subPalette set (rot shr 2)` in DSL scenes; the generated C is valid SDCC
- Plan 12 (Tier-1 emission invariants) can reference SubPaletteAccessorEmissionTest as evidence of D-07+D-08 delivery

---
*Phase: 10-port-metasprites-gbdk-example-to-gbkt*
*Completed: 2026-05-18*
