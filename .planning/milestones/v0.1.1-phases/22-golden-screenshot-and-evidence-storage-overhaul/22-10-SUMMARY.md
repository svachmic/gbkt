---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: 10
subsystem: test-infrastructure
tags: [evidence-dir, emission-tests, gbkt-genre-platformer, build-scratch, FIX-07]
dependency_graph:
  requires: []
  provides: [platformer-emission-tests-redirected-to-build-scratch]
  affects: [gbkt-genre-platformer/src/test]
tech_stack:
  added: []
  patterns: [user.dir-relative-build-scratch, 22-PATTERNS-Pitfall-5]
key_files:
  created: []
  modified:
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/HorizontalScrollEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/JumpHoldEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/LevelEndTriggerGroundedGuardEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerCameraCallSiteEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerInputEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerPhysicsSnapToTileTopEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerWalkCycleEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapPhysicsPlayerSymbolEmissionTest.kt
decisions:
  - "22-PATTERNS Pitfall 5: user.dir at :gbkt-genre-platformer:test runtime is the module root, so build/gbkt/test-evidence resolves correctly without ../  ascent"
  - "EVIDENCE_DIR companion name retained for backward compatibility within each test (consistent with 22-PATTERNS note on naming)"
metrics:
  duration: "4 min"
  completed: "2026-06-14"
  tasks: 2
  files: 9
---

# Phase 22 Plan 10: Redirect gbkt-genre-platformer Emission Test EVIDENCE_DIR Summary

**One-liner:** Redirect all 9 gbkt-genre-platformer emission test EVIDENCE_DIR companions from .planning/phases/**/evidence paths to the module's gitignored build/gbkt/test-evidence scratch directory (R1 + R3).

## What Was Built

Redirected the `EVIDENCE_DIR` companion object value in 9 emission test classes inside `gbkt-genre-platformer` from phase-specific `.planning/phases/**/evidence/tier1-shape` paths to `File(System.getProperty("user.dir")).resolve("build/gbkt/test-evidence").normalize()`.

Key insight applied (22-PATTERNS Pitfall 5): for `:gbkt-genre-platformer:test`, `user.dir` resolves to the `gbkt-genre-platformer` module root — not the repo root. The existing `../` ascent to the repo root followed by descent into `.planning/phases/` was the root cause of archived-phase evidence regeneration. The replacement path resolves to the module's own gitignored `build/` directory, which is always correct regardless of worktree state.

In-test C assertions remain the gate — the txt evidence files are purely for post-failure artifact review.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Redirect EVIDENCE_DIR to build/ in 5 platformer emission tests | 6e865c79 | HorizontalScrollEmissionTest, JumpHoldEmissionTest, LevelEndTriggerGroundedGuardEmissionTest, PlatformerCameraCallSiteEmissionTest, PlatformerInputEmissionTest |
| 2 | Redirect remaining 4 platformer emission tests + run module suite | 4064c424 | PlatformerPhysicsSnapToTileTopEmissionTest, PlatformerWalkCycleEmissionTest, TilemapCollisionEmissionTest, TilemapPhysicsPlayerSymbolEmissionTest |

## Verification

- `grep -rl "planning/phases" gbkt-genre-platformer/src/test` → **0 files** (clean)
- `./gradlew :gbkt-genre-platformer:test` → **BUILD SUCCESSFUL** (all tests pass)
- `./gradlew :gbkt-genre-platformer:spotlessApply :gbkt-genre-platformer:detekt` → **BUILD SUCCESSFUL**

## Deviations from Plan

None — plan executed exactly as written. The 22-PATTERNS Pitfall 5 clarification (no `../` needed, module root is user.dir) was already encoded in the plan.

## Threat Flags

None. Test-infrastructure-only; no externally-reachable surface.

## Known Stubs

None.

## Self-Check: PASSED

- All 9 test files modified: VERIFIED (git diff confirms)
- Commits exist: 6e865c79 FOUND, 4064c424 FOUND
- No .planning/phases references remain in any emission test: VERIFIED (grep → 0)
- Module test suite green: VERIFIED (BUILD SUCCESSFUL)
