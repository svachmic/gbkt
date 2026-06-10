---
phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
plan: 06
subsystem: release-gate
tags: [final-green, d-02, byte-identity, diagnosis-ledger, release-gate]
requires: [15-02, 15-03, 15-04, 15-05]
provides: [full-suite-green, finalized-diagnosis-ledger]
affects: []
tech-stack:
  added: []
  patterns: [byte-identity-split-guard]
key-files:
  created:
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/FINAL-GREEN.md
  modified:
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/DIAGNOSIS-LEDGER.md
key-decisions:
  - "D-02 EXPECTED path: zero production (src/main) source changed in phase 15 — all 6 fixes are test-side or build-wiring — so all 7 KEEP examples are byte-identical to baseline; NO re-pin. metasprites/metasprites-stress byte-identity guards stay green."
  - "Both canonical commands green from a clean tree: test --continue (0 failures) + pluginTest (IntegrationTest 19/0/0/0). Ledger finalized: 12 real-bug-fix + 6 provably-stale + 0 removals, zero threshold-weakening."
requirements-completed: [REQ-1, REQ-7]
duration: 14 min
completed: 2026-06-09
---

# Phase 15 Plan 06: Close release gate Summary

Closed the v0.1.0 release gate: both canonical suite commands report zero failures from a clean
tree, the D-02 split regression guard holds (all 7 examples byte-identical, all 7 :buildRom EXIT 0),
and the per-failure diagnosis ledger is finalized with zero threshold-weakening.

- **Duration:** 14 min · **Tasks:** 2 · **Files:** 1 created, 1 modified

## What was done

**Task 1 — D-02 split regression guard.** `git diff 771850c9 HEAD` over non-planning paths shows
**zero `src/main` / codegen changes** — all 6 fixes are test-side (4 test files) or build-wiring (root
`build.gradle.kts` republish-set, platformer `build.gradle.kts` test-task dep). EXPECTED path: all 7
KEEP examples byte-identical to baseline, NO re-pin. `metasprites`/`metasprites-stress`
`*GeneratedSpriteByteIdentityTest` are green in the `test --continue` run (would fail on any codegen
drift). 7× `:buildRom` → BUILD SUCCESSFUL in 3s, all ROMs present.

**Task 2 — Full-suite green + finalize ledger.** Consolidated the 4 per-class diagnosis fragments
into `DIAGNOSIS-LEDGER.md` (18 rows). Ran both canonical commands clean: `./gradlew test --continue`
→ BUILD SUCCESSFUL, 0 failures; `./gradlew pluginTest` → BUILD SUCCESSFUL, IntegrationTest
`19/0/0/0`. Wrote `FINAL-GREEN.md`. Ledger finalized: 12 `real-bug-fix` + 6
`provably-stale-assertion` + 0 `retired-capability-removal`; zero threshold-weakening rows; F2/F3/F4
corrected-not-deleted; D-04 deviation recorded.

## Deviations from Plan

**[Transient infra — recorded, not a test failure]** The first `pluginTest` attempt (immediately
after `test --continue`) hit a Gradle `BuildToolsApiClasspathEntrySnapshotTransform` cache error on
the freshly-republished `gbkt-analysis-0.1.0-SNAPSHOT.jar` during `compileTestKotlin`. A clean
`./gradlew --stop && ./gradlew pluginTest` re-run was GREEN. Documented in FINAL-GREEN.md.

The plan listed metasprites/metasprites-stress byte-identity test files in `files_modified` for the
CONTINGENCY re-pin path; the EXPECTED path (no codegen change) was taken, so they were NOT modified.

**Total deviations:** 1 transient infra hiccup (resolved by re-run). **Impact:** none — final state
is green on both commands.

## Issues Encountered

None unresolved.

## Next

Phase 15 plans complete (6/6). Ready for phase verification. Tagging v0.1.0 and re-presenting Phase
14's sign-off are downstream manual steps (out of scope).

## Self-Check: PASSED

- [x] `./gradlew test --continue` BUILD SUCCESSFUL, 0 failures (/tmp/gsd15_final_test.log)
- [x] `./gradlew pluginTest` BUILD SUCCESSFUL, IntegrationTest 19/0/0/0 (/tmp/gsd15_final_plugin2.log)
- [x] D-02: zero production source changed → all 7 byte-identical (no re-pin); 7× :buildRom EXIT 0
- [x] DIAGNOSIS-LEDGER.md finalized — 18 rows, 0 threshold-weakening, 0 removals, D-04 noted
- [x] FINAL-GREEN.md written with both aggregate-command results
- [x] `git log --grep="15-06"` returns 1 commit
