# SEED: Phase 12 — Player metasprite levitating (not pinned to ground tilemap-collision floor)

**Created:** 2026-05-25 (Phase 12 Plan 12-27 human-checkpoint review — user-surfaced)
**Origin phase:** 12 (port-platformer-template-gbdk-example-to-gbkt)
**Source:** User direct review of `evidence/uat-screenshots/anchor-5/01-near-end.png` during Plan 12-27 ship-clearance checkpoint
**Status:** Active blocker — gates Phase 12 close per Visual Evidence Rule (CLAUDE.md). Phase 12 cannot ship until resolved.
**Routing:** Phase 12.6 (recommended absorption into existing main()-loop level-switch codegen fix phase — the orchestrator decides whether to add to 12.6 or open sibling 12.7) — NOT routed to Phase 13.
**Blast radius:** Medium-to-large (`gbkt-genre-platformer/PlatformerVisitor` 5-point AABB probe codegen, `gbkt-backend-gbdk/GBDKPipelineV2.buildPhysicsUpdateFunction`, possibly `gbkt-examples/platformer-template/.../PlatformerTemplate.kt` spawn position).

## Symptom

In the round-2 `01-near-end.png` capture (level 1, near the right-edge level-end
trigger), the player metasprite is rendered visibly **above** the ground tilemap floor
row, with several pixels of gap between the player's foot-row and the top edge of the
nearest solid tile.

The player is NOT pinned to the floor — gravity + the 5-point AABB probe + the
`is_tile_solid` HOME helper SHOULD cooperate to settle `_playerY` such that the player's
foot-row exactly contacts the top edge of the highest underlying solid tile each frame
when no jump is active. The visible gap means one of those three layers is wrong.

**This is a visual SC under the CLAUDE.md Visual Evidence Rule** — variable
assertions like `assertVariable("_playerVy", 0)` would falsely report "grounded"
because `_playerVy` is indeed 0 (collision is registering as ending the fall), but the
visual surface (the screen) shows the player is NOT on the ground. The bug class is
exactly the one the Visual Evidence Rule was codified to catch (per Phase 07.4 history).

## Why this was not surfaced earlier in Phase 12

- **Anchor 2 (Plan 12-20)** locked grounded-state via `_playerVy = 0` after a jump
  arc (`0 → -800 → 0`). The arc completed; `_playerVy` returned to 0. The visual
  ambiguity was flagged as `SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY` and
  framed as a spawn-position concern ("player at screen-vertical-center, no visible
  platform clearly underneath"). **In retrospect that seed may have been THIS bug
  under a different framing** — the player wasn't ambiguously positioned, it was
  hovering above the floor because the physics codegen wasn't producing a hard
  ground-snap.
- **Anchor 3 (Plan 12-21, horizontal scroll), Anchor 4 (Plan 12-22, walk-frame
  cycle):** Both anchors verified motion truths (scroll registers; walkFrameIdx
  cycles) and did not gate on "player IS visibly on the floor". The levitation was
  visible but treated as orthogonal cosmetic.
- **Anchor 5 (Plan 12-23):** Round-2 PNG captures (`01-near-end.png`) made the
  levitation visible to the user during 12-27 ship-clearance review. The Plan 12-23
  load-bearing truths (cross-bank tilemap reload + level-end trigger fire) were
  proven; the orthogonal visual defect was not in the per-anchor verification
  matrix's load-bearing list.

The user's 12-27 review correctly identifies this as a Phase-12-close blocker:
allowing a port to ship with a player visibly hovering above the floor would
violate the Visual Evidence Rule AND ship a broken substrate (any future port
inheriting the platformer-template would inherit the bug).

## Suspected root causes (in priority order, for the investigator)

### 1. 5-point AABB probe missing or wrong on the foot-row (HIGHEST PRIORITY)

The reference GBDK `platformer_template/src/player.c` uses a 5-point probe that
checks (top-left, top-right, mid-left, mid-right, BOTTOM-CENTER) and applies a
"snap to top of solid tile" correction when the BOTTOM-CENTER probe registers a
solid tile AND the previous frame's foot-row was strictly above that tile's top
edge. Suspect: gbkt's `PlatformerVisitor` emission of the 5-point probe (landed in
Plan 12-11) may:

