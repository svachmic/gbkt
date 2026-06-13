---
phase: 19-codegen-fixes-metasprite-cluster
plan: "03"
subsystem: gbkt-examples/metasprites (test)
tags: [visual-evidence, uat, gbc-mode, metasprites, fix-01]
dependency_graph:
  requires: [19-01, 19-02]
  provides: [fix-01-evidence-pngs, rom-smoke-screenshot]
  affects: []
tech_stack:
  added: []
  patterns: [StepAgent GBC-mode UAT, captureAndRename evidence capture]
key_files:
  created:
    - gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt
    - .planning/phases/19-codegen-fixes-metasprite-cluster/evidence/SEED-004/screenshot.png
    - .planning/phases/19-codegen-fixes-metasprite-cluster/evidence/SEED-005/screenshot.png
    - .planning/phases/19-codegen-fixes-metasprite-cluster/evidence/SEED-006/screenshot.png
    - .planning/phases/19-codegen-fixes-metasprite-cluster/evidence/SEED-013/screenshot.png
    - .planning/phases/19-codegen-fixes-metasprite-cluster/evidence/ROM-smoke/screenshot.png
  modified: []
decisions:
  - "Used captureAndRename with target.parentFile.mkdirs() to create per-seed subdirs rather than pre-creating them at test start — cleaner and DRY"
  - "ROM-smoke captured in the same bootFrame test method as SEED-004/005 (same boot frame, same ROM, same scene entry) — avoids a third agent session"
  - "SEED-006 and SEED-013 captured in separate captureAndRename calls from the same sub-palette climax frame for distinct traceability per seed"
  - "JSON sidecar files committed alongside PNGs as they are produced by the harness and provide capture metadata"
metrics:
  duration: "2 min"
  completed_date: "2026-06-13"
  tasks_completed: 2
  files_created: 6
---

# Phase 19 Plan 03: Visual Evidence Capture Summary

**One-liner:** GBC-mode UAT class + five Phase-19-HEAD evidence PNGs (SEED-004/005/006/013 + ROM-smoke) captured against a clean-rebuilt metasprites ROM.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Author Phase19VisualEvidenceTest.kt | fbc81ed6 | Phase19VisualEvidenceTest.kt |
| 2 | Clean-build ROM + capture five evidence PNGs | 06676b22 | 5x screenshot.png + 5x screenshot.json |

## What Was Built

**Task 1 — Phase19VisualEvidenceTest.kt:**
GBC-mode UAT test class in package `io.github.gbkt.examples.metasprites`, modeled on `MetaspriteUatTest.kt` with three changes mandated by D-01/D-03:

1. `EVIDENCE_DIR` points at `../../.planning/phases/19-codegen-fixes-metasprite-cluster/evidence` (not Phase 10's directory).
2. `newGbcAgent()` uses `AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR).copy(gbcMode = true)` — all captures are GBC-mode.
3. `captureAndRename()` calls `target.parentFile.mkdirs()` before rename so per-seed subdirs (`SEED-004/`, etc.) are created automatically.

Two test methods:
- `bootFrame capturesSeed004And005` — 30 GBC boot frames, `waitForScene("play")`, captures SEED-004, SEED-005, and ROM-smoke from a single play frame.
- `subPaletteClimax capturesSeed006And013` — 30 GBC boot frames, 8 A-press/release cycles to rot=8 (subpal=2=cyan), 2 PPU-flush frames, captures SEED-006 and SEED-013.

**Task 2 — Evidence PNGs:**
- Clean `:gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom` ran immediately before capture (D-03 staleness gate).
- All five `Phase19VisualEvidenceTest` tests passed (BUILD SUCCESSFUL).
- Visual confirmation:
  - SEED-004/screenshot.png: elephant in gray (subpal 0) on checkerboard — tiles uncorrupted.
  - SEED-005/screenshot.png: same boot frame — checkerboard BG confirmed.
  - SEED-006/screenshot.png: elephant in **cyan** (subpal 2) — GBC color confirmed, NOT grayscale.
  - SEED-013/screenshot.png: same cyan climax frame — correct GBC OBJ palette colors confirmed.
  - ROM-smoke/screenshot.png: ROM renders correctly at HEAD.

## Verification Results

- spotlessApply: PASS
- detekt: PASS
- compileTestKotlin: PASS (BUILD SUCCESSFUL)
- :gbkt-examples:metasprites:buildRom: PASS (32 KB ROM, 2 source files, 0 errors)
- All five PNGs exist and are non-empty: PASS
- SEED-006/013 GBC color check: PASS (cyan, not grayscale — visually confirmed from PNG read)

## Deviations from Plan

None — plan executed exactly as written. No production/codegen source was modified.

## Known Stubs

None — all five PNGs are real captures from a running ROM emulated in GBC mode.

## Threat Flags

None — plan adds test source and evidence files only; no new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries.
