---
phase: 16-seed-triage
plan: "04"
subsystem: triage
tags: [metasprites, emission, triage, evidence, generated-c]
dependency_graph:
  requires: [16-01, 16-02]
  provides: [evidence/SEED-006..011, evidence/SEED-PHASE-13-SPRITE-OUTLINE, evidence/TODO-metasprites-baseline, evidence/_drafts/cluster-metasprites-emission.md]
  affects: [TRIAGE.md (Plan 09 will merge these rows)]
tech_stack:
  added: []
  patterns: [generated-C scoped function-body inspection, substrate read-only evidence workflow]
key_files:
  created:
    - .planning/phases/16-seed-triage/evidence/SEED-006/main-c-excerpt.txt
    - .planning/phases/16-seed-triage/evidence/SEED-007/main-c-excerpt.txt
    - .planning/phases/16-seed-triage/evidence/SEED-008/main-c-excerpt.txt
    - .planning/phases/16-seed-triage/evidence/SEED-009/main-c-excerpt.txt
    - .planning/phases/16-seed-triage/evidence/SEED-010/main-c-excerpt.txt
    - .planning/phases/16-seed-triage/evidence/SEED-011/main-c-excerpt.txt
    - .planning/phases/16-seed-triage/evidence/SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX/source-inspection.txt
    - .planning/phases/16-seed-triage/evidence/TODO-metasprites-baseline/test-output.txt
    - .planning/phases/16-seed-triage/evidence/_drafts/cluster-metasprites-emission.md
  modified: []
decisions:
  - "All 8 metasprite emission/structural cluster entries are VERIFIED-ALREADY-FIXED at substrate SHA 8cef3dbc — significant deviation from pre-analysis CONFIRMED-OPEN expectations"
  - "SEED-008/010/011 (structural latents) verified via metasprites-stress example which has actor + two metasprites — the single-metasprites example cannot exercise these paths"
  - "TODO-metasprites-baseline: test GREEN because baseline was regenerated post Phase-13.6 visual approval (D-15 satisfied); todo is moot"
metrics:
  duration: "7 min"
  completed_date: "2026-06-12"
  tasks: 2
  files: 9
---

# Phase 16 Plan 04: Metasprites Emission Cluster Triage Summary

All 8 entries in the metasprites emission/structural cluster triaged against the Plan 02 substrate at SHA `8cef3dbca7d0868f42cf0d627921b8559d7754e8`. Evidence-backed proposed dispositions produced for SEED-006..011, SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX, and TODO-metasprites-baseline — all 8 entries proposed VERIFIED-ALREADY-FIXED based on scoped generated-C inspection and source inspection.

## Tasks Completed

### Task 1: Generated-C inspection for SEED-006..011

Read-only inspection of substrate generated C (no `gradlew clean` or `buildRom`). Scoped to function-body level per T-16-09 (never file-level contains()).

| Seed | Check | File Inspected | Verdict |
|------|-------|----------------|---------|
| SEED-006 | `_elephant_subPalette = subpal;` in play_frame() | metasprites/main.c:283 | VERIFIED-ALREADY-FIXED |
| SEED-007 | `actorPaletteAutoSlot++` vs old `else 0` | GameBuilder.kt:716 | VERIFIED-ALREADY-FIXED |
| SEED-008 | Shared VRAM allocator: set_sprite_data(0,2), (2,N), (2+N,M) | metasprites-stress/main.c:241-243 | VERIFIED-ALREADY-FIXED |
| SEED-009 | `<gbdk/metasprites.h>` in bank1.c header | metasprites-stress/bank1.c:7 | VERIFIED-ALREADY-FIXED |
| SEED-010 | ID-namespaced symbols: elephant_metasprites, tiger_metasprites | metasprites-stress/bank1.c + elephant.c | VERIFIED-ALREADY-FIXED |
| SEED-011 | hiwater=0 once at frame start; hide_sprites_range once at end | metasprites-stress/bank1.c:14-77 | VERIFIED-ALREADY-FIXED |

