---
phase: 16-seed-triage
plan: "06"
subsystem: testing
tags: [triage, dsl, mcp, serializer, deprecation, gradle-plugin]

requires:
  - phase: 16-01
    provides: TRIAGE.md skeleton, evidence directory, substrate SHA
  - phase: 16-02
    provides: substrate build artifacts, substrate-test-report.txt, plugin-validate-report.txt

provides:
  - SEED-002 disposition: VERIFIED-ALREADY-FIXED (moveTo(Expr,Expr) overload exists)
  - SEED-003 disposition: RE-DEFERRED v0.2.0 (reference-faithful wrap; playable-demos trigger)
  - SEED-012 disposition: VERIFIED-ALREADY-FIXED (emulator_read/write_memory tools registered)
  - SEED-020 disposition: CONFIRMED-OPEN → Phase 21 FIX-06 (10 stubs still in deserializeGameIR)
  - SEED-023 disposition: CONFIRMED-OPEN → Phase 18 DEPR-01 (whenever() not @Deprecated)
  - SEED-025 disposition: CONFIRMED-OPEN → Phase 18 DEPR-02 (String overload present @Deprecated)
  - SEED-026 disposition: VERIFIED-ALREADY-FIXED (validatePlugins+pluginTest GREEN at substrate SHA)
  - TODO-triggersystem disposition: CONFIRMED-OPEN → Phase 21 FIX-06 (no registry validation)
  - 8-row cluster-dsl.md draft for TRIAGE.md merge

affects: [16-seed-triage, Phase 18, Phase 21]

tech-stack:
  added: []
  patterns: [Serena-symbolic-source-inspection for CONFIRMED-OPEN/VERIFIED-ALREADY-FIXED verdicts]

key-files:
  created:
    - .planning/phases/16-seed-triage/evidence/SEED-002/source-inspection.txt
    - .planning/phases/16-seed-triage/evidence/SEED-003/evidence.txt
    - .planning/phases/16-seed-triage/evidence/SEED-012/source-inspection.txt
    - .planning/phases/16-seed-triage/evidence/SEED-020/evidence.txt
    - .planning/phases/16-seed-triage/evidence/SEED-023/source-inspection.txt
    - .planning/phases/16-seed-triage/evidence/SEED-025/source-inspection.txt
    - .planning/phases/16-seed-triage/evidence/SEED-026/evidence.txt
    - .planning/phases/16-seed-triage/evidence/TODO-triggersystem-validation/source-inspection.txt
    - .planning/phases/16-seed-triage/evidence/_drafts/cluster-dsl.md
  modified: []

key-decisions:
  - "SEED-003 dispositioned RE-DEFERRED: wrap is intentional reference-faithful behavior; trigger is a future 'playable demos' milestone, not v0.1.1 Hardening"
  - "SEED-002/012/026 all VERIFIED-ALREADY-FIXED: overload, MCP tools, and Gradle hygiene were all fixed post-planting"
  - "SEED-020 CONFIRMED-OPEN: substrate tests GREEN because test fixtures don't exercise 10 stubbed collections; source confirms all stubs present"
  - "TODO-triggersystem RED repro shaped: triggerSystem(SystemRef('nonexistent')) should throw at build() but does not"

patterns-established:
  - "Serena symbolic search is sufficient for source-only disposition verdicts — no full-file reads required"
  - "Substrate test report + source inspection together form the CONFIRMED-OPEN evidence bar for serializer stubs"

requirements-completed: [TRIAGE-01]

duration: 30min
completed: 2026-06-12
---

# Phase 16, Plan 06: DSL/Lang/Tooling Cluster Triage Summary

**3 VERIFIED-ALREADY-FIXED, 2 CONFIRMED-OPEN → Phase 18, 2 CONFIRMED-OPEN → Phase 21, 1 RE-DEFERRED; 8-row cluster-dsl.md draft ready for TRIAGE.md merge**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-06-12
- **Completed:** 2026-06-12
- **Tasks:** 2
- **Files modified:** 9 created (evidence files + cluster draft)

## Accomplishments

