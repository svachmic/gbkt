---
id: FEAT-CAMERA-EXTRAS
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-7"
triage_date: 2026-06-12
---

# FEAT-CAMERA-EXTRAS: Camera smoothing/deadzone/snapTo/followX/followY + wipe/iris/flash transitions

## Source

Removed from context/DSL_REFERENCE.md lines 1585–1698 (sections 7 and 8, commit removal-commit-TBD).

**Implemented today:** `CameraBuilder` in `gbkt-lang/.../dsl/SystemBuilders.kt:65` implements `follow(actor)` and `bounds(mapWidth, mapHeight)`. Script-level camera ops go through `cameraOp(CameraAction.FOLLOW/UNFOLLOW/SHAKE/MOVE_TO)`. `fade(fadeIn, frames)` is implemented as a `ScriptBuilder` method (ScriptBuilder.kt:447) — NOT a camera method. What is NOT implemented: `offset()` in CameraBuilder, `deadzone()` in CameraBuilder, range-form `bounds(0..256, 0..256)`, runtime `camera.update()`, runtime `camera.follow(player) { smoothing=...; offset(...) }` config block, `camera.followX(player)`, `camera.followY(player)`, `camera.shake { intensity=6; duration=20.frames; decay=Decay.EXPONENTIAL }` block form, `camera.impact(n)`, `camera.stopShake()`, wipe/iris/flash transitions, `camera.snapTo(player)` / `camera.snapTo(x, y)`, `camera.x` / `camera.y` read-only conditions.

## Why This Matters

Smooth follow with deadzone, single-axis follow, and snap-to are essential for polished platformers and RPGs. The shake builder form (with decay) is more ergonomic than the map-of-args `cameraOp` form. Wipe and iris transitions add visual polish beyond simple fades.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

### Basic Setup (aspirational portions)

```kotlin
// Define camera with configuration
val camera = camera {
    smoothing = 0.15f           // Lerp factor (0 = instant, 1 = slow)
    offset(0, -16)              // Look-ahead offset from target
    deadzone(24 x 16)           // No movement within this area
    bounds(0..256, 0..256)      // World bounds clamp
}

// Use in scene
gameplayScene = scene("gameplay") {
    enter {
        camera.follow(player)   // Start following
        camera.fadeIn(20.frames)
    }

    frame {
        camera.update()         // Required: processes follow/shake/transitions
    }
}
```

### Smooth Follow

```kotlin
// Simple follow - camera tracks sprite/entity position
camera.follow(player)

// Follow with custom configuration
camera.follow(player) {
    smoothing = 0.2f            // Override smoothing
    offset(0, -16)              // Camera 16px above target
}

// Follow single axis
camera.followX(player)          // Only follow horizontally
camera.followY(player)          // Only follow vertically

// Stop following
camera.stopFollow()
```

### Screen Shake

```kotlin
// Basic shake - intensity in pixels, duration in frames
camera.shake(4, 10.frames)

// With decay configuration
camera.shake {
    intensity = 6
    duration = 20.frames
    decay = Decay.EXPONENTIAL   // or LINEAR, NONE
}

// Quick impact shake (short, punchy)
camera.impact(4)

// Stop shake
camera.stopShake()
```

### Transitions (wipe/iris/flash — not implemented)

Screen fades are a script-level op (`ScriptBuilder.fade`), not a camera method. Wipe, iris,
and flash transitions are not implemented in the current DSL.

```kotlin
// Fade out over 30 frames, then navigate (continuation runs after fade completes)
fade(fadeIn = false, frames = 30) {
    navigate(gameoverScene)
}

// Fade in over 20 frames
fade(fadeIn = true, frames = 20)
```

### Direct Positioning (aspirational portions)

```kotlin
// Set camera position directly
camera.setPosition(100, 50)

// Snap instantly to target (no smoothing)
camera.snapTo(player)
camera.snapTo(100, 50)

// Read camera position
whenever(camera.x isAbove 100) { /* ... */ }
```
