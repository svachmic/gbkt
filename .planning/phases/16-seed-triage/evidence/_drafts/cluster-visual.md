# Cluster Visual — Draft TRIAGE Rows (Plan 03)

**Status:** PROPOSED — all dispositions are agent-proposed, pending D-08 human visual gate.
No rows in this file are final. Finalization happens after Plan 08 human approval of visual-review-document.md.

**Visual seeds captured:** 10 (3 metasprites + 7 platformer)
**Capture mode:** gbcMode=true for all
**Evidence base:** Screenshots at HEAD under .planning/phases/16-seed-triage/evidence/

---

## Proposed TRIAGE Rows

| ID | Title | Type | Proposed Disposition | Evidence | Fix-phase routing | Notes |
|----|-------|------|---------------------|----------|------------------|-------|
| SEED-004 | Elephant tile rendering (D-V1) | visual | CONFIRMED-OPEN (PROPOSED) | evidence/SEED-004/screenshot.png | Phase 19 FIX-01 | Elephant shape visible, pixel fidelity vs png2asset reference requires human pixel comparison; no prior approved baseline |
| SEED-005 | bgFillCheckerboard diagonal vs checkerboard | visual | VERIFIED-ALREADY-FIXED (PROPOSED) | evidence/SEED-005/screenshot.png | — | HEAD shows proper checkerboard (not diagonal stripes); fix landed in Phase 10.1 or later |
| SEED-013 | GBC sub-palette cycling all-black (D-V3) | visual | VERIFIED-ALREADY-FIXED (PROPOSED) | evidence/SEED-013/screenshot-before-b-press.png, evidence/SEED-013/screenshot-after-b-press.png | — | Elephant renders in correct GBC colors (gray then pink after A-press cycling); NOT all-black; Phase 10.2 fixed this |
| SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT | Platformer title screen doubled text | visual | VERIFIED-ALREADY-FIXED (PROPOSED) | evidence/SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT/screenshot.png | — | Title shows single clean "GBDK-2020 PLATFORMER TEMPLATE"; no doubling; Phase 12.2/12.3 Plans 11-13 fixed ConvertZoneTilesetsTask |
| SEED-platformer-template-spawn-polish | Player spawn position polish | visual | CONFIRMED-OPEN (PROPOSED) | evidence/SEED-platformer-template-spawn-polish/screenshot.png | Phase 21 FIX-05 | grounded=1 at spawn (functional); visual polish of exact spawn position requires human review; supersedes SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY |
| SEED-PHASE-12-PLAYER-METASPRITE-RENDER | Player sprite: duck vs checkerboard | visual | VERIFIED-ALREADY-FIXED (PROPOSED) | evidence/SEED-PHASE-12-PLAYER-METASPRITE-RENDER/screenshot.png | — | Player renders as proper duck sprite (6 OAM entries); NOT dark checkerboard; Phase 12.4 ConvertSpritesTask fix confirmed |
| SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED | Player floating above ground | visual | VERIFIED-ALREADY-FIXED (PROPOSED) | evidence/SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED/screenshot.png | — | grounded=1 at frame 123; player visually on ground tile row; Phase 12.6/12.7 physics codegen fixed |
| SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS | White pixels in grass tilemap | visual | VERIFIED-ALREADY-FIXED (PROPOSED) | evidence/SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS/screenshot.png | — | No white pixel artifacts visible in world1 grass area; Phase 12.9 palette polarity fix confirmed |
| SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ | BG+OBJ palette polarity inverted | visual | VERIFIED-ALREADY-FIXED (PROPOSED) | evidence/SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ/screenshot-head.png | — | HEAD shows correct green colors; Phase 13.7 OBJ polarity fix + Phase 13.8 hardening approved BYTE-IDENTICAL; requires human pixel comparison vs 13.4 before reference |
| SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK | Player feet 1-2px sunk/floating | visual | CONFIRMED-OPEN (PROPOSED) | evidence/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK/screenshot.png | Phase 21 FIX-05 | Sprite bottom at ~screenY=120, ground at ~screenY=128; 8px gap may be intentional collision-box offset or sub-pixel bug; requires human pixel measurement |

---

## Summary of Proposed Dispositions

| Disposition | Count | Seeds |
|-------------|-------|-------|
| VERIFIED-ALREADY-FIXED (proposed) | 6 | SEED-005, SEED-013, SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT, SEED-PHASE-12-PLAYER-METASPRITE-RENDER, SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED, SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS |
| CONFIRMED-OPEN (proposed) | 3 | SEED-004, SEED-platformer-template-spawn-polish, SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK |
| Ambiguous (proposed VERIFIED but requires extra human care) | 1 | SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ |

> All dispositions PROPOSED. Final verdicts set only after Plan 08 human gate.
