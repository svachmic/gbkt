---
phase: 06-complete-gap-closure
plan: 03
subsystem: module-structure
tags: [gradle, module-restructure, gbkt-world, gbkt-engine, gbkt-all, bom, test-infra]
dependency_graph:
  requires: [06-02]
  provides: [gbkt-world-module, gbkt-all-module, module-bom-complete]
  affects: [settings.gradle.kts, gbkt-bom, gbkt-lang, gbkt-core, gbkt-engine, gbkt-world, gbkt-all]
tech_stack:
  added: [gbkt-world, gbkt-all]
  patterns: [api-reexport-pattern, placeholder-package-info, internal-to-module-test-placement]
key_files:
  created:
    - gbkt-world/build.gradle.kts
    - gbkt-world/src/main/kotlin/io/github/gbkt/core/world/package-info.kt
    - gbkt-world/src/main/kotlin/io/github/gbkt/core/exploration/package-info.kt
    - gbkt-engine/src/main/kotlin/io/github/gbkt/core/scene/package-info.kt
    - gbkt-engine/src/main/kotlin/io/github/gbkt/core/entity/package-info.kt
    - gbkt-engine/src/main/kotlin/io/github/gbkt/core/input/package-info.kt
    - gbkt-engine/src/main/kotlin/io/github/gbkt/core/graphics/package-info.kt
    - gbkt-all/build.gradle.kts
    - gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/ArrayVarHelpersTest.kt
  modified:
    - settings.gradle.kts
    - gbkt-core/build.gradle.kts
    - gbkt-bom/build.gradle.kts
    - gbkt-gradle-plugin/build.gradle.kts
    - gbkt-lang/build.gradle.kts
decisions:
  - "Package extraction plan (B2) was aspirational: v1 types deleted in Plan 01 left empty packages; created structural placeholders instead of actual file moves"
  - "gbkt-world uses api(gbkt-ir) + api(gbkt-lang) since exploration types need DSL builders"
  - "ScriptBuilderContext is internal to gbkt-lang module — ArrayVarHelpersTest moved from gbkt-core to gbkt-lang so internal types are accessible"
  - "gbkt-gradle-plugin test framework: replaced invalid junit-bom:6.0.1 with junit-bom:5.11.4 + kotlin(test)"
metrics:
  duration: "~40 minutes (across two sessions due to context limit)"
  completed: "2026-02-21"
  tasks: 3
  files_changed: 15
---

# Phase 06 Plan 03: Module Restructure Summary

Module restructure per Directive B2: created gbkt-world and gbkt-all modules, populated gbkt-engine with package placeholders, completed gbkt-bom with all published modules, standardized test framework.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Create gbkt-world module | a793706 | gbkt-world/build.gradle.kts, world/+exploration/ package-info.kt files |
| 2 | Populate gbkt-engine with package structure | 45b7de3 | scene/, entity/, input/, graphics/ package-info.kt files in gbkt-engine |
| 3 | Create gbkt-all, fix test infra, complete BOM | 29e33c5 | gbkt-all/build.gradle.kts, gbkt-lang test dir, ArrayVarHelpersTest.kt |

## What Was Built

### Task 1: gbkt-world Module

Created `gbkt-world/build.gradle.kts` as a new Gradle module for world and exploration types:

```kotlin
plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    api(project(":gbkt-ir"))
    api(project(":gbkt-lang"))
}
```

Added `include("gbkt-world")` to `settings.gradle.kts` and `api(project(":gbkt-world"))` to `gbkt-core/build.gradle.kts` so existing consumers of `gbkt-core` continue to see world/exploration types transitively.

Created package placeholder files:
- `gbkt-world/src/main/kotlin/io/github/gbkt/core/world/package-info.kt`
- `gbkt-world/src/main/kotlin/io/github/gbkt/core/exploration/package-info.kt`

**Discovery:** The world/ and exploration/ packages in `gbkt-core` only contained `CLAUDE.md` documentation files — actual v1 types were deleted in Plan 01. The plan's "extraction" was therefore creating proper structural placeholders for future v2 world types.

### Task 2: gbkt-engine Package Structure

Populated `gbkt-engine` with four domain package directories (replacing a generic `engine/` placeholder):

