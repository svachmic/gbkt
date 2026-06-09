# Sub-Probe C-4 — constant declaration + bgFillCheckerboard hoist (no set_bkg_palette) (Phase 10.2 bisect)

| Signal | Probe A | Sub-Probe C-4 | Change |
|--------|---------|---------------|--------|
| CYAN in PNG | YES | YES | SAME |
| CHECKER in PNG | YES | YES | SAME |
| BCPD any-slot non-zero | true | true | SAME |
| BCPD slot 0 first-color | 0x7FFF | 0x7FFF | SAME |
| OCPD slot 2 first-color | 0x7FFF | 0x7FFF | SAME |
| Distinct colors | 5 | 5 (inferred: PNG size = 1452) | SAME |
| LCDC | 0xC3 | 0xC3 | SAME |
| PNG size | 1452 bytes | 1452 bytes | SAME |

## Verdict

CYAN: YES

REGRESSION: NOT-NAMED at C-4. The pair (constant declaration + bgFillCheckerboard hoist) does NOT break cyan.

The minimal breaking pair is (set_bkg_palette + bgFillCheckerboard hoist).

## Bisect Chain Summary

| Sub-probe | Emissions tested | Cyan result | Status |
|-----------|-----------------|-------------|--------|
| C-1 | #1: constant only | YES | CLEARED |
| C-2 | #1+#2: constant + set_bkg_palette | YES | CLEARED |
| C-3 | #3: bgFillCheckerboard hoist only | YES | CLEARED |
| C-4 | #1+#3: constant + bgFillCheckerboard (no set_bkg_palette) | YES | CLEARED |
| Probe B | #1+#2+#3 all combined | NO | REGRESSION |

**CONCLUSION: The minimal breaking interaction is (set_bkg_palette + bgFillCheckerboard hoist).**

The constant declaration is required for `set_bkg_palette` to compile (it references
`_gbkt_default_bg_pal`) but the constant alone is dead/harmless code. The regression
occurs when BOTH Emission #2 (`set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)`) AND
Emission #3 (`fill_bkg_rect` + `set_bkg_data` bgFillCheckerboard hoist) are present together.

## C-4 Context

This probe applies:
- C-1 emission: `const palette_color_t _gbkt_default_bg_pal[4] = {0x7FFF, 0x56B5, 0x294A, 0x0000};`
  (added to paletteDataRaw via buildList in GBDKPipelineV2.kt ~line 1001)
- C-3 emission: `hoistedBgFillCheckerboardStatements` val block + `addAll(hoistedBgFillCheckerboardStatements)`
  in mainBody after `addAll(allSpriteDataLoads)`
- Does NOT apply C-2 emission: `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` via
  `hoistedDefaultBgPaletteStatements`

## BCPD Observation

BCPD slot 0 = [0x7FFF, 0x56B5, 0x294A, 0x0000] in C-4 — same as Probe A and all prior sub-probes.
This data comes from `cgb_compatibility()` initializing BCPD slot 0 to a grayscale ramp, NOT from
the constant declaration. The unused static array has no runtime effect on palette RAM.

## Probe C-4 Parameters

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **C-4 commit in scratch/bisect:** a7aacaa2
- **Test scaffolding commit in scratch/bisect:** 636c9ddf (dropped after capture)
- **Edit applied:** GBDKPipelineV2.kt:
  - Added `gbktDefaultBgPalRaw` val block + `paletteDataRaw` buildList restructuring (~lines 997-1006)
  - Added `hoistedBgFillCheckerboardStatements` val block (~lines 3714-3722)
  - Added `addAll(hoistedBgFillCheckerboardStatements)` in mainBody (~line 3762)
- **ROM:** scratch/bisect/gbkt-examples/metasprites/build/gbkt/output/metasprites.gb
- **Protocol:** 8 A-presses × 2 frames each (with release), GBC mode=true
- **Capture frame:** rot=8 (8 A-presses with release frames, GBC boot 30 frames)
- **PNG size:** 1452 bytes (byte-identical to Probe A)
- **LCDC at capture:** 0xC3

## Input to Plan 07

Plan 07 should investigate why `set_bkg_palette` + `bgFillCheckerboard` hoist together
break the OAM attribute byte / subpal selection.

- `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` writes to BCPD slot 0 (BG palette RAM)
- `fill_bkg_rect(0, 0, 20, 18, 0)` + `set_bkg_data(0, 1, _checkerboard_bg_pattern)` writes
  checker tile data to BG VRAM tile slot 0

The fix-target is the ordering/interaction of these two emissions in `buildMainFunction()`'s
mainBody in GBDKPipelineV2.kt. Options:
- Option A: Reorder — move bgFillCheckerboard hoist BEFORE allSpriteDataLoads
- Option B: Tile-slot offset — use set_bkg_data(128, ...) for checker pattern
- Option C: Remove hoist — leave bgFillCheckerboard in play_enter only
