# Sub-Probe C-2 — constant + set_bkg_palette emission (Phase 10.2 bisect)

| Signal | Value |
|---|---|
| CYAN in PNG | YES |
| CHECKER in PNG | YES |
| BCPD any slot non-zero | true |
| BCPD slot 0 first-color | 0x7FFF |
| OCPD slot 2 first-color (cyan_pal) | 0x7FFF |
| Distinct colors | 5 |
| LCDC | 0xC3 |
| PNG size (bytes) | 1452 |

## BCPD slot 0 full palette (bytes 0-7)

[0x7FFF, 0x56B5, 0x294A, 0x0000]

## OCPD slot 2 full palette

[0x7FFF, 0x7FEA, 0x56A0, 0x2940]

## Verdict

CYAN: YES

CYAN PRESERVED — set_bkg_palette call alone does NOT break cyan sprite rendering.
The BG palette write (BCPD slot 0) is independent of the OBJ palette (OCPD slot 2).
Proceed to Plan 06c (Emission #3 — bgFillCheckerboard hoist) as the next candidate.

## Comparison vs. Probe A

| Signal | Probe A (+Plans 19+20) | Sub-Probe C-2 (+constant +set_bkg_palette) | Change |
|--------|------------------------|-------------------------------------------|--------|
| CYAN in PNG | YES | YES | SAME |
| CHECKER in PNG | YES | YES | SAME |
| BCPD slot 0 first-color | 0x7FFF | 0x7FFF | SAME |
| OCPD slot 2 first-color (cyan_pal) | 0x7FFF | 0x7FFF | SAME |
| Distinct colors | 5 | 5 | SAME |
| LCDC | 0xC3 | 0xC3 | SAME |

## Probe C-2 Parameters

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **C-2 commit in scratch/bisect:** 85de90af (constant + set_bkg_palette)
- **Capture frame:** rot=8 (subpal=rot>>2 state)
- **Protocol:** 8 A-presses x 2 frames each (press + release), GBC mode=true
- **ROM:** scratch/bisect/gbkt-examples/metasprites/build/gbkt/output/metasprites.gb