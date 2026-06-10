# Deferred Items — Phase 06.7

## Pre-existing test failures revealed during 06.7-07

### GBDKSystemVisitorTest — actor pool test assertions use hex string format

**Found during:** Plan 06.7-07 Task 2 (when fixing missing `assertEquals` import in GBDKSystemVisitorTest.kt)

**Issue:** Two actor pool tests assert `emitted.contains("0xFF")` but the CEmitter emits `CLiteral(255)` as `"255u"`, not `"0xFF"`. The tests were previously not compiling due to a missing `kotlin.test.assertEquals` import, so the failures were hidden.

**Affected tests:**
- `GBDKSystemVisitorTest > buildActorPoolFunctions SILENT_NOOP spawn returns 0xFF when pool full`
- `GBDKSystemVisitorTest > buildActorPoolFunctions destroy guards against 0xFF invalid slot`

**Fix needed:** Change test assertions from `contains("0xFF")` to `contains("255")` OR change the codegen to use `CRawExpr("0xFF")` where a hex sentinel display is desired.

**File:** `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitorTest.kt` lines 521, 570

### NpcCollisionCodegenTest — pre-existing failures

**Found during:** Plan 06.7-07 (pre-existing, visible before this plan via git stash check)

**Issue:** `NpcCollisionCodegenTest > actors without npcCollisionConfig are not involved in NPC collision checks` fails.

**Context:** NPC collision tests from plan 06.7-08 (not yet fully implemented) are already present and failing.
