---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 19
subsystem: testing
tags: [kotlin, gbdk, codegen, metasprites, jvm-tests, emission-invariants, oracle]

# Dependency graph
requires:
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    provides: "Plan 15 clean generated C source with play_frame body containing D-pad accel, B/A button behaviors, and moveMetasprite emission"

provides:
  - "Three D-12 Tier-1 JVM emission invariant tests (D_12_1, D_12_2, D_12_3) using extractFunctionBody brace-walk that lock the play_frame body shape against future codegen regressions"
  - "Evidence artifacts: tier1-shape/*.txt with play_frame body snapshots for each test"

affects:
  - "10-port-metasprites-gbdk-example-to-gbkt future plans — D-12 oracle guards play_frame codegen"
  - "Any future changes to MetaspriteVisitor, ScriptOpVisitor.visitMoveMetasprite, or ExprVisitor that affect play_frame emission"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "extractFunctionBody brace-walk helper (Pattern C): scope-level grep gate, extracts named C function body before grep, guards against cross-function token masking"
    - "playFrameBody() bank1.c → main.c fallback: handles single-scene HOME fast-path where BankingAnalysisPass assigns scene to bank 0 and pipeline folds into main.c"
    - "Token disjunction assertions (D-overfitting-1): assertTrue(a || b || c) accepts multiple valid emission shapes"

key-files:
  created:
    - "gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteEmissionTest.kt"
    - ".planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape/01-animation-index-advance.txt"
    - ".planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape/02-flip-oam-attribute.txt"
    - ".planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape/03-sub-palette-oam-attribute.txt"
  modified: []

key-decisions:
  - "playFrameBody() falls back from bank1.c to main.c: for single-scene games with romBanks=2, BankingAnalysisPass places scene in HOME bank (bank=0) via its single-scene fast-path, and the pipeline folds scene functions into main.c, omitting bank1.c entirely. The fallback is transparent to test logic."
  - "Token disjunctions accept multiple valid emission shapes (D-overfitting-1): D_12_1 uses `_idx = _idx + 1` OR `_idx = 0` OR `sprite_metasprites[_idx]`; D_12_2 uses flip-variant OR OAMF flag constants; D_12_3 uses `_rot >> 2` OR `subpal` OR `OAMF_CGB_PAL`. This ensures tests remain GREEN across minor reformatting."
  - "evidence/tier1-shape/ written before assertions: evidence files are written regardless of test outcome, enabling inspection of the C shape even when RED."

patterns-established:
  - "Pattern C (scope-level brace-walk): always call extractFunctionBody before any assertion — never grep the whole file"
  - "playFrameBody() bank fallback pattern: try bank1.c first, fall back to main.c; transparently handles both multi-scene banked and single-scene HOME configurations"

requirements-completed: []

# Metrics
duration: 20min
completed: 2026-05-18
---

# Phase 10 Plan 19: MetaspriteEmissionTest D-12 Oracle Summary

**Three JVM-tier Tier-1 emission invariant tests locking the play_frame C body shape for animation index advance, flip OAM attribute, and GBC sub-palette, using extractFunctionBody brace-walk (Pattern C) against GBDKPipelineV2 output**

## Performance

- **Duration:** 20 min
- **Started:** 2026-05-18T17:30:00Z
- **Completed:** 2026-05-18T17:50:00Z
- **Tasks:** 1
- **Files modified:** 4 (1 test + 3 evidence artifacts)

## Accomplishments
- Created MetaspriteEmissionTest.kt with three @Test methods (D_12_1, D_12_2, D_12_3) using extractFunctionBody brace-walk to scope assertions to the play_frame body
- All three tests pass GREEN against Plan 15 clean generated C source
- Evidence artifacts written to tier1-shape/ before assertions for offline inspection
- Identified and handled bank1.c / main.c fallback: single-scene HOME fast-path yields main.c only; playFrameBody() is transparent to both configurations

## Task Commits

1. **Task 1: MetaspriteEmissionTest.kt — three D-12 emission invariants (anim idx, flip, subpal)** - `6b21a358` (feat)

## Files Created/Modified
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteEmissionTest.kt` - Three D-12 Tier-1 JVM emission invariant tests (D_12_1, D_12_2, D_12_3) with extractFunctionBody brace-walk helper and playFrameBody() pipeline invocation
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape/01-animation-index-advance.txt` - play_frame body evidence for D_12_1
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape/02-flip-oam-attribute.txt` - play_frame body evidence for D_12_2
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape/03-sub-palette-oam-attribute.txt` - play_frame body evidence for D_12_3

## Decisions Made
- playFrameBody() bank fallback: the BankingAnalysisPass single-scene fast-path assigns the play scene to bank 0 (HOME) for the metasprites game (1 scene, fits under HOME_BANK_SCENE_BUDGET). The pipeline then folds scene functions into main.c and omits bank1.c. Helper tries bank1.c first, falls back to main.c; adapts to both banked and folded configurations without test changes.
- Token disjunctions for D-overfitting-1 compliance: each test accepts multiple token shapes (e.g. `_idx = _idx + 1u` OR `_idx = 0u` OR `sprite_metasprites[_idx]`), ensuring tests probe the codegen oracle shape rather than incidentally testing a specific lowering detail.

## Deviations from Plan

None - plan executed exactly as written with one clarification: the plan references `bank1.c` but the metasprites single-scene game folds into `main.c` at runtime. The `playFrameBody()` helper handles this transparently via fallback, consistent with the plan's intent and D-overfitting-1 principle.

## Issues Encountered
- None significant. The bank1.c / main.c distinction was discovered during analysis and resolved cleanly in the helper implementation.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- D-12 Tier-1 oracle is locked: future codegen changes that break play_frame emission shape for animation index advance, flip OAM, or sub-palette will be caught at JVM tier before UAT
- Tests are independent of UAT plans 17 + 18 — they run against GBDKPipelineV2 directly, no ROM required
- Evidence artifacts in tier1-shape/ document the current C emission shape for review

---
*Phase: 10-port-metasprites-gbdk-example-to-gbkt*
*Completed: 2026-05-18*
