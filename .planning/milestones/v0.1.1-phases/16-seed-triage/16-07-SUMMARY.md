---
plan: 16-07
phase: 16-seed-triage
status: complete
date: 2026-06-12
substrate_sha: 8cef3dbca7d0868f42cf0d627921b8559d7754e8
---

# Plan 16-07 Summary — Platformer/Zone Source Cluster Triage

## What was done

Triaged 9 platformer/zone source seeds via Serena source inspection (find_symbol + grep) and
substrate generated-C artifact reads. No code was changed; no gradle was invoked; substrate
artifacts from Plan 16-01 were read-only. All evidence saved under
`.planning/phases/16-seed-triage/evidence/` per D-09.

## Dispositions

| ID | Disposition | Routing |
|----|-------------|---------|
| SEED-017 | CONFIRMED-OPEN | Phase 21 FIX-06 |
| SEED-021 | CONFIRMED-OPEN | Phase 21 FIX-05 |
| SEED-022 | CONFIRMED-OPEN | Phase 21 FIX-06 |
| SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION | CONFIRMED-OPEN | Phase 21 FIX-06 |
| SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS | VERIFIED-ALREADY-FIXED | — |
| SEED-PHASE-12-ONE-WAY-TILE | RE-DEFERRED | v0.2.0 backlog |
| SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS | RE-DEFERRED | v0.2.0 backlog |
| SEED-PHASE-12-SHARED-TILESET | RE-DEFERRED | v0.2.0 backlog |
| SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY | CONFIRMED-OPEN | Phase 21 FIX-05 (absorb into spawn-polish) |

## Key findings

**VERIFIED-ALREADY-FIXED (1):**
- SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS: Phase 13.5 removed ALL cEmit() escape
  hatches from PlatformerTemplate.kt. grep -n "cEmit" produces zero matches. The 4 wiring gaps
  (input→velocity, camera update call site, camera-offset render, walkFrameIdx increment) are
  all auto-emitted by PlatformerVisitor.

**CONFIRMED-OPEN (5):**
- SEED-017: buildBuiltinTrackTilesetVarDecl still lives in SportVisitor.kt (Serena
  body_location 509–562); SceneVisitor.kt has "LEGACY path (SEED-017)" comment; INV-8 test
  locks the legacy emission shape. Two tileset-generation paths coexist.
- SEED-021: "Deferred (SEED-021)" marker at PlatformerVisitor.kt:625; pivotAdjust is still
  computed in the visitor via metasprite lookup + fallback constants; tilemapCollision { } DSL
  builder has no pivotAdjust property.
- SEED-022: Two private `gameUsesTilemapCollision` implementations exist: PlatformerVisitor.kt:1663
  + GBDKPipeline.kt:2183; "Deferred (SEED-022)" at PlatformerVisitor.kt:1589. Lockstep doc only,
  no enforcement.
- SEED-ZONE-MAGIC-STRING: Primary GameBuilder.zone(id: String) was FIXED (ZoneDelegate class exists
  in WorldBuilders.kt:975, delegate form is the only top-level zone factory, examples use `by zone`).
  PickupBuilder.zone(id: String, pickupId: String) at gbkt-engine/.../PickupBuilder.kt:229 remains
  as the only outstanding magic-string violation.
- SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY: Spawn still at Y=72 (mid-screen). Superseded by
  SEED-platformer-template-spawn-polish; absorb into Phase 21 FIX-05 spawn-polish work.

**RE-DEFERRED (3) — all Serena-backed rationale:**
- SEED-PHASE-12-ONE-WAY-TILE: Serena find_symbol "oneWayThreshold" → empty. Symbol never
  implemented. No example exercises ONE_WAY tiles. Revival requires a triggering port.
- SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS: checkTilemapBankOverflow guard exists (BankingAnalysisPass.kt
  line 285–330) but has not tripped. Bank 2 = 6120 / 14336 bytes (42.7%), 8216 bytes headroom.
  Confirmed by substrate .noi: l__CODE_2 = 0x17E8 = 6120.
- SEED-PHASE-12-SHARED-TILESET: No dedup in ConvertZoneTilesetsTask (grep: no contentHash/
  SharedTilesetRef). MultiTilesetAllocationTest.kt asserts duplication EXISTS (the documented
  canary). ROM = 64 KB, within 2× threshold. Option (a) still in effect.

## Evidence artifacts

- evidence/SEED-017/source-inspection.txt
- evidence/SEED-021/source-inspection.txt
- evidence/SEED-022/source-inspection.txt
- evidence/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION/source-inspection.txt
- evidence/SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS/source-inspection.txt
- evidence/SEED-PHASE-12-ONE-WAY-TILE/source-inspection.txt
- evidence/SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS/source-inspection.txt
- evidence/SEED-PHASE-12-SHARED-TILESET/source-inspection.txt
- evidence/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY/evidence.txt
- evidence/_drafts/cluster-platformer-source.md (9-row TRIAGE fragment)

## Deviations

None. All tasks executed per plan. No code changed. No gradle invoked. Serena used for all
code exploration. Substrate artifacts read-only per D-16.
