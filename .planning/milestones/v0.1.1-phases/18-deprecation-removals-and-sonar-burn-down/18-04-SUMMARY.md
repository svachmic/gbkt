---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: "04"
subsystem: docs
tags: [changelog, contributing, deprecation, depr-03, d-04, d-09, seed-023, seed-025, seed-028]

requires: []

provides:
  - "Two-tier API deprecation convention section in CONTRIBUTING.md (D-04)"
  - "Root CHANGELOG.md in Keep a Changelog format with complete v0.1.1 breaking-change entry (D-09)"

affects:
  - 18-deprecation-removals-and-sonar-burn-down
  - any phase that removes or deprecates public API

tech-stack:
  added: []
  patterns:
    - "Two-tier deprecation rule: post-1.0 @Deprecated grace vs pre-1.0 hard removal with CHANGELOG note"
    - "Keep a Changelog 1.0.0 format for root CHANGELOG.md"

key-files:
  created:
    - CHANGELOG.md
  modified:
    - CONTRIBUTING.md

key-decisions:
  - "Deprecation section placed before Code Review Checklist (after Organizing Large Games) per implementor discretion (RESEARCH.md Open Question 3)"
  - "v0.1.1 CHANGELOG entry is authored complete here — other plans in Phase 18 do not edit CHANGELOG.md"
  - "CHANGELOG.md v0.1.0 entry is a brief stub; full feature set was tracked via ROADMAP/milestones not CHANGELOG"

patterns-established:
  - "Tier 1 (post-1.0): @Deprecated(WARNING) in N, removal in N+1, CHANGELOG required"
  - "Tier 2 (pre-1.0/Hardening): hard removal, no shim, CHANGELOG minimum bar"

requirements-completed: [DEPR-03]

duration: 2min
completed: "2026-06-13"
---

# Phase 18 Plan 04: Deprecation Convention Docs + CHANGELOG Summary

**Two-tier API deprecation convention added to CONTRIBUTING.md (D-04) and root CHANGELOG.md created in Keep a Changelog format with the complete v0.1.1 breaking-change entry for whenever, combatIsInState(String), and ramBanks setter removal (D-09).**

## Performance

- **Duration:** 2 min
- **Started:** 2026-06-13T10:02:05Z
- **Completed:** 2026-06-13T10:04:01Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Added `## API Deprecation Convention` section to CONTRIBUTING.md documenting the two-tier rule with worked examples from v0.1.1 (SEED-023, SEED-025, SEED-028) and a cross-reference to CHANGELOG.md.
- Created root CHANGELOG.md in Keep a Changelog 1.0.0 format with `[Unreleased]` scaffold, complete `[0.1.1]` entry (3 breaking changes), and `[0.1.0]` stub.

## Task Commits

1. **Task 1: Add two-tier API Deprecation Convention section to CONTRIBUTING.md** - `62141703` (docs)
2. **Task 2: Create root CHANGELOG.md with complete v0.1.1 entry** - `3686f6e6` (docs)

## Files Created/Modified

- `CONTRIBUTING.md` - New `## API Deprecation Convention` section (46 lines inserted before Code Review Checklist)
- `CHANGELOG.md` - New root file: Keep a Changelog format, [Unreleased] + [0.1.1] + [0.1.0] entries

## Decisions Made

- Deprecation section placed between `## Organizing Large Games` and `## Code Review Checklist` per implementor discretion (RESEARCH.md Open Question 3 resolved).
- v0.1.1 CHANGELOG entry is the single authoritative record for this plan — no other Phase 18 plans need to edit CHANGELOG.md.
- Added a brief `[0.1.0]` stub entry so the changelog is not orphaned; the initial MVP release is documented.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- D-04 and D-09 deliverables complete and committed.
- CHANGELOG.md is ready to receive future entries from Phase 18 plans if needed (Unreleased section scaffolded).
- CONTRIBUTING.md deprecation convention is ready for DEPR-01 and DEPR-02 removal commits to reference.

## Known Stubs

None — both documents are complete for their Phase 18 scope. The `[Unreleased]` CHANGELOG section is intentionally empty (awaiting future breaking changes after v0.1.1).

## Threat Flags

None — documentation authoring only; no new code, network, or data-handling surface introduced.

## Self-Check

Files created/modified:
- `CONTRIBUTING.md` — FOUND (46 lines added, section present)
- `CHANGELOG.md` — FOUND (30 lines, [0.1.1] entry present with ramBanks, whenever, combatIsInState)

Commits:
- `62141703` — FOUND (docs(18-04): add two-tier API deprecation convention to CONTRIBUTING.md)
- `3686f6e6` — FOUND (docs(18-04): create root CHANGELOG.md with complete v0.1.1 breaking-change entry)

## Self-Check: PASSED

---
*Phase: 18-deprecation-removals-and-sonar-burn-down*
*Completed: 2026-06-13*
