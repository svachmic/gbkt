---
phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
plan: 03
subsystem: banks-example-test
tags: [uat, screenshot, dominant-colour, provably-stale-assertion, d-03]
requires: [15-01]
provides: [banks-test-green]
affects: [15-06]
tech-stack:
  added: []
  patterns: [region-scoped-non-uniformity]
key-files:
  created:
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/diagnosis/banks.md
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/banks-anchor1-play-scene.png
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/banks-anchor2-tilemap.png
  modified:
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
key-decisions:
  - "Live D-03 MCP screenshot proves the banked checker renders as a 16x16 swatch (swatch dominant 0.50); the full-frame <95% dominant gate is arithmetically unsatisfiable for a 1.1%-of-frame swatch — wrong premise, not a render bug."
  - "Provably-stale fix: scope the SAME 0.95 non-uniformity gate to the painted 16x16 region (region param added). 0.95 threshold UNCHANGED, no assertion deleted. Test-only, no codegen edit."
requirements-completed: [REQ-4]
duration: 18 min
completed: 2026-06-09
---

# Phase 15 Plan 03: BanksUatTest green Summary

Drove `BanksUatTest` green (2 → 0) by proving via a live D-03 screenshot that the banked zone
checker renders correctly as a small 16×16 swatch, and re-architecting the non-uniformity gate to
the painted region — without lowering the 0.95 threshold.

- **Duration:** 18 min · **Tasks:** 2 · **Files:** 3 created (incl. 2 screenshots), 1 modified

## What was done

**Task 1 — Live D-03 diagnosis.** Rebuilt the banks ROM and drove it live with the MCP emulator
(title → START → play). Captured `evidence/banks-anchor{1,2}*.png`. Pixel analysis: full-frame
dominant 0.9944, but the top-left 16×16 region is a perfect 0.50 checker (content bbox x[0–15]
y[0–15]). The MCP BG-tile dump shows the 2×2 checker `tile0/tile1/tile1/tile0`. The generated
`_zone_playZone_tilemap.c` is `{0x00,0x01,0x01,0x00}` (tileset-only `playZone` → 2×2 tilemap,
bank-loaded from bank 2 via `_bkg_tiles_load_banked`). Verdict = **provably-stale-assertion**: the
banked checker renders correctly but is intentionally a 16×16 swatch (≤1.1% of the frame), so the
full-frame `<95% dominant` gate is arithmetically unsatisfiable and tests the wrong premise. Not a
bank-load/render bug; no codegen edit.

**Task 2 — Fix + prove green.** Added an optional `region` parameter to
`assertScreenshotIsNonUniform` and scoped anchors 1 & 2 to the painted 16×16 swatch, where the
SAME gate (≥2 colours AND dominant **< 0.95**, threshold unchanged) passes (swatch dominant 0.50).
`./gradlew :gbkt-examples:banks:test` → **0 failures** (XML 13:43). The `0.95` constant is intact
(6 occurrences) and no assertion was deleted.

## Deviations from Plan

None — executed as written (EXPECTED provably-stale path; live D-03 evidence; threshold intact).
Codegen-touch status: NONE.

**Total deviations:** 0. **Impact:** none.

## Issues Encountered

None. (The GBC-mode MCP capture collapsed the checker to one hue — no GBC BG palette in this minimal
demo — so DMG mode was used for the binding capture, matching the StepAgent the test runs.)

## Next

Wave 2 complete (15-02, 15-03, 15-04 all green). Ready for Wave 3 (15-05 platformer).

## Self-Check: PASSED

- [x] `./gradlew :gbkt-examples:banks:test` 0 failures (XML 2026-06-09 13:43, failures="0")
- [x] Live D-03 screenshots in evidence/ for both anchors
- [x] `0.95` dominant-colour ratio NOT lowered (6 occurrences remain); no assertion deleted
- [x] No gbkt-backend-gbdk edit (collision note N/A; 15-04 already complete)
- [x] evidence/diagnosis/banks.md filled with live-screenshot evidence refs
- [x] `git log --grep="15-03"` returns 2 commits
