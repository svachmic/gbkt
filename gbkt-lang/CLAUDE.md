# gbkt-lang -- DSL Builders

Provides the user-facing Kotlin DSL that records game definitions into IR nodes. Every DSL call (variable declaration, scene lifecycle, actor setup, input handling) is captured by a builder and converted to the `gbkt-ir` intermediate representation for downstream code generation.

## Dependencies

- **Depends on:** `gbkt-ir`
- **Used by:** `gbkt-engine`, `gbkt-world`, `gbkt-core`, `gbkt-genre-rpg`, `gbkt-genre-platformer`

## Key Files

| File | Role |
|------|------|
| `GameBuilder.kt` | Top-level entry point; `game { }` lambda configures actors, scenes, variables, zones, items, systems |
| `ScriptBuilder.kt` | Records imperative ops (`whenever`, `ifOp`, `navigate`, `moveBy`, `playSound`, etc.) into `ScriptOp` lists |
| `ScriptBuilderContext.kt` | Thread-local holder for the active `ScriptBuilder` -- enables operator extensions without explicit receiver |
| `VariableBuilders.kt` | `u8Var()`, `i8Var()`, `u16Var()`, `i16Var()`, `u8Array()` delegates; `AssignableVar`, `ArrayVar` value types; `GameBuilderContext` thread-local |
| `SceneBuilder.kt` | `scene { enter { } frame { } exit { } }` lifecycle recording; produces `SceneRef` for type-safe navigation |
| `ActorBuilder.kt` | Actor definition with `position`, `sprite`, `hitbox`, `movement`, `physics`, `animationStates`, custom props (`i8Prop`/`u8Prop`); `ActorRef` with `.x`, `.y`, `.visible` properties |
| `ExprBuilder.kt` | Arithmetic/comparison/bitwise/logical operators on `Expr` (e.g. `plus`, `isAbove`, `logicalAnd`, `shl`, `inv`, type casts) |
| `InputBuilders.kt` | `dpad` and `buttons` objects; `InputRef` with `.held`, `.pressed`, `.released` properties |
| `UIBuilders.kt` | `DialogBuilder`, `MenuBuilder`, `HudBuilder` (bars, numbers, icons); `DialogHandle.say()`, `MenuHandle.show()/hide()` |
| `WorldBuilders.kt` | `ZoneBuilder` (tileset, tiles, collision, encounters, transitions), `FlagsBuilder`, `GaugeBuilder`, `KeyBuilder` |
| `CollectionBuilders.kt` | Generic data structures: `HashTableRef`, `PoolRef`, `RingBufferRef`, `FixedSlotsRef` with delegates |
| `StructBuilder.kt` | `struct { }` for user-defined C structs; `StructVar.get()` field access |
| `InventoryBuilders.kt` | `ItemBuilder`, `ContainerBuilder`, `DropTableBuilder`, `ItemCatalogBuilder` |
| `SystemBuilders.kt` | `CameraBuilder`, `SaveDataBuilder`, `ExplorationBuilder`, `PathfindingBuilder`, `SoundEffectBuilder`, `ConfigBuilder` |
| `DslMarkers.kt` | `@GbktDsl` annotation preventing scope leakage |
| `RefRegistry.kt` | Deferred name resolution -- `ref("id")` creates a pending reference resolved at `build()` time |
| `Errors.kt` | `DSLValidationError` for builder constraint violations |
| `SourceLocationCapture.kt` | `captureV2Location()` for diagnostic source mapping |

## Architecture

### Recording Context Pattern

The DSL uses two thread-local context objects to avoid passing builder references explicitly:

1. **`GameBuilderContext`** -- holds the active `GameBuilder` so that variable/array delegates can auto-register definitions. Also tracks transient variable names for save/load exclusion.
2. **`ScriptBuilderContext`** -- holds the active `ScriptBuilder` so that operator extensions on `AssignableVar`, `ActorPropertyRef`, and `ArrayVar` can emit `ScriptOp` nodes into the enclosing block.

Both use the same idiom: `with(builder) { block() }` sets the thread-local, executes the lambda, and restores the previous value in a `finally` block (supporting nested contexts).

### DSL Call Flow

```
game { }  -->  GameBuilder  -->  Game (IR)
  scene { enter { } frame { } }  -->  SceneBuilder  -->  SceneDef (IR)
    whenever(cond) { body }  -->  ScriptBuilder.whenever()  -->  IRIf (IR)
      score += 10  -->  AssignableVar.plusAssign()  -->  IRAssign (IR)
```

### Delegate Pattern

Variables use Kotlin property delegates (`provideDelegate` / `getValue` / `setValue`):
- `var score by u8Var(0)` creates a `VarDelegate` that registers a `VariableDef` with the `GameBuilder` and returns an `AssignableVar` with full operator support.
- `val ball by actor { }` uses `ActorDelegate.provideDelegate` to infer the actor name from the Kotlin property name.

## Testing

```bash
./gradlew :gbkt-lang:test
```

## Common Tasks

- **Add a new DSL keyword:** Create a builder method in the appropriate builder class, have it construct the corresponding IR node, and register it with `GameBuilder` or emit it via `ScriptBuilder`.
- **Add operator overloads:** Extend `AssignableVar`, `ActorPropertyRef`, or `Expr` in `VariableBuilders.kt`, `ActorBuilder.kt`, or `ExprBuilder.kt`. Operators emit `ScriptOp` nodes via `ScriptBuilderContext.current`.
- **Add a new data structure:** Follow the pattern in `CollectionBuilders.kt` -- create a `*Ref` value type, a `*Delegate` for `by` syntax, and top-level factory functions.
- **Add a new scene lifecycle hook:** Extend `SceneBuilder` with a new lambda property and wire it into `SceneBuilder.build()`.
