---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 26
subsystem: phase-verification/build-smoke
tags: [build-smoke, regression-sweep, verifier-gate, d-21, d-overfitting-1, ship-clearance]
requires:
  - 12-24  # 3-signal artifact + 4th-signal bank-layout artifact
  - 12-25  # (prior wave plan)
provides:
  - phase-12-final-smoke-evidence
  - phase-12-regression-sweep-evidence
  - phase-12-ship-clearance-verdict
affects:
  - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/final-smoke.md
  - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/regression-sweep.md
tech-stack:
  added: []
  patterns: [clean-buildRom-staleness-rule, gradle-continue-flag-for-sweep]
key-files:
  created:
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/final-smoke.md
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/regression-sweep.md
  modified: []
decisions:
  - "Bundle Task 3 cross-reference into Task 1's final-smoke.md commit (single-write pattern) rather than two separate commits — the 3-signal/4th-signal re-confirm is a continuation of the smoke artifact, not an independent deliverable."
  - "Use `--continue` on the 7-target sweep so a single-target failure surfaces ALL failing targets in one pass (rather than halting at the first)."
  - "Document the pre-existing `0 errors, 1 warnings` BudgetReporter baseline as NOT a Phase 12 regression — it appears on every target including non-Phase-12 examples (pong/breakout/simple-physics) and traces to advisory-level Severity.WARNING emissions in OAMAllocationPass/ConstraintCheckPass/etc., which pre-date this phase."
metrics:
  duration: 12 minutes
  completed: 2026-05-25
---

# Phase 12 Plan 26: Final Smoke + Regression Sweep Summary

Plan 12-26 is the verifier's ship-clearance gate per D-21 + user memory
`feedback_rom_build_smoke_test_for_codegen_phases.md`. It runs the FINAL
clean `:gbkt-examples:platformer-template:buildRom` PLUS the 7-target
regression sweep across all other `gbkt-examples`, captures both as
evidence artifacts, and cross-references Plan 12-24's 3-signal +
4th-signal verdicts to confirm post-rebuild determinism. Pure
build-invocation + log-capture plan — zero source modifications.

## What was built

1. **`evidence/final-smoke.md`** (Task 1 + Task 3) — Clean buildRom
   verdict for platformer-template, `.noi` bank-size validation against
   the 16 384-byte cap, SDCC/lcc warning summary, cross-reference to
   Plan 12-24 oracle-comparison.md (Signals 1+2) and
   bank-layout-signal.md, post-rebuild determinism confirmation, and
   final Ship-Clearance Verdict.

2. **`evidence/regression-sweep.md`** (Task 2) — 7-row per-target
   verdict table (pong, breakout, simple-physics, metasprites,
   metasprites-stress, banks, racer), warning/error scan, and
   anti-overfitting interpretation showing which D-12..D-15 codegen
   surfaces are exercised by sibling examples.

## Verdicts

| Gate                                          | Verdict | Evidence                                            |
| --------------------------------------------- | ------- | --------------------------------------------------- |
| D-21 final clean smoke (platformer-template)  | **GREEN** | `evidence/final-smoke.md` — EXIT 0, ROM 65 536 B, all banks ≤ 16 384 |
| D-overfitting-1 7-target regression sweep     | **GREEN** | `evidence/regression-sweep.md` — all 7 EXIT 0 |
| 3-signal (Plan 12-24) Signal 1 re-confirm     | **GREEN re-confirmed** | post-rebuild ROM bytes match 12-24 exactly (65 536) |
| 3-signal (Plan 12-24) Signal 2 re-confirm     | **GREEN re-confirmed** | post-rebuild generated-C file list unchanged |
| 3-signal (Plan 12-24) Signal 3                | **RED (UNCHANGED)** | anchor-5 visual-RED routed to Phase 12.6 per Plan 12-23 OPTION A — expected state |
| 4th-signal bank-layout re-confirm             | **GREEN re-confirmed** | per-bank .noi byte counts match 12-24 exactly |
| Post-rebuild determinism                      | **GREEN** | ROM bytes + .noi bank sizes identical to 12-24 |

