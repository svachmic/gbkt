# sample-minimal

A beginner-friendly minimal gbkt example demonstrating the absolute basics of Game Boy development with Kotlin.

## What This Sample Demonstrates

- **Single sprite**: Creating and displaying an 8x8 sprite
- **D-pad movement**: Reading input and moving the sprite
- **Boundary checking**: Keeping the sprite within screen bounds
- **Basic scene**: A simple scene with enter and frame logic

## Features

| Feature | Description |
|---------|-------------|
| Sprite | 8x8 player sprite with owned position |
| Movement | D-pad moves sprite 2 pixels per frame |
| Bounds | Player stays within visible screen area |
| HUD | Position coordinates displayed at bottom |

## Code Highlights

### Sprite with Position

```kotlin
val player by entity {
    position(80, 72)  // Start at center of screen

    sprite(SpriteAsset("player.png")) {
        size = 8 x 8
        hitbox(0, 0, 8, 8)
    }
}
```

### D-Pad Input

```kotlin
whenever(buttons.right.held) {
    player.x += speed
}
```

### Boundary Checking

```kotlin
whenever(player.x isAbove 152) {
    player.x set 152
}
```

## Building

```bash
# Generate C code
./gradlew :sample-minimal:generateC

# Build ROM (requires GBDK-2020)
./gradlew :sample-minimal:buildRom

# Run code generation (for debugging)
./gradlew :sample-minimal:run
```

## Assets Required

- `assets/player.png` - 8x8 sprite for the player (see player.txt for specs)

## Next Steps

After understanding this example, try:

1. **sample-dialog/** - Learn about UI, dialogs, and menus
2. **sample-save/** - Add persistence with save/load
3. **sample-adventure/** - Build a complete game with multiple scenes
