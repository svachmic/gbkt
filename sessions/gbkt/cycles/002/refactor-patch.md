## Changes: 3

### 1. Extract `forEachNestedOpList` in ScriptOpTraversal.kt

**File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt`

`collectNavigations` and `collectAllOps` both manually enumerated the same 6 ScriptOp subtypes that contain nested op lists (IfOp, WhileOp, ForOp, FadeOp, DialogChoice, PoolForEachActive). Extracted a shared `forEachNestedOpList` inline function that is now the single source of truth for which op types have nested children. Both functions now delegate to it, eliminating the duplicated when-blocks.

**Before:** Two 15-line `when` blocks with identical structure.
**After:** One 10-line helper; callers reduced to 3-4 lines each.

### 2. Extract `sanitizeCId()` utility in gbkt-backend-api

**Files:**
- `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/GenreSystemVisitor.kt` (new function)
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` (4 call sites)
- `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt` (4 call sites)

The pattern `.replace('-', '_').replace(' ', '_')` appeared 8 times across the two genre visitors (and 80+ times across the broader codebase, outside refactoring scope). Extracted a `sanitizeCId(id: String): String` top-level function in `gbkt-backend-api` and replaced all 8 occurrences in the recently changed files.

This establishes the canonical implementation that future refactoring of the remaining 80+ call sites can adopt incrementally.

### 3. Extract `buildDeadZoneCheck` in PlatformerVisitor

**File:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`

`buildSmoothFollowBody` contained two nearly identical 40-line blocks for the horizontal and vertical dead-zone camera checks, differing only by axis name (`x`/`y`) and dead-zone value. Extracted a `buildDeadZoneCheck(axis, deadZone)` private method that generates the C AST for a single axis, and the caller now invokes it twice.

**Before:** ~80 lines of duplicated C AST construction.
**After:** ~30-line helper called twice; `buildSmoothFollowBody` reduced from 106 lines to 14.

### Test results

Full test suite: **168 tasks, 0 failures** (`./gradlew test` -- BUILD SUCCESSFUL).
