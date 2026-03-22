# codegen/visitor

Visitors convert IR subsystems into typed C AST nodes (`CFunction`, `CVarDecl`, `CDefine`). Each visitor is responsible for one domain.

## Visitor Catalog

| Visitor | Kind | Purpose |
|---------|------|---------|
| `ScriptOpVisitor` | object | Lowers `ScriptOp` IR nodes (assign, if, while, move, navigate, spawn, animate, fade, etc.) into `CStatement` lists |
| `ExprVisitor` | class | Converts `IRExpression` trees into `CExpr` nodes; handles AABB collision expressions, var refs, binary/unary ops, casts |
| `ActorVisitor` | object | Generates sprite data loads, OAM init, animation state machines, physics velocity vars, waypoint patrol routes, smooth movement |
| `SceneVisitor` | object | Produces scene enum `#define`s and enter/frame/exit `CFunction`s per scene |
| `DialogVisitor` | class | Emits dialog globals (`_dialog_speed`, VWF width tables), window-layer print helpers, dialog display functions |
| `MenuVisitor` | class | Builds menu rendering and input-handling functions (cursor movement, selection callbacks) |
| `HudVisitor` | class | Generates HUD global vars, show/hide/update functions, and wires `hud_update()` calls into scene frame functions |
| `InventoryVisitor` | class | Item catalog constants, container add/remove/count/contains functions, drop table roll functions, PRNG state |
| `CombatVisitor` | class | Turn-based and ATB combat state machines, damage/trigger functions, wave survival, hook call sites |
| `RpgVisitor` | class | Character stats, ability dispatch, status effects, monster AI behavior trees, leveling, equipment, party, merchants, crafting, roguelike, currency |
| `SoundVisitor` | class | Sound driver globals (channel state arrays), NRxx register write functions, music play/stop/pause/resume |
| `CollisionVisitor` | class | Tile collision `const` arrays and map collision lookup functions per scene |
| `GBDKSystemVisitor` | class | Camera, save, exploration, pathfinding, combat engine, puzzle objects, entity collisions, NPC collisions, actor pools, audio mixer |

## Conventions

- `object` visitors are stateless; `class` visitors receive `GameIR` in their constructor for cross-cutting queries.
- `ScriptOpVisitor` is the most heavily used visitor -- it is called transitively whenever a scene's enter/frame/exit script ops are lowered.
- `ExprVisitor` is consumed by both `ScriptOpVisitor` (statement-level expressions) and `ActorVisitor` (animation frame expressions).
