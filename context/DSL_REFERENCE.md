# DSL Reference

Complete reference for all gbkt DSL syntax and usage patterns.

## Variables and Assignments

```kotlin
// Variable declaration
var score by u8Var()
var playerX by u16Var()

// Assignments with 'set' for literals
playerX set 20
playerY set 100

// Compound assignments (new in v2.0!)
playerY -= 2       // playerY = playerY - 2
score += 10        // score = score + 10
gameSpeed *= 2     // multiply
damage /= 2        // divide
frame %= 60        // modulo
```

## Readable Comparisons

```kotlin
// Human-readable comparison aliases (new in v2.0!)
whenever(isJumping isEqualTo 0) { /* ... */ }
whenever(playerY isAbove groundY) { /* ... */ }
whenever(health isBelow 10) { /* ... */ }
whenever(score isAtLeast 100) { /* ... */ }
whenever(lives isAtMost 3) { /* ... */ }
whenever(playerX isBetween 10..150) { /* ... */ }

// Also available: isNotEqualTo, isGreaterThan, isLessThan
// Short forms: `is`, isNot

// Legacy operators still work: gt, lt, eq, neq, gte, lte
```

## Text Rendering DSL

```kotlin
// Screen control (new in v2.0!)
screen.clear()          // Clear screen
screen.showSprites()    // Show all sprites
screen.hideSprites()    // Hide all sprites
screen.showBackground() // Show background
screen.hideBackground() // Hide background

// Print text with positioning
print("SCORE: ", score) at (4 to 9)   // x=4, y=9
print("LIVES: ", lives) at (4 to 11)

// Centered text (auto-calculates X position)
printCentered("GAME OVER") at 6       // centered at row 6
printCentered("PRESS START") at 10
```

## Sprite Definition with Position

```kotlin
// RECOMMENDED: Sprite with owned position
val player = sprite(SpriteAsset("player.png")) {
    size = 8 x 16
    position(80, 72)            // Sprite owns its position (x=80, y=72)
    palette = playerPalette     // GBC color palette
    hitbox(2, 2, 4, 12)         // x-offset, y-offset, width, height
}

// Access position directly on the sprite
player.x += 2                   // Move right
player.y set 100                // Set Y position
whenever(player.x isAbove 160) { player.x set 0 }  // Wrap around

// ALTERNATIVE: Bind to external variables (advanced use)
var sharedX by u8Var()          // Shared variable
val sprite1 = sprite(SpriteAsset("a.png")) { boundTo(sharedX, y1) }
val sprite2 = sprite(SpriteAsset("b.png")) { boundTo(sharedX, y2) }  // Both follow sharedX

// Fluent spawning with .at() (copies position values)
val bullet = sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }.at(100, 50)

// Fluent following with .follow() (live binding to variables)
val shadow = sprite(SpriteAsset("shadow.png")) { size = 8 x 8 }.follow(player.x, player.y)

// Collision detection (uses type-safe SceneRef)
whenever(player collidesWith obstacle) {
    scene(gameoverScene)
}
```

## Sprite Animations

```kotlin
// Declare AnimationRefs before the sprite
lateinit var idleAnim: AnimationRef
lateinit var runAnim: AnimationRef
lateinit var jumpAnim: AnimationRef

// Define animations in sprite builder, capturing refs
val player = sprite(SpriteAsset("player.png")) {
    size = 8 x 16
    position(80, 72)  // Sprite owns its position

    animations {
        idleAnim = "idle" plays frames(0, 1) every 30.frames
        runAnim = "run" plays frames(2, 3, 4, 5) every 6.frames
        jumpAnim = "jump" plays frame(6)  // Single static frame
    }
}

// Play animations using type-safe refs
player.play(runAnim)
player.play(idleAnim, loop = true)
player.stopAnimation()
player.setFrame(2)
```

## State Machine DSL

