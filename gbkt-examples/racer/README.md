# Racer

A top-down racing game for Game Boy Color demonstrating the gbkt sport genre package.

Race around a circuit track, complete 3 laps, beat the AI rival.

## How to Play

| Button | Action |
|--------|--------|
| D-pad Up/Down   | Accelerate / Brake |
| D-pad Left/Right | Steer |
| START           | Start race / Return to title |

**Objective:** Complete 3 laps of the circuit track. Your final position and time are shown on the results screen.

## Features Demonstrated

- `racing("track1")` from `gbkt-genre-sport` — complete racing DSL setup
- `mode(RacingMode.AI_OPPONENT)` — single player vs AI rival
- `laps(3)` — 3-lap race
- `track("circuit") { waypoint(...) }` — 4-waypoint oval circuit with checkpoints
- `vehicle("car_player") { stats { speed(200); acceleration(160); handling(180) } }` — player vehicle stats
- `vehicle("car_ai")` — AI rival with different stats (speed 180, higher handling)
- `ai { speedPercent(85); difficulty(3); rubberBanding(enabled=true, strength=40) }` — AI config
- `camera { follow("car"); smoothing = 0.3f; bounds(256, 256) }` — smooth camera follow
- `movement { style(MovementStyle.SMOOTH); speed(3); acceleration(1); friction(1) }` — actor smooth movement
- `zone("circuit")` — 32x32 tile circuit map (256x256 pixel world)
- `target(GbcTarget.GBC_COMPATIBLE)` — GBC color mode (enhances racing track visuals)
- `sceneRef()` forward declaration — `titleRef` breaks results→title cycle

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:racer:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:racer:buildRom

# Run in emulator (GBC mode recommended)
./gradlew :gbkt-examples:racer:runEmulator
```

Generated C: `gbkt-examples/racer/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/racer/build/gbkt/output/racer.gb`
