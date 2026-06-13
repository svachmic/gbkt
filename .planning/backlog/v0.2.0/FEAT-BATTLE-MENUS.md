---
id: FEAT-BATTLE-MENUS
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-12"
triage_date: 2026-06-12
---

# FEAT-BATTLE-MENUS: Battle menu builder, combatFormulas, custom battle states, battleTransition

## Source

Removed from context/DSL_REFERENCE.md lines 2408–2483 (commit 929653a4).

**Implemented today:** `simpleBattle("combat") { party(hero); encounter { +goblin }; onVictory { }; onDefeat { } }` at `gbkt-genre-rpg/.../dsl/RpgExtensions.kt:168`. `battleUpdate(BattleRef)` at `RpgExtensions.kt:348`. 19 built-in `CombatStates.*` constants. What is NOT implemented: `battleMenu("menu") { position(0,12); commands { command("Attack") { } }; statusDisplay { } }` builder, `combatFormulas { d20HitRoll(baseAC=10); criticalChance(5); damageVariance(25) }` builder, `val cutsceneState by battleState("Cutscene")` custom state delegate, `battleTransition(cutsceneState)` script op.

## Why This Matters

A `battleMenu` builder would make custom combat UI declarative — without it, authors must write C-escape-hatch code for anything beyond the built-in text-mode battle display. `combatFormulas` would allow customizing hit/crit/damage math. Custom battle states beyond the 19 built-in states enable cutscene-in-battle, skill-animation-pause, and other narrative devices.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

### Battle Menu

```kotlin
val battleMenu by battleMenu("menu") {
    position(0, 12)           // Menu position (tile coords)

    // Main commands
    commands {
        command("Attack") { action(ActionType.ATTACK) }
        command("Magic") { submenu(magicMenu) }
        command("Item") { submenu(itemMenu) }
        command("Flee") { action(ActionType.FLEE) }
    }

    // Status display configuration
    statusDisplay {
        showHp(true)
        showSp(true)
        showStatusIcons(true)
        position(0, 0)
    }
}
```

### Combat Formulas

```kotlin
val combat = combatFormulas {
    // Hit formula strategies
    d20HitRoll(baseAC = 10)           // D&D-style: roll + ATK vs DEF + AC
    percentageHitChance(baseChance = 80, minChance = 20, maxChance = 95, perDiff = 3)
    agilityBasedHit(baseChance = 70)  // Hit based on AGL difference
    alwaysHits()                       // No miss chance

    // Critical hit strategies
    criticalChance(5)                  // Flat 5% chance
    criticalOnHighRoll(threshold = 20, dieSize = 20)  // Natural 20
    noCriticalHits()                   // Disable crits
    criticalMultiplier(200)            // 2x damage on crit

    // Damage variance strategies
    damageVariance(25)                 // ±12.5% variance
    damageMultiplierRange(min = 75, max = 125)  // Lookup table
    noVariance()                       // Exact damage

    // Fumble system
    enableFumble(threshold = 1)        // Fumble on natural 1
}
```

### Custom Battle States

```kotlin
val game = game("MyGame") {
    // Define custom battle states beyond the 19 built-in states
    val cutsceneState by battleState("Cutscene")
    val animationState by battleState("Animation")

    val combat by battle("combat") {
        onState(cutsceneState) {
            // Custom cutscene logic
        }
    }

    scene("battle") {
        frame {
            battleTransition(cutsceneState)  // Transition to custom state
        }
    }
}
```
