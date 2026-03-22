---
phase: 01-ir-foundation-and-dsl
plan: 02
subsystem: dsl
tags: [kotlin, dsl, builder-pattern, ref-resolution, property-delegates, thread-local]

# Dependency graph
requires:
  - phase: 01-01
    provides: "Sealed IR v2 hierarchy (GameIR, SceneIR, ActorIR, ScriptOp, Expr, SystemIR, Types)"
provides:
  - "game() top-level DSL entry point producing GameIR"
  - "SceneBuilder with enter/frame/exit lifecycle handler recording"
  - "ActorBuilder with position, sprite (AssetRef), hitbox configuration"
  - "ScriptBuilder with methods for all 24 ScriptOp types"
  - "RefRegistry: two-stage ref resolution with Did you mean? suggestions"
  - "Variable property delegates: u8Var/u16Var/i8Var/i16Var registering VariableDef in GameIR"
  - "ExprBuilder: literal/varRef/stringLiteral + arithmetic/comparison operator extensions on Expr"
  - "SystemBuilders: CameraBuilder, SaveDataBuilder, ExplorationBuilder, SoundEffectBuilder"
  - "DSLValidationError with compiler-style error format"
  - "GameBuilderContext thread-local for variable delegate registration"
affects:
  - 01-03-PLAN (example game definitions using v2 DSL)
  - future backend codegen (consumes GameIR)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Thread-local context pattern (GameBuilderContext) for cross-scope registration without passing refs"
    - "Two-stage DSL validation: recording time (duplicates, immediate errors) + build() time (ref resolution)"
    - "@GbktDsl DSL marker on all builder classes to prevent implicit receiver leakage"
    - "Top-level fun asset() for AssetRef creation (bypasses @GbktDsl scope restriction)"
    - "ReadWriteProperty<Any?> for var delegates with thread-local for typed registration"
    - "Suggestions.formatSuggestion() reused for Did you mean? in RefRegistry and GameBuilder"
    - "TDD: RED tests committed before GREEN implementation"

key-files:
  created:
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/GameBuilder.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/SceneBuilder.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ActorBuilder.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ScriptBuilder.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/RefRegistry.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/AssetRegistry.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/SystemBuilders.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/VariableBuilders.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ExprBuilder.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/DslMarkers.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/Errors.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/dsl/v2/GameBuilderTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/dsl/v2/RefRegistryTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/dsl/v2/ScriptBuilderTest.kt
  modified: []

key-decisions:
  - "GameBuilderContext uses ThreadLocal<GameBuilder?> for variable delegate registration — same pattern as v1 RecordingContext; avoids threading typed receiver through all delegate call sites"
  - "VarDelegate implements ReadWriteProperty<Any?> with no-op setValue — allows 'var score by u8Var(0)' DSL syntax while keeping registration correct; setValue is intentionally a no-op (mutations use ScriptBuilder.assign)"
  - "asset() is a top-level function not a GameBuilder member — @GbktDsl on ActorBuilder blocks GameBuilder methods from being called in actor{} scope; top-level functions have no receiver and are not subject to DSL marker restrictions"
  - "RefRegistry tracks pending refs separately from registered IDs — resolveAll() validates at build() time, not at ref() time, so DSL recording can proceed in any order"
  - "ScriptBuilder is pure list accumulator (no thread-local) — lifecycle handlers (enter/frame/exit) create their own ScriptBuilder instances, so no state leakage between handlers"

patterns-established:
  - "GameBuilderContext thread-local pattern: set in game() top-level function, read in variable delegates"
  - "DSL scope safety pattern: @GbktDsl on all builder classes; top-level helpers for cross-scope access"
  - "Two-stage validation pattern: record-time for structure (duplicates), build()-time for semantics (refs)"
  - "Kotlin sealed interface delegation constraint: AssignableVar cannot implement Expr (cross-package seal); use toExpr() method instead"

requirements-completed: [IR-03, DSL-01, DSL-02, DSL-03]

# Metrics
duration: 9min
completed: 2026-02-17
---

# Phase 1 Plan 02: DSL Builders Summary

