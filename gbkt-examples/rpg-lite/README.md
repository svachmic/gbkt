# RPG Lite

A mini-RPG for Game Boy demonstrating the gbkt RPG genre package.

The hero explores a dungeon, fights random encounters, and spends gold to heal in town.

## How to Play

| Button | Action |
|--------|--------|
| D-pad  | Move hero in dungeon/town |
| A      | Enter dungeon (from town) |
| START  | Heal in town (costs 5 gold) / Press START on title |

**Objective:** Reach the right edge of each dungeon floor to go deeper. Survive as long as possible.

## Features Demonstrated

- `character()` / `monster()` from `gbkt-genre-rpg` — define hero (HP 30, ATK 8, DEF 5) and two monsters (slime, bat)
- `simpleBattle("combat")` — wires party + encounter table + onVictory/onDefeat callbacks
- `battleUpdate("combat")` — called every frame in dungeon scene to drive the combat state machine
- `sceneRef()` forward declarations — `titleRef` and `gameoverRef` break circular navigation cycles
- `u8Var` variables — `hp`, `gold`, `dungeonLevel`, `stepCount`
- `soundEffect { preset(...) }` — hit, coin, win, explode presets
- Step-based random encounters — encounter triggered every 60 steps
- Compound operators — `gold += 5`, `hp += 10`, `dungeonLevel += 1`
- `MBC5_RAM_BATTERY` cartridge config with 4 ROM banks + 1 RAM bank

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:rpg-lite:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:rpg-lite:buildRom

# Run in emulator
./gradlew :gbkt-examples:rpg-lite:runEmulator
```

Generated C: `gbkt-examples/rpg-lite/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/rpg-lite/build/gbkt/output/rpg-lite.gb`
