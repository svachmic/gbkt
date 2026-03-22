# Platformer GBC

A Game Boy Color variant of the Platformer example.
Identical gameplay to the DMG Platformer — physics, platforms, goal zone — but configured
with `GBC_COMPATIBLE` target so the game renders in color mode on Game Boy Color hardware
while remaining backward-compatible with the original DMG hardware.

## How to Play

| Button | Action |
|--------|--------|
| D-pad Left / Right | Move player |
| A | Jump |
| START | Start game (from title) / Return to title (from win) |

**Objective:** Navigate from the ground to the high platform and reach the goal zone. You have 3 lives.

## Level Layout

```
y=40  [goal zone x=112..128]
y=56  [======= high platform x=96..144 =======]

y=88  [====== mid platform x=40..88 ======]

y=120 [========= ground (full width) =========]
```

## Features Demonstrated

- Everything in the DMG Platformer, plus:
- `target(GbcTarget.GBC_COMPATIBLE)` — enables GBC color mode; falls back on DMG hardware
- Demonstrates the DMG/GBC variant pattern: copy the game, change the config target

## DMG vs GBC_COMPATIBLE vs GBC_ONLY

| Config | DMG hardware | GBC hardware | Color palettes |
|--------|-------------|-------------|----------------|
| (none) | Yes | Yes (DMG mode) | No |
| `GBC_COMPATIBLE` | Yes | Yes (GBC mode) | 8 palettes × 4 colors |
| `GBC_ONLY` | No | Yes (GBC mode) | 8 palettes × 4 colors |

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:platformer-gbc:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:platformer-gbc:buildRom

# Run in emulator
./gradlew :gbkt-examples:platformer-gbc:runEmulator
```

Generated C: `gbkt-examples/platformer-gbc/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/platformer-gbc/build/gbkt/output/platformer-gbc.gb`
