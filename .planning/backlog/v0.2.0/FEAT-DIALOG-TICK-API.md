---
id: FEAT-DIALOG-TICK-API
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-2"
triage_date: 2026-06-12
---

# FEAT-DIALOG-TICK-API: Dialog tick / isActive / isComplete / show / hide on DialogHandle

## Source

Removed from context/DSL_REFERENCE.md lines 922–1001 (commit eb0c6aaa).

**Implemented today:** `DialogBuilder` exists in `gbkt-lang/.../dsl/UIBuilders.kt:64`. The builder is function-style (`textSpeed(3)`, `speaker("Elder")`, `box(x, y, width, height)`). `DialogHandle.say(text)` and `DialogHandle.say(vararg)` and `DialogHandle.choice(block)` + `portrait(AssetRef)` + `border(BorderStyle)` + `fontMode(FontMode)` are all implemented. What is NOT implemented: `DialogHandle.tick()`, `DialogHandle.isActive`, `DialogHandle.isComplete`, `DialogHandle.show()`, `DialogHandle.hide()` — `show()`/`hide()` exist only on `MenuHandle`.

## Why This Matters

Without `tick()` / `isActive` / `isComplete`, the caller has no frame-driven update hook or completion check for the typewriter effect. The show/hide API on DialogHandle would allow explicit dialog box visibility control without always being coupled to `say()`.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

```kotlin
// === Named Dialog (RPGs, adventures) ===
// Define once, reuse everywhere
val elder = dialog("elder") {
    speaker = "Elder"           // Prefix: "Elder: Hello!"
    textSpeed = 3               // Characters per frame (higher = faster)
    box {
        position(0, 10)         // Tile coordinates (x, y)
        size = 20 x 6           // Width x Height in tiles
        border = BorderStyle.SIMPLE  // NONE, SIMPLE, ROUNDED, DOUBLE
        padding = 1
    }
}

lateinit var villageScene: SceneRef
lateinit var questScene: SceneRef

villageScene = scene("village") {
    enter {
        elder.say("Welcome, young hero!")
        elder.say("The kingdom needs you.")
    }

    frame {
        // Update typewriter effect (REQUIRED when dialog is active!)
        elder.tick()

        // Check dialog state
        whenever(elder.isComplete) {
            // Dialog finished displaying
        }
    }
}

// === Dialog Visibility ===
elder.show()    // Show dialog box (without text)
elder.hide()    // Hide dialog box
```

**Important Notes (from the removed section):**
- Always call `dialog.tick()` in `frame { }` when a dialog is active
- Use `dialog.isActive` and `dialog.isComplete` conditions to check state
