# Pong — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:pong:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:pong:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:pong:test        # Run tests
```

## Code Structure

`PongV2.kt` (~220 lines) — single file, single `game("Pong") { }` block:

1. Config: `ROM_ONLY`, 2 ROM banks
2. Forward reference: `val titleRef = sceneRef("title")`
3. Variables: `p1Score`, `p2Score` (u8), `ballDx`, `ballDy` (i8)
4. Sound effects: `bounceSfx`, `scoreSfx`, `winSfx`
5. Actors: `paddle1` (16, 64), `paddle2` (152, 64), `ball` (80, 72)
6. Scenes (reverse order): `gameoverScene` → `gameScene` → `titleScene`

## Key DSL Patterns

### Coordinate-Range Paddle Collision
```kotlin
// Pong avoids ball.collides(paddle) — uses x-range + Y overlap for tighter feel
whenever(ball.x isBelow 20) {
    whenever(ball.x isAtLeast 4) {
        whenever(ball.y isAtLeast paddle1.y) {
            whenever(ball.y isBelow (paddle1.y + 16)) {
                ballDx set 1
                playSound(bounceSfx)
            }
        }
    }
}
```

### AI Paddle Tracking
```kotlin
// Track ball to paddle CENTER (y+8), 2px/frame with boundary clamp
whenever((paddle2.y + 8) isAbove ball.y) {
    whenever(paddle2.y isAbove 16) { moveBy(paddle2, 0, -2) }
}
```

### Score Update with print()
```kotlin
// Formatted print with variable expressions
print("P1:%d    P2:%d", p1Score.toExpr(), p2Score.toExpr(), position = PositionDef(5, 1))
```

### Forward Reference Pattern
```kotlin
val titleRef = sceneRef("title")   // top of game block
// gameoverScene defined first — navigates to titleRef without forward error
scene("gameover") {
    frame { whenever(buttons.start.pressed) { navigate(titleRef) } }
}
// titleScene defined last — titleRef resolves to it
scene("title") { ... }
```

## How to Modify

- **Change winning score:** Adjust `5` in `whenever(p1Score isAtLeast 5)`
- **Change AI speed:** Replace `2` in `moveBy(paddle2, 0, 2)` / `moveBy(paddle2, 0, -2)`
- **Add 2-player mode:** Replace AI logic with `dpad.up/down` for paddle2 (note: DMG only has one d-pad)
- **Adjust ball speed:** Change `ballDx set 1` / `ballDy set 1` to larger values
- **Add ball acceleration:** Increment `ballDx` / `ballDy` after each score

## Dependencies

- `gbkt-core` — DSL, IR, scene/actor/sound/print system
- `gbkt-backend-gbdk` — Game Boy C code generation
