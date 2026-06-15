---
phase: 20-codegen-fixes-banks-and-sprite-transparency
plan: "02"
subsystem: testing
tags: [banks, audit, emission-guards, codegen, regression-guard, uat, sram]

requires:
  - phase: 20-01
    provides: D-02 ordering gate satisfied; INV-2/INV-5/INV-6 GREEN evidence; Anchor 4 GREEN evidence

provides:
  - 20-AUDIT-FIX-03.md: FIX-03 banks-trio emission-guard audit (D-01)
  - D-03 no-duplicate-coverage confirmed: 0 of 3 seeds needed new guards
  - SEED-014/015/016 mapped 1:1 to existing guards with reverted-fix scenarios documented

affects:
  - 20-03 (FIX-04 oracle captures — next plan)

tech-stack:
  added: []
  patterns:
    - "D-03 audit-first: locate all pre-existing guards before authoring; author new guard ONLY if real gap found"

key-files:
  created:
    - .planning/phases/20-codegen-fixes-banks-and-sprite-transparency/20-AUDIT-FIX-03.md
  modified: []

key-decisions:
  - "D-03 satisfied: zero new guards authored; all three banks seeds had full pre-existing JVM guard coverage"
  - "SEED-014 has two companion guards (INV-2 in main.c wrapper, INV-6 in bank1.c play_enter) both at GBDKPipeline.kt:1428 hasZoneSceneBinder gate"
  - "SEED-016/Anchor 4 documented as requiring ROM build + GBDK_HOME (RESEARCH Pitfall 1); test auto-skips without GBDK"

requirements-completed: [FIX-03]

duration: 2min
completed: 2026-06-14
---

# Phase 20 Plan 02: FIX-03 Banks-Trio Emission-Guard Audit Summary

**Standalone 20-AUDIT-FIX-03.md authored mapping SEED-014/015/016 to existing BanksEmissionTest (INV-2/INV-5/INV-6) and BanksUatTest (Anchor 4) guards; zero new guards needed; D-02 GREEN result recorded; D-03 no-duplicate-coverage confirmed**

## Performance

- **Duration:** 2 min
- **Started:** 2026-06-14T08:10:07Z
- **Completed:** 2026-06-14T08:12:00Z
- **Tasks:** 1
- **Files modified:** 1 (audit doc only)

## Accomplishments

- 20-AUDIT-FIX-03.md created at the phase dir, mirroring 19-AUDIT-FIX-02.md structure
- All three banks seeds mapped 1:1 to pre-existing JVM guards (D-03 confirmed)
- D-02 GREEN re-verification result from Plan 01 recorded in audit status block (run commands + evidence file references)
- Exact assertion method names and line numbers confirmed via grep of BanksEmissionTest.kt (INV-2:200, INV-5:408, INV-6:463) and BanksUatTest.kt (Anchor 4:291)
- Zero new guards authored; zero production .kt files modified

## Task Commits

1. **Task 1: Audit existing banks-seed coverage and confirm zero gaps (D-03)** - `4966a232` (docs)

## Files Created/Modified

- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/20-AUDIT-FIX-03.md` — FIX-03 banks-trio emission-guard audit (standalone, mirrors 19-AUDIT-FIX-02.md format)

## Decisions Made

- D-03 satisfied: RESEARCH was correct — zero gaps found; all 3 seeds had full pre-existing coverage
- SEED-014 documented with two companion guards (INV-2 + INV-6) that together prove the hasZoneSceneBinder fix at GBDKPipeline.kt:1428 is complete (wrapper in HOME bank + call site in banked file)
- SEED-016/Anchor 4 pitfall noted in guard details: requires buildRom + GBDK_HOME; test auto-skips without GBDK (not a test failure)

## Deviations from Plan

None - plan executed exactly as written. Audit confirmed full pre-existing coverage. Zero production code change. D-03 gate satisfied.

## Issues Encountered

None. All guard locations confirmed from evidence files captured in Plan 01. Method names and line numbers verified by direct grep of test files.

## Known Stubs

None. This plan produces an audit document only; no feature stubs exist.

## Threat Flags

No new trust boundaries or security-relevant surfaces. This plan authors a markdown audit document only. No user input, no network I/O, no production code path.

## Next Phase Readiness

- D-01 satisfied: 20-AUDIT-FIX-03.md exists at the phase dir
- D-02 satisfied: D-02 GREEN run result recorded in audit status block
- D-03 satisfied: zero new guards authored; full existing coverage confirmed
- FIX-03 acceptance criteria met: all three seeds mapped, four assertion names present, Decisions Captured table present
- Ready for Plan 03 (FIX-04 oracle captures)

## Self-Check

Files created:
- [x] 20-AUDIT-FIX-03.md — FOUND

Commits:
- [x] 4966a232 — FOUND (docs(20-02): author 20-AUDIT-FIX-03.md banks-trio emission-guard audit)

Production .kt files modified: ZERO (confirmed via git status --porcelain)

## Self-Check: PASSED

---
*Phase: 20-codegen-fixes-banks-and-sprite-transparency*
*Completed: 2026-06-14*
