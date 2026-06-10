# Green Patch Report -- Cycle 005

**Branch:** `autoresearch/improve`
**Date:** 2026-03-22

## Fixed (10 of 11 issues)

| Issue | Summary | Files Changed |
|-------|---------|---------------|
| F-078 | `isMaybeSigned` now checks `CastExpr.targetType` for I8/I16 before recursing into inner expression | `gbkt-analysis/.../BitwiseOptimizationPass.kt` |
| F-079 | Added short-circuit folding: `Literal(0) && x` folds to `0`, `Literal(nonzero) \|\| x` folds to `1` | `gbkt-analysis/.../ConstantFoldingPass.kt` |
| F-081 | `PickupBuilder.build()` validates that zone `pickupId` references exist in defined pickups | `gbkt-engine/.../PickupBuilder.kt` |
| F-082 | `DropListBuilder.drop()`, `dropCurrency()`, and `LootEntry` now reject chance values outside 0-100 | `gbkt-genre-rpg/.../MonsterBuilder.kt`, `gbkt-genre-rpg/.../LootTableDef.kt` |
| F-083 | `PickupDefBuilder.effectType()` validates against allowed set: instant, timed, permanent | `gbkt-engine/.../PickupBuilder.kt` |
| F-084 | `LootEntry` init block validates `minQuantity <= maxQuantity` | `gbkt-genre-rpg/.../LootTableDef.kt` |
| F-085 | `PickupZoneBuilder.build()` requires positive width and height | `gbkt-engine/.../PickupBuilder.kt` |
| F-086 | Collection dispatch in `ScriptOpInterpreter` uses `splitCollectionOp` helper (longest-suffix-first) instead of fragile `endsWith` matching | `gbkt-core/.../ScriptOpInterpreter.kt` |
| F-087 | `CombatStatsBuilder` setters (hp, atk, def, sp, matk, mdef, agl) now validate at call site instead of deferring to `CombatStats.init` | `gbkt-genre-rpg/.../CharacterBuilder.kt` |
| F-088 | `TournamentBuilder.build()` requires at least 2 participants | `gbkt-genre-sport/.../SportBuilders.kt` |

## Skipped (1 of 11 issues)

| Issue | Reason |
|-------|--------|
| F-080 | `transformExprsInOp` catch-all `else` branch is an inherent consequence of the non-sealed `ScriptOp` interface design. No minimal fix exists: making it exhaustive requires sealing the interface (breaking the module architecture), and adding runtime warnings would change behavior without compiler-enforced guarantees. The comment on the else branch already documents the known safe types. |

## Final Test Status

```
BUILD SUCCESSFUL in 42s
168 actionable tasks: 57 executed, 111 up-to-date
```

All tests pass across all modules.
