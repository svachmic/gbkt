# Platformer GBC

## Overview
The Game Boy Color variant of the Platformer example. Identical physics gameplay to the DMG Platformer — gravity, variable-height jumping, coyote time, jump buffering — but built with the GBC_COMPATIBLE target so it renders in color mode on Game Boy Color hardware while remaining backward-compatible with the original DMG. Reach the goal zone on the high platform to win.

## How to Play
Identical to the DMG Platformer: run left and right with the d-pad, press A to jump. The physics engine handles gravity, jump force, coyote time (6 frames), and jump buffering (8 frames). Land on solid ground and one-way platforms. Reach the goal zone at x[112,128) y[24,56) on top of the high platform to win. Fall below y=136 to lose a life.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start the game |
| gameplay | LEFT | Move player left (2px/frame) |
| gameplay | RIGHT | Move player right (2px/frame) |
| gameplay | A | Jump (physics system applies jump force 8px upward) |
| win | START | Return to title |

## Scene Flow
- title -> gameplay (press START)
- gameplay -> win (reach goal zone at high platform)
- gameplay -> title (all 3 lives lost; no gameover scene)
- win -> title (press START)

## Win / Lose Conditions
- **Win**: Player reaches goal zone — position x in [112, 128) AND y in [24, 56) → win scene
- **Lose**: Player falls below y=136 — one life lost, respawn at (20, 104). All 3 lives lost → title

## Known Quirks
- GBC_COMPATIBLE target: runs in GBC color mode on GBC hardware; falls back to DMG monochrome on original Game Boy
- Game logic is byte-for-byte identical to the DMG Platformer; only the config and title text differ
- Same goal zone coordinate check: x in [112, 128) AND y in [24, 56)
- Camera smooth-follows player horizontally with dead zone x=16, y=8
- `lives` resets to 3 on each gameplay enter

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| lives | UINT8 | lives | Remaining lives (starts at 3, return to title at 0) |
