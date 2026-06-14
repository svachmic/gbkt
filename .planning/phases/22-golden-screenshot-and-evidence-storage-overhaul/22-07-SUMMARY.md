---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "07"
subsystem: test-infrastructure
tags: [golden-screenshots, visual-uat, platformer, gbcMode, evidence-migration]
dependency_graph:
  requires: ["22-01", "22-02", "22-05"]
  provides: ["platformer-template-uat-golden-migration"]
  affects: ["PlatformerTemplateUatTest", "PlatformerTemplatePhase20OracleTest", "PlatformerTemplate128UatTest"]
tech_stack:
  added: []
  patterns:
    - "assertGoldenMatch against src/test/resources/goldens/ (15+1 anchors)"
    - "compareOrBless for anchor4 settled frames"
    - "D-07 GBC-header guard: check(baseConfig.gbcMode)"
    - "captureScreenshot + length>0 + assertScreenshotIsNonUniform smoke for non-blessed 128UatTest"
key_files:
  created: []
  modified:
    - "gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt"
    - "gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplatePhase20OracleTest.kt"
    - "gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate128UatTest.kt"
    - "gbkt-examples/platformer-template/build.gradle.kts"
decisions:
  - "anchor4's VisualDiff.compareRegion HIGH/LOW OAM gate preserved intact; assertGoldenMatch calls added before it (not replacing it)"
  - "PlatformerTemplate128UatTest retains assertScreenshotIsNonUniform for smoke — function is actively called on every capture"
  - "build.gradle.kts gbcMode.set(COMPATIBLE) added explicitly due to CompileRomTask convention-always-present bug"
metrics:
  duration: "~2 sessions (context boundary mid-execution)"
  completed: "2026-06-15"
  tasks_completed: 2
  files_modified: 4
---

# Phase 22 Plan 07: Platformer-Template Visual-UAT Golden Migration Summary

Migrated all 3 platformer-template visual-UAT test classes off EVIDENCE_DIR, captureAndRename, and `.copy(gbcMode = true)`. 15+1 binding anchor frames now diff against committed goldens via `assertGoldenMatch`. anchor4's `VisualDiff.compareRegion` OAM-bounding-box gate is preserved. EVIDENCE_DIR paths (.planning/phases) are fully removed.

## Tasks Completed

| # | Task | Commit | Key Change |
|---|------|--------|------------|
| 1 | Swap PlatformerTemplateUatTest to assertGoldenMatch (15 anchors) | b57e7286 | Remove EVIDENCE_DIR; add SCRATCH_DIR + TEST_EVIDENCE_DIR; 11 assertGoldenMatch + 4 compareOrBless; compareRegion gate intact |
| 2 | Swap Phase20OracleTest to assertGoldenMatch + redirect 128UatTest to scratch smoke | fb1fd247 | Phase20Oracle: 1 assertGoldenMatch; 128UatTest: captureScreenshot smoke |
| D | Fix gbcMode propagation in build.gradle.kts (deviation) | a150d8bf | Add gbcMode.set("COMPATIBLE") so -Wm-yc reaches lcc and ROM 0x143 = 0x80 |

## Verification

- `./gradlew :gbkt-examples:platformer-template:test` — BUILD SUCCESSFUL (all tests pass or skip on ROM-absent CI)
- `grep compareRegion PlatformerTemplateUatTest.kt` — 7 occurrences (gate preserved)
- No `val EVIDENCE_DIR =`, `.copy(gbcMode`, or `File(...)` constructors pointing to `.planning/phases` remain in any of the 3 test classes
- 17 `assertGoldenMatch` call-sites in UatTest (11 regular + 1 import + header refs; active anchors confirmed 15 via plan map)
- ROM 0x143 = 0x80 (CGB_ENHANCED) after explicit `gbcMode.set("COMPATIBLE")` in build.gradle.kts

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CompileRomTask gbcMode convention always-present prevents metadata fallback**

- **Found during:** Plan verification (test suite run)
- **Issue:** `extension.gbcMode` has a Gradle convention of `"DISABLED"` (set in `GbktPlugin.kt` line 68). Because Gradle Property.isPresent() returns `true` for convention values, `CompileRomTask.compile()` always took the `gbcMode.isPresent` branch and never fell back to `readGbcModeFromMetadata(sourceDir)`. The `gbkt-build.properties` file correctly contained `gbcMode=COMPATIBLE` (written by GenerateCTask from `target(GbcTarget.GBC_COMPATIBLE)` in the DSL), but this was never read. The ROM was compiled without `-Wm-yc`, leaving 0x00 at ROM offset 0x143 instead of 0x80 (CGB_ENHANCED). The D-07 guard (`check(baseConfig.gbcMode)`) in all 3 test classes correctly detected this and threw `IllegalStateException`, failing 12 of 14 tests.
- **Fix:** Added `gbcMode.set("COMPATIBLE")` to `gbkt-examples/platformer-template/build.gradle.kts` `gbkt {}` block. This is the minimal, correct fix within 22-07 scope — the underlying `CompileRomTask.isPresent` bug (which affects all games relying on the properties fallback) is deferred to a separate issue.
- **Files modified:** `gbkt-examples/platformer-template/build.gradle.kts`
- **Commit:** a150d8bf

## Known Stubs

None — all 15+1 golden diffs are wired to committed PNG files in `src/test/resources/goldens/platformer-template/`.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes. Test files write to gitignored `build/gbkt/screenshots/` and `build/gbkt/test-evidence/` only. The D-07 guard (`check(baseConfig.gbcMode)`) added in all 3 test classes is the threat mitigation for T-22-07 (DMG ROM blessing inverted GBC platformer golden).

## Self-Check: PASSED

Files verified present:
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` — FOUND
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplatePhase20OracleTest.kt` — FOUND
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate128UatTest.kt` — FOUND
- `gbkt-examples/platformer-template/build.gradle.kts` — FOUND

Commits verified:
- b57e7286 — feat(22-07): swap PlatformerTemplateUatTest to assertGoldenMatch (15 anchors)
- fb1fd247 — feat(22-07): swap Phase20Oracle + 128UatTest to scratch-smoke (EVIDENCE_DIR removed)
- a150d8bf — fix(22-07): add explicit gbcMode=COMPATIBLE to platformer-template build config
