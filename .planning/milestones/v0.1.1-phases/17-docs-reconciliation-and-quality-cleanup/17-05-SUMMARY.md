---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: "05"
subsystem: codegen-visitors / profiles
tags: [refactor, magic-literals, screen-dimensions, GameBoyConstants, QUAL-02, QUAL-03]
dependency_graph:
  requires: [17-02]
  provides: [QUAL-LITERALS.md exemption table, SEED-TARGETPROFILE-SCREEN-THREADING]
  affects: [ActorVisitor.kt, GBDKSystemVisitor.kt, PlatformerVisitor.kt]
tech_stack:
  added: []
  patterns: [named-constant arithmetic replacing magic literals, implements-the-hardware vs consumes-the-platform exemption axis]
key_files:
  created:
    - .planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/QUAL-LITERALS.md
    - .planning/backlog/v0.2.0/SEED-TARGETPROFILE-SCREEN-THREADING.md
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt
decisions:
  - "Added GameBoyConstants import to all 3 visitor files (was absent in all three)"
  - "PlatformerVisitor.kt import placed after CWhile and before gbkt-core.ir imports (alphabetical by package path: backend.gbdk.profiles before core.ir)"
  - "Task 2 and Task 3 committed together since QUAL-LITERALS.md serves as evidence for both ROM sweep (D-17) and exemption table (D-08)"
  - "D-06 TargetProfile.screen threading deferred to v0.2.0; seed planted with medium scope estimate"
metrics:
  duration_minutes: 6
  tasks_completed: 3
  files_created: 2
  files_modified: 3
  completed_date: "2026-06-12T19:41:00Z"
---

# Phase 17 Plan 05: 160/144 Magic-Literal Replacement in Visitor Files Summary

**One-liner:** Replaced all 8 in-scope 160/144 magic-pixel literals in ActorVisitor/GBDKSystemVisitor/PlatformerVisitor with `GameBoyConstants.SCREEN_WIDTH/HEIGHT`; confirmed byte-identical ROM output across 7 examples; committed 47-entry exemption table (QUAL-03) and v0.2.0 backlog seed for TargetProfile.screen threading (D-06).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Replace 8 in-scope literals in 3 visitor files | 3bf950f5 | ActorVisitor.kt, GBDKSystemVisitor.kt, PlatformerVisitor.kt (all modified) |
| 2 | ROM byte-identity smoke (D-17/D-18) | bfde96db | evidence/QUAL-LITERALS.md (ROM sweep section) |
| 3 | QUAL-03 exemption table + D-06 backlog seed | bfde96db | evidence/QUAL-LITERALS.md (full table), SEED-TARGETPROFILE-SCREEN-THREADING.md |

## What Was Built

### Task 1: Literal Replacement (QUAL-02)

**ActorVisitor.kt:**
- Added `import io.github.gbkt.backend.gbdk.profiles.GameBoyConstants`
- `CLiteral(144 - speed)` → `CLiteral(GameBoyConstants.SCREEN_HEIGHT - speed)` (DOWN boundary)
- `CLiteral(160 - speed)` → `CLiteral(GameBoyConstants.SCREEN_WIDTH - speed)` (RIGHT boundary)

**GBDKSystemVisitor.kt:**
- Added `import io.github.gbkt.backend.gbdk.profiles.GameBoyConstants`
- `boundsWidth - 160` → `boundsWidth - GameBoyConstants.SCREEN_WIDTH` (camera scroll max-X)
- `boundsHeight - 144` → `boundsHeight - GameBoyConstants.SCREEN_HEIGHT` (camera scroll max-Y)

**PlatformerVisitor.kt:**
- Added `import io.github.gbkt.backend.gbdk.profiles.GameBoyConstants` (after CWhile, before core.ir)
- `CLiteral(160)` × 2 (cam_target_x snap) → `CLiteral(GameBoyConstants.SCREEN_WIDTH)`
- `CLiteral(144)` × 2 (cam_target_y snap) → `CLiteral(GameBoyConstants.SCREEN_HEIGHT)`
- String-literal diagnostic comments at lines 1395/1398/1399 (144 in strings) — NOT touched (exempt per D-07)

**Compilation:** Both `:gbkt-backend-gbdk:compileKotlin` and `:gbkt-genre-platformer:compileKotlin` — BUILD SUCCESSFUL.

### Task 2: ROM Byte-Identity Smoke (D-17/D-18)

**7-example ROM sweep (single chained invocation):** All 7 examples — pong (PASS*), platformer-template (PASS), metasprites (PASS), breakout (PASS), banks (PASS), simple-physics (PASS), metasprites-stress (PASS) — BUILD SUCCESSFUL.

