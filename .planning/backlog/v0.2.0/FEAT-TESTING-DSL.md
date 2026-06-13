---
id: FEAT-TESTING-DSL
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-11"
triage_date: 2026-06-12
---

# FEAT-TESTING-DSL: testGame() / testScene() DSL, press/tap input simulation, fluent assertions, IR verification

## Source

Removed from context/DSL_REFERENCE.md lines 2011–2239 (commit removal-commit-TBD).

**Implemented today:** JVM-tier simulation is done via `SimulationContext` / `ScriptOpInterpreter` in `gbkt-core/.../test/`; emulator-tier testing uses `GbktTestExtension` in `gbkt-test` (see `context/TESTING.md`). `SimulationContext.advanceFrame()` exists. The `testGame()` / `testScene()` DSL wrappers shown below do NOT exist — they are aspirational builder-pattern convenience wrappers. `press(Button.RIGHT) { advanceFrames(5) }` input simulation in DSL form is absent. `advanceUntil(maxFrames) { } orFail` is absent. `mock("actor") { }` is absent.

## Why This Matters

A first-class `testGame { ... test { ... } }` DSL would lower the bar for writing unit-level game logic tests — no need to instantiate `SimulationContext` directly. Fluent assertions (`expect("playerX").toEqual(90)`) and input helpers (`press(Button.RIGHT) { }`) would make tests read like specs.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

### Basic Test Structure

```kotlin
import io.github.gbkt.core.test.*
import kotlin.test.*

class MyGameTest {
    @Test
    fun `player moves right`() = testGame("movement") {
        var playerX by u8Var(80)

        val gameplay = scene("gameplay") {
            frame {
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

    frame { counter += 1 }

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
