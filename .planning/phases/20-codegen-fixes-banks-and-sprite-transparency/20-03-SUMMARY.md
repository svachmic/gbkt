---
phase: 20-codegen-fixes-banks-and-sprite-transparency
plan: 03
subsystem: testing
tags: [uat, screenshot, emulator, gbc, metasprites, platformer, trns, visual-oracle]

requires:
  - phase: 20-codegen-fixes-banks-and-sprite-transparency
    provides: Phase 20 context, D-04/D-05/D-08 constraints, EVIDENCE_DIR layout, captureAndRename harness
  - phase: 13.6-tRNS-sprite-outline
    provides: ConvertSpritesTask.kt:328-372 tRNS auto-route (elephant.png permutation to OBJ index 0)
  - phase: 19-codegen-fixes-metasprite-cluster
    provides: Phase19VisualEvidenceTest clone-and-retarget precedent, Phase 12.8 PlatformerTemplate128UatTest precedent

provides:
  - MetaspritePhase20OracleTest.kt — GBC-mode elephant sprite-outline screenshot oracle (D-08 #1)
  - PlatformerTemplatePhase20OracleTest.kt — GBC-mode player-transparency twin shot (D-08 #2)
  - evidence/fix-04/metasprites-sprite-outline.png — 160x144, 4 distinct colours, dominant 0.4978
  - evidence/fix-04/platformer-player-transparency.png — 160x144, 7 distinct colours, dominant 0.8599
  - Both assertScreenshotIsNonUniform() gates PASSED

affects:
  - phase 20 verification (human visual sign-off on both PNGs)
  - FIX-04 Success Criterion 3 and 4

tech-stack:
  added: []
  patterns:
    - "clone-and-retarget EVIDENCE_DIR: new Phase-20-specific test class per precedent from PlatformerTemplate128UatTest (Phase 12.8) and Phase19VisualEvidenceTest"
    - "GBC oracle test: newGbcAgent() + captureAndRename() + assertScreenshotIsNonUniform() non-blank gate before human sign-off"

key-files:
  created:
    - "gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspritePhase20OracleTest.kt"
    - "gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplatePhase20OracleTest.kt"
    - ".planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/metasprites-sprite-outline.png"
    - ".planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/platformer-player-transparency.png"
    - ".planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/phase20-fix04-sprite-outline-perceptual.txt"
    - ".planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/phase20-fix04-platformer-player-transparency-perceptual.txt"
  modified: []

key-decisions:
  - "GBC mode for both captures: metasprites targets GBC_COMPATIBLE; GBC mode avoids the DMG-artifact trap (learning_platformer_mcp_needs_gbc_mode); matches MetaspriteUatTest.newGbcAgent() precedent"
  - "Separate Phase-20 test classes (not new methods in existing files): avoids invasive EVIDENCE_DIR changes to production test classes; mirrors Phase 12.8 / Phase 19 clone-and-retarget precedent"
  - "RIGHT+A navigation for platformer: held-RIGHT-only stalls at designed tree obstacle (learning_platformer_traversal_needs_jumps); periodic A presses get the player clearly on screen within 120 frames without reaching level-end trigger"

patterns-established:
  - "Phase-20-specific EVIDENCE_DIR via System.getProperty(user.dir).resolve(../../.planning/phases/20-.../evidence/fix-04).normalize() — consistent with Phase 19 and Phase 12.8 patterns"
  - "assertScreenshotIsNonUniform() non-blank gate (>=2 distinct colours AND dominant < 95%) copied verbatim from PlatformerTemplate128UatTest — standard gate before human visual sign-off"

requirements-completed: [FIX-04]

duration: 4min
completed: 2026-06-14
---

# Phase 20 Plan 03: FIX-04 tRNS Visual Oracle Summary

**GBC-mode runtime screenshot oracles for FIX-04: metasprites elephant sprite-outline (4 colours, dominant 0.4978) and platformer player-transparency (7 colours, dominant 0.8599) — both assertScreenshotIsNonUniform() gates PASSED, evidence in fix-04/ for human sign-off**

## Performance

- **Duration:** 4 min
- **Started:** 2026-06-14T08:02:28Z
- **Completed:** 2026-06-14T08:06:41Z
- **Tasks:** 2
- **Files modified:** 6 created (2 test classes + 2 PNGs + 2 perceptual sidecars), 0 production

## Accomplishments

- MetaspritePhase20OracleTest.kt authored with newGbcAgent() (gbcMode=true), captureAndRename() 2-param, and assertScreenshotIsNonUniform() gate; confirms elephant sprite-outline clean against freshly clean-built metasprites.gb (D-05 satisfied)
- PlatformerTemplatePhase20OracleTest.kt authored as clone-and-retarget with Phase-20 EVIDENCE_DIR, newGbcAgent() (D-05 LOCKED: gbcMode=true), captureAndRename() 4-param; uses RIGHT+A navigation to clear tree obstacle and show player on screen
- Both evidence PNGs captured into `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/` with perceptual sidecar files; both non-blank gates GREEN; zero production code changed

## Task Commits

Each task was committed atomically:

1. **Task 1: Capture metasprites elephant sprite-outline clean (D-08 oracle #1, GBC mode)** - `4c945f85` (feat)
2. **Task 2: Capture platformer-template player-transparency twin shot, no regression (D-08 oracle #2, GBC mode)** - `c8183549` (feat)

## Files Created/Modified

- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspritePhase20OracleTest.kt` — Phase 20 FIX-04 sprite-outline oracle test class (GBC mode, EVIDENCE_DIR fix-04)
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplatePhase20OracleTest.kt` — Phase 20 FIX-04 player-transparency oracle test class (GBC mode LOCKED, EVIDENCE_DIR fix-04)
- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/metasprites-sprite-outline.png` — D-08 visual oracle #1 (160x144, 4 distinct colours, dominant 0.4978)
- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/platformer-player-transparency.png` — D-08 visual oracle #2 (160x144, 7 distinct colours, dominant 0.8599)
- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/phase20-fix04-sprite-outline-perceptual.txt` — non-blank gate metrics for metasprites
- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/phase20-fix04-platformer-player-transparency-perceptual.txt` — non-blank gate metrics for platformer

## Decisions Made

- **GBC mode for both captures:** metasprites targets GBC_COMPATIBLE; using GBC mode avoids the DMG-artifact trap (green-tinted captures per `learning_platformer_mcp_needs_gbc_mode`). Decision matches RESEARCH open question #1 recommendation and D-05.
- **Separate Phase-20 test classes:** new standalone `MetaspritePhase20OracleTest` and `PlatformerTemplatePhase20OracleTest` (not new methods in existing test files) avoids invasive EVIDENCE_DIR changes to Phase 10/12.8 test classes. Mirrors `PlatformerTemplate128UatTest` (Phase 12.8) and `Phase19VisualEvidenceTest` precedents.
- **RIGHT+A navigation for platformer:** 120 frames of periodic RIGHT+A puts the player clearly on screen without reaching the level-end trigger (trigger requires ~736 frames). Avoids the held-RIGHT-only stall at the designed tree obstacle (cols 39-40, rows 12-15 of world1Area1).

## Deviations from Plan

None — plan executed exactly as written. The two spotlessApply runs reformatted the files (expected; D-08 mandates spotless per-commit), producing the finalised on-disk content but no logic changes.

## Issues Encountered

None. Both ROM builds (metasprites clean, platformer-template clean) succeeded on first attempt. Both test runs produced BUILD SUCCESSFUL with the evidence PNGs written to `evidence/fix-04/`. Both assertScreenshotIsNonUniform() gates passed without adjustment.

## Known Stubs

None. Both test classes wire directly to the ROM and emulator; no placeholder data, hardcoded empty values, or deferred wiring.

## Threat Flags

None. Both new test classes drive an embedded emulator on local ROMs and write PNG evidence files. No new network endpoints, auth paths, file access patterns outside the existing `build/` and `.planning/` directories, or schema changes at trust boundaries.

## Gate Results

| Gate | Result | Details |
|------|--------|---------|
| Task 1 BUILD SUCCESSFUL | GREEN | metasprites clean+buildRom+test passed |
| Task 1 gbcMode = true | PASS | grep confirms `.copy(gbcMode = true)` in MetaspritePhase20OracleTest.kt |
| Task 1 assertScreenshotIsNonUniform | GREEN | 4 distinct colours, dominant ratio 0.4978 (<0.95) |
| Task 2 BUILD SUCCESSFUL | GREEN | platformer-template clean+buildRom+test passed |
| Task 2 gbcMode = true | PASS | grep confirms `.copy(gbcMode = true)` in PlatformerTemplatePhase20OracleTest.kt |
| Task 2 assertScreenshotIsNonUniform | GREEN | 7 distinct colours, dominant ratio 0.8599 (<0.95) |
| Production .kt unchanged | PASS | git diff shows only test files and evidence PNGs added |

## Screenshot Artifact Locations

- `metasprites-sprite-outline.png` → `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/metasprites-sprite-outline.png`
- `platformer-player-transparency.png` → `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/platformer-player-transparency.png`

Both PNGs are ready for human visual sign-off at phase verification. They confirm:
1. The Phase 13.6 tRNS auto-route (`ConvertSpritesTask.kt:328-372`) routes elephant's transparent pixel to GB OBJ index 0 (sprite-outline clean)
2. Platformer-template player transparency is unchanged at HEAD (no regression)

## Next Phase Readiness

- FIX-04 visual oracle artifacts produced and non-blank gates passed; human visual sign-off completes Success Criteria 3 and 4
- Plan 20-04 (byte-identity oracle sweep) is the next plan in Phase 20 wave 1
- No blockers

## Self-Check

---
*Phase: 20-codegen-fixes-banks-and-sprite-transparency*
*Completed: 2026-06-14*
