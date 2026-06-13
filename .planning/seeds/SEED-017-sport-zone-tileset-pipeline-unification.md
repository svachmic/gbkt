# SEED-017 — Unify sport and zone tileset pipelines

> **Triage:** CONFIRMED-OPEN — [TRIAGE.md#SEED-017](.planning/phases/16-seed-triage/TRIAGE.md#SEED-017) · 2026-06-12

**Origin phase:** 11.2 (tileset-pipeline-set-bkg-data-emission)
**Status:** Deferred — captured for a future phase with bandwidth
**Routing:** Open; not yet bound to a target phase
**Blast radius:** Moderate (touches `gbkt-genre-sport` + the new `ConvertZoneTilesetsTask` from 11.2)

## Context

After Phase 11.2 ships, two parallel patterns for generating tile pixel data coexist in the gbkt codebase:

| Path | Author | Where |
|------|--------|-------|
| **NEW path** — Gradle `ConvertZoneTilesetsTask` invokes GBDK `png2asset` against user-supplied PNGs; emits a per-zone `_zone_<id>_tileset.h` header `#include`d by the consuming bank file | Phase 11.2 | `gbkt-gradle-plugin/.../ConvertZoneTilesetsTask.kt` + `gbkt-backend-gbdk/.../SceneVisitor.kt` zone-load block |
| **LEGACY path** — Kotlin codegen authors a hand-coded `const UINT8 _racing_<id>_tileset[N] = { 0x00, 0xff, ... }` inline byte literal | Phase 07.4 (sport-genre codegen) | `gbkt-genre-sport/.../SportVisitor.kt` `buildBuiltinTrackTilesetVarDecl()` |

The two patterns diverged for honest reasons:
- The sport genre ships hand-coded racing tracks because the framework generates them procedurally (no user-supplied PNG asset). The Kotlin codegen authors the bytes via `buildBuiltinTrackTilesetVarDecl()`.
- Zones (banks game, future zone-using games) ship user-supplied PNGs because games carry their own art. The Gradle task processes those PNGs into 2bpp bytes via `png2asset`.

Phase 11.2 deliberately did NOT retrofit sport onto the new pipeline (per SPEC out-of-scope + CONTEXT D-D2). It preserved the legacy path with a clear KDoc marker on `buildBuiltinTrackTilesetVarDecl()` and a sentinel test (`INV-8`) locking that path's emission shape unchanged.

## Why this matters

Two patterns in the same codebase invite drift. A future maintainer might:
- Add a third pattern for a third type of tile data (compressed assets? streamed tilesets? GBC palette banks?).
- Touch one path while assuming both behave identically.
- Refactor SceneVisitor without realising SportVisitor mirrors part of its emission shape.

A dedicated unification phase consolidates the two paths cleanly — either by extending the Gradle task to handle procedurally-generated tile data (so sport authors a virtual PNG-equivalent that flows through `ConvertZoneTilesetsTask`), or by extracting a shared `TilesetEmission` interface that both `SceneVisitor` and `SportVisitor` consume.

## Potential unification options (for the future phase to evaluate)

1. **Shared `Png2AssetInvocation` base class.** Already mentioned in CONTEXT 11.2 D-A1 as deferred. Extract a parent for `ConvertSpritesTask` + `ConvertZoneTilesetsTask` first; sport stays separate. Smallest unification.

2. **Procedural-input mode in `ConvertZoneTilesetsTask`.** Add a code path where the task accepts a Kotlin-side byte producer (a `() -> ByteArray`) instead of a PNG file. Sport's `buildBuiltinTrackTilesetVarDecl()` becomes a producer; the task emits the same `_<id>_tileset.h` header shape for both inputs.

3. **Shared `TilesetEmission` interface in `gbkt-backend-api`.** Define a contract that both visitors satisfy. Codegen emits `set_bkg_data(0, _<id>_tileset_count, _<id>_tileset)` uniformly; the data source (PNG via task vs inline bytes) is a producer-side concern.

4. **Move sport to `ConvertZoneTilesetsTask` entirely.** Drop `buildBuiltinTrackTilesetVarDecl()`. Ship racing tracks as bundled PNG assets inside `gbkt-genre-sport/src/main/resources`. Sport genre joins the asset-pipeline world.

The future phase's discuss-phase round picks among these (or surfaces a fifth option) based on what's true at the time.

## Hard requirements for any unification

- **INV-8 stays GREEN.** Whatever unification approach is picked, the sport racing emission contract (current `_racing_<id>_tileset[N]` const-array shape) must remain identical at the generated-C level until the unification phase itself updates INV-8.
- **No regression on banks / dungeon / explorer / racer buildRom.** The 4-game smoke set inherited from Phase 11.1 D-08 stays green through any unification.
- **DSL surface unchanged.** Game authors do not write any new DSL because of the unification.

## Discovery hooks (so a future maintainer finds this seed)

- `gbkt-genre-sport/.../SportVisitor.kt` `buildBuiltinTrackTilesetVarDecl()` KDoc — references SEED-017 by ID.
- `gbkt-backend-gbdk/.../SceneVisitor.kt` zone-load block KDoc — references SEED-017 by ID.
- `.planning/codebase/CONVENTIONS.md` §"Tile pixel data emission: two paths, when to use which" — references SEED-017 as the open-unification surface.
- This file picked up by `/gsd:audit-open` and milestone summaries.

## Related artifacts

- `.planning/phases/11.2-tileset-pipeline-set-bkg-data-emission/11.2-SPEC.md` — phase that deferred this.
- `.planning/phases/11.2-tileset-pipeline-set-bkg-data-emission/11.2-CONTEXT.md` §D-D1, D-D2, D-D3, D-D4 — the discipline lockdown that made deferral safe.
- `.planning/seeds/SEED-014-banks-bkg-tiles-load-banked-gating.md` — earlier seed in the same lineage.
