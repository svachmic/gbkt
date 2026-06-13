---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 15
subsystem: gbkt-backend-gbdk/codegen/pipeline
tags: [sonar, s3776, refactor, extract-method, gbdk-pipeline, byte-identity]
dependency_graph:
  requires: ["18-14"]
  provides: ["SONAR-01-E17", "SONAR-01-E20", "SONAR-01-E24"]
  affects: ["GBDKPipeline.kt"]
tech_stack:
  added: []
  patterns:
    - "value-returning extract-method: helpers return List<CStatement>/JSONObject/JSONArray/String"
    - "one commit + 7-example byte-identity ROM sweep per finding (D-06)"
key_files:
  created: []
  modified:
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt"
decisions:
  - "E-17: Extracted 10 section helpers from buildMetadataFile; JSON put() order preserved exactly"
  - "E-20: Extracted 4 helpers from buildMainFunction; emission ORDER preserved (palette/VRAM-sensitive per Phase 17 bisect)"
  - "E-24: Extracted buildBankedBkgTilesCallRaw from guardCrossBankBgTilemapAccess inner loop; guard remains dispatcher"
  - "No NOSONAR used for any of the 3 findings"
metrics:
  duration: 11 min
  completed: "2026-06-13"
  tasks: 3
  files: 1
---

# Phase 18 Plan 15: GBDKPipeline S3776 E-17/E-20/E-24 Extract-method Summary

Three GBDKPipeline.kt Sonar S3776 findings resolved via value-returning extract-method, with per-commit 7-example byte-identity ROM sweeps confirming zero C output change.

## Tasks Completed

| Task | Finding | Function | CC Before | Extraction | Commit |
|------|---------|----------|-----------|------------|--------|
| 1 | E-17 | `buildMetadataFile` | 22 | 10 section helpers (scenes/actors/variables/texts/terminalScenes/controls/transitions/tileDecoders/zoneTilesets/sprites) | c11aa09e |
| 2 | E-20 | `buildMainFunction` | 21 | 4 section helpers (startEnterCall/gameLoopBody/defaultBgPalette/spriteModeMacros) | 73d80d50 |
| 3 | E-24 | `guardCrossBankBgTilemapAccess` | 19 | `buildBankedBkgTilesCallRaw` extracts inner args-match logic | f03735a5 |

## Extraction Details

### E-17 `buildMetadataFile` (c11aa09e)

Decomposed 236-line method into 10 private helpers, each returning its JSON section value (`JSONObject` or `JSONArray`). The caller assembles them via `json.put()` in the same insertion order. Key: `org.json.JSONObject` 20251224 preserves insertion order, so the JSON output is byte-identical.

New helpers:
- `buildMetadataScenesJson(gameIR)` → JSONObject
- `buildMetadataActorsJson(gameIR)` → JSONArray
- `buildMetadataVariablesJson(gameIR)` → JSONArray
- `buildMetadataTextsJson(gameIR)` → JSONArray
- `buildMetadataTerminalScenesJson(gameIR)` → JSONArray
- `buildMetadataControlsJson(gameIR)` → JSONObject
- `buildMetadataTransitionsJson(gameIR)` → JSONArray
- `buildMetadataTileDecodersJson()` → JSONObject (no gameIR needed)
- `buildMetadataZoneTilesetsJson(gameIR)` → JSONArray
- `buildMetadataSpritesJson(gameIR)` → JSONArray

### E-20 `buildMainFunction` (73d80d50)

Decomposed 295-line method into 4 private helpers. **Emission ORDER preserved exactly** (palette/VRAM-sensitive per Phase 17 bisect: DISPLAY_OFF → cgb_compat → palettes → sound → VRAM → LCDC → DISPLAY_ON).

New helpers:
- `buildMainStartEnterCall(gameIR)` — start-scene enter conditional (2 branches removed from outer)
- `buildMainGameLoopBody(gameIR, frameCases, levelSwitchGuardStatements)` — per-frame loop (3 conditionals removed from outer)
- `buildMainHoistedDefaultBgPaletteStatements(gameIR)` — GBC palette guard (1 branch removed)
- `buildMainSpriteModeMacros(gameIR)` — sprite-mode macro selection (2 nested branches removed)

### E-24 `guardCrossBankBgTilemapAccess` (f03735a5)

Extracted the inner args-match block (lines 2177-2190 in original) into:
- `buildBankedBkgTilesCallRaw(op, bank, bkgTilesArgsPattern): String` — returns the `_bkg_tiles_load_banked(...)` call string. Uses elvis return for fallback (`?: return op.code`). Removes 2 nesting levels from the guard loop.

## Byte-Identity Sweep Results

**Baseline:** Captured before Task 1 from a clean 7-example build.

| Example | Task 1 | Task 2 | Task 3 |
|---------|--------|--------|--------|
| banks.gb | PASS (12c8ee2e) | PASS (12c8ee2e) | PASS (12c8ee2e) |
| breakout.gb | PASS (564465cd) | PASS (564465cd) | PASS (564465cd) |
| metasprites-stress.gb | PASS (bc51eadd) | PASS (bc51eadd) | PASS (bc51eadd) |
| metasprites.gb | PASS (9b2440db) | PASS (9b2440db) | PASS (9b2440db) |
| platformer-template.gb | PASS (9a8f268a) | PASS (9a8f268a) | PASS (9a8f268a) |
| pong.gb | PASS* (C: b5e81de7) | PASS* (C: b5e81de7) | PASS* (C: b5e81de7) |
| simple-physics.gb | PASS (247e16d2) | PASS (247e16d2) | PASS (247e16d2) |

All 6 non-pong examples byte-identical across all 3 commits. Pong PASS* — .gb hash is non-deterministic (pre-existing toolchain issue), generated main.c is byte-identical in all sweeps.

## Deviations from Plan

None — plan executed exactly as written.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. All changes are internal to `GBDKPipeline.kt` within the codegen pipeline.

## Self-Check: PASSED

- SUMMARY.md: FOUND
- Commit c11aa09e (E-17): FOUND
- Commit 73d80d50 (E-20): FOUND
- Commit f03735a5 (E-24): FOUND
- GBDKPipeline.kt modified: CONFIRMED
