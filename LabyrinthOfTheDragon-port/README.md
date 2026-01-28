# Labyrinth of the Dragon - gbkt Port

A complete port of the D&D-style dungeon crawler RPG using the gbkt framework. This demonstrates how to build a full-featured Game Boy Color game using Kotlin DSL.

## Features

- **4 playable classes**: Fighter, Sorcerer, Monk, Druid
- **24 unique abilities** across all classes
- **12 monsters** with distinct AI behaviors
- **8 dungeon floors** with increasing difficulty
- **Turn-based combat** with status effects, items, and leveling
- **Save system** with 3 save slots

## Quick Start

### Prerequisites

- JDK 21+
- GBDK (for ROM compilation)

### Build the ROM

```bash
# 1. Generate C code from Kotlin DSL
./gradlew :LabyrinthOfTheDragon-port:generateC

# 2. Compile to Game Boy ROM (requires GBDK installed)
./gradlew :LabyrinthOfTheDragon-port:buildRom

# 3. Run in emulator
./gradlew :LabyrinthOfTheDragon-port:runEmulator
```

The ROM will be output to `build/generated/gbdk/labyrinth.gbc` (32KB).

### Generate C Code Only

If you don't have GBDK installed, you can still generate the C code:

```bash
./gradlew :LabyrinthOfTheDragon-port:generateC
```

Output: `build/generated/gbdk/main.c` (7500+ lines of GBDK-compatible C)

## Project Structure

```
LabyrinthOfTheDragon-port/
├── src/main/kotlin/io/github/gbkt/examples/labyrinth/
│   ├── LabyrinthOfTheDragon.kt  # Main game definition
│   ├── GameConfig.kt            # Gameplay constants
│   ├── GameState.kt             # Runtime variables
│   ├── SaveSystem.kt            # Save/load logic
│   ├── Sounds.kt                # Sound effects (procedural)
│   ├── scenes/                  # Game scenes (7 scenes)
│   ├── rpg/                     # Combat system, characters, monsters
│   └── world/                   # Dungeon floors and encounters
├── res/                         # Migrated assets (sprites, tiles, maps)
├── ASSETS.md                    # Asset integration guide
├── CLAUDE.md                    # Developer documentation
└── README.md                    # This file
```

## Asset Migration

Assets are migrated from the original game automatically:

```bash
./gradlew :LabyrinthOfTheDragon-port:migrateAssets
```

See [ASSETS.md](ASSETS.md) for details on the migrated assets.

## Game Controls

| Button | Title Screen | Battle | Exploration |
|--------|--------------|--------|-------------|
| D-Pad  | Navigate menu | Navigate menu | Move |
| A      | Select | Confirm | Interact |
| B      | - | Back/Cancel | - |
| Start  | Start game | Pause | Pause |

## Combat System

The turn-based combat system features:

- **Speed-based turn order**: Higher agility acts first
- **Status effects**: Poison, Regen, Haste, Slow, and more
- **Elemental damage**: Fire, Ice, Lightning with resistances
- **Items**: Potions, Elixirs, and buff items
- **Flee mechanic**: Based on agility comparison

## Save System

- 3 save slots with character progress
- Saves current floor, position, and all stats
- Battery-backed SRAM on real hardware

## Building Without GBDK

If you don't have GBDK, the generated C code can be compiled with any SDCC-based toolchain:

```bash
# Generate C code
./gradlew :LabyrinthOfTheDragon-port:generateC

# Compile manually with lcc (GBDK compiler)
lcc -Wa-l -Wl-m -Wl-j -DUSE_SFR_FOR_REG \
    -c -o main.o build/generated/gbdk/main.c
lcc -Wa-l -Wl-m -Wl-j -o labyrinth.gbc main.o
```

## Documentation

- [CLAUDE.md](CLAUDE.md) - Developer documentation with DSL patterns
- [ASSETS.md](ASSETS.md) - Asset integration and migration guide
- [gbkt DSL Reference](../context/DSL_REFERENCE.md) - Complete DSL syntax reference

## Original Game

The original C implementation is in `../LabyrinthOfTheDragon/` for reference.

## License

This port is part of the gbkt project. See the root LICENSE file for details.
