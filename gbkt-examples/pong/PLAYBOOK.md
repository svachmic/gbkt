# Pong

## Overview
Classic two-player Pong. Player 1 controls the left paddle via the d-pad; Player 2 is AI-controlled and tracks the ball automatically. First player to reach 5 points wins and the game transitions to the game over screen.

## How to Play
Move your paddle up and down to deflect the ball past your opponent. When the ball passes the opponent's paddle it exits the screen on their side and you score a point. The AI paddle tracks the ball center (y+8) and moves 2px per frame within screen bounds. First to score 5 points wins.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start the game |
| game | UP | Move Player 1 paddle up |
| game | DOWN | Move Player 1 paddle down |
| gameover | START | Return to title |

## Scene Flow
- title -> game (press START)
- game -> gameover (either player reaches 5 points)
- gameover -> title (press START)

## Win / Lose Conditions
- **Win**: Player 1 score reaches 5 before Player 2
- **Lose**: Player 2 (AI) score reaches 5 first
- Both outcomes transition to the gameover scene where scores are displayed

## Known Quirks
- Ball can clip through a paddle at high speeds if the paddle is at the screen edge
- AI paddle uses center tracking: it tracks `paddle2.y + 8` against `ball.y`, moving 2px/frame, clamped between y=16 and y=112
- Ball resets to center (80, 72) after each score; direction reverses toward the scoring player
- Score is displayed via `print("P1:%d    P2:%d")` at position (5, 1) — updated on each score event
- Paddle collision uses coordinate-range checks (x 4..20 for P1, x 148..156 for P2) rather than AABB hitboxes, for tighter gameplay feel

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| p1Score | UINT8 | score | Player 1 score (0-5); game ends when this reaches 5 |
| p2Score | UINT8 | score | Player 2 (AI) score (0-5); game ends when this reaches 5 |
| ballDx | INT8 | velocity | Ball horizontal direction: 1 (moving right) or -1 (moving left) |
| ballDy | INT8 | velocity | Ball vertical direction: 1 (moving down) or -1 (moving up) |
