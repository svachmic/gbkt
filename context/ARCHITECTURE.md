# gbkt Architecture

## Core Pattern

```
Kotlin DSL → Recording Context → IR Tree → C Code
```

`RecordingContext` captures DSL operations as IR nodes instead of executing them.
`CodeGenerator` traverses the IR tree and emits GBDK-compatible C code.

---

## File Map

**DSL Core** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/`)

| File | Purpose |
|------|---------|
| `RecordingContext.kt` | IR capture during DSL execution |
| `Conditionals.kt` | `whenever`, `condition` DSL |
| `Loops.kt` | `every.frame`, iteration constructs |
| `LogicBlock.kt` | Logic block definitions |
| `LogicBlockBuilder.kt` | Logic block construction |
| `GameScope.kt` | Game-level scope |
| `DslMarkers.kt` | DSL scope markers |
| `TypeAliases.kt` | Common type aliases |

**IR Nodes** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/`)

| File | Purpose |
|------|---------|
| `CoreIR.kt` | Core IR node definitions (IRStatement, IRExpression) |
| `CoreTypes.kt` | Primitives (`u8`/`u16`/`i8`), basic types |
| `Variables.kt` | Variable IR nodes |
| `ExpressionWrapper.kt` | `Expr` with 60+ operator overloads |
| `FixedPointTypes.kt` | Fixed-point number support |
| `DialogIR.kt` | Dialog system IR |
| `MenuIR.kt` | Menu system IR |
| `Transitions.kt` | Scene transition IR |
| `AudioIR.kt`, `SoundIR.kt` | Audio/sound IR |
| `BattleIR.kt`, `BattleMenuIR.kt` | Battle system IR |
| `StatsIR.kt`, `AbilityIR.kt` | RPG stats/abilities IR |
| `MonsterIR.kt`, `ItemIR.kt` | Monster/item IR |
| `StatusEffectIR.kt` | Status effects IR |
| `FlagsIR.kt` | Game flags IR |
| + 20 more IR files | Domain-specific IR nodes |

**Builder** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/builder/`)

| File | Purpose |
|------|---------|
| `GameBuilder.kt` | Main DSL entry point |
| `GameConfig.kt` | Game configuration |
| `GameBuilderFeatures.kt` | Feature registration |

**Code Generation** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/codegen/`)

Organized into subdirectories:

| Directory | Files | Purpose |
|-----------|-------|---------|
| `core/` | 7 files | Expression, Statement, Main, Scene, Pool, Variables, Data |
| `features/` | 8 files | Audio, Collision, Link, Movement, Pathfinding, Physics, Save, Tween |
| `graphics/` | 3 files | Animation, Camera, Tilemap |
| `rpg/` | 23 files | Battle, Stats, Ability, Monster, Item, StatusEffect, etc. |
| `ui/` | 4 files | Menu, Dialog, Cutscene, StatusBar |
| `world/` | 8 files | Zone, Encounter, Flags, MapObject, Exploration, etc. |
| `combat/` | 1 file | BattleEngineCodegen |
| `data/` | 2 files | StringsCodegen, TablesCodegen |

**Entity System** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/entity/`)

| File | Purpose |
|------|---------|
| `Entity.kt` | Entity definitions |
| `EntityBuilder.kt` | Entity construction DSL |
| `EntityComponents.kt` | Component definitions |
| `EntityRegistry.kt` | Entity tracking |
| `Pool.kt` | Object pools |
| `PoolBuilder.kt` | Pool construction |
| `Interfaces.kt` | Entity interfaces |

**Graphics** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/graphics/`)

| File | Purpose |
|------|---------|
| `Animation.kt` | Sprite animation DSL |
| `Camera.kt`, `CameraBuilder.kt` | Camera system |
| `Sprite.kt` | Sprite definitions |
| `TileMap.kt`, `TileMapDsl.kt` | Tilemap DSL with collision |
| `Palette.kt` | GBC palette support |
| `Particles.kt` | Particle effects |

**Input** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/input/`)

| File | Purpose |
|------|---------|
| `Input.kt` | D-pad, buttons handling |
| `InputBuffer.kt` | Input buffering for combos |

**Scene** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/scene/`)

