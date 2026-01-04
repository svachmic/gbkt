# Tooling

Build tools, asset pipeline, and configuration for gbkt.

## Asset Pipeline (JVM only)

Convert PNG sprites to Game Boy tile format:

```kotlin
// In your main function:
val assetDir = "src/main/resources/sprites"

// Option 1: Auto-compile all sprite assets
val code = compileWithAssets(myGame, assetDir)

// Option 2: Manual conversion
val sheet = AssetPipeline.loadSprite("player.png")
val cCode = AssetPipeline.generateTileData("player", sheet)
```

The pipeline:
1. Reads PNG files (must be 8px multiples)
2. Maps colors to 4-shade GB palette using luminance
3. Converts to GB tile format (2bpp, 16 bytes per 8x8 tile)
4. Generates C arrays with `set_sprite_data()` calls

Custom palettes:
```kotlin
AssetPipeline.DEFAULT_PALETTE      // [192, 128, 64] thresholds
AssetPipeline.HIGH_CONTRAST_PALETTE // [200, 140, 80]
AssetPipeline.INVERTED_PALETTE     // [64, 128, 192]
```

## GBC Color Palette Support

gbkt supports Game Boy Color with full 15-bit RGB555 color palettes (8 sprite palettes + 8 background palettes, 4 colors each).

### Enabling GBC Mode

```kotlin
val myGame = gbGame("ColorGame") {
    config {
        gbcSupport = true              // Enable GBC features
        gbcMode = GBCMode.COMPATIBLE   // Works on both DMG and GBC
        // gbcMode = GBCMode.ONLY      // GBC exclusive
    }
    // ...
}
```

### Defining Palettes

```kotlin
// Manual palette definition with hex colors
val playerPalette = palette("player") {
    colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000)  // lightest to darkest
}

// Individual color setting
val enemyPalette = palette("enemy") {
    color(0, 255, 255, 255)  // index, R, G, B (0-255)
    color(1, 255, 0, 0)
    color(2, 128, 0, 0)
    color(3, 0, 0, 0)
}

// Use preset palettes
val bgPalette = palette("bg", PalettePreset.FOREST)
// Available: GRAYSCALE, FOREST, OCEAN, FIRE, ICE, NIGHT, SEPIA
```

### Assigning Palettes to Sprites

```kotlin
val player = sprite(SpriteAsset("player.png")) {
    size = 8 x 16
    boundTo(playerX, playerY)
    palette = playerPalette  // Assign palette to sprite
}

// Or use direct index assignment
val enemy = sprite(SpriteAsset("enemy.png")) {
    size = 8 x 8
    paletteIndex = 2  // Use palette slot 2
}
```

### Runtime Palette Effects

```kotlin
gameplayScene = scene("gameplay") {
    enter {
        playerPalette.apply()  // Apply palette to its assigned slot
    }

    every.frame {
        whenever(playerHit eq 1) {
            playerPalette.flash(0xFF0000)  // Flash red
        }

        // Fade toward target colors
        playerPalette.fadeTo(
            listOf(0xFFFFFF, 0xFF0000, 0x880000, 0x000000),
            fadeProgress  // 0-255 progress
        )
    }
}
```

### Automatic Palette Extraction

When `gbcSupport = true`, the asset pipeline automatically extracts colors from PNG sprites:

```kotlin
// Palettes are auto-extracted if sprite has no explicit palette
val autoSprite = sprite(SpriteAsset("colorful.png")) {
    size = 8 x 8
    // No palette specified - colors extracted automatically!
}
```

### GBC Types Reference

- `GBCColor` - RGB555 color value class
- `GBCPalette` - 4-color palette data class
- `GBCMode` - Enum: `DISABLED`, `COMPATIBLE`, `ONLY`
- `PaletteType` - Enum: `SPRITE`, `BACKGROUND`
- `Palette` - DSL wrapper with runtime operations

### Generated C Code

GBC mode generates:
- `#include <gb/cgb.h>` header
- Palette arrays: `UINT16 player_pal[] = { 0x7FFF, 0x47F1, ... };`
- `if (_cpu == CGB_TYPE)` runtime checks
- `set_sprite_palette()` / `set_bkg_palette()` calls
- `set_sprite_prop()` for palette attribute assignment

## Gradle Plugin

The `io.github.gbkt` plugin enables one-command ROM builds.

### Configuration

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("io.github.gbkt")
}

