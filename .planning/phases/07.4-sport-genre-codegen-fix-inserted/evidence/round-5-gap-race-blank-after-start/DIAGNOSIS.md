# GAP-RACE-BLANK-AFTER-START — Round 5 Diagnosis

**Phase:** 07.4-sport-genre-codegen-fix-inserted
**Plan:** 07.4-25 (diagnose; the fix lives in 07.4-26)
**Date:** 2026-05-11
**ROM HEAD:** 4655e7d7c95e0e44a86401270b1e810a2616af60

## Visual Symptom At HEAD

The race scene at HEAD renders a nearly-blank screen. The golden reference `02-race-entry-frame30.png`
(160x144, `unique_pixels=2`) shows only a light green background (R=230,G=248,B=218 — 99% of
pixels) with 79 dark pixels forming the "LAP:" HUD text in the top-left corner. No road tiles,
no track corridor, no car sprites are visible. After holding UP for 60 frames (`03-race-after-hold-up-60f.png`,
`unique_pixels=2`, identical to race-entry), the screen does not change — confirming both that
the BG layer is invisible and that the camera is frozen. Compare to the title screen
(`01-title-pre-start.png`, `unique_pixels=2`, 957 non-background pixels), which correctly shows
text across four lines — proving the emulator and ROM are functional; the blank symptom is
specific to the race scene's BG tile layer.

## Captured Evidence

| Artifact | Path | What it proves |
|----------|------|----------------|
| Title screenshot | 01-title-pre-start.png | unique_pixels=2; 957 dark pixels = title text rendered correctly; emulator+ROM healthy pre-START |
| Race-entry screenshot | 02-race-entry-frame30.png | unique_pixels=2; only 79 dark pixels = "LAP:" HUD only; BG tilemap not rendering |
| After-hold-up screenshot | 03-race-after-hold-up-60f.png | unique_pixels=2; identical to 02; no camera scroll, no BG change after 60 frames of UP |
| Variable trace | 04-variable-trace.txt | Title: `_current_tileset_id=255`, `_camera_x=0`, `_camera_y=0`, `_car_x=80`, `_car_y=80`, `_pool_carAi_active=0`; race-entry: `_current_tileset_id=1` (set in race_enter line 7), `_camera_x=0`, `_camera_y=0` (camera locked per H-2) |
| race_enter body | 05-race_enter_body.c | Op sequence: `_camera_target=0`, `set_bkg_data(0,3,_racing_track1_tileset)`, `SWITCH_ROM(2)`, `set_bkg_tiles(0,0,19u,19u,_zone_track1_tiles)`, `_current_tileset_id=1`, `pool_carAi_spawn`, `HIDE_SPRITES`, `_win_clear_region`, `SHOW_SPRITES`, `_raceTime=0`, `_position=1`, `_win_print_at(1u,1u,"LAP:",4u)` — NO SHOW_BKG |
| update_camera body | 06-update_camera_body.c | Camera clamp: `_camera_x = (UINT8)(rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX;` — BOTH branches evaluate to 0 |
| main body | 07-main_body.c | Has `DISPLAY_ON;` and `SHOW_SPRITES;` — **SHOW_BKG is ABSENT**; LCD enabled, sprites enabled, BG layer NEVER enabled |
| racing_tick body | 08-racing_tick_body.c | Calls `update_camera_camera()` every frame; also `update_sprites()`, joypad processing, physics — camera update runs but produces 0 |
| Scope grep counts | 09-scope-grep-counts.txt | `SHOW_BKG=0` in BOTH `race_enter` scope AND `main` scope; `set_bkg_tiles=1` in race_enter (tiles ARE written); `SWITCH_ROM=1` in race_enter (bank guard from Plan 22 intact) |
| Pixel signatures | 10-race-entry-pixel-signature.txt | 01: `unique_pixels=2`; 02: `unique_pixels=2`; 03: `unique_pixels=2`; race has 79 non-bg px vs. 957 on title — tile layer contributes zero pixels |

## Ranked Root-Cause Hypotheses

Each hypothesis cites specific evidence files. Rank 1 = most likely.

### H-1: SHOW_BKG never called — LCDC.BG bit 0 permanently 0

**Evidence:**
- `09-scope-grep-counts.txt`: `SHOW_BKG=0` in BOTH `race_enter` scope and `main` scope
- `07-main_body.c`: `DISPLAY_ON;` present, `SHOW_SPRITES;` present, `SHOW_BKG` absent
- `05-race_enter_body.c`: `set_bkg_data` + `set_bkg_tiles` called (tiles are written to VRAM), but no `SHOW_BKG`
- `10-race-entry-pixel-signature.txt`: `unique_pixels=2` on race-entry (only BG color + HUD text)

