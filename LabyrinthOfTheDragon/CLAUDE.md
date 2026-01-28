# CLAUDE.md - Labyrinth of the Dragon (Original)

## Overview

D&D-style dungeon crawler RPG for Game Boy Color, written in C using GBDK-2020.

**Original Author:** NESHacker
**Repository:** https://github.com/NESHacker/LabyrinthOfTheDragon

## Build

```bash
# Prerequisites: GBDK-2020, GNU Make, NodeJS
npm install
make assets
make
```

Set `GBDK_HOME` environment variable to your GBDK installation directory.

## Project Structure

```
LabyrinthOfTheDragon/
├── src/           # C source code
│   ├── main.c     # Entry point
│   ├── battle.c/h # Turn-based combat system
│   ├── map.c/h    # Dungeon exploration
│   ├── player.c/h # Player character system
│   ├── monster.c/h # Monster definitions
│   ├── floor*.c   # Individual floor definitions (1-8)
│   └── ...
├── assets/        # Source art (PSD files)
├── res/           # Compiled resources
├── data/          # Game data tables
└── tools/         # Build tools (NodeJS)
```

## Game Features

| Feature | Description |
|---------|-------------|
| Classes | 4 playable: Druid, Fighter, Monk, Sorcerer |
| Floors | 8 dungeon levels with progressive difficulty |
| Monsters | 12 unique D&D-inspired creatures |
| Combat | Turn-based with elemental weaknesses |
| Exploration | Grid-based movement, torch system, random encounters |

## Key Source Files

| File | Purpose |
|------|---------|
| `battle.c/h` | Combat state machine, damage calculation |
| `map.c/h` | Exploration, movement, collision |
| `encounter.c/h` | Random encounter tables per floor |
| `player.data.c` | Character class definitions and stats |
| `monsters.bank6.c` | Monster stat tables |
| `floor_common.c` | Shared floor functionality |
| `floor1.c` - `floor8.c` | Individual floor layouts and encounters |

## Monster Tiers

| Tier | Floors | Examples |
|------|--------|----------|
| Common | 1-2 | Kobold, Goblin, Zombie |
| Uncommon | 3-4 | Bugbear, Owlbear, Gelatinous Cube |
| Rare | 5-6 | Displacer Beast, Will-o'-Wisp |
| Elite | 7 | Death Knight, Mind Flayer |
| Boss | 8 | Beholder, Dragon |

## Technical Notes

- Uses GBDK-2020 bank switching for large content
- 8x8 pixel tiles, 160x144 display
- GBC palette system (8 BG palettes, 8 sprite palettes)
- SRAM save system with 3 slots

## Relationship to gbkt Port

This is the **reference implementation** being ported to the gbkt Kotlin DSL framework. The port lives in `../LabyrinthOfTheDragon-port/`.

When porting:
1. Reference this code for exact game mechanics
2. Extract constants from `data.h`, `player.h`, `monster.h`
3. Copy encounter tables from `map.encounters.c`
4. Use floor files for map object positions
