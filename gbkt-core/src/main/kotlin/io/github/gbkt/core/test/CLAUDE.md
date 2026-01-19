# Test Module

In-memory simulation of game logic without ROM/emulator for unit testing.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `SimulationContext.kt` | Core simulation engine, executes IR in-memory | ~745 |
| `SimValue.kt` | Value wrapper with Game Boy semantics | ~100 |
| `SimSprite.kt` | Simulated sprite state | ~30 |
| `SimPool.kt` | Simulated entity pool state | ~150 |
| `TestFramework.kt` | JUnit-style test DSL | ~100 |
| `LogicTest.kt` | Test case definition and assertions | ~150 |
| `TestScope.kt` | Assertion utilities | ~80 |
| `Assertions.kt` | Custom assertion functions | ~100 |
| `InputMocking.kt` | Mock input for tests | ~80 |
| `InlineExecutor.kt` | Execute IR directly for testing | ~50 |

## SimulationContext (SimulationContext.kt)

The heart of the testing framework - executes IR nodes in-memory:

```kotlin
val game = game("TestGame") { ... }
val sim = SimulationContext(game)

// Execute frames
sim.executeFrame()
sim.executeFrame()
sim.executeFrame()

// Check state
assertEquals(100, sim.getVariable("score").toInt())
assertEquals("gameplay", sim.currentScene)
```

### State Tracking

SimulationContext maintains:
- All game variables
- Current scene
- Frame count
- Input state (joypad, joypad_prev)
- Sprite positions and animations
- Pool entity states
- Camera position
- Transition state

### Input Simulation

```kotlin
// Set input before frame
sim.joypad = 0x10        // A button held
sim.joypadPrev = 0x00    // Nothing held last frame
sim.executeFrame()        // This frame: A just pressed

sim.joypadPrev = 0x10    // A was held
sim.joypad = 0x10        // A still held
sim.executeFrame()        // This frame: A held
```

### IR Execution

All core IR statements are simulated:
- Assignments (IRAssign)
- Conditionals (IRIf, IRWhen)
- Loops (IRWhile, IRFor)
- Scene changes (IRSceneChange)
- Pool operations (spawn, despawn, forEach)
- Animations (play, stop, pause)
- Camera (position, snap)

Some are no-ops in simulation:
- Visual rendering (palettes, clear screen)
- Raw C code
- Dialog/menu systems (not fully simulated)

## LogicTest DSL (LogicTest.kt, TestFramework.kt)

Declarative test syntax:

```kotlin
class MovementTest {
    @Test
    fun `player moves right when dpad right held`() = logicTest {
        game = testGame

        setup {
            setVariable("playerX", 80)
        }

        input {
            pressRight()
        }

        frames(5)

        verify {
            variable("playerX") shouldBe 90  // 5 frames * 2 pixels
        }
    }
}
```

### Test DSL Components

```kotlin
logicTest {
    // Game to test
    game = myGame

    // Initial setup
    setup {
        setVariable("score", 0)
        enterScene("gameplay")
    }

    // Set input
    input {
        pressA()                    // A pressed
        holdRight()                 // D-pad right held
        release()                   // Clear all input
    }

    // Execute frames
    frames(10)                      // Run 10 frames

    // Verify state
    verify {
        variable("score") shouldBe 100
        scene shouldBe "gameplay"
        sprite("player").x shouldBe 90
    }
}
```

## SimValue (SimValue.kt)

Wrapper for values with Game Boy arithmetic semantics:

```kotlin
val a = SimValue.of(250)
val b = SimValue.of(10)
val c = a + b  // Wraps at 256 for u8

// Boolean operations
SimValue.TRUE.isTrue   // true
SimValue.ZERO.isTrue   // false

// Comparisons
a eq b                 // SimValue(0) - false
a gt b                 // SimValue(1) - true
```

### SimValue Operations

```kotlin
+ - * / %              // Arithmetic
and or xor             // Bitwise
shl shr                // Shifts
eq neq lt lte gt gte   // Comparisons
land lor               // Logical
inv() lnot()           // Unary
```

## InputMocking (InputMocking.kt)

Mock input provider for tests:

```kotlin
class MockInputProvider {
    fun pressA() { joypad = joypad or Button.A.mask }
    fun pressRight() { joypad = joypad or Button.RIGHT.mask }
    fun release() { joypad = 0 }
    fun advanceFrame(sim: SimulationContext) {
        sim.joypadPrev = sim.joypad
        sim.joypad = joypad
        sim.executeFrame()
    }
}
```

## Assertions (Assertions.kt)

Custom assertions for game state:

```kotlin
verify {
    variable("hp") shouldBe 100
    variable("hp") shouldBeGreaterThan 0
    variable("hp") shouldBeLessThan 200
    variable("hp") shouldBeInRange 0..100

    scene shouldBe "gameplay"

    sprite("player").x shouldBe 80
    sprite("player").visible shouldBe true
    sprite("player").animation shouldBe "idle"

    pool("bullets").activeCount shouldBe 3
}
```

## Testing Pattern

```kotlin
class GameLogicTest {
    private lateinit var game: Game
    private lateinit var sim: SimulationContext

    @BeforeEach
    fun setup() {
        game = createTestGame()
        sim = SimulationContext(game)
    }

    @Test
    fun `collision triggers scene change`() {
        // Setup overlapping positions
        sim.setVariable("playerX", 100)
        sim.setVariable("playerY", 50)
        sim.setVariable("enemyX", 102)
        sim.setVariable("enemyY", 52)

        // Run frame
        sim.executeFrame()

        // Verify scene changed
        assertEquals("gameover", sim.currentScene)
    }
}
```

## Limitations

Not simulated in detail:
- Visual rendering (palettes, tiles, sprites)
- Audio playback
- Save system persistence
- Dialog/menu UI
- Raw C code

## Related Modules

- `ir/` - IR nodes being simulated
- `services/MockServices.kt` - Mock service injection
- `entity/Pool.kt` - Pool entity system
