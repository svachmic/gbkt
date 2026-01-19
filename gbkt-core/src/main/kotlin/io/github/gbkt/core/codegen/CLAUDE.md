# Code Generation Module

Transforms IR (Intermediate Representation) nodes into GBDK-compatible C code.

## Architecture

```
IR Nodes → Codegen Visitors → C StringBuilder → Output Files
```

## Directory Structure

```
codegen/
├── CodegenConstants.kt    # Shared constants
├── core/                  # Core codegen (expressions, statements, scenes)
├── features/              # Feature codegen (physics, audio, save, etc.)
├── graphics/              # Graphics codegen (camera, animation, tilemap)
├── rpg/                   # RPG codegen (battle, stats, abilities, etc.)
├── ui/                    # UI codegen (menu, dialog, cutscene)
└── world/                 # World codegen (floors, encounters, flags)
```

## Core Codegen (`core/`)

| File | Purpose | LOC |
|------|---------|-----|
| `ExpressionCodegen.kt` | IR expressions → C expressions | ~500 |
| `StatementCodegen.kt` | IR statements → C statements | ~1300 |
| `MainCodegen.kt` | main() function generation | ~150 |
| `SceneCodegen.kt` | Scene lifecycle code | ~200 |
| `PoolCodegen.kt` | Entity pool management | ~300 |
| `VariablesCodegen.kt` | Variable declarations | ~150 |
| `DataCodegen.kt` | Static data tables | ~200 |

## Features Codegen (`features/`)

| File | Purpose |
|------|---------|
| `AudioCodegen.kt` | Sound/music playback |
| `CollisionCodegen.kt` | AABB and sweep collision |
| `LinkCodegen.kt` | Scene transitions |
| `PathfindingCodegen.kt` | A* pathfinding |
| `PhysicsCodegen.kt` | Velocity and gravity |
| `SaveCodegen.kt` | Save/load to SRAM |
| `TweenCodegen.kt` | Property animation |

## Graphics Codegen (`graphics/`)

| File | Purpose |
|------|---------|
| `AnimationCodegen.kt` | Sprite animation frames |
| `CameraCodegen.kt` | Camera follow, shake, transitions |
| `TilemapCodegen.kt` | Tilemap rendering |

## RPG Codegen (`rpg/`)

| File | Purpose | LOC |
|------|---------|-----|
| `StatsCodegen.kt` | Stat read/write/modify | 129 |
| `DamageCodegen.kt` | Damage calculation | 129 |
| `AbilityCodegen.kt` | Ability execution | 868 |
| `ItemCodegen.kt` | Item usage | 585 |
| `MonsterCodegen.kt` | Monster instantiation | 1,058 |
| `StatusEffectCodegen.kt` | Effect application | 675 |
| `EquipmentCodegen.kt` | Equip/unequip | 325 |
| `LevelingCodegen.kt` | Level-up logic | 352 |
| `CombatTraitsCodegen.kt` | Aspects/resistances | 514 |
| `CombatStateCodegen.kt` | Combat queries | 232 |
| `CombatFormulasCodegen.kt` | Hit/crit formulas | 344 |
| `TargetSelectionCodegen.kt` | Target resolution | 369 |
| `TurnOrderCodegen.kt` | Turn calculation | 254 |
| `CombatCoreCodegen.kt` | Battle core loop | 618 |
| `BattleCodegen.kt` | Battle state machine | 1,145 |
| `BattlePresentationCodegen.kt` | Visual feedback | 404 |
| `BattleMenuCodegen.kt` | Combat menus | 1,090 |
| `ActionExecutionCodegen.kt` | Action pipeline | 485 |

## UI Codegen (`ui/`)

| File | Purpose |
|------|---------|
| `MenuCodegen.kt` | Menu rendering and input |
| `DialogCodegen.kt` | Dialog boxes and text |
| `CutsceneCodegen.kt` | Cutscene sequences |
| `StatusBarCodegen.kt` | HP/SP bars |

## World Codegen (`world/`)

| File | Purpose |
|------|---------|
| `FloorCodegen.kt` | Dungeon floor definitions |
| `EncounterCodegen.kt` | Random encounter tables |
| `FlagsCodegen.kt` | Global boolean flags |
| `MapObjectCodegen.kt` | Chests, NPCs, objects |
| `ExplorationCodegen.kt` | Dungeon exploration movement |

## Common Patterns

### Line Generation
```kotlin
fun CodegenContext.generateSomething() {
    line("uint8_t x = 0;")
    block("if (condition)") {
        line("do_something();")
    }
}
```

### Expression Generation
```kotlin
fun generateExpr(expr: IRExpression): String = when (expr) {
    is IRLiteral -> "${expr.value}u"
    is IRVarRead -> expr.name
    is IRBinaryOp -> "(${generateExpr(expr.left)} ${expr.op} ${generateExpr(expr.right)})"
    else -> error("Unhandled: $expr")
}
```

### Delegation Pattern
```kotlin
// Many codegens delegate to specialized handlers
val result = statsCodegen.generateStatRead(expr)
    ?: damageCodegen.generateDamageExpr(expr)
    ?: error("Unhandled expression")
```

## Known Issues

1. **ExpressionCodegen.kt:292** - Side-effect in expression (comma operator)
2. **StatementCodegen.kt:1226** - CollisionResponse stub (comment only)
3. **CameraCodegen.kt:54** - Memory leak in transition callbacks
4. **AnimationCodegen.kt:43** - Silent failure for empty animations
5. **CollisionCodegen.kt:250** - Integer overflow risk in fixed-point

## TODOs in This Module

- ~~`BattlePresentationCodegen.kt:177` - Dialog integration~~ (DONE: CombatCoreCodegen.kt)
- ~~`BattlePresentationCodegen.kt:267` - Damage number sprites~~ (DONE: CombatCoreCodegen.kt)
- ~~`BattleCodegen.kt:439` - Monster status effect checking~~ (DONE: _combatant_can_act)
- ~~`ExplorationCodegen.kt:521` - Sprite position interpolation~~ (DONE: playerSprite option with move_sprite())
- `StatusBarCodegen.kt:195` - Sprite slot allocation

## Recent Additions (CombatCoreCodegen.kt)

- `_show_battle_message(const char* msg)` - Display battle messages at screen bottom
- `_battle_show_damage_number(...)` - Show floating damage numbers above targets
- `_combatant_can_act(target)` - Check if combatant can act (status effects)

## Recent Additions (StatusEffectCodegen.kt)

- `_effect_dot_damage[]` - Damage per turn lookup table
- `_effect_hot_heal[]` - Heal per turn lookup table
- `_effect_prevents_action[]` - Action prevention lookup table
- `_effect_base_duration[]` - Base duration lookup table