- Triaged all 8 DSL/lang/tooling cluster entries (SEED-002/003/012/020/023/025/026 + TODO-triggersystem) via Serena symbolic source inspection and substrate test report — no Gradle invoked, no full-file reads
- Discovered 3 already-fixed seeds (moveTo overload, MCP read/write memory tools, validatePlugins hygiene) that were planted before the fixes landed — reduces Phase 18/21 scope
- Shaped Phase 21 FIX-06 RED repros for SEED-020 (serialize/deserialize zones) and TODO-triggersystem (triggerSystem registry check) per D-07
- Produced 8-row cluster-dsl.md draft with dispositions, evidence paths, routing, and repro shapes

## Task Commits

1. **Task 1: SEED-002/003/012/020 + triggersystem source/test triage** — `4445edc7`
2. **Task 2: SEED-023/025/026 + cluster-dsl.md draft** — `3b8ac9b0`

## Files Created/Modified

- `evidence/SEED-002/source-inspection.txt` — moveTo(Expr,Expr) overload exists at ActorBuilder.kt:335-346; VERIFIED-ALREADY-FIXED
- `evidence/SEED-003/evidence.txt` — no bounds clamping in generated C; PLAYBOOK.md misclaim present; RE-DEFERRED (v0.2.0)
- `evidence/SEED-012/source-inspection.txt` — emulator_read/write_memory tools registered in ToolHandlers.kt; VERIFIED-ALREADY-FIXED
- `evidence/SEED-020/evidence.txt` — 10 emptyList() stubs confirmed in deserializeGameIR; substrate tests GREEN but don't cover stubs; CONFIRMED-OPEN → Phase 21 FIX-06
- `evidence/SEED-023/source-inspection.txt` — whenever() has no @Deprecated; KDoc defers to future phase; CONFIRMED-OPEN → Phase 18 DEPR-01
- `evidence/SEED-025/source-inspection.txt` — combatIsInState(String,String) still present @Deprecated(ReplaceWith); CONFIRMED-OPEN → Phase 18 DEPR-02
- `evidence/SEED-026/evidence.txt` — validatePlugins=PASS, pluginTest=174/0 at substrate SHA; VERIFIED-ALREADY-FIXED
- `evidence/TODO-triggersystem-validation/source-inspection.txt` — triggerSystem() no registry check; build() no validation; RED repro shaped; CONFIRMED-OPEN → Phase 21 FIX-06
- `evidence/_drafts/cluster-dsl.md` — 8-row TRIAGE fragment draft with dispositions, evidence, routing, D-07 repros

## Decisions Made

- **SEED-003 → RE-DEFERRED**: The wrap-around is intentional, reference-faithful behavior (matches GBDK phys.c exactly). The seed's own trigger condition is "playable demos or examples polish milestone" — that milestone hasn't opened. The PLAYBOOK.md misclaim is packaged with the same seed. Deferring both to v0.2.0 keeps v0.1.1 Hardening focused.
- **SEED-020 → CONFIRMED-OPEN despite GREEN tests**: The 15 passing GameIRSerializerTest tests cover scenes/actors/ScriptOps — none exercise the 10 stubbed collections. Source confirms all 10 stubs remain.
- **SEED-025**: Although the seed text says "v0.1.0 shipped the String overload un-deprecated," source inspection shows it IS @Deprecated at HEAD. The deprecation annotation was added post-planting; the remaining work is REMOVAL.

## Deviations from Plan

None — plan executed exactly as written. All inspections used Serena MCP tools; no full-file reads; no Gradle invoked in this plan.

## Issues Encountered

None. SEED-002/012/026 being already-fixed was expected by the cluster pre-analysis (RESEARCH.md §Cluster D: "SEED-002: CONFIRMED-OPEN" was a conservative pre-estimate — actual evidence showed it was fixed). The source inspection definitively resolved the uncertainty.

## Next Phase Readiness

- cluster-dsl.md is ready to merge into TRIAGE.md in the phase-close plan
- SEED-023 and SEED-025 → Phase 18 (DEPR-01/DEPR-02): dispositions and evidence complete
- SEED-020 and TODO-triggersystem → Phase 21 (FIX-06): RED repros shaped; accepting phase has concrete test specs
- SEED-003 → v0.2.0 backlog: will be moved to .planning/backlog/v0.2.0/ in phase-close plan
- 3 VERIFIED-ALREADY-FIXED seeds (SEED-002/012/026) will be archived in phase-close plan

---
*Phase: 16-seed-triage*
*Completed: 2026-06-12*
