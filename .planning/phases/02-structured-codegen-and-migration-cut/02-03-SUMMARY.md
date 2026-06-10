---
phase: 02-structured-codegen-and-migration-cut
plan: 03
subsystem: codegen
tags: [kotlin, ir, ast, visitor, codegen, gbdk]

# Dependency graph
requires:
  - phase: 02-01
    provides: C AST sealed hierarchy (CExpr, CStatement, CFunction, CVarDecl, CDefine)
  - phase: 01-ir-foundation-and-dsl
    provides: IR v2 types (Expr, ScriptOp, SceneIR, ActorIR, BinaryOp, UnaryOp, AssignOp)
provides:
  - ExprVisitor: all 9 Expr subtypes → typed CExpr nodes with sanitized GBDK variable names
  - ScriptOpVisitor: 7 Pong ScriptOps + RawOp → typed CStatement nodes, else → CRawCode TODO
  - SceneVisitor: SceneIR → List<CFunction> with enter/frame/exit lifecycle naming
  - ActorVisitor: ActorIR → List<CVarDecl> for actor position state variables
  - SceneVisitor.generateSceneEnum(): scene ID list → SCENE_ prefixed CDefine constants
affects:
  - 02-04 (Pong codegen integration uses these visitors)
  - Any future codegen plans that translate IR to C AST

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Visitor pattern for IR→AST translation (object with visit() function per domain)
    - Exhaustive sealed when matching — no else branches except for fallback TODO
    - GBDK variable naming convention: underscore prefix, dots→underscores (_ball_x)
    - TDD RED→GREEN→REFACTOR cycle with per-phase commit per TDD step

key-files:
  created:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitorTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitorTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitorTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitorTest.kt
  modified: []

key-decisions:
  - "ExprVisitor.sanitizeVarName() is internal (not private) — ScriptOpVisitor calls it for actor ID sanitization in SetPosition/MoveBy, avoiding duplicate sanitization logic"
  - "MoveBy skips zero Literal offsets at visitor level — avoids emitting no-op += 0 assignments in generated C, keeping output clean"
  - "SceneVisitor sectionComment only on enter function — frame/exit functions don't repeat the block separator, matches GBDK style"
  - "ScriptOpVisitor else → CRawCode TODO fallback — prevents scope creep; unneeded ScriptOp types stay compilable without blocking Pong"
  - "ActorVisitor uses CU8 for position types — matches GBDK native coordinate range 0-255, consistent with Game Boy hardware"

patterns-established:
  - "Visitor objects use exhaustive sealed when on input type, returning typed output — no string concatenation"
  - "GBDK C variable naming: _prefix + dot-sanitized name (e.g. ball.x → _ball_x)"
  - "Scene lifecycle functions named {id}_enter, {id}_frame, {id}_exit — all isBanked=true"
  - "Scene defines use SCENE_{ID_UPPERCASE} format for navigate_to_scene() calls"

requirements-completed:
  - CGEN-05

# Metrics
duration: 6min
completed: 2026-02-18
---

# Phase 2 Plan 03: IR-to-AST Visitor Layer Summary

**Four domain visitors translate IR v2 Expr/ScriptOp/SceneIR/ActorIR to typed C AST CExpr/CStatement/CFunction/CVarDecl nodes with zero string output**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-18T06:21:47Z
- **Completed:** 2026-02-18T06:27:47Z
- **Tasks:** 2 (each with TDD RED→GREEN cycle)
- **Files created:** 8 (4 visitors + 4 test files)

## Accomplishments
- ExprVisitor converts all 9 Expr sealed subtypes to CExpr nodes; 18 BinaryOp + 3 UnaryOp mappings exhaustive
- ScriptOpVisitor handles Pong's 7 ScriptOp types (Assign, IfOp, SetPosition, MoveBy, NavigateTo, PrintOp, FadeOp) plus RawOp passthrough; else → CRawCode TODO for all others
- SceneVisitor converts SceneIR to List<CFunction> with {id}_enter/{id}_frame/{id}_exit naming, isBanked=true on all functions, sectionComment on enter only; generateSceneEnum() produces SCENE_ prefixed defines
- ActorVisitor converts ActorIR to _actorId_x / _actorId_y CVarDecl position variables with CU8 type and initial values from PositionDef
- Zero string concatenation in any visitor — all outputs are typed C AST nodes

## Task Commits

Each task was committed atomically with TDD RED→GREEN:

