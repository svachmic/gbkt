# Labyrinth of the Dragon - Game Assets

This folder contains all game assets from the original Labyrinth of the Dragon.
The gbkt build system automatically converts these to GBDK-compatible formats.

## Folder Structure

```
res/
├── sprites/           # Character and UI sprites
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
├── tiles/             # Tilesets
│   ├── dungeon.png    # Dungeon tileset (walls, floors, etc.)
│   ├── battle.png     # Battle UI tiles
│   ├── objects.png    # Interactive objects (chests, doors, etc.)
│   ├── font.png       # Game font
│   └── world_map.png  # World/menu map tiles
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
└── data/              # Game data
    ├── strings.js     # All game text strings
    └── tables.csv     # Balance tables (stats, items, etc.)
```

## Asset Sources

These assets are from the original Labyrinth of the Dragon game by NESHacker.
The PSD source files are available in `../assets/art/` for reference.

## Building

Run `./gradlew buildRom` to process all assets and generate the ROM.
