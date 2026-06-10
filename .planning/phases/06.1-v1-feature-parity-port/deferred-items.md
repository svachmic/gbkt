# Deferred Items — Phase 06.1

## Pre-existing Issues (Out of Scope)

### gbkt-examples:explorer compilation failure

**File:** `gbkt-examples/explorer/src/main/kotlin/io/github/gbkt/examples/explorer/ExplorerV2.kt:90`

**Error:** `Assignment type mismatch: actual type is 'Int', but 'Unit' was expected.`

**Code:** `saveData("explorer_save") { slots = 1 }`

**Status:** Pre-existing before Phase 06.1-06 (confirmed by git stash test). The `slots = 1` expression inside the `saveData {}` builder produces a type mismatch — the `slots` setter returns `Unit` but the assignment `slots = 1` is being treated as expression assignment.

**Impact:** `./gradlew build` fails; `./gradlew :gbkt-backend-gbdk:build` passes.

**Resolution:** Fix the `saveData {}` DSL builder or the `ExplorerV2.kt` usage in a future phase.
