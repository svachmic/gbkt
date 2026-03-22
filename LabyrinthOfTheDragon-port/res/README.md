# Labyrinth of the Dragon - Game Assets

This folder contains all game assets from the original Labyrinth of the Dragon.
The gbkt build system automatically converts these to GBDK-compatible formats.

## Folder Structure

```
res/
├── sprites/           # Character and UI sprites (canonical — no duplicates)
│   ├── hero.png       # Hero sprite sheet (4 directions)
│   ├── title_1.png    # Title screen main graphic
│   ├── title_fire.png # Animated fire effect
│   ├── title_smoke.png # Animated smoke effect
│   ├── neshacker_presents.png
│   └── monsters/      # Monster sprites (one per monster)
│       ├── goblin.png
│       ├── kobold.png
│       ├── zombie.png
│       ├── bugbear.png
│       ├── owlbear.png
│       ├── gelatinous_cube.png
│       ├── will_o_wisp.png
│       ├── displacer_beast.png
│       ├── mindflayer.png
│       ├── deathknight.png
│       ├── beholder.png
│       ├── dragon.png
│       └── monsters.png  # Combined sprite sheet
├── tiles/             # Tilesets (canonical — no duplicates)
│   ├── dungeon_tiles.png   # Dungeon tileset (walls, floors, etc.)
│   ├── battle_tiles.png    # Battle UI tiles
│   ├── objects.png    # Interactive objects (chests, doors, etc.)
│   ├── font.png       # Game font
│   ├── monsters_sheet.png  # Monster tile sheet for battle
│   ├── world_map.png  # World/menu map tiles
│   └── dungeon.bin    # Binary tileset data (INCBIN)
├── tilemaps/
│   ├── floors/        # Dungeon floor layouts (8 floors)
│   │   ├── floor1.tilemap
│   │   ├── floor2.tilemap
│   │   ├── floor3.tilemap
│   │   ├── floor4.tilemap
│   │   ├── floor5.tilemap
│   │   ├── floor6.tilemap
│   │   ├── floor7.tilemap
│   │   └── floor8.tilemap
│   └── ui/            # UI screen layouts
│       ├── title_screen.tilemap
│       ├── hero_select.tilemap
│       ├── save_select.tilemap
│       ├── name_entry.tilemap
│       ├── map_menu.tilemap
│       ├── battle_menus.tilemap
│       ├── battle_monster_layouts.tilemap
│       ├── textbox.tilemap
│       └── neshacker_presents.tilemap
├── maps/              # TMX map files (floor layouts)
├── data/              # Game data tables
│   ├── tables.csv     # Balance tables (stats, items, etc.)
│   └── tables.schema.json
├── strings/           # Localization PO files
│   ├── en.po
│   └── messages.pot
├── sfx/               # Sound effects (empty — Original has no separate SFX files)
├── palettes/          # Palette data (empty — palettes defined in Kotlin)
└── music/             # Music (empty — Original is SFX-only)
```

## Asset Sources

These assets are from the original Labyrinth of the Dragon game by NESHacker.
The PSD source files are available in `../assets/art/` for reference.

## Canonical Layout Rules

- **Sprites:** All sprite PNGs live in `sprites/` (hero, UI sprites) or `sprites/monsters/`
- **Tilesets:** All tileset PNGs live in `tiles/` with `_tiles` suffix (e.g., `dungeon_tiles.png`)
- **No duplicates:** Each asset has exactly one canonical location — no copies across directories

## Building

Run `./gradlew buildRom` to process all assets and generate the ROM.
