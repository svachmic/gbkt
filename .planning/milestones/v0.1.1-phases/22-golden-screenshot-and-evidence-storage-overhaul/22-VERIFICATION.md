---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
verified: 2026-06-15T00:00:00Z
status: passed
score: 10/10
overrides_applied: 0
---

# Phase 22: Golden Screenshot and Evidence Storage Overhaul — Verification Report

**Phase Goal:** This milestone's visual + emission evidence is stored as durable, immutable, ROM+anchor-keyed goldens that tests compare against — not scattered per-phase scratch. The EVIDENCE_DIR-pinned-to-a-phase pattern is eliminated across all UAT/emission tests, so test runs neither regenerate archived-phase directories nor churn committed sidecars, and GBC-vs-DMG capture mode is derived from the ROM rather than configured per-test.
**Verified:** 2026-06-15
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

The ROADMAP defines 6 success criteria. Each is verified against the codebase.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | No test writes evidence into a `.planning/phases/**/evidence` path; EVIDENCE_DIR removed or redirected to `build/` in all UAT/emission test classes | VERIFIED | `grep -rn "planning/phases" --include="*.kt" /src/test/` with `File(\|resolve(` filter returns zero results. All remaining `EVIDENCE_DIR` variables resolve to `build/gbkt/test-evidence`. CleanTreeEvidenceAcceptanceTest Gate 1 + 2 lock this permanently. |
| 2 | Goldens are immutable + committed; UAT tests diff against them failing on mismatch; re-baselining is explicit opt-in only | VERIFIED | `GoldenAssertions.kt` implements `assertGoldenMatch` + `compareOrBless` with `VisualDiff.compare(tolerance=0.0)`. Missing golden throws AssertionError naming `GBKT_UPDATE_GOLDENS_PROP`. All 4 example modules wire `-Pgbkt.updateGoldens` → `systemProperty` in test task. `GoldenAssertionsTest.kt` covers all 4 paths. |
| 3 | `.planning/phases/**/evidence/` is gitignored; a clean `./gradlew test` leaves zero new untracked files and zero modified committed evidence | VERIFIED | `.gitignore` line 64: `.planning/phases/**/evidence/`. `git ls-files ".planning/phases/**/evidence/**"` returns 0. `capturedAt` removed from `ScreenshotCapture.kt` JSON sidecar (no more timestamp churn). Full test suite GREEN, git delta EMPTY post-run (confirmed by orchestrator). CleanTreeEvidenceAcceptanceTest Gate 4 locks the git ls-files check. |
| 4 | `AgentSessionConfig.discoverFiles` auto-detects GBC mode from ROM byte 0x143; `.copy(gbcMode = true)` workaround removed from all test code | VERIFIED | `AgentSessionConfig.kt` companion reads `readNBytes(0x144)` deterministically; `CGB_ENHANCED=0x80 \|\| CGB_ONLY=0xC0` → `gbcMode=true`, short ROM or 0x00 → `false`. `AgentSessionConfigTest.kt` has 4 CGB tests (0x80, 0xC0, 0x00, short). Zero live `.copy(gbcMode = true)` calls in test code (only in comments and string messages). CleanTreeEvidenceAcceptanceTest Gate 3 locks this. |
| 5 | The Phase 19/20/21 binding goldens (22 total) are migrated byte-identically into central goldens dir; archived-phase evidence removed | VERIFIED | `find gbkt-examples/*/src/test/resources/goldens -name "*.png" | wc -l` = 22 (6 metasprites + 16 platformer-template). `git ls-files ".planning/phases/**/evidence/**"` = 0. `git rm -r --cached` removed all 143 previously-tracked evidence files. CleanTreeEvidenceAcceptanceTest Gate 5 locks the count at 22. |
| 6 | TESTING.md documents the goldens layout and the re-baseline command; no stale EVIDENCE_DIR convention remains | VERIFIED | `context/TESTING.md` has "Visual Goldens" section covering: `src/test/resources/goldens/<rom>/<anchor>.png` layout, `assertGoldenMatch` helper, missing-golden error message, re-baseline command `./gradlew test -Pgbkt.updateGoldens`, GBC-header guard, gitignored scratch note. Zero references to per-phase EVIDENCE_DIR as the convention. |

**Score:** 10/10 truths verified (6 SPEC success criteria + 4 plan-level must-haves confirmed below)

### Additional Plan-Level Must-Haves

