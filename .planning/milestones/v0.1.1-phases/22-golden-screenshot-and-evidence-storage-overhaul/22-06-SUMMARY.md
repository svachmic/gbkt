---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "06"
subsystem: testing
tags: [golden-screenshots, visual-uat, metasprites, GBC, assertGoldenMatch, D-07]

requires:
  - phase: 22-01
    provides: "GoldenAssertions.kt (assertGoldenMatch + compareOrBless)"
  - phase: 22-02
    provides: "AgentSessionConfig.discoverFiles auto-detects GBC from ROM 0x143"
  - phase: 22-04
    provides: "6 PNG goldens committed in gbkt-examples/metasprites/src/test/resources/goldens/metasprites/"

provides:
  - "Phase19VisualEvidenceTest: 5 anchors diffed via assertGoldenMatch against committed goldens"
  - "MetaspritePhase20OracleTest: elephant-sprite-outline-clean.png diff via assertGoldenMatch"
  - "MetaspriteUatTest: Phase-10 behavior shots redirected to SCRATCH_DIR smoke (no golden)"
  - "D-07 guard (check baseConfig.gbcMode) in all 3 GBC-target test helpers"
  - "Perceptual .txt output from MetaspritePhase20OracleTest redirected to build/gbkt/test-evidence scratch"

affects: [22-07, 22-08, 22-09, 22-10, 22-11, 22-12, 22-13, 22-14]

tech-stack:
  added: []
  patterns:
    - "assertGoldenMatch(agent, label, goldenFile, scratchDir) replaces captureAndRename + assertTrue(length > 0)"
    - "D-07 guard: check(baseConfig.gbcMode) immediately after discoverFiles, before any golden write"
    - "SCRATCH_DIR = File(System.getProperty(\"user.dir\"), \"build/gbkt/screenshots\") — gitignored scratch"
    - "Smoke-only capture: agent.captureScreenshot(label) + assertTrue(png.length() > 0) for non-blessed captures"
    - "Perceptual .txt → build/gbkt/test-evidence scratch (R3 — no text golden)"

key-files:
  created: []
  modified:
    - "gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt"
    - "gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspritePhase20OracleTest.kt"
    - "gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt"

key-decisions:
  - "Phase-10 behavior shots (behavior1/2/3) are NOT in the blessed 22-anchor set — smoke-only, no golden"
  - "D-07 guard is caller responsibility: check(baseConfig.gbcMode) before any golden write — not inside GoldenAssertions"
  - "MetaspritePhase20OracleTest perceptual .txt stays as scratch (build/gbkt/test-evidence), no text golden (R3)"
  - "newGbcAgent in MetaspriteUatTest retains @Suppress(UnusedPrivateMember) for behavior3 test"

requirements-completed: [FIX-07]

duration: 4min
completed: 2026-06-14
---

# Phase 22 Plan 06: Metasprites Visual-UAT Test Migration Summary

**3 metasprites visual-UAT test classes migrated from captureAndRename/EVIDENCE_DIR pattern to assertGoldenMatch golden diffs and SCRATCH_DIR smoke captures, with D-07 GBC-header guards added to all GBC-target helpers**

## Performance

- **Duration:** 4 min
- **Started:** 2026-06-14T21:52:42Z
- **Completed:** 2026-06-14T21:56:52Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Phase19VisualEvidenceTest: removed EVIDENCE_DIR companion, removed `.copy(gbcMode = true)`, deleted captureAndRename helper; replaced 5 capture+assertTrue sites with assertGoldenMatch against committed goldens (elephant-boot-seed004, elephant-boot-seed005-checkerboard, elephant-cyan-subpalette, elephant-gbc-colors, rom-smoke-boot)
- MetaspritePhase20OracleTest: same pattern — removed EVIDENCE_DIR, removed `.copy(gbcMode = true)`, deleted captureAndRename helper; oracle capture diffs against elephant-sprite-outline-clean.png; perceptual .txt output redirected to build/gbkt/test-evidence scratch (R3)
- MetaspriteUatTest: EVIDENCE_DIR removed, screenshotDir set to SCRATCH_DIR, captureAndRename deleted, all 3 call sites replaced with agent.captureScreenshot(label) + assertTrue(png.length() > 0); Phase-10 captures are not in the blessed anchor set so remain smoke-only
- D-07 guard added to all 3 GBC-target newGbcAgent helpers: `check(baseConfig.gbcMode) { "ROM 0x143 CGB flag not set..." }` immediately after discoverFiles call

## Task Commits

Each task was committed atomically:

1. **Task 1: Swap Phase19VisualEvidenceTest + MetaspritePhase20OracleTest to assertGoldenMatch** - `6ecca5c0` (feat)
2. **Task 2: Redirect MetaspriteUatTest captures to scratch smoke (no golden)** - `98597f89` (feat)

## Files Created/Modified
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt` — EVIDENCE_DIR removed; 5 anchor diffs via assertGoldenMatch; D-07 guard; SCRATCH_DIR
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspritePhase20OracleTest.kt` — EVIDENCE_DIR removed; assertGoldenMatch for outline golden; perceptual .txt to test-evidence scratch; D-07 guard
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt` — EVIDENCE_DIR removed; captureAndRename deleted; behavior1/2/3 captures to SCRATCH_DIR smoke; D-07 guard in newGbcAgent

## Decisions Made
- Phase-10 behavior shots are NOT in the blessed 22-anchor set (per 22-RESEARCH) — smoke-only length>0 check, no golden diff
- D-07 guard is the caller's responsibility (not inside assertGoldenMatch) — per plan design
- MetaspritePhase20OracleTest perceptual .txt artifact stays as scratch (R3 — no text golden)
- All remaining grep matches for "planning/phases", "EVIDENCE_DIR", "copy(gbcMode" are in KDoc/comments only, documenting the removed pattern — not functional code

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None. The ROM was absent (no GBDK available in CI environment), so tests assumed-skipped per their `Assumptions.assumeTrue(ROM_FILE.exists(), ...)` guards — this is the expected behavior. Compilation succeeded and all other tests in the module pass.

## Known Stubs

None. No stubs introduced — these are test-source migrations only.

## Threat Flags

No new threat surface introduced. The D-07 guard `check(baseConfig.gbcMode)` is the mitigation for T-22-06 (Spoofing — DMG ROM blessing inverted GBC golden), now present in all 3 GBC-target helpers.

## Next Phase Readiness
- Metasprites visual-UAT migration complete: 6 anchors now diff against committed goldens
- Patterns established for wave 3/4 test migrations (platformer-template + emission tests)
- No blockers

---
*Phase: 22-golden-screenshot-and-evidence-storage-overhaul*
*Completed: 2026-06-14*

## Self-Check: PASSED

- FOUND: 22-06-SUMMARY.md
- FOUND: Phase19VisualEvidenceTest.kt
- FOUND: MetaspritePhase20OracleTest.kt
- FOUND: MetaspriteUatTest.kt
- FOUND commit: 6ecca5c0 (Task 1)
- FOUND commit: 98597f89 (Task 2)
- FOUND commit: 02101a0b (metadata)
