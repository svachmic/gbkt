---
id: FEAT-TWEENING
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-6"
triage_date: 2026-06-12
---

# FEAT-TWEENING: Tweening / Easing DSL (tween(), Easing enum, MAX_TWEENS)

## Source

Removed from context/DSL_REFERENCE.md lines 1473–1539 (commit removal-commit-TBD).

**Implemented today:** Nothing in this section is implemented. `tween()` does not exist in `ScriptBuilder.kt`. The `Easing` enum does not exist in any DSL file. `MAX_TWEENS` configuration constant is absent.

## Why This Matters

Smooth value interpolation is essential for UI transitions, character animations, and visual polish. Without tweening, authors must manually manage lerp tables or write C escape-hatch code. A built-in `tween()` + pre-computed lookup tables would be a significant quality-of-life upgrade.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

### Basic Tweening

```kotlin
// Tween a sprite position from 0 to 100 over 60 frames
tween(player.x, from = 0, to = 100, duration = 60.frames, easing = Easing.EASE_OUT)

// Tween a variable
tween(fadeAlpha, from = 0, to = 255, duration = 30.frames, easing = Easing.LINEAR)

// Tween with expression values
tween(enemy.x, from = Expr(startX), to = Expr(targetX), duration = 120.frames)
```

### Easing Functions

```kotlin
// Basic easing
Easing.LINEAR          // Constant speed
Easing.EASE_IN         // Start slow, end fast
Easing.EASE_OUT        // Start fast, end slow (default)
Easing.EASE_IN_OUT     // Slow at both ends
Easing.EASE_OUT_IN     // Fast at both ends

// Quadratic (t²)
Easing.EASE_IN_QUAD
Easing.EASE_OUT_QUAD
Easing.EASE_IN_OUT_QUAD

// Cubic (t³)
Easing.EASE_IN_CUBIC
Easing.EASE_OUT_CUBIC
Easing.EASE_IN_OUT_CUBIC

// Special effects
Easing.EASE_OUT_BOUNCE  // Bouncy landing
Easing.EASE_OUT_ELASTIC // Springy overshoot
```

### How It Works

- Pre-computed 256-entry lookup tables for each easing function
- Only tables for used easing types are generated (saves ROM space)
- Supports both increasing and decreasing tweens (signed math)
- Maximum 16 concurrent tweens (configurable via `MAX_TWEENS`)

### Usage in Scenes

```kotlin
introScene = scene("intro") {
    enter {
        // Slide title in from left
        tween(titleX, from = -80, to = 80, duration = 45.frames, easing = Easing.EASE_OUT_BOUNCE)
    }

    frame {
        // Tweens update automatically
    }
}
```
