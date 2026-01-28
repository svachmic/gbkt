# Collision Module

AABB collision detection with sweep/continuous collision for moving objects.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `CollisionDetection.kt` | Simple AABB overlap checks | ~100 |
| `CollisionPrimitives.kt` | Primitive shapes and results | ~150 |
| `SweepCollision.kt` | Continuous collision detection | ~650 |

## Architecture

```
Entity hitboxes → AABB primitives → Sweep test → Collision response
```

## Quick Reference

### Simple Collision Check
```kotlin
// Check if two entities overlap
whenever(player collidesWith enemy) {
    takeDamage()
}

// Check against any in pool
whenever(player collidesWithAny enemies) {
    handleEnemyCollision()
}
```

### Sweep Collision (Continuous)
```kotlin
// Sweep collision prevents tunneling through objects
val result = sweep(
    entity = player,
    dx = player.vx,
    dy = player.vy,
    against = walls
)

if (result.collided) {
    // Slide along surface
    player.x += result.contactX
    player.y += result.contactY
}
```

## Collision Types

### AABB (Axis-Aligned Bounding Box)
- Simple rectangle collision
- Fast but only works for non-rotated rectangles
- Used for most game objects

### Sweep Collision
- Tests collision along movement path
- Prevents fast objects from passing through thin walls
- Returns contact point and normal

## SweepResult Structure

```kotlin
data class SweepResult(
    val collided: Boolean,      // Did collision occur?
    val hitTime: Float,         // 0.0-1.0 along movement
    val contactX: Int?,         // X position at contact
    val contactY: Int?,         // Y position at contact
    val normalX: Int?,          // Surface normal X (-1, 0, 1)
    val normalY: Int?           // Surface normal Y (-1, 0, 1)
)
```

## Collision Tags

```kotlin
// Define collision groups
val player by entity {
    hitbox(0, 0, 8, 16)
    collisionTag("player")
}

val enemy by entity {
    hitbox(0, 0, 8, 8)
    collisionTag("enemy")
}

// React to specific collisions
onCollision("player", "enemy") {
    takeDamage()
}
```

## Tilemap Collision

```kotlin
// Check against tilemap solid tiles
whenever(player collidesWithTilemap level) {
    stopMovement()
}
```

## Fixed-Point Math

Collision calculations use 8.8 fixed-point for precision:
- Integer part: 8 bits (0-255)
- Fractional part: 8 bits (1/256 precision)
- Prevents floating-point on Game Boy

## Known Issues

1. **SweepCollision.kt:166,172** - Throws if entity missing position/velocity
2. **CollisionCodegen.kt:250** - Integer overflow risk in fixed-point division
3. **SweepResult nullable fields** - contact_x/y, normal_x/y should use discriminated union

## Performance Notes

- Simple AABB is O(1) per pair
- Sweep collision is more expensive - use sparingly
- Spatial partitioning not implemented (fine for GB scale)

## Related Modules

- `gbkt-backend-gbdk/.../codegen/features/CollisionCodegen.kt` - Collision code generation (in backend)
- `entity/EntityComponents.kt` - Hitbox component definition
- `ir/CoreIR.kt` - Collision-related IR nodes
