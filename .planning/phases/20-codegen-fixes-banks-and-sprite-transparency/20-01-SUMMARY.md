---
phase: 20-codegen-fixes-banks-and-sprite-transparency
plan: "01"
subsystem: testing
tags: [banks, emission-tests, uat, gbdk, sram, codegen, regression-guard]

requires:
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: hasZoneSceneBinder guard at GBDKPipeline.kt:1428, FunctionDeduplicationPass comment-skip fix, SRAM save system

provides:
  - D-02 ordering gate satisfied: INV-2/INV-5/INV-6 GREEN on chore/hardening_0_1_0
  - SEED-014/015/016 re-confirmed VERIFIED-ALREADY-FIXED at HEAD
  - Four GREEN console-capture evidence files under evidence/fix-03/

affects:
  - 20-02 (FIX-03 audit doc — Plan 02 may proceed; D-02 gate cleared)

tech-stack:
  added: []
  patterns:
    - "D-02 re-verify gate: run existing tests fresh before authoring any audit; no production change"

key-files:
  created:
    - .planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/inv2-emission-run.txt
    - .planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/inv5-emission-run.txt
    - .planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/inv6-emission-run.txt
    - .planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/anchor4-sram-run.txt
  modified: []

key-decisions:
  - "Per-assertion INV filter runs used for attribution clarity (--tests INV-2* / INV-5* / INV-6* individually), plus full BanksEmissionTest suite run to confirm all 8 pass together"
  - "Anchor 4 ran as PASS (not SKIP) because GBDK_HOME is available locally; documented GBDK_HOME path in evidence file"

patterns-established:
  - "FIX-03 re-verify gate pattern: run tests fresh, capture XML testsuite output + full Gradle console, write evidence file with header metadata + verdict line"

requirements-completed: [FIX-03]

duration: 3min
completed: 2026-06-14
---

# Phase 20 Plan 01: FIX-03 Re-verify Gate Summary

**BanksEmissionTest INV-2/INV-5/INV-6 and BanksUatTest Anchor 4 all GREEN at HEAD on chore/hardening_0_1_0; SEED-014/015/016 confirmed VERIFIED-ALREADY-FIXED; D-02 ordering gate satisfied; zero production code change**

## Performance

- **Duration:** 3 min
- **Started:** 2026-06-14T07:56:05Z
- **Completed:** 2026-06-14T07:59:13Z
- **Tasks:** 2
- **Files modified:** 4 (evidence files only)

## Accomplishments

- BanksEmissionTest suite (8 tests, 1 expected skip) ran to GREEN: INV-2 (SEED-014), INV-5 (SEED-015), INV-6 (SEED-014) all PASSED; D-02 gate satisfied
- Anchor 4 SRAM round-trip (BanksUatTest) PASSED on a freshly clean-built banks.gb ROM (64 KB, MBC5_RAM_BATTERY, 4 banks); SEED-016 re-confirmed
- `hasZoneSceneBinder` guard at `GBDKPipeline.kt:1428` confirmed sufficient for SEED-014 on current master
- Zero production .kt files modified

## Task Commits

1. **Task 1: Re-verify FIX-03 emission guards (INV-2/INV-5/INV-6) GREEN at HEAD** - `c6ec5a1d` (chore)
2. **Task 2: Build banks.gb clean and re-verify Anchor 4 SRAM round-trip GREEN** - `acdc0c03` (chore)

## Files Created/Modified

- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/inv2-emission-run.txt` - INV-2 GREEN evidence (SEED-014 wrapper emission, SWITCH_ROM shape in main.c)
- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/inv5-emission-run.txt` - INV-5 GREEN evidence (SEED-015 trampoline section comment preservation)
- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/inv6-emission-run.txt` - INV-6 GREEN evidence (SEED-014 play_enter -> _bkg_tiles_load_banked(2u,...) in bank1.c)
- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/anchor4-sram-run.txt` - Anchor 4 GREEN evidence (SEED-016 SRAM non-tautological round-trip PASSED)

## Decisions Made

- Used per-assertion filter runs (`--tests "*.INV-2*"`) for individual evidence attribution, plus a full BanksEmissionTest suite run confirming 8/8 pass together (testsuite XML: tests=8, skipped=1, failures=0, errors=0)
- Anchor 4 ran as PASS (not SKIP) because GBDK_HOME=/Users/michalsvacha/gbdk was available locally; documented in anchor4-sram-run.txt

## Deviations from Plan

None - plan executed exactly as written. All tests GREEN. Zero production code change. D-02 gate satisfied.

## Issues Encountered

None. All BanksEmissionTest INV guards (INV-2/INV-5/INV-6) passed immediately. Banks ROM built clean in 6s. BanksUatTest all 3 anchors PASSED (anchor 1, 2, and 4). The only warning from Coffee-GB emulator ("RAM bank is defined to 0. Overriding to 1.") appeared 3 times (once per UAT test) and is a pre-existing non-fatal emulator message not related to test logic.

## Known Stubs

None. This plan produces evidence artifacts only; no feature stubs exist.

## Threat Flags

No new trust boundaries or security-relevant surfaces. This plan runs existing tests and captures console output. No user input, no network I/O, no production code path.

## Next Phase Readiness

- D-02 gate cleared: Plan 02 (FIX-03 audit doc `20-AUDIT-FIX-03.md`) may now proceed
- All four evidence files exist under `evidence/fix-03/`
- SEED-014/015/016 disposition: VERIFIED-ALREADY-FIXED confirmed on `chore/hardening_0_1_0`
- No blockers

## Self-Check

Files created:
- [x] evidence/fix-03/inv2-emission-run.txt — FOUND
- [x] evidence/fix-03/inv5-emission-run.txt — FOUND
- [x] evidence/fix-03/inv6-emission-run.txt — FOUND
- [x] evidence/fix-03/anchor4-sram-run.txt — FOUND

Commits:
- [x] c6ec5a1d — FOUND (chore(20-01): capture BanksEmissionTest INV-2/INV-5/INV-6 GREEN evidence)
- [x] acdc0c03 — FOUND (chore(20-01): capture BanksUatTest Anchor 4 SRAM round-trip GREEN evidence)

Production .kt files modified: ZERO (confirmed via git status --short)

## Self-Check: PASSED

---
*Phase: 20-codegen-fixes-banks-and-sprite-transparency*
*Completed: 2026-06-14*
