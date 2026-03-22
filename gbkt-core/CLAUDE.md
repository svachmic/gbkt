# gbkt-core Module

Aggregator module that re-exports the four foundational modules and provides
asset-pipeline utilities, source mapping, and file I/O helpers.

## Dependencies

```
api(project(":gbkt-ir"))       // IR nodes, expressions, sealed hierarchies
api(project(":gbkt-lang"))     // DSL recording context, variables, operators
api(project(":gbkt-engine"))   // Scene, input, collision, entity, graphics, UI, flow
api(project(":gbkt-world"))    // Floors, encounters, flags, exploration, RPG
```

Used by: `gbkt-backend-api`, genre modules, `gbkt-cli`

## Local Files (root package)

| File | Purpose |
|------|---------|
| `AssetPipeline.kt` | Sprite/tile loading, 2bpp conversion, GB/GBC palette extraction (object) |
| `AssetManifest.kt` | JSON-serializable manifest of all game assets (`AssetManifest`, `AssetManifestEntry`) |
| `PngValidator.kt` | PNG signature and IHDR validation for Game Boy constraints (object) |
| `TileDeduplicator.kt` | Removes duplicate tiles from tileset data |
| `TiledParser.kt` | Parses Tiled `.tmj`/`.json` map files into `TiledMap`/`TiledLayer`/`TiledTileset` |
| `LdtkParser.kt` | Parses LDtk `.ldtk` level files into `LdtkMap`/`LdtkLayer` |
| `FileIO.kt` | Thin file-system abstraction: `exists`, `readBytes`, `isReadable`, `resolvePath` |
| `SourceMap.kt` | Kotlin-to-C source mapping (`SourceMap`, `SourceMapping`, `SourceLocation`, `SourceMapBuilder`) |
| `GameIR.kt` | Marker interface for game IR passed to codegen backends |
| `References.kt` | Typed reference value classes: `SceneRef`, `AnimationRef`, `StateRef`, `TagRef`, `CharacterRef`, `AbilityRef`, `ItemRef`, `MonsterRef`, `StatusEffectRef` |

## Sub-packages

| Package | CLAUDE.md | Contents |
|---------|-----------|----------|
| `assets/` | Yes | Type-safe asset references and registry |
| `constraints/` | Yes | Target-profile hardware specs (screen, sprite, memory, audio) |
| `optimization/` | Yes | Asset analysis, reporting, and optimization suggestions |
| `test/` | Yes | In-memory IR simulation for unit testing |

## Key Patterns

- **AssetPipeline** converts PNG images to Game Boy 2bpp tile data. It handles
  both DMG (4-shade) and GBC (multi-palette) sprite formats.
- **SourceMap** enables mapping generated C line numbers back to Kotlin DSL
  source locations for debugging.
- **References.kt** value classes provide type-safe handles that prevent
  mixing scene IDs with animation IDs, etc.

## Related Modules

- `gbkt-ir/` -- IR node types re-exported through this module
- `gbkt-lang/` -- DSL builders and variable system
- `gbkt-engine/` -- Runtime game constructs (scenes, input, entities)
- `gbkt-world/` -- World/dungeon constructs (floors, encounters, flags)
- `gbkt-backend-gbdk/` -- GBDK C code generation consuming GameIR
