# Deferred Items — Phase 04 Analysis Pass Pipeline

## Detekt Violations in VRAMLayoutPass.kt (Out of Scope for Plan 04-03)

**Discovered during:** Plan 04-03 full build verification
**File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPass.kt`
**Owner:** Plan 04-04 (VRAMLayoutPass)

The 4 violations below are from the parallel agent that executed plan 04-04. Plan 04-03 does not
modify VRAMLayoutPass.kt and cannot fix them without violating parallel execution isolation.

| Rule | Location | Issue |
|------|----------|-------|
| LongParameterList | line 190 | `buildTileOverflowError` has 7 params (threshold: 6) |
| FunctionOnlyReturningConstant | line 175 | `estimateBgTiles` always returns constant |
| UnusedParameter | line 175 | `scene` param unused in `estimateBgTiles` |
| UnusedParameter | line 175 | `game` param unused in `estimateBgTiles` |

**Resolution:** The plan 04-04 agent should fix these in a subsequent step, or they can be fixed in
plan 04-05 or later as part of a detekt cleanup pass.
