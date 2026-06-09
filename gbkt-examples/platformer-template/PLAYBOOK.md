# Platformer Template

## Overview
Platformer-genre reference port (Phase 12, D-03) — the supported successor to the archived
`gbkt-examples/platformer/`. Side-scrolling tilemap platformer with sub-pixel physics, a
6-frame walk metasprite with hflip, three gameplay levels, a banked title card, and a
banked "next level" card between levels. GBC-target (palette upload per zone). The GBDK
`platformer_template` reference C is used as a codegen-quality oracle, not a DSL style
template.

## How to Play
Press START on the title card to begin. Walk the player left/right with the D-pad and
**jump with A or UP**. Reach the level-end trigger block at the right edge of a level
(while grounded) to advance: a "next level" card appears — press START to drop into the
next level. There are three levels (world1Area1 → world1Area2 → world2Area1).

## Controls
| Scene         | Button     | Effect |
|---------------|------------|--------|
| title         | START      | Enter gameplay (level 1) |
| gameplay      | LEFT       | Walk left (held) |
| gameplay      | RIGHT      | Walk right (held) |
| gameplay      | **A or UP**| **Jump** — `_playerVy = -800` when grounded (edge-triggered on press) |
| nextLevelScene| START      | Advance to the next level's gameplay |

> **Jump is bound to A OR UP — NOT B.** This is the single most important fact for anyone
> driving this ROM via the MCP emulator. See "Jump wiring (MCP testing note)" below.

## Scene Flow
- title → (START) → gameplay
- gameplay → (reach level-end trigger while grounded) → nextLevelScene
- nextLevelScene → (START) → gameplay (next level)
- Levels cycle through the 3 gameplay zones via `_current_level % 3`.

## Win / Lose Conditions
No explicit win/lose state in the template. The level cycle wraps; the template is a
genre-mechanics reference, not a complete game.

## Jump wiring (MCP testing note) — READ BEFORE DRIVING THIS ROM

Phase 12.9 Round 1 (Plan 12.9-08) recorded a "dead jump" G3 verdict that was a **false
alarm**: the testing agent pressed **B**, which is not a jump button. Plan 12.9-08a
runtime-verified that jump works correctly. Two things to know:

1. **Button mapping.** Jump fires on `button_pressed(J_A) || button_pressed(J_UP)` while
   `_grounded != 0` (generated `main.c` jump block). Use **A** or **UP** to jump. **B does
   nothing.** This matches the framework input API: `buttons.a.pressed` / `dpad.up.pressed`.

2. **Metadata `controls` omits jump — do NOT infer jump is unwired.** The gameplay scene's
   `game_metadata.json` `controls` lists only LEFT/RIGHT (held). Jump is **absent from the
   metadata** because `platformer_physics_update()` is injected as a `RawOp`, and
   `GBDKPipelineV2.extractControls()` only walks structured ops (`IfOp`/`WhileOp`/etc.) — it
   cannot see jump inside the raw physics block. The absence is a metadata-extraction gap,
   **not** evidence that jump is missing. Verify jump by reading `_playerVy`, never by
   reading the metadata `controls` list.

3. **Rising-edge test sequence (Pitfall 9).** `button_pressed()` is true only on the
   not-held → held transition frame. To observe a jump:
   - Settle the player grounded (`grounded == 1`) — e.g. `emulator_step(frames=60)` with no
     buttons after entering gameplay.
   - Step one frame with **no** A/UP held (guarantees a clean prior frame).
   - Step one frame with A (or UP) held — `_playerVy` flips to `-800` and `grounded` → 0.

## Variables Reference
| Variable          | Type  | Description |
|-------------------|-------|-------------|
| playerX / playerY | I16   | Player position (sub-pixel 12.4 fixed-point; `>> 4` = screen pixel). |
| playerVx / playerVy| I16  | Player velocity (sub-pixel). Jump sets `playerVy = -800` (up). |
| grounded          | U8    | 1 when the player is standing on a solid tile; jump is gated on `grounded != 0`. |
| facingRot         | U8    | Sprite hflip / facing direction for the walk metasprite. |
| walkFrameIdx      | U8    | Current frame index in the 6-frame walk animation. |
| threeFrameCounter | U8    | Animation sub-counter. |

## MCP Input Scripts

### Jump — A press while grounded (binding behavior)

```
emulator_start(romFile="build/gbkt/output/platformer-template.gb", gbcMode=true)
emulator_wait_for_scene(scene="title", maxFrames=300)
emulator_step(frames=10, buttons=["start"])          # hold START long enough to transition
emulator_wait_for_scene(scene="gameplay", maxFrames=300)
emulator_step(frames=60)                              # settle: player falls and grounds (grounded=1)
emulator_read_variable("grounded")                    # expect: 1
emulator_step(frames=1)                               # clean prior frame (no A held)
emulator_step(frames=1, buttons=["a"])                # rising-edge A press while grounded
emulator_read_variable("playerVy")                    # expect: -800 (jump fired)
emulator_read_variable("grounded")                    # expect: 0 (left the ground)
emulator_screenshot(label="jump-a-grounded")
```

UP corroborates identically — swap `buttons=["a"]` for `buttons=["up"]`.
