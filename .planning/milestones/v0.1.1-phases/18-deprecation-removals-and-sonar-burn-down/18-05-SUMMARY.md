---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: "05"
subsystem: constraints
tags: [constants, kdoc, correctness, gbc, bitsPerPixel]
dependency_graph:
  requires: []
  provides: [DEPR-03-GBC-bitsPerPixel-corrected]
  affects: [gbkt-core/constraints]
tech_stack:
  added: []
  patterns: [KDoc-narrowed-MUST-derive-claim]
key_files:
  created: []
  modified:
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/ScreenSpec.kt
decisions:
  - "Narrowed the top-level TargetProfiles KDoc MUST-derive claim to width/height only — bitsPerPixel is a documentation constant until SEED-TARGETPROFILE-SCREEN-THREADING wires it in (v0.2.0)"
  - "Fixed contradictory ScreenSpec.kt @property bitsPerPixel example (4 for GBC → 2 for both GB and GBC) as Rule 1 deviation"
metrics:
  duration: "~2 min"
  completed: "2026-06-13"
---

# Phase 18 Plan 05: GBC bitsPerPixel Correctness Summary

**One-liner:** Corrected `GAME_BOY_COLOR_SCREEN.bitsPerPixel` from `4` to `2`, fixed GBC KDoc prose to "2 bits per pixel, color via 8 hardware palettes (4 colours each)", and narrowed the top-level "All backends MUST derive" claim to `width`/`height` only.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Correct GBC bitsPerPixel and KDoc in TargetProfiles.kt | fe0e3b0d | TargetProfiles.kt, ScreenSpec.kt |

## What Was Built

Corrected a latent documentation landmine in `TargetProfiles.GAME_BOY_COLOR_SCREEN`: the constant declared `bitsPerPixel = 4` (GBA semantics) when GBC hardware tiles are always 2bpp — GBC color comes from per-tile palette attributes, not a deeper tile pixel format.

Changes applied to `TargetProfiles.kt`:
- `bitsPerPixel = 4` → `bitsPerPixel = 2` in `GAME_BOY_COLOR_SCREEN`
- GBC constant KDoc: "4 bits per pixel" → "2 bits per pixel, color via 8 hardware palettes (4 colours each); GBC color depth comes from per-tile palette attributes, not deeper tile data"
- Object-level KDoc: narrowed "All backends and constants that need these values MUST derive" to "screen dimensions (`width`/`height`) MUST derive"; noted other fields are documentation constants until v0.2.0

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed contradictory ScreenSpec.kt @property bitsPerPixel KDoc**
- **Found during:** Task 1
- **Issue:** `ScreenSpec.kt` line 14 documented `"2 for GB, 4 for GBC with palettes"` — directly contradicting the `bitsPerPixel = 2` constant we established as correct for GBC
- **Fix:** Updated @property KDoc to `"2 for both GB and GBC — GBC color comes from per-tile palette attributes, not deeper tile data"`
- **Files modified:** `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/ScreenSpec.kt`
- **Commit:** fe0e3b0d (same commit as TargetProfiles fix)

## Verification

- **Zero production readers confirmed:** All grep hits for `bitsPerPixel` are setters in profile constructors or test fixtures — no code reads the value from a `ScreenSpec` instance. Change is byte-identical by construction.
- **`./gradlew :gbkt-core:test`**: BUILD SUCCESSFUL, 35 tasks (4 executed, 31 UP-TO-DATE)
- **KDoc no longer says "4 bits per pixel"**: confirmed
- **MUST-derive claim narrowed to width/height**: confirmed

## Known Stubs

None.

## Threat Flags

None — constants/KDoc correction only; no new network endpoints, auth paths, file access patterns, or schema changes.

## Self-Check: PASSED

- [x] `TargetProfiles.kt` exists and contains `bitsPerPixel = 2` for GBC
- [x] `ScreenSpec.kt` exists with corrected KDoc
- [x] Commit `fe0e3b0d` exists in git log
- [x] `./gradlew :gbkt-core:test` BUILD SUCCESSFUL
