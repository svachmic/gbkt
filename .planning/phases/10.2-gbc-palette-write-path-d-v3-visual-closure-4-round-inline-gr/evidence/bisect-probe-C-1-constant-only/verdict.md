# Sub-Probe C-1 — _gbkt_default_bg_pal constant declaration ONLY (Phase 10.2 bisect)

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

CYAN PRESERVED — C-1 constant alone does not break cyan

SAME as Probe A (cyan preserved)

## C-1 Context

This probe applies ONLY the `_gbkt_default_bg_pal[4]` constant declaration (Emission #1 of Plan 22).
No `set_bkg_palette()` call was emitted. No `bgFillCheckerboard` hoist.

Expected outcome: CYAN=YES (the constant has no runtime effect without a consumer;
SDCC should treat it as an unused static; runtime behavior identical to Probe A).

If CYAN=NO: SURPRISE FINDING — the constant declaration alone is sufficient to break cyan,
suggesting a linker or banking interaction (the array may be placed at an address that
conflicts with sprite tile VRAM or OAM region, or SDCC's placement of the unused array
is causing a bank-overflow regression).

## Probe C-1 Parameters

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **C-1 commit in scratch/bisect:** 9b04f0b7
- **Edit applied:** GBDKPipelineV2.kt: add gbktDefaultBgPalRaw val block (constant declaration only)
- **ROM:** scratch/bisect/gbkt-examples/metasprites/build/gbkt/output/metasprites.gb
- **Protocol:** 8 A-presses × 2 frames each (with release), GBC mode=true
- **Capture frame:** 50
- **PNG size:** 1452 bytes
- **LCDC at capture:** 0xC3

## Comparison with Probe A

| Signal | Probe A (+Plans 19+20) | Sub-Probe C-1 (+constant only) | Change |
|--------|------------------------|-------------------------------|--------|
| CYAN | YES | YES | SAME |
| CHECKER | YES | YES | SAME |
| BCPD slot 0 | 0x7FFF | 0x7FFF | SAME |
| OCPD slot 2 | 0x7FFF | 0x7FFF | SAME |
| Distinct colors | 5 | 5 | SAME |
