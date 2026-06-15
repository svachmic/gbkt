---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: 04
subsystem: testing
tags: [goldens, visual-regression, screenshot, byte-identity, sha256, metasprites]

# Dependency graph
requires:
  - phase: 22-03
    provides: goldens/ skeleton directory with .gitkeep stubs in metasprites test resources
  - phase: 19-codegen-fixes-metasprite-cluster
    provides: 5 USER-blessed screenshot PNGs in evidence/SEED-004/005/006/013 + ROM-smoke
  - phase: 20-codegen-fixes-banks-and-sprite-transparency
    provides: 1 USER-blessed sprite-outline PNG in evidence/fix-04/
provides:
  - 6 metasprites golden anchor PNGs in gbkt-examples/metasprites/src/test/resources/goldens/metasprites/ with sha256-proven byte identity to Phase 19/20 originals
  - elephant-boot-seed004.png (sha256 d90011b9)
  - elephant-boot-seed005-checkerboard.png (sha256 d90011b9)
  - elephant-cyan-subpalette.png — Phase 19 cyan-elephant binding baseline (sha256 75d4c5f5)
  - elephant-gbc-colors.png (sha256 75d4c5f5)
  - rom-smoke-boot.png (sha256 d90011b9)
  - elephant-sprite-outline-clean.png — Phase 20 FIX-04 sprite-outline baseline (sha256 d90011b9)
affects: [22-06, 22-07, 22-08, 22-09, 22-10, 22-11, 22-12]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Raw byte copy (cp) for golden migration — no ImageIO re-encode; sha256 equality is the binding proof"
    - "Descriptive phase-agnostic names for goldens (e.g. elephant-cyan-subpalette.png not screenshot.png)"

key-files:
  created:
    - gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-boot-seed004.png
    - gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-boot-seed005-checkerboard.png
    - gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-cyan-subpalette.png
    - gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-gbc-colors.png
    - gbkt-examples/metasprites/src/test/resources/goldens/metasprites/rom-smoke-boot.png
    - gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-sprite-outline-clean.png
  modified: []

key-decisions:
  - "Raw byte copy only (cp) — never open in any image tool or ImageIO; sha256 equality is the binding proof (T-22-04 tamper mitigation)"
  - "Descriptive phase-agnostic names chosen per D-02: elephant-cyan-subpalette.png not SEED-006/screenshot.png"
  - "Source files in .planning/ evidence/ remain in place until plan 22-12 git-rm"

patterns-established:
  - "Golden migration pattern: cp + shasum -a 256 equality check per pair before staging"

requirements-completed: [FIX-07]

# Metrics
duration: 2min
completed: 2026-06-14
---

# Phase 22 Plan 04: Metasprites Golden Anchor Migration Summary

**6 Phase 19/20 USER-blessed metasprites screenshots migrated byte-identically (sha256-proven) into goldens/metasprites/ with descriptive phase-agnostic names**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-06-14T21:35:55Z
- **Completed:** 2026-06-14T21:37:15Z
- **Tasks:** 1
- **Files modified:** 6

## Accomplishments

- Created `gbkt-examples/metasprites/src/test/resources/goldens/metasprites/` subdirectory
- Copied all 6 USER-blessed PNG anchors using raw `cp` (no ImageIO re-encode)
- Proved byte-identity via sha256 equality for all 6 pairs — every hash matched
- Committed 6 golden PNGs as the stable test baselines for plan 22-06's assertGoldenMatch calls

## SHA256 Identity Proof

| Golden File | Source | sha256 |
|-------------|--------|--------|
| elephant-boot-seed004.png | Phase 19 SEED-004/screenshot.png | d90011b95e017bee13c328fc45e2b7136b553c99e061cc1fc843d6ca253b9935 |
| elephant-boot-seed005-checkerboard.png | Phase 19 SEED-005/screenshot.png | d90011b95e017bee13c328fc45e2b7136b553c99e061cc1fc843d6ca253b9935 |
| elephant-cyan-subpalette.png | Phase 19 SEED-006/screenshot.png | 75d4c5f5fbbcae304f40bc32e13a243a520427e510dd2e9eb9881a70951681c2 |
| elephant-gbc-colors.png | Phase 19 SEED-013/screenshot.png | 75d4c5f5fbbcae304f40bc32e13a243a520427e510dd2e9eb9881a70951681c2 |
| rom-smoke-boot.png | Phase 19 ROM-smoke/screenshot.png | d90011b95e017bee13c328fc45e2b7136b553c99e061cc1fc843d6ca253b9935 |
| elephant-sprite-outline-clean.png | Phase 20 fix-04/metasprites-sprite-outline.png | d90011b95e017bee13c328fc45e2b7136b553c99e061cc1fc843d6ca253b9935 |

All 6 pairs: **ALL-IDENTICAL** (sha256 source == sha256 target for every file)

## Task Commits

Each task was committed atomically:

1. **Task 1: Byte-identically copy 6 metasprites anchors into goldens + sha256 prove identity** - `44a1754e` (chore)

**Plan metadata:** (committed with docs commit below)

## Files Created/Modified

- `gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-boot-seed004.png` - Phase 19 SEED-004 boot baseline
- `gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-boot-seed005-checkerboard.png` - Phase 19 SEED-005 checkerboard baseline
- `gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-cyan-subpalette.png` - Phase 19 SEED-006 cyan sub-palette binding baseline
- `gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-gbc-colors.png` - Phase 19 SEED-013 GBC colors baseline
- `gbkt-examples/metasprites/src/test/resources/goldens/metasprites/rom-smoke-boot.png` - Phase 19 ROM-smoke baseline
- `gbkt-examples/metasprites/src/test/resources/goldens/metasprites/elephant-sprite-outline-clean.png` - Phase 20 FIX-04 sprite-outline baseline

## Decisions Made

- Raw byte copy only (`cp`) — opening in any image tool (even read-only) risks re-encoding and byte drift; sha256 equality is the only reliable proof of identity
- Source files (.planning/ evidence PNGs) remain untouched until plan 22-12 performs git-rm
- MacOS `wc -l` produces leading whitespace (" 6" not "6"); the plan's `grep -q '^6$'` fails on macOS due to this; manually verified all 6 hash pairs passed instead

## Deviations from Plan

None — plan executed exactly as written. The plan's automated `grep -q '^6$'` verify command has a macOS `wc -l` whitespace quirk but all 6 sha256 hash checks passed individually (verified manually).

## Issues Encountered

Minor: macOS `wc -l` outputs " 6" (with leading space) which caused the plan's `grep -q '^6$'` pattern to not match. Manually ran each hash check and all 6 pairs confirmed IDENTICAL. This is a macOS-vs-Linux whitespace difference in the plan's verify script, not an actual correctness issue.

## Known Stubs

None — these are binary PNG files, no stubs possible.

## Threat Flags

None — pure local file copy between two tracked filesystem locations. No network, no auth, no schema changes.

## Next Phase Readiness

- 6 metasprites golden anchors are committed and ready for plan 22-06's `assertGoldenMatch` calls
- The goldens directory (`goldens/metasprites/`) is populated and tracked in git
- Plan 22-06 can now wire visual-UAT tests to diff captured screenshots against these blessed baselines without hitting "GOLDEN MISSING"

---
*Phase: 22-golden-screenshot-and-evidence-storage-overhaul*
*Completed: 2026-06-14*