- `gbkt-engine/src/main/kotlin/io/github/gbkt/core/scene/package-info.kt`
- `gbkt-engine/src/main/kotlin/io/github/gbkt/core/entity/package-info.kt`
- `gbkt-engine/src/main/kotlin/io/github/gbkt/core/input/package-info.kt`
- `gbkt-engine/src/main/kotlin/io/github/gbkt/core/graphics/package-info.kt`

Removed old `gbkt-engine/src/main/kotlin/io/github/gbkt/core/engine/package-info.kt` placeholder. Same discovery as Task 1: source files were deleted in Plan 01; these are structural scaffolds for future extraction.

### Task 3: gbkt-all + BOM + Test Framework

**gbkt-all meta-module** (`gbkt-all/build.gradle.kts`):
```kotlin
dependencies {
    api(project(":gbkt-core"))
    api(project(":gbkt-ir"))
    api(project(":gbkt-lang"))
    api(project(":gbkt-engine"))
    api(project(":gbkt-world"))
    api(project(":gbkt-backend-api"))
    api(project(":gbkt-backend-gbdk"))
    api(project(":gbkt-rpg"))
    api(project(":gbkt-analysis"))
}
```

**gbkt-bom** updated with all 9 modules including gbkt-ir, gbkt-lang, gbkt-engine, gbkt-world, gbkt-core, gbkt-backend-api, gbkt-backend-gbdk, gbkt-analysis, gbkt-all.

**gbkt-gradle-plugin** test framework fixed: replaced invalid `platform("org.junit:junit-bom:6.0.1")` with `kotlin("test")` + valid `platform("org.junit:junit-bom:5.11.4")` + `junit-jupiter` + `junit-platform-launcher`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ArrayVarHelpersTest misplaced in wrong module**
- **Found during:** Task 3 full build verification (`./gradlew :gbkt-core:test`)
- **Issue:** `ArrayVarHelpersTest.kt` was in `gbkt-core/src/test/` but used `ScriptBuilderContext.with()` which is `internal` to the `gbkt-lang` module. Kotlin's `internal` visibility is module-scoped, not package-scoped.
- **Additional bug:** Test used `arr.name` but `ArrayAccessExpr` property is `arr.array` (wrong field name).
- **Fix:** Moved test to `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/ArrayVarHelpersTest.kt`. Fixed `arr.name` → `arr.array`. Added `testImplementation(kotlin("test"))` to `gbkt-lang/build.gradle.kts`.
- **Files modified:** `gbkt-lang/build.gradle.kts`, new `gbkt-lang/src/test/.../ArrayVarHelpersTest.kt`, deleted `gbkt-core/src/test/.../ArrayVarHelpersTest.kt`
- **Commit:** 29e33c5

**Note:** Several other auto-fixes were applied in the previous session (ran out of context) covering:
- MusicCodegenTest expectations corrected for `CLiteral(n)` → `nu` unsigned suffix convention
- IntelliJ plugin detekt violations fixed (ReturnCount, LoopWithTooManyJumpStatements, SwallowedException)
- SoundEffectBuilder API updated in BreakoutV2.kt and ExplorerV2.kt (`preset = "HIT"` → `preset(SoundPreset.HIT)`)
- BreakoutIRTest `has sound effect systems` updated to use `ir.soundEffects` (not `ir.systems`)
- ScriptOpVisitor.kt missing visitMusicPlay/Stop/Pause/Resume implementations added
- SemanticValidationPass.kt MaxLineLength violation fixed

## Module Dependency Graph (Post Plan 03)

```
gbkt-ir (leaf)
  └── gbkt-lang
        └── gbkt-engine
              └── gbkt-core (re-exports all via api())
gbkt-ir ──── gbkt-world
                └── gbkt-core (re-exports via api())
gbkt-all ──── {gbkt-core, gbkt-ir, gbkt-lang, gbkt-engine, gbkt-world,
               gbkt-backend-api, gbkt-backend-gbdk, gbkt-rpg, gbkt-analysis}
```

## Verification

```
./gradlew build   → BUILD SUCCESSFUL
```

All 150 tasks pass across the full multi-module project.

## Self-Check: PASSED

Files verified:
- `gbkt-world/build.gradle.kts` — EXISTS
- `gbkt-all/build.gradle.kts` — EXISTS
- `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/ArrayVarHelpersTest.kt` — EXISTS
- Commits a793706, 45b7de3, 29e33c5 — FOUND in git log
