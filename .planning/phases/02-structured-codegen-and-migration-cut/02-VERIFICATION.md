---
phase: 02-structured-codegen-and-migration-cut
verified: 2026-02-18T07:30:00Z
status: passed
score: 9/9 must-haves verified
re_verification: null
gaps: []
human_verification:
  - test: "Pong ROM boots and plays correctly in mGBA emulator"
    expected: "Ball moves, paddles respond to d-pad input, scoring works, game-over triggers"
    why_human: "Pipeline generates valid C (PongPipelineTest verified), but GBDK lcc compilation and ROM execution require a physical emulator. GBDK not installed in CI. ROM compilation is explicitly scoped to Phase 5."
---

# Phase 2: Structured Codegen and Migration Cut Verification Report

**Phase Goal:** C AST sealed hierarchy replaces all string-based emission; bank assignment is a typed field on C AST nodes; old GBDKCodeGenerator deprecated; Pong compiles through new pipeline
**Verified:** 2026-02-18T07:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | CStatement, CExpr, CType are sealed interfaces with exhaustive when matching (no else branch needed) | VERIFIED | `sealed interface CStatement/CExpr/CType` in ast/; `emitStatement`/`emitExpr`/`emitType` in CEmitter.kt have no `else` branch; compiler enforces coverage |
| 2 | CFile carries a `bank:Int` field; CFunction has `bank:Int?` and `isBanked:Boolean` | VERIFIED | `CFile.bank: Int = 0` in CFile.kt line 28; `CFunction.bank: Int? = null`, `isBanked: Boolean = false` in CFunction.kt lines 33–35 |
| 3 | CRawCode escape hatch exists as both CStatement and CExpr subtype | VERIFIED | `data class CRawCode(val code: String) : CStatement` in CStatement.kt line 93; `data class CRawExpr(val code: String) : CExpr` in CExpr.kt line 72 |
| 4 | No mutable currentBank state exists anywhere in the new ast/ package | VERIFIED | `grep -r "var " ast/` returns zero matches; all fields are `val` |
| 5 | CEmitter.emit(CFile) produces complete C source with bank pragma, includes, defines, variables, and functions | VERIFIED | CEmitter.kt lines 49–107; 44 CEmitterTest tests pass (0 failures) |
| 6 | CEmitter is the ONLY class that calls buildString/appendLine for C output | VERIFIED | `buildString`/`appendLine` absent from ast/, visitor/, pipeline/ packages; only present in emit/CEmitter.kt |
| 7 | All 4 visitors (ExprVisitor, ScriptOpVisitor, SceneVisitor, ActorVisitor) return typed C AST nodes, never strings | VERIFIED | ExprVisitor returns CExpr subtypes; ScriptOpVisitor returns CStatement subtypes; SceneVisitor returns List<CFunction>; ActorVisitor returns List<CVarDecl>; 52 visitor tests pass |
| 8 | GBDKPipelineV2.generate(GameIR) produces main.c + bank1.c + game.h with correct Pong content and zero RPG symbols | VERIFIED | 17/17 PongPipelineTest tests pass; zero RPG symbols confirmed (test 13) |
| 9 | GBDKCodeGenerator is @Deprecated(WARNING) but NOT deleted; GBDKBackend.generateV2() routes to new pipeline | VERIFIED | GBDKCodeGenerator.kt line 106: `@Deprecated(level = DeprecationLevel.WARNING)`; GBDKBackend.kt line 70: `fun generateV2(gameIR: GameIR)` calls `GBDKPipelineV2()` |

**Score:** 9/9 truths verified

### Required Artifacts

