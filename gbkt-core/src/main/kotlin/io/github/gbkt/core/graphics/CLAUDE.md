# Graphics Module

Visual rendering systems for Game Boy: sprites, animations, camera, tilemaps, palettes, and particles.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Sprite.kt` | Sprite definition, position, collision, animations | ~520 |
| `Animation.kt` | Frame-based sprite animations with callbacks | ~610 |
| `Camera.kt` | Scrolling, following, shake, transitions | ~650 |
| `CameraBuilder.kt` | Camera configuration DSL | ~100 |
| `TileMap.kt` | Background tile maps and scrolling | ~200 |
| `TileMapDsl.kt` | Tilemap DSL builders | ~150 |
| `Palette.kt` | GBC color palette management | ~100 |
| `Particles.kt` | Particle system for effects | ~150 |

## Sprite System (Sprite.kt)

### Sprite Definition

```kotlin
val player = sprite(SpriteAsset("player.png")) {
    size = 8 x 16           // 8 wide, 16 tall (metasprite)
    position(80, 72)        // Initial position (sprite owns position)
    hitbox(2, 2, 4, 12)     // Collision hitbox (relative to sprite)
    palette = playerPalette // GBC palette
}
```

### Position Models

Two ways a sprite can have position:

1. **Sprite-owned position** - Sprite manages its own x/y variables:
```kotlin
position(80, 72)  // Creates sprite0_x, sprite0_y
player.x += 2     // Works directly
```

2. **Variable binding** - Sprite reads from external variables:
```kotlin
boundTo(playerX, playerY)  // References existing variables
```

### Collision Detection

```kotlin
// AABB collision between sprites
whenever(player collidesWith enemy) {
    takeDamage()
}

// Access effective hitbox (custom or full sprite bounds)
val box = player.effectiveHitbox  // Hitbox(xOffset, yOffset, width, height)
```

### Sprite Methods

```kotlin
player.moveTo(x, y)       // Move to position (recording context)
player.show()             // Make visible
player.hide()             // Make invisible
player.tile(3)            // Set tile index
player.flipX(true)        // Horizontal flip
player.setPalette(2)      // Change GBC palette at runtime
```

## Animation System (Animation.kt)

### Basic Animation Definition

```kotlin
sprite(SpriteAsset("player.png")) {
    size = 8 x 16
    position(80, 72)

    animations {
        "idle" plays (frames(0, 1) every 30.frames)
        "run" plays (frames(2, 3, 4, 5) every 6.frames)
        "jump" plays frame(6)  // Single static frame
    }
}
```

### Animation with Regions

Named regions organize sprite sheets:

```kotlin
regions {
    "idle" at 0 size 2
    "run" at 2 size 4
    "attack" at 6 size 6
}

animations {
    "idle" plays (region("idle") every 30.frames)
    "run" plays (region("run") every 6.frames)
}
```

### Animation Callbacks

```kotlin
animations {
    animation("death") {
        frames(10..15)
        delay(8)
        loop(false)
        onComplete { scene(gameoverScene) }
        onFrame(3) { sound.play(sfxDeath) }
    }
}
```

### Animation Playback

```kotlin
player.play(runAnimation)                    // Loop
player.play(attackAnimation, loop = false)   // One-shot
player.play(runAnimation, speed = 200)       // 2x speed
player.playOnce(attackAnim).then(idleAnim)   // Chain animations
player.stopAnimation()
player.pauseAnimation()
player.resumeAnimation()
player.setFrame(0)
```

## Camera System (Camera.kt)

### Camera Definition

```kotlin
val camera = camera {
    smoothing = 0.15f       // Follow lerp factor (0-1)
    offset(0, -16)          // Offset from target
    bounds(0..256, 0..256)  // World bounds
}
```

### Following

```kotlin
camera.follow(player)           // Follow sprite
camera.follow(player) {
    smoothing = 0.2f
    offset(0, -20)
}
camera.followX(player)          // X axis only
camera.followY(player)          // Y axis only
camera.stopFollow()
camera.snapTo(player)           // Instant position
camera.setPosition(100, 50)
```

### Screen Shake

```kotlin
camera.shake(4, 10.frames)      // intensity, duration
camera.impact(6)                // Quick punch shake
camera.shake {
    intensity = 6
    duration = 20.frames
    decay = Decay.EXPONENTIAL
}
camera.stopShake()
```

### Transitions

```kotlin
camera.fadeOut(30.frames) { scene(nextScene) }
camera.fadeIn(20.frames)
camera.flash(8.frames)
camera.flash(GBCColor.RED, 8.frames)
camera.wipeLeft(45.frames) { scene(level2) }
camera.wipeRight(duration)
camera.wipeUp(duration)
camera.wipeDown(duration)
camera.irisClose(60.frames, player) { scene(next) }
camera.irisOpen(60.frames, player)
```

### Transition State

```kotlin
whenever(camera.isTransitioning) {
    // Skip input during transitions
}
```

### Camera Position

```kotlin
camera.x  // Current X as Expr
camera.y  // Current Y as Expr

whenever(camera.x isAbove 100) { /* scrolled past 100 */ }
```

## Frame Timing

The `FrameTiming` type specifies animation/transition durations:

```kotlin
val Int.frames: FrameTiming  // 8.frames
val Int.framesOnce: FrameTiming  // Non-looping
```

## Palette System (Palette.kt)

GBC 4-color palettes:

```kotlin
val playerPalette = palette("player") {
    colors(
        GBCColor.WHITE,
        GBCColor.LIGHT_GRAY,
        GBCColor.DARK_GRAY,
        GBCColor.BLACK
    )
    slot = 0  // 0-7 for sprites, 0-7 for backgrounds
    type = PaletteType.SPRITE
}
```

## Game Boy Constraints

- **160x144 pixels** screen resolution
- **8x8 or 8x16 pixel** sprites
- **40 sprites max** visible (OAM limit)
- **10 sprites per scanline** (hardware limit)
- **4 colors per sprite** (palette)
- **8 sprite palettes** on GBC
- **8 background palettes** on GBC

## Related Modules

- `ir/SystemIR.kt` - Palette and screen IR nodes
- `ir/Transitions.kt` - Transition IR nodes
- `codegen/graphics/` - Graphics code generation
- `entity/Entity.kt` - Entities with sprite components
