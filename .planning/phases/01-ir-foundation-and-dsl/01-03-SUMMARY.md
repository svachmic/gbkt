---
phase: 01-ir-foundation-and-dsl
plan: 03
subsystem: dsl
tags: [kotlin, dsl, acceptance-test, bom-architecture, genre-package, rpg, example-games]

# Dependency graph
requires:
  - phase: 01-02
    provides: "game() builder DSL with ScriptBuilder, ActorBuilder, SceneBuilder, RefRegistry, variable delegates"
  - phase: 01-04
    provides: "gbkt-rpg module with character{}/monster{}/simpleBattle{}/battleUpdate() GameBuilder extensions"
provides:
  - "PongV2.kt: Pong game in v2 DSL (3 scenes, 3 actors, 4 variables, core-only)"
  - "BreakoutV2.kt: Breakout game in v2 DSL (4 scenes, 2 actors, 5 variables, sound effects, core-only)"
  - "ExplorerV2.kt: Explorer game in v2 DSL (5 scenes, 1 actor, camera, save, gbkt-rpg combat)"
  - "PongIRTest.kt: 19 IR validation tests for Pong"
  - "BreakoutIRTest.kt: 20 IR validation tests for Breakout"
  - "ExplorerIRTest.kt: 16 IR validation tests for Explorer (including BOM constraint tests)"
  - "BOM genre package pattern proven: Explorer depends on both gbkt-core and gbkt-rpg"
  - "RPG builders produce only core IR types — sealed hierarchy not extended by genre packages"
affects:
  - 02-backend-codegen (backend must handle all ScriptOp types present in these game IRs)
  - future genre packages (established the extension pattern proven by gbkt-rpg in Explorer)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wildcard import pattern for dsl.v2 package to include extension operators (plus, minus, isAbove, etc.) — operator extensions require package import or explicit import of extension function names"
    - "setPosition(actorId, Expr, Expr) overload must use literal() for mixed Int/Expr arguments — mixing raw Int with varRef() Expr triggers 'None of following candidates applicable' compile error"
    - "BOM opt-in pattern: genre packages not in gbkt-bom constraints; game modules add explicit implementation(project(':gbkt-rpg')) to opt in"

key-files:
  created:
    - gbkt-examples/pong/src/main/kotlin/io/github/gbkt/examples/pong/PongV2.kt
    - gbkt-examples/breakout/src/main/kotlin/io/github/gbkt/examples/breakout/BreakoutV2.kt
    - gbkt-examples/explorer/src/main/kotlin/io/github/gbkt/examples/explorer/ExplorerV2.kt
    - gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongIRTest.kt
    - gbkt-examples/breakout/src/test/kotlin/io/github/gbkt/examples/breakout/BreakoutIRTest.kt
    - gbkt-examples/explorer/src/test/kotlin/io/github/gbkt/examples/explorer/ExplorerIRTest.kt
  modified:
    - gbkt-examples/explorer/build.gradle.kts

key-decisions:
  - "Wildcard import 'import io.github.gbkt.core.dsl.v2.*' used in game definition files — operator extension functions (plus, minus, etc.) on Expr must be in scope; explicit named imports would miss operator functions"
  - "literal() wrapping required for setPosition() mixed Int/Expr calls — Kotlin cannot resolve overload when one arg is Int and another is Expr; both must be same type (all Expr or all Int)"
  - "gbkt-rpg NOT added to gbkt-bom constraints — BOM only coordinates gbkt-core, gbkt-backend-api, gbkt-backend-gbdk; genre packages are opt-in per game; Explorer uses explicit implementation(project(':gbkt-rpg'))"
  - "Brick entity pool limitation documented in BreakoutV2 comment — v2 DSL doesn't have entity pools yet; brick tracking via bricksLeft counter variable with zone logic comment"