```kotlin
// Define entity states (new in v2.0!)
// Assumes idleAnim, runAnim, jumpAnim are declared (see Sprite Animations)
val playerState = states("player") {
    "idle" {
        enter { player.play(idleAnim) }
        on(buttons.a.pressed) { goto("jump") }
        on(dpad.any) { goto("run") }
    }

    "run" {
        enter { player.play(runAnim) }
        tick { playerX += dpad.x * 2 }
        on(dpad.none) { goto("idle") }
        on(buttons.a.pressed) { goto("jump") }
    }

    "jump" {
        enter { player.play(jumpAnim) }
        tick {
            playerY -= playerVelY
            playerVelY -= 1
        }
        on(playerY isAtLeast groundY) { goto("idle") }
    }
}

// Use state machine in scene
gameplayScene = scene("gameplay") {
    enter { playerState.start("idle") }
    every.frame { playerState.update() }
}
```

## Control Flow

```kotlin
// Conditionals with whenever()
whenever(buttons.a.pressed) {
    playerVelY set 8
}

// D-pad also supports .held, .pressed, .released via DpadDirectionState:
whenever(dpad.right.held) { player.x += 2 }
whenever(dpad.up.pressed) { jump() }
// buttons.dpad provides unified access to d-pad state

// Nested conditions
whenever(isJumping isEqualTo 1) {
    whenever(playerVelY isAbove 0) {
        playerY -= 2
    }
}

// Branch for multiple conditions
branch {
    buttons.a.pressed then { jump() }
    buttons.b.pressed then { shoot() }
}
```

## Scenes and Timing

```kotlin
// Declare SceneRefs for type-safe transitions
lateinit var gameplayScene: SceneRef
lateinit var gameoverScene: SceneRef

// Scene definition (captures SceneRef)
gameplayScene = scene("gameplay") {
    enter { /* called once when entering */ }
    exit { /* called once when leaving */ }

    every.frame { /* runs 60 fps */ }
    every.second { /* once per second */ }
    every(5).frames { /* every 5 frames */ }
}

// Scene transitions (type-safe)
scene(gameplayScene)
scene(gameoverScene)
```

## Raw C Escape Hatch

```kotlin
// Rarely needed with new DSL features
raw("SHOW_SPRITES;")
raw("custom_function();")
```

## Dialog System DSL

gbkt provides a smooth, Compose-like dialog system for RPGs, adventures, and action games. Supports typewriter text effects, customizable borders, variable interpolation, and choice menus.

```kotlin
// === Quick Inline Dialog (action games) ===
// Perfect for item pickups, notifications, one-liners
gameplayScene = scene("gameplay") {
    every.frame {
        whenever(gotItem isEqualTo 1) {
            say("You found a key!")
            gotItem set 0
        }
    }
}

// === Named Dialog (RPGs, adventures) ===
// Define once, reuse everywhere
val elder = dialog("elder") {
    speaker = "Elder"           // Prefix: "Elder: Hello!"
    textSpeed = 3               // Characters per frame (higher = faster)
    box {
        position(0, 10)         // Tile coordinates (x, y)
        size = 20 x 6           // Width x Height in tiles
        border = BorderStyle.SIMPLE  // NONE, SIMPLE, ROUNDED, DOUBLE
        padding = 1
    }
}

lateinit var villageScene: SceneRef
lateinit var questScene: SceneRef

villageScene = scene("village") {
    enter {
        elder.say("Welcome, young hero!")
        elder.say("The kingdom needs you.")
    }

    every.frame {
        // Update typewriter effect (REQUIRED when dialog is active!)
        elder.tick()

        // Check dialog state
        whenever(elder.isComplete) {
            // Dialog finished displaying
        }
    }
}

// === Variable Interpolation ===
val shopkeeper = dialog("shop") { textSpeed = 2 }

shopkeeper.say("That'll be ", price, " gold.")
shopkeeper.say("You have ", coins, " coins!")

// === Choice Menus (with type-safe SceneRefs) ===
elder.choice("Accept quest", "Decline", "Tell me more") { selected ->
    whenever(selected isEqualTo 0) { scene(questScene) }
    whenever(selected isEqualTo 1) { scene(villageScene) }
    whenever(selected isEqualTo 2) { elder.say("Long ago...") }
}

// === Dialog Visibility ===
elder.show()    // Show dialog box (without text)
elder.hide()    // Hide dialog box

// === Border Styles ===
// BorderStyle.NONE   - No border, text only
// BorderStyle.SIMPLE - ASCII border: +--+
// BorderStyle.ROUNDED - Rounded corners (custom tiles)
// BorderStyle.DOUBLE  - Double-line border (custom tiles)
```

