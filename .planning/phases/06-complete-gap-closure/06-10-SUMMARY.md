---
phase: 06-complete-gap-closure
plan: 10
subsystem: engine
tags: [gbkt-engine, gbkt-bom, scene, entity, input, graphics, types]

# Dependency graph
requires:
  - phase: 06-complete-gap-closure-plan-03
    provides: Module restructure establishing gbkt-ir, gbkt-lang, gbkt-engine layered hierarchy
provides:
  - gbkt-engine populated with real v2 Kotlin types in all 4 domain packages
  - gbkt-bom constraint covering gbkt-rpg genre package
affects:
  - consumers of gbkt-engine (game authors importing engine types)
  - gbkt-all meta-module re-export surface

# Tech tracking
tech-stack:
  added: []
  patterns:
    - v2 engine types as public API surface (scene, entity, input, graphics)
    - typealias for semantic type names (SceneId = String)
    - sealed-adjacent: enum for fixed alternatives (FadeType, Button, DpadDirection)

key-files:
  created:
    - gbkt-engine/src/main/kotlin/io/github/gbkt/core/scene/SceneTypes.kt
    - gbkt-engine/src/main/kotlin/io/github/gbkt/core/entity/EntityTypes.kt
    - gbkt-engine/src/main/kotlin/io/github/gbkt/core/input/InputTypes.kt
    - gbkt-engine/src/main/kotlin/io/github/gbkt/core/graphics/GraphicsTypes.kt
  modified:
    - gbkt-bom/build.gradle.kts

key-decisions:
  - "gbkt-engine type files replace package-info.kt placeholders entirely — same package declaration, richer content"
  - "FadeType.NONE as default for SceneTransitionRequest — no-op default avoids callers specifying fade on every navigate"
  - "InputState interface uses enum parameters (Button, DpadDirection) — type-safe vs string-based lookups"
  - "gbkt-rpg added to gbkt-bom constraints — enables version coordination; genre package inclusion is opt-in per game module"

patterns-established:
  - "Engine public API: interfaces + data classes in gbkt-engine packages; IR data in gbkt-ir; recording builders in gbkt-lang"

requirements-completed: [BOM-04]

# Metrics
duration: 5min
completed: 2026-02-21
---

# Phase 06 Plan 10: v2 Engine Types and BOM Update Summary

**gbkt-engine populated with 12 v2 Kotlin types across scene/entity/input/graphics packages, replacing empty package-info.kt placeholders; gbkt-rpg added to BOM constraints**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-21T13:41:24Z
- **Completed:** 2026-02-21T13:46:00Z
- **Tasks:** 1 of 1
- **Files modified:** 5

## Accomplishments

- Created SceneTypes.kt: `SceneId` typealias, `SceneLifecycle` interface, `FadeType` enum, `SceneTransitionRequest` data class
- Created EntityTypes.kt: `Positionable` interface, `Movable` interface, `Hitbox` data class, `EntityState` data class
- Created InputTypes.kt: `Button` enum, `DpadDirection` enum, `InputState` interface
- Created GraphicsTypes.kt: `SpriteSize`, `AnimationFrame`, `AnimationDef`, `PaletteIndex` data classes
- Deleted 4 package-info.kt placeholders (replaced by type files declaring the same package)
- Added `api(project(":gbkt-rpg"))` constraint to `gbkt-bom/build.gradle.kts`
- Full `./gradlew build` passes across all modules

## Task Commits

Each task was committed atomically:

1. **Task 1: Create v2 engine types in gbkt-engine and add gbkt-rpg to BOM** - `d7506b8` (feat)

**Plan metadata:** (final docs commit below)

## Files Created/Modified

- `gbkt-engine/src/main/kotlin/io/github/gbkt/core/scene/SceneTypes.kt` - SceneId, SceneLifecycle, FadeType, SceneTransitionRequest
- `gbkt-engine/src/main/kotlin/io/github/gbkt/core/entity/EntityTypes.kt` - Positionable, Movable, Hitbox, EntityState
- `gbkt-engine/src/main/kotlin/io/github/gbkt/core/input/InputTypes.kt` - Button, DpadDirection, InputState
- `gbkt-engine/src/main/kotlin/io/github/gbkt/core/graphics/GraphicsTypes.kt` - SpriteSize, AnimationFrame, AnimationDef, PaletteIndex
- `gbkt-bom/build.gradle.kts` - Added gbkt-rpg api constraint between gbkt-backend-gbdk and gbkt-analysis

## Decisions Made

- gbkt-engine type files replace package-info.kt placeholders entirely — same package declaration, richer content
- FadeType.NONE as default for SceneTransitionRequest — no-op default avoids callers specifying fade on every navigate
- InputState interface uses enum parameters (Button, DpadDirection) — type-safe vs string-based lookups
- gbkt-rpg added to gbkt-bom constraints — enables version coordination; genre package inclusion is opt-in per game module

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Applied spotless formatting after file creation**

- **Found during:** Task 1 (after creating all type files)
- **Issue:** Spotless formatting checks failed on new files — doc comments exceeded column width, data class multi-line format non-conformant
- **Fix:** Ran `./gradlew spotlessApply` to auto-fix formatting
- **Files modified:** All 4 new type files
- **Verification:** Full `./gradlew build` passes with all spotless checks green
- **Committed in:** d7506b8 (Task 1 commit, spotless apply included)

---

**Total deviations:** 1 auto-fixed (1 missing critical — formatting compliance)
**Impact on plan:** Auto-fix essential for build compliance. No scope creep.

## Issues Encountered

None beyond the spotless formatting fix (auto-resolved).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Gap 1 from Phase 06 verification fully closed: gbkt-engine has real v2 types, gbkt-bom includes gbkt-rpg
- Ready for Phase 06.1 (V1 Feature Parity Port)

## Self-Check: PASSED

All created files verified on disk. Task commit d7506b8 confirmed in git log.

---
*Phase: 06-complete-gap-closure*
*Completed: 2026-02-21*
