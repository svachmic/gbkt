---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: "02"
subsystem: constraints / profiles
tags: [refactor, single-source-of-truth, screen-dimensions, gbkt-core, gbkt-backend-gbdk]
dependency_graph:
  requires: []
  provides: [TargetProfiles.GAME_BOY_SCREEN, TargetProfiles.GAME_BOY_COLOR_SCREEN]
  affects: [GameBoyConstants.SCREEN_WIDTH, GameBoyConstants.SCREEN_HEIGHT, GameBoyProfile.screen, GameBoyColorProfile.screen]
tech_stack:
  added: []
  patterns: [single-source-of-truth canonical presets, const-to-val derivation chain]
key_files:
  created:
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/profiles/GameBoyConstants.kt
decisions:
  - "Dropped `const val` to plain `val` on SCREEN_WIDTH/HEIGHT: ScreenSpec.width/height are not compile-time constants; zero annotation-argument uses confirmed via whole-repo grep (A4 gate); all call sites accept runtime Int"
  - "TargetProfiles object placed in io.github.gbkt.core.constraints alongside ScreenSpec for maximum discoverability; named GAME_BOY_SCREEN / GAME_BOY_COLOR_SCREEN per D-05"
metrics:
  duration_minutes: 1
  tasks_completed: 3
  files_created: 1
  files_modified: 1
  completed_date: "2026-06-12T19:12:12Z"
---

# Phase 17 Plan 02: TargetProfiles Single Source of Truth for 160x144 Summary

**One-liner:** Added `TargetProfiles.GAME_BOY_SCREEN` canonical `ScreenSpec` preset in `gbkt-core` and wired `GameBoyConstants.SCREEN_WIDTH/HEIGHT` to derive from it, establishing 160x144 as a single-source-of-truth constant (D-05).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add TargetProfiles preset in gbkt-core | c68c6864 | gbkt-core/.../constraints/TargetProfiles.kt (created) |
| 2 | Derive GameBoyConstants from the core preset | 244c6f7b | gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt (modified) |
| 3 | Compile-verify backend-gbdk tests | (no code change) | — |

## What Was Built

**`TargetProfiles.kt`** (new) — `object TargetProfiles` in `io.github.gbkt.core.constraints`:
- `GAME_BOY_SCREEN`: `ScreenSpec(160, 144, bpp=2, tile=8, bgLayers=1, palettes=false, paletteCount=0, colorsPerPalette=4)` — canonical DMG preset
- `GAME_BOY_COLOR_SCREEN`: `ScreenSpec(160, 144, bpp=4, tile=8, bgLayers=1, palettes=true, paletteCount=8, colorsPerPalette=4)` — canonical GBC preset

**`GameBoyConstants.kt`** (modified):
- Added `import io.github.gbkt.core.constraints.TargetProfiles`
- Changed `const val SCREEN_WIDTH = 160` → `val SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width`
- Changed `const val SCREEN_HEIGHT = 144` → `val SCREEN_HEIGHT = TargetProfiles.GAME_BOY_SCREEN.height`
- KDoc for both vals updated to document the derivation chain and the single-source-of-truth rationale

## Verification Results

- `:gbkt-core:compileKotlin` — BUILD SUCCESSFUL
- `:gbkt-backend-gbdk:compileKotlin` — BUILD SUCCESSFUL
- `:gbkt-backend-gbdk:test` — BUILD SUCCESSFUL (all tests pass)
- `grep -c 'TargetProfiles.GAME_BOY_SCREEN' GameBoyConstants.kt` — 4 (2 KDoc references + 2 value derivations)
- Whole-repo annotation-argument scan: zero `@Annotation(SCREEN_WIDTH)`/`@Annotation(SCREEN_HEIGHT)` usages confirmed — `const` drop is safe

## Decisions Made

1. **`const val` dropped to `val`** — `ScreenSpec.width/height` are runtime properties, not `const`-qualified compile-time values. Kotlin requires compile-time constants on the RHS of `const val`. A whole-codebase grep confirmed zero annotation arguments reference `SCREEN_WIDTH`/`SCREEN_HEIGHT`, so the consumer impact is zero.

2. **Placement in `io.github.gbkt.core.constraints`** — co-located with `ScreenSpec.kt` for discoverability. Named `TargetProfiles` (plural) to signal it is the registry object, not an additional `TargetProfile` interface implementation.

3. **GBC preset included** — `GAME_BOY_COLOR_SCREEN` was added alongside the DMG preset to make the object immediately useful for GBC paths; `GameBoyColorProfile` can derive from it in plan 17-05 literal replacement.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — pure in-process constant refactor. No new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Self-Check: PASSED

- `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt` — FOUND
- Commit `c68c6864` — FOUND
- Commit `244c6f7b` — FOUND
- `TargetProfiles.GAME_BOY_SCREEN` references in `GameBoyConstants.kt` — CONFIRMED (lines 30, 32, 38, 40)
- `:gbkt-backend-gbdk:test` — PASSED
