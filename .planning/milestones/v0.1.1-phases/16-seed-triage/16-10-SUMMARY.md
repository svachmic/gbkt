---
phase: 16-seed-triage
plan: 10
subsystem: planning
tags: [triage, seeds, backlog, requirements, roadmap, d-03, d-04, d-11]
dependency_graph:
  requires: ["16-09"]
  provides: ["seeds-live-queue", "v0.2.0-backlog", "d-11-reconciliation"]
  affects: [".planning/seeds/", ".planning/seeds/archive/", ".planning/backlog/v0.2.0/", ".planning/REQUIREMENTS.md", ".planning/ROADMAP.md"]
tech_stack:
  added: []
  patterns: ["git mv for history preservation", "D-03 archive", "D-04 backlog", "D-11 requirements reconciliation"]
key_files:
  created: []
  modified:
    - ".planning/seeds/archive/ (24 VERIFIED-ALREADY-FIXED seeds moved here)"
    - ".planning/backlog/v0.2.0/ (10 RE-DEFERRED seeds moved here)"
    - ".planning/REQUIREMENTS.md (Future Requirements entries + FIX-01..06 D-11 annotations + TRIAGE traceability updated)"
    - ".planning/ROADMAP.md (Phase 19/20/21 D-11 notes + Plan 16-10 marked complete)"
decisions:
  - "Drive all moves strictly from finalized TRIAGE.md disposition table — no re-judgment"
  - "RE-DEFERRED count discrepancy (table=10, summary=11): table rows are authoritative; summary line is stale — moved 10 seeds to backlog"
  - "Add EXAMPLES-01/PLAT-EXT-01/02/03 as new Future Requirements entries for newly RE-DEFERRED seeds without existing IDs"
  - "D-11 reconciliation: annotate FIX-01..06 with triage findings; no requirement IDs deleted"
metrics:
  duration: "4 min"
  completed: "2026-06-12"
  tasks: 2
  files: 36
---

# Phase 16 Plan 10: Move Seeds + D-11 Reconciliation Summary

**One-liner**: Relocated 34 seed files per TRIAGE.md verdict (24 archive, 10 backlog), leaving 10 confirmed-open seeds as the live work queue, and reconciled FIX-01..06 / Phase 19-21 scope against triage findings.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Move seeds to archive/backlog per TRIAGE.md (git mv) | 5e7bd6e2 | 34 seed file renames |
| 2 | Backlog index entries + D-11 REQUIREMENTS.md/ROADMAP.md reconciliation | 83e8caa8 | REQUIREMENTS.md, ROADMAP.md |

## What Was Done

### Task 1: Seed File Moves (D-03/D-04)

All 34 seed moves were driven strictly by the finalized TRIAGE.md disposition table:

**Moved to `.planning/seeds/archive/` (24 VERIFIED-ALREADY-FIXED):**
- Numbered: SEED-002, 004, 005, 006, 007, 008, 009, 010, 011, 012, 013, 014, 015, 016, 026
- Phase-named: SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT, SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS, SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS, SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED, SEED-PHASE-12-PLAYER-METASPRITE-RENDER, SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT, SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT, SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ, SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX

**Moved to `.planning/backlog/v0.2.0/` (10 RE-DEFERRED, including 6 fast-path):**
- Fast-path (D-12): SEED-001, SEED-018, SEED-019, SEED-024, SEED-PHASE-X-CPAREN-EXPR-IN-C-AST, SEED-RAW-C-CODEGEN-AST-MIGRATION
- Newly RE-DEFERRED: SEED-003, SEED-PHASE-12-ONE-WAY-TILE, SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS, SEED-PHASE-12-SHARED-TILESET

**Remaining in `.planning/seeds/` (10 CONFIRMED-OPEN — live work queue for Phases 19-21):**
- SEED-017, SEED-020, SEED-021, SEED-022, SEED-023, SEED-025
- SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY, SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK
- SEED-platformer-template-spawn-polish, SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION

**Notes:**
- TRIAGE.md progress summary says "RE-DEFERRED: 11" but the table has 10 RE-DEFERRED rows — the table is authoritative; the summary line is stale. Moved 10 seeds.
- 3 folded todos (TODO-metasprites-baseline, TODO-13.8-wr-followups, TODO-triggersystem-validation) are TRIAGE rows only; their source files under `.planning/todos/pending/` were not moved.
- `.planning/seeds/evidence/` untouched.
- All moves used `git mv` to preserve history with D-02 stamps intact.

### Task 2: Backlog Index + D-11 Reconciliation

