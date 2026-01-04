# gbkt Architecture

## Core Pattern

```
Kotlin DSL → Recording Context → IR Tree → C Code
```

`RecordingContext` captures DSL operations as IR nodes instead of executing them.
`CodeGenerator` traverses the IR tree and emits GBDK-compatible C code.

---

## File Map

**Core DSL** (`gbkt-core/src/commonMain/kotlin/gbkt/core/`)

| File | Purpose |
|------|---------|
| `Core.kt` | Primitives (`u8`/`u16`/`i8`), IR nodes, `RecordingContext`, `Expr`, `Condition` |
| `Game.kt` | `GameBuilder`, `Sprite`, `Scene`, palette DSL, `SpritePosition` |
| `CodeGenerator.kt` | IR → C code emission (orchestrates codegen modules) |
| `Entity.kt` | Entity-component system |
| `Animation.kt` | Sprite animation DSL |
| `StateMachine.kt` | State machine DSL |
| `Input.kt` | `dpad`/`buttons` handling |
| `Text.kt` | `print`/`printCentered` DSL |
| `Tween.kt` | Tweening DSL, `IRTween` node, `Easing` enum |
| `TileMap.kt` | Tilemap DSL with collision layer support |

**Code Generation** (`gbkt-core/src/commonMain/kotlin/gbkt/core/codegen/`)

| File | Purpose |
|------|---------|
| `DataCodegen.kt` | Tile data, map data, collision maps, sound data |
| `TweenCodegen.kt` | Easing lookup tables, tween update function |
| `TilemapCodegen.kt` | Collision helper functions |
| `CameraCodegen.kt` | Camera and transition system |
| `PathfindingCodegen.kt` | A* pathfinding implementation |
| `SaveCodegen.kt` | SRAM save system |
| `DialogCodegen.kt` | Dialog system |
| `MenuCodegen.kt` | Menu system |
| `PoolCodegen.kt` | Entity pool system |
| `VariablesCodegen.kt` | Variables and enums |
| `AnimationCodegen.kt` | Animation and state machines |
| `SceneCodegen.kt` | Scene functions |
| `AudioCodegen.kt` | Audio mixer system |
| `MainCodegen.kt` | Main function generation |

**Platform-Specific** (`jvmMain/` and `nativeMain/`)

| File | Purpose |
|------|---------|
| `RecorderHolder.kt` | Thread-local storage (expect/actual) |
| `AssetPipeline.kt` | PNG → GB tile conversion (JVM only) |

**Gradle Plugin** (`gbkt-gradle-plugin/`)

| File | Purpose |
|------|---------|
| `GbktPlugin.kt` | Plugin entry, task registration |
| `GenerateCTask.kt` | Kotlin DSL → C via reflection |
| `CompileRomTask.kt` | C → .gb ROM via GBDK |

---

## Key Types

| Type | Purpose |
|------|---------|
| `Expr` | Expression wrapper with operator overloading |
| `AssignableExpr` | Extends `Expr`, adds `set`, `+=`, `-=` |
| `Condition` | Boolean expression for `whenever` blocks |
| `IRStatement` | Sealed interface for all statement IR nodes |
| `IRExpression` | Sealed interface for all expression IR nodes |
| `StatementRecorder` | Collects IR nodes during recording |
| `GameBuilder` | Main DSL entry point |
| `CodeGenerator` | Converts IR tree to C string |

---

## IR Nodes

**Statements:** `IRAssign`, `IRIf`, `IRWhen`, `IRWhile`, `IRFor`, `IRCall`, `IRSceneChange`, `IRPrintAt`, `IRAnimationPlay`, `IRAnimationStop`, `IRStateMachineStart`, `IRStateMachineUpdate`

**Camera Statements:** `IRCameraUpdate`, `IRCameraSetPosition`, `IRCameraFollow`, `IRCameraStopFollow`, `IRCameraSnapTo`, `IRCameraShake`, `IRCameraShakeStop`

**Transition Statements:** `IRTransitionFadeOut`, `IRTransitionFadeIn`, `IRTransitionFlash`, `IRTransitionWipe`, `IRTransitionIris`

**Tween Statements:** `IRTween` (target, targetType, from, to, duration, easing)

**Expressions:** `IRLiteral`, `IRVar`, `IRBinary`, `IRUnary`, `IRCallExpr`, `IRTernary`, `IRArrayAccess`

**Camera Expressions:** `IRCameraX`, `IRCameraY`, `IRTransitionActive`

---

## Data Flow

```
gbGame("Name") { ... }
    ↓
GameBuilder executes inside RecordingContext
    ↓
Operations emit IR nodes to StatementRecorder
    ↓
GameBuilder.build() → Game object
    ↓
game.compile() → CodeGenerator.generate()
    ↓
C code string
```

---

## Adding New Features

1. **Add IR node** in `Core.kt`:
   ```kotlin
   data class IRMyFeature(val param: String) : IRStatement
   ```

2. **Add DSL function** in `Game.kt` (or relevant file):
   ```kotlin
   fun myFeature(param: String) {
       RecordingContext.current?.emit(IRMyFeature(param))
   }
   ```

3. **Add emission** in `CodeGenerator.kt`:
   ```kotlin
   is IRMyFeature -> line("my_feature(\"${stmt.param}\");")
   ```
