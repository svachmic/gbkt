---
phase: 06-complete-gap-closure
plan: 02
subsystem: package-structure
tags: [refactor, package-rename, ir, dsl, imports]
dependency_graph:
  requires: [06-01]
  provides: [clean-top-level-ir-dsl-packages]
  affects: [gbkt-ir, gbkt-lang, gbkt-core, gbkt-backend-gbdk, gbkt-analysis, gbkt-rpg, gbkt-examples, gbkt-gradle-plugin]
tech_stack:
  added: []
  patterns: [find-and-replace-import-paths, spotless-apply-formatting]
key_files:
  created: []
  modified:
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/*.kt (15 files, package rename)
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/*.kt (15 files, package rename)
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/*.kt (4 test files, moved)
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/dsl/*.kt (3 test files, moved)
    - gbkt-backend-gbdk/src/ (13 files, import updates)
    - gbkt-analysis/src/ (15 files, import updates)
    - gbkt-examples/ (9 files, import updates)
    - gbkt-lang/src/ (12 files, import updates)
    - gbkt-rpg/src/ (4 files, import updates)
    - gbkt-gradle-plugin/src/ (2 files, import updates)
    - detekt.yml (WildcardImport excludeImports: dsl.v2.* -> dsl.*)
decisions:
  - v2 package namespace removed — all IR types now at io.github.gbkt.core.ir.*, DSL at io.github.gbkt.core.dsl.*
  - detekt.yml WildcardImport excludeImports updated from dsl.v2.* to dsl.* — wildcard required for operator extension functions in game files
  - spotlessApply run to fix KDoc line reflow violations caused by import path length change
metrics:
  duration: 6 min
  completed: 2026-02-21
  tasks: 2
  files: 85+
---

# Phase 06 Plan 02: V2 Package Path Promotion Summary

Promoted v2 package paths to top-level: `ir/v2/` contents moved to `ir/`, `dsl/v2/` contents moved to `dsl/`. All imports updated codebase-wide. Full build and test suite pass with zero compilation errors.

## Tasks Completed

### Task 1: Move files from v2/ subdirectories to parent directories

Moved 15 IR files from `gbkt-ir/src/.../ir/v2/` to `gbkt-ir/src/.../ir/`:
- ActorIR.kt, AssetRef.kt, CollectionsIR.kt, CoreTypes.kt, Expr.kt, ExprVisitorI.kt, GameIR.kt, PlatformAnnotations.kt, Ref.kt, SceneIR.kt, ScriptOp.kt, ScriptOpVisitorI.kt, SystemIR.kt, SystemIRVisitorI.kt, Types.kt

Moved 15 DSL files from `gbkt-lang/src/.../dsl/v2/` to `gbkt-lang/src/.../dsl/`:
- ActorBuilder.kt, AssetRegistry.kt, DmgColor.kt, DslMarkers.kt, Errors.kt, ExprBuilder.kt, GameBuilder.kt, InputBuilders.kt, RefRegistry.kt, SceneBuilder.kt, ScriptBuilder.kt, ScriptBuilderContext.kt, SourceLocationCapture.kt, SystemBuilders.kt, VariableBuilders.kt

Updated package declarations in all 30 moved files. Deleted empty v2/ directories.

**Commit:** 36a697d

### Task 2: Update all imports codebase-wide and verify build

Found 78 files with `io.github.gbkt.core.ir.v2` imports and 7 with `io.github.gbkt.core.dsl.v2` imports. Performed global find-and-replace via `sed`.

Also discovered and moved 7 test files from `gbkt-core/src/test/kotlin/.../v2/` to parent test directories (ir/ and dsl/). Test package declarations were already updated by the global sed.

Updated `detekt.yml` WildcardImport `excludeImports` from `dsl.v2.*` to `dsl.*` — game files use `import io.github.gbkt.core.dsl.*` wildcard for operator extension functions.

Fixed `ConstantFoldingPassTest.kt`: added explicit `Expr` import and collapsed FQN type references in function signature (Spotless format violation).

Ran `spotlessApply` to fix KDoc line reflow violations caused by shorter import path names.

Build passes across all 140 tasks. All tests pass.

**Commit:** 34dc971

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] Test files in v2/ subdirectories not covered by plan**
- **Found during:** Task 2 (build verification)
- **Issue:** `gbkt-core/src/test/kotlin/io/github/gbkt/core/{ir,dsl}/v2/` contained 7 test files that the plan didn't mention
- **Fix:** Moved test files to parent test directories; v2/ test dirs deleted
- **Files modified:** 7 test files (GameBuilderTest, RefRegistryTest, ScriptBuilderTest, ExprTest, IRHierarchyTest, PlatformAnnotationsTest, ScriptOpTest)
- **Commit:** 34dc971

**2. [Rule 1 - Bug] detekt.yml WildcardImport excludeImports not updated**
- **Found during:** Task 2 (build verification: detekt failure on BreakoutV2.kt)
- **Issue:** `detekt.yml` still listed `io.github.gbkt.core.dsl.v2.*` in `excludeImports`; game files now use `io.github.gbkt.core.dsl.*`
- **Fix:** Updated `excludeImports` entry from `dsl.v2.*` to `dsl.*`
- **Files modified:** `detekt.yml`
- **Commit:** 34dc971

**3. [Rule 1 - Bug] Spotless formatting violations after import path shortening**
- **Found during:** Task 2 (initial build run)
- **Issue:** Spotless detected two files where shorter import paths caused KDoc line reformatting or FQN collapse to violate formatting rules
- **Fix:** Added `Expr` import to `ConstantFoldingPassTest.kt`, ran `spotlessApply` globally
- **Files modified:** `ConstantFoldingPassTest.kt` + various files via spotlessApply
- **Commit:** 34dc971

## Verification

- `find . -path "*/ir/v2" -o -path "*/dsl/v2" | grep -v .planning | grep -v build/` — returns nothing (PASS)
- `grep -rl "\.ir\.v2\." --include="*.kt"` — 0 files (PASS)
- `grep -rl "\.dsl\.v2\." --include="*.kt"` — 0 files (PASS)
- `./gradlew build` — BUILD SUCCESSFUL (140 tasks)
- `./gradlew :gbkt-core:test :gbkt-backend-gbdk:test :gbkt-analysis:test` — PASS

## Self-Check: PASSED

Files verified:
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt` — exists, package `io.github.gbkt.core.ir`
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt` — exists, package `io.github.gbkt.core.dsl`
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/ExprTest.kt` — exists, package `io.github.gbkt.core.ir`

Commits verified:
- 36a697d: refactor(06-02): move IR and DSL files from v2/ subdirs to parent packages
- 34dc971: refactor(06-02): update all imports from ir.v2/dsl.v2 to top-level packages
