---
phase: 06-complete-gap-closure
plan: 11
subsystem: dsl
tags: [kotlin, dsl, collision, scene, ir, tile-collision]

# Dependency graph
requires:
  - phase: 06-complete-gap-closure
    provides: GBDKPipelineV2 tile collision codegen (_map_collision() arrays and functions)
provides:
  - SceneBuilder.collisionData(data, mapWidth) DSL method with input validation
  - End-to-end DSL → SceneIR → GBDKPipelineV2 collision wiring path
affects:
  - 06.1-v1-feature-parity-port (can use collision DSL for scene definitions)
  - Future TMX asset pipeline integration (TiledParser → collisionData())

# Tech tracking
tech-stack:
  added: []
  patterns:
    - SceneBuilder field + validation method + build() wiring (same as tileset() pattern)
    - collisionBytes/collisionMapWidth as nullable private fields defaulting to null

key-files:
  created:
    - gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/SceneBuilderCollisionTest.kt
  modified:
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt

key-decisions:
  - "collisionData() validates mapWidth > 0, data non-empty, data.size % mapWidth == 0 — fail-fast at DSL layer prevents malformed SceneIR reaching codegen"
  - "Full TMX pipeline automation (TiledParser -> SceneIR) deferred to Phase 06.1 — manual ByteArray is sufficient for now as the codegen side was already complete"

patterns-established:
  - "DSL method pattern: private var field + require() validation + pass through in build()"

requirements-completed: [COLL-01]

# Metrics
duration: 2min
completed: 2026-02-21
---

# Phase 06 Plan 11: SceneBuilder Collision DSL Wiring Summary

**collisionData() DSL method added to SceneBuilder closing the DSL-to-SceneIR gap for tile collision, completing the end-to-end path from game author code to GBDKPipelineV2 _map_collision() codegen**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-21T13:41:26Z
- **Completed:** 2026-02-21T13:43:39Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Added `collisionData(data: ByteArray, mapWidth: Int)` to SceneBuilder with three-condition input validation
- Wired collision fields through `build()` to `SceneIR.collisionData` and `SceneIR.mapWidth`
- Created 6 unit tests covering positive case, null case, three validation errors, and tileset integration

## Task Commits

Each task was committed atomically:

1. **Task 1: Add collisionData() to SceneBuilder and wire to SceneIR** - `f675da2` (feat)
2. **Task 2: Add collision data DSL wiring tests** - `f431249` (test)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified

- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt` - Added collisionBytes/collisionMapWidth fields, collisionData() method, wired through build()
- `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/SceneBuilderCollisionTest.kt` - 6 tests for DSL collision wiring

## Decisions Made

- Input validation at DSL layer (`require()` calls) prevents malformed SceneIR from reaching codegen — fail-fast is better than silent incorrect output
- Full automated TMX-to-ByteArray pipeline integration deferred to Phase 06.1 — TiledParser already extracts collision layers (`isCollisionLayer`), but wiring it to the build pipeline is Phase 06.1 scope; manual ByteArray covers all test scenarios now

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Gap 2 from Phase 06 verification is closed: DSL → SceneIR → GBDKPipelineV2 tile collision path is complete end-to-end
- Phase 06.1 (V1 Feature Parity Port) can now use `collisionData()` in scene definitions for dungeon/exploration games
- Full TMX pipeline automation (TiledParser → collisionData() call) remains as Phase 06.1 work

---
*Phase: 06-complete-gap-closure*
*Completed: 2026-02-21*
