# Platformer — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:platformer:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:platformer:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:platformer:test        # Run tests
```

## Code Structure

`Platformer.kt` (~170 lines) — single file, single `game("Platformer") { }` block:

1. Config: `ROM_ONLY`, 2 ROM banks
2. Forward reference: `val titleRef = sceneRef("title")`
3. Variables: `lives` (u8)
4. Sound effects: `jumpSfx` (HIT), `landSfx` (BUMP), `winSfx` (WIN)
5. Platformer systems: `platformerPhysics`, `platformerCamera`, 3 `platform()` definitions, `goalZone()`
6. Actor: `player` at (20, 104) with `MovementStyle.PHYSICS`
7. Scenes (reverse order): `winScene` → `gameplayScene` → `titleScene`

## Genre DSL Patterns

### Platformer Package Import
```kotlin
import io.github.gbkt.genre.platformer.domain.PlatformType
import io.github.gbkt.genre.platformer.dsl.*
```

### Physics Configuration
```kotlin
platformerPhysics {
    gravity(2)             // downward acceleration per frame
    jumpForce(8)           // initial upward velocity on jump
    terminalVelocity(12)   // max fall speed
    coyoteTime(6)          // frames after leaving edge where jump still works
    jumpBuffer(8)          // frames before landing where jump input is buffered
}
```

### Camera Configuration
```kotlin
platformerCamera {
    smoothFollow()
    horizontal()           // horizontal-only follow (no vertical scroll)
    deadZone(x = 16, y = 8)
}
```

### Platform Definitions
```kotlin
// Solid floor — player cannot pass through from any direction
platform("ground") { type(PlatformType.SOLID) }

// One-way — player can jump through from below, lands on top
platform("mid_platform") { type(PlatformType.ONE_WAY) }
```

### Physics Actor
```kotlin
val player by actor {
    position(20, 104)
    sprite(asset("sprites/player.png")) { size(8, 16); hitbox(0, 0, 8, 16) }
    movement { style(MovementStyle.PHYSICS); speed(2) }
}
```

### Fall Detection
```kotlin
whenever(player.y isAbove 136) {
    lives -= 1
    playSound(landSfx)
    player.moveTo(20, 104)              // respawn
    whenever(lives isEqualTo 0) { navigate(titleRef) }
}
```

## How to Modify

- **Add more platforms:** Add `platform("name") { type(PlatformType.ONE_WAY) }` at game level
- **Change gravity/jump feel:** Adjust `gravity()`, `jumpForce()`, `terminalVelocity()` in `platformerPhysics`
- **Enable vertical camera scroll:** Remove `horizontal()` from `platformerCamera`
- **Add enemies:** Declare additional actors; add collision logic in gameplay frame block
- **Add collectibles:** Declare actors with `visible set false` initially; show on proximity

## Dependencies

- `gbkt-core` — DSL, IR, scene/actor/sound system
- `gbkt-backend-gbdk` — Game Boy C code generation
- `gbkt-genre-platformer` — `platformerPhysics()`, `platformerCamera()`, `platform()`, `goalZone()`
