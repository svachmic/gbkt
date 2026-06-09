---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 20
subsystem: planning
tags: [phase-close, seeds, surplus-defects, roadmap, state]
dependency_graph:
  requires: [10-17, 10-18, 10-19]
  provides: [phase-10-close, phase-10.1-placeholder, phase-13-requirements-4-5]
  affects: [ROADMAP.md, STATE.md, .planning/seeds/]
tech_stack:
  added: []
  patterns: [surplus-to-seeds, phase-close-ritual, rolling-requirements-collector]
key_files:
  created:
    - .planning/seeds/SEED-004-metasprites-corrupted-tile-rendering.md
    - .planning/seeds/SEED-005-metasprites-diagonal-bg-not-checkerboard.md
    - .planning/seeds/SEED-006-metasprites-subpalette-global-not-synced.md
    - .planning/seeds/SEED-007-gamebuilder-actor-palette-slot-zero-default.md
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/phase-close.md
  modified:
    - .planning/ROADMAP.md
    - .planning/STATE.md
decisions:
  - Phase 10 verdict PARTIAL — mechanism layer PASS, visual parity deferred to Phase 10.1
  - 4 surplus seeds created (SEED-004..007): D-V1 corrupted tiles, D-V2 diagonal BG, D-V3 stale subPalette global, D-extra GameBuilder palette slot
  - Phase 10.1 placeholder inserted in ROADMAP (4 seeds trigger conditional insert)
  - 2 new Phase 13 requirements: MetaspriteBuilder.sprite() method (req 4) + explicit-slot palette DSL (req 5)
  - Phase 10 requirements 1/2/3 (Cartridge enum, if/unless, sub-pixel) all confirmed by Phase 10 markers
metrics:
  duration: 20 min
  completed_date: 2026-05-18
  tasks: 4
  files_created: 6
  files_modified: 2
---

# Phase 10 Plan 20: Phase Close (Seeds + Phase 10.1 + Phase 13 edits) Summary

Phase 10 closing rituals: 4 surplus codegen defects seeded for Phase 10.1, Phase 10.1 placeholder inserted in ROADMAP, 2 new framework-shaping DSL gaps added to Phase 13 requirements, ROADMAP+STATE updated, phase-close audit written.

## What Was Done

### Task 1: Surplus defect inventory + seed capture

Walked `10-UAT.md §Defects`, `oracle-comparison.md §Surplus seed candidates`, and the
objective's `D-extra` item. Four surplus seeds created:

| Seed | Defect | Fix Size |
|------|--------|----------|
| SEED-004 | D-V1: elephant sprite tiles render corrupted | medium |
| SEED-005 | D-V2: bgFillCheckerboard() emits diagonal stripes, not checker | small (1-line) |
| SEED-006 | D-V3: `_elephant_subPalette` global never assigned in `play_frame()` | small |
| SEED-007 | D-extra: GameBuilder.kt:713 actor-palette slot defaults to 0 | small |

SEED numbering: 004-007 (follows 001-003 from Phases 07.9/09/09.1 — no collision).

### Task 2: Phase 10.1 placeholder inserted

≥1 surplus seed (4 seeds) → Phase 10.1 inserted in ROADMAP.md:
- Summary line added between Phase 10 and Phase 11 in the phase list
- Full `### Phase 10.1` section added before `### Phase 11` with Goal, Seeds, Success Criteria
- Phase 10 summary line updated to `[x]` with completion date
- Phase 10 Plans count updated to `20/20`
- Plan 10-20 marked `[x]`

### Task 3: Phase 13 requirements updated

12 PHASE-13 markers found in `Metasprites.kt`. Analysis:

**Confirmed existing requirements (already in Phase 13, no edit needed):**
- Req 1 (Typed Cartridge enum): confirmed at lines 13, 63
- Req 2 (if/unless primitive): confirmed at lines 338, 365, 375
- Req 3 (sub-pixel abstraction): confirmed at lines 73, 388

**New requirements added:**
- Req 4: `MetaspriteBuilder.sprite()` method — surfaced at line 130 + CLAUDE.md
- Req 5: Explicit-slot `palette()` DSL — surfaced at lines 92, 317 + CLAUDE.md

Both added to Phase 13 ROADMAP.md `§Initial requirements` under a new section
`§Additional requirements (from Phase 10 metasprites audit)`.

### Task 4: ROADMAP + STATE + phase-close audit

- ROADMAP.md Phase 10 marked `[x]` complete with date
- STATE.md frontmatter updated: `stopped_at`, `last_updated`, `total_phases` (46 — adds Phase 10.1), `completed_phases` (35)
- STATE.md current position updated with Phase 10 SHIPPED entry
- `evidence/phase-close.md` written with all required sections

## Decisions Made

1. **Phase 10 verdict:** PARTIAL at mechanism layer PASS; visual defects (D-V1/D-V2) deferred to Phase 10.1 per single-named-bug doctrine.
2. **4 seeds → Phase 10.1 inserted:** D-V1/D-V2/D-V3 are visual-parity defects best fixed together in Phase 10.1 (hex-compare sprites, fix BG pattern, fix global sync). D-extra (GameBuilder slot bug) bundled as it shares the same fix-class as the Plan 16 SceneBuilder fix.
3. **Phase 13 requirements 4 and 5:** Both are genuinely new DSL surface (not in Phase 9's simple_physics markers), both surfaced during port assembly (Plan 13), and both require IR + DSL + codegen changes spanning multiple modules — they belong in Phase 13, not Phase 10.
4. **Decel-ladder abstraction (line 397) NOT added to Phase 13:** `// PHASE-13: candidate primitive — ladder pattern abstraction` was noted but is speculative — neither simple_physics nor the Phase 11/12 reference programs necessarily need it. Per Phase 13's "bounded by what the ports actually need" doctrine, this is held back until Phase 11 or 12 confirms the pattern is cross-port.

## Deviations from Plan

None — plan executed exactly as written.

The objective specified 4 surplus seeds (D-V1, D-V2, D-V3, D-extra GameBuilder) and 2 Phase 13 gaps (MetaspriteBuilder.sprite() + SceneBuilder.palette() default). All 6 were delivered.

## Known Stubs

None — this is a planning/metadata plan only. No code was written.

## Threat Flags

None — plan modifies only planning artifacts (.planning/ directory). No new source code, network endpoints, or data schemas.

## Self-Check

Files created:
- .planning/seeds/SEED-004..SEED-007: all exist (verified by `ls .planning/seeds/`)
- .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/phase-close.md: exists

ROADMAP.md edits:
- Phase 10 marked `[x]`: verified by grep
- Phase 10.1 placeholder: verified by grep for "Phase 10.1"
- Phase 13 requirements 4+5: verified by reading the ROADMAP Phase 13 section

STATE.md edits:
- "Phase 10 SHIPPED" entry: verified by grep

## Self-Check: PASSED
