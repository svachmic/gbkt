---
phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff
plan: 01
subsystem: testing
tags: [audit, examples, mcp-emulator, buildRom, visual-evidence, keep-retire]

# Dependency graph
requires:
  - phase: 13.8
    provides: surviving example set + byte-identity baselines that this audit re-confirms at runtime
provides:
  - Empirical KEEP/RETIRE verdict per included example (the survivor-set gate)
  - evidence/AUDIT.md table (build + live MCP run-check per example)
  - 16 boot/after run-check screenshots in evidence/
affects: [14-02 retire, 14-03 baseline, 14-04 deadcode-sweep, 14-05 rename, 14-06 textual-sweep, 14-07 ci-docs, 14-08 final-regression]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Live MCP run-check (boot + one input cycle, paired screenshots) as KEEP/RETIRE evidence per Visual Evidence Rule"
    - "Orchestrator drives gbkt-emulator MCP when subagents have those tools stripped"

key-files:
  created:
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/AUDIT.md
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/*-boot.png (8)
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/*-after.png (8)
  modified: []

key-decisions:
  - "KEEP set (7): pong, breakout, simple-physics, metasprites, metasprites-stress, banks, platformer-template"
  - "RETIRE (1): racer — builds EXIT 0 but racing gameplay non-functional at runtime (D-03 confirmed)"
  - "Run-check driven by orchestrator (not the gsd-executor subagent) because spawned agents have gbkt-emulator MCP tools stripped; D-01 forbids a JVM proxy"

patterns-established:
  - "metasprites-stress + banks are codegen-exercise oracles: liveness = scene-machine/composition evidence, not rich gameplay"

requirements-completed: [Req 1]

# Metrics
duration: 35min
completed: 2026-06-06
---

# Phase 14 / Plan 01: Example Audit Summary

**Empirical KEEP/RETIRE survivor-set gate — all 8 included examples build EXIT 0; live MCP run-checks confirm 7 KEEP and racer RETIRE (D-03), backed by 16 boot/after screenshots.**

## Performance

- **Duration:** ~35 min (build audit + 8 live MCP run-checks)
- **Completed:** 2026-06-06
- **Tasks:** 2 (Task 1 build audit; Task 2 live MCP run-check)
- **Files created:** AUDIT.md + 16 screenshots

## Accomplishments
- Audited all 8 included examples with clean `:generateC` + `:buildRom` (serial, no parallel gradle clean) — all EXIT 0.
- Drove each example through the **live gbkt-emulator MCP server** (boot + one input cycle), capturing paired `evidence/<example>-{boot,after}.png` per the Visual Evidence Rule.
- Produced the empirical **KEEP set (7)** and **RETIRE (1, racer)** verdict that gates every downstream wave.

## Task Commits

1. **Task 1: buildRom + generateC audit of all 8 examples** — `93bbc4a3` (chore: AUDIT.md skeleton)
2. **Task 2: live MCP run-check + verdicts** — committed with this SUMMARY (run-check artifacts: AUDIT.md run columns + 16 screenshots)

## Files Created/Modified
- `evidence/AUDIT.md` — per-example build + run-check table with KEEP/RETIRE verdicts and evidence refs
- `evidence/{pong,breakout,racer,simple-physics,metasprites,metasprites-stress,banks,platformer-template}-{boot,after}.png` — 16 run-check screenshots

## Verdict

| KEEP (7) | RETIRE (1) |
|----------|-----------|
| pong, breakout, simple-physics, metasprites, metasprites-stress, banks, platformer-template | racer (D-03) |

- **racer:** car responds to input but the racing game is broken — static non-scrolling track box, no camera follow (`camera_x/y`=0), car drives off-screen, `racing_lap_count`/`checkpoint_idx` stuck at 0. Documented runtime failure, not a repair target.
- **metasprites-stress / banks:** codegen-exercise oracles kept on scene-machine/composition evidence (per D-06/D-07 and PLAYBOOK), not gameplay.

## Decisions Made
- Drove the run-check from the orchestrator because spawned gsd-executor subagents have the `mcp__gbkt-emulator__*` tools stripped (restricted `tools:` frontmatter, upstream bug). D-01 forbids a JVM proxy, so the orchestrator is the only path to live-emulator evidence.
- GBC examples (metasprites, metasprites-stress, platformer-template) started with `gbcMode:true` + `.noi` symFile to avoid the DMG green-tint false positive (Pitfall 4).

## Deviations from Plan
None - plan executed as specified. Task 2's live-emulator work was performed by the orchestrator rather than the subagent (subagent MCP-tool limitation), which is the D-01-compliant path; the evidence and verdicts are unchanged.

## Issues Encountered
- gsd-executor subagent could not access the gbkt-emulator MCP tools. Resolved by the orchestrator driving the run-checks directly (live emulator, no JVM fallback).

## User Setup Required
None.

## Next Phase Readiness
- **Survivor set approved by user:** KEEP = {pong, breakout, simple-physics, metasprites, metasprites-stress, banks, platformer-template}; RETIRE = {racer}.
- Wave 2 (14-02 hard-delete) is unblocked. ⚠ Carry-forward: reconcile plan 14-02's `rm -rf gbkt-examples/.archive/` against the gbkt-examples/CLAUDE.md "Do NOT delete `.archive/platformer/`" ledger note before deleting.

---
*Phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff*
*Completed: 2026-06-06*
