# SEED: Phase 12 — Per-Zone Tilemap Bank Allocation

> **Triage:** RE-DEFERRED — [TRIAGE.md#SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS](.planning/phases/16-seed-triage/TRIAGE.md#SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS) · 2026-06-12

**Created:** 2026-05-23 (Phase 12.2 close — terminal close as `gaps_found`)
**Origin phase:** 12.2 (ConvertZoneTilesetsTask real-tilemap extraction via png2asset -map mode)
**Source:** Phase 12.2 SPEC §"Out of scope"
**Status:** Deferred — not active. Fires only when the cumulative-overflow guard trips.
**Routing:** Open; bound to a future inserted phase under the gbkt-analysis subsystem.
**Blast radius:** Moderate (`gbkt-analysis/BankingAnalysisPass` + `gbkt-backend-gbdk/GBDKPipelineV2.allocateZoneBanks` mirror)

## Context

Phase 12.2 emits all `_zone_<id>_tilemap.c` files with `#pragma bank 2` (shared bank),
and `BankingAnalysisPass` (Plan 12.2-08) guards against cumulative overflow at 14336
bytes (16 KB bank size minus 2 KB safety margin for headers / prologue).

Phase 12's 5 platformer-template zones sum to ~6480 bytes — well under the 14336
threshold:

| Zone | Bytes | Path |
|------|-------|------|
| world1Area1Zone | 1920 | D-01 Path B (60×32 tiles) |
| world1Area2Zone | 1920 | D-01 Path B (60×32 tiles) |
| world2Area1Zone | 1920 | D-01 Path B (60×32 tiles) |
| titleZone | 180 | D-01 Path A (20×9 tiles) |
| nextLevelZone | 180 | D-01 Path A (20×9 tiles) |
| **Total** | **6120** | bank 2 = 6120 B per regression sweep (Plan 12.2-09) |

(Note: regression sweep reports bank 2 size = 6120 B = 3×1920 + 2×180 — the math is
exact, confirming all 5 tilemaps land in bank 2 without padding/dead space.)

Adding more or larger zones could trigger the overflow:

- A 6th `tilemap()`-bearing zone of the same size (1920 B) would push to 8040 B — still
  safe.
- A single 14336-byte zone (e.g., a 32×56 or 64×28 tilemap) would consume the entire
  guarded budget, blocking all other zones from sharing bank 2.
- Two large zones together (e.g., a 12-KB and a 4-KB tilemap) trip the guard.

The current behavior is to fail the build with a clear error naming each zone's
contribution and pointing maintainers at THIS seed.

## What's Deferred

Implement per-zone tilemap bank allocation, mirroring the existing
`GBDKPipelineV2.allocateZoneBanks` bin-packing logic for tileset data. Each zone's
tilemap (or a small bin-packed bank-group) would land in its own bank, with the
`#pragma bank N` set per-zone instead of the current shared `bank 2`.

Tile-coordinate-system changes may be required IF the bank-switching cost on tilemap
access becomes a runtime concern (typically not — `_bkg_tiles_load_banked` performs a
single banked copy from ROM-to-VRAM at scene-enter, after which the tilemap is
VRAM-resident; per-frame BG access does not re-read the source tilemap bank).

## Revival Condition

`BankingAnalysisPass.checkTilemapBankOverflow` fires with `cumulative > 14336 bytes`.
The error message MUST explicitly cite this SEED by ID so the maintainer can find this
file in `.planning/seeds/`. Verify the error wording at revival time still names this
seed.

## Investigation entry points

- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt` —
  the cumulative-size guard from Plan 12.2-08 (`checkTilemapBankOverflow` or similar).
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  `allocateZoneBanks` — the bin-packing template for tileset data.
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt` —
  emits the `#pragma bank 2` directive that would need to become per-zone.

## Related artifacts

- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-08-PLAN.md` — overflow guard introduction
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-08-SUMMARY.md` — JVM test (`BankingAnalysisPassTilemapOverflowTest`)
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/evidence/regression-sweep.md` — Plan 12.2-09 bank-2 = 6120 B measurement