**Important Notes:**
- Always call `dialog.tick()` in `every.frame` when a dialog is active
- Use `dialog.isActive` and `dialog.isComplete` conditions to check state
- Press A button to advance text after typewriter completes
- Named dialogs require `dialog("name") { ... }` builder syntax
- Inline `say()` uses default dialog at bottom of screen

## Menu System DSL

gbkt provides a Compose-like menu system for title screens, pause menus, settings, and inventories. Menus handle navigation, selection, and input automatically.

```kotlin
// === Simple Vertical Menu (Title Screens, Pause Menus) ===
// Assumes gameplayScene, continueScene are declared as SceneRef
val mainMenu = menu("main") {
    style {
        position(5, 8)        // Tile coordinates (x, y)
        cursor = ">"          // Cursor character
        border = BorderStyle.ROUNDED
        spacing = 2           // Lines between items
    }

    item("NEW GAME") { scene(gameplayScene) }
    item("CONTINUE") { scene(continueScene) }
    item("OPTIONS") { open(optionsMenu) }
}

titleScene = scene("title") {
    enter {
        screen.clear()
        printCentered("MY GAME") at 3
        mainMenu.show()
    }

    every.frame {
        mainMenu.tick()  // REQUIRED - handles input and rendering
    }
}

// === Settings Menu with Controls ===
val optionsMenu = menu("options") {
    parent = mainMenu  // B button returns to parent

    style {
        position(3, 4)
        labelWidth = 10   // Width of label column
        valueWidth = 6    // Width of value column
    }

    // Toggle: A button or left/right to flip
    toggle("MUSIC", musicEnabled) {
        onChange { applyMusicSetting() }
    }

    // Slider: Left/right to adjust
    slider("VOLUME", volume, 0..7) {
        step = 1
        onChange { applyVolume() }
    }

    // Option cycle: Left/right to cycle through choices
    option("DIFFICULTY", difficulty) {
        choices("EASY", "NORMAL", "HARD")
    }

    item("BACK") { close() }
}

// === Submenu Navigation ===
// open(childMenu) - Push child menu onto stack
// close() - Pop current menu, return to parent
// B button auto-returns when parent is set

// === Grid Menu (Inventories) ===
val inventory = gridMenu("inventory") {
    grid(4, 3)  // 4 columns, 3 rows

    style {
        position(2, 2)
        cellSize = 2 x 2      // Cell size in tiles
        padding = 1           // Padding between cells
        border = BorderStyle.SIMPLE
    }

    itemsFrom(inventorySlots) { slot, index ->
        onSelect { useItem(index) }
        whenEmpty { /* nothing */ }
    }
}

// === Menu State Conditions ===
whenever(mainMenu.isVisible) { /* menu is shown */ }
whenever(mainMenu.isActive) { /* menu has focus */ }
val idx = mainMenu.selectedIndex  // Current cursor position

// === Cursor Styles ===
// cursor = ">"           // Custom character
// cursorStyle = CursorStyle.ARROW   // Predefined: ARROW, DASH, DOT
// cursorSprite = myCursor           // Sprite-based cursor
// cursorOffset = -8 to 0            // Pixel offset for sprite

// === Wrap Modes ===
// wrapMode = WrapMode.WRAP   // Wrap from last to first (default)
// wrapMode = WrapMode.CLAMP  // Stop at edges
```

**Important Notes:**
- Always call `menu.tick()` in `every.frame` when a menu is active
- Use `menu.show()` to display and focus a menu
- D-Pad navigates, A selects, B cancels/goes back
- For settings: use `toggle`, `slider`, `option` controls
- For inventories: use `gridMenu` with `itemsFrom` binding
- Submenus: set `parent = parentMenu` for automatic back navigation