patterns-established:
  - "DSL import pattern: use wildcard import for dsl.v2 package to avoid missing operator extension imports"
  - "BOM opt-in pattern: genre packages added explicitly to game modules, not via BOM"
  - "Expr/Int mixing: use literal() consistently when mixing Expr and Int in the same function call"

requirements-completed: [DSL-04]

# Metrics
duration: 8min
completed: 2026-02-17
---

# Phase 1 Plan 03: Example Games Summary

**Pong, Breakout, and Explorer v2 DSL definitions proving the new IR/DSL pipeline — Explorer uses gbkt-rpg for turn-based combat, proving BOM genre package separation without extending the sealed IR hierarchy**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-17T20:57:24Z
- **Completed:** 2026-02-17T21:05:32Z
- **Tasks:** 2
- **Files modified:** 7 (6 production/test, 1 config)

## Accomplishments

- Three example game DSL definitions serve as end-to-end acceptance tests for the v2 IR and DSL pipeline
- Pong and Breakout prove the core-only path: no genre packages needed, pure v2 DSL
- Explorer proves the BOM genre package pattern: `gbkt-rpg` extends `GameBuilder` with `character{}`, `monster{}`, `simpleBattle{}` and `ScriptBuilder` with `battleUpdate()`, all producing core IR types
- 55 total IR validation tests verify the sealed hierarchy constraint: all `ScriptOp` types in Explorer's IR qualify as `io.github.gbkt.core.ir.v2.*` — gbkt-rpg adds zero sealed IR subtypes
- All 1592 existing core tests and 17 RPG tests pass with zero regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Write Pong and Breakout example games with IR validation tests** - `4a9f3a3` (feat)
2. **Task 2: Write Explorer example game using gbkt-rpg for combat with IR validation tests** - `63d83fa` (feat)

## Files Created/Modified

**Production (3 files):**
- `gbkt-examples/pong/src/main/kotlin/io/github/gbkt/examples/pong/PongV2.kt` — Pong: title/game/gameover scenes, paddle1/paddle2/ball actors, p1Score/p2Score/ballDx/ballDy variables, D-pad + AI movement, scoring, win condition
- `gbkt-examples/breakout/src/main/kotlin/io/github/gbkt/examples/breakout/BreakoutV2.kt` — Breakout: title/game/gameover/win scenes, paddle/ball actors, score/lives/bricksLeft/ballDx/ballDy variables, 4 sound effects, ball physics, win/lose conditions
- `gbkt-examples/explorer/src/main/kotlin/io/github/gbkt/examples/explorer/ExplorerV2.kt` — Explorer: title/gameplay/pause/combat_scene/gameover scenes, player actor, camera + save systems, RPG combat via gbkt-rpg DSL builders

**Tests (3 files):**
- `gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongIRTest.kt` — 19 tests: scene count, actor count, variable types (U8/I8), positions, sprites, no RPG systems
- `gbkt-examples/breakout/src/test/kotlin/io/github/gbkt/examples/breakout/BreakoutIRTest.kt` — 20 tests: scene count, actor count, variable types, sound effects, no RPG systems
- `gbkt-examples/explorer/src/test/kotlin/io/github/gbkt/examples/explorer/ExplorerIRTest.kt` — 16 tests: camera/save systems, GenericSystem from simpleBattle, TriggerSystem from battleUpdate, core-only ScriptOp assertion

**Config (1 file):**
- `gbkt-examples/explorer/build.gradle.kts` — Added `implementation(project(":gbkt-rpg"))` with BOM opt-in comment

## Decisions Made

- **Wildcard import for dsl.v2 package:** Operator extension functions (`plus`, `minus`, `isAbove`, `isBelow`, etc.) on `Expr` are defined as top-level extensions in the dsl.v2 package. Using explicit imports would require listing every operator. Wildcard import `import io.github.gbkt.core.dsl.v2.*` brings all of them into scope cleanly.

- **`literal()` wrapping for setPosition() mixed calls:** The `setPosition(actorId, Expr, Expr)` and `setPosition(actorId, Int, Int)` overloads cannot resolve when mixing raw `Int` values with `Expr` from `varRef()`. Fixed by wrapping constants in `literal()` to make all arguments `Expr`.

