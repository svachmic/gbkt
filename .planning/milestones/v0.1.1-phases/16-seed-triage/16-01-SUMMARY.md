---
phase: 16-seed-triage
plan: 01
subsystem: planning
tags: [triage, seeds, disposition-table, scaffolding]

requires: []
provides:
  - "TRIAGE.md skeleton: 47-row disposition table with 6 fast-path RE-DEFERRED rows and 41 TBD rows"
  - ".planning/seeds/archive/ directory for VERIFIED-ALREADY-FIXED + INVALID seed archival (Plan 10)"
  - ".planning/backlog/v0.2.0/ directory for RE-DEFERRED seed movement (Plan 10)"
  - ".planning/phases/16-seed-triage/evidence/_drafts/ for W2 cluster agent draft fragments (Plans 03-08)"
affects: [16-02, 16-03, 16-04, 16-05, 16-06, 16-07, 16-08, 16-09, 16-10, phase-19, phase-20, phase-21]

tech-stack:
  added: []
  patterns:
    - "TRIAGE.md disposition table: ID | Title | Type | Disposition | Evidence | Fix-phase routing | Notes"
    - "D-12 fast-path RE-DEFERRED: six seeds deferred by REQUIREMENTS.md Future Requirements — no verification required"
    - "Type taxonomy: visual | emission | jvm-test | source-only | re-deferred"
    - "Disposition taxonomy: VERIFIED-ALREADY-FIXED | CONFIRMED-OPEN | RE-DEFERRED | INVALID | TBD"

key-files:
  created:
    - ".planning/phases/16-seed-triage/TRIAGE.md"
    - ".planning/seeds/archive/.gitkeep"
    - ".planning/backlog/v0.2.0/.gitkeep"
    - ".planning/phases/16-seed-triage/evidence/_drafts/.gitkeep"
  modified: []

key-decisions:
  - "TRIAGE.md column schema: ID | Title | Type | Disposition | Evidence | Fix-phase routing | Notes (Claude's-discretion D-10)"
  - "Archive directory name: .planning/seeds/archive/ (locked for all 10 plans per D-03)"
  - "D-12 fast-path rows: 6 seeds RE-DEFERRED citing REQUIREMENTS.md IDs — no W1/W2/W3 verification work"

patterns-established:
  - "TRIAGE.md is the single canonical disposition record (D-01) — seed files carry only a pointer stamp (D-02)"
  - "47-entry scope: 44 seeds + 3 folded todos; folded todos get full rows with identical evidence bar (D-05)"

requirements-completed: [TRIAGE-01]

duration: 7min
completed: 2026-06-12
---

# Phase 16 Plan 01: Seed Triage Scaffold Summary

**47-row TRIAGE.md disposition skeleton created with 6 D-12 fast-path RE-DEFERRED rows pre-filled (SEED-001/018/019/024/RAW-C/CPAREN citing REQUIREMENTS.md IDE-02/RPG-01/IDE-01/IDE-01/ARCH-01/ARCH-02) and three phase-close destination directories established.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-06-12T13:36:24Z
- **Completed:** 2026-06-12T13:43:02Z
- **Tasks:** 2
- **Files modified:** 4 (3 .gitkeep + TRIAGE.md)

## Accomplishments

- Created `.planning/phases/16-seed-triage/TRIAGE.md` with all 47 entries (44 seeds + 3 folded todos), header with Substrate SHA placeholder and D-14 SHA-pinning notice, and disposition reference table
- Pre-filled 6 D-12 fast-path RE-DEFERRED rows — SEED-001 (IDE-02), SEED-018 (RPG-01), SEED-019 (IDE-01), SEED-024 (IDE-01), SEED-RAW-C-CODEGEN-AST-MIGRATION (ARCH-01), SEED-PHASE-X-CPAREN-EXPR-IN-C-AST (ARCH-02) — each citing the REQUIREMENTS.md Future Requirements ID as rationale
- Created three destination directories: `.planning/seeds/archive/` (D-03), `.planning/backlog/v0.2.0/` (D-04), `.planning/phases/16-seed-triage/evidence/_drafts/` (D-16); no seed files moved

## Task Commits

Each task was committed atomically:

1. **Task 1: Create directory destinations** - `2a6a515a` (chore)
2. **Task 2: Write TRIAGE.md skeleton** - `68745a41` (docs)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `.planning/phases/16-seed-triage/TRIAGE.md` - Canonical 47-row disposition table; 6 fast-path RE-DEFERRED rows finalized; 41 TBD rows with Type and Fix-phase routing pre-filled from research
- `.planning/seeds/archive/.gitkeep` - Archive destination for VERIFIED-ALREADY-FIXED + INVALID seeds at phase close (D-03)
- `.planning/backlog/v0.2.0/.gitkeep` - Backlog destination for RE-DEFERRED seeds at phase close (D-04)
- `.planning/phases/16-seed-triage/evidence/_drafts/.gitkeep` - Holding area for W2 cluster agent per-cluster draft disposition fragments (D-16)

## Decisions Made

- **TRIAGE.md column schema:** `ID | Title | Type | Disposition | Evidence | Fix-phase routing | Notes` — follows PATTERNS.md §TRIAGE.md schema exactly; matches D-01 canonical-record requirement
- **Archive directory name:** `.planning/seeds/archive/` — locked per Claude's-discretion clause in D-03 for consistency across all 10 plans
- **D-12 fast-path rationale format:** Evidence column cites `REQUIREMENTS.md <ID>` for each fast-path seed; REQUIREMENTS.md ID is the auditable rationale per T-16-02 (Repudiation threat)
- **Detail section format:** Fast-path detail uses bullet list (not a second `| SEED-` table) to keep `grep -c '^| SEED-\|^| TODO-'` count exactly 47

## Deviations from Plan

None — plan executed exactly as written. The TRIAGE.md fast-path detail table was restructured from a markdown table to a bullet list to satisfy the acceptance criterion `grep -c '^| SEED-\|^| TODO-'` == 47 (a secondary detail table with `| SEED-` rows would have inflated the count).

## Known Stubs

- TRIAGE.md has 41 TBD dispositions — this is intentional per plan spec. The plan's goal is a skeleton; TBD entries are required scaffolding, not incomplete stubs. Dispositions will be filled by W2 cluster plans (Plans 03-08) and locked after the W3 visual review gate (Plan 09).

## Threat Flags

None — this plan creates only documentation and empty directories. No security-relevant surface was introduced.

## Issues Encountered

None.

## Next Phase Readiness

- TRIAGE.md skeleton is ready for Plan 02 (substrate pass) to pin the Substrate SHA field
- Archive/backlog/evidence-drafts directories are ready for Plans 03-10
- W2 cluster agents (Plans 03-08) can begin referencing `evidence/_drafts/` immediately after Plan 02 pins the SHA

---
*Phase: 16-seed-triage*
*Completed: 2026-06-12*
