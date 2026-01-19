# Combat Module

Abstract battle engine system supporting multiple combat styles for different game genres.

## Purpose

The combat module provides a genre-agnostic battle engine abstraction. Instead of hardcoding turn-based JRPG combat, it defines interfaces that support:
- Turn-based combat (Dragon Quest, Final Fantasy 1-3)
- Active Time Battle (Final Fantasy 4-9)
- Real-time action combat (Zelda, Secret of Mana)
- Grid-based tactical combat (Fire Emblem, Final Fantasy Tactics)

## Files

| File | Purpose |
|------|---------|
| `BattleEngine.kt` | Core interfaces and implementations |
| `BattleEngineExtensions.kt` | GameBuilder property delegates |

## Key Types

### Combat Types

```kotlin
enum class CombatType {
    TURN_BASED,    // Traditional JRPG
    ACTIVE_TIME,   // ATB gauge-based
    REAL_TIME,     // Action combat
    TACTICAL,      // Grid-based tactics
    WAVE_SURVIVAL  // Tower defense / arena
}
```

### Battle Engine Interface

```kotlin
interface BattleEngine {
    val id: String
    val combatType: CombatType
    val maxPartySize: Int
    val maxEnemies: Int
    val onVictoryStatements: List<IRStatement>
    val onDefeatStatements: List<IRStatement>
    var systemIndex: Int
}
```

### Engine Implementations

- `TurnBasedBattleEngine` - Turn order strategies, flee mechanics
- `ActiveTimeBattleEngine` - Gauge fill rate, pause settings
- `RealTimeBattleEngine` - Hit stun, i-frames, knockback, blocking
- `TacticalBattleEngine` - Grid size, facing, flanking bonuses

## DSL Usage

```kotlin
// Turn-based JRPG
val combat by turnBasedBattle {
    name("Main Combat")
    maxPartySize(4)
    maxEnemies(4)
    turnOrder(TurnOrderStrategy.SPEED_BASED)
    fleeMechanics(baseChance = 50, perAgility = 2)

    onVictory { awardExp(); scene(gameplayScene) }
    onDefeat { scene(gameOverScene) }
}

// Action combat
val combat by realTimeBattle {
    name("Action Combat")
    hitStun(10)
    invincibility(60)
    knockback(8)
    blocking(enabled = true, reduction = 50)

    onHit { flashSprite(); playSound(hit) }
}

// Tactical grid combat
val combat by tacticalBattle {
    name("Tactical Combat")
    gridSize(16, 16)
    baseMoveRange(4)
    facing(enabled = true, flankBonus = 25)
    turnOrder(TurnOrderStrategy.SPEED_BASED)
}
```

## Relationship to RPG Module

The `combat/` module provides the abstract engine, while `rpg/` provides:
- Character/monster definitions
- Stats, abilities, items
- Status effects
- Battle menu configuration

The RPG module's `Battle.kt` uses `TurnBasedBattleEngine` internally.

## Extending

To add a new combat type:

1. Add enum value to `CombatType`
2. Create new engine class implementing `BattleEngine`
3. Create builder class extending `BattleEngineBuilder`
4. Add delegate class in `BattleEngineExtensions.kt`
5. Add `GameBuilder` extension function
6. Add codegen in `codegen/combat/BattleEngineCodegen.kt`
