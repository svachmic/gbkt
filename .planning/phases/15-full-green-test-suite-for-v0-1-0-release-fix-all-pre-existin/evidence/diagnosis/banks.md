# Diagnosis fragment — BanksUatTest (×2) — F5/F6

**Plan:** 15-03 · **Requirement:** REQ-4 · **Evidence tier:** D-03 (visual — live MCP screenshot REQUIRED)

## Symptom

Both fail `assertScreenshotIsNonUniform`:
`anchor1-play-scene` / `anchor2-tilemap: dominant colour must cover < 95% of pixels … ratio 0.994`.

## Live screenshot evidence (D-03)

Built banks ROM fresh (`:gbkt-examples:banks:buildRom`, Jun 9 13:37) and drove it live with the
MCP `gbkt-emulator` (title → START → play, 42 frames settle). Screenshots captured to
`evidence/banks-anchor1-play-scene.png` and `evidence/banks-anchor2-tilemap.png` (DMG mode — matches
the StepAgent the test uses; analysis below). Pixel analysis of the live capture:

| Metric | Full frame (160×144) | Top-left 16×16 swatch |
|--------|----------------------|------------------------|
| distinct colours | 2 | 2 |
| dominant ratio | **0.9944** | **0.5000** (perfect checker) |
| content bounding box | x[0–15] y[0–15], 128 px | (the swatch IS the content) |

The MCP `emulator_step` BG-tile dump confirms the tilemap: row0 = ` !`, row1 = `! ` — i.e. the
2×2 checker `tile0/tile1/tile1/tile0` painted at the top-left corner. (Note: GBC-mode capture
collapses the two tiles to one hue because this minimal demo sets no GBC BG palette; the test runs
DMG-default, where the checker shows two shades — DMG is the mode the assertion actually sees.)

## Root cause (provably-stale assertion — confirmed by live evidence)

banks is a **bank-switching codegen demo**. Its `playZone` is **tileset-only** (`tileset(checker.png)`,
no `tilemap()` — Banks.kt:59-61), so codegen emits a minimal **2×2** tilemap
`_zone_playZone_tilemap[4] = {0x00,0x01,0x01,0x00}` (generated `_zone_playZone_tilemap.c`),
bank-loaded from bank 2 via `_bkg_tiles_load_banked(2u,0u,0u,W,H,…)` (bank1.c:26). The banked
checker therefore renders **correctly** as a 16×16 swatch in the top-left corner — proven by the
live screenshot (swatch dominant 0.50 = a real checker) and the BG-tile dump.

The generic `assertScreenshotIsNonUniform` gate asserts the *dominant colour covers < 95% of the
WHOLE 160×144 frame*. A 16×16 swatch is at most 256/23040 ≈ **1.1%** of the frame, so the full-frame
dominant ratio is **always ≥ ~98.9%** no matter how correctly the banked tilemap renders — the
threshold is **arithmetically unsatisfiable** for this scene. The assertion tests the WRONG PREMISE
(it assumes a full-screen content scene); the banked tilemap IS visible (small, by design). This is
the F5/F6 verdict the SPEC's D-03 exists for, and matches research A4's "near-blank by design".

NOT a real render/bank-load bug: the cross-bank SWITCH_ROM load works (scene transitions title→play,
the checker swatch paints from bank 2). So NO `gbkt-backend-gbdk` codegen edit is needed — the
wave_collision_note escalation does NOT apply (and 15-04 is already complete; no Wave-2 concurrency).

## Fix Path

**`provably-stale-assertion`** — re-architect the measure to the CORRECT premise: assert the banked
checker swatch renders by applying the SAME non-uniformity gate (≥2 distinct colours AND dominant
**< 0.95** — threshold UNCHANGED) to the **painted region** (the top-left 16×16 swatch the scene
actually draws), instead of the full frame. Within the swatch dominant = 0.50 < 0.95, proving the
banked tilemap loaded and rendered. The `0.95` constant is NOT lowered and no assertion is deleted —
the gate is applied to the scope that carries the scene's intended visual.

## Codegen-touch status (D-02 input for plan 06)

**NO `gbkt-backend-gbdk` codegen edited** — test-only re-architecture in `BanksUatTest.kt`.

## Evidence ref

- Live screenshots (D-03): `evidence/banks-anchor1-play-scene.png`, `evidence/banks-anchor2-tilemap.png` (full-frame 0.9944, swatch 0.50)
- Generated tilemap: `gbkt-examples/banks/build/gbkt/generated/_zone_playZone_tilemap.c` `{0x00,0x01,0x01,0x00}` (2×2)
- Banked load: `gbkt-examples/banks/build/gbkt/generated/bank1.c:26` `_bkg_tiles_load_banked(2u,…)`
- DSL: `Banks.kt:59-61` tileset-only `playZone`
