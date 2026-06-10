# Scratch/Bisect Worktree Setup

**Created:** 2026-05-19 (Plan 10.2-03, Task 2)
**Deviation:** Original plan specified cbe81d29 but that commit doesn't build. See below.

## Worktree Details

| Field              | Value                                   |
|--------------------|-----------------------------------------|
| Path               | `scratch/bisect`                        |
| Commit             | `cfe41ad7`                              |
| Commit message     | fix(10.1-18): swap bgFillCheckerboard literal to 4-row-period (DEF-10.1-13-B) |
| HEAD type          | Detached HEAD (not on a branch)         |
| Gradle version     | 9.0.0 (matches main checkout)           |
| JVM                | 21 (Eclipse Adoptium 21.0.2+13-LTS)    |

## Creation Command

```bash
# Initial attempt at cbe81d29 failed to build (missing metasprites.h game.h include)
# Recreated at cfe41ad7 (pre-Plan-19/20 buildable baseline)
git worktree remove scratch/bisect  # removed cbe81d29 version
git worktree add scratch/bisect cfe41ad7
```

## Deviation: Why cfe41ad7 instead of cbe81d29

The plan specified cbe81d29 but that commit is a docs tracking commit between Plans 10.1-08
and 10.1-09. At that state, Plan 10.1-07 (WR-02) had added the `extern const metasprite_t*`
declaration to game.h, but the matching `#include <gbdk/metasprites.h>` was NOT added to game.h
until commit 20fdd8e8 (which comes after cbe81d29). The ROM at cbe81d29 fails to compile:

```
game.h:36: error 1: Syntax error, declaration ignored at 'metasprite_t'
```

cfe41ad7 is the correct pre-Plan-19/20 buildable baseline:
- INCLUDES the metasprites.h game.h fix (20fdd8e8, Plan 10.1-w4)
- INCLUDES the Plan 18 4-row-period BG checker literal fix
- Does NOT include Plans 19/20/22's bootstrap-order or palette-hoisting changes
- Verified: both CYAN sprite and CHECKER BG are visible at this commit (see verdict.md)

## Verification Results

- `git worktree list` shows both main checkout and `scratch/bisect`
- `scratch/bisect/.git` file exists (points to `.git/worktrees/bisect`)
- `./gradlew --version` in scratch/bisect outputs `Gradle 9.0.0`
- `git log -1 --format="%h"` outputs `cfe41ad7`
- `./gradlew :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom` BUILD SUCCESSFUL

## Purpose

This worktree is the bisect anchor — the last known-good state of the metasprites
ROM where the cyan elephant was visible. It is the comparison baseline for:
- Plan 10.2-04 (Probe A: Plan 19 edit set)
- Plan 10.2-05 (Probe B: Plan 20 edit set)
- Plan 10.2-06 (Probe C: Plan 22 edit set)

## Notes

- This worktree is **read-only diagnostic surface** — do NOT merge from it
- The worktree branch is preserved per `feedback_claude_code_worktree_drift_quirks.md`
- Teardown happens at Phase 10.2 close via `git worktree remove scratch/bisect`
