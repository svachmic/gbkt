# Cluster Visual — TRIAGE Rows (Plan 08 — Verdicts Locked)

**Status:** LOCKED — all dispositions finalized via D-08 binding human visual gate (2026-06-12).
Verdicts were proposed by the cluster agent and locked by the human reviewer in visual-review-document.md.
The SEED-004 verdict was OVERRIDDEN by the user from CONFIRMED-OPEN to VERIFIED-ALREADY-FIXED.
These rows are authoritative inputs for Plan 09 merge into TRIAGE.md.

**Visual seeds locked:** 10 (3 metasprites + 7 platformer)
**Capture mode:** gbcMode=true for all
**Evidence base:** Screenshots at HEAD under .planning/phases/16-seed-triage/evidence/
**D-08 gate:** PASSED 2026-06-12 by Michal Svacha

---

## Locked TRIAGE Rows

| ID | Title | Type | Locked Disposition | Evidence | Fix-phase routing | Notes |
|----|-------|------|---------------------|----------|------------------|-------|
| SEED-004 | Elephant tile rendering (D-V1) | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-004/screenshot.png | — | LOCKED via visual review 2026-06-12. USER OVERRIDE: agent proposed CONFIRMED-OPEN; human reviewer confirmed elephant renders correctly at HEAD. |
| SEED-005 | bgFillCheckerboard diagonal vs checkerboard | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-005/screenshot.png | — | LOCKED via visual review 2026-06-12. HEAD shows proper checkerboard (not diagonal stripes); fix landed in Phase 10.1 or later. |
| SEED-013 | GBC sub-palette cycling all-black (D-V3) | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-013/screenshot-before-b-press.png, evidence/SEED-013/screenshot-after-b-press.png | — | LOCKED via visual review 2026-06-12. Elephant renders in correct GBC colors (gray then pink after A-press cycling); NOT all-black; Phase 10.2 fixed this. |
| SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT | Platformer title screen doubled text | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT/screenshot.png | — | LOCKED via visual review 2026-06-12. Title shows single clean "GBDK-2020 PLATFORMER TEMPLATE"; no doubling; Phase 12.2/12.3 Plans 11-13 fixed ConvertZoneTilesetsTask. |
| SEED-platformer-template-spawn-polish | Player spawn position polish | visual | CONFIRMED-OPEN | evidence/SEED-platformer-template-spawn-polish/screenshot.png | Phase 21 FIX-05 | LOCKED via visual review 2026-06-12. grounded=1 at spawn (functional); visual polish of exact spawn position remains open. |
| SEED-PHASE-12-PLAYER-METASPRITE-RENDER | Player sprite: duck vs checkerboard | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-PLAYER-METASPRITE-RENDER/screenshot.png | — | LOCKED via visual review 2026-06-12. Player renders as proper duck sprite (6 OAM entries); NOT dark checkerboard; Phase 12.4 ConvertSpritesTask fix confirmed. |
| SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED | Player floating above ground | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED/screenshot.png | — | LOCKED via visual review 2026-06-12. grounded=1 at frame 123; player visually on ground tile row; Phase 12.6/12.7 physics codegen fixed. |
| SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS | White pixels in grass tilemap | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS/screenshot.png | — | LOCKED via visual review 2026-06-12. No white pixel artifacts visible in world1 grass area; Phase 12.9 palette polarity fix confirmed. |
| SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ | BG+OBJ palette polarity inverted | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ/screenshot-head.png | — | LOCKED via visual review 2026-06-12. HEAD shows correct green colors; Phase 13.7 OBJ polarity fix + Phase 13.8 hardening approved BYTE-IDENTICAL. |
| SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK | Player feet 1-2px sunk/floating | visual | CONFIRMED-OPEN | evidence/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK/screenshot.png | Phase 21 FIX-05 | LOCKED via visual review 2026-06-12. Sprite bottom at ~screenY=120, ground at ~screenY=128; 8px gap may be intentional collision-box offset or sub-pixel bug; remains open. |

---

## Summary of Locked Dispositions

| Disposition | Count | Seeds |
|-------------|-------|-------|
| VERIFIED-ALREADY-FIXED | 8 | SEED-004 (user override), SEED-005, SEED-013, SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT, SEED-PHASE-12-PLAYER-METASPRITE-RENDER, SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED, SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS, SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ |
| CONFIRMED-OPEN | 2 | SEED-platformer-template-spawn-polish, SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK |

> All 10 dispositions LOCKED. D-08 gate passed 2026-06-12 by Michal Svacha.
> Plan 09 may now merge these rows into TRIAGE.md without further human visual gating.
> SEED-004 override note: agent proposed CONFIRMED-OPEN; human reviewer locked VERIFIED-ALREADY-FIXED.