## Save System DSL

gbkt supports type-safe SRAM persistence with auto-serialization, multi-slot saves, and data integrity validation.

```kotlin
// Define save data structure
val save = saveData("mygame") {
    var score by u16Field()           // 2 bytes (0-65535)
    var level by u8Field(default = 1) // 1 byte with default value
    var lives by u8Field(default = 3)
    var highScore by u16Field()
    var playerX by u8Field()
    var playerY by u8Field()
    var flags by flagsField()         // 8 boolean flags (1 byte)
    var inventory by arrayField(8)    // Fixed-size array (8 bytes)

    config {
        slots = 3                     // 3 save slots
        checksum = Checksum.CRC8      // Data integrity (NONE, XOR, CRC8, SUM16)
        magic = "GBKT"                // 4-char validation marker
        version = 1                   // Save format version
    }
}

// Usage in scenes (assumes SceneRefs are declared)
titleScene = scene("title") {
    enter {
        // Check if save exists before loading
        whenever(save.exists(slot = 0)) {
            print("CONTINUE") at (4 to 8)
        }
    }

    every.frame {
        whenever(buttons.a.pressed) {
            save.load(slot = 0)
            scene(gameplayScene)
        }
    }
}

gameplayScene = scene("gameplay") {
    every.frame {
        // Access save fields like normal variables
        save.score += 10

        // Compare with save data
        whenever(score isAbove save.highScore) {
            save.highScore set score
        }

        // Save on checkpoint
        whenever(buttons.start.pressed) {
            save.save()  // Saves to current slot
        }
    }
}

// Flags field for boolean states
save.flags.setBit(0)        // Set flag 0
save.flags.clearBit(1)      // Clear flag 1
save.flags.toggleBit(2)     // Toggle flag 2
whenever(save.flags.isSet(0)) { /* flag 0 is set */ }

// Array field access
save.inventory[0] set 5     // Set item at index 0
whenever(save.inventory[0] isEqualTo 5) { /* ... */ }

// Slot management
save.erase(slot = 1)        // Erase a slot
save.eraseAll()             // Erase all slots
save.copy(from = 0, to = 1) // Copy slot 0 to slot 1
```

**Note:** The cartridge type automatically upgrades to `MBC5_RAM_BATTERY` when using `saveData()`.

## Entity Pools

Entity pools manage collections of similar entities (bullets, particles, enemies) with lifecycle management.

### Pool Definition

```kotlin
val bullets = pool("bullet", size = 8) {
    position(0, 0)                    // Each entity has x, y position
    velocity(0, 0)                    // Optional: velX, velY (signed)

    sprite(SpriteAsset("bullet.png")) {
        size = 4 x 4
        hitbox(0, 0, 4, 4)
        animations {
            "fly" plays frames(0, 1) every 4.frames
            "explode" plays frames(2, 3, 4) every 3.frames once()
        }
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
    every.frame {
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

## Tweening/Easing

Smooth value interpolation for animations, UI effects, and transitions.

### Basic Tweening

```kotlin
// Tween a sprite position from 0 to 100 over 60 frames
tween(player.x, from = 0, to = 100, duration = 60.frames, easing = Easing.EASE_OUT)

// Tween a variable
tween(fadeAlpha, from = 0, to = 255, duration = 30.frames, easing = Easing.LINEAR)

// Tween with expression values
tween(enemy.x, from = Expr(startX), to = Expr(targetX), duration = 120.frames)
```

### Easing Functions

```kotlin
// Basic easing
Easing.LINEAR          // Constant speed
Easing.EASE_IN         // Start slow, end fast
Easing.EASE_OUT        // Start fast, end slow (default)
Easing.EASE_IN_OUT     // Slow at both ends
Easing.EASE_OUT_IN     // Fast at both ends

// Quadratic (t²)
Easing.EASE_IN_QUAD
Easing.EASE_OUT_QUAD
Easing.EASE_IN_OUT_QUAD

