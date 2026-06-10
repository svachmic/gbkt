# gbkt-engine — Engine Runtime Types

Shared type definitions (interfaces, enums, data classes) that represent engine-level concepts used at compile time. This module is a thin, dependency-light layer that `gbkt-core`, backends, and genre modules all depend on for common type contracts.

## Dependencies

- **Depends on:** `gbkt-lang` (transitive: `gbkt-ir`)
- **Used by:** `gbkt-core`, `gbkt-backend-gbdk`, `gbkt-genre-platformer`, `gbkt-genre-sport`

## Sub-packages

| Package | Key Types | Description |
|---------|-----------|-------------|
| `combat/` | `CombatState` (interface), `COMBAT_INIT`, `PLAYER_TURN`, `ENEMY_TURN`, `VICTORY`, `DEFEAT` | State-machine interface for turn-based combat; five predefined state constants |
| `input/` | `Button` (enum), `DpadDirection` (enum), `InputState` (interface) | Game Boy button/d-pad enums and an input-polling interface (`isHeld`, `isPressed`, `isDpadHeld`, `isDpadPressed`) |
| `entity/` | `Positionable` (interface), `Movable` (interface), `Hitbox`, `EntityState` | Position/movement contracts and AABB hitbox + entity runtime state data classes |
| `pickup/` | `PickupDef`, `PickupZone`, `PickupSystemConfig`, `PickupDefBuilder`, `PickupZoneBuilder`, `PickupBuilder` | Pickup item definitions, spawn zones, system config, and their DSL builders |
| `inventory/` | `ItemEffect` (interface), `CATEGORY_CONSUMABLE`, `CATEGORY_KEY_ITEM` | Item effect contract and item-category constants |
| `scene/` | `SceneId` (value class), `SceneLifecycle` (interface), `FadeType` (enum), `SceneTransitionRequest` | Scene identity wrapper, lifecycle hooks (`onEnter`/`onFrame`/`onExit`), fade types (`NONE`, `FADE_BLACK`, `FADE_WHITE`), and transition requests |
| `graphics/` | `SpriteSize`, `AnimationFrame`, `AnimationDef`, `PaletteIndex` | Sprite dimensions, animation frame/definition data, and palette index wrapper |

## Design Notes

- **No logic, only types.** Files contain interfaces, enums, data classes, and constants. No code generation or DSL recording happens here.
- **Stable API surface.** Because multiple modules depend on these types, changes are breaking across the dependency graph. Prefer additive changes.
- **Naming convention.** Each file is named `<Domain>Types.kt` (except `PickupBuilder.kt` which holds DSL builders for the pickup sub-package).

## Testing

```bash
./gradlew :gbkt-engine:test
```
