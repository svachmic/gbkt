---
phase: 04-analysis-pass-pipeline
plan: 05
subsystem: analysis
tags: [kotlin, analysis-pass, oam, ram, wram, hardware-resources, tdd, game-boy]

# Dependency graph
requires:
  - phase: 04-analysis-pass-pipeline
    plan: 01
    provides: PassPipeline, PassContext, AnalysisConfig, ResourceInventory data classes
  - phase: 04-analysis-pass-pipeline
    plan: 02
    provides: ResourceInventoryPass (populates variableBytes, spriteTileCounts, collectionBytes)
  - phase: 04-analysis-pass-pipeline
    plan: 03
    provides: BankingAnalysisPass (completes ROM bank allocation)
  - phase: 04-analysis-pass-pipeline
    plan: 04
    provides: VRAMLayoutPass (completes VRAM tile allocation)
provides:
  - OAMAllocationPass: sequential OAM slot assignment per sprite-bearing actor with scanline advisory
  - RAMPlanningPass: WRAM layout computation from variables + actors + collections + overhead
  - RAMLayout populated on PassContext with wramUsed, hramUsed, sramUsed fields
  - Five hardware resource domains now have allocation passes (banks, VRAM, OAM, WRAM, SRAM)
affects:
  - 04-06-plan (DeadCodeElimination/ConstantFolding runs in same pipeline after these passes)
  - 04-07-plan (PassPipeline integration test exercises all 5 resource passes)
  - gbkt-backend-gbdk (consumes PassContext.oamAssignments and ramLayout for code generation)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "OAM advisory-only scanline warnings: density > maxPerScanline is WARNING not ERROR"
    - "Actor state overhead constant: 5 bytes/actor (x:1, y:1, visible:1, type:1, reserved:1)"
    - "Engine overhead constant: 10 bytes (scene mgmt ~4 + camera ~6)"
    - "RAMPlanning fallback: computes variableBytes directly from game.variables when inventory null"

key-files:
  created:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/OAMAllocationPass.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/RAMPlanningPass.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/OAMAllocationPassTest.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/RAMPlanningPassTest.kt
  modified: []

key-decisions:
  - "OAMAllocationPass uses profile.sprites.maxSprites for hard overflow check (hardware ceiling), config.oamWarningThreshold for advisory warning (configurable developer preference)"
  - "Scanline density advisory is WARNING not ERROR per research pitfall 3: flickering only occurs when all actors cluster on same scanline at runtime — static count alone cannot determine this"
  - "RAMPlanningPass reads variableBytes from inventory when available (authoritative), falls back to recomputing from game.variables — supports both pipelines (with and without ResourceInventoryPass)"
  - "HRAM and SRAM set to 0 as explicit future extension points — no DSL syntax targets HRAM, no v2 IR save system yet"
  - "Actor state overhead is 5 bytes/actor constant (x, y, visible, type, reserved) — conservative estimate for OAM-managed sprite state"
  - "Engine overhead is 10 bytes constant (scene management 4 + camera state 6) — small fixed cost for runtime subsystems"

patterns-established:
  - "Advisory-vs-hard distinction: hardware absolute limits are errors, developer-configured thresholds are warnings"
  - "Fallback computation pattern: prefer inventory data (pre-computed), fall back to direct IR traversal"

requirements-completed: [ANLZ-04, ANLZ-05]

# Metrics
duration: 14min
completed: 2026-02-18
---

# Phase 4 Plan 05: OAMAllocationPass and RAMPlanningPass Summary

**OAM slot assignment (sequential, per sprite-bearing actor) and WRAM layout computation (variables + actors + collections + 10-byte engine overhead) completing all five hardware resource domains**

## Performance

- **Duration:** 14 min
- **Started:** 2026-02-18T20:55:25Z
- **Completed:** 2026-02-18T21:09:25Z
- **Tasks:** 2
- **Files modified:** 4 files created

## Accomplishments

- `OAMAllocationPass` assigns sequential OAM slot indices to all sprite-bearing actors; actors without sprites are skipped entirely
- Scanline density advisory (WARNING, not error) emitted per scene when active sprite count exceeds `maxPerScanline` — correctly advisory-only per research finding that hardware flickering requires runtime clustering, not just static count
- `RAMPlanningPass` computes WRAM from four components: variable bytes (from inventory or direct computation), actor state (5 bytes/actor), collection bytes, and constant 10-byte engine overhead
- WRAM overflow produces a hard error with a detailed breakdown (variables/actors/collections/overhead bytes); near-threshold produces a warning; all five hardware resource domains now covered

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement OAMAllocationPass with TDD** - `b1cbbea` (feat)
2. **Task 2: Implement RAMPlanningPass with TDD** - `040dd0a` (feat)

**Plan metadata:** (docs commit below)

_Note: TDD tasks each followed RED→GREEN cycle_

## Files Created/Modified

- `/gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/OAMAllocationPass.kt` - Sequential OAM slot assignment with overflow check and scanline density advisory
- `/gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/RAMPlanningPass.kt` - WRAM/HRAM/SRAM computation from IR sources
- `/gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/OAMAllocationPassTest.kt` - 6 tests: slot assignment, no-sprite skip, overflow error, warning threshold, scanline advisory, empty game
- `/gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/RAMPlanningPassTest.kt` - 8 tests: variable bytes, actor state, collections, within budget, overflow, near-threshold, layout populated, empty game

## Decisions Made

- Scanline density warnings are advisory (WARNING) not errors — per the plan's "truths" section and hardware reality: 12 actors in a scene could all be at different Y positions and never cause flickering
- `OAMAllocationPass` checks `profile.sprites.maxSprites` for the hard limit (40 on GB/GBC) and `config.oamErrorThreshold` is unused — the profile's hardware limit is authoritative; the config threshold configures the advisory warning level
- `RAMPlanningPass` falls back to direct `game.variables` computation when `inventory` is null — makes the pass safe to run standalone without `ResourceInventoryPass`
- HRAM and SRAM fields are set to 0 with explicit documentation as future extension points — no DSL constructs target them in v2 IR

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- Parallel plan 04-06 agent had already written `DeadCodeEliminationPassTest.kt` with a reference to `DeadCodeEliminationPass`, but the 04-06 agent had also already fully implemented `DeadCodeEliminationPass.kt` by the time this plan ran — no blocking compilation issue actually occurred
- Memory pressure from parallel Kotlin daemon caused one `./gradlew :gbkt-analysis:test` invocation to fail with OOM; resolved by using `--no-daemon` flag

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All 5 hardware resource allocation passes implemented (banks, VRAM, OAM, WRAM, SRAM)
- PassContext fully populated after pipeline run: bankAssignments, vramAssignments, oamAssignments, ramLayout
- 79 total analysis tests passing (including 14 new from this plan)
- Ready for plan 04-07 (PassPipeline integration test) and 04-08 (end-to-end verification)

---
*Phase: 04-analysis-pass-pipeline*
*Completed: 2026-02-18*

## Self-Check: PASSED

- OAMAllocationPass.kt: FOUND
- RAMPlanningPass.kt: FOUND
- OAMAllocationPassTest.kt: FOUND
- RAMPlanningPassTest.kt: FOUND
- Commit b1cbbea (OAMAllocationPass): FOUND
- Commit 040dd0a (RAMPlanningPass): FOUND