// Cubic (t³)
Easing.EASE_IN_CUBIC
Easing.EASE_OUT_CUBIC
Easing.EASE_IN_OUT_CUBIC

// Special effects
Easing.EASE_OUT_BOUNCE  // Bouncy landing
Easing.EASE_OUT_ELASTIC // Springy overshoot
```

### How It Works

- Pre-computed 256-entry lookup tables for each easing function
- Only tables for used easing types are generated (saves ROM space)
- Supports both increasing and decreasing tweens (signed math)
- Maximum 16 concurrent tweens (configurable via `MAX_TWEENS`)

### Usage in Scenes

```kotlin
introScene = scene("intro") {
    enter {
        // Slide title in from left
        tween(titleX, from = -80, to = 80, duration = 45.frames, easing = Easing.EASE_OUT_BOUNCE)
    }

    every.frame {
        // Tweens update automatically
    }
}
```

## Tilemap Collision

Access collision data from Tiled maps for physics and pathfinding.

### Configuring Collision Layer

```kotlin
val level = tilemap("level.json") {
    collisionLayer = "Collision"  // Name of collision layer in Tiled
}
```

### Checking Collisions

```kotlin
// Check if tile is blocked (by tile coordinates)
val blocked = level.isBlocked(tileX, tileY)

// Check if pixel position is blocked
val hit = level.isBlockedAtPixel(player.x, player.y)

// Get raw collision data
val collisionData = level.getCollisionData()
```

### Generated C Helpers

For each tilemap with collision data, these C functions are generated:

```c
// Check by tile coordinates
UINT8 level_is_blocked(UINT8 tile_x, UINT8 tile_y);

// Check by pixel coordinates  
UINT8 level_is_blocked_px(UINT16 pixel_x, UINT16 pixel_y);

// Get collision value (0 = walkable, >0 = blocked)
UINT8 level_get_collision(UINT8 tile_x, UINT8 tile_y);
```

## Camera System

The camera system provides scrolling, smooth follow, screen shake, and transitions.

### Basic Setup

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

    every.frame {
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

### Transitions

```kotlin
// Fade to/from black (with type-safe SceneRef)
camera.fadeOut(30.frames) {
    scene(gameoverScene)        // Runs after fade completes
}
camera.fadeIn(20.frames)

// Screen flash (white flash for damage/impacts)
camera.flash(8.frames)
camera.flash(GBCColor.RED, 8.frames)  // Custom color

// Wipe transitions
camera.wipeLeft(45.frames) { scene(level2Scene) }
camera.wipeRight(45.frames)
camera.wipeUp(45.frames)
camera.wipeDown(45.frames)

// Iris transitions (circle close/open)
camera.irisClose(60.frames, player) { scene(nextScene) }
camera.irisOpen(60.frames, 80, 72)   // Centered on coordinates

// Check transition state
whenever(camera.isTransitioning) {
    // Skip input during transitions
}
```

### Direct Positioning

```kotlin
// Set camera position directly
camera.setPosition(100, 50)

// Snap instantly to target (no smoothing)
camera.snapTo(player)
camera.snapTo(100, 50)

// Read camera position
whenever(camera.x isAbove 100) { /* ... */ }
```

### Automatic Sprite Offsetting

When a camera is defined, all sprite positions are automatically offset by the camera position. This means you can use world coordinates for sprites and they'll be correctly positioned on screen.

```kotlin
// Player at world position (200, 100)
player.x set 200
player.y set 100

// Camera at world position (100, 50)
camera.setPosition(100, 50)

