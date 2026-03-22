# gbkt-backend-gbdk

GBDK backend module -- compiles `GameIR` into GBDK-compatible C source files for Game Boy / Game Boy Color.

## Architecture

The backend follows a four-stage pipeline:

1. **Analysis** -- `DefaultPipeline` validates IR, allocates banks/VRAM/OAM, produces a budget report.
2. **Annotation** -- `applyAnnotations()` copies bank/VRAM/OAM assignments back onto the `GameIR`.
3. **Code generation** -- `GBDKPipelineV2` builds a typed C AST (`CFile` trees) from the annotated IR, then emits C text via `CEmitter`. Source maps are collected per file.
4. **Post-processing** -- `COutputOptimizer` runs `SharedConstantTablePass` and `FunctionDeduplicationPass` on the emitted C text to shrink ROM size.

## Entry Point

`GBDKBackend` implements `CodegenBackend`. Its `generate()` method delegates to `generateV2()`, which orchestrates the full pipeline and returns a `GenerationResult` containing `main.c`, `bank1.c`, `game.h`, and optional zone bank files.

## Package Layout

| Package | Purpose |
|---------|---------|
| `codegen/ast/` | Typed C AST model -- `CFile`, `CFunction`, `CStatement`, `CExpr`, `CType` |
| `codegen/visitor/` | 13 visitors that convert IR subsystems into C AST nodes |
| `codegen/pipeline/` | `GBDKPipelineV2` orchestrates visitor calls; `SourceMapCollector` tracks C-line to DSL-line mappings |
| `codegen/postprocess/` | Text-level optimizations on emitted C output (dedup constants + functions) |
| `codegen/` | `GBDKCollectionCodegen` -- hash tables, pools, ring buffers, fixed slots |
| `profiles/` | `GameBoyProfile`, `GameBoyColorProfile`, `GameBoyConstants` -- target hardware specs |

## How Visitors Fit Together

`GBDKPipelineV2.buildHomeFile()` and `buildSceneFile()` invoke visitors to produce typed AST fragments:

- **Variable declarations** (`CVarDecl`): `ActorVisitor.visit()`, `SoundVisitor.buildSoundDriverGlobals()`, `DialogVisitor.buildDialogGlobalVars()`, `HudVisitor.buildHudGlobalVars()`
- **#define constants** (`CDefine`): `SceneVisitor.generateSceneEnum()`, `ActorVisitor.generateAnimationDefines()`/`generatePhysicsDefines()`
- **Functions** (`CFunction`): `ScriptOpVisitor.visit()` (called inside scene visitors), `ExprVisitor.visit()` (expression lowering), `MenuVisitor.buildMenuFunctions()`, `CollisionVisitor.buildCollisionCodegen()`
- **System functions**: `GBDKSystemVisitor` handles camera, save, sound, exploration, dialog, combat engine, pathfinding, puzzle objects, NPC collisions, and actor pools.

All visitors produce immutable `CFile`/`CFunction`/`CVarDecl` data classes. The pipeline assembles them into `CFile` instances, which `CEmitter` serializes to C text.

## Output Files

| File | Bank | Contents |
|------|------|----------|
| `main.c` | 0 (HOME) | Globals, input helpers, sprite helpers, sound driver, dialog/menu/HUD, system functions, `main()` |
| `bank1.c` | 1 | Scene enter/frame/exit functions, tileset guards |
| `game.h` | -- | Include guards, extern declarations, forward prototypes with `BANKED` |
| `zone_bankN.c` | N | Tilemap data arrays for dungeon zones |

## Dependencies

- **gbkt-backend-api**: `CodegenBackend` interface, `GenerationResult`, `ValidationResult`
- **gbkt-analysis**: `DefaultPipeline`, `PassContext`, analysis passes
- **gbkt-engine**: `GameIR`, all IR node types
- **gbkt-genre-rpg**: RPG IR nodes (characters, monsters, abilities, combat)

## Key Design Decisions

- **Typed C AST** eliminates bank-state-leak bugs. Each `CFile` carries an immutable `bank` field instead of relying on mutable `currentBank` state.
- **Visitors produce AST fragments, not strings.** The `CEmitter` is the single point of text serialization, which enables source map collection.
- **Post-processing operates on text**, not the AST, because dedup patterns (identical function bodies, identical constant arrays) are easier to detect after emission.
- **Sound and input helpers live in HOME bank** (bank 0) so they are always accessible without bank switching.
- **Header prototypes are auto-extracted** from the built `CFile` function lists via `CFunction.toPrototype()`, not manually enumerated. This guarantees every generated function has a matching prototype in `game.h` and eliminates a class of GBDK linker bugs.
