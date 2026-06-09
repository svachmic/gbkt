# Tooling

Build tools, asset pipeline, and configuration for gbkt.

## Asset Pipeline (JVM only)

Convert PNG sprites to Game Boy tile format:

```kotlin
// Manual conversion via AssetPipeline
val sheet = AssetPipeline.loadSprite("player.png")
val cCode = AssetPipeline.generateTileData("player", sheet)

// Batch conversion for multiple sprites
val allCode = AssetPipeline.generateAllTileData(mapOf("player" to sheet))
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

## Sprite Asset Pipeline

> Phase 12.4 contract: every metasprite declares its source PNG explicitly via the
> `sprite(asset("..."))` DSL binder; the pipeline routes the path through `game_metadata.json` to
> png2asset deterministically.

### DSL

```kotlin
val player by metasprite {
    sprite(asset("graphics/player-sheet.png"))    // required since Phase 12.4
    posX(playerX); posY(playerY)
    idx(walkFrameIdx); rot(facingRot)
    frame {
        tile(0, 0, 0); tile(8, 0, 1); tile(16, 0, 2)
        tile(0, 16, 3); tile(8, 16, 4); tile(16, 16, 5)
    }
    // ... additional frames
}
```

The `sprite(asset(path))` binder is mandatory. `GenerateCTask` throws a `GradleException` with the
failing metasprite's id if it is omitted (validation gate — see "What changed in Phase 12.4" below).

### Asset path resolution

`asset("relative/path.png")` resolves to `{assetDirectory}/relative/path.png` where `assetDirectory` is
configured in the project's `build.gradle.kts`:

```kotlin
gbkt {
    // assetDirectory defaults to "res" — override with:
    // assetDirectory.set(file("res"))
}
```

So `sprite(asset("graphics/player-sheet.png"))` looks at `<projectDir>/res/graphics/player-sheet.png`.

### Pipeline flow

```
DSL: sprite(asset(...))
  ↓
MetaspriteBuilder.sprite(AssetRef)        (gbkt-lang)
  ↓
MetaspriteIR.spritePath                   (gbkt-ir — additive nullable field)
  ↓
GBDKPipeline.buildMetadataFile()
emits game_metadata.json sprites[] entry  (gbkt-backend-gbdk)
  ↓
ConvertSpritesTask reads the sidecar       (gbkt-gradle-plugin)
  ↓
png2asset <assetDir>/<spritePath>          (GBDK toolchain)
  ↓
build/gbkt/generated/sprites/<id>.c        (lcc compiles this)
```

The `game_metadata.json` sidecar carries the sprites array:

```json
{
  "sprites": [
    { "id": "player",   "spritePath": "graphics/player-character-gbapduck-sprites.png", "mirrorDedup": false },
    { "id": "elephant", "spritePath": "sprites/elephant.png", "mirrorDedup": false }
  ]
}
```

This is the same cross-task sidecar pattern used by `ConvertZoneTilesetsTask` for zone tilesets
(Phase 12.2 D-A2 sidecar pattern).

### Mirror-dedup opt-in

`metasprite { mirrorDedup() }` omits png2asset's `-noflip` flag for that metasprite, allowing
png2asset to detect mirror-pair tiles and emit a smaller `_tiles[]` array with `S_FLIPX`/`S_FLIPY`
OAM attrs. Use ONLY for from-scratch authored metasprites that take advantage of the dedup; do
NOT use for metasprites transcribed from a reference's `-noflip` id space.

### What changed in Phase 12.4

**Pre-12.4:** PNG path was implicit — `ConvertSpritesTask` parsed `main.c` for
`#include "sprites/<stem>.h"` directives and looked for `<assetDirectory>/sprites/<stem>.png`. This
silently failed (emitting a 64-byte checkerboard placeholder) when the include stem didn't match the
asset filename or the asset lived outside `sprites/`.

**Post-12.4:** PNG path is explicit via `sprite(asset(...))`; the implicit convention is **REMOVED**. All
4 silent-stub fallback paths in `ConvertSpritesTask` now throw `GradleException` (fail-fast). This
follows the "explicit > implicit" principle — future authors and artists can `grep` for the actual
PNG path in the DSL without knowing any `_<id>` → file convention.

See `.planning/phases/12.4-sprite-pipeline-png2asset-integration-wire-png2asset-binary-/12.4-SPEC.md`
for the full requirement set and acceptance criteria.

## GBC Color Palette Support

gbkt supports Game Boy Color with full 15-bit RGB555 color palettes (8 sprite palettes + 8 background palettes, 4 colors each).

### Enabling GBC Mode

