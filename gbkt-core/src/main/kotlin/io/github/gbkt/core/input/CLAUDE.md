# Input Module

Game Boy button and d-pad input handling with edge detection.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Input.kt` | Button/dpad state, held/pressed/released detection | ~262 |
| `InputBuffer.kt` | Frame-perfect input buffering for combos | ~100 |

## Input API (Input.kt)

### D-pad

```kotlin
// Directional input with state detection
whenever(dpad.right.held) { playerX += 2 }    // Held down
whenever(dpad.left.pressed) { dash() }         // Just pressed this frame
whenever(dpad.up.released) { releaseCharge() } // Just released this frame

// Backward compatible (implicit .held)
whenever(dpad.right) { playerX += 2 }

// Axis values for smooth movement
playerX += dpad.x * speed   // x: -1 (left), 0, +1 (right)
playerY += dpad.y * speed   // y: -1 (up), 0, +1 (down)
```

### Buttons

```kotlin
whenever(buttons.a.pressed) { jump() }         // A button
whenever(buttons.b.pressed) { shoot() }        // B button
whenever(buttons.start.pressed) { pause() }    // Start
whenever(buttons.select.pressed) { inventory() } // Select
```

### State Properties

Each direction and button has three states:

| Property | Meaning |
|----------|---------|
| `.held` | True while held down |
| `.pressed` | True only on the frame it was pressed |
| `.released` | True only on the frame it was released |

### Convenience Properties

```kotlin
dpad.any          // Any direction held
dpad.none         // No direction held
moving            // Alias for dpad.any
stationary        // Alias for dpad.none
movingLeft        // dpad.left.held
movingRight       // dpad.right.held
movingUp          // dpad.up.held
movingDown        // dpad.down.held
facing            // -1, 0, or +1 based on horizontal input
```

### Button Combinations

```kotlin
// Check multiple buttons at once
val combo = buttons(buttons.a.held, buttons.b.held)
whenever(combo.allHeld) { specialMove() }    // A+B together
whenever(combo.anyHeld) { somethingPressed() }
```

### Unified API

Access d-pad through buttons object:

```kotlin
buttons.dpad.left.pressed  // Same as dpad.left.pressed
```

## Input Buffer (InputBuffer.kt)

For fighting-game style input timing:

```kotlin
val jumpBuffer = inputBuffer("jump") {
    button = buttons.a
    window = 6.frames           // Buffer window
}

// Check if buffered input is available
whenever(jumpBuffer.active and onGround) {
    jumpBuffer.consume()        // Use the buffered input
    jump()
}
```

## Hardware Details

Button masks (for reference):

| Button | Mask | GBDK Name |
|--------|------|-----------|
| A | 0x10 | J_A |
| B | 0x20 | J_B |
| Select | 0x40 | J_SELECT |
| Start | 0x80 | J_START |
| Right | 0x01 | J_RIGHT |
| Left | 0x02 | J_LEFT |
| Up | 0x04 | J_UP |
| Down | 0x08 | J_DOWN |

## Implementation

Input state is tracked via generated variables:

```c
// Generated C code
UINT8 _joypad;       // Current frame input
UINT8 _joypad_prev;  // Previous frame input

// Per frame:
_joypad_prev = _joypad;
_joypad = joypad();

// Pressed detection: (current & mask) && !(previous & mask)
// Released detection: !(current & mask) && (previous & mask)
```

## Related Modules

- `dsl/Conditionals.kt` - `whenever` integrates with input
- `test/InputMocking.kt` - Mock input for testing
- `codegen/core/MainCodegen.kt` - Input variable generation
