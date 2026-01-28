# RPG Module

Turn-based RPG system for character stats, abilities, items, monsters, and combat.

## Architecture

```
DSL Builders → IR Nodes → Codegen → C Output
```

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Stats.kt` | Character statistics (HP, SP, ATK, DEF, MATK, MDEF, AGL) | 443 |
| `Character.kt` | Player character definition with entity composition | 559 |
| `Ability.kt` | Skills/spells with targeting, costs, and effects | 453 |
| `Item.kt` | Consumables and equipment definitions | 696 |
| `Monster.kt` | Enemy definitions with tiers, AI, and loot | 970 |
| `StatusEffect.kt` | Buffs/debuffs with stacking modes | 797 |
| `Leveling.kt` | Experience curves and level-up mechanics | 571 |
| `Equipment.kt` | Equipped items and stat bonuses | 165 |
| `Inventory.kt` | Item container with stack limits | 230 |
| `Battle.kt` | Turn-based combat state machine | 916 |
| `BattleMenu.kt` | Combat UI configuration | 604 |
| `BattlePresentation.kt` | Visual feedback (damage numbers, shake) | 346 |
| `TurnOrder.kt` | Speed-based or custom turn ordering | 193 |
| `TargetSelection.kt` | Single/multi/all targeting modes | 248 |
| `Damage.kt` | Aspect-based (elemental) damage with resistances | 331 |
| `CombatFormulas.kt` | Hit/crit/variance formula configuration | 343 |
| `CombatState.kt` | State tracking during battle | 367 |
| `ActionExecution.kt` | Action queue and pipeline | 288 |
| `StatOperations.kt` | Stat read/modify/clamp utilities | 101 |

## Quick Reference

### Character Definition
```kotlin
val hero by character {
    name("Hero")
    stats { hp(100); sp(50); atk(15); def(10); matk(8); mdef(8); agl(12) }
    level(1, maxLevel = 99, expCurve = ExpCurve.STANDARD)
    onLevelUp { stats.hp += 10; stats.atk += 2 }
}
```

### Monster Definition
```kotlin
val goblin by monster {
    name("Goblin")
    tier(MonsterTier.COMMON)
    baseStats { hp(30); atk(8); def(5); agl(10) }
    ai {
        hpBelow(25) { flee() }
        basicAttack(context.randomTarget)
    }
    exp(15)
    drops { drop(herb, chance = 30) }
}
```

### Ability Definition
```kotlin
val fireball by ability {
    name("Fireball")
    cost(sp = 8)
    targeting(TargetingMode.SINGLE_ENEMY)
    aspect(Aspect.FIRE)
    execute { target.damage(caster.matk * 2, Aspect.FIRE) }
}
```

### Battle System
```kotlin
val combat by battle("combat") {
    maxPartySize(4)
    maxEnemies(3)
    turnOrder(TurnOrderStrategy.SPEED_BASED)
    onState(BattleState.VICTORY) { awardExp(); awardDrops() }
    onState(BattleState.DEFEAT) { scene(gameoverScene) }
}

// In battle scene:
scene("battle") {
    every.frame {
        battleUpdate(combat)  // Drive state machine each frame
        whenever(combatIsInState("COMBAT_STATE_VICTORY")) { /* show victory */ }
    }
}
```

### Status Effects
```kotlin
val poison by statusEffect {
    name("Poison")
    debuff()
    duration(5)
    damagePerTurn(10)
    stackMode(StackMode.REFRESH_DURATION)
}
```

## Key Types

- `CharacterRef` - Type-safe reference to character
- `AbilityRef` - Type-safe reference to ability
- `ItemRef` - Type-safe reference to item
- `MonsterRef` - Type-safe reference to monster
- `StatusEffectRef` - Type-safe reference to status effect
- `BattleStateRef` - Type-safe reference to battle

## Stacking Modes (StatusEffect)

| Mode | Behavior |
|------|----------|
| `NONE` | Only one instance allowed |
| `REFRESH_DURATION` | Resets duration on reapply |
| `ADD_DURATION` | Adds duration on reapply |
| `INTENSITY` | Stacks intensity up to max |

## Targeting Modes

| Mode | Description |
|------|-------------|
| `SELF` | Caster only |
| `SINGLE_ALLY` | One ally |
| `ALL_ALLIES` | All allies |
| `SINGLE_ENEMY` | One enemy |
| `ALL_ENEMIES` | All enemies |
| `ANY` | Any single target |

## Known Issues (Resolved)

1. ~~**BattlePresentationCodegen.kt:177** - Dialog system integration TODO~~ (DONE: CombatCoreCodegen.kt)
2. ~~**BattlePresentationCodegen.kt:267** - Damage number sprites TODO~~ (DONE: CombatCoreCodegen.kt)
3. ~~**BattleCodegen.kt:439** - Monster status effect checking TODO~~ (DONE: `_combatant_can_act()`)

## Remaining TODOs

- `gbkt-backend-gbdk/.../ExplorationCodegen.kt:521` - Sprite position interpolation during movement

## Related Modules

- `gbkt-backend-gbdk/.../codegen/rpg/` - Code generation for RPG systems (in backend)
- `ir/` - IR nodes for RPG (AbilityIR, StatsIR, etc.)
- `entity/CombatComponents.kt` - Combat-related entity components