| File | Purpose |
|------|---------|
| `Scene.kt` | Scene definitions |
| `SceneTransition.kt` | Scene transitions |
| `Transition.kt` | Transition effects |
| `Link.kt` | Scene linking |

**RPG System** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/rpg/`)

| File | Purpose |
|------|---------|
| `Character.kt` | Playable character definitions |
| `Stats.kt`, `StatOperations.kt` | Character/monster statistics |
| `Battle.kt` | Battle system orchestration |
| `BattleMenu.kt` | Battle menu configuration |
| `Ability.kt` | Ability definitions and effects |
| `Item.kt`, `Inventory.kt` | Items and inventory |
| `Equipment.kt` | Equipment system |
| `Monster.kt` | Monster definitions and AI |
| `StatusEffect.kt` | Status effect definitions |
| `Damage.kt` | Damage types and calculation |
| `Leveling.kt` | Experience and level-up system |
| `TargetSelection.kt` | Target selection modes |
| `TurnOrder.kt` | Turn order strategies |
| `CombatState.kt` | Combat state management |
| `ActionExecution.kt` | Action execution system |

**World System** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/world/`)

| File | Purpose |
|------|---------|
| `Encounter.kt` | Random encounter tables |
| `Flags.kt` | Global flag system |

**Combat Engine** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/combat/`)

| File | Purpose |
|------|---------|
| `BattleEngine.kt` | Abstract combat system for different genres |
| `BattleEngineExtensions.kt` | GameBuilder extensions |

**Movement Controller** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/movement/`)

| File | Purpose |
|------|---------|
| `MovementController.kt` | Abstract movement system |
| `MovementControllerExtensions.kt` | GameBuilder extensions |

**Exploration** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/exploration/`)

| File | Purpose |
|------|---------|
| `Exploration.kt` | Dungeon crawling system |

**Game Flow** (`gbkt-core/src/main/kotlin/io/github/gbkt/core/flow/`)

| File | Purpose |
|------|---------|
| `GameFlow.kt` | Scene flow configuration |
| `PauseMenu.kt` | Pause menu system |
| `SaveMenu.kt` | Save/load menu |

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

**RPG Combat Statements:**
- `IRDamage` - Apply damage to target
- `IRHeal` - Heal target HP/SP
- `IRInflictStatus` - Apply status effect
- `IRRemoveStatus` - Remove status effect
- `IRApplyStatModifier` - Temporary stat change
- `IREquipItem` - Equip item to character
- `IRUnequipItem` - Unequip item from slot
- `IRUseItem` - Use consumable item
- `IRAddToInventory` - Add item to inventory
- `IRRemoveFromInventory` - Remove item from inventory

**RPG State Expressions:**
- `IRGetStat` - Get character/monster stat value
- `IRGetMaxStat` - Get max stat value
- `IRHasStatus` - Check if target has status
- `IRIsEquipped` - Check if item is equipped
- `IRInventoryContains` - Check inventory for item
- `IRInventoryCount` - Count item in inventory
- `IRGetLevel` - Get character level
- `IRGetExp` - Get character experience

**Battle Expressions:**
- `IRBattleState` - Current battle state
- `IRAlivePartyCount` - Count of alive party members
- `IRAliveEnemyCount` - Count of alive enemies
- `IRCurrentTurn` - Current combatant index
- `IRTargetIndex` - Selected target index

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

1. **Add IR node** in `ir/` directory (new file or add to relevant IR file):
   ```kotlin
   // ir/MyFeatureIR.kt
   data class IRMyFeature(val param: String) : IRStatement
   ```

2. **Add DSL function** in `builder/GameBuilder.kt` or domain-specific builder:
   ```kotlin
   // builder/GameBuilder.kt or ui/MenuBuilder.kt, rpg/Character.kt, etc.
   fun myFeature(param: String) {
       RecordingContext.current?.emit(IRMyFeature(param))
   }
   ```

3. **Add emission** in appropriate codegen file in `codegen/` subdirectory:
   ```kotlin
   // codegen/features/MyFeatureCodegen.kt or relevant codegen file
   is IRMyFeature -> line("my_feature(\"${stmt.param}\");")
   ```

See [DEVELOPER_EXPERIENCE.md](DEVELOPER_EXPERIENCE.md) for detailed patterns.
