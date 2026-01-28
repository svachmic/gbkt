# Entity Module

ECS-inspired entity system with components, pools, and state management.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Entity.kt` | Core entity class with component access | ~400 |
| `EntityBuilder.kt` | DSL builder for entity definitions | ~200 |
| `EntityComponents.kt` | Component definitions (position, velocity, etc.) | ~300 |
| `CombatComponents.kt` | Combat-specific components (HP, stats) | ~150 |
| `Pool.kt` | Entity pool for batch management | ~550 |
| `PoolBuilder.kt` | DSL builder for pools | ~200 |
| `PoolStateBuilder.kt` | Pool state configuration | ~100 |
| `EntityRegistry.kt` | Global entity tracking | ~150 |
| `Interfaces.kt` | Shared interfaces | ~50 |

## Quick Reference

### Entity Definition
```kotlin
val player by entity {
    position(80, 72)
    velocity(0, 0)
    sprite(SpriteAsset("player.png")) {
        size = 8 x 16
        hitbox(0, 0, 8, 16)
    }
    states("idle", "walking", "jumping")
}
```

### Pool Definition
```kotlin
val enemies by pool(maxSize = 8) {
    entity {
        sprite(SpriteAsset("enemy.png"))
        position(0, 0)
        velocity(0, 0)
    }
}
```

### Property Access
```kotlin
// Safe access (throws if component missing)
player.x += 2
player.y -= 1

// Nullable access
player.xOrNull?.let { ... }
player.yOrNull?.let { ... }
```

## Components

### Position Component
- `x: Int` - X coordinate
- `y: Int` - Y coordinate
- Accessed via `entity.x`, `entity.y`

### Velocity Component
- `vx: Int` - X velocity
- `vy: Int` - Y velocity
- Accessed via `entity.vx`, `entity.vy`

### Sprite Component
- `sprite: SpriteRef` - Visual representation
- `animations: Map<String, Animation>` - Named animations
- `currentAnimation: String` - Active animation

### States Component
- `states: List<String>` - Available states
- `currentState: String` - Active state
- State machine for entity behavior

### Combat Components (CombatComponents.kt)
- `hp: Int`, `maxHp: Int` - Health points
- `sp: Int`, `maxSp: Int` - Skill points
- `atk`, `def`, `matk`, `mdef`, `agl` - Combat stats
- `level`, `exp` - Progression

## Pool Operations

```kotlin
// Spawn entity from pool
val enemy = enemies.spawn {
    position(100, 50)
}

// Despawn entity
enemies.despawn(enemy)

// Iterate active entities
enemies.forEach { entity ->
    entity.x += 1
}

// Count active
val count = enemies.activeCount
```

## Key Types

- `EntityRef` - Type-safe reference to entity
- `PoolRef` - Type-safe reference to pool
- `ComponentRef<T>` - Reference to component type

## Important Notes

1. **Property Access Throws** - `entity.x` throws if no position component
   - Use `entity.xOrNull` for safe access
   - This is a design decision, not a bug

2. **Pool Capacity** - Pools have fixed max size
   - `spawn()` returns null if pool is full
   - Use `canSpawn()` to check before spawning

3. **Entity Lifecycle**
   - Entities are created via `entity { }` DSL
   - Pool entities are spawned/despawned, not created/destroyed
   - Standalone entities exist for the game lifetime

## Related Modules

- `gbkt-backend-gbdk/.../codegen/core/PoolCodegen.kt` - Pool code generation (in backend)
- `ir/CoreIR.kt` - Entity-related IR nodes
- `graphics/Sprite.kt` - Sprite component details
