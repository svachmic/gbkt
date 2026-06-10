---
phase: 02-structured-codegen-and-migration-cut
plan: 01
subsystem: codegen
tags: [kotlin, sealed-interface, ast, gbdk, code-generation, bank-management]

requires: []

provides:
  - "CType sealed hierarchy: CU8, CU16, CI8, CI16, CVoid, CPointer, CArray, CConst"
  - "CExpr sealed hierarchy: CLiteral, CStringLiteral, CVar, CBinaryExpr, CUnaryExpr, CCall, CTernary, CArrayAccess, CCast, CRawExpr"
  - "CStatement sealed hierarchy: CIf, CFor, CWhile, CSwitch, CReturn, CBlock, CVarDecl, CExprStatement, CRawCode, CComment, CBlankLine"
  - "CFile data class with typed bank:Int field (eliminates mutable currentBank state)"
  - "CFunction data class with bank:Int? (null=inherit) and isBanked:Boolean"
  - "CDeclaration: CDefine and CTypedef for file-level preprocessor directives"
  - "CRawCode/CRawExpr escape hatches for GBDK-specific code"
  - "CAstTest: 17 tests verifying exhaustive when matching, bank fields, construction"

affects:
  - "02-02 (C AST emitter depends on these types)"
  - "02-03 (IR-to-AST visitors depend on these types)"
  - "02-04 (CFile-based pipeline orchestrator depends on CFile/CFunction)"

tech-stack:
  added: []
  patterns:
    - "Sealed interface hierarchy for exhaustive when matching without else branch"
    - "Bank assignment as typed immutable field (CFile.bank:Int, CFunction.bank:Int?) instead of mutable state"
    - "Null-means-inherit pattern: CFunction.bank=null inherits from containing CFile"
    - "Escape hatch pattern: CRawCode (statement) and CRawExpr (expression) for GBDK-specific code"
    - "TDD Red-Green-Refactor: tests committed before implementation"

key-files:
  created:
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CType.kt"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CExpr.kt"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CStatement.kt"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CDeclaration.kt"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFunction.kt"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFile.kt"
    - "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CAstTest.kt"
  modified: []

key-decisions:
  - "CBlankLine implemented as data object (singleton) — there is only one kind of blank line; data object avoids unnecessary heap allocation per blank line in emitted output"
  - "CSwitchCase is a regular data class (not sealed subtype of CStatement) — it is a structural component of CSwitch, not a standalone statement"
  - "CFunction.bank:Int? uses null-means-inherit semantics — avoids requiring callers to always look up the parent CFile bank; emitter handles null by inheriting file bank"

patterns-established:
  - "All ast/ files: all fields are val — zero mutable state"
  - "Sealed hierarchy + data class/object pattern mirrors ir.v2 Expr/ScriptOp design"
  - "License headers: MPL-2.0 on every file"

requirements-completed:
  - CGEN-01
  - CGEN-02

duration: 2min
completed: 2026-02-18
---

# Phase 2 Plan 01: C AST Sealed Hierarchy Summary

**Six-file sealed C AST hierarchy in `codegen/ast/` with bank as typed immutable field on CFile/CFunction, eliminating the mutable currentBank state that caused GBDK bank-leak bugs**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-18T06:16:47Z
- **Completed:** 2026-02-18T06:19:17Z
- **Tasks:** 1 (TDD: RED commit + GREEN commit)
- **Files created:** 7 (6 source + 1 test)

## Accomplishments

- Built complete sealed C AST type hierarchy (CType, CExpr, CStatement) with exhaustive `when` matching — no `else` branch needed in emitter/visitor code
- Bank assignment is now a typed `val bank: Int` field on `CFile` and `val bank: Int?` on `CFunction` — eliminates the root cause of bank-state-leak bugs documented in MEMORY.md
- `CRawCode` (statement) and `CRawExpr` (expression) escape hatches allow GBDK-specific code that cannot be represented by the typed hierarchy
- 17 tests covering all sealed hierarchies, bank field semantics, construction patterns, and edge cases

## Task Commits

Each task was committed atomically (TDD pattern):

1. **RED: Failing tests** - `5a6b903` (test) — CAstTest with 17 tests, all failing
2. **GREEN: Implementation** - `e1e58fb` (feat) — Six source files making all tests pass

## Files Created

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CType.kt` — Sealed CType hierarchy (CU8, CU16, CI8, CI16, CVoid, CPointer, CArray, CConst)
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CExpr.kt` — Sealed CExpr hierarchy (CLiteral, CStringLiteral, CVar, CBinaryExpr, CUnaryExpr, CCall, CTernary, CArrayAccess, CCast, CRawExpr)
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CStatement.kt` — Sealed CStatement hierarchy (CIf, CFor, CWhile, CSwitch, CReturn, CBlock, CVarDecl, CExprStatement, CRawCode, CComment, CBlankLine) + CSwitchCase
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CDeclaration.kt` — CDefine and CTypedef for file-level preprocessor directives
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFunction.kt` — CFunction data class with bank:Int? and isBanked:Boolean; CParam data class
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFile.kt` — CFile data class with bank:Int typed field
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CAstTest.kt` — 17 tests for the complete hierarchy

## Decisions Made

- `CBlankLine` implemented as `data object` (singleton) — there is only one kind of blank line; avoids unnecessary allocation per blank line in emitted output
- `CSwitchCase` is a regular `data class` (not a sealed CStatement subtype) — it is a structural component of `CSwitch`, not a standalone statement
- `CFunction.bank: Int?` uses null-means-inherit semantics — avoids requiring callers to always look up the parent `CFile` bank; the emitter handles null by inheriting the file bank

## Deviations from Plan

None — plan executed exactly as written. TDD RED-GREEN-REFACTOR followed cleanly.

## Issues Encountered

None. The benign compiler warnings (`Check for instance is always 'true'`) in the test file are expected — `assertTrue(raw is CStatement)` always passes since `CRawCode` always implements `CStatement`. These are documentation-style assertions in the tests.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- The `codegen/ast/` package is the foundation that Plans 02-02, 02-03, and 02-04 all depend on
- All sealed hierarchies are final and exhaustive — emitter (02-02) can use them without `else` branches
- Bank-as-typed-field pattern is established — visitor code (02-03) will populate bank from IR bank annotations

---
*Phase: 02-structured-codegen-and-migration-cut*
*Completed: 2026-02-18*
