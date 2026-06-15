---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "08"
subsystem: test-infrastructure
tags: [evidence-storage, uat, scratch-redirect, dmg-examples]
dependency_graph:
  requires: ["22-02"]
  provides: []
  affects: [gbkt-examples/simple-physics, gbkt-examples/banks]
tech_stack:
  added: []
  patterns: ["capture-to-scratch smoke (R1/R6) — agent.captureScreenshot + assertTrue(length>0)"]
key_files:
  created: []
  modified:
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsUatTest.kt
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
decisions:
  - "SimplePhysicsUatTest and BanksUatTest redirect captures to build/gbkt/screenshots (SCRATCH_DIR); no EVIDENCE_DIR"
  - "BanksUatTest side artifacts (.gbst, perceptual .txt) redirect to build/gbkt/test-evidence (EVIDENCE_SCRATCH_DIR)"
  - "captureAndRename helpers deleted in both files; agent.captureScreenshot(label) used directly"
  - "Comment references to .planning/phases also removed so grep verification passes cleanly"
metrics:
  duration: "4 min"
  completed_date: "2026-06-14"
  tasks: 2
  files: 2
---

# Phase 22 Plan 08: DMG-Example UAT Scratch Redirect Summary

Redirected SimplePhysicsUatTest and BanksUatTest captures + side artifacts off EVIDENCE_DIR onto capture-to-scratch smoke checks using `build/gbkt/screenshots` and `build/gbkt/test-evidence` gitignored directories.

## What Was Done

**Task 1 — SimplePhysicsUatTest (`a12ec8ff`)**

- Removed `EVIDENCE_DIR` companion (was pointing at archived `.planning/phases/09.4-...`)
- Added `SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/screenshots")`
- Changed `discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)` to `screenshotDir = SCRATCH_DIR`
- Replaced all three `captureAndRename(agent, label, targetName)` calls with `agent.captureScreenshot(label)` returning `File`
- Kept all `assertTrue(png.length() > 0, ...)` smoke assertions unchanged
- Deleted the now-unused `captureAndRename` private helper (43 lines removed)
- Removed comment references to `.planning/phases` so verification grep passes cleanly

**Task 2 — BanksUatTest (`d219d961`)**

- Removed `EVIDENCE_DIR` companion (was pointing at archived `.planning/phases/11-...`)
- Added `SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/screenshots")`
- Added `EVIDENCE_SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/test-evidence")`
- Changed `discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)` to `screenshotDir = SCRATCH_DIR`
- Replaced both `captureAndRename(...)` calls in anchor 1 + 2 with `agent.captureScreenshot(label)` returning `File`
- Redirected `assertScreenshotIsNonUniform` perceptual `.txt` sidecar from `EVIDENCE_DIR` to `EVIDENCE_SCRATCH_DIR`
- Redirected `anchor4-pre.gbst` save-state from `EVIDENCE_DIR` to `EVIDENCE_SCRATCH_DIR`
- Redirected `anchor4-sram-persistence.txt` from `EVIDENCE_DIR` to `EVIDENCE_SCRATCH_DIR`
- Deleted the now-unused `captureAndRename` private helper
- Removed all `.planning/phases` comment references

## Verification Results

- `grep -l "EVIDENCE_DIR" SimplePhysicsUatTest.kt BanksUatTest.kt` → exit 1 (no matches — PASS)
- `grep -l "planning/phases" SimplePhysicsUatTest.kt BanksUatTest.kt` → exit 1 (no matches — PASS)
- `./gradlew :gbkt-examples:simple-physics:spotlessApply :gbkt-examples:simple-physics:detekt` → BUILD SUCCESSFUL
- `./gradlew :gbkt-examples:simple-physics:test` → BUILD SUCCESSFUL (UAT tests skipped — ROM not built, expected)
- `./gradlew :gbkt-examples:banks:spotlessApply :gbkt-examples:banks:detekt` → BUILD SUCCESSFUL
- `./gradlew :gbkt-examples:banks:test` → BUILD SUCCESSFUL (UAT tests skipped — ROM not built, expected)

## Deviations from Plan

None — plan executed exactly as written. The `captureAndRename` helper was deleted as specified, `agent.captureScreenshot(label)` was substituted directly, all smoke assertions preserved, all side artifacts redirected.

## Known Stubs

None — no stubs or placeholder values introduced.

## Threat Flags

No new network endpoints, auth paths, file access patterns, or schema changes introduced. Test-source-only changes; DMG-only examples carry no GBC inversion risk.

## Self-Check: PASSED

- `SimplePhysicsUatTest.kt` exists: FOUND
- `BanksUatTest.kt` exists: FOUND
- Task 1 commit `a12ec8ff` exists: FOUND (git log confirms)
- Task 2 commit `d219d961` exists: FOUND (git log confirms)
