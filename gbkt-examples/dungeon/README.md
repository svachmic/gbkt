# Dungeon

A torch-crawling dungeon game for Game Boy demonstrating the gbkt exploration and RPG systems.

Explore grid-based dungeon floors while managing your torch. Find keys, fight monsters, survive.

## How to Play

| Button | Action |
|--------|--------|
| D-pad  | Move one tile at a time (8px grid) |
| START  | Return to title (from game over) |

**Objective:** Explore as many floors as possible before your torch burns out. Avoid enemies or fight them.

## Features Demonstrated

- `exploration { preset(ExplorationPreset.DUNGEON_CRAWLER) }` — grid movement (8px tiles), step callbacks
- `gauge("torch")` — depletes every 4 steps; `onLow(50)` and `onDepleted` navigate to game over
- `keys("room_key")` — key counter (max 9) displayed via HUD icon strip
- `zone("floor1")` — dungeon zone with encounter table (`safeSteps(10)`, weight-based entries)
- `flags("dungeon_flags")` — global story flags (`bossDefeated`, `gotTreasure`, `foundKey`)
- `simpleBattle("combat")` from `gbkt-genre-rpg` — adventurer vs bat/skeleton
- `battleUpdate("combat")` — drives combat state machine each frame in battle scene
- `hud()` with `number()` (torch level) and `icons()` (key counter) elements
- `dialog()` — torch-low warning rendered on window layer
- `camera { smoothing = 0.2f }` and `saveData()` system
- `MBC5_RAM_BATTERY` cartridge, 4 ROM banks, 1 RAM bank

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:dungeon:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:dungeon:buildRom

# Run in emulator
./gradlew :gbkt-examples:dungeon:runEmulator
```

Generated C: `gbkt-examples/dungeon/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/dungeon/build/gbkt/output/dungeon.gb`
