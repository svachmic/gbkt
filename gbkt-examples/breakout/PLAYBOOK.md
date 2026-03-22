# Breakout

## Overview
Single-player brick-breaking game. Move the paddle left and right to keep the ball in play and break all 30 bricks arranged in three rows. Lose a life when the ball falls below the paddle; lose all 3 lives and the game is over. Clear all bricks to win.

## How to Play
Use the d-pad to slide the paddle horizontally across the bottom of the screen. The ball bounces off walls, the paddle, and bricks. Each brick hit scores 10 points and destroys that brick. When the ball falls below the paddle you lose a life and the ball resets above the paddle. Destroy all 30 bricks to win. Run out of lives (starting at 3) to get game over.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start the game |
| game | LEFT | Move paddle left (3px/frame) |
| game | RIGHT | Move paddle right (3px/frame) |
| gameover | START | Return to title |
| win | START | Return to title |

## Scene Flow
- title -> game (press START)
- game -> gameover (all 3 lives lost)
- game -> win (all 30 bricks destroyed)
- gameover -> title (press START)
- win -> title (press START)

## Win / Lose Conditions
- **Win**: `bricksLeft` reaches 0 — all 30 bricks destroyed; transitions to win scene showing final score
- **Lose**: `lives` reaches 0 — all 3 lives exhausted; transitions to gameover scene showing final score

## Known Quirks
- Brick grid occupies pixel region x[40,120) y[24,48) — exactly 3 rows x 10 columns of 8px tiles
- Brick collision is tile-based: `col = (ball.x - 40) >> 3`, `row = (ball.y - 24) >> 3`, `idx = row * 10 + col`
- Bricks are drawn as `#` characters on the BG tile layer; a hit brick is erased with a space character via `gotoxy` + `print(" ")`
- Ball bounces off top wall at y=16 (below HUD), left wall at x=4, right wall at x=152
- Paddle is clamped between x=3 and x=136; paddle spans 24px wide
- HUD displays `SCORE:%d  LIVES:%d` at position (2, 1) on the BG layer, updated after every life lost or score event
- A HUD panel using the `hud()` DSL also renders a score number and lives icon bar on the window layer

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| score | UINT8 | score | Player score; +10 per brick destroyed |
| lives | UINT8 | lives | Remaining lives (starts at 3, game over at 0) |
| bricksLeft | UINT8 | counter | Remaining bricks (starts at 30, win at 0) |
| ballDx | INT8 | velocity | Ball horizontal direction: 1 (right) or -1 (left) |
| ballDy | INT8 | velocity | Ball vertical direction: -1 (up, initial) or 1 (down) |
| bc | UINT8 | temp | Temporary: brick column index during collision |
| brow | UINT8 | temp | Temporary: brick row index during collision |
| bidx | UINT8 | temp | Temporary: flat brick array index (brow * 10 + bc) |
