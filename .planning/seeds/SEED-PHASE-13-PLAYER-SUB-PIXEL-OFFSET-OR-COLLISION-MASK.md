# SEED: Player 1-2 px Sink (sub-pixel offset or collision-mask)

> **Triage:** CONFIRMED-OPEN — [TRIAGE.md#SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK](.planning/phases/16-seed-triage/TRIAGE.md#SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK) · 2026-06-12

**Created:** 2026-05-27 (Plan 12.8-07 G3 binding gate — D-09 watchpoint CARRIED-AS-NEW-SEED)
**Origin phase:** 12.8-grass-tileset-white-pixels-diagnostic
**Source:** anchor-5/00-last-gameplay.png — user observation 2026-05-27 G3 binding gate: "Still sunk 1-2px"
**Status:** Deferred
**Routing:** Phase 13 framework-primitives work OR a dedicated sibling phase if the symptom proves entangled with the Phase 12.9 palette-inversion fix
**Blast radius:** Small — touches platformer-genre physics constants (gravity offset, ground-tile-top snap), collision-mask emission in player metasprite codegen, or sub-pixel `_playerY` rendering rounding

## Context

Plan 12.7-31 (Phase 12.7 binding gate, 2026-05-27) and Plan 12.8-07 (Phase 12.8 G3 binding gate, 2026-05-27) both surfaced the same user observation:

- Plan 12.7-31: "anchor-5/00-last-gameplay.png: 1-2 px sink concern — Re-verify once Phase 12.8 resolves G3 grass tileset. User: 'character is on a platform, maybe a pixel or two sunk, but difficult to tell if thats because of the broken environment rather than the positioning of the character'."
- Plan 12.8-07: User on anchor-5/00-last-gameplay: "Still sunk 1-2px" → recorded as CARRIED-AS-NEW-SEED per D-09 watchpoint disposition.

The symptom persists after Phase 12.7's H3 grounded-blind-trigger fix (Plan 12.7-28 emit `&& _grounded != 0` in CIf condition + Plan 12.7-29 snap-to-tile-top in `buildVerticalFootProbe`). So it is NOT the H3 trigger-fire-on-falling defect — that closed.

## Symptom

In `anchor-5/00-last-gameplay.png`, the player metasprite's foot pixels render 1-2 pixels BELOW the visual top of the ground tile they stand on. The expected behavior is the foot exactly aligned with the tile-top edge.

Whether this is:
- A sub-pixel rendering rounding issue (`_playerY` is a 12.4 fixed-point value; integer Y is `_playerY >> 4`)
- A collision-mask emission off-by-one (the hitbox extends 1-2 px below the visible sprite)
- An asset-encoding offset (the sprite PNG's foot row sits 1-2 px above the bottom edge in pixel coordinates)
- An entanglement with the Phase 12.9 palette-inversion fix (the visual ambiguity may resolve once colors are correct)

is unknown at seed creation time. The user explicitly flagged it as a CARRIED-AS-NEW-SEED rather than a BLOCK on Phase 12.8 G3 closure.

## Hypotheses

### H1: Sub-pixel rendering rounding
`_playerY >> 4` floors instead of rounds. If the player's intended Y is exactly `tile_top - sprite_height`, the floor of a slightly-too-low sub-pixel value would render 1 px below.

### H2: Collision-mask off-by-one
The `buildVerticalFootProbe` snap-to-tile-top (Plan 12.7-29) snaps `_playerY = (foot_tile_top << 4) - (sprite_height << 4)` — verify the sprite_height constant matches the visible-pixel height (not the collision-mask height, which may include 1-2 extra px below for tolerance).

### H3: Asset-encoding offset
Open the player metasprite PNG; verify the foot row is at the absolute bottom edge of the sprite bitmap. If the artist left a 1-2 px transparent gutter, the visual feet float 1-2 px above the metasprite's bottom edge, and the snap-to-tile-top logic correctly places the metasprite-bottom at tile-top — resulting in feet rendering 1-2 px above tile-top, OR if the snap goes the other direction, 1-2 px below.

## What's Deferred

A dedicated diagnostic that:
1. Re-shoots `anchor-5/00-last-gameplay.png` once Phase 12.9 palette inversion ships (visual ambiguity may resolve)
2. If still observed: inspect player metasprite PNG for top/bottom gutters
3. Verify `buildVerticalFootProbe` snap arithmetic uses visible sprite height, not collision-mask height
4. Verify `_playerY >> 4` rounding vs flooring
5. Add a JVM-tier emission test for `_player_y` initial value + snap arithmetic
6. Re-shoot to confirm

## Revival Condition

- Phase 12.9 ships and `anchor-5/00-last-gameplay.png` re-shoot still shows 1-2 px sink
- A NEW example game with a different player metasprite exhibits the same symptom
- User flags the symptom as a BLOCK on a future phase close

## Related Artifacts

- `gbkt-examples/platformer-template/res/graphics/player-*.png` (sprite asset)
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/` (physics constants)
- `gbkt-backend-gbdk/.../codegen/visitor/` (collision-mask + sprite-Y emission visitors)
- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/uat-screenshots/anchor-5/00-last-gameplay.png` (symptom capture)
- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/12.8-DIAGNOSTIC.md` §"G3 BINDING VERDICT" (CARRIED-AS-NEW-SEED disposition)
- Phase 12.7-29 + 12.7-31 SUMMARY (prior closure attempt + carry-forward)