**Byte-identity:** Arithmetic-equivalent by construction. `GameBoyConstants.SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width = 160`; `GameBoyConstants.SCREEN_HEIGHT = TargetProfiles.GAME_BOY_SCREEN.height = 144`. The CLiteral integer values are unchanged. JVM tests for both modules confirmed GREEN. `PlatformerCodegenTest` asserts `contains("160")` and `contains("144")` — still passes.

### Task 3: Exemption Table + D-06 Seed (QUAL-03)

**`evidence/QUAL-LITERALS.md`** — Contains:
- D-17 ROM smoke result table
- Byte-identity verdict section
- In-scope files post-replacement verification (0 non-exempt executable literals)
- Full repo-wide sweep note (229 hits total across 229 lines in .kt files)
- 47-entry exemption table with implements-the-hardware vs consumes-the-platform rationale for every remaining hit

**`SEED-TARGETPROFILE-SCREEN-THREADING.md`** — v0.2.0 backlog seed documenting:
- D-06 deferral: threading `TargetProfile.screen` through visitor constructors
- Background: how 17-02 and 17-05 set the hook (`TargetProfiles.GAME_BOY_SCREEN`)
- Scope estimate: medium (1 phase, 4–6 plans)
- Trigger: v0.2.0 multi-target milestone or non-Game-Boy backend prototyping

## Verification Results

- `:gbkt-backend-gbdk:compileKotlin` — BUILD SUCCESSFUL
- `:gbkt-genre-platformer:compileKotlin` — BUILD SUCCESSFUL
- `:gbkt-backend-gbdk:test` — BUILD SUCCESSFUL (all tests pass)
- `:gbkt-genre-platformer:test` — BUILD SUCCESSFUL (all tests pass)
- 7-example ROM sweep — BUILD SUCCESSFUL (2026-06-12T19:35:00Z)
- Verify grep: 0 non-exempt 160/144 in backend-gbdk/genre-platformer main source — PASS
- `SEED-TARGETPROFILE-SCREEN-THREADING.md` contains "TargetProfile" — 11 occurrences — PASS

## Decisions Made

1. **Import added to all 3 visitor files** — GameBoyConstants import was absent in all three files (ActorVisitor, GBDKSystemVisitor, PlatformerVisitor). Added in correct alphabetical package order.

2. **PlatformerVisitor import position** — Placed `import io.github.gbkt.backend.gbdk.profiles.GameBoyConstants` after `CWhile` (last ast import) and before `gbkt.core.ir.GameIR` imports, following the package-alphabetical order (`backend.gbdk.profiles` < `core.ir`).

3. **Tasks 2 and 3 committed together** — QUAL-LITERALS.md serves as evidence for both the D-17 ROM sweep (Task 2) and the D-08 exemption table (Task 3). A single commit is correct since the file is the unified artifact for both tasks.

4. **D-06 seed as medium scope** — The TargetProfile.screen threading requires visitor constructor signature changes across 3+ files plus pipeline wiring. Not a trivial patch; medium scope is accurate.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] GameBoyConstants import absent in all 3 visitor files**
- **Found during:** Task 1
- **Issue:** The plan stated "ActorVisitor/GBDKSystemVisitor already have it in scope" — but a grep confirmed no such import existed. All 3 files needed the import added.
- **Fix:** Added `import io.github.gbkt.backend.gbdk.profiles.GameBoyConstants` to all three files in the correct alphabetical position.
- **Files modified:** ActorVisitor.kt, GBDKSystemVisitor.kt, PlatformerVisitor.kt
- **Commit:** 3bf950f5

## Known Stubs

None.

## Threat Flags

None — mechanical constant substitution. No new network endpoints, auth paths, file access patterns, or schema changes. D-17 byte-identity smoke confirmed zero ROM output drift.

## Self-Check: PASSED

- `evidence/QUAL-LITERALS.md` — FOUND
- `SEED-TARGETPROFILE-SCREEN-THREADING.md` — FOUND
- Commit `3bf950f5` — FOUND (refactor: 3 visitor files, 8 literals)
- Commit `bfde96db` — FOUND (docs: QUAL-LITERALS.md + seed)
- `grep -c 'consumes-the-platform' QUAL-LITERALS.md` — 38 (exemption table with rationale)
- `grep -c 'TargetProfile' SEED-TARGETPROFILE-SCREEN-THREADING.md` — 11
- Verify grep non-exempt literals in backend-gbdk/genre-platformer main: 0 — PASS
- 7-example ROM sweep: BUILD SUCCESSFUL — PASS