- Omit the snap-to-top step entirely (player's `_playerY` is integrated by
  `_playerVy` only; collision merely zeroes `_playerVy` without correcting position),
  OR
- Have a wrong coordinate for the bottom-center probe (e.g., probing at
  `_playerY + 23` instead of `+ 24`, leaving a 1-pixel gap), OR
- Have a wrong tile-coordinate-system conversion (e.g., dividing the world-pixel
  Y by 8 with the wrong rounding, finding the tile ABOVE the intended floor).

**Investigation entry point:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` → `build5PointProbe()` (or whatever the function is named in the wave-7 codegen landing from Plan 12-11). Compare line-by-line against `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c`.

### 2. Gravity / position integration order

`platformer_physics_update` integrates `_playerY += _playerVy >> 4` BEFORE applying
the collision correction. If the correction step is missing the "snap to tile top"
when the integrated position would push the foot-row INTO a solid tile, the player
settles one frame's gravity-velocity worth of pixels above the floor each frame
(visible as a stable hover, since gravity adds the same delta every frame and
collision zeroes `_playerVy` without correcting the over-integration).

**Investigation entry point:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` → `buildPhysicsUpdateFunction()` body. The reference applies the snap inline within the collision branch; gbkt may have separated them in a way that breaks ordering.

### 3. Spawn position vs. tilemap floor row mismatch

Per the existing `SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY` seed, the
DSL hardcodes spawn at `playerY by i16Var(72 shl 4)` (Y=72 pixels, screen vertical
center). If the world1Area1 tilemap's floor row is at e.g. Y=128 (bottom-ground), the
player will fall from Y=72 toward Y=128 — and if root causes 1 or 2 above are also
present, the player settles partway down and the spawn-vs-floor mismatch is what
makes the hover most visible.

**Possible relationship to existing seed:** Fixing root cause 1 or 2 may close
`SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY` as a byproduct. The investigator
should check both seeds together; if so, dedupe one seed in the closing SUMMARY.

### 4. Tile-coordinate-system rounding in `is_tile_solid`

`is_tile_solid()` computes the tile index from `(world_x >> 3, world_y >> 3)`. If
the integer divide rounds toward zero (typical for signed `>>` on negative values,
but `_playerX`/`_playerY` are unsigned-positive in the substrate), the floor-row tile
may be misidentified at the boundary, causing the probe to see "passable" when the
actual tile is "solid". Lower probability than #1 but worth checking.

