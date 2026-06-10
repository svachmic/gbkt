# gbkt-genre-rpg — RPG Genre Plugin

Provides the RPG domain model and DSL builders for gbkt. Defines characters, monsters, abilities, equipment, status effects, combat modes, and progression systems as data classes and type-safe Kotlin builders that compile down to Game Boy C code via the backend.

## Dependencies

- **Depends on:** `gbkt-core` (IR nodes, expression wrappers, DSL recording context)
- **Used by:** `gbkt-backend-gbdk` (codegen reads domain defs to emit C)
- **Registered via:** ServiceLoader as a genre plugin

## Structure

- `domain/` — Data classes and enums (21 files)
- `dsl/` — Builder DSL and extension functions (20 files)

## Key Domain Types

| Type | Role |
|------|------|
| `CharacterDef` | Party member: name, stats, level, exp curve, level-up ops |
| `MonsterDef` | Enemy: base stats, tier, AI behavior tree, exp/drops |
| `AbilityDef` | Skill/spell: cost, targeting, aspect, execute logic |
| `SimpleBattleDef` | Turn-based battle config: party size, enemy count, turn order, state hooks |
| `CombatStats` | Stat block: hp, sp, atk, def, matk, mdef, agl |
| `StatusEffectDef` | Buff/debuff: duration, damage-per-turn, stack mode |
| `EquipmentConfig` | Equipment system: slots, stat modifiers, set bonuses, requirements |
| `ClassDef` | Character class/job: stat growth rates, ability learn entries, job change mode |
| `LootTableDef` | Drop table: weighted entries with rarity tiers |
| `MerchantDef` | Shop: item list, crafting recipes |
| `CurrencyDef` | Currency type: max value, exchange rates |
| `PartyConfig` | Party constraints: max size, member configs |
| `BehaviorTree` | Monster AI: selector/sequence/condition/action nodes |

## Combat Modes

| Mode | Config Type | Builder |
|------|------------|---------|
| Turn-based | `SimpleBattleDef` | `SimpleBattleBuilder` |
| ATB (Active Time Battle) | `AtbConfig` | `AtbCombatBuilder` |
| Action RPG | `ActionRpgConfig` | `ActionRpgBuilder` |
| Roguelike | `RoguelikeConfig` | `RoguelikeBuilder` |
| Tactical Grid | `TacticalGridConfig` | `TacticalGridBuilder` |
| Wave Survival | — | `WaveSurvivalBuilder` |

## Key Enums

`TargetingMode`, `Aspect`, `StackMode`, `EffectCategory`, `EffectTrigger`, `MonsterTier`, `AoeShape`, `ResistType`, `ExpCurve`, `EquipSlot`, `JobChangeMode`, `Rarity`, `SaveMode`, `AutoSaveTrigger`, `CombatModel`, `RoguelikeMode`, `TargetStrategy`, `MonsterRole`

## DSL Entry Points (RpgExtensions.kt)

All top-level DSL functions are defined in `RpgExtensions.kt` and operate on `GameBuilder`:

`character`, `monster`, `simpleBattle`, `ability`, `statusEffect`, `equipmentSystem`, `characterClass`, `battleUpdate`, `atbCombat`, `waveSurvival`, `tacticalCombat`, `hooks`, `merchant`, `lootTable`, `craftingRecipes`, `partySystem`, `rpgSave`, `roguelike`, `abilityLearning`, `currency`, `actionRpg`

`RpgRegistry` manages thread-local character/monster registration during DSL evaluation. `BattleRef` is a type-safe handle returned by `simpleBattle` for referencing combat instances.

## Testing

```bash
./gradlew :gbkt-genre-rpg:test
```