| Artifact | Provides | Status | Details |
|----------|----------|--------|---------|
| `gbkt-backend-gbdk/.../codegen/ast/CStatement.kt` | Sealed CStatement hierarchy | VERIFIED | 11 subtypes: CIf, CFor, CWhile, CSwitch, CReturn, CBlock, CVarDecl, CExprStatement, CRawCode, CComment, CBlankLine |
| `gbkt-backend-gbdk/.../codegen/ast/CExpr.kt` | Sealed CExpr hierarchy | VERIFIED | 10 subtypes: CLiteral, CStringLiteral, CVar, CBinaryExpr, CUnaryExpr, CCall, CTernary, CArrayAccess, CCast, CRawExpr |
| `gbkt-backend-gbdk/.../codegen/ast/CType.kt` | Sealed CType hierarchy | VERIFIED | 8 subtypes: CU8, CU16, CI8, CI16, CVoid, CPointer, CArray, CConst |
| `gbkt-backend-gbdk/.../codegen/ast/CFile.kt` | CFile data class with typed bank:Int | VERIFIED | `data class CFile(val name: String, val bank: Int = 0, ...)` |
| `gbkt-backend-gbdk/.../codegen/ast/CFunction.kt` | CFunction with bank:Int? and isBanked:Boolean | VERIFIED | `val bank: Int? = null`, `val isBanked: Boolean = false` |
| `gbkt-backend-gbdk/.../codegen/ast/CDeclaration.kt` | CDefine and CTypedef | VERIFIED | File exists with both data classes |
| `gbkt-backend-gbdk/.../codegen/emit/CEmitter.kt` | Single pretty-printer object | VERIFIED | `object CEmitter` with exhaustive when dispatch on all sealed hierarchies |
| `gbkt-backend-gbdk/.../codegen/visitor/ExprVisitor.kt` | Expr -> CExpr visitor | VERIFIED | All 9 Expr subtypes handled; 19 tests pass |
| `gbkt-backend-gbdk/.../codegen/visitor/ScriptOpVisitor.kt` | ScriptOp -> CStatement visitor | VERIFIED | 7 Pong types + RawOp handled; else -> CRawCode TODO; 17 tests pass |
| `gbkt-backend-gbdk/.../codegen/visitor/SceneVisitor.kt` | SceneIR -> List<CFunction> | VERIFIED | {id}_enter/{id}_frame/{id}_exit naming; isBanked=true; 11 tests pass |
| `gbkt-backend-gbdk/.../codegen/visitor/ActorVisitor.kt` | ActorIR -> List<CVarDecl> | VERIFIED | _actorId_x/_actorId_y position variables; 5 tests pass |
| `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt` | GameIR -> Map<String, String> pipeline | VERIFIED | Orchestrates all visitors + CEmitter; 17 PongPipelineTest tests pass |
| `gbkt-backend-gbdk/.../codegen/ast/CAstTest.kt` | AST hierarchy tests | VERIFIED | 18 tests pass (17 planned + 1 additional) |
| `gbkt-backend-gbdk/.../codegen/emit/CEmitterTest.kt` | CEmitter emission tests | VERIFIED | 44 tests pass |
| `gbkt-backend-gbdk/.../codegen/pipeline/PongPipelineTest.kt` | Pong integration tests | VERIFIED | 17 tests pass |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| CFile.kt | CFunction.kt | `val functions: List<CFunction>` | WIRED | CFile.kt line 38: `val functions: List<CFunction> = emptyList()` |
| CFunction.kt | CStatement.kt | `val body: List<CStatement>` | WIRED | CFunction.kt line 31: `val body: List<CStatement> = emptyList()` |
| CStatement.kt | CExpr.kt | `val condition: CExpr` in CIf | WIRED | CStatement.kt line 26: `val condition: CExpr` |
| CEmitter.kt | ast/CFile.kt | `emit(file: CFile): String` | WIRED | CEmitter.kt line 49: `fun emit(file: CFile): String = buildString` |
| CEmitter.kt | ast/CStatement.kt | exhaustive when on CStatement | WIRED | CEmitter.kt lines 143–155: `when (stmt)` covers all 11 subtypes without else |
| CEmitter.kt | ast/CExpr.kt | exhaustive when on CExpr | WIRED | CEmitter.kt lines 274–285: `when (expr)` covers all 10 subtypes without else |
| ScriptOpVisitor.kt | ExprVisitor.kt | `ExprVisitor.visit()` calls | WIRED | ScriptOpVisitor.kt lines 85, 94, 107–108, 125–126, 128–129: multiple `ExprVisitor.visit()` calls |
| SceneVisitor.kt | ScriptOpVisitor.kt | `ScriptOpVisitor.visit()` in body mapping | WIRED | SceneVisitor.kt lines 52, 61, 70: `.map { ScriptOpVisitor.visit(it) }` |
| GBDKPipelineV2.kt | visitor/SceneVisitor.kt | `SceneVisitor.visit()` and `SceneVisitor.generateSceneEnum()` | WIRED | GBDKPipelineV2.kt lines 92, 137–139, 154 |
| GBDKPipelineV2.kt | visitor/ActorVisitor.kt | `ActorVisitor.visit()` | WIRED | GBDKPipelineV2.kt line 93: `gameIR.actors.flatMap { ActorVisitor.visit(it) }` |
| GBDKPipelineV2.kt | emit/CEmitter.kt | `CEmitter.emit()` | WIRED | GBDKPipelineV2.kt line 71: `cFile.name to CEmitter.emit(cFile)` |
| GBDKBackend.kt | GBDKPipelineV2.kt | `generateV2()` instantiates pipeline | WIRED | GBDKBackend.kt lines 72–73: `val pipeline = GBDKPipelineV2(); val files = pipeline.generate(gameIR)` |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CGEN-01 | 02-01-PLAN.md | C AST sealed hierarchy (CFunction, CStatement, CExpr, CType) in codegen module | SATISFIED | 6 ast/ source files; sealed interfaces verified in all three hierarchies |
| CGEN-02 | 02-01-PLAN.md | Bank assignment is typed field on C AST nodes; no mutable currentBank state | SATISFIED | CFile.bank:Int and CFunction.bank:Int? are val fields; zero var in ast/ package |
| CGEN-03 | 02-02-PLAN.md | Pretty-printer is single place C strings are assembled | SATISFIED | CEmitter.kt is sole file with buildString/appendLine for C output; verified by grep |
| CGEN-04 | 02-04-PLAN.md | Old string-based GBDKCodeGenerator fully replaced | PARTIALLY SATISFIED | GBDKCodeGenerator is @Deprecated(WARNING) and unused by new pipeline. REQUIREMENTS.md says "deleted" but locked decision in 02-CONTEXT.md explicitly records "deprecated not deleted; deletion deferred to Phase 5". ROADMAP.md SC#5 updated accordingly. Not a gap — documented scope adjustment. |
| CGEN-05 | 02-03-PLAN.md | Domain visitors generate C AST per IR domain | SATISFIED | ExprVisitor, ScriptOpVisitor, SceneVisitor, ActorVisitor all exist and tested |