```kotlin
val myGame = game("ColorGame") {
    config {
        target(GbcTarget.GBC_COMPATIBLE)   // Works on both DMG and GBC
        // target(GbcTarget.GBC_ONLY)      // GBC exclusive
        // default is GbcTarget.DMG        // classic grayscale
    }
    // ...
}
```

### Defining Palettes

```kotlin
// Background palette — name inferred from the Kotlin property (by-delegate)
val playerPalette by palette {
    color0(GBCColor.fromRGB888(255, 255, 255))  // lightest
    color1(GBCColor.fromRGB888(136, 255, 136))
    color2(GBCColor.fromRGB888(68, 136, 68))
    color3(GBCColor.fromRGB888(0, 0, 0))        // darkest
}

// Sprite palette — same builder, registered as PaletteType.SPRITE
val enemyPalette by spritePalette {
    copy(GbcPresets.FIRE)
    color3(GBCColor.fromRGB888(0, 0, 0))  // override just the darkest shade
}

// Use preset palettes via copy(GbcPresets.X)
val bgPalette by palette { copy(GbcPresets.NATURE) }
// Available: CLASSIC_GREEN, NATURE, FIRE, ICE, OCEAN, DUNGEON, CAVERN, SUNSET,
//            NIGHT, PASTEL, SEPIA, NEON, MONOCHROME_BLUE, WARM_GRAY, UI_LIGHT, UI_DARK
```

### Assigning Palettes to Sprites

```kotlin
// Per-actor palette — inside the actor { } block
val player by actor {
    position(80, 72)
    sprite(asset("sprites/player.png")) { size(8, 16) }
    palette(GbcPresets.FIRE)   // assign GBC palette to this actor's sprite
}

// Per-scene hardware slots — palette() calls in the scene block load slots 0-7
scene("gameplay") {
    palette(playerPalette)           // auto-assigns next free slot
    palette(enemyPalette, slot = 3)  // or pin an explicit slot (0-7)
}
```

### Automatic Palette Extraction

The asset pipeline automatically extracts colors from PNG sprites when no explicit palette
is assigned:

```kotlin
// Palettes are auto-extracted if the actor's sprite has no explicit palette
val autoSprite by actor {
    position(80, 72)
    sprite(asset("sprites/colorful.png")) { size(8, 8) }
    // No palette specified - colors extracted automatically!
}
```

### GBC Types Reference

- `GBCColor` - RGB555 color value class (`GBCColor.fromRGB888(r, g, b)`)
- `GBCPalette` - 4-color palette data class
- `GbcTarget` - Enum: `DMG`, `GBC_COMPATIBLE`, `GBC_ONLY`
- `PaletteType` - Enum: `SPRITE`, `BACKGROUND`
- `GbcPresets` - 16 ready-made `GBCPalette` presets

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

## GBDK Troubleshooting

### Installation Verification

```bash
# Check GBDK_HOME is set
echo $GBDK_HOME

# Verify lcc exists
ls $GBDK_HOME/bin/lcc

# Test compilation
$GBDK_HOME/bin/lcc --version
```

### Common Issues

| Issue | Solution |
|-------|----------|
| "GBDK not found" | Set GBDK_HOME or install to /opt/gbdk-2020 |
| "Permission denied" on lcc | `chmod +x $GBDK_HOME/bin/lcc` |
| Bank overflow errors | Reduce code/data or use banking |
| Undefined symbols | Check generated C for typos |
| Source map not loading | Ensure generateC ran before compileRom |

### Manual Compilation

If Gradle fails, try manual compilation:

```bash
# Navigate to generated code
cd build/gbkt/generated

# Compile with GBDK
$GBDK_HOME/bin/lcc -Wa-l -Wl-m -Wl-j -o ../output/game.gb main.c
```

### Common GBDK Errors and Fixes

| Error Message | Cause | Fix |
|---------------|-------|-----|
| `undefined identifier 'xxx'` | Variable not declared | Check DSL spelling, ensure variable is defined |
| `type mismatch` | Wrong type assignment | Check u8/u16/i8 types in DSL |
| `too many global variables` | Exceeded RAM limits | Use pools, reduce variable count |
| `bank N overflow` | Too much code/data in bank | Split across banks |
| `function too complex` | Single function too large | Split scene logic into multiple functions |

### Debugging Tips

1. **Check generated C first**: Review `build/gbkt/generated/main.c` for issues
2. **Use source maps**: The `.gbkt.map` file maps C lines back to Kotlin
3. **Start minimal**: Build with minimal DSL code, add features incrementally
4. **Check GBDK docs**: Some errors are GBDK-specific, not gbkt issues
