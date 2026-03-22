# Platformer

A side-scrolling platformer for the original Game Boy (DMG).
Jump across platforms and reach the goal zone at the top to win.

## How to Play

| Button | Action |
|--------|--------|
| D-pad Left / Right | Move player |
| A | Jump |
| START | Start game (from title) / Return to title (from win) |

**Objective:** Navigate from the ground to the high platform and reach the goal zone at position (112, 40). You have 3 lives — falling off the screen costs one life.

## Level Layout

```
y=40  [goal zone x=112..128]
y=56  [======= high platform x=96..144 =======]

y=88  [====== mid platform x=40..88 ======]

y=120 [========= ground (full width) =========]
```

## Features Demonstrated

- `gbkt-genre-platformer` package: `platformerPhysics()`, `platformerCamera()`, `platform()`, `goalZone()`
- `platformerPhysics`: gravity=2, jumpForce=8, terminalVelocity=12, coyoteTime=6, jumpBuffer=8
- `platformerCamera`: smooth horizontal follow with dead zone (16, 8)
- `PlatformType.SOLID` (ground) and `PlatformType.ONE_WAY` (mid/high — jump through from below)
- `MovementStyle.PHYSICS` actor movement for gravity and velocity
- Fall detection: player Y > 136 → lose life, respawn at start
- Goal zone check: coordinate-range detection for level exit
- Forward-declared `titleRef` for circular title → gameplay → win → title navigation
- 3 scenes: `title` → `gameplay` → `win` → `title`
- `ROM_ONLY` cartridge config, 2 ROM banks

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:platformer:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:platformer:buildRom

# Run in emulator
./gradlew :gbkt-examples:platformer:runEmulator
```

Generated C: `gbkt-examples/platformer/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/platformer/build/gbkt/output/platformer.gb`
