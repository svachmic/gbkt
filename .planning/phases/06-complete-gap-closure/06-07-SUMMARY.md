---
phase: 06-complete-gap-closure
plan: 07
subsystem: dsl
tags: [cast-expr, type-casting, palette-validation, array-helpers, sprite-frames, raw-warning, semantic-validation]

# Dependency graph
requires:
  - phase: 06-02
    provides: v2 package path promotion — all imports at io.github.gbkt.core.ir.* and io.github.gbkt.core.dsl.*
provides:
  - CastExpr IR node with toU8/toU16/toI8/toI16 DSL extension methods
  - CCast C AST node emitted by ExprVisitor.visitCast()
  - GBCColor.hasPrecisionLoss() RGB888→RGB555 quantization check
  - paletteStrictMode in AnalysisConfig with WARNING diagnostics from SemanticValidationPass
  - SemanticValidationPass raw() warning counting RawOp instances across all scenes
  - ArrayVar.fill/forEach/indexOf/count collection-like helpers emitting inline C loops
  - SpriteDef.frameWidth/frameHeight fields + SpriteBuilder.frameWidth()/frameHeight() DSL
  - ActorVisitor.generateFrameOffsetInit() generating set_<actorId>_frame(frame) functions
affects:
  - gbkt-backend-gbdk (ExprVisitor needs visitCast)
  - gbkt-analysis (SemanticValidationPass, AnalysisConfig)
  - gbkt-lang (ArrayVar, ScriptBuilder, ActorBuilder)
  - gbkt-core (ScriptOpInterpreter CastExpr evaluation)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CastExpr wraps inner Expr with VarType target — accept() dispatches to visitCast()"
    - "ArrayVar helpers use ScriptBuilderContext.current to emit ForOp IR without explicit receiver"
    - "Temp variable naming convention: _arr_<name>_i (loop index), _arr_<name>_idx (indexOf result), _arr_<name>_cnt (count result)"
    - "generateFrameOffsetInit: absent frameWidth returns emptyList(); present frameWidth generates set_<actorId>_frame(frame: UINT8)"

key-files:
  created:
    - gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/ArrayVarHelpersTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitorTest.kt (frame layout tests added)
  modified:
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Expr.kt (CastExpr added)
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ExprVisitorI.kt (visitCast method)
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/CoreTypes.kt (GBCColor.hasPrecisionLoss helpers)
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Types.kt (SpriteDef frameWidth/frameHeight)
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ExprBuilder.kt (toU8/toU16/toI8/toI16)
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt (internal emit() method)
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt (ArrayVar helpers)
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt (SpriteBuilder frameWidth/frameHeight)
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt (visitCast)
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt (public emitStatement overload)
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt (generateFrameOffsetInit)
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/config/AnalysisConfig.kt (paletteStrictMode)
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt (raw warning + palette check)
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt (CastExpr evaluation)

key-decisions:
  - "CastExpr uses VarType (U8/U16/I8/I16) not a C-type — IR remains platform-agnostic; ExprVisitor maps to CCast(CU8/CU16/CI8/CI16)"
  - "ArrayVar helpers use ScriptBuilderContext.current (thread-local) not explicit ScriptBuilder parameter — consistent with how operator extensions on AssignableVar/ActorPropertyRef work"
  - "ScriptBuilder.emit(op) is internal (not public) — only ArrayVar helpers and module-internal code should call it; public API remains DSL methods"
  - "generateFrameOffsetInit returns emptyList when frameWidth is null — enables gradual adoption; existing actors without frameWidth are unaffected"
  - "SemanticValidationPass raw() warning is WARNING not ERROR — allows legacy raw() usage while encouraging migration; matches palette strict mode severity"
  - "ArrayVarHelpersTest lives in gbkt-lang (not gbkt-core) — ScriptBuilderContext is internal to gbkt-lang; tests using it must be in same module"

patterns-established:
  - "CastExpr pattern: IR node wraps inner Expr, ExprVisitor emits CCast, DSL provides .toU8/.toU16/.toI8/.toI16 extensions"
  - "Array helper pattern: ForOp-based loops with auto-named temp variables (_arr_<name>_i)"
  - "Frame layout pattern: generateFrameOffsetInit() generates set_<actorId>_frame(frame: UINT8) C function when frameWidth set"

requirements-completed: [DSL-E1, DSL-E2, DSL-E3, DSL-E4, DSL-E5, DSL-E6]

# Metrics
duration: 35min
completed: 2026-02-21
---

# Phase 06 Plan 07: DSL Ergonomics Completions Summary

**CastExpr type casting (toU8/toU16/toI8/toI16), palette strict mode, raw() compiler warning, ArrayVar collection helpers (fill/forEach/indexOf/count), and sprite frame layout metadata (generateFrameOffsetInit)**

## Performance

- **Duration:** 35 min
- **Started:** 2026-02-21T11:54:00Z
- **Completed:** 2026-02-21T12:29:30Z
- **Tasks:** 2
- **Files modified:** 15+

## Accomplishments