1. **Task 1 RED: ExprVisitor + ScriptOpVisitor tests** - `8735a24` (test)
2. **Task 1 GREEN: ExprVisitor + ScriptOpVisitor implementation** - `5f4dbd7` (feat)
3. **Task 2 RED: SceneVisitor + ActorVisitor tests** - `1b9d80c` (test)
4. **Task 2 GREEN: SceneVisitor + ActorVisitor implementation** - `ca44a3c` (feat)

## Files Created/Modified

- `gbkt-backend-gbdk/src/main/kotlin/.../visitor/ExprVisitor.kt` - Expr → CExpr visitor with sanitizeVarName and binaryOpToC/unaryOpToC mappings
- `gbkt-backend-gbdk/src/main/kotlin/.../visitor/ScriptOpVisitor.kt` - ScriptOp → CStatement visitor for 7 Pong op types + RawOp + TODO fallback
- `gbkt-backend-gbdk/src/main/kotlin/.../visitor/SceneVisitor.kt` - SceneIR → List<CFunction> lifecycle visitor + generateSceneEnum()
- `gbkt-backend-gbdk/src/main/kotlin/.../visitor/ActorVisitor.kt` - ActorIR → List<CVarDecl> position variable visitor
- `gbkt-backend-gbdk/src/test/kotlin/.../visitor/ExprVisitorTest.kt` - 11 tests covering all Expr subtypes + all 18 BinaryOp values
- `gbkt-backend-gbdk/src/test/kotlin/.../visitor/ScriptOpVisitorTest.kt` - 13 tests covering all 7 Pong ScriptOp types + RawOp + TODO fallback
- `gbkt-backend-gbdk/src/test/kotlin/.../visitor/SceneVisitorTest.kt` - 11 tests covering lifecycle functions, naming, isBanked, sectionComment, generateSceneEnum
- `gbkt-backend-gbdk/src/test/kotlin/.../visitor/ActorVisitorTest.kt` - 5 tests covering position variables, multi-actor, ID sanitization

## Decisions Made

- `ExprVisitor.sanitizeVarName()` is `internal` (not `private`) — ScriptOpVisitor needs it for actor IDs in SetPosition and MoveBy to avoid duplicating sanitization logic
- `MoveBy` skips zero `Literal` offsets at the visitor level — avoids emitting `_ball_x += 0;` no-op assignments
- `SceneVisitor` puts `sectionComment` only on the enter function — frame/exit reuse the same section block, avoiding repeated comment headers
- `ScriptOpVisitor` uses `else -> CRawCode("/* TODO: ${op::class.simpleName} */")` for unimplemented ScriptOps — keeps Pong compilable without implementing RPG/camera/audio ops

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Smart cast compilation error in ScriptOpVisitor.visitPrintOp**
- **Found during:** Task 1 (ScriptOpVisitor implementation, GREEN phase)
- **Issue:** `op.position.x` and `op.position.y` failed smart cast — Kotlin compiler rejects smart cast on public API properties from different modules
- **Fix:** Captured `val position = op.position` as a local variable before the null check, enabling smart cast on the local val
- **Files modified:** ScriptOpVisitor.kt
- **Verification:** Compiled and all tests pass
- **Committed in:** 5f4dbd7 (Task 1 GREEN commit)

---

**Total deviations:** 1 auto-fixed (1 bug — Kotlin smart cast restriction)
**Impact on plan:** Minimal fix required for cross-module null safety. No scope creep.

## Issues Encountered

- Stubs for all 4 visitors were pre-created by plan 02-02 (to allow CEmitter tests to compile). This made the RED phase detection by compilation error still valid but slightly different — "method not implemented" rather than "class not found". The tests still failed in RED as expected.

## Next Phase Readiness
- All 4 visitors are ready for plan 02-04 (Pong codegen integration)
- SceneVisitor.generateSceneEnum() is ready to produce #define constants for the game header
- ScriptOpVisitor.visit() and ExprVisitor.visit() compose correctly — ScriptOpVisitor delegates expression translation to ExprVisitor

## Self-Check: PASSED

All 8 files verified to exist on disk. All 4 task commits verified in git log:
- `8735a24` test(02-03): ExprVisitorTest + ScriptOpVisitorTest RED
- `5f4dbd7` feat(02-03): ExprVisitor + ScriptOpVisitor GREEN
- `1b9d80c` test(02-03): SceneVisitorTest + ActorVisitorTest RED
- `ca44a3c` feat(02-03): SceneVisitor + ActorVisitor GREEN

---
*Phase: 02-structured-codegen-and-migration-cut*
*Completed: 2026-02-18*
