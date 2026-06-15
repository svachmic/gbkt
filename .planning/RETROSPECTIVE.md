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

## Milestone: v0.1.1 — Hardening

**Shipped:** 2026-06-15
**Phases:** 7 | **Plans:** 79 | **Tasks:** 110

### What Was Built

- **Phase 16 — Seed Triage:** A 47-row TRIAGE.md with every seed terminally dispositioned against a pinned substrate SHA (24 VERIFIED-ALREADY-FIXED, 12 CONFIRMED-OPEN, 10 RE-DEFERRED). A binding human visual review gate (D-08) locked all 10 visual-seed verdicts. This became the authoritative gate for the four FIX phases that followed.
- **Phase 17 — Docs Reconciliation and Quality Cleanup:** 13 stale DSL_REFERENCE.md sections rewritten to match the implemented DSL; `TargetProfiles.GAME_BOY_SCREEN` canonical preset added; 8 in-scope magic-pixel literals replaced with `GameBoyConstants.SCREEN_WIDTH/HEIGHT`; `./gradlew detekt` driven to zero violations with zero baseline files.
- **Phase 18 — Deprecation Removals and Sonar Burn-down:** `whenever` API hard-removed (80+ call sites migrated to `runIf`); deprecated `combatIsInState(String,String)` overload removed; CONTRIBUTING.md deprecation convention documented; 46 SonarCloud S3776 HIGH findings cleared to 0 via extract-method across 29 EMITTING commits — zero NOSONAR annotations used.
- **Phases 19–21 — Codegen Fix Cluster:** FIX-01..06 — metasprite visual parity + structural emission guards; banks trio re-verified; tRNS sprite outline confirmed fixed; `pivotAdjust(Int)` DSL lift; `gameUsesTilemapCollisionPathC` predicate consolidated; GameIRSerializer 10 deserialization stubs replaced with real round-trip tests. `.planning/seeds/` reached empty.
- **Phase 22 — Golden Evidence Storage Overhaul:** Replaced the per-phase `EVIDENCE_DIR` pattern (27 test classes, 143 tracked files) with central ROM+anchor-keyed immutable goldens; `discoverFiles` GBC auto-detect from ROM byte 0x143 so per-test `.copy(gbcMode = true)` is gone; 22 binding goldens migrated byte-identically from Phases 19/20/21; clean-tree `./gradlew test` leaves zero new untracked files.

### What Worked

- **Triage-first discipline (Phase 16 gate).** Running a binding diagnostic triage before any fix work meant the four FIX phases knew exactly what was already fixed and what required new coverage — preventing redundant work and confirming scope accurately. Most "open" seeds turned out to be VERIFIED-ALREADY-FIXED, which the gate surfaced cheaply.
- **Byte-identity oracle across 17 Sonar waves.** Requiring a 7-example byte-identity ROM sweep per S3776 commit made it safe to do 29 consecutive extract-method refactors in the codegen visitor layer with zero regressions — each commit was independently verified. The discipline also made the Phase 22 golden migration straightforward: "byte-identical" is a falsifiable claim.
- **Extract-method as the S3776 fix strategy.** All 46 findings closed via pure extract-method (no NOSONAR). This produced cleaner, more testable code rather than hiding complexity.
- **Phase 22 timing.** Closing the evidence-storage overhaul as the final v0.1.1 phase — before merging the PR — prevented the "archived phases regenerate as untracked garbage" problem from entering the main branch. The fix of the root cause lived in the same milestone as the evidence it was meant to preserve.

### What Was Inefficient