**Ship-Clearance Verdict:** Phase 12 cleared for administrative
phase-close (Plan 12-27). The outstanding anchor-5 visual-RED is the
EXPECTED state at Phase 12 close per the Plan 12-23 OPTION A escalation
contract.

## Key measurements

### Task 1 — Final clean smoke

| Metric                  | Value                                                    |
| ----------------------- | -------------------------------------------------------- |
| EXIT                    | 0 (`BUILD SUCCESSFUL in 8s`)                            |
| ROM bytes               | 65 536 (matches Plan 12-24 Signal 1 exactly)            |
| Highest bank            | 2 (CODE_0 + CODE_1 + CODE_2 + HOME)                      |
| `l__CODE_0`             | 0 bytes (0% of 16 384)                                   |
| `l__CODE_1`             | 0xB84 = 2 948 bytes (18.0%)                              |
| `l__CODE_2`             | 0x17E8 = 6 120 bytes (37.4%) — max utilization           |
| `l__CODE` (cross-bank)  | 0x32CD = 13 005 bytes                                    |
| SDCC unknown addr/value | 0                                                        |
| New lcc warnings        | 0 (only pre-existing cEmit() framework warning)          |

### Task 2 — Regression sweep

| Target              | EXIT | ROM bytes | KB | Verdict |
| ------------------- | ---:| ---------:| ---: | ------- |
| pong                |   0 |    32 768 | 32 | GREEN   |
| breakout            |   0 |    32 768 | 32 | GREEN   |
| simple-physics      |   0 |    32 768 | 32 | GREEN   |
| metasprites         |   0 |    32 768 | 32 | GREEN   |
| metasprites-stress  |   0 |    32 768 | 32 | GREEN   |
| banks               |   0 |    65 536 | 64 | GREEN   |
| racer               |   0 |    65 536 | 64 | GREEN   |

## Commits

| Commit     | Description |
| ---------- | ----------- |
| `a53d8c89` | `docs(phase-12-26): Task 1 + 3 - final clean buildRom smoke + Plan 12-24 re-confirm` |
| `15c0c68e` | `docs(phase-12-26): Task 2 - 7-target regression sweep GREEN (D-overfitting-1)` |

(SUMMARY.md commit follows this file.)

## Deviations from Plan

**None — plan executed exactly as written.**

Task 3 was bundled into Task 1's commit (single-write pattern for
final-smoke.md) since the cross-reference + Ship-Clearance Verdict
sections are continuations of the smoke artifact, not independent
deliverables. The plan explicitly allows this in Task 3 step 5: "Append
a `## Cross-Reference to Plan 12-24 Three-Signal Artifact` section to
`final-smoke.md`" — written in one pass rather than as a separate
append-commit.

## Auto-fixed Issues

**None.** Plan 12-26 is admin/log-capture only — no source code touched.

## Authentication Gates

**None.** Local Gradle build only.

## Self-Check

- [x] `evidence/final-smoke.md` exists at the planned path
- [x] `evidence/regression-sweep.md` exists at the planned path
- [x] Both files committed to git (`a53d8c89`, `15c0c68e`)
- [x] Ship-Clearance Verdict section present in final-smoke.md (line 171)
- [x] All 5 verdict lines (D-21 / D-overfitting-1 / 3-signal / 4th-signal / Post-rebuild) present in Ship-Clearance section
- [x] 7-target per-target verdict table present in regression-sweep.md
- [x] No source modifications (only `.planning/phases/12-.../evidence/*.md` files added)
- [x] STATE.md / ROADMAP.md untouched (orchestrator owns those writes per worktree contract)

## Threat Flags

None. Plan is admin-only with no surface changes.

## Self-Check: PASSED

All acceptance criteria met. Ship-Clearance Verdict GREEN. Phase 12
cleared for Plan 12-27 administrative close.
