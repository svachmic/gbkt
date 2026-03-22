# Pong

A classic two-paddle Pong game for the original Game Boy.
Player 1 controls the left paddle; the right paddle is AI-controlled.
First player to score 5 points wins.

## How to Play

| Button | Action |
|--------|--------|
| D-pad Up / Down | Move left paddle |
| START | Start game (from title) / Restart (from game over) |

**Objective:** Hit the ball past the opponent's paddle to score. First to 5 wins.

## Features Demonstrated

- Three actors: `paddle1`, `paddle2`, `ball` — name inferred via `val x by actor { }`
- Type-safe input: `dpad.up.held`, `dpad.down.held`, `buttons.start.pressed`
- Simple AI: `paddle2` tracks ball Y position at 2px/frame
- Coordinate-range collision for paddles (instead of AABB) for tighter gameplay feel
- Sound effects: `bounceSfx` (HIT), `scoreSfx` (COIN), `winSfx` (WIN)
- `print()` with `%d` format for live score display
- `delay(30)` for brief visual feedback after scoring
- Forward-declared `titleRef` breaking the circular navigation cycle
- 3 scenes: `title` → `game` → `gameover` → `title`
- `ROM_ONLY` cartridge config, 2 ROM banks

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:pong:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:pong:buildRom

# Run in emulator
./gradlew :gbkt-examples:pong:runEmulator
```

Generated C: `gbkt-examples/pong/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/pong/build/gbkt/output/pong.gb`