- Implemented CastExpr IR node with ExprVisitor.visitCast() generating `(UINT8)`, `(UINT16)`, `(INT8)`, `(INT16)` C casts; DSL toU8/toU16/toI8/toI16 extensions on Expr/AssignableVar/ActorPropertyRef
- Added GBCColor.hasPrecisionLoss() helpers and palette strict mode in SemanticValidationPass with ANLZ-06 WARNING diagnostics when RGB888→RGB555 quantization loses bits
- Added SemanticValidationPass raw() warning (ANLZ-05) counting RawOp instances across all scenes with count in diagnostic message
- Implemented ArrayVar collection-like helpers (fill, forEach, indexOf, count) that emit ForOp-based inline loops via ScriptBuilderContext; all backed by tests in gbkt-lang
- Added SpriteDef.frameWidth/frameHeight fields and SpriteBuilder.frameWidth()/frameHeight() DSL; ActorVisitor.generateFrameOffsetInit() generates `set_<actorId>_frame(frame: UINT8)` function for multi-frame sprites
- Added public CEmitter.emitStatement(stmt) overload (no indent parameter) to unblock ActorVisitorTest frame body assertions

## Task Commits

Each task was committed atomically:

1. **Task 1: CastExpr type casting, palette strict mode, and raw() warning** - `80f9b1c` (feat)
2. **Task 2: Array helpers and sprite frame layout** - `15ff5d2` (chore — VariableBuilders/ScriptBuilder/ActorBuilder/ActorVisitor), `b83600f` (chore — ActorVisitorTest), `29e33c5` (feat — ArrayVarHelpersTest in gbkt-lang)

_Note: Task 2 was committed across multiple previous session commits; the implementation was already in HEAD when this session verified it._

## Files Created/Modified

- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Expr.kt` - Added CastExpr data class (10th Expr subtype)
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ExprVisitorI.kt` - Added visitCast(CastExpr): T method
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/CoreTypes.kt` - Added GBCColor.hasPrecisionLoss(hex) and hasPrecisionLoss(r,g,b) helpers
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Types.kt` - Added frameWidth/frameHeight to SpriteDef
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ExprBuilder.kt` - Added toU8/toU16/toI8/toI16 extensions
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt` - Added internal emit(op: ScriptOp) method
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt` - Added fill/forEach/indexOf/count to ArrayVar
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt` - Added frameWidth/frameHeight to SpriteBuilder
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt` - visitCast() implementation
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt` - Public emitStatement(stmt) overload
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt` - generateFrameOffsetInit()
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/config/AnalysisConfig.kt` - paletteStrictMode: Boolean = false
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt` - checkRawOpUsage() + checkPalettePrecision()
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt` - CastExpr evaluation with truncation/sign-extension
- `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/ArrayVarHelpersTest.kt` - Tests for fill/forEach/indexOf/count
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitorTest.kt` - Frame layout tests for generateFrameOffsetInit

## Decisions Made

- CastExpr uses VarType (U8/U16/I8/I16) not a C-type — IR remains platform-agnostic; ExprVisitor maps to CCast(CU8/CU16/CI8/CI16)
- ArrayVar helpers use ScriptBuilderContext.current (thread-local) — consistent with how operator extensions on AssignableVar/ActorPropertyRef work; no explicit ScriptBuilder parameter threading needed
- ScriptBuilder.emit(op) is internal (not public) — only ArrayVar helpers and module-internal code should call it
- generateFrameOffsetInit returns emptyList when frameWidth is null — enables gradual adoption
- SemanticValidationPass raw() warning is WARNING not ERROR — allows legacy usage while encouraging migration
- ArrayVarHelpersTest lives in gbkt-lang (not gbkt-core) — ScriptBuilderContext is internal to gbkt-lang module

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added public CEmitter.emitStatement(stmt) overload**
- **Found during:** Task 2 (ActorVisitorTest frame body assertions)
- **Issue:** CEmitter.emitStatement(stmt, indent) was private; ActorVisitorTest needed to call emitStatement(CBlock(fn.body)) without indent parameter
- **Fix:** Added `fun emitStatement(stmt: CStatement): String = emitStatement(stmt, indent = 0)` public overload
- **Files modified:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt`
- **Verification:** ActorVisitorTest frame body assertions compile and pass
- **Committed in:** `15ff5d2` (part of Task 2 commits)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Auto-fix was necessary for test compilation. No scope creep.

## Issues Encountered

- Task 2 was found to already be committed by prior sessions (`15ff5d2`, `b83600f`, `29e33c5`) — detected via `git status` showing "nothing to commit" after re-implementing. Verified content matched expected implementation before proceeding.
- ArrayVarHelpersTest was initially written to `gbkt-core/src/test/` but ScriptBuilderContext is `internal` to `gbkt-lang` — test was correctly placed in `gbkt-lang/src/test/` by a previous session.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 6 DSL ergonomics completion directives (E1-E6) resolved
- CastExpr available for use in game code via score.toU16() syntax
- Palette strict mode available via AnalysisConfig(paletteStrictMode = true)
- ArrayVar helpers available in DSL: bricks.fill(0), bricks.forEach { e -> ... }, bricks.indexOf(Literal(1)), bricks.count(Literal(1))
- Sprite frame animation metadata available via sprite { frameWidth(16); frameHeight(16) }
- Phase 06 is complete (9/9 plans) — ready for Phase 06 closure

---
*Phase: 06-complete-gap-closure*
*Completed: 2026-02-21*

## Self-Check: PASSED

- FOUND: `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/ArrayVarHelpersTest.kt`
- FOUND: `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitorTest.kt`
- FOUND: `80f9b1c` (feat(06-07): implement CastExpr, palette strict mode, raw() warning)
- FOUND: `15ff5d2` (chore(06-06): apply spotless formatting and commit accumulated pre-existing changes — Task 2 implementation)
- FOUND: `b83600f` (chore(06-06): add ActorVisitorTest that was untracked)
- FOUND: `29e33c5` (feat(06-03): create gbkt-all module, fix test infra, move ArrayVarHelpersTest to gbkt-lang)
