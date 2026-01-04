# Sample Game: Comprehensive Feature Demo

This updated sample game demonstrates all major gbkt features through a simple platformer-style game.

## Features Demonstrated

### 1. Sprite Collision Detection
- **Player vs Coin**: Collecting coins increases score and spawns particle effects
- **Player vs Enemy**: Collision with enemy reduces lives and triggers camera shake
- **Tag-based collision**: Entities tagged for organized collision queries

**Code Example:**
```kotlin
whenever(player collidesWith coin) {
    coins += 1
    score += 100
    // Spawn particle burst...
}
```

### 2. Entity Physics System
- **Gravity simulation**: Custom velocity variables with gravity applied each frame
- **Ground detection**: Player lands on floor at groundY
- **Velocity-based movement**: Separate velocities for X and Y axes
- **Screen bounds**: Clamp player position to screen edges

**Code Example:**
```kotlin
// Physics variables
var playerVelX by u8Var(0)
var playerVelY by u8Var(0)
var gravity by u8Var(1)

// Apply gravity when airborne
whenever(isGrounded isEqualTo 0) {
    whenever(playerVelY isAbove 0) {
        playerVelY -= gravity
    }
}
```

### 3. Object Pools for Particle Effects
- **Pool of 8 particles**: Efficient sprite recycling for dynamic effects
- **Spawn/Despawn lifecycle**: Automatic management of particle lifetime
- **Pool update**: `particlePool.update()` processes all active particles
- **Auto-despawn**: Particles despawn when off-screen

**Code Example:**
```kotlin
val particlePool = pool("particle", size = 8) {
    position(0, 0)
    velocity(0, 0)

    onFrame {
        x += velX
        y += velY
        velY -= 1  // Gravity effect on particles
    }

    despawnWhen {
        y isBelow 8
        y isAbove 144
    }
}

// Spawn particles
particlePool.spawn {
    x set player.x
    y set player.y
    velY set -2
}
```

### 4. Tilemap Collision (Prepared)
- **Tiled JSON integration**: Ready for `level.json` with collision layer
- **Commented out**: Until actual Tiled map asset is created
- **API demonstrated**: `level.isBlocked(tileX, tileY)`

**Placeholder Code:**
```kotlin
// val level = tilemap("level.json") {
//     collisionLayer = "Collision"
// }
```

### 5. Tweening with Easing Functions
- **Coin bounce-in**: Coins drop from top with EASE_OUT easing
- **Smooth animations**: Pre-computed lookup tables for performance
- **Duration control**: Frame-based duration (30.frames)

**Code Example:**
```kotlin
// Coin falls in with smooth easing
tween(coin.y, from = 0, to = 100, duration = 30.frames, easing = Easing.EASE_OUT)

// After collecting, respawn with new tween
tween(coin.y, from = 0, to = 100, duration = 30.frames, easing = Easing.EASE_OUT)
```

### 6. Camera Effects
- **Smooth follow**: Camera tracks player with interpolation (smoothing = 0.15f)
- **Camera shake**: Triggered on enemy collision (intensity = 4, duration = 10 frames)
- **Fade transitions**: Fade out/in between scenes
- **Camera update**: `camera.update()` required each frame

**Code Example:**
```kotlin
val camera = camera {
    smoothing = 0.15f  // Smooth camera interpolation
}

// Setup smooth following
camera.follow(player)

// Shake on collision
camera.shake(4, 10.frames)

// Fade transitions
camera.fadeOut(30.frames) {
    scene("gameplay")
}
```

### 7. GBC Palettes and Animations
- **Custom color palettes**: 4-color GBC palettes for each entity type
- **Sprite regions**: Named animation regions (idle, run, jump, fall)
- **Auto-advancing animations**: Frame-based animation playback
- **Palette assignment**: Each sprite can have custom palette

**Code Example:**
```kotlin
val playerPalette = palette("player") {
    colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000)
}

sprite(SpriteAsset("player.png")) {
    palette = playerPalette
    regions {
        "idle" at 0 size 2
        "run" at 2 size 4
    }
    animations {
        "idle" plays (region("idle") every 30.frames)
        "run" plays (region("run") every 8.frames)
    }
}
```

### 8. Scene Management
- **Type-safe scene references**: `SceneRef` for compile-time safety
- **Enter/Exit hooks**: Initialize and cleanup logic
- **Frame timing**: `every.frame` for game loop logic
- **Scene transitions**: String-based for forward references

**Code Example:**
```kotlin
lateinit var titleScene: SceneRef
lateinit var gameplayScene: SceneRef

gameplayScene = scene("gameplay") {
    enter {
        // Initialize game state
        player.x set 80
        camera.follow(player)
        camera.fadeIn(20.frames)
    }

    every.frame {
        // Game loop logic
        particlePool.update()
        camera.update()
    }

    exit {
        // Cleanup
        particlePool.despawnAll()
    }
}
```

## Game Controls

- **A Button**: Move right / Increase horizontal velocity
- **B Button**: Move left / Decrease horizontal velocity
- **Start Button**: Jump (when grounded)
- **Select Button**: Return to title screen

## How to Build

```bash
# Generate C code from Kotlin DSL
./gradlew :sample-game:generateC

# Build ROM (requires GBDK-2020)
./gradlew :sample-game:buildRom
```

## Generated Code Stats

- **Lines of C**: ~1355 lines
- **File size**: ~30KB
- **Sprites**: 4 (player, coin, ball/enemy, particle)
- **Palettes**: 5 (player, coin, enemy, particle, background)
- **Pool size**: 8 particle entities
- **Max tweens**: 16 concurrent tweens
- **Scenes**: 3 (title, gameplay, gameover)

## Asset Files

Located in `sample-game/src/main/resources/sprites/`:
- `player.png` - 8x16 sprite (2 tiles)
- `coin.png` - 8x8 sprite (1 tile)
- `ball.png` - 8x8 enemy sprite (1 tile)
- `particle.png` - 4x4 particle sprite (1 tile)

See `sample-game/assets/README.md` for asset creation guidelines.

## Inline Comments

The RunnerGame.kt source code includes extensive inline comments explaining:
- How each feature works
- API usage examples
- When lifecycle hooks are called
- Why certain patterns are used

Read through the source to learn gbkt DSL patterns!
