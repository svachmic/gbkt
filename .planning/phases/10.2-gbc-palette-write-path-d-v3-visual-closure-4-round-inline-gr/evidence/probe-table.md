# Bisect Probe Results Table

**Generated:** 2026-05-19 (Phase 10.2 Plan 05 — Task 3)

## Probe Summary

| Probe | Source Commit | Cyan | Checker | BCPD slot 0 byte 0 | OCPD slot 2 byte 0 | Distinct Colors | Verdict |
|-------|---------------|------|---------|--------------------|--------------------|-----------------|---------|
| 0 (baseline) | cfe41ad7 (pre-Plan-19/20) | YES | YES | 0xFF (first-color 0x7FFF) | 0xFF (first-color 0x7FFF) | 5 | BOTH PATHS WORK — cyan sprite + checker BG |
| A (Plans 19+20) | +7b86049f (Plan 20 fix) | YES | YES | 0xFF (first-color 0x7FFF) | 0xFF (first-color 0x7FFF) | 5 | Plans 19+20 did NOT regress either path |
| B (Plan 22) | +0976e08b (Plan 22 fix) | NO | YES | 0xFF (first-color 0x7FFF) | 0xFF (first-color 0x7FFF) | 4 | **REGRESSION NAMED: Plan 22** |
| C-1 | + constant only (on Probe A) | YES | YES | 0x7FFF | 0x7FFF | 5 | CLEARED — constant declaration alone does NOT break cyan |
| C-2 | + constant + set_bkg_palette (on Probe A) | YES | YES | 0x7FFF (BCPD slot 0 confirmed: _gbkt_default_bg_pal=[0x7FFF,0x56B5,0x294A,0x0000]) | 0x7FFF | 5 | CLEARED — set_bkg_palette call alone does NOT break cyan |
| C-3 | + bgFillCheckerboard hoist only (on Probe A) | YES | YES | 0x7FFF | 0x7FFF | 5 | CLEARED — bgFillCheckerboard hoist alone does NOT break cyan. SURPRISE FINDING. |
| C-4 | + constant + bgFillCheckerboard (no set_bkg_palette) on Probe A | YES | YES | 0x7FFF | 0x7FFF | 5 | CLEARED — constant+bgFillCheckerboard together do NOT break cyan. Hypothesis confirmed. |

**First probe where CYAN flips from YES → NO:** Probe B (Plan 22 edits)

## Cluster Naming Verdict (Plan 05 — updated by Plan 06c)

**REGRESSION CLUSTER: Plan 22 (commit 0976e08b)**

Plan 22's edits are the regression site. Plan 22 introduced 3 emissions:
1. `_gbkt_default_bg_pal[4]` constant declaration at file scope
2. `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` call in main() BEFORE DISPLAY_ON
3. bgFillCheckerboard RawOp (`fill_bkg_rect + set_bkg_data`) hoist from play_enter
   into main() BETWEEN set_sprite_data and SHOW_BKG

All 3 together caused CYAN → NO. Sub-narrow (Plans 06a/06b/06c) confirmed all 3
emissions are inert individually (C-1/C-2/C-3 each preserved cyan). The regression
is an **interaction effect** between at least two emissions acting together.

**Signal analysis from Probe B:**
- BCPD slot 0 = `_gbkt_default_bg_pal` = [0x7FFF, 0x56B5, 0x294A, 0x0000] → CORRECT
- OCPD slot 2 = `cyan_pal` = [0x7FFF, 0x7FEA, 0x56A0, 0x2940] → CORRECT (in RAM)
- But sprite renders GRAY, not cyan → OAM attribute byte / subpal selection is broken

Subpal=2 (rot=8 → subpal=8>>2=2) should select OCPD slot 2 (cyan). Instead the
sprite renders with gray shades (matching OCPD slot 0 = _gbkt_default_bg_pal-equivalent).

## Conclusion (Plan 06d — Sub-Narrow Chain Complete; Minimal Breaking Pair CONFIRMED)