**Mechanism:**
GBDK's `DISPLAY_ON` macro sets LCDC bit 7 (LCD enable) but does NOT set LCDC bit 0 (BG enable).
`SHOW_BKG` is a separate macro that sets LCDC bit 0. Without it, the Game Boy's hardware BG
layer is never composited into the frame buffer — the BG plane stays off even though `set_bkg_tiles`
has written valid tile indices into VRAM. The tiles exist in memory but are never rendered.
This is a codegen omission: the `GbktInitCodegen` or the main()-emitting visitor never emits
`SHOW_BKG`, and the racing scene-enter codegen similarly omits it.

**Falsifiable predictions:**
- Adding `SHOW_BKG;` to `main()` after `DISPLAY_ON;` should make the BG layer visible in ALL scenes
- Post-fix race-entry screenshot should have `unique_pixels >= 4` (road tiles add color variety)
- `_current_tileset_id=1` is already set correctly in race_enter — no variable-level fix needed
- The window layer (HUD "LAP:" text) should remain visible (it is on a separate layer, not affected)

**Estimated effort:** S (single-line insertion in codegen init visitor or directly in generated main())

---

### H-2: Camera clamp expression produces constant 0 — camera permanently locked at (0,0)

**Evidence:**
- `06-update_camera_body.c` line 4: `_camera_x = (UINT8)(rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX;`
- `04-variable-trace.txt`: `_camera_x=0` at both title and (inferred) race-entry states
- `03-race-after-hold-up-60f.png`: identical to `02-race-entry-frame30.png` — no scroll after 60 UP frames

**Mechanism:**
The C expression `(UINT8)(rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX` has a cast precedence bug.
`(UINT8)` binds to the comparison `(rawX < 0u)` only — it casts a boolean (0 or 1) to UINT8, then
the outer ternary picks 0u when the cast result is nonzero, or the inner ternary when it is zero.
The net effect: when rawX > 0, both the outer AND inner branches return 0u. The camera X is
permanently locked at 0 regardless of car position. This does NOT directly cause the blank BG
(the BG would still render at SCX=0 if LCDC.BG were set), but it masks any horizontal scrolling
behavior that would normally confirm correct tile-layer rendering. Fix BG first (H-1), then
reassess camera clamp in a follow-up.

**Falsifiable predictions:**
- Fixing only H-2 (camera clamp) without fixing H-1 will produce NO visual change (BG still blank)
- Fixing H-1 first and then H-2 should produce a scrolling track as the car moves right
- After H-1 fix: `03-race-after-hold-up-60f.png` should still be identical to `02-race-entry-frame30.png` if H-2 is unfixed (camera locked at 0)

**Estimated effort:** S (fix ternary precedence in camera codegen, or DSL-level bounds expression)

---

### H-3: GBC palette uninitialized — BG tiles render but are invisible (black-on-black)

**Evidence:**
- `07-main_body.c`: `BGP_REG=0` in main scope (from `09-scope-grep-counts.txt`) — no palette write
- `10-race-entry-pixel-signature.txt`: only 2 unique pixel values (background green + dark text); if BG were rendering with black palette it would still show 2 colors but with different distribution
- Racer.kt uses `target(GbcTarget.GBC_COMPATIBLE)` — GBC palette init is separate from DMG BGP_REG

**Mechanism:**
On Game Boy Color hardware in GBC_COMPATIBLE mode, the BG color palette is set via the GBC BCPS/BCPD
registers, not the DMG BGP_REG register. If the codegen does not emit GBC palette initialization,
the BG palette defaults to undefined hardware state — on some GBC revisions this may be all-black,
making the BG layer render but invisible against a black LCD. However, this hypothesis is WEAKENED
by the fact that the emulated screen shows the background color (R=230,G=248,B=218) uniformly —
this color comes from the default Coffee-GB palette mapping for uninitialized BG, not from tile data.
The complete absence of tile-color variation (only 2 colors total) supports H-1 (BG off) over
H-3 (BG on but invisible). If BG were rendering with a black palette, we would expect 1 color
(pure black) covering the BG region, which contradicts the observed green field.

**Falsifiable predictions:**
- H-3 predicts: adding SHOW_BKG without palette init = BG visible but wrong colors OR black screen
- H-1 predicts: adding SHOW_BKG + palette already correct = BG visible with correct tileset colors
- The title screen uses only 2 colors (window-layer text only, no BG tiles) — cannot use title for palette health check

**Estimated effort:** M (requires GBC BCPS/BCPD palette codegen, not a single-line fix)

---

### H-4: OAM/sprite-table interference at frame boundary (scene-machine race condition)

**Evidence:**
- `05-race_enter_body.c`: `HIDE_SPRITES` called AFTER `pool_carAi_spawn()` — OAM writes may be
  in-flight when HIDE_SPRITES fires
