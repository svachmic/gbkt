---
id: FEAT-ENTITY-POOL-LIFECYCLE
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-5"
triage_date: 2026-06-12
---

# FEAT-ENTITY-POOL-LIFECYCLE: Sprite / lifecycle pool (position, velocity, sprite, onSpawn, onFrame, despawnWhen, spawn, spawnAt, trySpawn, forEachActive, despawnAll)

## Source

Removed from context/DSL_REFERENCE.md lines 1316–1471 (commit d6e1e5f7).

**Implemented today:** `pool(elementType, capacity)` and `pool(structDef, capacity)` data-pool delegates in `gbkt-lang/.../dsl/CollectionBuilders.kt:510,520`. These are data pools — they store data structures, not sprite entities with position/sprite/lifecycle. What is NOT implemented: `pool("bullet", size = 8) { position(0,0); velocity(0,0); sprite(asset) { }; state { }; onSpawn { }; onFrame { }; despawnWhen { }; onDespawn { } }` block form; `PoolRef.spawn { }`, `PoolRef.spawnAt(x, y)`, `PoolRef.trySpawn { } orElse { }`, `PoolRef.activeCount`, `PoolRef.hasSpace`, `PoolRef.isFull`, `PoolRef.forEachActive { }`, `PoolRef.despawnAll()`, `PoolRef.despawnWhere { }`.

## Why This Matters

Entity-pool DSL with sprite/lifecycle blocks would make bullet-hell, particle effects, and enemy waves dramatically simpler — replacing manual array management with declarative spawn/despawn and per-entity callbacks. The current data-pool builder only stores data, requiring manual frame-loop code to drive entity behavior.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

### Pool Definition

```kotlin
val bullets = pool("bullet", size = 8) {
    position(0, 0)                    // Each entity has x, y position
    velocity(0, 0)                    // Optional: velX, velY (signed)

    sprite(asset("sprites/bullet.png")) {
        size(4, 4)
        hitbox(0, 0, 4, 4)
    }

    // Per-entity custom state
    state {
        val timer by u8Var()          // Creates bullet_0_timer, bullet_1_timer, etc.
        val damage by u8Var(10)       // With default value
    }

    // Lifecycle hooks
    onSpawn {
        play("fly")
        timer set 120                 // 2 seconds at 60fps
    }

    onFrame {
        y -= 4                        // Move up
        timer -= 1
    }

    // Auto-despawn conditions (entity despawns when ANY is true)
    despawnWhen {
        y isBelow 8                   // Off-screen top
        timer isEqualTo 0             // Timer expired
        isAnimationComplete           // One-shot animation finished
    }

    onDespawn {
        hide()
    }
}
```

### Spawning Entities

```kotlin
gameplayScene = scene("gameplay") {
    frame {
        bullets.update()              // REQUIRED: Updates all active entities

        whenever(buttons.a.pressed) {
            // Simple spawn with init block
            bullets.spawn {
                x set player.x
                y set player.y
            }

            // Spawn at position (shorthand)
            bullets.spawnAt(player.x, player.y) {
                this["damage"] set 20 // Access custom state
            }

            // Try spawn with fallback
            bullets.trySpawn {
                x set player.x
            } orElse {
                // Pool full - handle gracefully
            }
        }
    }
}
```

### Pool Queries

```kotlin
// Check active count
whenever(bullets.activeCount isEqualTo 0) {
    // No bullets active
}

// Check if pool has space
whenever(bullets.hasSpace) {
    bullets.spawn { /* ... */ }
}

// Check if pool is full
whenever(bullets.isFull) {
    // Show "MAX" indicator
}
```

### Iterating Active Entities

```kotlin
bullets.forEachActive {
    // 'this' is the current entity scope
    whenever(collidesWith(enemy)) {
        enemy.takeDamage(this["damage"])
        despawn()
    }
}
```

### Bulk Operations

```kotlin
bullets.despawnAll()                  // Clear all bullets

bullets.despawnWhere { x isAbove 160 } // Conditional bulk despawn
```

### Lifecycle Scope Properties

Inside `onSpawn`, `onFrame`, `onDespawn`, and `spawn` blocks:

```kotlin
// Position
x                    // AssignableExpr for X position
y                    // AssignableExpr for Y position

// Velocity (if velocity() was called)
velX                 // AssignableExpr for X velocity
velY                 // AssignableExpr for Y velocity

// Sprite operations
play("animation")    // Play animation
show()               // Show sprite
hide()               // Hide sprite

// Custom state (from state {} block)
this["timer"]        // Access custom field
this["damage"]       // Access custom field

// Index
index                // Current entity's pool index (0..size-1)

// Lifecycle control
despawn()            // Return this entity to pool

// Animation state
isAnimationComplete  // Condition: current animation finished
isPlaying("name")    // Condition: specific animation playing
```

### Generated C Code

For a pool with size 4, the generated code includes:
- Per-entity static variables (unrolled for performance)
- Pointer arrays for indexed access
- `spawn()`, `despawn()`, `update()` functions
- Active count tracking
