# Breakout

A brick-breaking game for the original Game Boy.
Move the paddle to bounce the ball into the three rows of bricks.
Clear all 30 bricks without running out of lives to win.

## How to Play

| Button | Action |
|--------|--------|
| D-pad Left / Right | Move paddle |
| START | Start game (from title) / Restart (from game over or win) |

**Objective:** Destroy all 30 bricks. You have 3 lives — each time the ball falls past the paddle, you lose one life.

## Features Demonstrated

- Two actors: `paddle` and `ball` — name inferred via `val x by actor { }`
- `u8Array(30)` global brick state array — bracket read/write syntax
- Tile-based brick collision: column/row computed from ball position using `shr 3`
- `hud()` DSL builder — score number and lives icons on the window layer
- `gotoxy()` + `print(" ")` for erasing individual brick tiles from the BG layer
- `whileOp()` loop for initializing the brick array in scene enter
- Compound operators: `bricksLeft -= 1`, `score += 10`, `ballDy *= -1`
- `ball.collides(paddle)` AABB collision for the paddle
- Sound effects: `hitSfx` (HIT), `scoreSfx` (COIN), `loseSfx` (EXPLODE), `winSfx` (POWERUP)
- 4 scenes: `title` → `game` → `win` or `gameover` → `title`
- `ROM_ONLY` cartridge config, 2 ROM banks

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:breakout:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:breakout:buildRom

# Run in emulator
./gradlew :gbkt-examples:breakout:runEmulator
```

Generated C: `gbkt-examples/breakout/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/breakout/build/gbkt/output/breakout.gb`
