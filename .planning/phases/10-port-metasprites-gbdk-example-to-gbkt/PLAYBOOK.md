# Metasprites — gbkt Port Playbook

**Port of:** GBDK cross-platform metasprites example (`metasprites.c`, 309 lines)
**Phase:** 10-port-metasprites-gbdk-example-to-gbkt

---

## What this game does

The metasprites demo shows a 5-frame animated elephant sprite moving around a checkerboard
background. The elephant is built from multiple 8x8 hardware sprites composited into a
single logical "metasprite" — a technique GBDK uses to draw large sprites that exceed the
8x8 or 8x16 hardware limit.

The elephant animates through 5 frames when the player presses B. Each frame has a
different arrangement and count of tiles (the variable-length OAM descriptor model that
distinguishes metasprites from fixed-grid sprites). The elephant also supports runtime
X/Y flipping and GBC sub-palette cycling via the A button.

The background is a diagonal-stripe checkerboard pattern generated from a 1-tile 16-byte
constant repeated across the entire screen. This provides visual contrast to judge the
elephant's position and movement.

The elephant moves in all four directions using the D-pad with sub-pixel physics
(acceleration ±2 per frame, clamped at ±32 sub-pixels/frame; position stored as i16
shifted right 4 bits for screen coordinates).

---

## Controls

| Input | Behavior |
|-------|----------|
| D-pad (up/down/left/right) | Move elephant in X/Y with sub-pixel acceleration and clamping |
| A button (edge-triggered) | Advance `_rot` counter; cycles Normal → Flip-Y → Flip-XY → Flip-X (4 states via `rot & 0x3`); after every 4 presses `rot >> 2` increments the sub-palette (gray → pink → cyan → green on GBC) |
| B button (edge-triggered) | Advance `_idx` counter; wraps back to 0 after the last frame (5 frames total); each press shows the next elephant animation frame |

---

## What to expect visually

- **Boot:** Checkerboard background fills the screen. Elephant sprite appears near the
  center. Frame 0, no flip, sub-palette 0 (gray on GBC, single shade on DMG).
- **B presses:** Elephant shape changes — frame 1 looks slightly different from frame 0
  (different tile layout due to variable-length OAM per frame). After 5 presses the
  animation loops back to frame 0.
- **A presses:** Elephant orientation flips. First press: Flip-Y (top/bottom mirrored).
  Second press: Flip-XY (both axes). Third press: Flip-X (left/right mirrored). Fourth
  press: Normal again, but on GBC the elephant changes color (first palette cycle to pink).
  Every 4 A presses the sub-palette advances: gray → pink → cyan → green → gray (loop).
- **D-pad:** Elephant accelerates in the pressed direction, coasts to a stop when released.

---

## Variables of interest

| Variable | Type | Description |
|----------|------|-------------|
| `_idx` | u8 | Animation frame index (0..4). Advances on each B edge press. Wraps at `NUM_FRAMES` (5). |
| `_rot` | u8 | Rotation counter (0..15, wraps via `rot & 0xF`). Drives both flip state (`rot & 0x3`) and sub-palette (`rot >> 2`). |
| `_posX` | i16 | Sub-pixel X position (screen pixel = `posX >> 4`). |
| `_posY` | i16 | Sub-pixel Y position (screen pixel = `posY >> 4`). |
| `_spdX` | i16 | Sub-pixel X velocity (clamped ±32). |
| `_spdY` | i16 | Sub-pixel Y velocity (clamped ±32). |

---

## Scene structure

Single scene `"play"` with:
- `enter { }` — loads elephant sprite tile data; sets initial position; enables display
- `frame { }` — reads D-pad/buttons; updates sub-pixel physics; updates `_idx`/`_rot`;
  applies flip and sub-palette to metasprite OAM attributes; calls `move_metasprite_*`;
  calls `hide_sprites_range` on the OAM tail; applies deceleration

No title screen, no game-over screen.

---

## UAT behaviors (summary)

1. **B press → animation advance**: `_idx` increments on B edge; elephant frame changes visibly
2. **A press → flip cycle**: `_rot` increments on A edge; `rot & 0x3` selects one of 4 orientations
3. **A press (after wrap) → sub-palette cycle**: `rot >> 2` selects one of 4 GBC sub-palettes (requires GBC mode screenshot)

See `10-UAT.md` for full mcp_scripts and evidence paths.
