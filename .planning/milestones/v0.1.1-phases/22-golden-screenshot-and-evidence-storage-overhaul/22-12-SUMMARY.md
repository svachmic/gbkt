---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "12"
subsystem: planning
tags: [git, evidence, cleanup, gitignore, untrack]

# Dependency graph
requires:
  - phase: 22-03
    provides: .gitignore rule /.planning/phases/**/evidence/ (stops NEW files tracking)
  - phase: 22-04
    provides: 6 metasprites golden anchors byte-identically migrated to src/test/resources/goldens/
  - phase: 22-05
    provides: 16 platformer-template golden anchors byte-identically migrated to src/test/resources/goldens/
  - phase: 22-06
    provides: metasprites visual-UAT tests wired to assertGoldenMatch
  - phase: 22-07
    provides: platformer-template visual-UAT tests wired to assertGoldenMatch
  - phase: 22-08
    provides: GoldenAssertions capturedAt removal
  - phase: 22-09
    provides: GoldenStorage.EVIDENCE_DIR deprecation
  - phase: 22-10
    provides: GoldenStorage.EVIDENCE_DIR remaining uses removed
  - phase: 22-11
    provides: goldens/ scratch-directory pattern complete

provides:
  - All 143 tracked .planning/phases/**/evidence/ files removed from git index
  - evidence/ directories now gitignored scratch (working-tree copies remain)
  - Zero evidence churn on git status for archived phases

affects: [22-13, 22-14]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "git rm --cached via xargs to untrack files whose glob was expanded by ls-files"
    - "Precondition guard (22 goldens must exist before any rm) as anti-anti-pattern per T-22-12"

key-files:
  created: []
  modified:
    - .planning/phases/16-seed-triage/evidence/ (64 files untracked)
    - .planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/ (3 files untracked)
    - .planning/phases/19-codegen-fixes-metasprite-cluster/evidence/ (12 files untracked)
    - .planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/ (12 files untracked)
    - .planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/evidence/ (52 files untracked)

key-decisions:
  - "Used git rm --cached (not bare git rm) to keep working-tree copies as gitignored scratch"
  - "Precondition guard confirmed 22 goldens exist in resources BEFORE any git rm"
  - "git ls-files | xargs git rm --cached used because the shell glob in the PLAN was not expanded by the subshell substitution"

patterns-established:
  - "git rm --cached $(git ls-files 'pattern') can fail if ls-files returns empty; use xargs instead"

requirements-completed: [FIX-07]

# Metrics
duration: 1min
completed: "2026-06-15"
---

# Phase 22 Plan 12: Untrack 143 Per-Phase Evidence Files Summary

**143 tracked .planning/phases/**/evidence/ files removed from git index via git rm --cached; evidence dirs are now gitignored scratch; 22 blessed goldens in src/test/resources/goldens/ are untouched and byte-identical**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-06-14T22:24:17Z
- **Completed:** 2026-06-14T22:24:57Z
- **Tasks:** 1
- **Files modified:** 143 (index deletions only; working-tree copies remain)

## Accomplishments

- Ran precondition guard: `find gbkt-examples/*/src/test/resources/goldens -name '*.png' | wc -l` = 22 (PASS)
- Enumerated 143 tracked evidence files via `git ls-files -- '.planning/phases/**/evidence/**'`
- Ran `git ls-files -- '.planning/phases/**/evidence/**' | xargs git rm --cached` — all 143 files untracked
- Post-removal verification: `git ls-files` for the pattern returns 0; blessed goldens count = 22 (PASS)
- Evidence directories remain on the working tree as gitignored scratch per the plan 22-03 gitignore rule

## Files Removed from Index

143 files across 5 phase evidence directories:

| Phase | Files untracked |
|-------|----------------|
| 16-seed-triage | 64 |
| 17-docs-reconciliation-and-quality-cleanup | 3 |
| 19-codegen-fixes-metasprite-cluster | 12 |
| 20-codegen-fixes-banks-and-sprite-transparency | 12 |
| 21-codegen-fixes-platformer-and-remaining-seeds | 52 |
| **Total** | **143** |

File types: 33 PNG + 77 TXT + 22 JSON + 8 MD + 2 SHA256 + 1 GITKEEP = 143

## Task Commits

1. **Task 1: Verify blessed anchors migrated, then git rm all tracked evidence** - `8883bb63`

## Decisions Made

- Used `--cached` flag to keep working-tree copies as gitignored scratch (preferred over bare `git rm` which deletes from disk too)
- Precondition guard confirmed 22 goldens in `gbkt-examples/*/src/test/resources/goldens/` BEFORE running any git rm
- The plan's `git rm -r --cached $(git ls-files ...)` form fails when the inner command produces no output — worked around by piping through `xargs` which handles empty input gracefully

## Deviations from Plan

**1. [Rule 3 - Blocking] Shell substitution form of git rm failed with empty pathspec**

- **Found during:** Task 1
- **Issue:** `git rm -r --cached $(git ls-files ".planning/phases/**/evidence/")` failed with `fatal: No pathspec was given` because without quotes, the glob was not expanded by git ls-files in the subshell context
- **Fix:** Used `git ls-files -- '.planning/phases/**/evidence/**' | xargs git rm --cached` instead — the quoted glob pattern inside `git ls-files` worked correctly, and piping through xargs passed the 143 file paths to git rm
- **Files modified:** None (this was command syntax adjustment, not a file change)

## Known Stubs

None — this plan performs a git index operation; no application code stubs possible.

## Threat Flags

None — local git index operation only; no network surface, no auth, no schema changes. T-22-12 (accidental baseline loss) mitigated by precondition guard (22 goldens confirmed in resources before git rm).

## Self-Check: PASSED

- [x] `find gbkt-examples/*/src/test/resources/goldens -name '*.png' | wc -l` = 22 (blessed anchors untouched)
- [x] `git ls-files -- '.planning/phases/**/evidence/**' | wc -l` = 0 (all evidence untracked)
- [x] Task commit `8883bb63` exists in git log
- [x] Working-tree evidence copies remain on disk as gitignored scratch

---
*Phase: 22-golden-screenshot-and-evidence-storage-overhaul*
*Completed: 2026-06-15*
