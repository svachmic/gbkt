# Deferred Items — Phase 06.8

## Out-of-Scope Issues Discovered During Plan 05 Execution

### CurrencyTest.kt — wrong API usage for delegate pattern inside lambdas

**Discovered during:** Task 1 — running `./gradlew :gbkt-genre-rpg:test` (pre-existing failure)

**Issue:** `CurrencyTest.kt` (from plan 03) uses `val gold by currency { max(9999) }` inside
game `{}` lambda blocks. In Kotlin, `provideDelegate` operator is called for class member
properties but NOT for local variables inside lambdas with a typed receiver. The `_rogue_*`
globals registered by `GameBuilder.currency(id, block)` are never called, so the system is not
found in `ir.systems`.

**Failing tests (6):**
- `currency builder infers name from property`
- `multiple currencies can be defined and coexist`
- `exchange rates recorded correctly`
- `monster drop accepts CurrencyRef with amount and chance`
- `merchant item with per-item currency pricing`
- `currency max defaults to 9999`

**Fix:** Rewrite the 6 failing tests to use the explicit `currency("gold") { max(9999) }` form
(with explicit ID) instead of the delegate `by currency { }` form. The explicit form works inside
lambdas. This belongs to plan 03 (Currency system).

**Why not fixed:** Out of scope for plan 05 (Roguelike system). The CurrencyTest.kt is from plan
03's incomplete work. Fixing requires rewriting 6+ test methods that belong to that plan's scope.

## Out-of-Scope Issues Discovered During Plan 07 Execution

### gbkt-backend-gbdk compile failure

**Discovered during:** Plan 07 overall `./gradlew build` verification

**Issue:** The full `./gradlew build` fails because there are uncommitted modifications in
`gbkt-backend-gbdk` (RpgVisitor.kt, GBDKPipelineV2.kt, GBDKSystemVisitor.kt) from concurrent
agent work on other plans (Plan 04 — Action RPG codegen). These files reference
`generateActionRpgVarDecls` and `generateActionRpgFunctions` which are not yet defined.

**Why not fixed:** This is a pre-existing issue in the working directory from another plan's
in-progress work. It is OUTSIDE the scope of Plan 07 (gbkt-genre-puzzle module creation).
The puzzle module itself compiles and tests pass in isolation.

**Resolution:** Plan 04 (Action RPG codegen) should commit these files once implementation
is complete. Until then, `./gradlew build` may fail if the backend changes are uncommitted.
Run `./gradlew :gbkt-genre-puzzle:test` to verify plan 07 work in isolation.
