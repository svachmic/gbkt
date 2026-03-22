## Changes: 4

### 1. Extract `mapExprChildren()` into ScriptOpTraversal.kt

**Files:** `ScriptOpTraversal.kt`, `BitwiseOptimizationPass.kt`, `ConstantFoldingPass.kt`

Both `BitwiseOptimizationPass.optimizeExpr` and `ConstantFoldingPass.foldExpr` contained identical code for recursing into compound expression children (UnaryExpr, TernaryExpr, ArrayAccessExpr, CallExpr, CastExpr). Extracted a shared `mapExprChildren(expr, transform)` function into `ScriptOpTraversal.kt`. `BitwiseOptimizationPass` now delegates all non-BinaryExpr recursion to it in a single line. `ConstantFoldingPass` uses it as the `else` fallback for types it does not specially fold.

### 2. Extract `applyAssignOp()` in ScriptOpInterpreter

**File:** `ScriptOpInterpreter.kt`

`executeAssign` and `executeArrayAssign` both contained identical 9-branch `when(AssignOp)` blocks (SET, ADD, SUB, MUL, DIV, MOD, AND, OR, XOR). Extracted to a single `applyAssignOp(op, current, value)` helper. `executeArrayAssign` body reduced from 9 lines to 1.

### 3. Extract `collectDuplicates<T>()` in SemanticValidationPass

**File:** `SemanticValidationPass.kt`

Three nearly identical methods (`collectDuplicateSceneIds`, `collectDuplicateActorIds`, `collectDuplicateVariableNames`) all followed the same pattern: iterate items, track seen names in a set, emit ANLZ-01 diagnostic on duplicate. Replaced with a single generic `collectDuplicates<T>(items, entityKind, fieldKind, diagnostics, nameOf)` function, called with lambdas at each call site.

### 4. Extract `sanitizePuzzleId()` in ScriptOpInterpreter

**File:** `ScriptOpInterpreter.kt`

The expression `id.replace('-', '_').replace(' ', '_')` appeared 5 times across puzzle execution methods. Extracted to `sanitizePuzzleId(id)`.

### Stats

- 5 files changed, 86 insertions, 106 deletions (net -20 lines)
- Full test suite passes (168 tasks, 0 failures)
