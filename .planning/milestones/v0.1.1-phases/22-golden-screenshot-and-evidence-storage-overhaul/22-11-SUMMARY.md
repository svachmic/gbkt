---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: 11
subsystem: gbkt-examples emission tests
tags: [evidence-dir, build-scratch, r1, r3, emission-test, refactor]
dependency_graph:
  requires: []
  provides: [emission-test-evidence-dir-redirected]
  affects: [gbkt-examples/metasprites, gbkt-examples/platformer-template, gbkt-examples/simple-physics, gbkt-examples/pong, gbkt-examples/breakout, gbkt-examples/banks]
tech_stack:
  added: []
  patterns: [user.dir-relative build/ scratch path for emission test dumps]
key_files:
  created: []
  modified:
    - gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteEmissionTest.kt
    - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateEmissionTest.kt
    - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlayerMetaspriteGeometryTest.kt
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt
    - gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongNoExitRegressionTest.kt
    - gbkt-examples/breakout/src/test/kotlin/io/github/gbkt/examples/breakout/BreakoutNoExitRegressionTest.kt
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt
decisions:
  - EVIDENCE_DIR redirected to build/gbkt/test-evidence (gitignored) — no committed artifact, R1 + R3 compliant
  - KDoc comments updated to explain build/ scratch pattern and R1+R3 rationale
metrics:
  duration: 3m
  completed: 2026-06-14
---

# Phase 22 Plan 11: Example Module Emission Test EVIDENCE_DIR Redirect Summary

Redirected EVIDENCE_DIR in 7 example-module emission test classes from `.planning/phases/**/evidence` paths to each module's gitignored `build/gbkt/test-evidence/` scratch directory (R1 + R3). In-test C/IR assertions remain the gate and continue to pass.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Redirect EVIDENCE_DIR in metasprites/platformer/simple-physics emission tests | 4ae2cb57 | MetaspriteEmissionTest.kt, PlatformerTemplateEmissionTest.kt, PlayerMetaspriteGeometryTest.kt, SimplePhysicsEmissionTest.kt |
| 2 | Redirect pong/breakout/banks emission tests + run module suites | 8e41f165 | PongNoExitRegressionTest.kt, BreakoutNoExitRegressionTest.kt, BanksEmissionTest.kt |

## What Was Done

All 7 example-module emission test classes had their `EVIDENCE_DIR` companion value changed from:

```kotlin
// BEFORE (example from MetaspriteEmissionTest):
val EVIDENCE_DIR =
    File(System.getProperty("user.dir"))
        .resolve("../../.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape")
        .normalize()
```

to:

```kotlin
// AFTER (all 7 files):
val EVIDENCE_DIR =
    File(System.getProperty("user.dir"))
        .resolve("build/gbkt/test-evidence")
        .normalize()
```

The `build/gbkt/test-evidence` path is gitignored via the root `.gitignore` `build/` pattern — no committed artifact. This completes the emission-half EVIDENCE_DIR elimination for the example modules.

Additionally:
- KDoc comments updated in all 7 files to describe the gitignored build/ scratch pattern and reference R1+R3
- A stale comment in `PlayerMetaspriteGeometryTest.kt` (line 60) that mentioned `.planning/phases/12.5-.../evidence/tier1-geometry/` was updated to reference `build/gbkt/test-evidence/`

## Verification

- `grep -rl "planning/phases" <all 7 files> | wc -l` → 0 (clean)
- `./gradlew :gbkt-examples:pong:test :gbkt-examples:breakout:test :gbkt-examples:banks:test --tests "*EmissionTest" --tests "*NoExitRegressionTest"` → BUILD SUCCESSFUL
- spotlessApply + detekt clean for all 6 affected modules

## Deviations from Plan

None — plan executed exactly as written. The one minor addition was updating the stale comment in `PlayerMetaspriteGeometryTest.kt` (line 60) which also referenced a `.planning/phases` path but was not in the functional EVIDENCE_DIR companion — this was a Rule 1 inline fix (correctness: the comment was misleading about where evidence lands).

## Known Stubs

None.

## Threat Flags

None — test-infrastructure-only change. No new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries.

## Self-Check: PASSED

- All 7 modified files exist and contain `build/gbkt/test-evidence` in EVIDENCE_DIR: FOUND
- Task 1 commit 4ae2cb57: FOUND
- Task 2 commit 8e41f165: FOUND