- **Phase 18 sprawl (27 plans, 17 waves).** Breaking Sonar burn-down into per-finding plans was granular enough to maintain oracle hygiene but created a long sequential wave chain. A coarser batch strategy with a shared ROM sweep at the wave boundary would have been faster.
- **Triage underestimated the CONFIRMED-OPEN work.** Phase 16 dispositioned 12 CONFIRMED-OPEN seeds; four FIX phases were needed. The research-phase scope model held, but it would have been faster to budget 2-3 combined FIX phases from the start rather than four separate phase directories.
- **Phase 22 arrived late.** The per-phase evidence-dir pattern was present since Phase 07.9. Addressing it earlier would have prevented 143 tracked per-phase evidence files accumulating across the milestone.

### Patterns Established

- **Triage gate before FIX phases.** A structured triage with evidence-backed dispositions, pinned substrate SHA, and binding human visual sign-off is required before any codegen-fix phase. Without it, fix work risks re-fixing already-closed issues or closing seeds with insufficient evidence.
- **Byte-identity oracle per emitting commit.** Every commit that touches visitor codegen or GBDKPipeline.kt must carry a 7-example byte-identity ROM sweep as exit evidence. This is now a first-class process rule, not an optional check.
- **Evidence storage must be central and immutable.** Per-phase `EVIDENCE_DIR` patterns scatter proof across an ever-growing directory tree; archived phases regenerate on every test run. Central ROM+anchor-keyed goldens with a dedicated re-baseline command are the correct model — establish at the start of a milestone, not the end.
- **Zero-NOSONAR S3776 remediation.** Extract-method can close every S3776 finding in the codebase without NOSONAR annotations; the annotations should not be used for complexity-hiding.

### Key Lessons

1. A triage phase is worth its weight. Running Phase 16 before any fix work saved significant time by confirming 24 of 44 seeds as already-fixed before a single production line was touched.
2. Evidence hygiene is a first-class deliverable. The Phase 22 root cause — 27 test classes hardcoding per-phase paths — accumulated silently over 7 phases because evidence storage was treated as a detail, not a deliverable. Establish the storage pattern at milestone start.
3. Long sequential Sonar waves work but are slow. Per-finding granularity is correct for the oracle, but the bottleneck is the wave chain length, not the per-wave cost. Batch by visitor file, not by finding.
4. The Visual Evidence Rule held perfectly. Every visual seed (FIX-01..04) required a fresh GBC-mode runtime screenshot as closure evidence. No variable-state assertion was accepted as a substitute.

### Cost Observations

- Sessions / model usage: not tracked (instrument for v0.2.0).
- S3776 strategy: 29 EMITTING commits + 11 non-emitting commits; zero NOSONAR; every emitting commit byte-identity checked.
- Phase 22 code-review: 5 Warnings fixed in-phase (incl. broken `-Pgbkt.updateGoldens` bless path; regression gate caught metasprites DMG-build gap same class as platformer 22-07 fix).

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v0.1.0 | n/t | 66 | Established diagnose-first, byte-identity oracles, Visual Evidence Rule, and the full-green release gate |
| v0.1.1 | n/t | 7 | Added triage-gate-before-fix pattern; zero-NOSONAR S3776 discipline; central immutable golden evidence storage |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v0.1.0 | full JVM suite green (`test --continue` 0 failures; pluginTest IntegrationTest 19/0/0/0) | n/t | gbkt-ir (zero-dependency leaf module) |
| v0.1.1 | full JVM suite green; detekt zero violations; SonarCloud S3776 HIGH → 0 | n/t | central goldens dir (22 immutable binding goldens); `GameBoyConstants` screen constants |

### Top Lessons (Verified Across Milestones)

1. Codegen GREEN is necessary but never sufficient for visual truths — demand runtime evidence. *(established v0.1.0; confirmed v0.1.1)*
2. Gate releases on the full suite continuously; deferred red tests compound. *(established v0.1.0; confirmed v0.1.1)*
3. A triage gate before fix work is worth its weight — confirms which bugs are already fixed before any production line is touched. *(established v0.1.1)*
4. Evidence storage is a first-class deliverable, not a detail. Per-phase scatter accumulates silently; establish central immutable goldens at milestone start. *(established v0.1.1)*
