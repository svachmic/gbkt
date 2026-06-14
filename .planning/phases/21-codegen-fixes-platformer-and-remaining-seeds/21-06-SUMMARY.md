---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
plan: "06"
subsystem: planning-artifacts
tags: [seed-triage, re-deferral, backlog, requirements]
dependency_graph:
  requires: []
  provides: [backlog/v0.2.0/SEED-017, backlog/v0.2.0/SEED-023, backlog/v0.2.0/SEED-025, backlog/v0.2.0/SEED-ZONE-MAGIC-STRING, requirements-fix06-disposition]
  affects: [.planning/REQUIREMENTS.md, .planning/seeds/, .planning/backlog/v0.2.0/]
tech_stack:
  added: []
  patterns: []
key_files:
  created:
    - .planning/backlog/v0.2.0/SEED-017-sport-zone-tileset-pipeline-unification.md
    - .planning/backlog/v0.2.0/SEED-023-whenever-runif-unification.md
    - .planning/backlog/v0.2.0/SEED-025-remove-deprecated-combat-string-overload.md
    - .planning/backlog/v0.2.0/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION.md
    - .planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/RE-DEFERRAL-NOTE.md
  modified:
    - .planning/REQUIREMENTS.md
decisions:
  - "Re-defer SEED-017 (sport-zone dual pipeline) to v0.2.0: moderate blast radius, INV-8 stays GREEN, no shipping example exercises the dual-pipeline path heavily"
  - "Re-defer SEED-023 (whenever→runIf unification) to v0.2.0: needs full deprecation cycle, functional API already removed in Phase 18 DEPR-01"
  - "Re-defer SEED-025 (combatIsInState String overload removal) to v0.2.0: scheduled by design, cannot remove until one release after deprecation ships"
  - "Re-defer SEED-ZONE-MAGIC-STRING (zone delegate migration) to v0.2.0: wide blast radius touches gbkt-lang + gbkt-engine + every game using zone()"
metrics:
  duration: "3 min"
  completed: "2026-06-14"
  tasks: 2
  files: 6
---

# Phase 21 Plan 06: Seed Re-deferrals + REQUIREMENTS.md FIX-06 Reconciliation Summary

**One-liner:** Re-deferred four D-03 seeds (SEED-017, SEED-023, SEED-025, SEED-ZONE-MAGIC-STRING) to backlog/v0.2.0 via git mv with rationale headers; updated REQUIREMENTS.md FIX-06 to reflect Phase 21 dispositions per D-04.

## What Was Done

### Task 1: Move four D-03 seeds to backlog/v0.2.0

All four seeds were moved via `git mv` from `.planning/seeds/` to `.planning/backlog/v0.2.0/`, preserving git history. Each received a one-line `> Re-deferred to v0.2.0 (Phase 21): <rationale>` header prepended immediately after the H1 title, following the Phase 16 Plan 10 file-move precedent.

| Seed | Rationale |
|------|-----------|
| SEED-017 | Moderate sport-genre dual-pipeline refactor; INV-8 lock-test stays GREEN; needs discuss/spec phase |
| SEED-023 | whenever→runIf needs full deprecation cycle; Phase 18 removed functional API but removal can't ship until one release after deprecation |
| SEED-025 | combatIsInState(String,String) removal scheduled v0.2.0 by design; v0.1.0 shipped the deprecated overload |
| SEED-ZONE-MAGIC-STRING | Wide blast radius — gbkt-lang + gbkt-engine IR + every zone() call site across all games |

Evidence note `RE-DEFERRAL-NOTE.md` written in the phase directory (satisfies D-04c).

### Task 2: Reconcile REQUIREMENTS.md FIX-06 disposition

FIX-06 line updated to reflect Phase 21 outcomes:
- `SEED-017`: CONFIRMED-OPEN → RE-DEFERRED v0.2.0
- `SEED-ZONE-MAGIC-STRING`: CONFIRMED-OPEN → RE-DEFERRED v0.2.0
- `SEED-020`: CONFIRMED-OPEN → FIXED (Phase 21, GameIRSerializer round-trip)
- `SEED-022`: CONFIRMED-OPEN → FIXED (Phase 21, predicate consolidation)
- Phase 21 FIX-06 active scope sentence updated to: "SEED-020 + SEED-022 (SEED-017 + ZONE-MAGIC-STRING re-deferred to v0.2.0 with evidence per Criterion 3)"
- Traceability FIX-06 row left Pending (closeout plan 21-08 flips it)

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | 5d15048d | docs(21-06): re-defer four D-03 seeds to backlog/v0.2.0 with rationale headers |
| Task 2 | 9f59c0d6 | docs(21-06): reconcile REQUIREMENTS.md FIX-06 disposition per D-04 |

## Deviations from Plan

None — plan executed exactly as written. Both git mv + header-prepend and REQUIREMENTS.md edit completed in straight-line order.

## Known Stubs

None. This is a planning-artifact move and documentation edit only.

## Threat Flags

None. No code, no runtime surface, no network endpoints introduced.

## Self-Check: PASSED

- [x] `.planning/backlog/v0.2.0/SEED-017-sport-zone-tileset-pipeline-unification.md` exists
- [x] `.planning/backlog/v0.2.0/SEED-023-whenever-runif-unification.md` exists
- [x] `.planning/backlog/v0.2.0/SEED-025-remove-deprecated-combat-string-overload.md` exists
- [x] `.planning/backlog/v0.2.0/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION.md` exists
- [x] `.planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/RE-DEFERRAL-NOTE.md` exists
- [x] `.planning/seeds/` no longer contains any of the four seeds
- [x] REQUIREMENTS.md FIX-06 contains "RE-DEFERRED v0.2.0" and "SEED-020 → FIXED"
- [x] Commits 5d15048d and 9f59c0d6 exist in git log
