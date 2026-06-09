# gbkt-ir -- Intermediate Representation

Lowest-level module in the gbkt stack. Defines every IR node type (expressions, script operations, game/scene/actor/world structures) and the visitor interfaces used by backends and analysis passes. Has zero gbkt dependencies.

## Dependencies

- **Depends on:** `org.json` (JSON serialization only) -- no gbkt modules
- **Used by:** `gbkt-lang`, `gbkt-engine`, `gbkt-world`, `gbkt-core`, `gbkt-all` (transitively all higher modules)
- **Boundary enforcement:** `validateModuleBoundaries` task runs during `check` to reject any dependency on `gbkt-lang`, `gbkt-engine`, `gbkt-world`, or `gbkt-core`

## Key Files

| File | Role |
|------|------|
| `Expr.kt` | `Expr` interface + 11 node types: `Literal`, `StringLiteral`, `VarRef`, `BinaryExpr`, `UnaryExpr`, `CallExpr`, `TernaryExpr`, `ArrayAccessExpr`, `PropertyAccessExpr`, `CastExpr`, `PoolGetActiveCount` |
| `ScriptOp.kt` | `ScriptOp` interface + 52 operation nodes (assign, control flow, movement, audio, dialog, UI, camera, pool, puzzle, physics, animation) |
| `Types.kt` | Core value types and enums: `VariableDef`, `ArrayDef`, `SpriteDef`, `PositionDef`, `HitboxDef`, `SourceLocation`, `CartridgeConfig`, `SoundEffectDef`; enums `VarType`, `BinaryOp`, `UnaryOp`, `AssignOp`, `MathFunction` |
| `CoreTypes.kt` | Extended types: `GBCColor`, `GBCPalette`, `MovementConfig`, `PhysicsConfig`, `AnimationStateDef`, `WaypointRoute`; enums `MovementStyle`, `FixedPointMode`, `WallResponse` |
| `GameIR.kt` | Top-level `GameIR` data class -- root of the entire IR tree (scenes, actors, variables, arrays, systems, etc.) |
| `SceneIR.kt` | `SceneIR` -- scene lifecycle (enter/frame/exit script lists) |
| `ActorIR.kt` | `ActorIR` -- actor definition (sprite, position, hitbox, properties) |
| `ActorPoolIR.kt` | `ActorPoolIR`, `ActorPoolConfig` -- object pooling for bullet-hell / platformer patterns |
| `WorldIR.kt` | `ZoneIR`, `ZoneTransitionIR`, `EncounterTableIR`, `GlobalFlagsIR`, `ExplorationGaugeIR` |
| `SystemIR.kt` | `SystemIR` interface + concrete systems: `DialogSystem`, `SoundSystem`, `SaveSystem`, `ExplorationSystem`, `CameraSystem`, `PathfindingSystem`, `GenericSystem` |
| `CombatEngineIR.kt` | `CombatEngineSystem`, `CombatantDef`, ATB/tactical-grid configs, turn-order strategies |
| `UITypes.kt` | Dialog, menu, and HUD definitions: `DialogDef`, `MenuDef`, `HudDef`, plus layout/style enums |
| `CollectionsIR.kt` | Low-level collection nodes: `IRCollHashTable`, `IRCollPool`, `IRCollRingBuffer`, `IRCollFixedSlots` with their operation nodes |
| `StructIR.kt` | `StructDef`, `StructFieldDef`, `CollElementType` interface -- user-defined struct support |
| `InventoryIR.kt` | `ItemDef`, `ItemCategoryDef`, `ContainerIR`, `DropTableIR`, item effect interfaces |
| `Ref.kt` | `Ref` (typed cross-reference by string ID) and `RefKind` enum (SCENE, ACTOR, SYSTEM, VARIABLE, ASSET, ZONE) |
| `AssetRef.kt` | `AssetRef` + `AssetType` enum -- type-safe asset references |
| `PlatformAnnotations.kt` | `PlatformAnnotatable` interface, `BankSlot`, `VRAMRange`, `OAMSlot` -- backend-hint annotations |
| `ExprVisitorI.kt` | Visitor interface for `Expr` nodes (10 visit methods) |
| `ScriptOpVisitorI.kt` | Visitor interface for `ScriptOp` nodes (56 visit methods) |
| `SystemIRVisitorI.kt` | Visitor interface for `SystemIR` nodes (8 visit methods) |
| `GameIRSerializer.kt` | `GameIRSerializer` object -- JSON round-trip for the full IR tree |
| `PuzzleObjectIR.kt` | `PuzzleObjectIR` interface + concrete types: switch, door, pressure plate, timed block, trigger |
| `NpcCollisionIR.kt` | `CollisionGroupIR`, `CollisionRuleIR`, `NpcCollisionConfig` |
| `EntityCollisionIR.kt` | `EntityCollisionConfig`, collision shape/mode/push-direction enums |
| `WaveSurvivalIR.kt` | `WaveSurvivalConfig`, `WaveDef`, scripted/procedural wave content |
| `Suggestions.kt` | `Suggestions` utility -- Levenshtein-based "did you mean?" for DSL error messages |

## Architecture

### Non-sealed interfaces + visitor dispatch

`Expr`, `ScriptOp`, and `SystemIR` are **non-sealed interfaces** (not `sealed`). This is intentional: the sealed constraint would force all implementations into a single module, which is already the case here, but using non-sealed interfaces allows future backend modules to define extension visitors without modifying this module.

Each interface declares an `accept(visitor)` method. Backends implement the corresponding visitor:

- `ExprVisitorI<R>` -- one `visit*` method per `Expr` subtype (10 methods)
- `ScriptOpVisitorI<R>` -- one `visit*` method per `ScriptOp` subtype (56 methods)
- `SystemIRVisitorI<R>` -- one `visit*` method per `SystemIR` subtype (8 methods)

This pattern gives backends exhaustive dispatch over all IR nodes without requiring `when` expressions, making it impossible to silently skip a new node type.

### IR tree structure

```
GameIR (root)
 +-- scenes: List<SceneIR>      (enter/frame/exit contain List<ScriptOp>)
 +-- actors: List<ActorIR>
 +-- variables / arrays
 +-- systems: List<SystemIR>    (dialog, sound, save, exploration, combat, ...)
 +-- zones: List<ZoneIR>        (world map data)
 +-- pools: List<ActorPoolIR>
 +-- items, structs, collections, puzzleObjects, ...
```

## Testing

```bash
./gradlew :gbkt-ir:test
```

Tests cover: struct byte-size calculations, actor pool IR construction, puzzle object wiring, NPC collision rules, JSON serialization round-trips, and phase integration checks.

## Common Tasks

- **Add a new expression node:** Add a data class implementing `Expr` in `Expr.kt`, add a `visit*` method to `ExprVisitorI`, implement in all backends
- **Add a new script operation:** Add a data class implementing `ScriptOp` in `ScriptOp.kt`, add a `visit*` method to `ScriptOpVisitorI`, implement in all backends
- **Add a new system type:** Add a data class implementing `SystemIR` in the relevant file, add a `visit*` method to `SystemIRVisitorI`
- **Serialize a new node:** Add `serialize*`/`deserialize*` methods in `GameIRSerializer.kt`
- **Check module boundaries:** `./gradlew :gbkt-ir:validateModuleBoundaries`
