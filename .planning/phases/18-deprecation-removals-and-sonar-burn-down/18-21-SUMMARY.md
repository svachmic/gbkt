---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 21
subsystem: gbkt-backend-gbdk / codegen / visitor
tags: [sonar-s3776, extract-method, menu-codegen, byte-identity, emitting]
dependency_graph:
  requires: ["18-20"]
  provides: ["E-02 MenuVisitor.buildMenuFunction S3776 cleared"]
  affects: ["gbkt-backend-gbdk"]
tech_stack:
  added: []
  patterns: ["value-returning extract-method", "file-level private helpers to avoid TooManyFunctions"]
key_files:
  created: []
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MenuVisitor.kt
decisions:
  - "11 class methods (within TooManyFunctions limit=11); 5 additional file-level private funs to keep class count in check"
  - "buildMenuBodyStatements assembles sections in identical order as the original (Pitfall 1 avoided)"
  - "Local val captures (menuSfxOnSelect, menuSfxOnCancel, menuParentId) required for cross-module smart cast"
  - "buildMoveSoundStatements returns List<CStatement>; replaces local addMoveSound(MutableList) side-effect pattern"
  - "No NOSONAR added (E-02 resolved purely by decomposition)"
metrics:
  duration: "8 min"
  completed: "2026-06-13"
  tasks: 1
  files: 1
---

# Phase 18 Plan 21: MenuVisitor.buildMenuFunction Decomposition Summary

Decomposed `MenuVisitor.buildMenuFunction` (E-02, cc=90) into 16 focused value-returning helpers: 11 class methods and 5 file-level private functions. 7-example byte-identity ROM sweep green. No NOSONAR.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extract-method MenuVisitor.buildMenuFunction (E-02) | 63fa1a40 | MenuVisitor.kt |

## What Was Done

### Task 1: Extract-method MenuVisitor.buildMenuFunction (E-02)

`buildMenuFunction` (cc=90, line 84) mixed variable declarations, window/sprite setup, static and dynamic item rendering, cursor animation, layout-dependent navigation (vertical/horizontal/grid), selection, and cancel handling in a single 480-line method.

**Decomposition (value-returning helpers, same emission order):**

Class methods (11 total — within detekt `TooManyFunctions` limit):
- `buildMenuFunction` — thin wrapper constructing `CFunction` from `buildMenuBodyStatements`
- `buildMenuBodyStatements` — assembles all sections in original order
- `buildMenuVarDecls` — C89 local variable declarations (sel, joy, col, row, scroll_offset)
- `buildMenuInitStatements` — window layer setup + sprite cursor slot initialization
- `buildMenuItemDrawStatements` — static item draw (with early-return to dynamic helper)
- `buildMenuInputLoopBody` — assembles the `while(1)` loop body
- `buildMenuCursorDrawStatements` — text cursor draw per frame (skips if sprite cursor)
- `buildMenuNavigationStatements` — dispatches to vertical/horizontal/grid nav helper
- `buildMenuSelectStatements` — J_A select with optional SFX
- `buildMenuCancelStatements` — J_B cancel with sprite hide, parent submenu, or sentinel return

File-level private functions (5 total — outside class to avoid TooManyFunctions):
- `buildMenuDynamicItemStatements` — InventoryDataSource + ArrayDataSource population loops
- `buildMoveSoundStatements` — returns move-sound call or empty list (replaces addMoveSound closure)
- `buildMenuVerticalNavStatements` — J_UP / J_DOWN navigation with text/sprite cursor erase
- `buildMenuHorizontalNavStatements` — J_LEFT / J_RIGHT navigation
- `buildMenuGridNavStatements` — 4-direction grid navigation with row/col tracking

**Key correctness decisions:**
- All helpers return `List<CStatement>` (never mutate a passed-in accumulator) — Pitfall 1 avoided
- Emission order is identical to the original: varDecls → init → itemDraw → loop → post-loop hide → return
- Cross-module nullable properties (`sfxOnSelect`, `sfxOnCancel`, `parentId`) captured into local vals for Kotlin smart cast
- `@Suppress("LongMethod", "CyclomaticComplexMethod")` removed from `buildMenuFunction` (no longer needed)

## Byte-Identity Sweep (D-06)

Build: `./gradlew :gbkt-examples:{pong,breakout,simple-physics,metasprites,metasprites-stress,banks,platformer-template}:clean :gbkt-examples:{...}:buildRom`

| Example | Baseline SHA256 | Post-refactor SHA256 | Result |
|---------|----------------|---------------------|--------|
| banks.gb | 12c8ee2e... | 12c8ee2e... | IDENTICAL |
| breakout.gb | 564465cd... | 564465cd... | IDENTICAL |
| metasprites-stress.gb | bc51eadd... | bc51eadd... | IDENTICAL |
| metasprites.gb | 9b2440db... | 9b2440db... | IDENTICAL |
| platformer-template.gb | 9a8f268a... | 9a8f268a... | IDENTICAL |
| simple-physics.gb | 247e16d2... | 247e16d2... | IDENTICAL |
| pong.gb | 016fc9f8... | 5436584d... | PASS* (toolchain non-determinism — pong main.c IDENTICAL) |

**6/6 non-pong ROMs byte-identical. Pong PASS* (generated C identical).**

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Cross-module smart cast compile errors**
- **Found during:** Initial ROM sweep after refactoring
- **Issue:** Kotlin compiler rejected `menu.sfxOnSelect.replace(...)` inside `if (menu.sfxOnSelect != null)` because cross-module properties cannot be smart-cast
- **Fix:** Captured `menu.sfxOnSelect`, `menu.sfxOnCancel`, and `menu.parentId` into local vals before null checks (matching the original `buildMenuFunction` pattern)
- **Files modified:** MenuVisitor.kt (buildMenuSelectStatements, buildMenuCancelStatements)
- **Commit:** 63fa1a40 (same commit)

None other — plan executed as specified.

## Known Stubs

None. MenuVisitor emits full menu codegen; no placeholder logic introduced.

## Threat Flags

No new network endpoints, auth paths, file access patterns, or schema changes introduced. Codegen-internal refactor only.

## Self-Check: PASSED

- [x] MenuVisitor.kt exists: `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MenuVisitor.kt`
- [x] Commit 63fa1a40 exists in git log
- [x] spotlessApply + detekt: BUILD SUCCESSFUL
- [x] 7-example ROM sweep: BUILD SUCCESSFUL, 6/6 byte-identical + pong PASS*