- **gbkt-rpg not in BOM:** gbkt-bom coordinates only the core platform modules (gbkt-core, gbkt-backend-api, gbkt-backend-gbdk). Genre packages are deliberately opt-in — games that don't need RPG mechanics shouldn't pull in RPG code. Explorer adds `implementation(project(":gbkt-rpg"))` explicitly.

- **Brick entity pool limitation documented:** Breakout's brick grid requires entity pooling (spawn many identical brick actors from a pool). This isn't in the v2 DSL yet (planned for Phase 2 codegen or a later DSL extension). The workaround uses a `bricksLeft` counter variable — documented with a comment in the file.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Used wildcard import instead of named imports for operator extensions**
- **Found during:** Task 1 (PongV2.kt compilation)
- **Issue:** `varRef("ball.x") + varRef("ballDx")` failed with "Unresolved reference 'plus' for operator '+'" — the `plus` extension on `Expr` wasn't in scope via explicit named imports
- **Fix:** Changed from explicit named imports to `import io.github.gbkt.core.dsl.v2.*` wildcard — brings all extension operators into scope automatically
- **Files modified:** `PongV2.kt`, `BreakoutV2.kt`, `ExplorerV2.kt`
- **Verification:** All three files compile successfully; operator expressions resolve correctly
- **Committed in:** `4a9f3a3`, `63d83fa` (Task 1 and 2 commits)

**2. [Rule 1 - Bug] Used literal() for setPosition() mixed Int/Expr arguments**
- **Found during:** Task 2 (ExplorerV2.kt compilation)
- **Issue:** `setPosition(player.id, 8, varRef("player.y"))` failed — Kotlin cannot resolve overload when first arg is `Int` and second is `Expr`
- **Fix:** Wrapped all Int constants in `literal()` to make arguments uniformly `Expr`: `setPosition(player.id, literal(8), varRef("player.y"))`
- **Files modified:** `ExplorerV2.kt`
- **Verification:** Compiles successfully; all ExplorerIRTest tests pass
- **Committed in:** `63d83fa` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 — API usage bugs discovered at compile time)
**Impact on plan:** Both fixes are pure compile-time correctness fixes. No scope change. The observable DSL behavior is exactly as the plan specified.

## Issues Encountered

- Kotlin operator extension functions require either wildcard import or explicit import of each operator function name separately. Since the dsl.v2 package has many operator extensions (plus, minus, times, div, isAbove, isBelow, isAtLeast, isAtMost, isEqualTo, etc.), wildcard import is the practical solution.
- The `setPosition` overload resolution issue is a Kotlin type inference limitation — mixed Int/Expr calls need all arguments to be the same type.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All three example games are complete v2 DSL definitions that produce valid GameIR
- Phase 01 is fully complete — all 4 plans executed (IR hierarchy, DSL builders, example games, RPG genre package)
- Phase 02 (GBDK backend codegen) can now consume `GameIR` from any of these three games to drive code generation development
- The `PongV2.kt` and `BreakoutV2.kt` games serve as the simplest codegen targets (core-only)
- The `ExplorerV2.kt` game serves as the complex codegen target (camera, save, RPG combat GenericSystem)
- Entity pool support for Breakout's brick grid is deferred — needs design in Phase 02 or a later DSL extension plan

---
*Phase: 01-ir-foundation-and-dsl*
*Completed: 2026-02-17*

## Self-Check: PASSED

- All 7 files verified to exist on disk (6 production/test + SUMMARY)
- Both task commits verified in git log: `4a9f3a3` (Task 1) and `63d83fa` (Task 2)
- All tests pass: Pong (19), Breakout (20), Explorer (16) — 55 total IR validation tests
- Zero regressions: `./gradlew :gbkt-core:test :gbkt-rpg:test` BUILD SUCCESSFUL