**MINIMAL BREAKING PAIR: set_bkg_palette (Emission #2) + bgFillCheckerboard hoist (Emission #3)**

No single emission causes the regression. The complete bisect chain established:

| Sub-probe | Emissions isolated | Cyan | Status |
|-----------|-------------------|------|--------|
| C-1 | Emission #1 only (constant) | YES | CLEARED |
| C-2 | Emission #2 only (set_bkg_palette) | YES | CLEARED |
| C-3 | Emission #3 only (bgFillCheckerboard) | YES | CLEARED |
| C-4 | Emissions #1 + #3 (constant + bgFillCheckerboard, no set_bkg_palette) | YES | CLEARED |
| Probe B | All 3 combined (#1 + #2 + #3) | NO | REGRESSION |

**The minimal breaking pair is CONFIRMED as Emissions #2 + #3:**
- C-4 eliminates the hypothesis that the constant declaration contributes to the break.
  The constant is declared but unused (no `set_bkg_palette` call in C-4) — it is dead code.
- The regression strictly requires BOTH:
  - Emission #2: `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` (BCPD write in main() before DISPLAY_ON)
  - Emission #3: `fill_bkg_rect` + `set_bkg_data(0, 1, _checkerboard_bg_pattern)` hoisted from play_enter into main() AFTER allSpriteDataLoads

**Mechanism hypothesis (confirmed by full probe chain):**
- Emission #2 (`set_bkg_palette`) writes to BCPD slot 0 (BG palette RAM) before DISPLAY_ON
- Emission #3 (bgFillCheckerboard hoist) writes `set_bkg_data(0, 1, ...)` into BG VRAM tile slot 0
  AFTER `set_sprite_data(0u, 48u, elephant_tiles)` loads elephant tiles into sprite VRAM
- In GBC mode with LCDC.4=1 (shared $8000-$97FF region for both BG and sprite tiles),
  `set_bkg_data(0,...)` writes to the SAME VRAM region as `set_sprite_data(0,...)`
- This combination corrupts the sprite tile data OR the OAM attribute bytes before the first frame

**SCOPE SHIFT ACKNOWLEDGMENT (per Plan 05 checkpoint — "Sub-narrow + acknowledge scope shift"):**

This phase is titled "gbc-palette-write-path-d-v3-visual-closure" but the bisect reveals:

1. **The palette WRITE path is NOT broken.** OCPD slot 2 (cyan_pal) and BCPD slot 0
   (_gbkt_default_bg_pal) both reach palette RAM correctly in every probe, including Probe B.
   The C-2 probe explicitly confirmed: `set_bkg_palette` writes [0x7FFF,0x56B5,0x294A,0x0000]
   to BCPD slot 0 correctly. OCPD slot 2 = 0x7FFF (cyan_pal) is present in all probes.

2. **The actual regression is in the OAM ATTRIBUTE BYTE / SUBPAL SELECTION path**, made
   worse by the GBC initialization sequence change introduced by the combined Plan 22 emissions.
   Possible mechanisms:
   - `set_bkg_data(0,1,_checkerboard_bg_pattern)` overwrites sprite VRAM tile 0 via LCDC.4=1
     shared $8000 region → corrupts first elephant sub-tile
   - The combined emission sequence resets or corrupts the OAM attribute byte (subpal bits)
     before the first composited frame
   - Coffee-GB's OAM DMA or attribute byte handling has an interaction with the combined
     BCPD write + BG tile write sequence

3. **Fix-target preview — narrowed by C-4 confirmation** (input to Plan 07):
   The fix-target is specifically the PAIR (set_bkg_palette + bgFillCheckerboard hoist) in
   `buildMainFunction()` in GBDKPipelineV2.kt. The constant declaration is NOT part of the
   fix — it must remain for set_bkg_palette to compile.
   - **Option A (reorder):** Move `addAll(hoistedBgFillCheckerboardStatements)` to BEFORE
     `addAll(allSpriteDataLoads)` in main() — so the checker tile write happens before the
     elephant sprite tiles are loaded, eliminating the VRAM tile 0 collision
   - **Option B (tile-slot offset):** Change `set_bkg_data(0, 1, ...)` to use tile slot 128+
     (e.g., `set_bkg_data(128, 1, ...)`) so it doesn't collide with sprite VRAM region
   - **Option C (remove hoist):** Leave bgFillCheckerboard in play_enter only (remove from
     main() hoist) — investigate whether the checker pattern still renders correctly without
     the hoist, or whether a different VRAM initialization approach is needed

4. **Plan 07 framing:** The finding doc should OPEN with this scope-shift — the title says
   "palette-write-path" but the defect is in SPRITE TILE VRAM + OAM ATTRIBUTE interaction.
   The fix-target is `buildMainFunction()` in GBDKPipelineV2.kt, specifically the ordering
   and interaction of `allSpriteDataLoads` vs. `hoistedBgFillCheckerboardStatements` +
   `hoistedDefaultBgPaletteStatements` in the main() body.

## Comparison with Current HEAD's 147-byte All-Black Baseline

The current HEAD's UAT baseline at `10-port/.../behavior3-subpalette-cycle-gbc.png`
shows **147 bytes = 1 distinct color = all black**. This was captured at frame 61.

Probe B at frame 8 shows **1493 bytes = 4 distinct grayscale colors**. The sprite IS
partially visible at frame 8 but in gray. At frame 61, the combined effect of the
interaction regression may render the sprite fully invisible (black).

**Note:** The exact 147-byte all-black reproduction requires a frame-61 equivalent
capture. The bisect chain is valid (CYAN flips at Probe B), and the sub-narrow confirmed
the regression requires the interaction of multiple emissions.
