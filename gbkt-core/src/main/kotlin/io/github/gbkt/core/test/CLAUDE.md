# Test Module

In-memory simulation of game logic without ROM/emulator for unit testing.

## Files

| File | Purpose |
|------|---------|
| `SimulationContextV2.kt` | High-level test harness wrapping `ScriptOpInterpreter`. Provides `advanceFrames()`, `runUntil()`, button input (`press`/`release`/`tap`/`holdDpad`), variable access (`getVar`/`setVar`/`assertVar`), `enterScene()`, and execution tracing. Also defines `GameBoyButton` and `DpadDirection` enums. |
| `ScriptOpInterpreter.kt` | Core IR interpreter that executes `ScriptOp` nodes in-memory. Maintains all game state: variables, actor positions, arrays, pool slots, hash tables, ring buffers, fixed slots, joypad input, puzzle state. Evaluates expressions (binary, unary, cast, call, collection calls) and executes statements (assign, if, while, for, move, collision, pool ops, puzzle ops). |
| `SimSprite.kt` | Simulated sprite: tracks `name`, `x`/`y`, `visible`, `currentAnimation`, `animationPaused`, `animationFrame`. Provides AABB collision checks (`collidesWith`, `collidesWithHitbox`) and position queries (`isAt`). |

## Usage

```kotlin
val sim = SimulationContextV2(game)
sim.enterScene("gameplay")
sim.press(GameBoyButton.A)
sim.advanceFrames(5)
sim.assertVar("score", 100)
```

## Architecture

`SimulationContextV2` delegates frame execution to `ScriptOpInterpreter`, which
walks the IR tree and updates in-memory state. No C code or hardware is involved.

## Related

- `gbkt-ir` -- IR node types being interpreted
- `gbkt-engine` -- scene/entity/input constructs whose behavior is simulated
