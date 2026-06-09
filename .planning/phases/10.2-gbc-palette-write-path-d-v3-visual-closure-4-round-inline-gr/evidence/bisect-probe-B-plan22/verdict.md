# Probe B — Plan 22 edits onto Probe A state (Phase 10.2 bisect)

| Signal | Value |
|---|---|
| CYAN in PNG | NO |
| CHECKER in PNG | YES |
| BCPD any slot non-zero | true |
| BCPD slot 0 first-color | 0x7FFF |
| OCPD slot 2 first-color (cyan_pal) | 0x7FFF |
| OCPD slot 2 full palette | [0x7FFF, 0x7FEA, 0x56A0, 0x2940] |
| Distinct colors | 4 |
| LCDC | 0xC3 |

## Verdict

REGRESSION-NAMED: yes

REGRESSION-NAMED: yes — CYAN flips from YES (Probe A) to NO (Probe B). Plan 22's edits
are the named regression cluster. The CHECKER is now visible (Plan 22 emission #1/#2 for
`_gbkt_default_bg_pal` + `set_bkg_palette` are working), but the sprite renders in
GRAYSCALE (0xA8A8A8, 0x505050) instead of cyan (0x50F8F8 expected from OCPD slot 2).

## Color Analysis

Probe B PNG colors (4 distinct, all GRAYSCALE):
- 0x000000 (black): 11233 pixels — BG checker dark tiles
- 0xF8F8F8 (white): 10823 pixels — BG checker light tiles
- 0xA8A8A8 (mid-gray): 637 pixels — elephant sprite area (mid-tone)
- 0x505050 (dark-gray): 347 pixels — elephant sprite area (dark-tone)

NO true cyan (criterion: R<100, G>150, B>150): all 4 colors are pure grayscale
(R=G=B within ±5). The sprite is rendering with gray shades, not with the cyan_pal
colors expected at slot 2 of OCPD.

OCPD slot 2 = [0x7FFF, 0x7FEA, 0x56A0, 0x2940] (cyan_pal IS in palette RAM).
The cyan palette is correctly loaded into OCPD slot 2. But the sprite renders
gray — indicating the GBC hardware (Coffee-GB) is reading a DIFFERENT palette slot
when compositing the sprite tiles.

BCPD slot 0 = [0x7FFF, 0x56B5, 0x294A, 0x0000] — this IS `_gbkt_default_bg_pal`.
Plan 22's explicit `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` emission IS working.
The BG palette is initialized and the checker BG is now rendering correctly.

## Candidate Regression Site Within Plan 22 Cluster

Plan 22 has 3 emissions. This probe NAMES the cluster (Plan 22) but does not
sub-narrow to the specific emission. The 3 candidates:

1. **Emission #1:** `_gbkt_default_bg_pal[4]` constant declaration — benign (data-only)
2. **Emission #2:** `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` — writes BG palette RAM
3. **Emission #3:** bgFillCheckerboard hoist (`fill_bkg_rect + set_bkg_data`) into main()
   AFTER `set_sprite_data` — writes checker bytes to tile 0 at $8000. Since LCDC.4=1
   (shared sprite/BG tile region), `set_bkg_data(0, 1, ...)` OVERWRITES the first sprite
   tile (elephant tile 0) with the checker byte pattern. The sprite tile 0 content
   changes from elephant pixels to checker pixels; but the palette is still read from
   OCPD based on OAM attribute byte (subpal=2). The GRAY appearance may come from
   `set_sprite_palette(0u, ...)` (gray_pal at slot 0) being read when subpal should be 2.

Most likely regression: **Emission #3 (bgFillCheckerboard hoist)** may interfere with
the sprite VRAM at $8000 (tile 0). OR, the OAM attribute byte `current_base_prop=2`
is being reset to 0 by some interaction with the new main() hoist sequence.

Plan 06 sub-narrow will test these 3 emissions independently if needed.

## PNG Size Comparison

| Reference | Size | Distinct Colors | Verdict |
|-----------|------|-----------------|---------|
| Current HEAD's UAT baseline (`10-port/.../behavior3-subpalette-cycle-gbc.png`) | 147 bytes | 1 (all black) | Bug state (captured before Fix visual closure) |
| Probe A (Plans 19+20) | 1452 bytes | 5 (cyan + checker) | Plans 19+20 did NOT cause regression |
| Probe B (Plan 22, frame 8) | 1493 bytes | 4 (grayscale + checker) | Plan 22 IS the regression cluster |

**Reference-comparison block:**

Probe B PNG (1493 bytes) is NOT byte-identical to the current HEAD's 147-byte all-black
baseline (`10-port/.../behavior3-subpalette-cycle-gbc.png`). The size mismatch and color
count difference (4 vs 1) indicate:

1. The 147-byte all-black baseline was captured at frame 61 with a different game state;
   our Probe B capture is at frame ~8.
2. At frame 8, the sprite IS rendering (partially) using the checker pattern from the
   hoisted `set_bkg_data` — but at frame 61, additional game logic may cause the sprite
   to be positioned outside the viewport or fully composited as black via palette slot 0.
3. The critical signal (CYAN → NO) IS reproduced: Probe B cannot produce the cyan
   sprite that Probe A produced. The REGRESSION IS NAMED.

**Conclusion:** Probe B does NOT replicate the 147-byte all-black state byte-for-byte
(SURPRISE FINDING: bisect chain partially reproduces but at different frame state).
However, the diagnostic signal is unambiguous: CYAN is gone after Plan 22, confirming
Plan 22 as the regression cluster. The 147-byte all-black at frame 61 may require the
sub-narrow (Plan 06) or a longer-run capture to fully reproduce. Named regression is valid.

## Bisect Summary

| Probe | Source | Cyan | Checker | BCPD slot 0 | OCPD slot 2 | Distinct | Verdict |
|-------|--------|------|---------|-------------|-------------|----------|---------|
| 0 (baseline) | cfe41ad7 | YES | YES | 0x7FFF (non-zero) | 0x7FFF | 5 | Sprite works, BG works (pre-Plan-19) |
| A (Plans 19+20) | +7b86049f | YES | YES | 0x7FFF | 0x7FFF | 5 | No regression in Plans 19+20 |
| B (Plan 22) | +0976e08b | NO | YES | 0x7FFF | 0x7FFF | 4 | REGRESSION NAMED: Plan 22 |

## Probe B Parameters

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **Probe B commit in scratch/bisect:** 0d4e4bb4
- **Source commit for Probe B restore:** 0976e08b (Plan 22 fix commit)
- **Restored files:** GBDKPipelineV2.kt + DV3VisualV2DiagnosticTest.kt
- **ROM:** scratch/bisect/gbkt-examples/metasprites/build/gbkt/output/metasprites.gb
- **Protocol:** 4 A-presses × steps=8, GBC mode=true
- **Capture frame:** 8 (emulator response frame counter)
- **LCDC at capture:** 0xC3 (LCD enabled, BG+Sprites active)
