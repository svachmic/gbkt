---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "02"
subsystem: gbkt-emulator/agent
tags: [gbc-auto-detect, rom-header, agent-session-config, tdd]
dependency_graph:
  requires: [22-01]
  provides: [R5-gbc-auto-detect-in-discoverFiles]
  affects: [AgentSessionConfig, visual-UAT-tests, 22-03-onwards]
tech_stack:
  added: []
  patterns:
    - InputStream.skip(0x143) + read() for ROM CGB-flag read
    - named companion constants for magic byte values (Project Rule #1)
key_files:
  created: []
  modified:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfigTest.kt
decisions:
  - "CGB_FLAG_OFFSET=0x143L, CGB_ENHANCED=0x80, CGB_ONLY=0xC0 as companion const vals (no magic literals)"
  - "stream.read() returning -1 for short ROMs (<0x144 bytes) is safely treated as DMG (gbcMode=false) — no length guard needed"
  - "gbcMode default on data-class constructor left as false — only discoverFiles sets it from the header; manual .copy(gbcMode=true) callers still work"
metrics:
  duration: "~2 min"
  completed: "2026-06-14"
  tasks_completed: 1
  files_modified: 2
---

# Phase 22 Plan 02: GBC Auto-detect in discoverFiles Summary

**One-liner:** `AgentSessionConfig.discoverFiles` now reads ROM header byte `0x143` to set `gbcMode` automatically, eliminating the per-test `.copy(gbcMode = true)` workaround.

## What Was Built

### R5: ROM CGB-flag auto-detect in `discoverFiles`

Modified `AgentSessionConfig.kt` companion object:

- Added three named constants: `CGB_FLAG_OFFSET = 0x143L`, `CGB_ENHANCED = 0x80`, `CGB_ONLY = 0xC0` (Project Rule #1 compliance — no magic numbers)
- Inserted a 3-line CGB-flag read before the `return AgentSessionConfig(...)` block: opens an `InputStream`, skips to offset `0x143`, reads one byte; EOF (-1) for ROMs shorter than 0x144 bytes is correctly treated as DMG
- Added `gbcMode = gbcMode` to the `return` argument list

Added 4 new test cases to `AgentSessionConfigTest.kt`:
1. ROM with `byte[0x143] = 0x80` (CGB-enhanced) → `gbcMode = true`
2. ROM with `byte[0x143] = 0xC0` (GBC-only) → `gbcMode = true`
3. ROM with `byte[0x143] = 0x00` (DMG) → `gbcMode = false`
4. ROM of 64 bytes (short, `< 0x144`) → `gbcMode = false`

Pre-existing 6 test cases remain green (they use `ByteArray(64)` ROMs which are short → `gbcMode = false` by EOF path).

## TDD Gate Compliance

- RED: `test(22-02)` commit `28bad061` — 2 new assertions fail (0x80 / 0xC0 cases), 2 pass incidentally (DMG / short-ROM default false)
- GREEN: `feat(22-02)` commit `59c46761` — all 10 tests pass; `spotlessApply` + `detekt` clean

## Verification

```
./gradlew :gbkt-emulator:test          # 10/10 PASS (full module suite)
grep -c "0x143\|CGB_FLAG_OFFSET" ...AgentSessionConfig.kt  # → 3 (offset + compare)
./gradlew :gbkt-emulator:detekt        # CLEAN
```

## Deviations from Plan

None — plan executed exactly as written. Constants placed in companion object as specified. No new attack surface (reads a local ROM file that was already trusted for emulation).

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The ROM header read adds no new trust boundary — `romFile` was already fully read by the emulator. T-22-02 (DMG ROM masquerading as GBC test input) is mitigated: `discoverFiles` derives mode from the actual ROM header, not per-test configuration.

## Known Stubs

None. All four test cases are wired to actual behavior.

## Self-Check: PASSED

- AgentSessionConfig.kt: FOUND
- AgentSessionConfigTest.kt: FOUND
- 22-02-SUMMARY.md: FOUND
- Commit 28bad061 (RED): FOUND
- Commit 59c46761 (GREEN): FOUND
