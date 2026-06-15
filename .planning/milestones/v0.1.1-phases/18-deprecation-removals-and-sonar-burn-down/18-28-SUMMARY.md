---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 28
subsystem: quality
tags: [sonar, s3776, cognitive-complexity, byte-identity, extract-method, gbkt-backend-gbdk]
dependency_graph:
  requires: ["18-27"]
  provides: ["SONAR-01-gap-closure-4-findings", "s3776-cc-fix-walkOps", "s3776-cc-fix-buildHomeFileRawSections", "s3776-cc-fix-buildPressurePlateObjectOutput", "s3776-cc-fix-buildZoneLoadStatements"]
  affects: ["SonarCloud S3776 SONAR-01 re-scan", "gbkt-backend-gbdk pipeline", "gbkt-backend-gbdk system visitor", "gbkt-backend-gbdk scene visitor"]
tech_stack:
  added: []
  patterns: ["guard-return (?: return null) to flatten pyramid nesting", "listOfNotNull + takeIf to replace buildList if-add pattern", "extract-value-returning-helper to remove flatMap/buildList lambda nesting depth"]
key_files:
  created:
    - .planning/phases/18-deprecation-removals-and-sonar-burn-down/18-28-SUMMARY.md
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt
key_decisions:
  - "Root cause: detekt measures CYCLOMATIC complexity; Sonar S3776 measures COGNITIVE complexity (nesting-weighted). These 4 methods passed detekt but exceeded Sonar's cc≤15 threshold due to pyramid nesting inside lambdas."
  - "Fix strategy: extract nested structures into flat value-returning private helpers — removes nesting levels from the parent, reducing cognitive complexity."
  - "walkOps: extract 4-deep if-chain (condition/interactionType/arg/buttonName) into extractControlMappingFromIfOp using guard-return (?: return null) pattern. cc 21→3."
  - "buildHomeFileRawSections: replace buildList{8×if-add} with listOfNotNull+takeIf. cc 16→0."
  - "buildPressurePlateObjectOutput: extract buildPlateCheckBody / buildPlateRespondToChecks / buildPlatePressedBody / buildPlateReleasedBody. Collapse if(onStepX.isNotEmpty)/else — iterating an empty list is equivalent to the else branch. cc 23→1."
  - "buildZoneLoadStatements: extract buildSingleZoneLoad / buildZoneTilemapAndPalette / buildZonePixelLoad / buildZonePaletteLoad. cc 23→≤6."
  - "Generated C byte-identity: verified sha256 across all 7 examples after each commit — 6/6 non-pong ROMs IDENTICAL, pong main.c IDENTICAL (PASS*)."
  - "ROADMAP completion NOT updated — orchestrator confirms SONAR-01 via SonarCloud re-scan after branch push."
requirements-completed: []
duration: 30min
completed: 2026-06-13
---

# Phase 18 Plan 28: SONAR-01 Gap-Closure — 4 Remaining S3776 Findings

**Gap-closure plan that flattens nesting in the 4 methods SonarCloud PR #77 found still over cc=15 after Phase 18 EMITTING sweep. All 4 fixed via extract-method (no NOSONAR). Byte-identity preserved across all 7 examples. 4 atomic commits.**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-06-13T16:35:00Z
- **Completed:** 2026-06-13T17:04:31Z
- **Tasks:** 4 (one per S3776 finding)
- **Commits:** 4 code + 1 docs (this summary)
- **Files modified:** 3 source files

## Root Cause: Cyclomatic vs. Cognitive Complexity

detekt's `CyclomaticComplexMethod` rule measures **cyclomatic** complexity (control flow paths). Sonar S3776 measures **cognitive** complexity — which is **nesting-weighted**: each control flow structure adds `1 + current_nesting_level`. A deeply nested `if` inside a `when` inside a `for` inside a `flatMap` lambda can be cyclomatically fine but cognitively over-threshold.

These 4 methods passed detekt's gate but failed SonarCloud's PRs scan because they were extracted in Phase 18 earlier plans without flattening the nesting depth inside them.

## Fix 1: `walkOps` (GBDKPipeline.kt) — cc 21 → 3

**Finding:** S3776 cc=21 reported by SonarCloud PR #77.

