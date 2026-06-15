---
id: FEAT-SAVE-DATA-FIELDS
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-4"
triage_date: 2026-06-12
---

# FEAT-SAVE-DATA-FIELDS: Save field-level API (u16Field, flagsField, load, save, exists)

## Source

Removed from context/DSL_REFERENCE.md lines 1234–1307 (commit eb0c6aaa).

**Implemented today:** `SaveDataBuilder` in `gbkt-lang/.../dsl/SystemBuilders.kt:139` exposes `slots(n)`, `checksum(enabled: Boolean)`, and `version(v: Int)` only. Saves are triggered via `triggerSystem(saves)` (ScriptBuilder.kt:545). What is NOT implemented: `u16Field()`, `u8Field()`, `flagsField()`, `arrayField()`, `var` delegates on `SaveDataBuilder`; `config { magic = "GBKT" }` nested block; `SaveDataRef.exists()`, `SaveDataRef.load()`, `SaveDataRef.save()`, `SaveDataRef.erase()`, `SaveDataRef.eraseAll()`, `SaveDataRef.copy()`; field access as variables (`save.score += 10`); flags bit operations (`save.flags.setBit(0)`, etc.); array field access (`save.inventory[0]`).

## Why This Matters

The field-level API makes persistent game state feel like first-class DSL variables — strongly typed save slots, automatic CRC, and named fields without raw C memory management. Without this, save/load requires using the coarser `triggerSystem(saves)` which gives no field-level access.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

```kotlin
// Define save data structure
val save = saveData("mygame") {
    var score by u16Field()           // 2 bytes (0-65535)
    var level by u8Field(default = 1) // 1 byte with default value
    var lives by u8Field(default = 3)
    var highScore by u16Field()
    var playerX by u8Field()
    var playerY by u8Field()
    var flags by flagsField()         // 8 boolean flags (1 byte)
    var inventory by arrayField(8)    // Fixed-size array (8 bytes)

    config {
        slots = 3                     // 3 save slots
        checksum = Checksum.CRC8      // Data integrity (NONE, XOR, CRC8, SUM16)
        magic = "GBKT"                // 4-char validation marker
        version = 1                   // Save format version
    }
}

// Usage in scenes (assumes SceneRefs are declared)
titleScene = scene("title") {
    enter {
        // Check if save exists before loading
        whenever(save.exists(slot = 0)) {
            printAt(4, 8, "CONTINUE")
        }
    }

    frame {
        whenever(buttons.a.pressed) {
            save.load(slot = 0)
            navigate(gameplayScene)
        }
    }
}

gameplayScene = scene("gameplay") {
    frame {
        // Access save fields like normal variables
        save.score += 10

        // Compare with save data
        whenever(score isAbove save.highScore) {
            save.highScore set score
        }

        // Save on checkpoint
        whenever(buttons.start.pressed) {
            save.save()  // Saves to current slot
        }
    }
}

// Flags field for boolean states
save.flags.setBit(0)        // Set flag 0
save.flags.clearBit(1)      // Clear flag 1
save.flags.toggleBit(2)     // Toggle flag 2
whenever(save.flags.isSet(0)) { /* flag 0 is set */ }

// Array field access
save.inventory[0] set 5     // Set item at index 0
whenever(save.inventory[0] isEqualTo 5) { /* ... */ }

// Slot management
save.erase(slot = 1)        // Erase a slot
save.eraseAll()             // Erase all slots
save.copy(from = 0, to = 1) // Copy slot 0 to slot 1
```
