---
id: SEED-008
status: dormant
planted: 2026-05-18
planted_during: v1.0 / Phase 10 close (code-review-gate, CR-01)
trigger_when: before Phase 12 (platformer_template port) starts — earliest case where actor sprites and metasprites coexist; also any user game that mixes both
scope: medium
---

# SEED-008: VRAM tile-slot collision when actor sprites and metasprites coexist (CR-01)

## Why This Matters

Phase 10 added metasprite tile-data emission to `GBDKPipelineV2`. Both
`buildSpriteDataLoadStatements()` (actor sprites) and the new
`buildMetaspriteTileDataLoadStatements()` (metasprites) each start their VRAM
slot counter at `nextTile = 0` independently.

Result: a game using **both** actor sprites AND metasprites emits two
`set_sprite_data(0, ...)` calls into `main()`. Whichever runs second silently
overwrites the first's tile data in VRAM. The first asset's pixels are gone with
no compile-time or runtime warning. The corruption is silent and platform-faithful
(GBDK doesn't catch VRAM aliasing).

The metasprites Phase 10 example doesn't have any actor sprites, so the bug is
**latent** in the current ROM. Phase 12 (platformer_template port — actors +
metasprites + tilemap together) will surface it the moment the port boots.

## Root Cause

In `gbkt-backend-gbdk/.../GBDKPipelineV2.kt`:

```kotlin
// Actor sprites
fun buildSpriteDataLoadStatements(gameIR: GameIR): List<CStatement> {
    var nextTile = 0     // ← starts at 0
    // ... emits set_sprite_data(nextTile, count, asset_tiles)
    //     nextTile += count
}

// Metasprites (added Plan 10-15 continuation)
fun buildMetaspriteTileDataLoadStatements(gameIR: GameIR): List<CStatement> {
    var nextTile = 0     // ← ALSO starts at 0 (BUG)
    // ... emits set_sprite_data(nextTile, count, ms.id + "_tiles")
}
```

Both functions are called from `buildMainFunction()` and the emitted statements
are concatenated. With actor + metasprite both present, the generated `main()`
contains e.g.:

```c
set_sprite_data(0u, 16u, player_tiles);   // actor at 0..15
set_sprite_data(0u, 48u, elephant_tiles); // metasprite OVERWRITES 0..15 + extends 16..47
```

Actor sprite (`player_tiles`) is now corrupted.

## Fix Routes

**Route A — shared monotonic VRAM allocator (preferred):**

Compute a single `nextTile` across BOTH iterations in the pipeline. The metasprite
loop starts where the actor loop ended:

```kotlin
fun buildAllSpriteDataLoadStatements(gameIR: GameIR): List<CStatement> {
    var nextTile = 0
    val out = mutableListOf<CStatement>()
    // actors first (existing order — preserves Phase 09 semantics)
    for (actor in gameIR.actors) {
        out.add(emitSetSpriteData(nextTile, actor.tileCount, actor.assetSymbol))
        nextTile += actor.tileCount
    }
    // then metasprites (continue from where actors left off)
    for (ms in gameIR.metasprites) {
        val tileCount = computeMetaspriteTileCount(ms)
        out.add(emitSetSpriteData(nextTile, tileCount, "${ms.id}_tiles"))
        nextTile += tileCount
    }
    return out
}
```

Delete `buildSpriteDataLoadStatements` and `buildMetaspriteTileDataLoadStatements`;
inline both into the new function. Update `buildMainFunction()` call site.

**Route B — VRAM allocator pass:**

Introduce a `VramAllocator` data class as a per-build singleton (held on the
pipeline context). Each emission consults `allocator.reserve(tileCount): Int`.
Heavier refactor, but cleaner if Phase 11 adds banked sprite tile loading where
allocation needs to be per-scene rather than per-game.

## Tests Needed

JVM-tier test in `gbkt-backend-gbdk` that builds a game with one actor +
one metasprite and asserts the generated C contains:

```c
set_sprite_data(0u, <actor_count>u, ...);
set_sprite_data(<actor_count>u, <metasprite_count>u, ...);
```

NOT two `set_sprite_data(0u, ..., ...)` calls.

## Phase Routing

→ **Phase 10.1** (visual parity cluster — fits with the other latent codegen
defects). Or break out as Phase 10.2 if scope dictates a separate slice.

## Discovery

Code review gate post-Plan 10-20 (CR-01).

## Related

- SEED-009 (CR-02: missing `<gbdk/metasprites.h>` in bank1.c)
- SEED-010 (CR-03: non-namespaced descriptor symbol names)
- SEED-011 (WR-05: hiwater reset per moveMetasprite call collides OAM slot 0)
