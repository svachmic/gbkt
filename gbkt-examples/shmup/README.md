# Shmup

A vertical scrolling shoot-em-up for Game Boy demonstrating entity pools and collision detection.

Pilot your ship, shoot enemies, and survive as long as possible.

## How to Play

| Button | Action |
|--------|--------|
| D-pad  | Move ship (screen-clamped) |
| A      | Shoot bullet (cooldown-gated) |
| START  | Start game / restart after game over |

**Objective:** Shoot as many enemies as possible (10 points each). You have 3 lives.

## Features Demonstrated

- `pool(template, max = N)` — `bulletPool` (max 8) and `enemyPool` (max 4) entity pools
- `spawn(pool, x, y)` — instantiate pool entity at position
- `destroy(pool, indexRef)` — despawn individual pool entity
- `destroyAll(pool)` — clear all pool entities (used on scene enter)
- `forEachActive(pool, "idx") { }` — iterate over active pool entities
- Cooldown pattern — `shootCooldown` variable gates bullet firing (8 frames between shots)
- Wave spawning — `waveTimer` spawns a new enemy every 60 frames
- Scroll simulation — `scrollY` variable incremented each frame
- `bullet.collides(enemy)` — bullet–enemy AABB collision
- `enemy.collides(player)` — enemy–player AABB collision
- `sceneRef()` forward declaration — `titleRef` breaks gameover→title cycle
- `ROM_ONLY` cartridge, 2 ROM banks (smallest config)

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:shmup:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:shmup:buildRom

# Run in emulator
./gradlew :gbkt-examples:shmup:runEmulator
```

Generated C: `gbkt-examples/shmup/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/shmup/build/gbkt/output/shmup.gb`
