# SEED: Phase 12 — Platformer Player Spawn Position Clarity

> **Triage:** CONFIRMED-OPEN — [TRIAGE.md#SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY](.planning/phases/16-seed-triage/TRIAGE.md#SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY) · 2026-06-12

**Created:** 2026-05-23 (Phase 12 Wave 13 — Plan 12-20 Anchor 2 close-out)
**Origin phase:** 12 (port-platformer-template-gbdk-example-to-gbkt)
**Source:** Plan 12-20 human-verify gate feedback
**Status:** Deferred — captured for a future inserted phase or Phase 13 absorption.
**Routing:** Open; not yet bound to a target phase
**Blast radius:** Small (likely just `gbkt-examples/platformer-template/src/main/kotlin/.../PlatformerTemplate.kt`); may surface a level-design gap in the framework

## Context

Plan 12-20's anchor 2 (tilemap collision jump cycle) closed GREEN: the physics
transition `playerVy = 0 → -800 → 0` over 61 frames is mathematically a complete
jump arc that ended on a solid tile via `is_tile_solid` + the 5-point probe (the
re-grounding proves the collision system works end-to-end).

However, the user observed during human-verify that `01-grounded.png` and
`03-landed.png` both show the player at roughly screen-vertical-center, with no
visible platform tile clearly underneath. The DSL spawns the player at
`playerY by i16Var(72 shl 4)` which compiles to `INT16 _playerY = 1152u;` (pixel
Y=72, screen vertical center). The 5-point foot probe at `player_real_y + 24u =
Y=96` finds a solid tile somewhere — likely a middle-row platform in world1Area1
— but the visual context doesn't make it obvious whether the spawn is grounded
on the intended floor row or merely caught by a middle platform.

The GBDK reference (`/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/`)
initialises the player position per-level via `level_start_pos`, with world1Area1
using a position closer to the bottom-ground row. Our DSL uses a single hardcoded
spawn for all levels.

## What's Deferred

1. Either (a) re-position the spawn in `PlatformerTemplate.kt` to land on the
   bottom-ground row of world1Area1 (matching the reference's per-level start
   pos), OR (b) add per-zone `spawnPosition(x, y)` DSL on `ZoneBuilder` so each
   zone defines its player start coordinates.
2. Re-run Plan 12-20 anchor 2 with the corrected spawn so the visual clearly
   shows player → floor-ground → jump → floor-ground.
3. Optionally: capture diagnostic that dumps `_playerX/_playerY` + the 4 corner
   probes' tile values at the moment we declare "grounded" to make the visual
   evidence self-checking.

## Revival Condition

- Reviewer wants the visual to demonstrate "player on the floor" (not just
  "player at some grounded coordinate") at Anchor 2.
- Phase 13 or later absorbs the per-zone spawn DSL improvement.

## Related Artifacts

- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/01-grounded.png`
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/03-landed.png`
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/anchor2-variables.txt`
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt:151`
- Reference: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c` (level_start_pos initialisation)
- Related: [[SEED-PHASE-12-PLAYER-METASPRITE-RENDER]] (placeholder square issue is orthogonal but compounds the visual ambiguity)
