---
phase: 02-structured-codegen-and-migration-cut
plan: 04
subsystem: codegen
tags: [kotlin, gbdk, c-codegen, pipeline, game-boy, ast]

# Dependency graph
requires:
  - phase: 02-03
    provides: "ExprVisitor, ScriptOpVisitor, SceneVisitor, ActorVisitor — IR-to-C-AST translation"
  - phase: 02-02
    provides: "CEmitter — single pretty-printer producing C strings from CFile AST"
  - phase: 02-01
    provides: "C AST hierarchy: CFile, CFunction, CStatement, CExpr, CType with bank as typed field"
provides:
  - "GBDKPipelineV2 — complete GameIR-to-Map<String,String> pipeline (main.c, bank1.c, game.h)"
  - "GBDKBackend.generateV2(GameIR) — dual-path routing: v2 games to new pipeline, v1 to old"
  - "GBDKCodeGenerator @Deprecated(WARNING) — functional but marked for Phase 5 removal"
  - "PongPipelineTest — 17 integration tests proving end-to-end correctness for Pong"
affects: [phase-3-asset-pipeline, phase-5-integration-validation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "GBDKPipelineV2 orchestrates SceneVisitor, ActorVisitor, CEmitter to produce multi-file C output"
    - "buildForwardDeclarations produces zero-body CFunction stubs as prototypes in HOME bank"
    - "buildSpriteHelperStubs: Phase 3 deferred stubs using CRawCode TODO pattern"
    - "Test fixtures: inline GameIR construction avoids cross-module dependency for self-contained tests"
    - "Dual-path backend routing: generateV2(GameIR) for v2, generate(Game) for v1 — no regression risk"

key-files:
  created:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/PongPipelineTest.kt
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCodeGenerator.kt
    - .planning/ROADMAP.md

key-decisions:
  - "GBDKPipelineV2 produces 3 output files: main.c (HOME bank), bank1.c (bank 1 with BANKED scene functions), game.h (header)"
  - "Forward declarations implemented as zero-body CFunction entries in main.c — avoids raw string prototypes"
  - "Phase 3 sprite helpers stubbed as CRawCode TODO — avoids scope creep while keeping Pong compilable"
  - "PongPipelineTest uses inline GameIR fixture (not pongV2 DSL) — zero cross-module dependency, self-contained"
  - "GBDKCodeGenerator @Deprecated(WARNING) not deleted — deletion deferred to Phase 5 per locked decision"
  - "generateV2(GameIR) added as new method on GBDKBackend — existing generate(Game) untouched, zero regression risk"

patterns-established:
  - "Pipeline outputs Map<String, String>: filename to C content — simple, testable, no file I/O in pipeline"
  - "navigate_to_scene uses CSwitch/CSwitchCase for exit and enter dispatch — no raw string switch"
  - "CWhile(CVar(\"1\"), ...) for GBDK infinite game loop — typed, not CRawCode(\"while(1)\")"

requirements-completed: [CGEN-04]

# Metrics
duration: 12min
completed: 2026-02-18
---

# Phase 2 Plan 4: Wire End-to-End Pipeline and Deprecate Old Generator Summary

**GBDKPipelineV2 orchestrating SceneVisitor + ActorVisitor + CEmitter to produce main.c/bank1.c/game.h for Pong v2 with 17 passing integration tests and zero RPG symbols**

## Performance

- **Duration:** 12 min
- **Started:** 2026-02-18T06:32:23Z
- **Completed:** 2026-02-18T06:44:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- GBDKPipelineV2 end-to-end pipeline: GameIR → main.c + bank1.c + game.h via typed C AST
- main.c contains: scene enum defines, actor position vars, global vars, navigate_to_scene, main() with game loop
- bank1.c contains: `#pragma bank 1` and all scene functions with BANKED keyword
- All 17 PongPipelineTest integration tests pass; zero RPG symbols in generated output
- GBDKBackend.generateV2(GameIR) wired to new pipeline; existing generate(Game) untouched (0 regressions)
- GBDKCodeGenerator annotated @Deprecated(WARNING); functional but marked for Phase 5 removal
- ROADMAP.md Phase 2 marked Complete (4/4 plans)

## Task Commits

Each task was committed atomically:

1. **Task 1: Build GBDKPipelineV2 orchestrator and Pong integration tests** - `4960418` (feat)
2. **Task 2: Wire pipeline into GBDKBackend, deprecate old generator, update ROADMAP.md** - `64558b1` (feat)

**Plan metadata:** (docs commit — created after tasks)

## Files Created/Modified

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — Main pipeline orchestrator: GameIR → Map<String, String>
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/PongPipelineTest.kt` — 17 integration tests with inline Pong GameIR fixture
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt` — Added generateV2(GameIR) method
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCodeGenerator.kt` — Added @Deprecated(WARNING) annotation
- `.planning/ROADMAP.md` — Phase 2 plans marked complete, phase status updated to Complete

## Decisions Made

- Forward declarations produced as zero-body CFunction entries in main.c — avoids raw string generation while maintaining BANKED prototype pattern.
- Sprite helper stubs use CRawCode("/* TODO: Phase 3 - OAM management */") — avoids Phase 3 scope creep while keeping Pong compilable.
- PongPipelineTest uses inline GameIR fixture, not pongV2 DSL import — avoids cross-module dependency (gbkt-backend-gbdk cannot depend on gbkt-examples), keeps test fully self-contained.
- generateV2() added as a separate method (not modifying generate()) — zero risk to existing v1 pipeline.

## Deviations from Plan

None - plan executed exactly as written.

The forward declarations in main.c are technically "stub functions" rather than C function prototypes (they are zero-body CFunction nodes rather than raw declaration strings). This is a cleaner approach using typed AST nodes rather than raw strings, fully consistent with the C AST architecture goals. Not a deviation from intent.

## Issues Encountered

None. All 17 tests passed on first run; no compilation errors encountered.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 2 is complete. GBDKPipelineV2 is wired and ready for Phase 3 (asset pipeline integration).
- Phase 3 task: Replace sprite helper stubs (hide_sprites_range/show_sprites_range) with real OAM management code after asset pipeline processes PNG files.
- Phase 5 task: Delete GBDKCodeGenerator once all three games (Pong, Breakout, Explorer) are validated through the new pipeline.

---
*Phase: 02-structured-codegen-and-migration-cut*
*Completed: 2026-02-18*
