---
id: FEAT-PHYSICS-WORLD
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-9"
triage_date: 2026-06-12
---

# FEAT-PHYSICS-WORLD: Global physics world, gravity zones, tag(), maxVelocity, mass, useLocalFriction

## Source

Removed from context/DSL_REFERENCE.md lines 1704–1818 (commit removal-commit-TBD).

**Implemented today:** Per-actor `physics { }` block in `gbkt-lang/.../dsl/ActorBuilder.kt:500` with function-style API: `gravity(n: Int)`, `friction(n)` (via `MovementBuilder` at line 429), `velocity(dx, dy)`, `bounce(coefficient: Float)`, `maxFallSpeed(n: Int)`, `platformerMode(enabled: Boolean)`. `physicsUpdate(actor: ActorRef)` script op in `ScriptBuilder.kt:657`. What is NOT implemented: `maxVelocity = 4 to 8` pair form, `mass = 1.0f` property, top-level `physics { gravity = 0.5f }` world builder, `tag("player")` entity tagging, `physicsWorld.collide(tagA, tagB)`, `physicsWorld.update()`, `gravityZone(x, y, width, height) { gravity = 0.1f }`, `physics { useLocalFriction = true }` per-entity override.

## Why This Matters

A global physics world with tag-based collision response and gravity zones enables complex game physics (swimming sections, space levels, enemy-player collision response) without manual C escape-hatch code. Per-entity friction override would enable ice/mud surface effects.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

### Entity Physics Component (aspirational portions)

```kotlin
val player by actor {
    position(80, 72)
    velocity(0, 0)  // REQUIRED for physics

    physics {
        gravity = 0.5f    // Applied to velocityY each frame (0.5 = normal platformer)
        friction = 0.9f   // Multiplied to velocityX each frame (0.9 = normal)
        maxVelocity = 4 to 8  // Clamp velocityX to ±4, velocityY to ±8
        mass = 1.0f       // For collision response (heavier = harder to push)
    }
}

// Apply physics in frame loop
gameplayScene = scene("gameplay") {
    frame {
        player.applyPhysics()  // Applies gravity, friction, clamping
    }
}
```

**Gravity values:**
- `0.0f` = No gravity (space, swimming)
- `0.25f` = Light gravity (floating/moon)
- `0.5f` = Normal platformer gravity
- `1.0f` = Heavy gravity

**Friction values:**
- `1.0f` = No friction (ice, space)
- `0.9f` = Normal friction
- `0.8f` = High friction (sticky surfaces)
- `0.0f` = Instant stop

### Physics World (Global Physics)

For games with global physics rules and automatic collision response:

```kotlin
val physicsWorld = physics {
    gravity = 0.5f
    friction = 0.9f
    bounce = 0.3f  // Collision bounce coefficient (0.0-1.0)
}

// Enable collision response between tagged entities
val playerTag = tag("player")
val enemyTag = tag("enemy")

gameplayScene = scene("gameplay") {
    enter {
        physicsWorld.collide(playerTag, enemyTag)  // Auto-bounce on collision
    }

    frame {
        physicsWorld.update()  // Update all physics
    }
}
```

### Gravity Zones

Define rectangular areas with custom gravity:

```kotlin
val physicsWorld = physics {
    gravity = 0.5f

    // Water area with reduced gravity
    gravityZone(x = 0, y = 100, width = 160, height = 44) {
        gravity = 0.1f  // Slow fall in water
    }

    // Zero-gravity space section
    gravityZone(x = 100, y = 0, width = 60, height = 100) {
        gravity = 0f
    }

    // Reverse gravity zone
    gravityZone(x = 0, y = 50, width = 50, height = 50) {
        gravity = -0.3f  // Float upward
    }
}
```

### Per-Entity Friction Override

Make entities act as friction surfaces (ice, mud, etc.):

```kotlin
val icePlatform by actor {
    position(0, 100)
    physics {
        friction = 0.99f  // Very slippery
        useLocalFriction = true  // Use this instead of global friction
    }
}

val mudPatch by actor {
    position(50, 100)
    physics {
        friction = 0.7f  // Very sticky
        useLocalFriction = true
    }
}
```