**Investigation entry point:** `is_tile_solid()` HOME-bank helper body (D-16
invariant #2 locked its shape but not its boundary correctness).

## What's deferred to the fix phase

A dedicated investigation + fix that:

1. Captures a reference-vs-gbkt diff of `platformer_physics_update` AND the 5-point
   probe AND the `is_tile_solid` body, line by line. The Plan 12-24
   `oracle-comparison.md` Signal 2 ("generated-C diff") section is the starting
   point — extend it with a focused diff of the three physics-related functions.
2. Identifies the root cause (#1 / #2 / #3 / #4 above OR a compound).
3. Lands the codegen fix in `PlatformerVisitor` and/or `GBDKPipelineV2` per the
   diagnosis.
4. Re-shoots Anchor 2 (`01-grounded.png`, `03-landed.png`) AND Anchor 5
   (`01-near-end.png`) with the fix. Both anchors must show the player visibly
   pinned to the ground tile-row with zero pixel gap.
5. Adds a JVM-tier emission invariant that locks "the snap-to-tile-top step exists
   in the physics-update body" (per-function awk brace-walk grep, per CLAUDE.md
   scope-level grep gates corollary). This is the new D-16-style invariant that
   would have caught the bug at the codegen tier.
6. If the spawn-position seed (`SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY`)
   is closed as a byproduct, the closing SUMMARY marks both seeds resolved with a
   cross-reference; otherwise leave the spawn seed open for the next platformer
   port.

## Visual Evidence Rule alignment

**This seed is a textbook Visual Evidence Rule case** (per CLAUDE.md, codified after
Phase 07.4):

- The verification truth is "X is visible on screen" — specifically "the player is
  visibly pinned to the floor".
- Variable assertions (`_playerVy = 0`, `_grounded = 1` if it existed) do NOT prove
  the visual outcome, because the variable can register the END-of-fall condition
  while the visual surface shows the player at the WRONG Y position.
- A runtime screenshot is the required evidence — and `01-near-end.png` (already
  captured at Plan 12-23) is the binding visual artifact that surfaced the bug.
- The fix-phase verification MUST include a re-shot screenshot showing the player
  pinned to the floor; codegen GREEN alone is necessary but never sufficient.

## Routing Recommendation

**Phase 12.6 (preferred)** — absorb into the existing main()-loop level-switch
codegen fix phase that the orchestrator inserted post-Plan 12-23 OPTION A. The
levitation defect lives in the same `PlatformerVisitor` / `GBDKPipelineV2` codegen
surface as DEFECT-1 (card-paint) and DEFECT-2 (level-skip), so grouping them
keeps the investigator's context tight.

**Phase 12.7 (sibling, alternative)** — if the levitation root cause turns out to
require deeper `PlatformerVisitor` restructuring than the 12.6 scope can absorb, the
orchestrator may open a sibling 12.7. Per the user's BLOCKED signal at Plan 12-27
human-checkpoint and per memory rule `feedback_many_small_plans_terminal_subphase.md`,
12.6 (or 12.7) MUST be terminal — no 12.6.1 / 12.6.2 sub-sub-phases. The orchestrator
decides whether the scope fits 12.6 alone OR requires the sibling.

**NOT routed to Phase 13** — this is a substrate-correctness blocker, not a
framework-shaping DSL gap. Phase 13's "framework-primitives" scope is the wrong
absorption target.

## Phase 12 close gating

Phase 12 close (Plan 12-27 administrative final state) is **BLOCKED on this defect
being resolved**, along with:

1. DEFECT-1 (main() guard card-paint overwrite) — Phase 12.6 baseline (existing)
2. DEFECT-2 (level-skip 1→3) — Phase 12.6 baseline (existing)
3. ISSUE A (grass white-pixel artifacts) — `SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS` —
   per user 12-27 review, this is ALSO gated for Phase 12 close (NOT orthogonal).
   Orchestrator decides whether to absorb into 12.6 or open a sibling.
4. **THIS seed** (player levitating, not pinned to ground)
5. Anchor 1 re-verification AFTER the grass fix (since grass white-pixels affect
   the title screen / world1Area1 tilemap render that Anchor 1 verified)

Per the user's BLOCKED signal at the Plan 12-27 ship-clearance checkpoint, the
Phase 12 close contract is updated:

> Phase 12 closure is GATED on:
> - Phase 12.6 (+ optional sibling 12.7) ships GREEN
> - Anchor 5 visual re-shoot GREEN after 12.6
> - Grass white-pixel issue resolved + Anchor 1 re-verified GREEN
> - THIS seed (player levitating) resolved + Anchor 2 + Anchor 5 re-verified GREEN

The 3-signal Signal 3 verdict in `evidence/oracle-comparison.md` stays RED until
all four visual gates above are GREEN.

## Revival Conditions

This seed activates immediately for the Phase 12.6 (or sibling 12.7) investigator.
It does NOT have a deferred-revival condition because Phase 12 close is gated on it.

## Related Artifacts

- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/01-near-end.png` — the binding visual artifact showing the hover
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/01-grounded.png` — early anchor where the hover was visible but framed as spawn-clarity
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/03-landed.png` — same anchor, post-jump
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` — 5-point probe codegen (Plan 12-11)
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — `buildPhysicsUpdateFunction()` body (host for the snap-to-tile-top step)
- Reference: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c` — the upstream 5-point probe + snap reference
- Related: [[SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY]] — likely the same defect under a different framing; investigator should check both together
- Related: [[SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS]] — sibling PlatformerVisitor gap seed (4 gaps); if 12.6 absorbs the levitation fix, consider grouping with that seed's gap-1..4 work
- Related: [[SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS]] — orthogonal but co-gating (per user 12-27 review)
- CLAUDE.md §"Verification Methodology — Visual Evidence Rule" — the rule this seed exemplifies