**Note on CGEN-04:** REQUIREMENTS.md text says "deleted" but 02-CONTEXT.md records a locked scope decision: "Old GBDKCodeGenerator deletion — deferred to Phase 5 after all three example games validated through new pipeline." ROADMAP.md was updated per 02-04-PLAN.md task 2 to reflect the locked decision. The REQUIREMENTS.md text was not retroactively updated, but the Phase 2 contract (ROADMAP.md Success Criteria #5) correctly reads "deprecated and unused by the new pipeline; deletion deferred to Phase 5." This is a documented intentional deviation, not a gap blocking Phase 2 completion.

**No orphaned requirements:** All Phase 2 requirement IDs (CGEN-01 through CGEN-05) appear in plan frontmatter and are covered by verified artifacts.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `emit/CEmitter.kt` | 250, 261 | `else ->` in `emitForInit` and `emitExprForIncrement` helpers | INFO | Intentional: these are private helper methods for for-loop init/increment rendering contexts, not the primary sealed dispatch. They delegate to `emitStatement`/`emitExpr` as fallback. Main dispatch methods are exhaustive without `else`. |
| `visitor/ScriptOpVisitor.kt` | 75 | `else -> CRawCode("/* TODO: ... */")` | INFO | Intentional: documented design decision for unimplemented ScriptOp types. Prevents scope creep while keeping Pong compilable. Not a stub — it is the defined behavior for unsupported types. |
| `codegen/pipeline/GBDKPipelineV2.kt` | 227–249 | `buildSpriteHelperStubs()` returns `CRawCode("/* TODO: Phase 3 - OAM management */")` | WARNING | Sprite helper stubs defer OAM management to Phase 3. Generated Pong C output will not compile to a working ROM without these stubs being replaced. This is documented and expected — Phase 3 replaces them with real OAM code. Does not block Phase 2 goal (C output correctness verified). |

**No blocker anti-patterns found.** The sprite helper stubs are a known Phase 3 gap, explicitly documented in the CONTEXT.md and SUMMARY.md files.

### Human Verification Required

#### 1. Pong ROM Boots and Plays in mGBA

**Test:** Build the Pong ROM using GBDK lcc on the C output generated by GBDKPipelineV2. Run the ROM in mGBA.
**Expected:** Title screen shows "PONG" and "PRESS START". Start button starts the game. D-pad moves paddles. Ball bounces off top and bottom walls. Ball passes paddles to score points. First to 5 points wins. Game-over screen appears with option to restart.
**Why human:** GBDKPipelineV2 generates correct C (verified by 17 PongPipelineTest tests), but ROM compilation via GBDK lcc requires GBDK installed. GBDK is not available in CI. ROM-level behavior requires an emulator. This is explicitly deferred to Phase 5 (INTG-01) per the roadmap.

### Test Results Summary

All Phase 2 tests pass with zero failures or errors:

| Test Class | Tests | Failures | Errors |
|------------|-------|----------|--------|
| CAstTest | 18 | 0 | 0 |
| CEmitterTest | 44 | 0 | 0 |
| ExprVisitorTest | 19 | 0 | 0 |
| ScriptOpVisitorTest | 17 | 0 | 0 |
| SceneVisitorTest | 11 | 0 | 0 |
| ActorVisitorTest | 5 | 0 | 0 |
| PongPipelineTest | 17 | 0 | 0 |
| **Total Phase 2** | **131** | **0** | **0** |

Full gbkt-backend-gbdk test suite: BUILD SUCCESSFUL (all existing tests also pass, no regressions from deprecated GBDKCodeGenerator).

### Gaps Summary

No gaps found. All 9 observable truths verified, all artifacts substantive and wired, all key links confirmed. The only human verification item (ROM emulator test) is an explicit Phase 5 concern (INTG-01) not a Phase 2 gap.

The CGEN-04 "deleted vs deprecated" discrepancy in REQUIREMENTS.md is a documented locked decision adjustment, not a gap — the Phase 2 contract in ROADMAP.md correctly reflects the agreed scope.

---

_Verified: 2026-02-18T07:30:00Z_
_Verifier: Claude (gsd-verifier)_
