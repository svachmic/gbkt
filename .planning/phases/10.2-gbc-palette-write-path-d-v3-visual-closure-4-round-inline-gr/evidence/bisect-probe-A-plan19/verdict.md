# Probe A — Plan 19+20 edits onto cfe41ad7 baseline

| Signal | Value |
|---|---|
| CYAN in PNG | YES |
| CHECKER in PNG | YES |
| BCPD any slot non-zero | true |
| BCPD slot 0 first-color | 0x7FFF |
| OCPD slot 2 first-color (cyan_pal) | 0x7FFF |
| Distinct colors | 5 |
| LCDC | 0xC3 |

## Verdict

REGRESSION-NAMED: no

REGRESSION-NAMED: no — cyan persists after Plan 19+20 edits. Plan 22 may be the regression site. Proceed to Probe B (Plan 05).

## Bisect Context

- Baseline (cfe41ad7): CYAN=YES, CHECKER=YES, OCPD slot 2=0x7FFF
- After Plan 19+20 edits (7b86049f restore): CYAN=YES, CHECKER=YES

The first probe where CYAN flips from YES to NO names the regression plan.
Plans 19+20 are bundled because Plan 19 was diagnostic-only (no GBDKPipelineV2.kt change);
the actual codegen change is in Plan 20 (commit 7b86049f).

## Probe A Parameters

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline)
- **Edits applied:** Selective restore of 7b86049f (Plan 20 fix: DISPLAY_OFF prepend,
  sprite-palette hoist into main before DISPLAY_ON, LCDC reorder: SHOW_BKG + SHOW_SPRITES
  + SPRITES_8x8 before DISPLAY_ON LAST)
- **ROM:** scratch/bisect/gbkt-examples/metasprites/build/gbkt/output/metasprites.gb
- **Protocol:** rot=8 (8 A presses), GBC mode=true
