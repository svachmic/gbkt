# Shmup

## Overview
A vertical scrolling shoot-em-up. Pilot a space ship, shoot bullets upward to destroy descending enemy ships, and avoid being hit. Enemies spawn in waves every 60 frames. Lose all 3 lives and the game ends. There is no win condition — survive as long as possible and score as high as possible.

## How to Play
Move your ship in four directions with the d-pad. Press A to fire a bullet — limited to one bullet every 8 frames (cooldown gate). Up to 8 bullets can be active at once (bullet pool). Enemies spawn from the top of the screen every 60 frames (up to 4 active at once, enemy pool). Bullets fly upward at 4px/frame and despawn offscreen. Enemies descend at 1px/frame and despawn below the screen. Bullet-enemy collision scores 10 points. Enemy-player collision costs 1 life. Lose all 3 lives to trigger game over.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start the game |
| gameplay | UP | Move ship up (2px/frame, min y=8) |
| gameplay | DOWN | Move ship down (2px/frame, max y=128) |
| gameplay | LEFT | Move ship left (2px/frame, min x=4) |
| gameplay | RIGHT | Move ship right (2px/frame, max x=140) |
| gameplay | A | Fire bullet (if shootCooldown == 0) |
| gameover | START | Return to title (resets score=0, lives=3) |

## Scene Flow
- title -> gameplay (press START)
- gameplay -> gameover (lives reaches 0 after enemy collision)
- gameover -> title (press START)

## Win / Lose Conditions
- **Win**: No win condition — the game runs indefinitely until all lives are lost
- **Lose**: `lives` reaches 0 after enemy-player collision → gameover scene showing final score

## Known Quirks
- Fire cooldown: `shootCooldown` counts down from 8 to 0 after each shot; bullets fire only at 0
- Bullet pool max is 8; enemy pool max is 4 — excess spawn/fire attempts are silently ignored
- Enemy wave spawns at x=80, y=0 every 60 frames (`waveTimer` counter)
- Scroll simulation: `scrollY` increments every frame (background scroll, visual only)
- Player ship starts at (80, 120); enemies start at (80, 0)
- HUD shows `SC:%d LV:%d` (score and lives) at position (0, 0), updated on each score/life event
- On gameplay enter: `destroyAll(bulletPool)` and `destroyAll(enemyPool)` clear any leftover entities from a previous run

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| score | UINT8 | score | Points scored; +10 per enemy destroyed |
| lives | UINT8 | lives | Remaining lives (starts at 3, game over at 0) |
| scrollY | UINT8 | counter | Background scroll position (increments each frame, visual only) |
| shootCooldown | UINT8 | cooldown | Frames until next shot allowed; set to 8 after firing, counts to 0 |
| waveTimer | UINT8 | timer | Frames since last enemy spawn; resets to 0 and spawns at >= 60 |
