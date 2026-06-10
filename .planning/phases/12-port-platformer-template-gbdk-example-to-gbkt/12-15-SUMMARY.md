---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 15
subsystem: testing
tags: [jvm-test, gbkt-backend-gbdk, allocateZoneBanks, buildMetadataFile, zoneTilesets, multi-tileset, bank-allocation, d-15, anti-overfitting]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "Wave-0 stub MultiTilesetAllocationTest scaffold (Plan 12-03)"
  - phase: 11.1-banks-style-zone-tileset-pipeline
    provides: "ConvertZoneTilesetsTask + _zone_<id>_tileset.c codegen path"
  - phase: 11.2-banks-zone-tileset-include-wiring
    provides: "zoneTilesets metadata manifest (sanitizedSymbol per zone id)"
  - phase: 11.3-banks-zone-tileset-bank-allocation
    provides: "allocateZoneBanks + buildTilemapBankFiles + 16 KB hard-cap validation"
provides:
  - "D-15 verification GREEN: 3-zone × 2-tileset substrate locked at JVM tier"
  - "Shared-tileset DUPLICATION GAP documented in-code (distinct sanitizedSymbol per zone)"
  - "Per-bank 16 KB hard-limit regression guard for multi-zone substrates"
  - "Future-fix marker: when Phase 13 dedup lands, this test is the right one to update"
affects:
  - "Phase 12 Wave 4 plans (12-21..12-25 — assembly + ROM smoke test)"
  - "Phase 12 Plan 26 (phase close — SEED-PHASE-12-SHARED-TILESET creation)"
  - "Future Phase 13 (shared-tileset dedup fix)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JVM-tier multi-zone substrate fixture pattern (mirrors ZoneTilemapBankingTest.buildBankingGame)"
    - "Documented-gap test pattern: assert gap EXISTS today + cite SEED for future fix"
    - "Anti-overfitting fixture: no gbkt-examples dependency, no Banks/Platformer naming"

key-files:
  created: []
  modified:
    - "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt"

key-decisions:
  - "Adopt RESEARCH §D-15 recommendation (a) — ACCEPT shared-tileset duplication for Phase 12; seed Phase 13"
  - "Document the gap via `assertTrue(symA != symB)` so a future dedup fix forces a test update (the gap will flip from distinct to shared sanitizedSymbol)"
  - "Lock per-bank 16 KB hard-cap as a separate test (regression guard against FFD-packer bugs)"
  - "Reuse ExplorationSystem + multi-scene fixture shape to escape BankingAnalysisPass single-scene HOME fast-path (Pitfall 2 mirror)"

patterns-established:
  - "Documented-gap test pattern — assert gap EXISTS today, cite SEED, flag the assertion to flip on fix"
  - "Multi-tileset substrate fixture — 3 zones × 2 tilesets with shared-tileset proof via metadata manifest"

requirements-completed: [D-15, D-overfitting-2]

# Metrics
duration: ~5min
completed: 2026-05-21
---

# Phase 12 Plan 15: Multi-Tileset Asset Pipeline Verification (D-15) Summary

**JVM-tier lock for the 3-zone × 2-tileset platformer-template substrate on the existing `allocateZoneBanks` + `buildMetadataFile` pipeline, with the shared-tileset duplication gap documented in-code for the Phase 13 dedup follow-up.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-05-21T20:35:20Z
- **Completed:** 2026-05-21T20:40:20Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Replaced the Wave-0 placeholder `MultiTilesetAllocationTest.placeholder()` with 3 substantive `@Test` methods that lock the D-15 pass-with-known-gap verdict.
- Verified `allocateZoneBanks` returns one entry per zone (3 zones) with every assignment ≥ 2 (HOME/scenes banks respected) on a 2-tileset substrate.
- Verified `buildMetadataFile` emits 3 distinct `zoneTilesets` manifest entries when 2 of those zones share a tileset PNG — proving the duplication gap exists today and acting as a future-fix marker.
- Verified per-bank `tileData` totals stay within the 16 KB hard ROM-bank capacity threshold (Phase 11 D-15 carry, mirrored at 12-CONTEXT.md §D-17).
- `./gradlew :gbkt-backend-gbdk:test` → 999 tests / 0 failures / 100% successful.

## Task Commits

Each task was committed atomically:

1. **Task 1: Fill MultiTilesetAllocationTest with 3-zone allocation verification** — `4ffe6988` (test)

_(No `docs:` metadata commit yet — orchestrator owns STATE.md / ROADMAP.md and the final docs commit.)_

## Files Created/Modified

- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt` — Replaced 17-line Wave-0 stub with 332-line D-15 verification: 3 @Test methods + substrate fixture + KDoc citing RESEARCH §D-15 + SEED-PHASE-12-SHARED-TILESET.

## Test Methods (Per-Method Detail)

### 1. `three_zones_across_two_tilesets_each_get_a_bank_assignment_at_or_above_two`

- **What it locks:** `allocateZoneBanks` returns one entry per zone (3 zones total) with every assignment ≥ 2.
- **Why ≥ 2:** Banks 0 (HOME) and 1 (scenes) are reserved per `allocateZoneBanks`'s `tilemapBankStart = 2` constant.
- **Result:** GREEN (0.001s)

### 2. `shared_tileset_produces_distinct_zone_tileset_manifest_entries_per_zone_documented_gap`

- **What it locks (positive):** `buildMetadataFile` emits 3 `zoneTilesets` entries (one per zone), and the substrate paths match the documented Phase 12 substrate verbatim (`res/graphics/world1-tileset.png` shared by Areas 1+2; `res/graphics/world2-tileset.png` for World 2).
- **What it locks (DOCUMENTED GAP):** Despite Areas 1+2 sharing the same `path`, their `sanitizedSymbol` values differ (`world1Area1Zone` vs `world1Area2Zone`). `ConvertZoneTilesetsTask` therefore invokes png2asset twice on the same PNG → two identical `_zone_<id>_tileset.c` outputs → ROM duplication. Correctness is preserved; only ROM size is doubled for the shared tileset.
- **Future-fix marker:** When the Phase 13 dedup fix lands (tracked by SEED-PHASE-12-SHARED-TILESET, created by Plan 12-26 at phase close), the `assertTrue(symA != symB)` lock will flip to `assertEquals` — making this test the natural single edit point for the dedup landing.
- **Result:** GREEN (0.022s)

### 3. `every_assigned_bank_total_tile_data_size_fits_within_16_kb_hard_limit`

- **What it locks:** Per-bank `tileData.size` totals stay ≤ 16384 bytes (16 KB hard ROM-bank capacity threshold; Phase 11 D-15 bank-layout signal mirrored at 12-CONTEXT.md §D-17).
- **Algorithm:** Sums `zoneTileDataSize` (the same heuristic used by `allocateZoneBanks` itself) per allocated bank and asserts the total respects the cap.
- **Scope:** Informational + regression guard against future FFD-packer bugs that over-commit a bank (RESEARCH §Pitfall 5 mitigation).
- **Result:** GREEN (0.004s)

## Decisions Made

- **Accepted RESEARCH §D-15 recommendation (a) for Phase 12** — the shared-tileset duplication gap is acceptable for the platformer-template port (~1-3 KB ROM overhead, well within the 2× ROM size signal threshold). Phase 13 will dedup via Plan 12-26's seed.
- **Document the gap via `assertTrue(symA != symB)`** — rather than skipping the assertion or guarding via `@Disabled`. The positive assertion makes the gap into a fail-fast signal: the moment the dedup fix lands, this test goes RED and forces a coordinated update with the new behaviour. Cited inline so the future maintainer reads exactly what to do.
- **Reuse `ExplorationSystem` + multi-scene fixture shape** — same pattern as `ZoneTilemapBankingTest` and `ZoneTilesetIncludeTest`. Avoids BankingAnalysisPass single-scene HOME fast-path collapse (Pitfall 2 mirror).
- **Use 256-byte synthetic `tileData` per zone** — exercises the allocation algorithm without bringing PNG bytes or png2asset into scope. The test stays focused on the JVM-tier contract.

## Deviations from Plan

None — plan executed exactly as written.

The plan's `acceptance_criteria` listed three test methods + a Kotlin doc comment citing `SEED-PHASE-12-SHARED-TILESET` + `RESEARCH §D-15` + `./gradlew :gbkt-backend-gbdk:test --tests "MultiTilesetAllocationTest"` exit 0. All four delivered. Tests pass 3/3.

## Issues Encountered

None.

## User Setup Required

None — pure JVM-tier change.

## Known Stubs

None.

## Threat Flags

None — JVM test only, no I/O, no network surface, no schema or trust boundary changes.

## Next Phase Readiness

- **Wave 3 (D-15) closed.** The D-15 plan slot is verified; the shared-tileset gap has a documented JVM-tier marker.
- **Plan 12-26 (phase close) must create** `SEED-PHASE-12-SHARED-TILESET.md` to track the Phase 13 dedup fix. The seed should cite this test (`MultiTilesetAllocationTest.shared_tileset_produces_distinct_zone_tileset_manifest_entries_per_zone_documented_gap`) as the lock that flips when the dedup lands.
- **No blockers for Wave 4** (12-21..12-25 assembly + ROM smoke test). The pipeline `allocateZoneBanks` + `buildMetadataFile` paths are confirmed to handle the platformer substrate correctly.

## Self-Check: PASSED

- File `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt` exists (verified post-commit).
- Commit `4ffe6988` exists in `git log --oneline` (verified post-commit).
- File contains `allocateZoneBanks` (10 references) AND `SEED-PHASE-12-SHARED-TILESET` (6 references) AND `RESEARCH §D-15` (9 references) per `<verification>` checks.
- `./gradlew :gbkt-backend-gbdk:test --tests "MultiTilesetAllocationTest"` exits 0 (3 tests / 0 failures / 0.027s total).
- `./gradlew :gbkt-backend-gbdk:test` exits 0 (999 tests / 0 failures / 100% successful — no regressions).

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Plan: 15*
*Completed: 2026-05-21*