gbkt {
    // Required: class::property format
    game("sample.RunnerGameKt::runnerGame")

    // Optional: asset directory (default: src/main/resources/assets)
    assets("src/main/resources/sprites")

    // Optional: ROM name (default: "game")
    outputName.set("runner")

    // Optional: GBDK path (auto-detected from GBDK_HOME or common paths)
    // gbdkHome.set("/path/to/gbdk-2020")
}
```

### Tasks

- `generateC` - Generate GBDK C code from Kotlin DSL
- `compileRom` - Compile C to ROM (requires GBDK)
- `buildRom` - Full build pipeline
- `runEmulator` - Run ROM in mGBA (auto-detects installation)
- `runWatch` - Start emulator with live reload (for development)
- `cleanGbkt` - Clean generated files

### GBDK Setup

1. Download GBDK-2020: https://github.com/gbdk-2020/gbdk-2020/releases
2. Set `GBDK_HOME` environment variable or configure in `gbkt {}`
3. Run `./gradlew buildRom`

Output: `build/gbkt/output/<name>.gb`

### Enhanced Error Messages

When GBDK compilation fails, gbkt provides enhanced error messages that:

1. **Map back to Kotlin source**: Shows both the C error location and the original Kotlin DSL location
2. **Display code context**: Shows the relevant Kotlin code snippet
3. **Suggest fixes**: Common mistakes get "Did you mean X?" suggestions

Example error output:

```
Error in main.c:42:15 (error)
  undefined identifier 'playe_x'

Kotlin source: src/main/kotlin/Game.kt:28
  player.x += 1
  
Suggestion: Did you mean 'player_x'? (undefined variable)
```

**Source maps** are automatically generated during compilation and stored alongside the C code (`main.c.gbkt.map`).

### Emulator Integration

Configure mGBA emulator settings:

```kotlin
gbkt {
    emulator {
        // Optional: explicit path (auto-detects mGBA by default)
        // path.set("/path/to/mgba")

        // Optional: emulator arguments
        args.set(listOf("-s", "4"))  // 4x window scale

        // Live reload (default: true)
        liveReload.set(true)

        // Optional: custom Lua script for live reload
        // liveReloadScript.set("scripts/custom-reload.lua")
    }
}
```

### Live Reload Development

For rapid iteration during development:

**Two-terminal workflow:**
```bash
# Terminal 1: Continuous build
./gradlew -t buildRom

# Terminal 2: Run emulator with live reload
./gradlew runWatch
```

The live reload feature:
1. Monitors the ROM file for changes
2. Automatically reloads when a new build is detected
3. Works cross-platform (macOS, Linux, Windows)

**How it works:**
- Uses mGBA's Lua scripting API
- A Lua script checks the ROM file modification time every ~0.5 seconds
- When changes are detected, it calls `emu:loadFile()` to reload

**Platform-specific notes:**
- macOS: Uses `stat -f %m` for file modification time
- Linux: Uses `stat -c %Y` for file modification time
- Windows: Uses PowerShell to get LastWriteTime

## VSCode Extension

The gbkt VSCode extension provides enhanced development experience with code completion, documentation, real-time validation, and debugging tools.

### Installation

Install from the VSCode marketplace or build from source:

```bash
cd vscode-extension
npm install
npm run compile
```

### Features

#### Source Map Navigation

Jump from generated C code back to Kotlin DSL source:

1. Open the generated C file (`build/gbkt/generated/main.c`) or use "View Generated C" command
2. Right-click on any line → **"Jump to Kotlin Source"**
3. The extension opens the corresponding Kotlin file at the correct line

**Requirements:**
- Source maps are automatically generated during `buildRom`
- Saved to `build/gbkt/generated/main.c.gbkt.map`

**Usage:**
- Command Palette: `gbkt: Jump to Kotlin Source`
- Context menu: Right-click in C files
- Works with exact line mappings or falls back to closest previous mapping

#### Language Server Protocol (LSP)

Intelligent code assistance:

- **Autocomplete**: DSL keywords, variables, sprites, scenes, etc.
- **Hover Documentation**: Detailed docs and examples for all DSL constructs
- **Signature Help**: Function signatures with parameter descriptions
- **Symbol Rename**: F2 or context menu to rename symbols across file
- **Code Actions**: Quick fixes for missing frame blocks, start scenes

See [vscode-extension/server/README.md](../vscode-extension/server/README.md) for full LSP documentation.

#### Real-time Validation

The extension validates your DSL code as you type, showing warnings and errors for:

- **OAM Limits**: Warns when total sprites exceed 40 (Game Boy hardware limit)
- **Palette Usage**: Tracks sprite palettes (max 8) and background palettes (max 8)
- **Sprite Counts**: Shows breakdown of direct sprites, entity sprites, and pool sprites

The status bar displays current resource usage:

```
Sprites: 35/40 | Palettes: 4S/3B
```

**Note**: This uses regex-based heuristics for quick validation. For full accuracy, compile-time validation has access to the complete AST.

#### Build Integration

Commands available via Command Palette or status bar:

- `gbkt: Build ROM` - Compile Kotlin DSL to ROM
- `gbkt: Run Emulator` - Launch game in mGBA
- `gbkt: View Generated C` - Preview generated C code

### Configuration

```json
{
  "gbkt.gradleWrapper": "./gradlew",
  "gbkt.emulatorPath": "",
  "gbkt.autoRefreshCPreview": true,
  "gbkt.languageServer.enable": true
}
```

### Development Notes

The plugin uses composite builds:
- `gbkt-gradle-plugin` is included via `pluginManagement { includeBuild() }`
- `gbkt-core` must be published to mavenLocal before plugin builds:
  ```bash
  ./gradlew :gbkt-core:publishToMavenLocal
  ```
