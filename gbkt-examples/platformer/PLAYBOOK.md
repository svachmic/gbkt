# Platformer

## Overview
A side-scrolling platformer with physics-based movement, gravity, variable-height jumping, coyote time, and jump buffering. Move the player sprite from the ground to the goal zone on the high platform to win. Fall off screen and lose a life; lose all 3 lives and return to title.

## How to Play
Run left and right with the d-pad. Press A to jump — the physics engine handles gravity (2px/frame²), terminal velocity (12px/frame), coyote time (6 frames after walking off an edge), and jump buffering (8 frames before landing). The ground platform (solid) prevents falling through. Two one-way mid-air platforms (mid_platform at y=88, high_platform at y=56) can be jumped through from below but landed on from above. Reach the goal zone at x[112,128) y[24,56) on top of the high platform to win. Fall below y=136 to lose a life.

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
- gameplay -> title (all 3 lives lost; resets to title, no gameover scene)
- win -> title (press START)

## Win / Lose Conditions
- **Win**: Player reaches goal zone — position x in [112, 128) AND y in [24, 56) → navigates to win scene
- **Lose**: Player falls below y=136 — loses one life, respawns at (20, 104). Loses all 3 lives → navigates to title directly (no gameover scene)

## Known Quirks
- Goal zone check uses coordinate bounds, not a dedicated collision zone actor
- Player starts at (20, 104) on the ground; respawn position is the same after each fall
- `lives` resets to 3 on gameplay scene enter — each run starts fresh
- Camera follows player horizontally with smooth-follow and a dead zone of x=16, y=8
- A button plays `jumpSfx` (HIT preset) as audio feedback; actual jump is handled by the physics system
- Landing sound (`landSfx`, BUMP preset) plays on fall death
- Coyote time: 6 frames after walking off an edge where jump still registers

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| lives | UINT8 | lives | Remaining lives (starts at 3, return to title at 0) |