**game() builder DSL producing GameIR via RefRegistry ref resolution, ScriptBuilder op recording, and u8Var/i8Var/u16Var/i16Var property delegates with thread-local registration**

## Performance

- **Duration:** 9 min
- **Started:** 2026-02-17T20:34:27Z
- **Completed:** 2026-02-17T20:43:35Z
- **Tasks:** 2 (TDD: 1 RED + 1 GREEN)
- **Files modified:** 14 (11 production, 3 test)

## Accomplishments

- Full DSL recording layer for the v2 IR — `game() { scene() { enter { } ; frame { } } ; start = "scene" }.build()` produces valid `GameIR`
- `RefRegistry` validates all refs at `build()` time with Levenshtein "Did you mean?" suggestions via existing `Suggestions.kt`
- Variable delegates (`u8Var`, `u16Var`, `i8Var`, `i16Var`) capture property names via `provideDelegate` and register `VariableDef` instances through `GameBuilderContext` thread-local
- `ScriptBuilder` accumulates all 24 `ScriptOp` subtypes with input helpers (`buttonPressed`, `dpadHeld`) that produce `CallExpr` conditions
- `ExprBuilder` provides `literal()`, `varRef()`, `stringLiteral()` plus arithmetic and comparison infix operator extensions on `Expr`
- All 1592 tests pass across 114 test suites — zero regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: RED — failing tests for DSL builders, ref resolution, variable recording** - `f9b6d95` (test)
2. **Task 2: GREEN — DSL builders, RefRegistry, and variable recording implementation** - `3ce4e73` (feat)

_TDD plan: RED commit before GREEN implementation_

## Files Created/Modified

**Production (11 files):**
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/GameBuilder.kt` — Top-level `game()` entry point; produces `GameIR` with two-stage validation
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/SceneBuilder.kt` — `SceneRef` type + `enter/frame/exit` handler recording
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ActorBuilder.kt` — `ActorRef` type + `position/sprite/hitbox` builder; `SpriteBuilder` nested
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ScriptBuilder.kt` — Records all 24 `ScriptOp` subtypes; `whenever` sugar; input condition helpers
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/RefRegistry.kt` — Two-stage ref resolution with `Suggestions.formatSuggestion()` for "Did you mean?"
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/AssetRegistry.kt` — Asset tracking; top-level `asset()` function for cross-scope use
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/SystemBuilders.kt` — `CameraBuilder`, `SaveDataBuilder`, `ExplorationBuilder`, `SoundEffectBuilder`, `ConfigBuilder`
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/VariableBuilders.kt` — `VarDelegate` + `U8/U16/I8/I16VarDelegate`; `GameBuilderContext` thread-local; top-level `u8Var/u16Var/i8Var/i16Var`
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ExprBuilder.kt` — `literal/varRef/stringLiteral` factories; arithmetic/comparison/logical operator extensions on `Expr`
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/DslMarkers.kt` — `@GbktDsl` DSL marker annotation
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/Errors.kt` — `DSLValidationError` with compiler-style format

**Tests (3 files):**
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/dsl/v2/GameBuilderTest.kt` — 17 tests for `game()`, actors, scenes, systems, variables, validation
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/dsl/v2/RefRegistryTest.kt` — 10 tests for registration, resolution, "Did you mean?", error format
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/dsl/v2/ScriptBuilderTest.kt` — 16 tests for all ScriptOp recording methods, expr helpers

## Decisions Made

- **GameBuilderContext thread-local for variable delegate registration:** Kotlin local variable delegates receive `Any?` as `thisRef` in `provideDelegate` (not the enclosing class), so a typed receiver cannot be passed. Used a `ThreadLocal<GameBuilder?>` set by the `game()` top-level function — the same pattern the v1 DSL uses for `GameScopeContext`.

