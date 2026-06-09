---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 09
subsystem: phase-execution / codegen-diagnostics
tags: [buildrom, codegen, savesystem, banked, first-build, named-bug]
status: paused-at-checkpoint
requires:
  - 11-05 (Banks.kt DSL substrate; produces gbkt-examples/banks/)
  - 11-06 (BanksIRTest IR baseline)
  - 11-07 (BanksEmissionTest INV-1 GREEN / INV-2 RED routing)
  - 11-08 (deferred — placeholder JVM lock for Plan 11-10 contract)
provides:
  - evidence/first-buildrom.log (185-line verbatim build capture; EXIT_CODE=1)
  - evidence/named-bug.md (Candidate 1 named for Plan 11-10 fix)
  - Checkpoint payload for human-gate Task 3
affects:
  - Plan 11-10 (next plan; scope bound by named-bug.md)
  - Plan 11-14 (seed sweep — surplus INV-2 and D11-05-1 routed here)
tech-stack:
  added: []
  patterns:
    - "First-build bug-naming discipline (CONTEXT D-13 inherited from Phase 9/10)"
    - "One-bug scope cap with surplus → seeds routing (D-14)"
key-files:
  created:
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/11-09-SUMMARY.md
  modified: []
decisions:
  - "Named bug for Plan 11-10 = Candidate 1 (trigger_saves stub in visitSaveSystem) — sole compile-time blocker; HIGH-probability RESEARCH prediction confirmed verbatim by lcc linker output."
  - "Surplus deferred to Plan 11-14 seed sweep: INV-2 (_bkg_tiles_load_banked gating) + D11-05-1 (trampoline body inheritance). Both reproduced by the first-buildrom evidence."
metrics:
  buildrom_exit_code: 1
  buildrom_outcome: "BUILD FAILED at compileRom (lcc link stage); generateC SUCCEEDED"
  log_path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log"
  log_lines: 185
  named_bug_count: 1
  surplus_seeds_count: 2
  duration_seconds: ~360
  completed_date: 2026-05-20
---

# Phase 11 Plan 09: First-buildrom Bug Naming Summary

First clean `:gbkt-examples:banks:buildRom` against the Plan 11-05 DSL surfaces the
RESEARCH-predicted Candidate 1 (`trigger_saves` stub missing from
`GBDKSystemVisitor.visitSaveSystem`); Plan 11-10's scope is now bound to that
single visitor-file fix, with two surplus codegen defects routed to the
Plan 11-14 seed sweep.

## Status: PAUSED at Task 3 checkpoint (human-verify, blocking)

Per the plan's `type="checkpoint:human-verify" gate="blocking"`, Tasks 1 and 2
ran to completion and were committed; Task 3 awaits human review of the
named-bug scope before Plan 11-10 is allowed to begin. **This summary
documents the executor-side state; the orchestrator will mark the plan
complete only after the human gate approves the named-bug scope.**

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Run first buildRom and capture log | `7e689d6b` | `evidence/first-buildrom.log` (185 lines) |
| 2 | Write `named-bug.md` based on log outcome | `d5cc89e3` | `evidence/named-bug.md` (Branch A — Candidate 1) |

## buildRom Outcome (Task 1 evidence)

- **Toolchain:** `GBDK_HOME=/Users/michalsvacha/gbdk`; `lcc` at `/Users/michalsvacha/gbdk/bin/lcc`.
- **Command:** `./gradlew :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom --console=plain`.
- **Result:** `BUILD FAILED in 6s` / `EXIT_CODE=1`.
- **`generateC` SUCCEEDED:** 5 C files emitted (`main.c` 260L, `bank1.c` 41L,
  `game.h` 113L, `zone_bank2.c` 7L, `game_metadata.json` 53L; 474 total
  lines). Budget report: 1.8 KB ROM, 1/4 banks used, MBC5_RAM_BATTERY,
  mbcType=0x1B (matches reference Makefile `-Wl-yt0x1B` per RESEARCH Pitfall 5
  recommendation).
- **`compileRom` FAILED at lcc link stage** with:
  ```
  ?ASlink-Warning-Undefined Global '_trigger_saves' referenced by module 'bank1'
  bank1.c:26: warning 112: function 'trigger_saves' implicit declaration
  bank1.c:26: warning 84: 'auto' variable 'trigger_saves' may be used before initialization
  ```

## Named Bug (Task 2 — for Plan 11-10)

