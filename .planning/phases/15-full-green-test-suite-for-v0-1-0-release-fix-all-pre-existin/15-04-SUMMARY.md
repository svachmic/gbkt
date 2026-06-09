---
phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
plan: 04
subsystem: pong-example-test
tags: [oam-count, metadata, provably-stale-assertion]
requires: [15-01]
provides: [pong-test-green]
affects: [15-05, 15-06]
tech-stack:
  added: []
  patterns: [oam-16px-slot-rule]
key-files:
  created:
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/diagnosis/pong.md
  modified:
    - gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongStepAgentTest.kt
key-decisions:
  - "Paddle OAM count is 1 (4x16 sprite, 16px-OAM-slot rule); the runtime StepAgent read (actual=1) and metadata oamCount=1 agree, so the test's expected=2 was provably stale. Corrected to {1,1,1}/total=3."
  - "TEST-ONLY change — no gbkt-backend-gbdk codegen edited (A2 confirmed metadata correct). D-02 input for plan 06: pong codegen unchanged."
requirements-completed: [REQ-5]
duration: 8 min
completed: 2026-06-09
---

# Phase 15 Plan 04: PongStepAgentTest green Summary

Corrected the provably-stale paddle OAM expectation (`{2,2,1}`/5 → `{1,1,1}`/3) so
`PongStepAgentTest` agrees with the deliberately-16px-OAM-slot metadata and the runtime OAM read.

- **Duration:** 8 min · **Tasks:** 2 · **Files:** 1 created, 1 modified

## What was done

**Task 1 — Diagnosis.** Cited `GBDKPipeline.buildMetadataFile` L227-229 (16px-OAM-slot rule +
intentional comment). Pong paddle is `size(4,16)` → `oamCount = ceil(4/8) * ceil(16/16) = 1`.
Fresh `game_metadata.json` confirms paddle1/paddle2/ball oamCount = 1. The A2 runtime guard is
confirmed by the test's own failure message `actual=1` — the StepAgent's runtime OAM read returns
1, matching metadata. Fix Path = `provably-stale-assertion`, static evidence (D-03b).

**Task 2 — Fix + prove green.** Updated `MetadataExpectation` to `{PADDLE1:1, PADDLE2:1, BALL:1}`
/ `expectedTotalOam = 3` (with an explanatory comment). `./gradlew :gbkt-examples:pong:test` →
**BUILD SUCCESSFUL in 17s, 0 failures**. No assertion deleted or weakened.

## Deviations from Plan

None - plan executed exactly as written (EXPECTED provably-stale path; no codegen edit).

**Total deviations:** 0. **Impact:** none. Codegen-touch status: NONE (D-02 input for plan 06).

## Issues Encountered

None.

## Next

Ready for 15-03 (BanksUatTest, remaining Wave 2) and Wave 3 (15-05 platformer).

## Self-Check: PASSED

- [x] `./gradlew :gbkt-examples:pong:test` BUILD SUCCESSFUL, 0 failures (/tmp/gsd15_pong.log EXIT=0)
- [x] PongStepAgentTest.kt contains `expectedTotalOam = 3` and `{1,1,1}`
- [x] Expectation corrected to proven runtime value, not deleted/weakened
- [x] evidence/diagnosis/pong.md filled (root cause + provably-stale-assertion + static evidence)
- [x] Codegen-touch status = NONE recorded for plan 06 D-02 branch
- [x] `git log --grep="15-04"` returns 2 commits