**Backlog index entries added (D-04):**
- ARCH-01/02 and IDE-01/02/RPG-01: backlog file links added to existing entries
- NEW EXAMPLES-01: SEED-003 simple-physics playability polish
- NEW PLAT-EXT-01: SEED-PHASE-12-ONE-WAY-TILE one-way tile collision
- NEW PLAT-EXT-02: SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS per-zone tilemap banks
- NEW PLAT-EXT-03: SEED-PHASE-12-SHARED-TILESET shared tileset deduplication

**FIX-01..06 D-11 reconciliation:**
- FIX-01 (SEED-004/005/006/013): all VERIFIED-ALREADY-FIXED → Phase 19 scope = screenshot confirmation only
- FIX-02 (SEED-007/008/009/010/011): all VERIFIED-ALREADY-FIXED → Phase 19 scope = emission-test guards for verified behavior
- FIX-03 (SEED-014/015/016): all VERIFIED-ALREADY-FIXED → Phase 20 discuss-phase may scope down; re-verification only
- FIX-04 (SEED-PHASE-13-SPRITE-OUTLINE): VERIFIED-ALREADY-FIXED → Phase 20 scope = D-08 visual oracle only
- FIX-05 (platformer): 4 seeds CONFIRMED-OPEN → active Phase 21 scope unchanged
- FIX-06 (small DSL/tooling): SEED-002/012/026 archived, SEED-003/one-way/shared/per-zone backlogged → active Phase 21 scope = SEED-017/020/022/ZONE-MAGIC-STRING

**TRIAGE traceability updated:**
- TRIAGE-01: Complete — 47 rows finalized (24 VERIFIED-ALREADY-FIXED, 12 CONFIRMED-OPEN, 10 RE-DEFERRED, 1 TODO-VERIFIED)
- TRIAGE-02: Complete — 10 visual seeds closed with runtime screenshots; D-08 gate PASSED 2026-06-12
- TRIAGE-03: Complete — seeds/ holds 10 CONFIRMED-OPEN seeds; 10 RE-DEFERRED moved to backlog/v0.2.0/

**Phase 19/20/21 ROADMAP annotations:**
- Phase 19: D-11 note that FIX-01/02 seeds all VERIFIED-ALREADY-FIXED; scope = evidence confirmation
- Phase 20: D-11 note that FIX-03/04 seeds all VERIFIED-ALREADY-FIXED; discuss-phase scope may reduce
- Phase 21: D-11 notes for FIX-05 active platformer seeds and FIX-06 active/RE-DEFERRED breakdown

## Verification Results

- All 10 remaining `.planning/seeds/SEED-*.md` files are CONFIRMED-OPEN per TRIAGE.md
- `.planning/seeds/archive/` contains 24 VERIFIED-ALREADY-FIXED seed files
- `.planning/backlog/v0.2.0/` contains 10 RE-DEFERRED seed files (6 fast-path + 4 newly RE-DEFERRED)
- All 6 fast-path seeds present in backlog (SEED-001/018/019/024/RAW-C/CPAREN)
- `backlog/v0.2.0` referenced in REQUIREMENTS.md: PASS
- "Phase 16 triage" referenced in ROADMAP.md + REQUIREMENTS.md: PASS
- No requirement IDs deleted; edits are annotations/membership adjustments only

## Deviations from Plan

**1. [Rule 1 - Data Discrepancy] TRIAGE.md summary count mismatch (RE-DEFERRED: 11 vs 10 rows)**
- **Found during:** Task 1
- **Issue:** TRIAGE.md progress summary states "RE-DEFERRED: 11" but the disposition table contains exactly 10 RE-DEFERRED rows. The summary line is stale (likely from an earlier draft count).
- **Fix:** Moved the 10 RE-DEFERRED seeds actually present in the table. Did not invent a phantom 11th file. This is a documentation inconsistency in TRIAGE.md only — data is correct.
- **Files modified:** None (no file needed to be created; TRIAGE.md update would require re-finalization)
- **Commit:** Handled inline in Task 1 decision

## Known Stubs

None. This plan produces only file moves and documentation edits; no stubs applicable.

## Threat Flags

None. No new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Self-Check: PASSED

- Seeds in archive: 24 files confirmed present
- Seeds in backlog: 10 files confirmed present
- Seeds in seeds/: 10 CONFIRMED-OPEN files confirmed present
- Commits: 5e7bd6e2 (task 1) and 83e8caa8 (task 2) confirmed in git log
- REQUIREMENTS.md has backlog/v0.2.0 reference: PASS
- ROADMAP.md has Phase 16 triage annotation: PASS
- TRIAGE-03 marked Complete in traceability table: PASS
