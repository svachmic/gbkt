---
phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
plan: 01
subsystem: test-infra
tags: [inventory, diagnosis-ledger, release-gate]
requires: []
provides: [fresh-run-inventory, diagnosis-ledger-scaffold]
affects: [15-02, 15-03, 15-04, 15-05, 15-06]
tech-stack:
  added: []
  patterns: [diagnose-first-ledger, fresh-run-authoritative-scope]
key-files:
  created:
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/FRESH-RUN-INVENTORY.md
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/DIAGNOSIS-LEDGER.md
  modified: []
key-decisions:
  - "PlatformerTemplateUatTest is GREEN on the main checkout (fresh run); the failing XML was a stale agent worktree — 19-test snapshot becomes 18, and this class drops out of fix scope."
  - "IntegrationTest verdict comes from pluginTest (12 failures), not the root test aggregate, which does not run the gradle-plugin module."
requirements-completed: [REQ-2, REQ-7]
duration: 16 min
completed: 2026-06-09
---

# Phase 15 Plan 01: Fresh-run inventory + diagnosis ledger scaffold Summary

Established the authoritative phase scope by running the full JVM suite fresh
(`./gradlew test --continue` + `./gradlew pluginTest`) and recording every genuinely-red
test, then scaffolded the Req-7 / D-06 per-failure diagnosis ledger that plans 02–06 fill.

- **Duration:** 16 min · **Tasks:** 2 · **Files created:** 2

## What was done

**Task 1 — FRESH-RUN-INVENTORY.md.** Ran both canonical suite commands serially from a
settled tree. Recorded **18 genuinely-red tests across 5 classes**: `IntegrationTest` ×12
(`NoSuchMethodError: SceneIR.copy$default(...)` in the TestKit sandbox sub-build),
`BanksUatTest` ×2 (dominant-colour ≥95% near-blank gate), `PongStepAgentTest` ×1 (paddle1
OAM expected=2 actual=1), `PlatformerTemplate128UatTest` ×1 (facing diff 6.80% < 10%),
`PlayerMetaspriteGeometryTest` ×2 (`sprite_player_frame_0[]` not found in main.c). Reconciled
against the SPEC's 6 known classes and research F1–F7.

**Task 2 — DIAGNOSIS-LEDGER.md.** Scaffolded one empty row per red test (18 rows) plus the
binding D-06 contract: the three allowed fix paths, the D-03 (live-screenshot) vs D-03b
(static) evidence tiers, the no-threshold-weakening rule, the D-04 retired-capability
citation rule, the fragment→consolidation flow, and the research F1–F7 priors.

## Key findings / drift

- **`PlatformerTemplateUatTest` is no longer red.** Its fresh main-checkout XML reports
  `tests=5 failures=0`; the only failing XML lives in a stale `.claude/worktrees/agent-*`
  directory the research says to ignore. Snapshot 19 → fresh 18. Dropped from fix scope.
- **No red tests outside the SPEC's 6 known classes** — no drift-added failures.
- `gbkt-gradle-plugin:test` is not part of the root `test` aggregate; IntegrationTest's
  authoritative verdict is from `pluginTest` (12 failures, `BUILD FAILED in 29s`).

## Deviations from Plan

None - plan executed exactly as written.

**Total deviations:** 0. **Impact:** none.

## Issues Encountered

None. Note: running the example UAT tests regenerated some prior-phase
`evidence/uat-screenshots/*` artifacts in the working tree (a benign side effect of the
fresh suite run); these are unrelated to plan 15-01 and were not staged.

## Next

Ready for Wave 2 (15-02 IntegrationTest, 15-03 BanksUatTest, 15-04 PongStepAgentTest).

## Self-Check: PASSED

- [x] FRESH-RUN-INVENTORY.md exists, contains "FRESH-RUN" token + run date + per-test table + F1–F7 reconciliation
- [x] DIAGNOSIS-LEDGER.md exists, contains "## Diagnosis Ledger" + "Fix Path" header + one row per red test + D-06/D-03/D-04 contract text
- [x] `git log --grep="15-01"` returns 2 commits (Task 1, Task 2)
- [x] No fixes attempted (inventory + scaffold only)
