---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 01
subsystem: dsl
tags: [dsl, refactoring, deprecation, kotlin, gbkt-lang]

# Dependency graph
requires: []
provides:
  - "whenever DSL overloads hard-removed from ScriptBuilder and ActorPoolBuilder"
  - "runIf(PoolPoolCollisionExpr, block) pool-collision overload added to ActorPoolBuilder"
  - "All in-tree Kotlin call sites migrated from whenever( to runIf("
affects:
  - "All downstream phases that use ScriptBuilder DSL"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Exhaustive rename with compile-time enforcement (zero tolerance — a missed site = compile error)"

key-files:
  created: []
  modified:
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorPoolBuilder.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ExprBuilder.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/InputBuilders.kt
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt
    - gbkt-examples/pong/src/main/kotlin/io/github/gbkt/examples/pong/Pong.kt
    - gbkt-examples/breakout/src/main/kotlin/io/github/gbkt/examples/breakout/Breakout.kt
    - gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt
    - gbkt-examples/metasprites/src/main/kotlin/io/github/gbkt/examples/metasprites/Metasprites.kt
    - gbkt-examples/metasprites-stress/src/main/kotlin/io/github/gbkt/examples/metasprites_stress/MetaspritesStress.kt
    - gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt
    - gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt
    - gbkt-backend-gbdk/src/test (7 files)
    - gbkt-core/src/test (3 files)
    - gbkt-lang/src/test (2 files)
    - gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt
    - gbkt-gradle-plugin/src/test/resources/test-fixtures (3 files)

key-decisions:
  - "D-01: Hard removal of whenever with no @Deprecated grace period — same change removes both overloads and migrates all call sites"
  - "D-01a: ScriptBuilder.whenever(Expr, block) deleted outright (not deprecated)"
  - "D-01b: Exhaustive migration of all in-tree call sites in the same commit wave"
  - "D-01c: KDoc re-anchored from [whenever] to [runIf] across all affected files"
  - "D-02: Pool-collision overload relocated from whenever to runIf in ActorPoolBuilder"

patterns-established:
  - "Compile-enforced migration: whenever and runIf both lower to identical IfOp — the build is the completeness gate"

requirements-completed: [DEPR-01]

# Metrics
duration: ~85min
completed: 2026-06-13
---

# Phase 18 Plan 01: DEPR-01 whenever→runIf Summary

**Hard-removed both `whenever` DSL overloads and migrated all 80+ in-tree Kotlin call sites to `runIf` in a single atomic wave; build + pluginTest green with zero residual whenever( sites.**

## Performance

- **Duration:** ~85 min
- **Started:** 2026-06-13T09:35:00Z
- **Completed:** 2026-06-13T10:50:01Z
- **Tasks:** 2 completed
- **Files modified:** 37

## Accomplishments

- Deleted `ScriptBuilder.whenever(Expr, block)` outright (no @Deprecated shim per D-02)
- Relocated pool-collision overload from `whenever(PoolPoolCollisionExpr, block)` to `runIf(PoolPoolCollisionExpr, block)` in ActorPoolBuilder, preserving the `ScriptBuilderContext.with` body idiom verbatim
- Migrated 3 internal framework callers (VariableBuilders.kt:193, ExprBuilder.kt:298, ExprBuilder.kt:301) from `sb.whenever(` to `sb.runIf(`
- Re-anchored all KDoc `[whenever]` cross-references to `[runIf]` in ScriptBuilder, ActorPoolBuilder, InputBuilders, VariableBuilders
- Migrated all 7 example projects (~62 call sites): pong (24), breakout (18), simple-physics (5), metasprites (6), metasprites-stress (1), banks (4), platformer-template (3)
- Migrated 7 gbkt-backend-gbdk pipeline test files (AutoExitSynthesisTest, LevelSwitchEmissionTest, LevelCardSceneEmissionTest, TilemapCollisionPathCEmissionTest, BindCurrentLevelEmissionTest, SetupCurrentLevelDisplayGateEmissionTest, TitleSceneEmissionTest)
- Migrated 3 gbkt-core test files + 2 gbkt-lang test files + gbkt-genre-platformer PlatformerExtensions.kt
- Migrated IntegrationTest.kt (8 call sites) and 3 gradle-plugin test fixtures
- `./gradlew build pluginTest` passes; grep for `whenever(` across all Kotlin sources returns zero matches

## Task Commits

1. **Task 1: Remove both whenever overloads and migrate gbkt-lang internals** - `a27c7eed` (feat)
2. **Task 2: Migrate all in-tree whenever call sites in examples, tests, fixtures** - `08acf999` (feat)

## Files Created/Modified

**gbkt-lang (framework source)**
- `ScriptBuilder.kt` — deleted `whenever(Expr, block)`, updated section header + KDoc
- `ActorPoolBuilder.kt` — renamed pool-collision `whenever` → `runIf`, updated KDoc
- `VariableBuilders.kt` — migrated `sb.whenever(` → `sb.runIf(` at line 193, updated KDoc
- `ExprBuilder.kt` — migrated `sb.whenever(` at lines 298 and 301, updated KDoc

**gbkt-lang (tests)**
- `SaveDataDelegateTest.kt` — 1 call site migrated
- `CombatInventoryBuilderTest.kt` — 2 call sites migrated

**gbkt-lang (DSL builders)**
- `InputBuilders.kt` — KDoc [whenever] → [runIf] across InputRef, dpad, buttons docs

**gbkt-genre-platformer**
- `PlatformerExtensions.kt` — 1 call site migrated (auto-fixed Rule 1 — missed by initial scope)

**gbkt-examples (7 games)**
- `Pong.kt` — 24 replacements
- `Breakout.kt` — 18 replacements
- `SimplePhysics.kt` — 5 replacements
- `Metasprites.kt` — 6 replacements
- `MetaspritesStress.kt` — 1 replacement
- `Banks.kt` — 4 replacements
- `PlatformerTemplate.kt` — 3 replacements
- `SimplePhysicsEmissionTest.kt` — 1 replacement
- `BanksUatTest.kt` — 1 comment replacement

**gbkt-backend-gbdk tests**
- `AutoExitSynthesisTest.kt` — 4 replacements
- `LevelCardSceneEmissionTest.kt` — 6 replacements
- `LevelSwitchEmissionTest.kt` — 6 replacements
- `TilemapCollisionPathCEmissionTest.kt` — 4 replacements
- `BindCurrentLevelEmissionTest.kt` — 3 replacements
- `TitleSceneEmissionTest.kt` — 10 replacements
- `SetupCurrentLevelDisplayGateEmissionTest.kt` — 3 replacements
- `ActorPoolOperationsTest.kt` — 1 comment
- `GenericPoolCodegenTest.kt` — 7 comments
- `CombatCodegenTest.kt` — 1 comment (auto-fixed)

**gbkt-core tests**
- `GameBuilderTest.kt` — 4 replacements
- `ScriptBuilderTest.kt` — 2 replacements
- `UIBuilderTest.kt` — 1 replacement

**gbkt-gradle-plugin**
- `IntegrationTest.kt` — 8 call sites + 1 comment (auto-fixed Rule 1)
- `complex-game.kt` — 10 replacements
- `entity-game.kt` — 3 replacements
- `sprite-game.kt` — 1 replacement

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] gbkt-lang test compilation failures (Task 1 scope expansion)**
- **Found during:** Task 1 verification (`./gradlew :gbkt-lang:test`)
- **Issue:** `SaveDataDelegateTest.kt` and `CombatInventoryBuilderTest.kt` had `whenever(` call sites not listed in Task 1 scope (plan listed them under Task 2 gbkt-lang/src/test)
- **Fix:** Migrated both test files during Task 1 to satisfy the `./gradlew :gbkt-lang:test` acceptance criterion
- **Files modified:** `SaveDataDelegateTest.kt`, `CombatInventoryBuilderTest.kt`
- **Commit:** `a27c7eed`

