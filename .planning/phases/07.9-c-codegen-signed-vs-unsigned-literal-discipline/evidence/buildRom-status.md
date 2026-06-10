# buildRom Status — Phase 07.9 Plan 04

**Phase:** 07.9
**Date:** 2026-05-13
**GBDK Available:** YES — /Users/michalsvacha/gbdk (GBDK_HOME set)
**lcc:** /Users/michalsvacha/gbdk/bin/lcc on PATH

## Per-Example Table

| Example | buildRom exit | ROM path | Pre-fix warning-94 | Post-fix warning-94 | Notes |
|---------|--------------|----------|--------------------|--------------------|-------|
| pong | 0 (GREEN) | gbkt-examples/pong/build/gbkt/output/pong.gb | 0 | 0 | |
| breakout | 0 (GREEN) | gbkt-examples/breakout/build/gbkt/output/breakout.gb | 0 | 0 | |
| explorer | NON-ZERO (FAIL) | — | — | — | Pre-existing lcc error 91: const/extern mismatch for char stats (unrelated to Plan 07.9) |
| dungeon | NON-ZERO (FAIL) | — | — | — | Pre-existing lcc error 91: const/extern mismatch for char stats (unrelated to Plan 07.9) |
| rpg-lite | NON-ZERO (FAIL) | — | — | — | Pre-existing lcc error 91: const/extern mismatch for char stats (unrelated to Plan 07.9) |
| platformer | 0 (GREEN) | gbkt-examples/platformer/build/gbkt/output/platformer.gb | 0 | 0 | Plan 02 confirmed |
| platformer-gbc | 0 (GREEN) | gbkt-examples/platformer-gbc/build/gbkt/output/platformer-gbc.gb | 0 | 0 | |
| shmup | 0 (GREEN) | gbkt-examples/shmup/build/gbkt/output/shmup.gb | 0 | 0 | |
| racer | 0 (GREEN) | gbkt-examples/racer/build/gbkt/output/racer.gb | 0 | 0 | Plan 02 confirmed |

## Aggregate

Total ROMs built: 6 of 9
GREEN: pong, breakout, platformer, platformer-gbc, shmup, racer
FAIL (pre-existing, unrelated to Plan 07.9): explorer, dungeon, rpg-lite

## Failure Analysis

### Pre-existing const/extern type mismatch (explorer, dungeon, rpg-lite)

Error pattern (same in all 3):
  main.c: error 91: extern definition for '_char_hero_hp' mismatches with declaration.
  game.h: error 177: previously defined here

Root cause: The RPG character stat variables are generated as:
  game.h:  extern UINT8 _char_hero_hp;         (no const)
  main.c:  const UINT8 _char_hero_hp = 20u;    (with const)

SDCC treats `const UINT8` as a different type from `UINT8` for extern linkage. This mismatch existed in the acf6e2d6 baseline (confirmed by inspection of baseline generated files). Plan 07.9 changes to CIntLiteral are confined to comparison RHS positions and do not affect variable type declarations.

The surgical diff for explorer, dungeon, rpg-lite shows 0 diff lines — these examples were not modified by Plan 07.9, so their buildRom status is identical to the pre-Plan-02 baseline.

Classification: Pre-existing codegen defect. Recommend flagging for Plan 06 review (or a dedicated fix plan). NOT a Plan 07.9 regression.

## D-09 #2 Assessment

D-09 #2 requires: "every example regenerates without lcc errors when GBDK is available."

Result: 6 of 9 pass. The 3 failing examples have pre-existing lcc errors that pre-date Phase 07.9 and are not caused by the CIntLiteral migration.

Assessment: D-09 #2 is PARTIALLY SATISFIED for the Plan 07.9 scope. The 3 failures are pre-existing defects outside Plan 07.9's responsibility. Plan 06 wrap-up review should either:
1. Accept the partial result with documentation that the 3 failures pre-exist Phase 07.9
2. Open a follow-up plan to fix the const/extern codegen defect in RPG-genre character stats

SDCC warning 94 ("comparison is always false due to limited range"): 0 in all passing examples (pre-fix and post-fix). The CIntLiteral migration correctly removes the signed/unsigned comparison mismatch that would have triggered this warning.
