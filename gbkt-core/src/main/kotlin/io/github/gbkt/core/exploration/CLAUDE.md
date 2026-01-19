# Exploration Module

Unified dungeon/world exploration controller for grid-based or smooth movement.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Exploration.kt` | Exploration system, builders, DSL functions | ~439 |

## Exploration System

Ties together movement, collision, encounters, and floor transitions:

```kotlin
val dungeonExploration by exploration {
    tileSize(8)                          // 8x8 pixel tiles
    movementStyle(MovementStyle.GRID)    // Grid-based movement
    movementSpeed(8)                     // Frames per tile

    // Resource gauges
    gauge("torch") {
        max(255)
        initial(255)
        decrementPerStep(1)
        onLow(50) { showMessage("Torch dimming...") }
        onDepleted { setFlag("torchOut") }
    }

    // Key counters
    keys("magic_key") {
        max(99)
        initial(0)
    }

    startZone(floor1)

    // Callbacks
    onStep { checkEncounter("battle") }
    onInteract { tryInteractWithObject(state.currentFloor, state.playerX, state.playerY) }
    onBlocked { sounds.bump.play() }
}
```

## Movement Styles

```kotlin
enum class MovementStyle {
    GRID,   // Snap to tiles (classic dungeon crawler)
    SMOOTH, // Pixel-based with tile collision (action RPG)
}
```

### Grid Movement

- Player moves one tile at a time
- `movementSpeed` = frames per tile movement
- Clean tile-aligned positioning

### Smooth Movement

- Pixel-by-pixel movement
- `movementSpeed` = pixels per frame
- Collision against tile boundaries

## Movement States

```kotlin
enum class MovementState {
    IDLE,          // Not moving
    WALKING,       // Moving between tiles
    BLOCKED,       // Hit wall/obstacle
    INTERACTING,   // Talking to NPC, opening chest
    TRANSITIONING, // Going through door/stairs
}
```

## Exploration Gauges

Resource tracking during exploration:

```kotlin
data class ExplorationGauge(
    val id: String,
    val maxValue: Int,
    val initialValue: Int,
    val decrementPerStep: Int,    // Decrement when completing a step
    val decrementPerFrame: Int,   // Continuous decrement
    val onDepletedStatements: List<IRStatement>,
    val onLowStatements: List<IRStatement>,
    val lowThreshold: Int,
)
```

### Gauge Builder

```kotlin
gauge("torch") {
    max(255)
    initial(255)
    decrementPerStep(1)              // Lose 1 per step
    decrementPerFrame(0)             // Or continuous drain
    onLow(50) { /* warning */ }
    onDepleted { /* torch out */ }
}

gauge("stamina") {
    max(100)
    initial(100)
    decrementPerStep(5)              // Running costs stamina
    onDepleted { /* force walk */ }
}
```

## Exploration Keys

Collectible counters for locked doors/chests:

```kotlin
data class ExplorationKey(
    val id: String,
    val maxCount: Int,
    val initialCount: Int,
)

// Definition
keys("magic_key") {
    max(99)
    initial(0)
}
```

## Callbacks

| Callback | Triggered When |
|----------|----------------|
| `onStep` | Player completes a step/tile |
| `onBlocked` | Movement blocked by wall |
| `onInteract` | A button pressed |
| `onWater` | Player enters water tile |
| `onPit` | Player falls in pit |

```kotlin
exploration {
    onStep {
        checkEncounter("battle")
        decrementTorch()
    }

    onBlocked {
        sounds.bump.play()
    }

    onInteract {
        tryInteractWithObject(state.currentFloor, state.playerX, state.playerY)
    }

    onWater {
        setFlag("swimming")
    }

    onPit {
        takeDamage(10)
        respawnAtCheckpoint()
    }
}
```

## DSL Functions

### refillTorch

Restore torch fuel (typically from sconces or items):

```kotlin
fun refillTorch(value: Int)

// Usage in sconce callback
sconce("torch1") {
    onLit { refillTorch(100) }
}
```

### tryInteractWithObject

Check for and trigger interactions at position:

```kotlin
fun tryInteractWithObject(
    floor: AssignableExpr,
    x: AssignableExpr,
    y: AssignableExpr,
)

// Usage in gameplay
whenever(buttons.a.pressed) {
    tryInteractWithObject(state.currentFloor, state.playerX, state.playerY)
}
```

## Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `tileSize` | 8 | Tile size in pixels (1-32) |
| `movementSpeed` | 4 | Speed (frames/tile or pixels/frame) |
| `movementStyle` | GRID | Movement type |
| `wallCollision` | true | Enable wall collision |
| `waterBlocks` | true | Water tiles block movement |
| `pitDamage` | 10 | Damage from falling in pit |
| `playerSprite` | null | Sprite to update during movement |

## Player Sprite Integration

When a player sprite is configured, the exploration system automatically calls `move_sprite()` to update the sprite's position during tile movement interpolation:

```kotlin
val playerEntity by entity {
    position(startX * TILE_SIZE, startY * TILE_SIZE)
    sprite(SpriteAsset("player.png")) {
        size = 8 x 16
    }
}

val exploration by exploration {
    tileSize(8)
    movementStyle(MovementStyle.GRID)
    movementSpeed(8)

    // Wire sprite for smooth position updates during movement
    playerEntity.sprite?.let { playerSprite(it) }

    startZone(floor1)
}
```

This enables smooth visual movement as the player transitions between tiles.

## Integration with Zones

```kotlin
// Define zone (dungeon floor)
val floor1 by zone {
    type(ZoneType.DUNGEON)
    name("Dungeon Level 1")
    map("entrance") { ... }
    encounters { ... }
}

// Link to exploration
val exploration by exploration {
    startZone(floor1)
    onStep { checkEncounter("battle") }
}
```

## Generated State Variables

The exploration system generates these runtime variables:

```c
UINT8 _exp_state;           // MovementState enum
UINT8 _exp_move_timer;      // Movement animation timer
UINT8 _exp_torch_fuel;      // Gauge values
UINT8 _exp_magic_key_count; // Key counts
```

## Related Modules

- `world/Floor.kt` - Dungeon floor definitions
- `world/MapObject.kt` - Interactable objects (chests, NPCs)
- `world/Encounter.kt` - Random battle encounters
- `rpg/Battle.kt` - Combat system for encounters
- `scene/Scene.kt` - Scene transitions from exploration
