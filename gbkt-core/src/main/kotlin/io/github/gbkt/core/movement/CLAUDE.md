# Movement Module

Abstract movement controller system supporting multiple movement styles for different game genres.

## Purpose

The movement module provides a genre-agnostic movement abstraction. Instead of hardcoding grid-based RPG movement, it defines interfaces that support:
- Grid-based movement (RPGs, puzzle games, dungeon crawlers)
- Physics-based movement (platformers, action games)
- Free-roam pixel movement (shooters, racing)
- Top-down smooth movement (Zelda-like)

## Files

| File | Purpose |
|------|---------|
| `MovementController.kt` | Core interfaces and implementations |
| `MovementControllerExtensions.kt` | GameBuilder property delegates |

## Key Types

### Movement Types

```kotlin
enum class MovementType {
    GRID,       // Snap to tiles (RPGs, puzzle games)
    PHYSICS,    // Velocity/acceleration (platformers)
    FREE_ROAM,  // Unrestricted pixel movement (shooters)
    TOP_DOWN    // Smooth with tile collision (Zelda-like)
}
```

### Movement Controller Interface

```kotlin
interface MovementController {
    val id: String
    val movementType: MovementType
    val tileSize: Int
    val speed: Int
    val collisionEnabled: Boolean
    val onMoveStatements: List<IRStatement>
    val onBlockedStatements: List<IRStatement>
    val onPositionChangeStatements: List<IRStatement>
    var systemIndex: Int
}
```

### Controller Implementations

- `GridMovementController` - Tile snapping, smooth interpolation, step callbacks
- `PhysicsMovementController` - Gravity, friction, jumping, air jumps
- `FreeRoamMovementController` - 8-direction, acceleration/deceleration
- `TopDownMovementController` - Pixel-perfect, hitbox configuration, tile triggers

## DSL Usage

```kotlin
// Grid-based RPG movement
val movement by gridMovement {
    tileSize(8)
    speed(4)  // frames per tile
    smoothInterpolation(true)
    allowDiagonal(false)

    onStep { checkEncounter() }
    onBlocked { playSound(bump) }
}

// Platformer physics
val movement by physicsMovement {
    tileSize(8)
    gravity(4)
    jumpVelocity(-64)
    maxSpeedX(48)
    maxSpeedY(80)
    airJumps(1)  // double jump

    onLand { playSound(land) }
}

// Top-down Zelda-like
val movement by topDownMovement {
    tileSize(8)
    speed(2)
    hitbox(8, 8)
    hitboxOffset(0, 8)  // feet hitbox

    onTileEnter { checkTriggers() }
    onBlocked { pushBack() }
}
```

## Helper Types

### Position
```kotlin
data class Position(
    val x: Int,      // Tile or pixel coordinate
    val y: Int,
    val subX: Int,   // Sub-pixel (physics/smooth modes)
    val subY: Int
)
```

### Velocity
```kotlin
data class Velocity(
    val vx: Int,  // Pixels/frame * 16 (fixed-point)
    val vy: Int
)
```

### Movement Bounds
```kotlin
data class MovementBounds(
    val minX: Int, val minY: Int,
    val maxX: Int, val maxY: Int
)
```

## Relationship to Exploration Module

The `movement/` module provides abstract movement, while `exploration/` provides:
- Dungeon crawling integration
- Encounter triggering on steps
- Torch/gauge systems
- Zone transitions

The exploration module uses `GridMovementController` internally for dungeon movement.

## Extending

To add a new movement type:

1. Add enum value to `MovementType`
2. Create new controller class implementing `MovementController`
3. Create builder class extending `MovementControllerBuilder`
4. Add delegate class in `MovementControllerExtensions.kt`
5. Add `GameBuilder` extension function
6. Add codegen in `gbkt-backend-gbdk/.../codegen/features/MovementControllerCodegen.kt`
