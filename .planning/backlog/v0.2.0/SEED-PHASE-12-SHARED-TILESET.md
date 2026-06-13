# SEED: Phase 12 — `ConvertZoneTilesetsTask` shared-tileset deduplication

> **Triage:** RE-DEFERRED — [TRIAGE.md#SEED-PHASE-12-SHARED-TILESET](.planning/phases/16-seed-triage/TRIAGE.md#SEED-PHASE-12-SHARED-TILESET) · 2026-06-12

**Created:** 2026-05-25 (Phase 12 close — Plan 12-27 administrative phase-close)
**Origin phase:** 12 (port-platformer-template-gbdk-example-to-gbkt)
**Source:** Phase 12 RESEARCH.md §"D-15 Recommendations" (gap identified during research; RESEARCH-surfaced bonus seed beyond CONTEXT)
**Status:** Deferred — option (a) "accept duplication" chosen for Phase 12 budget. Phase 12 ships with the duplication and within the 2× ROM-size signal threshold.
**Routing:** Phase 13 — explicitly listed in RESEARCH as a Phase 13 candidate; pulls when a port surfaces ROM-size pressure OR when the framework wants to push under 1.5× the upstream reference for parity.
**Blast radius:** Medium-high (~5 files: `ConvertZoneTilesetsTask` + `GBDKPipelineV2` allocator + `ZoneBuilder` + `ZoneIR` + `game_metadata.json` schema).

## Context

`ConvertZoneTilesetsTask` (in `gbkt-gradle-plugin`) processes the `zoneTilesets` block
from `game_metadata.json` PER ZONE. When 2+ zones reference the same source PNG (e.g.
`res/graphics/world1-tileset.png` referenced by BOTH `world1Area1Zone` AND
`world1Area2Zone`), the task invokes `png2asset` TWICE, producing two byte-identical
`_zone_<id>_tileset.c` files:

- `_zone_world1Area1Zone_tileset.c` — tile data from world1-tileset.png
- `_zone_world1Area2Zone_tileset.c` — also tile data from world1-tileset.png

The tilemap data (which IS per-zone — different level layouts using the same shared
tileset) is correctly distinct. The duplication is purely on the **tileset** side.

The Phase 12 substrate exercises this exact pattern: `world1Area1Zone` and
`world1Area2Zone` share `world1-tileset.png`, and the gbkt ROM emits two copies of the
world1-tileset payload (~1-3KB extra ROM per shared-tileset pair). The reference
upstream uses `png2asset -source_tileset` to emit the shared tileset ONCE and
reference it from both area maps.

This is documented in `oracle-comparison.md` Signal 1 as a known driver of the 2×
ROM-size gap (ratio 2.000 exact; the tileset duplication contributes ~3KB of the
~32KB delta, with MBC1 4-bank power-of-two rounding contributing the remainder).

## Why Phase 12 chose option (a)

Per RESEARCH §"D-15 Recommendations":

- **Option (a) "Accept duplication":** Simpler, correct, ~3KB ROM overhead per shared
  pair, well within the 2× ROM-size signal threshold. Chosen for Phase 12 given the
  multi-bug integration scope (28 plans, 7 waves) and the 4 already-budgeted named
  codegen surfaces (D-12, D-13, D-14, D-15).
- **Option (b) "Extend ConvertZoneTilesetsTask for shared-tileset mode":** Adds a
  `sharedTileset` concept to ZoneBuilder/ZoneIR/game_metadata.json. Bank allocator
  must understand aliases. Significant scope — explicitly scoped OUT of Phase 12 and
  routed to Phase 13 via this seed.

## What's Deferred

Implement shared-tileset deduplication, with one of two approaches:

### Approach 1 — Explicit `sharedTileset` concept in the DSL

```kotlin
val world1Tileset = sharedTileset(asset("res/graphics/world1-tileset.png"))
val world1Area1Zone by zone {
    tileset(world1Tileset)      // ZoneBuilder.tileset() gains SharedTilesetRef overload
    tiles(loadCsv("...world1-area1.csv"))
}
val world1Area2Zone by zone {
    tileset(world1Tileset)      // Same SharedTilesetRef → same emitted symbol
    tiles(loadCsv("...world1-area2.csv"))
}
```

Pros: explicit and discoverable; matches the upstream `-source_tileset` mental model.
Cons: extra DSL surface; existing per-zone `tileset(asset(...))` callers don't benefit
automatically.

### Approach 2 — Automatic deduplication via content hash

`ConvertZoneTilesetsTask` content-hashes the source PNG (or, more aggressively, the
emitted tile-byte payload after png2asset processing). Zones with identical hashes
share a single emitted `_shared_tileset_<hash>.c` file; per-zone references
(`_zone_<id>_tileset_data` pointer / `_zone_<id>_tileset_count`) become aliases /
forward to the shared symbol.

Pros: backward-compatible; existing DSL works unchanged; opt-out via a per-zone
`disableTilesetSharing()` flag if needed.
Cons: more complex pipeline (hash table during task execution); error messages must
explain the alias relationship clearly; the bank allocator must understand that the
shared symbol occupies one bank slot (not N).

**Recommendation: Approach 2 (automatic dedup).** Matches the upstream mental model
without DSL changes, keeps Phase 12 source unchanged, and produces predictable ROM-size
wins (≥1-3KB per shared pair).

## Codegen / pipeline implications (Approach 2)

- **`ConvertZoneTilesetsTask`** — group zones by content hash of source PNG; emit one
  png2asset invocation per unique hash. Map per-zone symbol names to the shared symbol
  via header-emit aliases (`#define _zone_world1Area1Zone_tileset_data _shared_tileset_<hash>_data`)
  OR via direct symbol-rewrite in the dependent tilemap C files.
- **`ZoneIR`** — gains an optional `sharedTilesetRef: SharedTilesetRef?` field. When
  set, the visitor knows to emit the shared-symbol reference instead of the per-zone
  symbol. Approach 2 derives this automatically from the content hash.
- **`GBDKPipelineV2.allocateZoneBanks`** — bin-packing logic gains awareness that
  multiple zones can SHARE a single payload. The FFD allocator stops double-counting
  shared payloads in the per-bank size budget. This is the most error-prone touchpoint
  (mis-counting causes either under-utilization OR overflow), so JVM-tier tests must
  lock the allocator's shared-payload accounting.
- **`game_metadata.json` schema** — `zoneTilesets[].sourcePath` becomes the canonical
  dedup key. Optional `sharedTilesetGroupId` field can be pre-computed by the
  GenerateMetadataTask to short-circuit the hash on the Gradle side.
- **`buildSetupCurrentLevelFunction`** — no change. The setup function already
  references `_zone_<id>_tileset_data` by name; the alias / shared-symbol redirect is
  invisible at the call site.

## Blast-radius assessment

| File | Change |
|------|--------|
| `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt` | Group by content hash; emit one png2asset per unique hash; emit aliases / shared symbols |
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | `allocateZoneBanks` accounts for shared payloads (one bank slot per shared symbol, not per referencing zone) |
| `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/zone/ZoneBuilder.kt` | (Approach 1 only) — `tileset(SharedTilesetRef)` overload + `sharedTileset(asset)` top-level builder |
| `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/zone/ZoneIR.kt` | Optional `sharedTilesetRef` field (or per-zone hash key for Approach 2) |
| `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/metadata/GenerateMetadataTask.kt` | Emit `sharedTilesetGroupId` per zone (Approach 2 optimization) |

~5 files; medium-high scope. Approach 2 is implementation-heavy in the allocator
(BankingAnalysisPass must also be aware) but DSL-light. Approach 1 is DSL-heavy but
allocator-light.

## Routing Recommendation

**Phase 13.** Explicitly listed in `12-RESEARCH.md` §"D-15 Recommendations" as the
Phase 13 candidate. Triggers:

1. A new port (especially RPG or large platformer) where ROM size approaches the
   2-bank / 4-bank / 8-bank ROM threshold and tileset duplication pushes it OVER the
   next power-of-two boundary.
2. A Phase 13 framework-primitive phase that aggregates the Phase 11/12 deferred-but-known
   pipeline gaps (this seed + the existing oneWayThreshold seed + the existing
   per-zone-tilemap-banks seed could all close together).
3. Polish-phase work driven by the oracle-comparison.md Signal 1 ratio dropping
   below 1.5× as the target (currently 2.000 boundary GREEN).

## JVM-tier marker (already exists)

`Plan 12-15 MultiTilesetAllocationTest` lives at:

```
gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt
```

It builds a 3-zone game (`world1Area1` + `world1Area2` sharing `world1-tileset`;
`world2Area1` using `world2-tileset`) and currently **ASSERTS the duplication exists**
— specifically asserts both `_zone_world1Area1Zone_tileset_data` and
`_zone_world1Area2Zone_tileset_data` are emitted as distinct symbols, both carrying
the same tile payload.

**When the dedup fix lands, this test will FAIL** in a useful way — the failure is
the signal that the dedup landed correctly. The fix-phase plan should:

1. Replace the duplication assertion with a shared-symbol assertion (single
   `_shared_tileset_<hash>_data` symbol; both per-zone references are aliases or
   forward to it).
2. Add a bank-allocator regression test confirming `allocateZoneBanks` counts the
   shared payload once, not twice.
3. Update the test's docstring to point to this seed (closing the loop).

## Revival Conditions

1. A new port phase opens whose reference uses `-source_tileset` (i.e. the upstream
   shares a tileset across multiple area maps) AND the gbkt-side ROM-size delta
   becomes load-bearing (>1.5× ratio or pushes over an ROM-bank power-of-two boundary).
2. A Phase 13 framework-primitives phase pulls this seed into scope.
3. `MultiTilesetAllocationTest` fails because someone partially landed a dedup change
   without updating the test (the test's existence is the canary).

## Related artifacts

- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-RESEARCH.md`
  §"D-15 Recommendations" (the gap analysis + option-(a)-vs-(b) decision)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-RESEARCH.md`
  §"GBDKPipelineV2 — D-15 finding" (deeper pipeline trace)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/oracle-comparison.md`
  Signal 1 — documents the ROM-size impact (within 2× threshold; one of the drivers
  of the boundary verdict)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/bank-layout-signal.md`
  — CODE_2 bank contents show the duplicated tileset payloads explicitly
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt`
  — the JVM-tier canary (currently asserts duplication; flips polarity when dedup lands)
- Related: [[SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS]] — sibling pipeline-gap seed; if a
  future phase fixes tileset dedup AND tilemap-per-zone-banks together, group them.
- Related: [[SEED-PHASE-12-ONE-WAY-TILE]] — orthogonal but PlatformerVisitor-adjacent.
