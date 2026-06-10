# codegen/pipeline

Orchestrates the conversion of annotated `GameIR` into C AST files and tracks source mappings.

## GBDKPipeline

The main pipeline class. Its `generate(GameIR)` method returns `PipelineOutput` containing a map of filenames to C source text and a map of filenames to source map JSON.

### File Generation Flow

`buildCFiles()` produces a list of `CFile` instances:

1. **`buildHomeFile()`** -- Assembles `main.c` (bank 0). Invokes most visitors to collect global variables, `#define` constants, helper functions, system functions, and `main()`. This is the largest method in the pipeline.
2. **`buildSceneFile()`** -- Assembles `bank1.c`. Calls `SceneVisitor.visit()` per scene, wires in movement/animation/HUD update calls, and adds tileset reuse guards.
3. **`buildHeaderFile(homeFile, sceneFile)`** -- Assembles `game.h`. Extern declarations are built from `GameIR` data (actors, variables, arrays, combat state). Function prototypes are **auto-extracted** from the `homeFile` and `sceneFile` `CFile` objects via `CFunction.toPrototype()` — every non-static, non-main function gets a matching prototype. This eliminates manual prototype maintenance and guarantees no function is missing from the header.
4. **`buildTilemapBankFiles()`** -- Generates `zone_bankN.c` files for dungeon tilemap data, using `allocateZoneBanks()` to spread data across ROM banks.

### Source Map Collection

`SourceMapCollector` accumulates `Mapping` entries (C line number, DSL source location, IR node type, symbol name) during `CEmitter.emit()`. After emission, `buildSourceMapJson()` serializes the mappings as v2 JSON. Header files are excluded from source maps since they contain no DSL-originated statements.

### Auto-Prototype Architecture

`buildHeaderFile()` receives the already-built `homeFile` (`main.c`) and `sceneFile` (`bank1.c`) as `CFile` parameters. It extracts prototypes from their function lists using `CFunction.toPrototype()`:

- **HOME prototypes**: All non-static, non-`main` functions from `homeFile` → callable from banked code.
- **Scene prototypes**: All non-static functions from `sceneFile` → needed for HOME-resident trampolines (carry `isBanked = true`).

This replaces the previous approach of manually enumerating ~400 lines of prototype declarations in `buildHeaderFile()` plus two helper methods (`buildForwardDeclarations()`, `buildSystemHeaderDecls()`). Adding a new visitor function now automatically produces a matching header prototype — no manual sync required.

### Metadata File

`buildMetadataFile(GameIR)` produces `game_metadata.json` consumed by `gbkt-emulator`'s `GameMetadata` class:

- **`scenes`**: Map of scene name → index
- **`actors`**: Array of actor metadata (OAM slots, sprite dimensions, position variables)
- **`variables`**: Array of DSL-declared variables with name and type
- **`texts`**: Deduplicated literal display strings from scene scripts (trimmed, format strings excluded)
- **`terminalScenes`**: Convention-detected terminal scenes. Patterns: `gameover`, `game_over`, `win`, `victory`, `defeat`, `lose`

### Pipeline Helper Methods

The pipeline also builds input helpers (`button_pressed`, `dpad_held`), sprite helpers (`hide_sprites`, `show_sprites`), fade helpers, `delay_frames()`, `navigate_to_scene()`, and `main()`. These are structural C functions that do not come from visitors but from the pipeline's own builder methods.
