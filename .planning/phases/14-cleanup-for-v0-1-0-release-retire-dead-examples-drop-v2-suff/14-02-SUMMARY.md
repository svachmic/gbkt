---
phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff
plan: 02
subsystem: build
tags: [cleanup, examples, retire, settings, gitignore]

# Dependency graph
requires:
  - phase: 14
    plan: 01
    provides: empirical KEEP/RETIRE verdicts (racer = RETIRE confirmed by D-03)
provides:
  - settings.gradle.kts reduced to 7 KEEP examples
  - LabyrinthOfTheDragon/ and LabyrinthOfTheDragon-port/ removed from repo
  - gbkt-examples/racer/ removed from repo
  - gbkt-examples/.archive/ removed from filesystem + .gitignore entry deleted
affects: [14-03 baseline, 14-04 deadcode-sweep, 14-05 rename, 14-06 textual-sweep, 14-07 ci-docs, 14-08 final-regression]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "git rm -r + rm -rf for tracked tree removal (git preserves history; rm -rf clears untracked build artifacts)"
    - "rm -rf + .gitignore entry removal for gitignored directory cleanup (no git rm, no history to lose)"

key-files:
  created: []
  modified:
    - settings.gradle.kts
    - .gitignore

key-decisions:
  - "Removed LabyrinthOfTheDragon/ (160 tracked files) via git rm -r + rm -rf"
  - "Removed LabyrinthOfTheDragon-port/ (102 tracked files) via git rm -r + rm -rf"
  - "Removed gbkt-examples/racer/ (21 tracked files) via git rm -r + rm -rf"
  - "Removed gbkt-examples/.archive/ (79 files, gitignored) via rm -rf + deleted .gitignore lines 54-55"
  - "settings.gradle.kts now contains exactly 7 KEEP includes: pong, breakout, simple-physics, metasprites, metasprites-stress, banks, platformer-template"
  - ".archive/platformer/ deliberate deletion is intentional per research correction #2 (supersedes Phase 11.3 ledger note per milestone-close mandate)"

requirements-completed: [Req 2]

# Metrics
duration: 8min
completed: 2026-06-06
---

# Phase 14 / Plan 02: Hard-delete dead trees + retire racer

**Removed 283 git-tracked files across 3 dead tree directories, cleared 79 gitignored .archive files, and reduced settings.gradle.kts to the 7-example KEEP set — ./gradlew projects exits 0.**

## Performance

- **Duration:** ~8 min
- **Completed:** 2026-06-06
- **Tasks:** 2
- **Files changed:** 285 deletions (284 in Task 1 commit, 1 in Task 2 commit)

## Accomplishments

- `git rm -r LabyrinthOfTheDragon/` — removed all 160 tracked files (C source, assets, tools, tilemaps, PSD art); git history preserved
- `git rm -r LabyrinthOfTheDragon-port/` — removed all 102 tracked files (Kotlin DSL port, res/, src/, tools/); git history preserved
- `git rm -r gbkt-examples/racer/` — removed all 21 tracked files (Racer.kt, sprites, test probes, golden screenshots); git history preserved
- `rm -rf` on all three directories to clear untracked build artifact remnants
- `rm -rf gbkt-examples/.archive/` — removed 79 gitignored files across 6 subdirs (dungeon, explorer, platformer, platformer-gbc, rpg-lite, shmup)
- Removed Phase 11.3 .gitignore entry (comment + pattern at lines 54-55)
- settings.gradle.kts: deleted `include("gbkt-examples:racer")` line; now contains exactly the 7 KEEP examples

## Task Commits

1. **Task 1: git rm dead trees + RETIRE examples + settings update** — `80708ce2`
2. **Task 2: rm -rf .archive + remove .gitignore entry** — `4e3076b2`

## Verification Results

| Check | Result |
|-------|--------|
| `git ls-files` count under `LabyrinthOfTheDragon/` | 0 |
| `git ls-files` count under `LabyrinthOfTheDragon-port/` | 0 |
| `git ls-files` count under `gbkt-examples/racer/` | 0 |
| `git ls-files` count under `gbkt-examples/.archive/` | 0 (was 0 before — gitignored) |
| `grep -c "gbkt-examples:racer" settings.gradle.kts` | 0 |
| `grep -c "gbkt-examples/.archive" .gitignore` | 0 |
| `test ! -d gbkt-examples/.archive` | PASSED |
| `./gradlew projects` exit code | 0 (BUILD SUCCESSFUL) |

## Final KEEP Include Set (settings.gradle.kts)

```
include("gbkt-examples:pong")
include("gbkt-examples:breakout")
include("gbkt-examples:simple-physics")
include("gbkt-examples:metasprites")
include("gbkt-examples:metasprites-stress")
include("gbkt-examples:banks")
include("gbkt-examples:platformer-template")
```

## Removed Directories

| Directory | Tracked files removed | Untracked artifacts cleared | Method |
|-----------|----------------------|----------------------------|--------|
| `LabyrinthOfTheDragon/` | 160 | build/ (gitignored) | git rm -r + rm -rf |
| `LabyrinthOfTheDragon-port/` | 102 | build/ (gitignored) | git rm -r + rm -rf |
| `gbkt-examples/racer/` | 21 | build/ (gitignored) | git rm -r + rm -rf |
| `gbkt-examples/.archive/` | 0 (was gitignored) | 79 files (dungeon, explorer, platformer, platformer-gbc, rpg-lite, shmup) | rm -rf + .gitignore edit |

**Total git-tracked files removed:** 283

## Deviations from Plan

None — plan executed exactly as written. The deliberate deletion of `.archive/platformer/` was pre-confirmed in `<confirmed_inputs>` (research correction #2 supersedes the Phase 11.3 ledger note, milestone-close mandate).

## Threat Surface Scan

No new trust boundaries. Pure deletion + settings/gitignore edit. T-14-03 (accidental KEEP deletion) mitigated: `./gradlew projects` confirms all 7 KEEP examples still configure. T-14-04 (history loss): git rm preserves history; .archive had no git history.

## Known Stubs

None.

## Self-Check: PASSED

- `80708ce2` exists: confirmed (`git log --oneline` shows it)
- `4e3076b2` exists: confirmed
- `settings.gradle.kts` has exactly 7 KEEP includes: confirmed
- `./gradlew projects` exit 0: confirmed (BUILD SUCCESSFUL in 2s)
- All dead directories gone from disk: confirmed
- `.gitignore` .archive entry removed: confirmed

---
*Phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff*
*Completed: 2026-06-06*