- **`asset()` as top-level function:** `@GbktDsl` on `ActorBuilder` blocks calling `GameBuilder.asset()` from inside `actor {}` blocks (Kotlin's DSL marker prevents implicit outer receiver access). Making `asset()` a top-level function has no receiver, so it's not blocked. `GameBuilder.asset()` member retained for completeness but top-level is the recommended call site.

- **`ReadWriteProperty<Any?>` for `var` delegates:** Plan specified `var score by u8Var(0)` syntax (using `var`). Kotlin requires `ReadWriteProperty` for `var` delegation. The `setValue` is a no-op — variable mutations in game scripts use `ScriptBuilder.assign()`, not property assignment on the delegate.

- **`ScriptBuilder` as pure accumulator:** No thread-local needed — each lifecycle handler (`enter`, `frame`, `exit`) creates its own `ScriptBuilder` instance. State is local, no leakage between handlers.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Used top-level `asset()` function instead of `GameBuilder.asset()` member**
- **Found during:** Task 2 (GREEN implementation)
- **Issue:** `@GbktDsl` on `ActorBuilder` blocks `GameBuilder.asset()` from being called inside `actor {}` blocks. The plan specified `sprite(asset("..."))` inside actor blocks, but the member method `asset()` is on `GameBuilder` with `@GbktDsl` preventing implicit receiver access.
- **Fix:** Made `asset()` a top-level function in `AssetRegistry.kt` returning `AssetRef`. The top-level function has no DSL receiver and is not subject to marker restrictions. `GameBuilder.asset()` member is retained for symmetry but the top-level function is the practical call site.
- **Files modified:** `AssetRegistry.kt`, `GameBuilder.kt`
- **Verification:** `asset("sprites/player.png")` resolves correctly inside `actor {}` blocks; all tests pass
- **Committed in:** `3ce4e73` (Task 2 feat commit)

**2. [Rule 2 - Missing Critical] Added GameBuilderContext thread-local for variable delegate registration**
- **Found during:** Task 2 (GREEN implementation — compile error)
- **Issue:** Plan specified `var score by u8Var(0)` inside `game {}` lambda. Kotlin local variable delegates use `Any?` as `thisRef` in `provideDelegate` — the enclosing `GameBuilder` receiver is not passed. Without a mechanism to reach the builder, `VariableDef` registration would silently fail.
- **Fix:** Added `GameBuilderContext` object with `ThreadLocal<GameBuilder?>`. The `game()` top-level function calls `GameBuilderContext.with(builder) { builder.block() }`. `VarDelegate.provideDelegate` reads `GameBuilderContext.current` to register the variable.
- **Files modified:** `VariableBuilders.kt`, `GameBuilder.kt`
- **Verification:** `ir.variables.find { it.name == "score" }` returns non-null; `VarType.U8`, `initialValue=0` correct. All 4 delegate tests pass.
- **Committed in:** `3ce4e73` (Task 2 feat commit)

---

**Total deviations:** 2 auto-fixed (1 Rule 1 — bug in API design assumption, 1 Rule 2 — missing critical for correctness)
**Impact on plan:** Both fixes are direct consequences of Kotlin's @DslMarker semantics and local delegate limitations. They don't change the observable API surface — the DSL syntax works exactly as specified in the plan.

## Issues Encountered

- Kotlin sealed interface constraint (cross-package): `AssignableVar` cannot implement `Expr` via delegation because `Expr` is sealed in `ir.v2` package. Resolved by making `AssignableVar` a plain data class with a `toExpr()` method.
- Test imports: Initial tests used `org.junit.jupiter.*` imports (JUnit 5 directly), but the project uses `kotlin.test.*` only. Updated all test files to use `kotlin.test.assertFailsWith` instead of `assertThrows`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- DSL v2 recording layer is the complete user-facing API
- Phase 01-03 (example game definitions) can now write Pong, Breakout, Explorer using `game {}` builder
- Phase 02 backend codegen consumes `GameIR` produced by `build()` — IR v2 hierarchy is now fully reachable from both sides

---
*Phase: 01-ir-foundation-and-dsl*
*Completed: 2026-02-17*

## Self-Check: PASSED

- All 15 files verified to exist on disk
- Both task commits verified in git log: `f9b6d95` (RED tests) and `3ce4e73` (GREEN implementation)
- All 1592 tests pass: `./gradlew :gbkt-core:test` BUILD SUCCESSFUL
