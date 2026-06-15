---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 31
subsystem: codegen
tags: [sonar, kotlin, code-quality, gbdk-pipeline, menu-visitor, game-builder]

# Dependency graph
requires:
  - phase: 18-deprecation-removals-and-sonar-burn-down
    provides: Extract-method refactors that introduced 4 new MAJOR Sonar findings
provides:
  - S107 resolved: buildHomeFileRawSections reduced to vararg (8 params → 1)
  - S108 resolved: MenuVisitor null branch no longer empty block
  - S6524 x2 resolved: buildEffectiveNpcCollisions return type narrowed to immutable List
affects:
  - SonarCloud PR scan for chore/hardening_0_1_0
  - gbkt-backend-gbdk codegen pipeline
  - gbkt-lang DSL GameBuilder

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "vararg+filterNotNull idiom to collapse >7-param helpers without behavior change"
    - "null -> Unit instead of null -> {} for unreachable when branches (S108)"
    - "Narrow MutableList return types to List when callers never mutate post-return (S6524)"

key-files:
  created: []
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MenuVisitor.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt

key-decisions:
  - "S107: vararg approach keeps call-site ordering and null-filtering semantics identical; takeIf guards moved to call site"
  - "S108: null -> Unit chosen over null -> { /* comment */ } because it is the idiomatic Kotlin no-op expression"
  - "S6524: return type narrowed at declaration; function body unchanged (MutableList still built internally, widened on return); callers' .toList() calls preserved"

patterns-established:
  - "Byte-identity gate: all 7 examples verified identical before and after output-preserving refactors"

requirements-completed: []

# Metrics
duration: 4min
completed: 2026-06-13
---

# Phase 18 Plan 31: Sonar MAJOR Code-Smell Gap Closure Summary

**Four new Sonar MAJOR smells introduced by Phase 18 extract-method refactors fixed via output-preserving mechanical transforms with 7/7 example byte-identity confirmed.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-06-13T18:21:14Z
- **Completed:** 2026-06-13T18:25:00Z
- **Tasks:** 1 (all 4 findings in one atomic commit)
- **Files modified:** 3

## Accomplishments

- S107 (too many parameters): `buildHomeFileRawSections` in `GBDKPipeline.kt` collapsed from 8-param function to `vararg sections: String?` returning `sections.filterNotNull()`; the two non-nullable `String` args now have `.takeIf { it.isNotEmpty() }` applied at the call site so filtering is identical
- S108 (empty block): `null -> {}` branch in `MenuVisitor.kt` changed to `null -> Unit` with comment preserved; semantically identical, no empty block
- S6524 x2 (mutable collection in immutable variable): `buildEffectiveNpcCollisions` in `GameBuilder.kt` return type narrowed from `Pair<MutableList<...>, MutableList<...>>` to `Pair<List<...>, List<...>>`; confirmed callers only read via `.toList()` (no post-return mutation)
- Byte-identity sweep: 6/6 non-pong examples identical; pong PASS* (generated C identical)

## Task Commits

1. **Fix all 4 Sonar findings** - `da0e135d` (fix)

## Files Created/Modified

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt` - S107: collapsed 8-param function to vararg; updated call site
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MenuVisitor.kt` - S108: `null -> {}` → `null -> Unit`
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt` - S6524: narrowed return type to `Pair<List, List>`

## Decisions Made

- vararg approach for S107 rather than a builder/data-class wrapper: the 8 arguments are already ordered and caller doesn't pass them as a named set, so vararg is the minimal-change path with identical semantics
- `null -> Unit` for S108: Kotlin idiomatic no-op expression, avoids empty-block S108 without adding a dummy `return` or comment-only block
- Narrow-declaration-only for S6524: function body unchanged (still builds `MutableList` internally); Kotlin's `MutableList` extends `List` so the widening is implicit on return; callers' `.toList()` calls remain valid (no-op widening, zero mutation risk)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 4 Sonar MAJOR findings resolved; the SonarCloud re-scan on PR #77 push will confirm
- No requirements to mark complete (orchestrator re-confirms via SonarCloud after push)

---

## Self-Check: PASSED

- `da0e135d` commit exists: confirmed
- `GBDKPipeline.kt` modified: confirmed
- `MenuVisitor.kt` modified: confirmed
- `GameBuilder.kt` modified: confirmed
- 7/7 byte-identity sweep: PASSED (6 exact + pong PASS*)
- `spotlessApply` + `detekt` clean on both modules: PASSED
- `:gbkt-backend-gbdk:test` + `:gbkt-lang:test`: PASSED

---
*Phase: 18-deprecation-removals-and-sonar-burn-down*
*Completed: 2026-06-13*