**Key finding for latent seeds (SEED-008, SEED-010, SEED-011):** The metasprites-stress example (elephant + tiger + player) exercises all three latent defect paths. Evidence was scoped to this example, not the simpler metasprites example. The metasprites-stress bank1.c is the definitive artifact.

### Task 2: Sprite-outline tRNS seed + stale-baseline todo + cluster draft

**SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX:**
Source inspection of `ConvertSpritesTask.kt` confirmed Phase 13.6 tRNS auto-route is fully implemented: `getTransparentIndexShared(pngFile)` reads the tRNS chunk; `if (transparentIdx > 0)` branches on non-zero index; `prePermuteIndexedPng(...)` pre-permutes the palette before png2asset invocation. The generated elephant.c header confirms `gbkt_permuted_elephant.png` as input, proving the temp-file permute path runs at build time. Proposed disposition: VERIFIED-ALREADY-FIXED. Visual closure oracle (elephant outline visible in color cycle screenshots) belongs to Phase 20 D-08 batch gate.

**TODO-metasprites-baseline:**
Substrate test report shows `MetaspritesGeneratedSpriteByteIdentityTest` GREEN at HEAD. Baseline file `elephant.c.baseline` modified Jun 10 2026 (after Phase 12.8). Baseline content includes `-keep_palette_order` matching current generated output — confirming the baseline was regenerated after Phase 13.6 visual approval (D-15 satisfied). The todo's "stale baseline" condition no longer exists. Proposed disposition: VERIFIED-ALREADY-FIXED.

**cluster-metasprites-emission.md:** 8-row draft produced, one row per entry, all VERIFIED-ALREADY-FIXED.

## Deviations from Plan

### Key Pre-Analysis Correction — All Seeds VERIFIED-ALREADY-FIXED

**Found during:** Both tasks
**Pre-analysis expected:** CONFIRMED-OPEN for SEED-006, 007, 008, 010, 011 (and "Depends on banking config" for SEED-009)
**Actual evidence:** All 8 entries VERIFIED-ALREADY-FIXED at substrate SHA 8cef3dbc

**Root cause:** Seeds were planted during Phase 10 closeout (2026-05-18). Between Phase 10 and Phase 16 triage, Phases 10.1, 12.x, 13.3, 13.6, and 13.8 addressed these issues:
- SEED-006: MetaspriteVisitor assignment added
- SEED-007: GameBuilder counter fix (matching SceneBuilder pattern)
- SEED-008/009/010/011: GBDKPipelineV2 and bank-file-builder updates (confirmed by metasprites-stress substrate)
- SEED-PHASE-13-SPRITE-OUTLINE: Phase 13.6 ConvertSpritesTask tRNS auto-route
- TODO-metasprites-baseline: Baseline regenerated post Phase-13.6 visual approval

This is the expected behavior of seed-triage: pre-analysis assumptions are hypothesis-level; actual dispositions are evidence-driven. The significant finding is that the metasprites emission cluster was effectively cleaned up during the intermediate phases.

**Impact on downstream phases:**
- Phase 19 (FIX-01/FIX-02 metasprites fixes) no longer needs to implement any of these 6 emission fixes — they were pre-empted.
- Phase 19 scope should be re-evaluated against TRIAGE.md after Plan 09 finalizes all rows.
- The only remaining metasprites work is visual (SEED-004, SEED-005, SEED-013 — covered by Plans 03 visual cluster) and the PNG outline visual oracle (SEED-PHASE-13 → Phase 20 D-08 gate).

### SEED-007 Evidence Source

Plan expected generated-C inspection (actor palette slot arg). The metasprites example uses SceneBuilder.palette() not actor-level palette injection, so the generated C does not exercise this path. Evidence was sourced from GameBuilder.kt source inspection instead — same evidence quality, different artifact. Noted in evidence file.

## Known Stubs

None — this plan produces only evidence documentation files. No code artifacts were created.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes. All work is read-only inspection of substrate artifacts.

## Self-Check: PASSED

All 9 created files verified present on disk.
Task commits verified in git log:
- `8ac5c558` — Task 1 (SEED-006..011 emission seeds evidence)
- `7ec99a32` — Task 2 (sprite-outline + baseline todo + cluster draft)
