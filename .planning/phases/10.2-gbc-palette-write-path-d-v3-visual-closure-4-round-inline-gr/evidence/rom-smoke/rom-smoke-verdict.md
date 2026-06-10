# Phase 10.2 — ROM Smoke Verdict (D-16)

| Gate | Command | Log | Verdict |
|---|---|---|---|
| Primary ROM | `:gbkt-examples:metasprites:clean buildRom` | metasprites-buildrom.log | PASS |
| Stress ROM | `:gbkt-examples:metasprites-stress:clean buildRom` | metasprites-stress-buildrom.log | PASS |
| Full JVM suite | `:gbkt-backend-gbdk:test :gbkt-mcp-server:test` | full-test-suite.log | PASS |

## Stress inclusion justification

The named regression site lives in `GBDKPipelineV2.kt`, which is the banking/VRAM codegen surface covered by 10.1 D-21's stress gate. Per CONTEXT.md D-16's planner-discretion clause, stress smoke is INCLUDED.

Plan 08's fix (GBC sub-palette write-path diagnostic emission) also touches `GBDKPipelineV2.kt` — confirming the D-16 trigger condition for including the stress smoke build.

## Overall

PASS

ROM SMOKE: PASS

## ROM artifacts

| Artifact | Path | Size |
|---|---|---|
| metasprites.gb | `gbkt-examples/metasprites/build/gbkt/output/metasprites.gb` | 32,768 bytes (32 KB) |
| metasprites-stress.gb | `gbkt-examples/metasprites-stress/build/gbkt/output/metasprites-stress.gb` | 32,768 bytes (32 KB) |

## Test suite details

| Module | Tests | Failures | Errors |
|---|---|---|---|
| gbkt-backend-gbdk | 982 | 0 | 0 |
| gbkt-mcp-server | 71 | 0 | 0 |
| **Total** | **1,053** | **0** | **0** |

## Key tests confirmed GREEN

- `DV3VisualV3DiagnosticTest` — Plan 08's new diagnostic emission test
- `DV3VisualV2DiagnosticTest` — Plan 07's V2 visual test
- `DV3GbcPaletteWriteDiagnosticTest` — GBC palette write diagnostics (Plan 02/03)
- `Seed006SubPaletteSyncTest` — Sub-palette cycling correctness
- `SpritePaletteSlotEmissionTest` — Sprite palette slot emission
- `MetaspriteDescriptorEmissionTest` — Metasprite descriptor emission (Plan 08)

## Build notes

- metasprites: single-bank, ROM_ONLY, GBC_COMPATIBLE target, 32 KB
- metasprites-stress: 2-bank (MBC5 auto-upgrade), GBC_COMPATIBLE, 32 KB; 3 sprites (elephant, tiger, player), 2 scenes (play, title)
- Both builds completed in under 2 seconds each; no GBDK linker errors, no bank overflow

## Staleness gate

Per `feedback_rom_build_smoke_test_for_codegen_phases.md`: JVM tests cannot see staleness in `build/gbkt/generated/`. A clean `:buildRom` was run (with preceding `:clean`) for both examples, forcing full re-generation from the current codegen. The generated `main.c` is NOT stale. D-16 satisfied.
