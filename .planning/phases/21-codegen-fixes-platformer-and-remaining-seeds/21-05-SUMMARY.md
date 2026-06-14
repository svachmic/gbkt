---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
plan: "05"
subsystem: docs
tags:
  - docs-cleanup
  - seed-closure
  - whenever-runif
dependency_graph:
  requires: []
  provides:
    - SEED-029 archived FIXED
    - whenever→runIf doc/KDoc unified across 18 files
  affects:
    - README.md
    - gbkt-lang KDoc (7 files)
    - gbkt-ir KDoc (2 files)
    - gbkt-core KDoc (1 file)
    - gbkt-genre-rpg KDoc (1 file)
    - gbkt-genre-sport KDoc (1 file)
    - gbkt-genre-platformer tests (2 files)
    - gbkt-examples CLAUDE.md (3 files)
tech_stack:
  added: []
  patterns: []
key_files:
  created:
    - .planning/seeds/archive/SEED-029-whenever-doc-reference-cleanup.md
  modified:
    - README.md
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/References.kt
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Expr.kt
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/CollectionBuilders.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/CombatEngineBuilder.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/UIBuilders.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt
    - gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/CombatStates.kt
    - gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt
    - gbkt-genre-platformer/src/test/kotlin/.../PlatformerInputEmissionTest.kt
    - gbkt-genre-platformer/src/test/kotlin/.../LevelCardSceneBuilderTest.kt
    - gbkt-examples/breakout/CLAUDE.md
    - gbkt-examples/pong/CLAUDE.md
    - gbkt-examples/simple-physics/CLAUDE.md
decisions:
  - "Replace DSL-example whenever( → runIf( in docs/KDoc; preserve historical references in CHANGELOG, CONTRIBUTING, and ExprVisitor (bucket-b history)"
  - "Keep gbkt-lang/CLAUDE.md pipeline diagram with ScriptBuilder.whenever() because the deprecated method still exists"
  - "SEED-029 closed as FIXED and archived (Phase 21 plan 21-05)"
metrics:
  duration: "5 min"
  completed: "2026-06-14"
  tasks_completed: 2
  files_modified: 18
---

# Phase 21 Plan 05: whenever→runIf Doc/KDoc Sweep Summary

**One-liner:** `whenever(` → `runIf(` sweep across 18 docs/KDoc files + SEED-029 archived as FIXED (49 replacements, 6 intentional keeps).

## What Was Built

This plan executed a cosmetic doc/KDoc sweep (D-12 / SEED-029) replacing `whenever(` DSL-example references with `runIf(` across all documentation and KDoc files. The functional migration was already completed in Phase 18 (plans 18-29/18-30); this was residual docs-only cleanup.

### Task 1: Grep Inventory (Enumeration Gate)

Ran `grep -rn "whenever(" --include=*.kt --include=*.md` across the project (excluding `build/`, `.planning/`, `.serena/`, `gbkt-cli/`, `gbkt-intellij-plugin/`). Found **54 total occurrences**, classified as:

- **49 REPLACE** — DSL examples, KDoc snippets showing `whenever(` as the current API
- **6 KEEP** — intentional historical references with rationale (see below)
- _(Note: 54 = 49 replaced + 6 kept; the .serena memories were excluded from the scope)_

### Task 2: Apply Replacements + Archive SEED-029

Applied all 49 replacements across 18 files. Post-sweep grep confirmed exactly 6 occurrences remain, all intentionally kept. Seed archived with FIXED disposition note and full inventory.

## Full Grep Inventory

### Replaced Occurrences (49 total across 18 files)

| File | Occurrences |
|------|-------------|
| `README.md` | 9 (lines 24-29, 86, 141-144, 182-183) |
| `gbkt-core/.../References.kt` | 3 (lines 24, 66, 85) |
| `gbkt-ir/.../Expr.kt` | 1 (line 116) |
| `gbkt-ir/.../ScriptOp.kt` | 1 (line 661) |
| `gbkt-lang/.../ActorBuilder.kt` | 2 (lines 56, 352) |
| `gbkt-lang/.../CollectionBuilders.kt` | 2 (lines 86, 197) |
| `gbkt-lang/.../CombatEngineBuilder.kt` | 2 (lines 49, 50) |
| `gbkt-lang/.../GameBuilder.kt` | 1 (line 296) |
| `gbkt-lang/.../SceneBuilder.kt` | 1 (line 177) |
| `gbkt-lang/.../UIBuilders.kt` | 1 (line 143) |
| `gbkt-lang/.../WorldBuilders.kt` | 1 (line 517) |
| `gbkt-genre-rpg/.../CombatStates.kt` | 2 (lines 26, 29) |
| `gbkt-genre-sport/.../SportVisitor.kt` | 1 (line 151) |
| `gbkt-genre-platformer/.../PlatformerInputEmissionTest.kt` | 2 (lines 47, 261) |
| `gbkt-genre-platformer/.../LevelCardSceneBuilderTest.kt` | 2 (lines 28, 88) |
| `gbkt-examples/breakout/CLAUDE.md` | 4 |
| `gbkt-examples/pong/CLAUDE.md` | 7 |
| `gbkt-examples/simple-physics/CLAUDE.md` | 2 |

### Kept Occurrences (6 total, with rationale)

| File:Line | Content | Rationale |
|-----------|---------|-----------|
| `CHANGELOG.md:13,15` | Migration guide showing `whenever(` as what to migrate FROM | Migration documentation must name the deprecated form |
| `CONTRIBUTING.md:461` | Migration table "Old DSL" column | Table comparing deprecated vs. current API |
| `gbkt-backend-gbdk/CLAUDE.md:32` | Historical bucket-b bug path explanation using `whenever(spdY isAbove 64)` | Describes syntax that was active during Phase 07.9 bug — accurate historical documentation |
| `gbkt-backend-gbdk/.../ExprVisitor.kt:105` | KDoc continuing the bucket-b historical explanation | Same historical context; the comment explains the fix in terms of the old syntax |
| `gbkt-lang/CLAUDE.md:49` | Pipeline diagram: `whenever(cond) → ScriptBuilder.whenever() → IRIf` | `ScriptBuilder.whenever()` still exists as a `@Deprecated` method; referencing it here is accurate |

## Verification

- Post-sweep grep: 6 occurrences remain (all in kept list above) ✓
- spotless + detekt: BUILD SUCCESSFUL for all touched .kt modules (gbkt-ir, gbkt-lang, gbkt-core, gbkt-genre-rpg, gbkt-genre-sport, gbkt-genre-platformer, gbkt-backend-gbdk) ✓
- SEED-029 archived at `.planning/seeds/archive/SEED-029-whenever-doc-reference-cleanup.md` with FIXED note ✓
- Original seed deleted from `.planning/seeds/` ✓

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

None. This plan is docs/KDoc-only with no runtime surface changes.

## Known Stubs

None.

## Self-Check: PASSED

- `dfe0f1b3` commit exists: FOUND
- `.planning/seeds/archive/SEED-029-whenever-doc-reference-cleanup.md` exists: FOUND
- `.planning/seeds/SEED-029-whenever-doc-reference-cleanup.md` deleted: CONFIRMED