- `08-racing_tick_body.c`: calls `update_sprites()` + `wait_vbl_done()` — frame timing intact
- No evidence directly supporting this; included per anti-anchoring requirement

**Mechanism:**
If HIDE_SPRITES in race_enter fires mid-frame after OAM DMA has started, it could theoretically
corrupt the BG LCDC state (HIDE_SPRITES clears LCDC bit 1, OBJ enable — not BG). In theory a
badly-timed OAM write could corrupt LCDC.BG as a side effect, but this is highly unlikely in GBDK's
well-tested OAM path. This hypothesis is considered LOW probability.

**Falsifiable predictions:**
- Moving HIDE_SPRITES before pool_carAi_spawn would not change the visual outcome if H-1 is the root cause
- This hypothesis is effectively REFUTED by the fact that scope-grep shows SHOW_BKG=0 (BG was never set ON, so it cannot be set OFF by a race)

**Estimated effort:** S (reorder ops in race_enter codegen)

---

## Recommended Fix Scope For Plan 07.4-26

**Recommended: H-1 — Add `SHOW_BKG` to main() after `DISPLAY_ON`**

The scope-grep evidence in `09-scope-grep-counts.txt` is definitive: `SHOW_BKG=0` in both
`race_enter` and `main` scopes. The tile data is being written (`set_bkg_tiles=1` in race_enter),
but LCDC bit 0 is never set. This is a single-line codegen omission with zero interaction
complexity — add `SHOW_BKG;` after `DISPLAY_ON;` in the main-init codegen visitor. The pixel-
signature evidence corroborates: 79 non-background pixels on race-entry is explainable only by
BG-off (a working tilemap would contribute 2000+ tile pixels). H-2 (camera clamp) is a real bug
but secondary — fix BG first, then the camera in a follow-up.

**Required gates for Plan 07.4-26:**
1. JVM-tier RED test BEFORE the fix lands (lock the contract — failing test against HEAD).
2. JVM-tier GREEN test AFTER the fix lands.
3. Generated-C scope-level grep gates within the relevant function body (awk brace-walk), not file-level grep counts.
4. MCP screenshot at race-entry frame >= 30 BEFORE the fix (anchors the regression).
5. MCP screenshot at race-entry frame >= 30 AFTER the fix (proves visual closure).
6. UAT-racer.md flip from `failed` -> `passed` ONLY after the post-fix screenshot visually confirms BG + sprites visible.
7. Write `.uat-verdict` sentinel.

**Acceptance for SC-1-VISUAL / SC-3-VISUAL / SC-4-VISUAL (must_haves for Plan 26):**
- SC-1-VISUAL: post-fix race-entry screenshot shows BOTH player and AI sprites at distinct screen positions.
- SC-3-VISUAL: post-fix screenshot at frame >= 30 after holding UP shows camera scroll (BG offset differs from race-entry frame 0).
- SC-4-VISUAL: post-fix race-entry screenshot has unique_pixels >= 4 (non-uniform BG with corridor pattern).

## Out-Of-Scope For Plan 07.4-26

- **H-2 (camera clamp):** `update_camera_camera` has a ternary-precedence bug that locks `_camera_x`
  permanently at 0. This must be fixed AFTER H-1 so we can actually observe scrolling behavior.
  It does NOT cause the blank screen — the BG would render at SCX=0 if LCDC.BG were set.
  Defer to a follow-up after Plan 26 confirms BG is visible.

- **H-3 (GBC palette):** The BCPS/BCPD initialization path is out of scope for Plan 26. If BG
  becomes visible post-H-1-fix but with wrong colors, PHASE 07.7 (palette system) should address it.

- **Round5DiagnosisProbe.kt:** The JVM diagnostic test class created as Plan 25 fallback artifact
  may be removed or kept as a permanent regression guard. Plan 26 decides.

## Notes For Future Verifiers

This diagnosis round confirms that visual SCs can only be proven by PNG screenshots — not by
variable assertions. `_current_tileset_id=1` was set correctly in race_enter (confirmed by both
scope-grep and variable trace), yet the BG was never visible. The variable assertion would have
passed every round; only a runtime screenshot reveals the missing LCDC.BG bit. CLAUDE.md's
Verification Methodology rule — "Variable assertions prove DSL state, NOT runtime visual outcome"
— is directly instantiated by this gap. Any future visual SC that claims "track is visible" or
"BG renders" MUST produce a PNG with `unique_pixels >= 4` as the evidence artifact. File-level
grep counts (e.g., `grep -c SHOW_BKG bank1.c`) are insufficient: scope-level grep (awk brace-walk
extracting the specific function body) is required, as demonstrated by `09-scope-grep-counts.txt`.