**Root cause:** 4-deep nested `if` pyramid inside the `is IfOp` branch:
- `for` at depth 0 → depth 1: +1
- `when` at depth 1 → depth 2: +2
- `if (condition is CallExpr)` at depth 2 → depth 3: +3
- `if (interactionType != null)` at depth 3 → depth 4: +4
- `if (arg is VarRef)` at depth 4 → depth 5: +5
- `if (buttonName != null)` at depth 5 → depth 6: +6
Total: 1+2+3+4+5+6 = **21**.

**Fix:** Extracted `extractControlMappingFromIfOp(op: IfOp): ControlMapping?` using guard-return pattern (`?: return null`). Elvis guards are flat — they do not add to cognitive complexity in Sonar S3776 for Kotlin. The 4-level pyramid collapses to 4 sequential guard returns.

Post-fix `walkOps` cc: 1 (for) + 2 (when) = **3**.
`extractControlMappingFromIfOp` cc: **0** (all guard returns).

**Commit:** `0b176147` — `refactor(18-28): flatten walkOps nested-if pyramid (S3776 cc 21→flat)`

## Fix 2: `buildHomeFileRawSections` (GBDKPipeline.kt) — cc 16 → 0

**Finding:** S3776 cc=16 reported by SonarCloud PR #77.

**Root cause:** 8 `if` statements inside a `buildList {}` lambda. The lambda increments nesting to depth 1; each `if` at depth 1 contributes +1+1=+2. Total: 8×2 = **16**.

**Fix:** Replaced the entire body with `listOfNotNull(...)` using `takeIf { it.isNotEmpty() }` for the two non-nullable `String` params. `listOfNotNull` is a flat function call with no control flow — `takeIf` lambdas contain only simple boolean predicates, adding zero control flow structures.

Post-fix cc: **0** (flat expression, no control flow).

**Commit:** `7d3a24eb` — `refactor(18-28): replace buildHomeFileRawSections buildList+8-ifs with listOfNotNull (S3776 cc 16→0)`

## Fix 3: `buildPressurePlateObjectOutput` (GBDKSystemVisitor.kt) — cc 23 → 1

**Finding:** S3776 cc=23 reported by SonarCloud PR #77.

**Root cause:** Three nested structures inside `buildPressurePlateObjectOutput`:
1. Outer `buildList<CStatement>` lambda (depth +1 → depth 1)
2. `if (requiresGuard.isNotEmpty())` at depth 1: +2
3. `for (actorId in respondToActorIds)` at depth 1 → 2: +2; `if (actorId.startsWith("pool:"))` at depth 2 → 3: +3; `else`: +1
4. `if (obj.onStepOn.isNotEmpty())` at depth 1 → 2: +2; inner `buildList` lambda (depth +1 → 3); `for` at depth 3: +4; `else`: +1
5. `if (obj.onStepOff.isNotEmpty())` at depth 1 → 2: +2; inner `buildList` (depth 3); `for` at depth 3: +4; `else`: +1

**Fix:** Extracted 4 value-returning helpers:
- `buildPlateCheckBody(obj, id, puzzleById)` — orchestrates the whole body; cc≤3.
- `buildPlateRespondToChecks(obj)` — the for/if/else respondTo loop; cc≤6.
- `buildPlatePressedBody(obj, id)` — for over `onStepOn` + pressed=1; cc=2. Note: the `if (obj.onStepOn.isNotEmpty())/else` was collapsed — iterating an empty list produces the same body as the else branch (just `[pressed=1]`), so the conditional is unnecessary and was removed.
- `buildPlateReleasedBody(obj, id)` — for over `onStepOff` + pressed=0; cc=2. Same collapse.

Post-fix `buildPressurePlateObjectOutput` cc: **1** (`if (obj.hidden) 1 else 0` at depth 0).

**Commit:** `35b92648` — `refactor(18-28): extract helpers from buildPressurePlateObjectOutput (S3776 cc 23→1)`

## Fix 4: `buildZoneLoadStatements` (SceneVisitor.kt) — cc 23 → ≤6

**Finding:** S3776 cc=23 reported by SonarCloud PR #77.

**Root cause:** The `flatMap { zoneId -> ... }` lambda (depth +1) contained:
- 3× `if (zone.tilesetPath != null) ... else ...` (pixelLoad, widthArg, heightArg): 3×2 = +6
- `&&` operators in two predicates (sceneEndsWithDisplayOn, sceneHasCardOverdraw): +2
- `when {}` block with 3 branches (screenMode / cardOverdraw / default): several contributions
- `if (zone.tilesetPath != null && gbcTarget)` (palette): +1+1+1 = +3
- `if (sceneEndsWithDisplayOn)` (displayOn): +2

