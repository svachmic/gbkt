# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v0.1.0 — MVP Compiler Pipeline Rebuild

**Shipped:** 2026-06-09
**Phases:** 66 (incl. decimals) | **Plans:** 652 | **Tasks:** 887

### What Was Built
- A clean compiler pipeline replacing the string-concatenating prototype: Kotlin DSL → non-sealed IR + visitor dispatch → 9 ordered analysis passes (automatic bank/VRAM/OAM/RAM allocation) → structured C AST → GBDK C.
- 20-module layered architecture with ServiceLoader genre plugins (RPG, platformer, puzzle, sport), an embedded Coffee-GB emulator, a JVM ScriptOp test runner, and a 17-tool MCP server for agent-driven UAT.
- A GBDK SDK reference-port validation track (simple_physics → metasprites → banks → platformer_template) using GBDK reference C as a byte-identity codegen oracle, with binding visual UAT sign-offs.
- A hard full-green release gate: the entire pre-existing-red JVM suite driven green diagnose-first (Phase 15), zero threshold-weakening, zero production-codegen drift.

### What Worked
- **Byte-identity codegen oracles.** Pinning generated-C SHA-256 against GBDK reference output caught regressions a runtime test would have missed, and made "did this refactor change codegen?" a one-line check (used as the D-02 split guard in Phase 15).
- **Non-sealed IR + visitor pattern.** Replacing sealed `when` with open interfaces + visitor dispatch was the enabling decision for the 20-module split and for genre packages extending the IR without touching core.
- **Diagnose-first discipline.** Phases that root-caused before fixing (12.9, 13.3, 13.4, 15) consistently falsified their first hypothesis — the discipline prevented shipping plausible-but-wrong fixes.
- **Terminal-subphase contract.** Capping defect clusters in a decimal subphase (no X.Y.1 follow-ups; surplus flexes to the next plan number) kept the phase tree from fractally exploding under the platformer-template debugging.

### What Was Inefficient
- **The platformer-template port (Phase 12) sprawled.** Closing its palette/collision/sprite defects took 8+ rounds across 12.1–12.11 and spilled into 13.3–13.8 — a single reference example consumed a disproportionate share of the milestone.
- **Paying to confirm the obvious.** Phase 12.8 structured plans around "try the cheap fix, let the binding gate catch the failure" when the diagnostic evidence already said the cheap fix was insufficient — costing a shipped-then-reverted regression. (Codified as `feedback_dont_pay_to_confirm_obvious`.)
- **Shipping a red suite into the release phase.** Phase 14 (cleanup) left 7 pre-existing failing tests and tried to close on a `:buildRom`+byte-identity carve-out; that sign-off was correctly withheld, forcing a whole extra release-gate phase (15) to reach green.
- **Worktree drift.** `isolation=worktree` agents leaked commits onto the parent branch and orphaned branches, requiring reflog/fsck recovery. (Codified as `feedback_claude_code_worktree_drift_quirks`.)

### Patterns Established
- **Visual Evidence Rule** — for "X is visible on screen" truths, a variable-state assertion is necessary but never sufficient; runtime screenshots / live human sign-off are required. (Codified into CLAUDE.md after a Phase 07.4 false-positive.)
- **Full-green release gate** — a cleanup/release milestone must leave a tree that works end-to-end; a red suite is not acceptable, and "pre-existing / out-of-scope" is not a release-time excuse.
- **Quality over shortcuts** — reach green only by fixing a real bug or correcting a *provably*-stale assertion, never by weakening a threshold.
- **Route wide blast radius to a proper phase** — system-wide codegen/type changes go through spec → discuss → plan (with research), not inline recommendations.

### Key Lessons
1. Codegen GREEN ≠ visual correctness. The most expensive bug class this milestone (Phase 07.4 track-render, then the platformer palette/collision saga) was masked by variable-state evidence; demand pixels for visual truths.
2. Don't defer the test suite to the end. Pre-existing red tests accumulated silently behind a `:buildRom`-only acceptance gate and became a release blocker; gate on the full suite continuously.
3. When diagnostic evidence already says the cheap fix won't work, route to the real fix from the start — the binding gate is a safety net, not a low-cost experiment.
4. The `phase.complete` SDK walks the roadmap top-down and mis-reports the next phase after a decimal pitstop; always cross-check the parent's plan count + the STATE.md resume breadcrumb.

### Cost Observations
- Model mix / session count: not tracked this milestone (instrument for v0.2.0).
- Notable: heavy use of MCP emulator + Serena symbolic tools for diagnose-first cycles; a from-clean build OOM (no Gradle/Kotlin heap configured) masked a release-gate gap until the heap was pinned on the final day.

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v0.1.0 | n/t | 66 | Established diagnose-first, byte-identity oracles, Visual Evidence Rule, and the full-green release gate |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v0.1.0 | full JVM suite green (`test --continue` 0 failures; pluginTest IntegrationTest 19/0/0/0) | n/t | gbkt-ir (zero-dependency leaf module) |

### Top Lessons (Verified Across Milestones)

1. Codegen GREEN is necessary but never sufficient for visual truths — demand runtime evidence. *(established v0.1.0; verify it holds next milestone)*
2. Gate releases on the full suite continuously; deferred red tests compound. *(established v0.1.0)*
