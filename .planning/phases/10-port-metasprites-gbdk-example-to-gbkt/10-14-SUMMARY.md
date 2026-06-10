---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 14
subsystem: codegen/pipeline
tags: [buildRom, first-blocker, metasprite, gbdk-pipeline, d-05]
dependency_graph:
  requires: [10-13]
  provides: [evidence/first-build-log.txt, evidence/first-blocker-analysis.md]
  affects: [10-15]
tech_stack:
  added: []
  patterns: [D-05-exploratory-build, blocker-analysis]
key_files:
  created:
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/first-build-log.txt
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/first-blocker-analysis.md
  modified: []
decisions:
  - "First blocker is a pipeline wiring gap: MetaspriteVisitor.generateMetaspriteDescriptor() never called from GBDKPipelineV2 — Plan 15 fix is 1-5 lines in buildHomeFile()"
  - "Blocker is NOT in RESEARCH §12 catalog (novel) — not an OAM-tail, palette-ordering, or signed-comparison issue"
  - "Three surplus defects deferred to seeds: missing set_sprite_data() call, all-slot-0 palette loads (D-13), missing sprite PNG wiring (D-13)"
metrics:
  duration: "~15 min"
  completed: "2026-05-18"
  tasks_completed: 2
  tasks_total: 3
  files_created: 2
  files_modified: 0
---

# Phase 10 Plan 14: First ROM Build Smoke Test Summary

## One-liner

First `buildRom` run for metasprites example fails FAIL-COMPILE: `MetaspriteVisitor.generateMetaspriteDescriptor()` is implemented but never called from `GBDKPipelineV2`, so `sprite_metasprite_N[]` and `sprite_metasprites[]` arrays are absent from generated C.

## Tasks Completed

| Task | Name | Commit | Outcome |
|------|------|--------|---------|
| 1 | Run buildRom + capture log | 7446ff42 | FAIL-COMPILE — 8 lcc errors (4 unique) captured |
| 2 | Write first-blocker-analysis.md | 4d1443ea | Named blocker: pipeline wiring gap in GBDKPipelineV2 |

## Checkpoint Reached

Task 3 is `type="checkpoint:human-verify" gate="blocking"` — stopped per plan. Awaiting user sign-off on the named blocker before Plan 15 proceeds.

## Build Outcome

**FAIL-COMPILE**

```
main.c:267: error 20: Undefined identifier 'sprite_metasprites'
main.c:267: error 22: Array or pointer required for '[]' operation
(× 4 call sites = 8 error lines total)
```

No lcc warnings. The sole root cause is a single missing pipeline call-site.

## Named Blocker (for Plan 15)

**`GBDKPipelineV2` never calls `MetaspriteVisitor.generateMetaspriteDescriptor()`.**

- The visitor method is fully implemented (Plan 10-06, `MetaspriteVisitor.kt` lines 109-132).
- The pipeline correctly emits `_elephant_flipX/Y/subPalette` runtime vars and the
  `#include <gbdk/metasprites.h>` guard.
- But the actual C data arrays (`sprite_metasprite_0[]` .. `sprite_metasprite_4[]` and
  `sprite_metasprites[]`) are never emitted.
- `ScriptOpVisitor.visitMoveMetasprite()` references `sprite_metasprites[_idx]` — so lcc
  errors on the undefined symbol.

**Plan 15 fix scope:** 1-5 lines in `GBDKPipelineV2.buildHomeFile()` to call
`MetaspriteVisitor.generateMetaspriteDescriptor(ms)` for each metasprite in
`gameIR.metasprites` and include the returned `CRawCode` in the globals section.

## Surplus Defects (D-06 seeds, NOT Plan 15 scope)

1. `set_sprite_data()` call for VRAM tile loading also missing from `play_enter()` —
   `MetaspriteVisitor.generateMetaspriteTileData()` exists but not called from pipeline.
2. All four `set_sprite_palette()` calls use slot 0 (known PHASE-13 gap, deferred to Plan 18).
3. `ConvertSpritesTask: No sprite includes found in main.c` — elephant PNG not wired into
   asset pipeline (PHASE-13 gap 1, deferred to Plan 18).

## Deviations from Plan

None — plan executed as designed. D-05 is exploratory by definition; the build log and
blocker analysis are the deliverables. No code was changed.

## Self-Check: PASSED

- `evidence/first-build-log.txt` exists, non-empty, contains "BUILD FAILED" and lcc errors
- `evidence/first-blocker-analysis.md` exists, contains "First blocker name" section
- Both files committed (7446ff42, 4d1443ea)
- No STATE.md or ROADMAP.md modifications
