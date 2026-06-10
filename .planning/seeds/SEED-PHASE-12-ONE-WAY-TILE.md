# SEED: Phase 12 — `oneWayThreshold(M)` ONE_WAY tile-collision extension

**Created:** 2026-05-25 (Phase 12 close — Plan 12-27 administrative phase-close)
**Origin phase:** 12 (port-platformer-template-gbdk-example-to-gbkt)
**Source:** Phase 12 CONTEXT.md §D-13b (deferred at phase planning; captured at phase close per the same decision)
**Status:** Deferred — captured for future absorption when a port surfaces the real need.
**Routing:** Phase 13 IFF a future port surfaces real need (e.g. a Mario / Mega Man / Castlevania-style platformer port, or a shmup with traversable platforms). Pre-budgeting in Phase 13 NOT recommended — the platformer-template substrate does not exercise ONE_WAY tiles, so no pull from the Phase 12 close exists today.
**Blast radius:** Medium (~4 files: `PlatformerVisitor` tilemap-physics branch + `is_tile_solid` HOME helper + `PlatformerPhysicsConfig` + `PlatformerBuilders`).

## Context

Phase 12 ships solid-only tilemap collision via `platformerPhysics { solidThreshold(N) }` —
"tile index < N is solid, tile ≥ N is passable". This is the simplest classification a
tilemap-physics game can have and matches the reference platformer_template's behavior
exactly (per the upstream GBDK example).

Many classic platformers (Mario, Mega Man, Castlevania, Sonic) use a third tile class:
**ONE_WAY** — solid from above (player lands on top) but passable from below (player
jumps THROUGH from underneath). This enables traversable platforms ("jump up through,
stand on top") that are the spine of vertical-platforming level design.

Phase 12's substrate has no ONE_WAY tiles by intent — the port pulls only what the
reference uses, and the reference uses only SOLID + PASSABLE. The seed exists to
capture the design decision for the day a future port WILL need ONE_WAY.

## What's Deferred

A dedicated phase (Phase 13 absorption, OR a Phase 12-style sub-phase if the future port
landing is staged off Phase 12 directly) that:

### 1. New DSL surface — `oneWayThreshold(M)`

```kotlin
platformerPhysics {
    solidThreshold(17)       // tile index < 17 = SOLID
    oneWayThreshold(40)      // 17 ≤ tile index < 40 = ONE_WAY (solid only from above)
    // tile index ≥ 40 = PASSABLE (background decoration, ladders, etc.)
}
```

The `solidThreshold` / `oneWayThreshold` pair partitions the tileset's index space into
three contiguous ranges. Tile-index ordering is conventional and matches how upstream
GBDK examples ship tilesets (low indices = solid, mid indices = one-way, high indices =
decoration).

Per-level override should compose with the existing per-level `platformerPhysics { }`
extension pattern (D-12 from Phase 12 CONTEXT), e.g. `level("world1Area3") { platformerPhysics { oneWayThreshold(38) } }`.

### 2. Codegen surface changes

- **`is_tile_solid()` HOME helper becomes `tile_classify()`** — returns an enum
  `{ TILE_SOLID, TILE_ONE_WAY, TILE_PASSABLE }` instead of a boolean. Body extends to
  read the new `_current_level_one_way_threshold` global alongside the existing
  `_current_level_non_solid_tile_count` (which becomes the solid-threshold global).
- **5-point AABB probe branches on the classification.** Today the probe rejects any
  contact with a solid tile. With ONE_WAY:
  - SOLID: reject contact from any direction (unchanged).
  - ONE_WAY: reject contact ONLY when `_player_vy > 0` (falling) AND the player's
    feet-row was strictly above the tile's top edge during the previous frame. Both
    conditions are required to allow jump-through behavior.
  - PASSABLE: always allow (unchanged).
- **New globals:**
  - `_current_level_solid_threshold (UINT8)` — renames the existing
    `_current_level_non_solid_tile_count` to make the semantic explicit.
  - `_current_level_one_way_threshold (UINT8)` — new. Defaults to
    `_current_level_solid_threshold` (i.e. ONE_WAY range is empty) when the DSL
    doesn't call `oneWayThreshold(...)`, preserving Phase 12 compatibility.
- **Per-level override extends** — the existing per-level `platformerPhysics` shadow
  pattern (D-12) gains a `_level_<N>_one_way_threshold` shadow alongside the
  solid-threshold shadow.

### 3. Blast-radius assessment

| File | Change |
|------|--------|
| `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/PlatformerBuilders.kt` | Add `oneWayThreshold(Int)` builder method |
| `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/PlatformerPhysicsConfig.kt` | Add `oneWayThreshold: Int?` field |
| `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` | Extend `is_tile_solid` → `tile_classify` emission + 5-point probe branching |
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | Emit the new `_current_level_one_way_threshold` global + per-level shadow |

~4 files; medium scope. No IR-shape changes required (the classification is purely a
runtime enum derived from existing tile-index reads). No bank-allocation impact (no new
data tables). No metasprite / camera changes.

### 4. JVM-tier invariant (when implemented)

A test paralleling Phase 12 D-16 invariant #2 (`is_tile_solid` helper shape):
- `tile_classify()` body contains a 3-arm branch (SOLID / ONE_WAY / PASSABLE) selected
  by comparing `_current_level_map[index]` against BOTH thresholds.
- The 5-point probe body contains the conditional jump-through gate (`_player_vy > 0` AND
  `prev_feet_row < tile_top`) on the ONE_WAY arm.
- Per-function awk brace-walk grep (per CLAUDE.md scope-level grep gates corollary).

## Routing Recommendation

**Phase 13** IFF a future port surfaces real need. Specific triggers:

1. A new platformer port that USES the upstream `oneWayThreshold` pattern. Mario-style
   ports are the obvious candidate; the upstream GBDK examples directory doesn't
   currently include one, but community ports do.
2. A shmup port that adds platforms (e.g. Twinbee, Parodius — both have traversable
   platforms even though they're shmups).
3. A vertical-scrolling platformer port (would also pull D-14b vertical-scroll codegen).

**NOT recommended for proactive Phase 13 budgeting** — the framework already supports
SOLID + PASSABLE cleanly, and the addition is small enough (~4 files) to absorb as a
mid-port-phase plan once a triggering port exists. Pre-budgeting risks designing the
DSL surface without a concrete port to validate it against.

## Reference

**Upstream GBDK reference for ONE_WAY tile rendering:** N/A in
`/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/` (the upstream
reference is also solid-only). When this seed activates, look at community Mario-clone
ports (e.g. the GBStudio engine's `actor_collision` for the classic implementation
shape).

**Internal gbkt reference:**
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
  — the existing tilemap-physics codegen branch is the host for this extension.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  `buildSetupCurrentLevelFunction` — the host for the new `_current_level_one_way_threshold`
  global initialization.

## Revival Conditions

1. A new port phase opens whose reference C source contains a `oneWayThreshold` /
   `ONE_WAY_TILE` / equivalent constant.
2. A Phase 13 framework-shaping phase explicitly pulls this seed into scope (would
   require evidence that pre-budgeting beats opportunistic absorption — see Routing
   above).

## Related artifacts

- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-CONTEXT.md` §D-13b
  (the deferral decision)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-CONTEXT.md` §D-12
  (per-level `platformerPhysics` extension pattern that ONE_WAY composes with)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-CONTEXT.md` §D-20
  (Phase 13 routing convention)
- Related: [[SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS]] — sibling
  PlatformerVisitor extension work; if a future phase fixes both this seed AND the
  auto-emission gaps, group them.
