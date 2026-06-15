---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
plan: 07
subsystem: testing
tags: [uat, platformer, gbc, screenshot, evidence, seeds, gbcMode]

# Dependency graph
requires:
  - phase: 21-codegen-fixes-platformer-and-remaining-seeds (plan 21-01)
    provides: pivotAdjust DSL lift into tilemapCollision; grounded-player codegen
  - phase: 21-codegen-fixes-platformer-and-remaining-seeds (plan 21-02)
    provides: tilemap-collision predicate consolidation (SEED-022)
provides:
  - Post-fix GBC anchor screenshots (anchor-1/2/3) bound to the final fixed ROM
  - EVIDENCE_DIR repointed to the Phase 21 evidence directory
  - GBC-mode UAT capture fix (gbcMode=true) — DMG default rendered the GBC-target ROM inverted
  - Four LOCKED-visual platformer seeds archived with terminal disposition notes
affects: [phase-21-close, milestone-v0.1.1, platformer-uat]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "GBC-target ROMs must be captured with AgentSessionConfig.copy(gbcMode=true); discoverFiles() does NOT enable GBC"
    - "Fix-first then re-shoot (D-14): all anchors captured in one pass against the final ROM"

key-files:
  created:
    - .planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/evidence/uat-screenshots/ (anchor-1..5 PNGs + sidecars + traces)
  modified:
    - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt

key-decisions:
  - "EVIDENCE_DIR permanently repointed to Phase 21 (Phase 21 now owns the binding platformer PNGs)"
  - "gbcMode=true set explicitly in newAgent() — the DMG default inverts a GBC-target ROM palette"
  - "All four platformer seeds reached terminal disposition: 3 FIXED + 1 CLOSED-AS-ACCEPTED"

patterns-established:
  - "GBC-mode UAT capture: .copy(gbcMode = true) after discoverFiles() for GBC-target ROMs"

requirements-completed: [FIX-05]

# Metrics
duration: 59min
completed: 2026-06-14
---

# Phase 21 Plan 07: Platformer GBC Anchor Re-shoot + Seed Archival Summary

**Post-fix GBC anchor screenshots (grounded duck on platform, title→gameplay, initial→scrolled) captured against the final fixed ROM with a gbcMode harness fix, plus terminal archival of all four LOCKED-visual platformer seeds on binding user sign-off.**

## Performance

- **Duration:** 59 min (includes the deviation re-shoot cycle)
- **Started:** 2026-06-14T13:34:47Z
- **Completed:** 2026-06-14T14:34:00Z (approx)
- **Tasks:** 3 (Task 1 auto, Task 2 checkpoint:human-verify, Task 3 auto on sign-off)
- **Files modified:** 1 source file + evidence directory (anchor-1..5 PNGs/sidecars/traces) + 4 archived seeds

## Accomplishments

- Repointed `PlatformerTemplateUatTest.EVIDENCE_DIR` from Phase 12.7 to the Phase 21 evidence directory (Pitfall 5 avoided).
- Rebuilt the platformer-template ROM CLEAN in one pass (GBDK/lcc available; 64 KB `.gb`, exit 0) immediately before capture (Pitfall 4 avoided).
- Captured all 3 plan-required GBC anchors (plus the full 5-anchor suite) in one pass — all assertions GREEN.
- **Deviation fix:** corrected the UAT harness to capture in GBC mode — the DMG default was rendering the GBC-target ROM with an inverted/negative palette.
- Obtained binding user visual sign-off on the GBC-mode re-shoot (NOT the original DMG capture).
- Archived all four LOCKED-visual platformer seeds with disposition notes (3 FIXED, 1 CLOSED-AS-ACCEPTED).

## Task Commits

1. **Task 1: Repoint EVIDENCE_DIR, clean-build GBC ROM, run 3-anchor capture pass** - `9da75edf` (feat)
2. **Deviation fix: capture anchors in GBC mode** - `71dd3a57` (fix)
3. **Task 3: Archive four LOCKED-visual platformer seeds** - `d1b19bdd` (docs)

_Task 2 was a `checkpoint:human-verify` gate (`gate="blocking-human"`) — required binding user sign-off, which was given as "approved" on the GBC-mode screenshots._

## Files Created/Modified

- `gbkt-examples/.../PlatformerTemplateUatTest.kt` - EVIDENCE_DIR repointed to Phase 21; `newAgent()` now sets `gbcMode = true` on the session config.
- `.planning/phases/21-.../evidence/uat-screenshots/` - All anchor PNGs, JSON sidecars, perceptual notes, and variable traces (GBC mode).
- `.planning/seeds/archive/SEED-021-platformer-pivot-adjust-auto-derive.md` - archived, FIXED note.
- `.planning/seeds/archive/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md` - archived, FIXED note.
- `.planning/seeds/archive/SEED-platformer-template-spawn-polish.md` - archived, FIXED note (supersedes SPAWN-POSITION-CLARITY).
- `.planning/seeds/archive/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md` - archived, CLOSED-AS-ACCEPTED note.

