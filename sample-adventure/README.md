# sample-adventure

A complex game example demonstrating advanced gbkt features in a complete mini-game.

## What This Sample Demonstrates

- **Multiple scenes**: Title, gameplay, gameover, win screens
- **Entity pool**: Efficient enemy management with patrol AI
- **Camera system**: Smooth following with effects
- **GBC palettes**: Color-enhanced sprites
- **Physics**: Gravity, jumping, collision
- **Win/lose conditions**: Complete game loop

## Features

| Feature | Description |
|---------|-------------|
| Scenes | 4 scenes with transitions |
| Entity Pool | 4 patrolling enemies |
| Camera | Smooth follow with shake on hit |
| Palettes | Player (green), Enemy (red), Coin (gold) |
| Physics | Gravity, jump, ground detection |
| Collectibles | 3 coins to collect |
| Win Condition | Collect all coins |
| Lose Condition | Run out of lives |

## Code Highlights

### Entity Pool for Enemies

```kotlin
val enemyPool = pool("enemy", size = 4) {
    position(0, 0)
    velocity(-1, 0)
    tag(enemyTag)

    sprite(SpriteAsset("enemy.png")) {
        size = 8 x 8
        palette = enemyPalette
    }

    state {
        val minX by u8Var(0)
        val maxX by u8Var(160)
    }

    onFrame {
        x += velX
        whenever(x isBelow this["minX"]) { velX set 1 }
        whenever(x isAbove this["maxX"]) { velX set -1 }
    }
}
```

### Spawning Enemies with Custom Bounds

```kotlin
enemyPool.spawn {
    x set 20
    y set 110
    velX set 1
    this["minX"] set 20
    this["maxX"] set 70
}
```

### Camera with Follow and Shake

```kotlin
val camera = camera {
    smoothing = 0.15f
    deadzone(16 x 8)
}

// In scene:
camera.follow(player)
camera.shake(4, 10.frames)  // On hit
```

### GBC Palettes

```kotlin
val playerPalette = palette("player") {
    colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000)
}

sprite(SpriteAsset("player.png")) {
    palette = playerPalette
}
```

### Scene Transitions

```kotlin
camera.fadeOut(20.frames) {
    scene("gameplay")
}

camera.fadeIn(15.frames)
```

## Controls

| Button | Action |
|--------|--------|
| D-Pad Left/Right | Move player |
| A | Jump |
| Select | Return to title |
| Start | Start game / Restart |

## Building

```bash
# Generate C code
./gradlew :sample-adventure:generateC

# Build ROM (requires GBDK-2020)
./gradlew :sample-adventure:buildRom

# Run code generation (for debugging)
./gradlew :sample-adventure:run
```

## Assets Required

- `assets/player.png` - 56x16 sprite sheet (7 frames)
- `assets/enemy.png` - 8x8 enemy sprite
- `assets/coin.png` - 32x8 sprite sheet (4 frames)
- `assets/level.json` - Tiled map (optional, for tilemap collision)
- `assets/tiles.png` - Tileset for level (optional)

See the .txt placeholder files for detailed specifications.

## Game Flow

1. **Title Screen**: Shows objective (collect 5 coins)
   - START: Begin game with fade transition

2. **Gameplay Scene**: Platforming action
   - Move and jump to collect coins
   - Avoid patrolling enemies
   - 3 lives, lose one on enemy contact
   - Camera follows player

3. **Win Screen**: Shown when all coins collected
   - Displays final score
   - START: Return to title

4. **Game Over Screen**: Shown when lives = 0
   - Displays score and progress
   - START: Return to title

## Architecture

```
AdventureGame
+-- Title Scene (entry point)
|   +-- Fade transition to Gameplay
|
+-- Gameplay Scene
|   +-- Player entity (animated, physics)
|   +-- Coin entities (3 static collectibles)
|   +-- Enemy pool (4 patrolling enemies)
|   +-- Camera (following player)
|   +-- HUD (coins, lives)
|   +-- Win check -> Win Scene
|   +-- Death check -> Game Over Scene
|
+-- Win Scene
|   +-- Final score display
|
+-- Game Over Scene
    +-- Score and progress display
```

## Next Steps

After understanding this example, try:

1. Add tilemap collision for platforms
2. Add more enemy types with different behaviors
3. Add power-ups (invincibility, double jump)
4. Add multiple levels with scene transitions
5. Add background music and sound effects
6. Add a save system for high scores
