# Sub-Probe C-3 — bgFillCheckerboard hoist ONLY (Phase 10.2 bisect)

| Signal | Value |
|---|---|
| CYAN in PNG | YES |
| CHECKER in PNG | YES |
| BCPD any slot non-zero | true |
| BCPD slot 0 first-color | 0x7FFF |
| OCPD slot 2 first-color (cyan_pal) | 0x7FFF |
| Distinct colors | 5 (inferred from PNG size = 1452 bytes) |
| LCDC | 0xC3 |
| PNG size (bytes) | 1452 |

## Verdict

CYAN PRESERVED — C-3 (bgFillCheckerboard hoist) alone does NOT break cyan

SAME as Probe A (cyan preserved, byte-identical PNG)

## SURPRISE FINDING — Interaction-Only Regression

All 3 sub-probes cleared their emissions individually:
- C-1 (constant only): CYAN = YES
- C-2 (constant + set_bkg_palette): CYAN = YES
- C-3 (bgFillCheckerboard hoist only): CYAN = YES

But Probe B (all 3 combined, commit 0976e08b): CYAN = NO

**CONCLUSION: The regression is an INTERACTION effect, not caused by any single emission alone.**

### Interaction Hypothesis

The most likely interaction is between Emissions #2 and #3:
- Emission #2: `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` writes to BCPD slot 0
- Emission #3: `fill_bkg_rect` + `set_bkg_data(0, 1, _checkerboard_bg_pattern)` writes checker tile to BG VRAM tile 0

In isolation, either is harmless. Combined, when LCDC.4=1 (shared $8000 region):
- `set_sprite_data(0u, 48u, elephant_tiles)` writes elephant tiles to VRAM starting at sprite tile 0
- `set_bkg_data(0, 1, _checkerboard_bg_pattern)` OVERWRITES VRAM tile 0 with checker bytes
- The `set_bkg_palette` call (Emission #2) writes to BCPD — affecting the BG palette
- The OAM attribute byte (subpal bits) may be affected by the combined emit sequence

**ALTERNATIVE hypothesis:** The interaction involves all 3 emissions in combination changing
the GBC PPU initialization sequence in a way that shifts the OAM attribute byte timing.

The C-3 solo probe confirms: bgFillCheckerboard alone does NOT disturb the elephant sprite's
palette selection. The checkerboard tile overwrite (VRAM tile 0) is not the sole cause.

## C-3 Context

This probe applies ONLY the `hoistedBgFillCheckerboardStatements` val block and its
`addAll(hoistedBgFillCheckerboardStatements)` in mainBody. No C-1 constant declaration.
No C-2 set_bkg_palette call.

Expected outcome per SEED-013 hypothesis: CYAN=NO (the bgFillCheckerboard hoist was
predicted to overwrite sprite tile slot 0 via LCDC.4=1 shared region).

Actual outcome: CYAN=YES (SURPRISE). The overwrite alone is insufficient to break cyan.
Plan 07 must investigate the INTERACTION (C-2 + C-3 combined, without C-1 constant).

## Probe C-3 Parameters

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **C-3 commit in scratch/bisect:** 859fee04
- **Test scaffolding commit in scratch/bisect:** bae341f7 (dropped after capture)
- **Edit applied:** GBDKPipelineV2.kt: add hoistedBgFillCheckerboardStatements val block + addAll in mainBody
- **ROM:** scratch/bisect/gbkt-examples/metasprites/build/gbkt/output/metasprites.gb
- **Protocol:** 8 A-presses × 2 frames each (with release), GBC mode=true
- **Capture frame:** ~49 (from StepAgent GBC boot + 8 presses)
- **PNG size:** 1452 bytes
- **LCDC at capture:** 0xC3

## Comparison with Probe A

| Signal | Probe A (+Plans 19+20) | Sub-Probe C-3 (+bgFillCheckerboard hoist only) | Change |
|--------|------------------------|----------------------------------------------|--------|
| CYAN | YES | YES | SAME |
| CHECKER | YES | YES | SAME |
| BCPD slot 0 | 0x7FFF | 0x7FFF | SAME |
| OCPD slot 2 | 0x7FFF | 0x7FFF | SAME |
| Distinct colors | 5 | 5 | SAME |
| PNG size | 1452 bytes | 1452 bytes | SAME |