**2. [Rule 1 - Bug] gbkt-genre-platformer PlatformerExtensions.kt missed by initial scope**
- **Found during:** Task 2 full build (`./gradlew build`)
- **Issue:** `PlatformerExtensions.kt:811` had `whenever(buttons.start.pressed)` — not in the RESEARCH census, not in the plan scope
- **Fix:** Added to the bulk Python replacement; file migrated and committed in Task 2
- **Files modified:** `PlatformerExtensions.kt`
- **Commit:** `08acf999`

**3. [Rule 3 - Blocking] Spotless formatting failures after bulk replacement**
- **Found during:** Task 1 and Task 2 verification
- **Issue:** Python string replacement changed KDoc line lengths, triggering Spotless check failures
- **Fix:** Ran `./gradlew spotlessApply` after each bulk replacement
- **Files modified:** All changed files

**4. [Rule 3 - Blocking] gbkt-core tests not in initial Python replacement scope**
- **Found during:** Task 2 full build
- **Issue:** `GameBuilderTest.kt`, `ScriptBuilderTest.kt`, `UIBuilderTest.kt` had `whenever(` call sites not included in initial replacement directories
- **Fix:** Added gbkt-core to replacement scope
- **Files modified:** 3 test files
- **Commit:** `08acf999`

**5. [Rule 3 - Blocking] Corrupt ~/.m2 JARs on first pluginTest run**
- **Found during:** Task 2 pluginTest
- **Issue:** `gbkt-backend-gbdk-0.1.0-SNAPSHOT.jar` had `Unexpected end of ZLIB input stream` — stale mavenLocal
- **Fix:** Re-ran `./gradlew pluginTest` which republishes 7 modules to mavenLocal first; second run passed
- **Commit:** N/A (no code change needed)

**6. [Rule 1 - Bug] IntegrationTest.kt not in test-fixtures scope**
- **Found during:** Task 2 pluginTest (second run)
- **Issue:** `IntegrationTest.kt` had 8 actual `whenever(` call sites and 1 comment — test-fixtures scope in the plan covered only the `.kt` fixture files in `test/resources/test-fixtures/`, not the test itself
- **Fix:** Added `gbkt-gradle-plugin/src/test/kotlin` to replacement; ran spotlessApply
- **Files modified:** `IntegrationTest.kt`
- **Commit:** `08acf999`

## Known Stubs

None — this plan performs a pure rename; no data plumbing or UI involved.

## Threat Flags

None — pure DSL surface rename. No new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Self-Check: PASSED

- `a27c7eed` — confirmed in git log (feat(18-01): remove whenever overloads...)
- `08acf999` — confirmed in git log (feat(18-01): migrate all in-tree whenever call sites...)
- `ScriptBuilder.kt` — `fun.*whenever` pattern absent
- `ActorPoolBuilder.kt` — `fun ScriptBuilder.runIf` present
- `grep -rn --include="*.kt" "whenever(" gbkt-examples gbkt-backend-gbdk/src/test gbkt-lang/src/test gbkt-gradle-plugin/src/test/resources` — zero matches verified
- `./gradlew build pluginTest` — BUILD SUCCESSFUL