// Player appears at screen position (100, 50) = (200-100, 100-50)
// This happens automatically - no manual offsetting needed!
```

## Physics

gbkt provides a complete physics system for platformers and action games with gravity, friction, collision response, and gravity zones.

### Entity Physics Component

Add physics to individual entities for gravity, friction, and velocity clamping:

```kotlin
val player by entity {
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
    every.frame {
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

    every.frame {
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
val icePlatform by entity {
    position(0, 100)
    physics {
        friction = 0.99f  // Very slippery
        useLocalFriction = true  // Use this instead of global friction
    }
}

val mudPatch by entity {
    position(50, 100)
    physics {
        friction = 0.7f  // Very sticky
        useLocalFriction = true
    }
}
```

## Pathfinding

gbkt provides A* pathfinding optimized for tile-based games with navigation grids, weighted tiles, and dynamic obstacles.

### Navigation Grid Setup

Define which tiles are walkable for pathfinding:

```kotlin
// Manual definition
val navGrid = navGrid("arena") {
    size = 16 x 16
    default = true        // All tiles walkable by default
    blocked(0..15, 0)     // Top wall
    blocked(0..15, 15)    // Bottom wall
    blocked(0, 0..15)     // Left wall
    blocked(15, 0..15)    // Right wall
    blocked(8, 8)         // Obstacle in center
}

// From tilemap (auto-extract from Tiled map)
val navGrid = navGrid(from = dungeonMap) {
    blockedTiles(0, 1, 2)  // Wall tile indices are blocked
}

// With collision layer from Tiled
val navGrid = navGrid(from = dungeonMap) {
    collisionLayer = "Collision"  // Use Tiled layer name
}
```

### Weighted Tiles

Give tiles different movement costs for more realistic pathfinding:

```kotlin
val navGrid = navGrid("dungeon") {
    size = 16 x 16
    default = true

    // Swamp area is slow
    weight(4..8, 4..8, cost = 3)  // 3x slower than normal

    // Road is fast
    weight(0..15, 8, cost = 1)  // Normal speed

    // Impassable walls (cost = 0 means blocked)
    blocked(0..15, 0)
}
```

### Pathfinding Queries

Find paths between entities or tiles:

```kotlin
gameplayScene = scene("gameplay") {
    every.frame {
        // Fluent infix syntax
        val path = player findPathTo treasure using navGrid

        // Or with options
        val path2 = player.findPathTo(treasure).using(navGrid) {
            diagonal = true   // Allow 8-way movement
            maxDepth = 64     // Search limit
            heuristic = Heuristic.MANHATTAN  // or CHEBYSHEV, EUCLIDEAN
        }

        // From/to tile coordinates
        val path3 = findPath(fromTileX = 0, fromTileY = 0, toTileX = 15, toTileY = 15)
            .using(navGrid)
    }
}
```

### Following Paths

Move entities along computed paths:

```kotlin
gameplayScene = scene("gameplay") {
    every.frame {
        val path = enemy findPathTo player using navGrid

        whenever(path.found and path.hasNext) {
            // Move toward next waypoint
            enemy.x += path.directionX(enemy.x)  // Returns -1, 0, or 1
            enemy.y += path.directionY(enemy.y)

            // Advance when waypoint reached
            whenever(path.atWaypoint(enemy, threshold = 4)) {
                path.advance()
            }
        }
    }
}

// Or use automatic path following
every.frame {
    val path = enemy findPathTo player using navGrid

    enemy.followPath(path) {
        speed = 2
        onArrive { /* reached destination */ }
        onBlocked { /* path blocked */ }
    }
}
```

### Path State Queries

Check path state with conditions:

```kotlin
whenever(path.found) { /* valid path exists */ }
whenever(path.notFound) { /* no valid path */ }
whenever(path.hasNext) { /* more waypoints remain */ }

// Path properties (as Expr)
val len = path.length       // Total waypoints
val idx = path.currentIndex // Current waypoint index
val nextX = path.nextX      // Next waypoint X (tiles)
val nextY = path.nextY      // Next waypoint Y (tiles)
```

### Dynamic Obstacles

Modify navigation at runtime:

```kotlin
every.frame {
    // Block tile where enemy stands (pixels → tiles automatic)
    navGrid.addObstacle(enemy)

    // Later, clear it
    navGrid.removeObstacle(enemy)

    // Or by tile coordinates
    navGrid.setBlocked(8, 8)
    navGrid.setWalkable(8, 8)

    // Change movement cost
    navGrid.setWeight(x = 5, y = 5, cost = 3)

    // Check if tile is walkable
    whenever(navGrid.isWalkable(tileX, tileY)) {
        // Tile is passable
    }
}
```

### Heuristics

Choose the distance calculation method:

- `Heuristic.MANHATTAN` - |dx| + |dy| - Best for 4-way movement (default)
- `Heuristic.CHEBYSHEV` - max(|dx|, |dy|) - Best for 8-way movement
- `Heuristic.EUCLIDEAN` - sqrt(dx² + dy²) - Most accurate but slower

## Signed Integer Types

For velocities, directions, and relative positions, use signed integer delegates:

```kotlin
// Signed 8-bit (-128 to 127)
var velocityX by i8Var(0)
var direction by i8Var(-1)

// Signed 16-bit (-32768 to 32767)
var cameraOffsetX by i16Var(0)
var relativePosition by i16Var(-100)

// Usage in scenes
gameplayScene = scene("gameplay") {
    every.frame {
        velocityX += 1
        direction set -5
        cameraOffsetX -= 2
    }
}
```

## Testing Framework

gbkt includes a built-in testing framework that lets you test game logic without compiling to ROM or running an emulator. Tests run directly on the JVM with simulated game state.

### Basic Test Structure

```kotlin
import io.github.gbkt.core.test.*
import kotlin.test.*

class MyGameTest {
    @Test
    fun `player moves right`() = testGame("movement") {
        var playerX by u8Var(80)

        val gameplay = scene("gameplay") {
            every.frame {
                playerX += dpad.x * 2
            }
        }
        start = gameplay

        test {
            // Initially at 80
            expect("playerX").toEqual(80)

            // Hold right for 5 frames
            press(Button.RIGHT) { advanceFrames(5) }

            // Should have moved 10 pixels (2 * 5)
            expect("playerX").toEqual(90)
        }
    }
}
```

### Testing Single Scenes

For simpler tests, use `testScene` to test a scene in isolation:

```kotlin
@Test
fun `counter increments each frame`() = testScene("test") {
    var counter by u8Var(0)

    every.frame { counter += 1 }

    test {
        expect("counter").toEqual(0)
        advanceFrame()
        expect("counter").toEqual(1)
        advanceFrames(9)
        expect("counter").toEqual(10)
    }
}
```

### Frame Control

```kotlin
test {
    // Advance one frame
    advanceFrame()

    // Advance multiple frames
    advanceFrames(60)

    // Advance by approximate seconds (60 FPS)
    advanceSeconds(2.5f)

    // Advance until condition is met (with safety limit)
    val result = advanceUntil(maxFrames = 600) { getVariable("timer") >= 50 }
    result.assertMet("Timer should reach 50")

    // Or use orFail for cleaner syntax
    advanceUntil { getVariable("health") == 0 } orFail "Player should die"

    // Advance while condition is true
    advanceWhile { getVariable("jumping") == 1 }

    // Step one frame with inline assertions
    stepFrame {
        expect("score").toBeGreaterThan(0)
    }

    // Access frame count
    println("Current frame: $frameCount")
}
```

### Input Simulation

```kotlin
test {
    // Tap a button (press for one frame, release)
    tap(Button.A)
    tap(Button.START)

    // Tap multiple buttons simultaneously
    tap(Button.A, Button.B)

    // Hold while executing block
    press(Button.RIGHT) {
        advanceFrames(30)
        expect("playerX").toBeGreaterThan(80)
    }

    // Manual hold and release
    hold(Button.A)
    advanceFrames(10)
    release(Button.A)

    // Release all buttons
    releaseAll()
}
```

Available buttons: `Button.A`, `Button.B`, `Button.START`, `Button.SELECT`, `Button.UP`, `Button.DOWN`, `Button.LEFT`, `Button.RIGHT`

### Fluent Assertions

Integer expectations:

```kotlin
test {
    expect("score").toEqual(100)
    expect("health").toBeGreaterThan(0)
    expect("lives").toBeAtLeast(1)
    expect("timer").toBeLessThan(60)
    expect("ammo").toBeAtMost(99)
    expect("x").toBeBetween(0..160)
    expect("count").toBeZero()
    expect("money").toBePositive()
    expect("velocity").toBeNegative()
    expect("value").toSatisfy("is even") { it % 2 == 0 }
}
```

Sprite expectations:

```kotlin
test {
    expectSprite("player").toBeAt(80, 72)
    expectSprite("player").toHaveX(80)
    expectSprite("player").toHaveY(72)
    expectSprite("player").toBeVisible()
    expectSprite("enemy").toBeHidden()
    expectSprite("hero").toBePlayingAnimation("run")
    expectSprite("idle_enemy").toNotBeAnimating()
    expectSprite("player").toCollideWith(simulation.getSprite("enemy")!!)
    expectSprite("player").toNotCollideWith(simulation.getSprite("wall")!!)
}
```

Pool expectations:

```kotlin
test {
    expectPool("bullets").toHaveActiveCount(5)
    expectPool("particles").toBeEmpty()
    expectPool("enemies").toNotBeEmpty()
    expectPool("bullets").toHaveSpace()
    expectPool("bullets").toHaveSpaceFor(3)
    expectPool("bullets").toBeFull()

    // Check all/any entities match condition
    expectPool("bullets").allMatch("moving up") { idx ->
        getVariable("bullet_${idx}_vel_y") < 0
    }
    expectPool("enemies").anyMatch("on screen") { idx ->
        getVariable("enemy_${idx}_x") in 0..160
    }
}
```

Game/scene expectations:

```kotlin
test {
    game.toBeInScene("gameplay")
    game.toHaveFrameCount(100)
    game.toHaveRunForAtLeast(60)
    expectScene("gameplay")
}
```

### State Access

```kotlin
test {
    // Get variable value
    val health = getVariable("health")

    // Set variable directly (for test setup)
    setVariable("score", 1000)

    // Access current scene
    println("In scene: $currentScene")

    // Direct scene entry (for test setup)
    enterScene("gameplay")

    // Listen for scene changes
    onSceneChange { from, to ->
        println("Scene changed: $from -> $to")
    }
}
```

### IR Verification (Advanced)

For testing that your DSL generates correct IR:

```kotlin
import io.github.gbkt.core.test.*
import io.github.gbkt.core.ir.*

@Test
fun `assignment generates correct IR`() {
    val ir = recordIR {
        playerX += 1
    }

    assertTrue(ir.containsType<IRAssign>())
    val assigns = ir.filterType<IRAssign>()
    assertEquals("playerX", assigns.first().target)
}
```

### Complete Example

```kotlin
class PlatformerTest {
    @Test
    fun `player jumps when A pressed on ground`() = testGame("platformer") {
        var playerY by u8Var(100)  // Ground level
        var velocityY by i8Var(0)
        var jumping by u8Var(0)

        val gameplay = scene("gameplay") {
            every.frame {
                // Jump when A pressed and on ground
                whenever(buttons.a.pressed and (jumping isEqualTo 0)) {
                    velocityY set -8
                    jumping set 1
                }

                // Apply gravity
                whenever(jumping isEqualTo 1) {
                    playerY += velocityY
                    velocityY += 1

                    // Land
                    whenever(playerY isAtLeast 100) {
                        playerY set 100
                        jumping set 0
                    }
                }
            }
        }
        start = gameplay

        test {
            // Initially on ground
            expect("playerY").toEqual(100)
            expect("jumping").toEqual(0)

            // Press A to jump
            tap(Button.A)
            expect("jumping").toEqual(1)
            expect("velocityY").toEqual(-8)

            // Should rise
            advanceFrames(5)
            expect("playerY").toBeLessThan(100)

            // Wait to land
            advanceUntil { getVariable("jumping") == 0 } orFail "Player should land"

            // Back on ground
            expect("playerY").toEqual(100)
            expect("jumping").toEqual(0)
        }
    }
}
```
