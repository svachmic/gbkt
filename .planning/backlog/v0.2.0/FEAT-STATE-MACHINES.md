---
id: FEAT-STATE-MACHINES
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-1"
triage_date: 2026-06-12
---

# FEAT-STATE-MACHINES: Top-Level State Machine DSL

## Source

Removed from context/DSL_REFERENCE.md lines 370–408 (commit eb0c6aaa).

**Implemented today:** Per-actor `animationStates { }` DSL in `ActorBuilder.kt` and `setAnimationState(actor, "state")` in `ScriptBuilder.kt`. There is no top-level `states("...")` builder.

## Why This Matters

A first-class state machine DSL (`states("player") { ... }`) would let game logic be expressed declaratively as finite-state-machines — cleaner than nested `whenever()` conditions and easier to reason about for platformers, enemies, and UI flows.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

```kotlin
// Define entity states (new in v2.0!)
// Assumes idleAnim, runAnim, jumpAnim are declared (see Sprite Animations)
val playerState = states("player") {
    "idle" {
        enter { player.play(idleAnim) }
        on(buttons.a.pressed) { goto("jump") }
        on(dpad.any) { goto("run") }
    }

    "run" {
        enter { player.play(runAnim) }
        tick { playerX += dpad.x * 2 }
        on(dpad.none) { goto("idle") }
        on(buttons.a.pressed) { goto("jump") }
    }

    "jump" {
        enter { player.play(jumpAnim) }
        tick {
            playerY -= playerVelY
            playerVelY -= 1
        }
        on(playerY isAtLeast groundY) { goto("idle") }
    }
}

// Use state machine in scene
gameplayScene = scene("gameplay") {
    enter { playerState.start("idle") }
    frame { playerState.update() }
}
```
