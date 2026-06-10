# gbkt-genre-platformer -- Platformer Genre Plugin

Provides platformer-specific DSL constructs and GBDK code generation for side-scrolling games: physics (gravity, jump, wall-jump), camera modes, platforms, hazards, collectibles, ladders, and goal zones.

## Dependencies
- **Depends on:** `gbkt-lang`, `gbkt-backend-api`, `gbkt-backend-gbdk`, `gbkt-engine`
- **Used by:** `gbkt-backend-gbdk` (via ServiceLoader)

## Structure
- `domain/` -- Type definitions (`PlatformerTypes.kt`)
- `dsl/` -- Builder DSL (`PlatformerBuilders.kt`) and extension functions (`PlatformerExtensions.kt`)
- `codegen/` -- GBDK visitor (`PlatformerVisitor.kt`) for C generation

## Key Types
| Type | Role |
|------|------|
| `PlatformerPhysicsConfig` | Gravity, jump force, terminal velocity, coyote time, jump buffer, air control |
| `WallJumpConfig` | Wall-slide speed, i-frames, cooldown |
| `PlatformerCameraConfig` | Camera mode (smooth-follow / screen-lock), dead zone, scroll directions |
| `ParallaxLayer` | Background layer with independent scroll speed |
| `PlatformDef` | Platform definition with type (static, moving, crumble), move speed |
| `HazardDef` | Hazard tile with damage or instant death |
| `GoalZoneDef` | Level-end trigger area (position + size) |
| `CollectibleDef` | Pickup item (coin, key, powerup, custom) with value and tile ID |
| `LadderConfig` | Climbable region with climb speed and tile ID |
| `PlatformType` | Enum: `STATIC`, `MOVING`, `ONE_WAY`, `CRUMBLE` |
| `CameraScrollMode` | Enum: camera behavior mode |
| `CollectibleType` | Enum: `COIN`, `KEY`, `POWERUP`, `CUSTOM` |

## DSL Extensions
`platformerPhysics`, `platformerCamera`, `platform`, `hazard`, `goalZone`, `collectible`, `ladder` -- each takes a builder lambda and registers the config on the active scene/game.

## Codegen
`PlatformerVisitor` handles all platformer IR nodes and generates C functions: `buildPhysicsUpdateFunction`, `buildWallJumpFunction`, `buildCameraUpdateFunction`, `buildSmoothFollowBody`, `buildScreenLockBody`, `buildParallaxScrollFunction`, plus visitors for hazards, platforms, goals, collectibles, and ladders.

## Testing
```bash
./gradlew :gbkt-genre-platformer:test
```
