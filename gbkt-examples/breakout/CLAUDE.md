# Breakout — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:breakout:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:breakout:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:breakout:test        # Run tests
```

## Code Structure

`BreakoutV2.kt` (~280 lines) — single file, single `game("Breakout") { }` block:

1. Config: `ROM_ONLY`, 2 ROM banks
2. Forward reference: `val titleRef = sceneRef("title")`
3. Variables: `score`, `lives`, `bricksLeft` (u8); `ballDx`, `ballDy` (i8); `bc`, `brow`, `bidx` (u8 temporaries)
4. Array: `val bricks by u8Array(30)` — 3 rows × 10 cols brick state
5. Sound effects: `hitSfx`, `scoreSfx`, `loseSfx`, `winSfx`
6. Actors: `paddle` (72, 132), `ball` (80, 120)
7. HUD panel: `gameHud` — score number + lives icons
8. Scenes (reverse order): `winScene` → `gameoverScene` → `gameScene` → `titleScene`

## Key DSL Patterns

### Tile-Based Brick Collision
```kotlin
// Brick grid occupies pixels x[40,120) y[24,48) — 8px tiles
whenever((ball.y isAtLeast 24) logicalAnd (ball.y isBelow 48)) {
    whenever((ball.x isAtLeast 40) logicalAnd (ball.x isBelow 120)) {
        bc set ((ball.x - 40) shr 3)         // column 0..9
        brow set ((ball.y - 24) shr 3)       // row 0..2
        bidx set (brow * 10 + bc)            // flat index 0..29
        whenever(bricks[bidx] isEqualTo 1) {
            bricks[bidx] = 0
            gotoxy(bc + 5, brow + 3)         // erase tile from BG layer
            print(" ")
            bricksLeft -= 1
            score += 10
            ballDy *= -1
        }
    }
}
```

### Array Initialization Loop
```kotlin
bidx set 0
whileOp(bidx isBelow bricks.size) {
    bricks[bidx] = 1
    bidx += 1
}
```

### HUD Declaration
```kotlin
val gameHud = hud("breakout_hud") {
    anchor(Anchor.TOP_LEFT)
    number("score") { variable(score); label("SC:"); format("%d") }
    icons("lives") { variable(lives); max(3); fullTile(0x08); emptyTile(0x09)
                     displayMode(IconDisplayMode.FULL_AND_EMPTY) }
}
// In game scene enter:
gameHud.show()
```

### logicalAnd for Multi-Condition Checks
```kotlin
// Compound condition — both must be true
whenever((ball.y isAtLeast 24) logicalAnd (ball.y isBelow 48)) { ... }
```

## How to Modify

- **Change brick rows/cols:** Update `u8Array(30)`, adjust the 3/10 constants in brick collision
- **Adjust ball speed:** Change `ballDx set 1` / `ballDy set -1` initial values
- **Add ball speed-up:** Increment `|ballDx|` or `|ballDy|` every N bricks destroyed
- **Add more lives:** Change `lives set 3` in scene enter and `u8Var(3)` default
- **Change brick score:** Adjust `score += 10` per brick

## Dependencies

- `gbkt-core` — DSL, IR, scene/actor/array/sound/HUD/print system
- `gbkt-backend-gbdk` — Game Boy C code generation
