# Scene Module

Scene lifecycle management: enter, frame loop, and exit callbacks.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Scene.kt` | Scene class, SceneBuilder, TimingBlocks, FrameScope | ~325 |
| `SceneTransition.kt` | Scene transition DSL and builders | ~150 |
| `Transition.kt` | Transition effect definitions | ~100 |
| `Link.kt` | Link cable multiplayer definitions | ~80 |

## Scene Definition

Scenes are the primary organizational unit in gbkt games:

```kotlin
val gameplayScene = scene("gameplay") {
    // Called once when entering this scene
    enter {
        screen.showSprites()
        camera.fadeIn(20.frames)
    }

    // Called every frame
    every.frame {
        handleInput()
        updatePhysics()
        checkCollisions()
    }

    // Called once when leaving this scene
    exit {
        screen.hideSprites()
    }
}

start = gameplayScene  // Set as starting scene
```

## Scene Lifecycle

```
Scene A                    Scene B
   │                          │
   ├── exit { }               │
   │      │                   │
   │      └──────────────────>├── enter { }
   │                          │      │
   │                          │      v
   │                          ├── every.frame { }
   │                          │      │
   │                          │      v (loop)
```

## SceneRef

Type-safe scene references:

```kotlin
val titleScene = scene("title") { ... }
val gameplayScene = scene("gameplay") { ... }

// Use SceneRef for type-safe transitions
scene(gameplayScene)  // Not scene("gameplay")
```

## Timing Blocks (every.*)

Control when frame logic executes:

```kotlin
every.frame { ... }         // Every frame (~60 FPS NTSC)
every.second { ... }        // Every 60 frames
every.halfSecond { ... }    // Every 30 frames
every.quarterSecond { ... } // Every 15 frames
every(10).frames { ... }    // Every N frames
```

### Frame Rate Note

Timing assumes **60 FPS (NTSC)**. On PAL systems (50 FPS):
- `every.second` takes ~1.2 seconds
- `every.halfSecond` takes ~0.6 seconds

## FrameScope

The context available inside `enter { }`, `every.frame { }`, and `exit { }`:

### Scene Transitions

```kotlin
scene(gameplayScene)          // Go to scene (SceneRef)
scene("gameplay")             // Go to scene (by name, for forward refs)
goto(titleScene)              // Alias for scene()

// With transition effects
transitionTo(gameoverScene) {
    fadeOut then wait(30.frames) then fadeIn
}

goto(menuScene) using cinematicFade  // Use predefined transition
```

### Control Flow

```kotlin
whenever(condition) { ... }
whenever(condition) { ... } otherwise { ... }
```

### Utilities

```kotlin
include(logicBlock)      // Expand a reusable LogicBlock
sound(sfxId)             // Play sound effect
debug("message")         // Emulator-only debug print
raw("C code;")           // Raw C escape hatch
```

## Scene Transitions (SceneTransition.kt)

Fluent transition composition:

```kotlin
// Define a reusable transition
val cinematicFade = transition {
    fadeOut(30.frames)
    wait(15.frames)
    fadeIn(30.frames)
}

// Use in scene
goto(nextScene) using cinematicFade

// Inline transition
transitionTo(bossScene) {
    shake(4) then fadeOut then wait(30.frames) then fadeIn
}
```

### Available Effects

```kotlin
fadeOut(duration)           // Fade to black
fadeIn(duration)            // Fade from black
flash(duration)             // White flash
flash(color, duration)      // Color flash
wipeLeft(duration)          // Wipe transitions
wipeRight(duration)
wipeUp(duration)
wipeDown(duration)
irisClose(duration, target) // Circle closing on target
irisOpen(duration, target)  // Circle opening from target
wait(duration)              // Pause between effects
shake(intensity)            // Screen shake
```

### Composition

```kotlin
fadeOut then wait(30.frames) then fadeIn
shake(4) then fadeOut
```

## Link Cable (Link.kt)

Multiplayer communication:

```kotlin
val link = link {
    role = LinkRole.AUTO  // AUTO, MASTER, or SLAVE
    onConnect { /* initialize sync */ }
    onReceive { data -> /* handle received data */ }
    onDisconnect { /* cleanup */ }
}

// In frame logic
whenever(link.connected) {
    link.send(playerState)
}
```

## Scene Data Class

The immutable scene representation:

```kotlin
class Scene(
    val name: String,
    val onEnter: List<IRStatement>,   // enter { } statements
    val onFrame: List<IRStatement>,   // every.frame { } statements
    val onExit: List<IRStatement>,    // exit { } statements
)
```

## Best Practices

1. **Single timing block**: Each scene can only have one `every.*` block
2. **Single enter/exit**: Each scene can only have one `enter { }` and one `exit { }`
3. **Use SceneRef**: Prefer type-safe `SceneRef` over string scene names
4. **Cleanup in exit**: Release resources, stop sounds, hide sprites in `exit { }`
5. **Initialize in enter**: Set up sprites, variables, camera in `enter { }`

## Related Modules

- `dsl/RecordingContext.kt` - Records scene logic as IR
- `builder/GameBuilder.kt` - Registers scenes with game
- `graphics/Camera.kt` - Camera transitions
- `gbkt-backend-gbdk/.../codegen/core/SceneCodegen.kt` - Scene code generation (in backend)
