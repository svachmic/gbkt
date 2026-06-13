# Phase 16: Platformer/Zone Source Cluster — Proposed TRIAGE Rows

**Drafted by:** Plan 16-07 (cluster-platformer-source agent)
**Substrate SHA:** 8cef3dbca7d0868f42cf0d627921b8559d7754e8
**Date:** 2026-06-12
**Seeds covered:** 9 (SEED-017, SEED-021, SEED-022, SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION,
  SEED-PHASE-12-ONE-WAY-TILE, SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS, SEED-PHASE-12-SHARED-TILESET,
  SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY, SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS)

## Proposed TRIAGE Rows

| ID | Title | Type | Disposition | Evidence | Fix-phase routing | Notes |
|----|-------|------|-------------|----------|------------------|-------|
| SEED-017 | Sport-zone tileset pipeline unification | source-only | CONFIRMED-OPEN | evidence/SEED-017/source-inspection.txt | Phase 21 FIX-06 | buildBuiltinTrackTilesetVarDecl still in SportVisitor.kt; SceneVisitor.kt has LEGACY-path SEED-017 comment; INV-8 locks legacy path |
| SEED-021 | Auto-derive pivot_adjust in tilemapCollision | source-only | CONFIRMED-OPEN | evidence/SEED-021/source-inspection.txt | Phase 21 FIX-05 | "Deferred (SEED-021)" marker at PlatformerVisitor.kt:625; visitor still computes pivotAdjust internally; DSL builder has no pivotAdjust |
| SEED-022 | Consolidate duplicate gameUsesTilemapCollision | source-only | CONFIRMED-OPEN | evidence/SEED-022/source-inspection.txt | Phase 21 FIX-06 | Two private implementations: PlatformerVisitor.kt:1663 + GBDKPipeline.kt:2183; "Deferred (SEED-022)" at PlatformerVisitor.kt:1589 |
| SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION | zone(id: String) magic-string → delegate | source-only | CONFIRMED-OPEN | evidence/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION/source-inspection.txt | Phase 21 FIX-06 | Primary GameBuilder.zone(id:String) was FIXED (ZoneDelegate exists, examples migrated); PickupBuilder.zone(id: String, pickupId: String) at gbkt-engine/.../PickupBuilder.kt:229 remains |
| SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS | PlatformerVisitor auto-emission wiring gaps | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS/source-inspection.txt | — | grep -n "cEmit" PlatformerTemplate.kt → zero matches; Phase 13.5 removed all 4 escape-hatch cEmit calls |
| SEED-PHASE-12-ONE-WAY-TILE | oneWayThreshold ONE_WAY tile collision | re-deferred | RE-DEFERRED | evidence/SEED-PHASE-12-ONE-WAY-TILE/source-inspection.txt | v0.2.0 backlog (future platformer-port trigger) | Serena find_symbol "oneWayThreshold" → empty; symbol never implemented; no example exercises ONE_WAY; revival requires triggering port |
| SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS | Per-zone tilemap bank allocation | re-deferred | RE-DEFERRED | evidence/SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS/source-inspection.txt | v0.2.0 backlog (when overflow guard trips) | checkTilemapBankOverflow exists but not tripped; bank 2 = 6120 / 14336 bytes (42.7%); 8216 bytes headroom |
| SEED-PHASE-12-SHARED-TILESET | ConvertZoneTilesetsTask tileset deduplication | re-deferred | RE-DEFERRED | evidence/SEED-PHASE-12-SHARED-TILESET/source-inspection.txt | v0.2.0 backlog (when ROM size pressure triggers) | No dedup code in ConvertZoneTilesetsTask; MultiTilesetAllocationTest.kt still asserts duplication present (canary test); ROM 64KB within 2× threshold |
| SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY | Player spawn position mid-screen (superseded) | source-only | CONFIRMED-OPEN | evidence/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY/evidence.txt | Phase 21 FIX-05 (absorb into spawn-polish) | Superseded by SEED-platformer-template-spawn-polish (same root cause); consolidate into spawn-polish work; spawn still at Y=72 mid-screen |

## Notes

- SEED-021 and SEED-022 should be handled together in Phase 21 FIX-05/FIX-06 — both are in
  PlatformerVisitor.kt and their scope sketches explicitly recommend pairing.
- SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS: Phase 13.5 fully resolved this.
  Zero cEmit() calls remain in PlatformerTemplate.kt. Remove from active fix queue.
- The 3 RE-DEFERRED zone seeds (ONE-WAY-TILE, PER-ZONE-TILEMAP-BANKS, SHARED-TILESET) all have
  explicit revival conditions that are not met at HEAD. None should be in the Phase 21 fix queue.
- SEED-ZONE-MAGIC-STRING: The GameBuilder half was already fixed. Only PickupBuilder.zone(id:String)
  remains. This is a narrow, well-scoped change for Phase 21 FIX-06.
