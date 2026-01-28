# Labyrinth of the Dragon - Asset Integration

## Overview

Assets are automatically migrated from the original game using the Gradle task:

```bash
./gradlew :LabyrinthOfTheDragon-port:migrateAssets
```

This copies PNG sprites and converts binary tilemaps to TMX format.

## Source Assets Location

Original game assets are in `/LabyrinthOfTheDragon/assets/`:

```
assets/
├── art/            # Source PSD files
│   ├── Characters.psd
│   ├── Dungeon.psd
│   └── ...
├── tiles/          # Tile graphics (PNG - ready to use!)
├── tms_tilemaps/   # Tilemap data
├── strings.js      # Game text
└── tables.csv      # Data tables
```

## Migrated Assets

### Player Sprite (`res/sprites/`)

| Asset | Size | Description | Status |
|-------|------|-------------|--------|
| `hero.png` | 96x64 | Hero sprite sheet (all classes) | ✓ Migrated |

Note: The original uses a single hero sprite for all classes. Class-specific sprites
would need to be created if desired.

### Monster Sprites (`res/monsters/`)

| Asset | Size | Description | Status |
|-------|------|-------------|--------|
| `kobold.png` | 56x112 | Common enemy | ✓ Migrated |
| `goblin.png` | 56x112 | Common enemy | ✓ Migrated |
| `zombie.png` | 56x112 | Common enemy | ✓ Migrated |
| `bugbear.png` | 56x112 | Uncommon enemy | ✓ Migrated |
| `owlbear.png` | 56x112 | Uncommon enemy | ✓ Migrated |
| `gelatinous_cube.png` | 56x112 | Uncommon enemy | ✓ Migrated |
| `displacer_beast.png` | 56x112 | Rare enemy | ✓ Migrated |
| `will_o_wisp.png` | 56x112 | Rare enemy | ✓ Migrated |
| `deathknight.png` | 56x112 | Elite enemy | ✓ Migrated |
| `mindflayer.png` | 56x112 | Elite enemy | ✓ Migrated |
| `beholder.png` | 56x112 | Boss | ✓ Migrated |
| `dragon.png` | 56x112 | Final boss | ✓ Migrated |

### Tilesets (`res/tiles/`)

| Asset | Description | Status |
|-------|-------------|--------|
| `dungeon_tiles.png` | Main dungeon tileset (128x192) | ✓ Migrated |
| `battle_tiles.png` | Battle screen tiles (128x40) | ✓ Migrated |
| `objects.png` | Map objects (128x64) | ✓ Migrated |
| `font.png` | Game font (128x64) | ✓ Migrated |
| `monsters_sheet.png` | Combined monster sheet (96x192) | ✓ Migrated |

### UI Elements (`res/ui/`)

| Asset | Description | Status |
|-------|-------------|--------|
| `title.png` | Title screen (128x128) | ✓ Migrated |
| `title_fire.png` | Title fire animation (64x80) | ✓ Migrated |
| `title_smoke.png` | Title smoke effect (64x?) | ✓ Migrated |

### Tilemaps (`res/maps/`)

All floor tilemaps converted from binary to TMX format:

| Floor | Description | Status |
|-------|-------------|--------|
| `floor1.tmx` | Dungeon Entrance (32x32 tiles) | ✓ Converted |
| `floor2.tmx` | Goblin Warrens | ✓ Converted |
| `floor3.tmx` | Beast Dens | ✓ Converted |
| `floor4.tmx` | Slime Caverns | ✓ Converted |
| `floor5.tmx` | Shadow Halls | ✓ Converted |
| `floor6.tmx` | Haunted Depths | ✓ Converted |
| `floor7.tmx` | Death's Domain | ✓ Converted |
| `floor8.tmx` | Dragon's Lair | ✓ Converted |

## Palette Configuration

The original game uses GBC palettes. Extract from `palette.c/h`:

- BG Palette 0: UI elements
- BG Palette 1-7: Dungeon tiles per floor
- Sprite Palette 0-1: Player character
- Sprite Palette 2-7: Monsters

**Status:** Not yet configured in DSL (palettes will be auto-detected from PNGs)

## Migration Process

The `migrateAssets` Gradle task performs:

1. **PNG Copy** - Direct copy of all tile/sprite PNGs
2. **Tilemap Conversion** - Binary `.tilemap` → TMX XML format
   - Detects map dimensions from file size
   - Converts 2-byte tile entries (index + attributes)
   - Outputs Tiled-compatible TMX files

## Current Status

- [x] Player sprite migrated
- [x] Monster sprites migrated (12 monsters)
- [x] Dungeon tileset migrated
- [x] UI elements migrated
- [x] Floor tilemaps converted to TMX (8 floors)
- [ ] Palettes configured in DSL

## Re-running Migration

To refresh assets from the original game:

```bash
./gradlew :LabyrinthOfTheDragon-port:migrateAssets
```

This will overwrite existing assets in `res/`.

## Notes

The migration script is defined in `build.gradle.kts` as the `migrateAssets` task.
A standalone Kotlin script version is available in `tools/migrate-assets.main.kts`.