| Plan | Truth | Status | Evidence |
|------|-------|--------|----------|
| 22-01 | `assertGoldenMatch` fails on >=1 pixel diff, passes on identical, throws on missing, writes in update-mode | VERIFIED | `GoldenAssertions.kt` lines 44-124; 4 test paths in `GoldenAssertionsTest.kt` |
| 22-02 | `discoverFiles` sets gbcMode from byte 0x143 (GBC=true, DMG=false, short=false) | VERIFIED | `AgentSessionConfig.kt` companion using `readNBytes`; all 4 test cases in `AgentSessionConfigTest.kt` |
| 22-13 | After full `./gradlew test`, git status delta is empty; R1/R5/R6 grep gates automated | VERIFIED | `CleanTreeEvidenceAcceptanceTest.kt` 5-gate test (EVIDENCE_DIR→planning, File+planning, copy(gbcMode, git ls-files, 22 goldens count) passes in full suite run |
| metasprites GBC build fix | Metasprites ROM builds as GBC (0x143=0x80) so D-07 guard passes | VERIFIED | `gbkt-examples/metasprites/build.gradle.kts` line 47: `gbcMode.set("COMPATIBLE")` |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/GoldenAssertions.kt` | `assertGoldenMatch` + `compareOrBless` + `GBKT_UPDATE_GOLDENS_PROP` | VERIFIED | 151 lines, substantive; `VisualDiff.compare(tolerance=0.0)`, update-mode src-tree path remapping |
| `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/GoldenAssertionsTest.kt` | Unit tests for 4 behaviour paths | VERIFIED | Covers missing-golden, pixel-identical, mismatch, update-mode paths |
| `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt` | `discoverFiles` reads ROM 0x143 | VERIFIED | `CGB_FLAG_OFFSET = 0x143L`, `readNBytes` for deterministic read, constants `CGB_ENHANCED=0x80`, `CGB_ONLY=0xC0` |
| `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfigTest.kt` | GBC auto-detect tests | VERIFIED | 4 CGB tests added covering 0x80, 0xC0, 0x00, short-ROM cases |
| `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/ScreenshotCapture.kt` | `capturedAt` removed from JSON sidecar | VERIFIED | JSON sidecar has only `frameNumber`, `label`, `variables`, optional `debugLog` — no `capturedAt` |
| `.gitignore` | `.planning/phases/**/evidence/` rule | VERIFIED | Line 64: `.planning/phases/**/evidence/` |
| `gbkt-examples/metasprites/build.gradle.kts` | `hasProperty("gbkt.updateGoldens")` → `systemProperty` | VERIFIED | Lines 32-34; also `gbcMode.set("COMPATIBLE")` fix |
| `gbkt-examples/platformer-template/build.gradle.kts` | `hasProperty("gbkt.updateGoldens")` → `systemProperty` | VERIFIED | Lines 42-44 |
| `gbkt-examples/simple-physics/build.gradle.kts` | `hasProperty("gbkt.updateGoldens")` → `systemProperty` | VERIFIED | Lines 32-34 |
| `gbkt-examples/banks/build.gradle.kts` | `hasProperty("gbkt.updateGoldens")` → `systemProperty` | VERIFIED | Lines 33-35 |
| `gbkt-examples/metasprites/src/test/resources/goldens/metasprites/*.png` (6 files) | Byte-identical Phase 19/20 anchors | VERIFIED | 6 PNGs confirmed present (elephant-boot-seed004, seed005-checkerboard, cyan-subpalette, gbc-colors, rom-smoke-boot, sprite-outline-clean) |
| `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/*.png` (16 files) | Byte-identical Phase 21/20 anchors | VERIFIED | 16 PNGs confirmed present (anchor1-5 series + platformer-player-transparency) |
| `gbkt-examples/metasprites/src/test/kotlin/.../Phase19VisualEvidenceTest.kt` | Uses `assertGoldenMatch`, no EVIDENCE_DIR, no `.copy(gbcMode = true)` | VERIFIED | `assertGoldenMatch` import + D-07 guard + SCRATCH_DIR under build/ |
| `gbkt-examples/platformer-template/src/test/kotlin/.../PlatformerTemplateUatTest.kt` | `assertGoldenMatch` for 15 anchors, `compareRegion` preserved | VERIFIED | `assertGoldenMatch` import, `VisualDiff.compareRegion` at lines 790/806 preserved |
| `gbkt-test/src/test/kotlin/io/github/gbkt/test/CleanTreeEvidenceAcceptanceTest.kt` | 5-gate acceptance test locking R1/R5/R6 | VERIFIED | 5 `@Test` methods covering all gates; self-excluded from scans |
| `context/TESTING.md` | Visual Goldens section + re-baseline command | VERIFIED | Section "Visual Goldens" with layout, helper, missing-golden, re-baseline command, GBC-header guard |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `GoldenAssertions.kt` | `VisualDiff.compare` | `tolerance=0.0` exact diff | VERIFIED | Line 107: `VisualDiff.compare(expected=goldenFile, actual=capturedFile, tolerance=0.0, diffOutputDir=scratchDir)` |
| `GoldenAssertions.kt` | `System.getProperty(GBKT_UPDATE_GOLDENS_PROP)` | update-mode gate | VERIFIED | Line 89: `System.getProperty(GBKT_UPDATE_GOLDENS_PROP) != null` |
| `AgentSessionConfig.discoverFiles` | ROM byte 0x143 | `readNBytes` deterministic read | VERIFIED | Lines 97-107: `stream.readNBytes(buf, 0, headerLen)`, `buf[0x143].toInt() and 0xFF` |
| `Phase19VisualEvidenceTest` | `goldens/metasprites/*.png` | `assertGoldenMatch` | VERIFIED | `assertGoldenMatch` called with `javaClass.getResource`-resolved goldenFile |
| `PlatformerTemplateUatTest` | `goldens/platformer-template/*.png` | `assertGoldenMatch` | VERIFIED | `assertGoldenMatch` called for all 15 anchor frames |
| `build.gradle.kts tasks.test` | test JVM system property | `hasProperty → systemProperty` | VERIFIED | All 4 golden-bearing example modules wire `gbkt.updateGoldens` |
| `CleanTreeEvidenceAcceptanceTest` | `git ls-files` / grep gates | `ProcessBuilder` shell-out | VERIFIED | Uses 30-second bounded wait, fallback to filesystem scan |

### Data-Flow Trace (Level 4)

The phase does not produce data-display components — it produces infrastructure (golden diff helper, acceptance test, gitignore rules). Level 4 not applicable.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Zero tracked evidence files | `git ls-files ".planning/phases/**/evidence/**"` | 0 lines | PASS |
| 22 golden PNGs in test resources | `find gbkt-examples/*/src/test/resources/goldens -name "*.png" \| wc -l` | 22 | PASS |
| No File construction into .planning in test code | `grep -rn "planning/phases" --include="*.kt" /src/test/ \| grep -E "File\(\|resolve\("` | 0 results | PASS |
| No `.copy(gbcMode = true)` in live test code | `grep -rn "\.copy(gbcMode = true)" --include="*.kt" /src/test/ \| grep -v "//\| \* "` | 0 results | PASS |
| CleanTreeEvidenceAcceptanceTest exists + substantive | File present, 5 `@Test` methods | Present, 291 lines | PASS |
| Full test suite GREEN | Confirmed by orchestrator | BUILD SUCCESSFUL | PASS |

### Probe Execution

Step 7c: No conventional `scripts/*/tests/probe-*.sh` probes declared for this phase. Phase declares probes via acceptance test (`CleanTreeEvidenceAcceptanceTest`) which runs in the standard `./gradlew test` suite. SKIPPED (no standalone probe scripts).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| FIX-07 | All 14 plans | Golden evidence storage scheme — eliminate per-phase EVIDENCE_DIR, immutable goldens, GBC auto-detect, gitignore evidence dirs | SATISFIED | All 6 SPEC acceptance criteria verified against codebase; 14/14 plans complete per ROADMAP |

**Note on REQUIREMENTS.md traceability:** `REQUIREMENTS.md` shows FIX-07 as "Not planned" at the traceability table — this reflects the file not being updated when Phase 22 was added after the original milestone was closed. The ROADMAP.md correctly maps FIX-07 to Phase 22 (line 348: "Requirements: FIX-07"). This is a documentation staleness issue in REQUIREMENTS.md, not a functional gap. The ROADMAP is the authoritative source per GSD workflow design. No gap action required.

### Anti-Patterns Found

No blockers. Scan of phase-modified files:

| File | Pattern | Severity | Assessment |
|------|---------|----------|------------|
| `BgAspectDiagnosticTest.kt:66` | `.planning/phases/...` in string concat | Info | String appears in an AssertionError message (not a `File(` construction). `isPathConstructionWithPlanning` in CleanTreeEvidenceAcceptanceTest correctly excludes this — no `File(\|resolve(` on same line. Not a regression. |
| `GbktTestExtension.kt:188` | `.copy(gbcMode = true)` | Info | In `main/` source (not `test/`), not checked by R5 gate (which scans `/src/test/`). This is legitimate API usage in the GbktTestExtension integration — it reads a parameter and copies the config. Not a bug. |
| Various emission test files | `EVIDENCE_DIR` variable name retained | Info | The variable NAME is preserved for readability, but its VALUE now resolves to `build/gbkt/test-evidence`. CleanTreeEvidenceAcceptanceTest Gate 1 verifies no `EVIDENCE_DIR` assignment points to `.planning`. Acceptable per plan design. |

### Human Verification Required

No human verification required. Visual truths were confirmed via:
1. Runtime golden tests EXECUTE and PASS against committed 22-PNG baselines (metasprites Phase19 2/0, Phase20Oracle 1/0, MetaspriteUat 3/0; platformer 128Uat 6/0, Uat 5/0, Phase20Oracle 1/0) — all 0 skipped.
2. USER binding visual sign-off: cyan elephant + platformer title goldens match approved baselines (confirmed by orchestrator).
3. `CleanTreeEvidenceAcceptanceTest` automates the key clean-tree invariants.

### Gaps Summary

No gaps. All 10 must-haves verified. The REQUIREMENTS.md FIX-07 traceability staleness is a documentation-only issue (not a functional gap) and does not block milestone closure.

---

_Verified: 2026-06-15_
_Verifier: Claude (gsd-verifier)_