- **Class:** Candidate 1 — `SaveSystem` has no `trigger_<id>()` trampoline.
- **File:** `gbkt-backend-gbdk/.../codegen/visitor/GBDKSystemVisitor.kt`.
- **Site:** `visitSaveSystem()` opens at line 299; returns
  `listOf(saveGame, loadGame)` at line 485 — no `trigger_<id>` `CFunction` is
  ever constructed.
- **Blast radius:** ONE function in ONE visitor file. No IR additions, no
  new `ScriptOp` class, no new C AST nodes (uses existing
  `CFunction`/`CCall`/`CVar`/`CExprStatement`). Stays inside the one-commit
  budget Plan 11-10 was sized for; safely within the
  `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` line.
- **Lock contract:** Plan 11-10 will add the INV-4 JVM-tier emission
  invariant in `BanksEmissionTest` (`mainC.contains("trigger_saves")` AND
  `extractFunctionBody(mainC, "trigger_saves").contains("save_game_saves(")`).

## Surplus → Plan 11-14 seed sweep

Per CONTEXT D-13 + D-14, the first-buildrom also reproduced two additional
defects that are NOT folded into Plan 11-10:

| # | Defect | Routing source | First-buildrom evidence |
|---|--------|----------------|--------------------------|
| 1 | INV-2: `_bkg_tiles_load_banked` helper absent from `main.c` | Plan 11-07 `evidence/inv2-failure.txt` (RED-by-design routing) | `grep -E "_bkg_tiles\|SWITCH_ROM\|set_bkg_tiles" main.c` returns zero matches |
| 2 | D11-05-1: `title_enter_trampoline()` body delegates to `pause_enter()` | Plan 11-05 `deferred-items.md` | `main.c:202-209` reproduces the symptom verbatim — `// Trampoline: pause_enter (bank 1)` comment header on `title_enter_trampoline()` plus `pause_enter();` body |

Both have one-line dispositions ready for the `/gsd-capture --seed` step
when Plan 11-14 runs.

## Why Candidate 1 (and not the others) is the named bug

1. **Sole compile-time blocker.** lcc link fails before any of the runtime
   defects could be tested. The ROM literally cannot be produced.
2. **HIGH-probability RESEARCH prediction confirmed verbatim.** Pitfall 4 in
   `11-RESEARCH.md` named the exact symptom (`undefined identifier
   'trigger_saves'`); the log shows the SDCC/asLink equivalent
   (`?ASlink-Warning-Undefined Global '_trigger_saves'`).
3. **Tight blast radius.** Plan 11-10's one-commit budget holds; no risk of
   the wide-blast escalation guarded by
   `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`.
4. **Plan 11-10 can additionally LOCK the contract** with INV-4 emission
   invariant — the productive cousin of the Branch C "absence-of-bug" path.

## Decisions Made

- **Branch A** of Task 2 taken (log contains `Undefined Global
  '_trigger_saves'`). Branches B (different-bug) and C (no-bug) ruled out by
  the log evidence.
- **One bug only** per CONTEXT scope cap. INV-2 and D11-05-1 explicitly
  routed away from Plan 11-10 to the seed sweep.

## Deviations from Plan

None — plan executed exactly as written. Both Task 1 and Task 2 hit their
acceptance criteria on the first attempt; no auto-fix Rule 1/2/3 was
needed.

## Self-Check: PASSED

- `evidence/first-buildrom.log` — exists (185 lines, contains `BUILD FAILED`
  + `EXIT_CODE=1`).
- `evidence/named-bug.md` — exists (one `Bug class:` line, all 6 mandatory
  fields present, specific file path under `**File:**`, specific UAT anchor
  under `**UAT anchor blocked:**`).
- Commit `7e689d6b` (Task 1) — present in `git log --oneline -3`.
- Commit `d5cc89e3` (Task 2) — present in `git log --oneline -3`.
- Worktree HEAD on `worktree-agent-aeca1d21d5e148ba0`; base = expected base
  `f3c1cb72`; no protected-ref drift.

## Awaiting human gate (Task 3)

Per the plan's checkpoint resume-signal: human verifies that the named bug
class matches the log evidence, the fix spec is concrete, and only ONE bug
is named (CONTEXT scope cap honoured). Approve → spawn Plan 11-10 with this
scope. Or `route to new phase` → if the reviewer judges blast-radius wider
than one function. (Executor-side judgement: blast-radius is bounded; the
existing `visitGenericSystem` analog at lines 2616-2631 confirms the shape
is already in the codebase.)