## Anchor Evidence

- **anchor-1** (`01-title.png`, `02-gameplay.png`): title "GBDK-2020 PLATFORMER TEMPLATE" renders on a clean light background; gameplay screen visually distinct (cEmit-fixed level load — **Criterion 1**).
- **anchor-2** (`01-grounded.png`, `02-mid-jump.png`, `03-landed.png`): light-green sky, dark grounded duck standing cleanly on the platform/ground row, green tile blocks to the right. Trace: **grounded=1, playerY=1632 sub-pixels (=102 px), playerVy=0, playerX=640 (=40 px)**. Land/jump cycle confirmed (frames_to_land=61, mid-jump vy=-800, landed grounded=1).
- **anchor-3** (`01-initial.png`, `02-scrolled.png`): GBC palette, horizontal scroll advances the camera without corruption.

Together these satisfy **Criterion 1 (cEmit closure)** + **Criterion 2 (3 GBC anchors)** via the Visual Evidence Rule (runtime GBC screenshots, not variable assertions).

## Decisions Made

- EVIDENCE_DIR permanently repointed to Phase 21 (Phase 21 owns the load-bearing platformer PNGs for v0.1.1 close).
- `gbcMode=true` set explicitly in the harness — the GBC palette is the canonical render for this GBC-target ROM.
- D-07 sub-pixel sink closed as-accepted: diagnostic ladder found no off-by-one; snap arithmetic is locked by `PlatformerSnapArithmeticEmissionTest`; foot alignment is intended/imperceptible.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] UAT harness captured the GBC-target ROM in DMG mode → inverted palette**
- **Found during:** Task 2 (checkpoint verification of the captured anchors)
- **Issue:** `AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir=…)` wires the `.noi` symFile but does NOT enable `gbcMode` — it is an independent field defaulting to `false` (`GameboyType.DMG`). The platformer-template ROM is GBC-target, so the original Task-1 capture rendered an inverted/negative palette. The Task-1 note had misread "gbcMode via the .noi symFile" — those are independent fields.
- **Fix:** Appended `.copy(gbcMode = true)` to the session config in `newAgent()`, with an explanatory comment. ROM unchanged (clean 15:35 build reused); only the capture render mode changed. Re-ran all anchors in one pass (`--rerun-tasks`), overwriting the DMG PNGs with GBC-mode ones (newer mtime 16:14).
- **Files modified:** `gbkt-examples/.../PlatformerTemplateUatTest.kt` + regenerated evidence PNGs
- **Verification:** spotlessApply + detekt GREEN; all UAT anchor assertions GREEN; visually confirmed correct GBC palette (light-green sky, dark duck, no inversion); binding user sign-off obtained on the GBC re-shoot.
- **Committed in:** `71dd3a57`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** The fix was essential — without it the binding visual evidence would have been a DMG-inverted render of a GBC-target ROM, invalidating the sign-off. Binding sign-off was obtained on the corrected GBC-mode screenshots, not the original DMG capture. No scope creep (test-local change only).

## Issues Encountered

- The original DMG-mode capture (commit `9da75edf`) produced inverted colors; root cause identified and fixed as the deviation above. Resolved before sign-off.

## User Setup Required

None - no external service configuration required. GBDK/lcc was available locally (`/Users/michalsvacha/gbdk/bin/lcc`); the clean ROM build succeeded.

## Next Phase Readiness

- FIX-05 platformer seed cluster fully discharged: SEED-021, SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY, SEED-platformer-template-spawn-polish, and SEED-PHASE-13 are all archived with terminal dispositions.
- Criterion 1 (cEmit) + Criterion 2 (3 GBC anchors) satisfied with binding sign-off.
- Remaining Phase 21 work: plan 21-08 (re-deferrals / REQUIREMENTS update per D-03/D-04) and any remaining doc-residual plans.

## Self-Check: PASSED

- FOUND: 21-07-SUMMARY.md
- FOUND: evidence/uat-screenshots/anchor-2/01-grounded.png
- FOUND: seeds/archive/SEED-021-platformer-pivot-adjust-auto-derive.md (and 3 other archived seeds)
- FOUND commits: 9da75edf, 71dd3a57, d1b19bdd

---
*Phase: 21-codegen-fixes-platformer-and-remaining-seeds*
*Completed: 2026-06-14*