**Fix:** Extracted 4 value-returning helpers:
- `buildSingleZoneLoad(scene, zone, bank, gbcTarget)` — replaces the `flatMap` body; computes zoneSanitized, pixelLoad, sceneEndsWithDisplayOn, sceneHasCardOverdraw, then delegates to `buildZoneTilemapAndPalette`; cc≈2 (two `&&` operators in simple predicates).
- `buildZoneTilemapAndPalette(zone, zoneSanitized, bank, gbcTarget, sceneEndsWithDisplayOn, sceneHasCardOverdraw)` — holds widthArg/heightArg if/else + when block + displayOn; cc≈13 (safely under 15).
- `buildZonePixelLoad(zone, zoneSanitized)` — pixelLoad if/else; cc=2.
- `buildZonePaletteLoad(zone, zoneSanitized, gbcTarget)` — palette if+&&+else; cc=3.

Post-fix `buildZoneLoadStatements` cc: **≤6** (Elvis chains in flatMap only).

**Commit:** `854479bb` — `refactor(18-28): extract helpers from buildZoneLoadStatements (S3776 cc 23→≤6)`

## Task Commits

| Commit | Task | Files |
|--------|------|-------|
| `0b176147` | Fix 1: walkOps cc 21→3 | GBDKPipeline.kt (+extractControlMappingFromIfOp) |
| `7d3a24eb` | Fix 2: buildHomeFileRawSections cc 16→0 | GBDKPipeline.kt (listOfNotNull rewrite) |
| `35b92648` | Fix 3: buildPressurePlateObjectOutput cc 23→1 | GBDKSystemVisitor.kt (+4 helpers) |
| `854479bb` | Fix 4: buildZoneLoadStatements cc 23→≤6 | SceneVisitor.kt (+4 helpers) |

## Byte-Identity Sweep (per-fix and final)

All 4 fixes verified byte-identical after each commit. Final sweep hashes:

| Example | SHA256 | vs Baseline | Result |
|---------|--------|-------------|--------|
| pong main.c | `b5e81de7...` | `b5e81de7...` | PASS* |
| breakout.gb | `564465cd...` | `564465cd...` | IDENTICAL |
| simple-physics.gb | `247e16d2...` | `247e16d2...` | IDENTICAL |
| metasprites.gb | `9b2440db...` | `9b2440db...` | IDENTICAL |
| metasprites-stress.gb | `bc51eadd...` | `bc51eadd...` | IDENTICAL |
| banks.gb | `12c8ee2e...` | `12c8ee2e...` | IDENTICAL |
| platformer-template.gb | `9a8f268a...` | `9a8f268a...` | IDENTICAL |

Baseline: 18-27-SUMMARY.md (verified against Phase-18-start hashes in 18-13-SUMMARY.md).

## Detekt + Spotless Gate

`./gradlew :gbkt-backend-gbdk:spotlessApply :gbkt-backend-gbdk:detekt` GREEN before each commit. Full `./gradlew build` GREEN (398 tasks, BUILD SUCCESSFUL in ~75s).

## NOSONAR Budget

No NOSONAR suppressions added. All 4 findings resolved via extract-method.
NOSONAR count remains **0** (authoritative: `grep -r NOSONAR gbkt-*/src/main/kotlin/`).

## Known Stubs

None — this plan modifies only internal refactoring helpers with no UI or data surface.

## Threat Flags

None — refactoring only; no new network endpoints, auth paths, file access patterns, or schema changes.

## Deviations from Plan

### None

Plan executed exactly as written. The only minor process deviation: fixes 1 and 2 are both in GBDKPipeline.kt (same file), so they were committed by temporarily reverting fix 2, committing fix 1, then re-applying fix 2 and committing fix 2. This maintained the "one commit per finding" requirement while working around the single-file constraint.

## Self-Check: PASSED

- [x] GBDKPipeline.kt exists: `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt`
- [x] GBDKSystemVisitor.kt exists: `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt`
- [x] SceneVisitor.kt exists: `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt`
- [x] Commit `0b176147` exists
- [x] Commit `7d3a24eb` exists
- [x] Commit `35b92648` exists
- [x] Commit `854479bb` exists
