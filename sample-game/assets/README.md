# Sample Game Assets

This directory contains placeholder files for the comprehensive demo game assets.

## Required Assets

The sample game requires the following assets to be created:

### Sprites

1. **player.png** (8x16 sprite sheet, 8 frames)
   - Idle animation (frames 0-1)
   - Run animation (frames 2-5)
   - Jump pose (frame 6)
   - Fall pose (frame 7)

2. **coin.png** (8x8 sprite sheet, 4 frames)
   - Spin animation (frames 0-3)

3. **ball.png** (8x8 single sprite)
   - Enemy ball sprite

4. **particle.png** (4x4 single sprite)
   - Particle effect sprite

### Tilemap

5. **tiles.png** (8x8 tileset)
   - Ground, wall, platform, and decorative tiles
   - At least 8 tiles recommended

6. **level.json** (Tiled map JSON)
   - 20x18 tiles (160x144 pixels)
   - Background layer for visuals
   - Collision layer for solid tiles

## Creating Assets

### Option 1: Use the Placeholder Files

The `.placeholder` files in this directory contain detailed specifications for each asset. Follow the instructions in each placeholder to create the actual assets.

### Option 2: Let gbkt Generate Test Sprites

The sample game's `main()` function automatically generates simple test sprites using the `generateTestSprite()` utility:

```kotlin
generateTestSprite("$assetDir/player.png", 8, 16)
generateTestSprite("$assetDir/coin.png", 8, 8)
generateTestSprite("$assetDir/ball.png", 8, 8)
generateTestSprite("$assetDir/particle.png", 4, 4)
generateTestSprite("$assetDir/tiles.png", 8, 8)
```

These test sprites are functional but basic. For production games, create proper pixel art assets.

### Option 3: Create Pixel Art

Use a pixel art editor like:
- **Aseprite** (recommended, paid)
- **GraphicsGale** (free)
- **Piskel** (free, web-based)
- **GIMP** (free, general purpose)

Tips:
- Use the 4-color Game Boy palette
- Export as PNG
- Arrange sprite frames horizontally
- Use 8x8 or 8x16 dimensions (Game Boy standard)

## Tools

### Tiled Map Editor

For creating `level.json`, use [Tiled](https://www.mapeditor.org/):

1. Download and install Tiled
2. Create a new map (20x18 tiles, 8x8 tile size)
3. Import `tiles.png` as the tileset
4. Create two layers:
   - "Background" - visual tiles
   - "Collision" - collision map (0=empty, 1=solid)
5. Paint your level
6. Export as JSON format

## Game Boy Palette

The Game Boy uses 4-color palettes. Common choices:

- **Monochrome**: `0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000`
- **Green**: `0xFFFFFF, 0x88FF88, 0x448844, 0x000000`
- **Amber**: `0xFFFFFF, 0xFFCC44, 0x884400, 0x000000`

For GBC (Game Boy Color), you can use full RGB colors but still in sets of 4.

## Directory Structure

```
sample-game/
├── assets/                    # Placeholder specifications
│   ├── README.md             # This file
│   ├── player.png.placeholder
│   ├── coin.png.placeholder
│   ├── ball.png.placeholder
│   ├── particle.png.placeholder
│   ├── tiles.png.placeholder
│   └── level.json.placeholder
└── src/main/resources/sprites/  # Actual asset location (auto-generated)
    ├── player.png
    ├── coin.png
    ├── ball.png
    ├── particle.png
    ├── tiles.png
    └── level.json
```

## Building the Game

Once assets are created (or using generated test sprites):

```bash
# Generate C code
./gradlew :sample-game:generateC

# Build ROM (requires GBDK-2020)
./gradlew :sample-game:buildRom

# Or all in one step
./gradlew :sample-game:compileRom
```

The game will work with auto-generated test sprites, so you can build and test immediately without creating custom assets.
