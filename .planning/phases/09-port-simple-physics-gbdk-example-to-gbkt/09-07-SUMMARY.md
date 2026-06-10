---
phase: 09-port-simple-physics-gbdk-example-to-gbkt
plan: 07
subsystem: planning
tags: [phase-close, c-diff, oracle-comparison, three-signal, seed-harvest, roadmap-update, d-05, d-09-part-3]

# Dependency graph
requires:
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt-04
    provides: "Bug A fix landed; SEED-002 captured (Bug B)"
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt-05
    provides: "Clean-compile + ROM-size evidence; oracle-comparison.md §Codegen-quality filled; deferred-items.md (DEFERRED-09-01, DEFERRED-09-02)"
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt-06
    provides: "3/3 D-01 UAT behaviors GREEN with visual confirmations; oracle-comparison.md §UAT verdict filled; Plan-06 planner-arithmetic discrepancy noted as informational seed candidate"
provides:
  - "`evidence/c-diff.md` — D-09 part 3 informational C-diff appendix; side-by-side mapping of phys.c vs gbkt main.c+bank1.c with shorter/equal/longer regions + DSL value verdict"
  - "`evidence/oracle-comparison.md` finalized — §DSL value populated, §Seeds harvested inventoried, §Three-signal summary PASS/PASS/PASS table, §Phase 9 verdict: SHIPPED WITH CAVEATS"
  - "`.planning/ROADMAP.md` — `Phase 9.1: simple_physics surplus codegen defects (INSERTED)` placeholder inserted per D-05 conditional (PHASE9_SEEDS=1 ≥ 1)"
  - "Per-port close methodology proven for Phase 10/11/12 inheritance: three-signal verdict + C-diff appendix + seed harvest + conditional follow-up insert"
affects: [10-port-metasprites-gbdk-example-to-gbkt, 11-port-banks-gbdk-example-to-gbkt, 12-port-platformer-template-gbdk-example-to-gbkt, 09.1-simple-physics-surplus-codegen-defects]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-port three-signal close pattern: (1) C-diff appendix (informational; anti-overfitting-respecting), (2) oracle-comparison.md three-signal table (codegen quality + DSL value + UAT verdict), (3) seed inventory + conditional ROADMAP follow-up insert"
    - "D-05 conditional insert via in-place ROADMAP.md edits (top phases list + detail section) — Phase 9.1 placeholder anchored on SEED-002 alone (Phase-9-originated; SEED-001 predates Phase 9 and is excluded per the strict reading of plan-07 Step 1)"
    - "Anti-overfitting C-diff rationale: 'longer' regions in gbkt-generated C are framework value-add (HOME-bank scaffolding, scene dispatcher, OAM/sound/dialog/fade helpers) OR DSL-ergonomics seeds (Bug B → SEED-002). Never framed as 'gbkt should look more like phys.c' — explicit anti-overfitting guard in c-diff.md"

key-files:
  created:
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/c-diff.md
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-07-SUMMARY.md
  modified:
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/oracle-comparison.md
    - .planning/ROADMAP.md

key-decisions:
  - "PHASE9_SEEDS = 1 (SEED-002 only). SEED-001 (IDE & Tooling) predates Phase 9 per its planted_during metadata ('v1.0 / Phase 07.9 closeout') and the chronology (commit c4af1276 added SEED-001 BEFORE commit 594f3314 that added Phases 9-12 to ROADMAP) — excluded from the D-05 threshold."
  - "Phase 9.1 INSERTED per D-05 conditional (≥ 1 surplus seed). Placeholder only — to be scoped via /gsd-discuss-phase 9.1. Includes optional codegen-hygiene candidates (DEFERRED-09-01 scaffolding warnings, DEFERRED-09-02 MBC5 upgrade) as ergonomic adjacencies for the discussion to consider."
  - "Per parallel-executor worktree merge contract from the orchestrator: DO NOT flip Phase 09's top-list '- [ ]' → '- [x]' (orchestrator runs phase.complete after verification — that path writes the completion checkbox + date). DO NOT flip the per-plan '- [ ] 09-07-PLAN.md' inside the Phase 9 detail block (same orchestrator domain). DO write the Phase 9.1 placeholder INSERT (novel to plan 07; orchestrator does not handle this). This split was the orchestrator's explicit instruction."
  - "Surplus seed inventory in oracle-comparison.md §Seeds harvested distinguishes file-based seeds (SEED-002 — counts toward D-05 threshold) from informational discoveries (DEFERRED-09-01, DEFERRED-09-02, MCP-wiring nit, planner-arithmetic gap — captured in existing artifacts; do NOT count toward threshold; documented for completeness)."
  - "C-diff appendix is framed as informational from Purpose paragraph onward; every 'longer region' in gbkt-generated C is paired with a rationale (framework value-add OR captured-as-seed) — explicit anti-overfitting guard (D-overfitting-2)."
  - "Plan-07 verify regex requires 'completed 2026-05-13' in ROADMAP.md — that string will appear when the orchestrator runs phase.complete on Phase 9 after this plan's SUMMARY is verified. Self-check below does NOT assert it directly — that's outside this executor's scope per the orchestrator instruction."

requirements-completed: [D-05, D-09, D-10]

# Metrics
duration: ~20m
completed: 2026-05-13
---

# Phase 9 Plan 7: Port Close — C-Diff, Oracle Finalization, Seed Harvest, Roadmap Update

**Closed Phase 9 by producing the D-09 part 3 informational C-diff appendix, finalizing oracle-comparison.md with §DSL value + §Seeds harvested + §Three-signal summary (PASS/PASS/PASS) + §Phase 9 verdict (SHIPPED WITH CAVEATS), inventorying surplus seeds (PHASE9_SEEDS = 1: SEED-002), and inserting the `Phase 9.1: simple_physics surplus codegen defects (INSERTED)` placeholder into ROADMAP.md per the D-05 conditional. The Phase 09 `[x]` flip + completion date are deferred to the orchestrator's `phase.complete` write per the parallel-executor worktree merge contract.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-05-13 (worktree agent spawn)
- **Tasks:** 2 (both autonomous; both single-commit)
- **Files created:** 2 (`evidence/c-diff.md`, this SUMMARY.md)
- **Files modified:** 2 (`evidence/oracle-comparison.md`, `.planning/ROADMAP.md`)

## C-Diff Verdict

The three-column side-by-side mapping in `evidence/c-diff.md` covers all of
`phys.c` L37-94 (the active per-frame physics body + variable declarations
+ jump impulse + decel ladder + position integration + sub-pixel→pixel
conversion). 13 mapping rows total.

**Shorter (gbkt DSL is dramatically clearer at user surface):**
- Variable declarations (decl+init fused; reference splits)
- Sprite asset (`sprite(asset(...))` vs 64-byte inline hex array)
- Frame loop (zero DSL lines; framework emits joypad poll + scene dispatch
  + OAM sync + sound tick + vsync)
- Input edge detection (`buttons.a.pressed` vs `INPUT_PROCESS` macro + state
  comparison)

**Equal (line-count parity, structural parity):**
- D-pad accel + clamp branches (4 axis blocks — 3 lines DSL vs 3 lines C)
- A-press jump impulse
- Position integration (`posX += spdX`)

**Longer (gbkt generates more C than reference):**
- HOME-bank scaffolding (~130 lines: joypad helpers, OAM sync, sound driver,
  dialog/fade helpers, scene dispatcher) — **framework value-add, not a defect**
- Actor position-sync glue (Bug B workaround — 2 DSL lines vs 1
  `move_sprite(...)` call) — **captured as SEED-002**
- Decel ladder lowered as two independent `if` blocks (4 lines gen-C vs
  1 line reference `if/else if`) — **DSL author chose `whenever` blocks
  for Pitfall 4 anti-shadowing; observable behavior matches reference exactly**

**DSL value verdict:** gbkt is dramatically shorter overall for the
user-authored game-logic surface. Longer in HOME-bank scaffolding (the
framework's resource-management value-add). 14-byte `l__CODE` delta (1.025×
reference) is the per-frame cost amortized across the framework's primitives
for THIS minimal game; non-trivial games (Phase 10/11/12) will improve the
ratio.

## Surplus Seed Inventory

PHASE9_SEEDS = 1 (file-based, Phase-9-originated):

- **SEED-002** — `ActorRef.moveTo(Expr, Expr)` overload
  (`.planning/seeds/SEED-002-actor-moveto-expr-overload.md`).
  Captured during Plan 04. Status: dormant. Scope: small.
  Trigger: future port or DSL ergonomics milestone.

Informational discoveries (NOT counted toward D-05 threshold; captured in
existing artifacts):

- **DEFERRED-09-01** — gbkt scaffolding emits 4 pre-existing SDCC warnings
  (84/85/85/126) on `delay_frames` / `show_sprites_range` / `main`.
  Platform-wide, pre-existing. Out of scope per SCOPE BOUNDARY. Listed as
  Phase 9.1 codegen-hygiene candidate. Documented in `deferred-items.md`.
- **DEFERRED-09-02** — single-scene games auto-upgrade ROM_ONLY → MBC5 due
  to `BankingConfig` bank-1 default. Documented in `deferred-items.md` as
  Phase 9.1 candidate.
- **MCP-server-wiring gap** (Plan 06) — `.claude/mcp_servers.json` lacks
  the `gbkt-emulator` entry in the session. Resolved per Rule 3 via
  StepAgent JVM direct drive (bit-identical evidence). Tooling-setup nit;
  not a codegen seed.
- **Planner-arithmetic gap** (Plan 06) — Plan-06 expected variable values
  for D-01.1 / D-01.2 didn't trace through the per-frame decel ladder.
  Runtime physics is correct (matches reference C). Future Phase 10+ UAT
  contracts should account for the decel ladder, or probe before decel via
  savestate split (not currently exposed by gbkt-emulator).

## Phase 9.1 Placeholder Insert (per D-05)

PHASE9_SEEDS = 1 ≥ 1 → D-05 conditional fires.

Inserted into `.planning/ROADMAP.md` between Phase 09 and Phase 10:

- **Top phases list** (after line 40 / Phase 09 entry): single bullet line
  `- [ ] **Phase 9.1: simple_physics surplus codegen defects (INSERTED)** -
  Address surplus codegen seeds harvested during Phase 9 (single-named-bug
  doctrine surplus). [placeholder — run /gsd-discuss-phase 9.1 to scope]`

- **Phase-detail section** (between `### Phase 9: ...` detail block and
  `### Phase 10: ...` detail block): full section with Goal, Requirements
  TBD, Depends-on Phase 9, Seeds-to-address bullet for SEED-002, optional
  codegen-hygiene candidates list (DEFERRED-09-01 / DEFERRED-09-02), and
  TBD plans list pointing at `/gsd-discuss-phase 9.1` + `/gsd-plan-phase 9.1`.

ROADMAP changes are minimal — only the two Phase 9.1 inserts touched; Phase
10/11/12 entries are unchanged. Phase 09 list line and the Phase 9 detail
block's `- [ ] 09-07-PLAN.md` row are LEFT UNCHANGED for the orchestrator's
`phase.complete` write per the parallel-executor worktree merge contract.

## Phase 9 Final Three-Signal Verdict

| Signal | Result | Evidence |
|--------|--------|----------|
| Codegen quality (D-09 parts 1+2) | PASS | `buildrom-log.txt`, `rom-size-comparison.md` |
| DSL value (D-09 part 3) | PASS | `c-diff.md` |
| UAT verdict (D-01/D-02) | PASS (3/3 GREEN) | `uat-verdict.md` + 3 PNGs |

**Phase 9 verdict: SHIPPED WITH CAVEATS** — all three signals PASS; one
surplus seed (SEED-002) triggered the D-05 conditional Phase 9.1 follow-up.

## Methodology Notes for Phase 10/11/12 Inheritance

What worked in Phase 9 close (carry forward):

1. **Three-signal contract at oracle-comparison.md** — clean compile +
   ROM size + DSL value, each with separate evidence file and PASS/FAIL
   verdict. The aggregator's table format is reusable verbatim for
   Phases 10/11/12.

2. **C-diff appendix as informational, not parity contract** — anti-overfitting
   guard in the Purpose section + every "longer region" paired with a
   rationale. This prevented scope creep (no "tune gbkt to match phys.c"
   suggestions made it into the appendix).

3. **Seed inventory split into file-based vs informational** — only
   file-based seeds count toward D-05 threshold. SEED-001 (predates
   Phase 9) excluded by chronology + planted_during metadata; only SEED-002
   counted.

4. **Conditional ROADMAP insert as in-place edit** — both top phases list
   and detail section. Minimal blast radius (Phase 10/11/12 unchanged).

5. **Phase-close split from orchestrator's phase.complete** — executor
   writes the artifacts + ROADMAP placeholder; orchestrator owns the
   Phase 9 `[x]` flip + 09-07 plan `[x]` flip + completion date. Avoids
   merge race when worktree base differs from feat-branch tip.

What to refine for Phase 10:

1. **MCP server wiring** — if `gbkt-emulator` MCP is still not wired into
   the executor session, the UAT plan should either (a) require pre-flight
   `gbktSetupClaude` invocation in `09-CONTEXT.md` checklist, or
   (b) document the StepAgent fallback as the default-supported path
   (currently it's a Rule 3 auto-fix every time).

2. **Variable-read API for INT16 DSL types** — `StepAgent.readVariable`
   returns a byte regardless of declared type. Phase 10 UAT helpers should
   bake the `.noi` parse + signed 2-byte read into a reusable
   `readI16(agent, name)` helper rather than re-implementing it per phase.

3. **Decel-ladder-aware UAT contracts** — Plan-06 expected-value
   miscalculations would have been caught at planning time if the UAT
   planner had a "trace one frame through the DSL frame block, including
   end-of-frame mutations" checklist. Phase 10 UAT planner should adopt
   this trace as a required pre-write step.

4. **`ActorRef.moveTo(Expr, Expr)`** — if a Phase 10/11/12 port encounters
   the same gap (likely for metasprites which translates compute-positioned
   sprites), SEED-002 should be promoted to a named fix as part of the
   port plan rather than seeded again.

## Task Commits

1. **Task 1 — C-diff appendix + oracle-comparison finalization:** `9bdc3a38`
   (`docs(09-07): write C-diff appendix and finalize oracle-comparison.md three-signal verdict`).
2. **Task 2 — Phase 9.1 placeholder insert into ROADMAP.md:** `d3591332`
   (`docs(09-07): insert Phase 9.1 placeholder into ROADMAP per D-05 seed harvest`).

The plan metadata commit (this SUMMARY.md) will be `[next commit hash]`.

## Decisions Made

- **PHASE9_SEEDS = 1, not 2 or 4.** SEED-001 predates Phase 9; only SEED-002
  is Phase-9-originated. Informational deferreds (DEFERRED-09-01,
  DEFERRED-09-02, MCP-wiring, planner-arithmetic) live in existing
  artifacts (deferred-items.md, summaries) and do not have seed files,
  so they don't count toward the D-05 file-based threshold.
- **Phase 9.1 included optional codegen-hygiene candidates as adjacencies**
  in the detail section's "Optional codegen-hygiene candidates" list
  (DEFERRED-09-01, DEFERRED-09-02). These are listed for the
  /gsd-discuss-phase 9.1 to consider but are not a required scope —
  ergonomic prep so 9.1 doesn't have to re-discover them.
- **ROADMAP edits scoped narrowly** — only Phase 9.1 inserts. Phase 9
  `[x]` and 09-07 plan `[x]` are orchestrator's domain. This follows the
  user's explicit DOs/DON'Ts at agent spawn time.
- **C-diff appendix anti-overfitting framing** — every longer region paired
  with a rationale (framework value-add OR seed-captured). Explicit
  Purpose-section disclaimer that this is informational, NOT a parity
  contract.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Worktree filesystem missing phase context — brought forward from feat/d_and_d_gaps**

- **Found during:** initial Read calls (all .planning/, gbkt-examples/simple-physics/ paths errored "File does not exist").
- **Issue:** This worktree was branched from `435f9e7d26759b2fd6d2f5d60978127e5b7e1630`, a commit predating Phase 9's content. The phase plans, evidence, and `simple-physics` module source live on `feat/d_and_d_gaps`. `./gradlew :gbkt-examples:simple-physics:generateC` needs the module source to be in the worktree filesystem.
- **Fix:** `git checkout feat/d_and_d_gaps -- .planning/ gbkt-examples/ gbkt-backend-* gbkt-ir gbkt-lang gbkt-engine gbkt-world gbkt-core gbkt-analysis gbkt-test gbkt-genre-* gbkt-gradle-plugin gbkt-cli gbkt-bom gbkt-all gbkt-emulator gbkt-mcp-server gbkt-intellij-plugin LabyrinthOfTheDragon-port build.gradle.kts settings.gradle.kts gradle/ buildSrc/`, then `git reset HEAD .` to unstage all the brought-forward files (they remain in the working tree but are not part of any commit). Only my own deliverables (c-diff.md, oracle-comparison.md edits, ROADMAP.md edits, this SUMMARY.md) get committed.
- **Files modified:** working-tree-only (no commits).
- **Verification:** `./gradlew :gbkt-examples:simple-physics:generateC` succeeded; `build/gbkt/generated/main.c` (206 lines) and `bank1.c` (62 lines) were produced and read into the C-diff appendix.
- **Risk note:** when the orchestrator merges this worktree back to `feat/d_and_d_gaps`, the brought-forward files are not in any commit on this branch — they are part of the worktree's filesystem only. The merge will see only my actual deliverable commits; no risk of clobbering feat-branch state.

**2. [Rule 3 — Blocking] settings.gradle.kts references LabyrinthOfTheDragon-port (not in initial checkout list)**

- **Found during:** first `:gbkt-examples:simple-physics:generateC` attempt — Gradle errored "Configuring project ':LabyrinthOfTheDragon-port' without an existing directory is not allowed".
- **Issue:** Gradle requires every project referenced by `settings.gradle.kts` to exist on disk even if not built. The initial bring-forward missed `LabyrinthOfTheDragon-port`.
- **Fix:** `git checkout feat/d_and_d_gaps -- LabyrinthOfTheDragon-port/` then re-ran.
- **Files modified:** working-tree-only.
- **Verification:** Gradle build succeeded — `BUILD SUCCESSFUL in 11s`.

### Other Deviations

**None.** Plan 07's two tasks executed as written; both `<verify>` regexes pass; all `<acceptance_criteria>` satisfied at executor-scope (the `completed 2026-05-13` regex is orchestrator-scope per the executor instruction).

## Issues Encountered

- **Worktree base predates phase content** — resolved via auto-fix #1 above.
- **Missing module directory blocks Gradle settings.gradle.kts evaluation** — resolved via auto-fix #2.
- **No other issues.** The DSL/codegen/ROM artifacts from Plans 03/04/05/06 are unchanged; this plan only added documentation.

## Plan-Scoped Acceptance Criteria — Check

- ✓ `evidence/c-diff.md` exists with sections `## Purpose`, `## Methodology`, `## Side-by-side mapping`, `## Shorter/clearer regions (DSL value signal)`, `## Equal regions`, `## Longer regions and rationale`, `## DSL value verdict`.
- ✓ `## Side-by-side mapping` table has 13 rows (≥ 10 required) covering phys.c L37-94.
- ✓ `evidence/c-diff.md` quotes literal text from BOTH `phys.c` AND `bank1.c`/`main.c` (verified — table cells contain `int16_t PosX, PosY;`, `INT16 _posX = 1024u; INT16 _posY = 1024u;`, `if (button_held(J_UP)) { _spdY -= 2u; ... }`, `if (INPUT_KEY(J_UP)) { SpdY -= Y_ACCELERATION_IN_SUBPIXELS; ... }`, etc.).
- ✓ `evidence/c-diff.md` does NOT recommend codegen changes "to make gbkt look like phys.c" — Purpose section is explicit anti-overfitting guard; every "longer region" has a rationale that frames it as framework value-add OR captured-as-seed.
- ✓ `evidence/oracle-comparison.md` has all 4 sections populated (Codegen quality, DSL value, UAT verdict, Three-signal summary); zero `<placeholder` text remains (verified via grep — 0 matches for `<placeholder|<filled`).
- ✓ `evidence/oracle-comparison.md` has `## Phase 9 verdict` line: `SHIPPED WITH CAVEATS`.
- ✓ Phase 9.1 list-line and detail-section inserts present in `.planning/ROADMAP.md`.
- ✓ Phase 10 entry unchanged (verified — `### Phase 10: Port metasprites GBDK example to gbkt` still present at line 975 post-insert, was line 957 pre-insert; +18 lines for the Phase 9.1 detail section).
- ✓ `## Seeds harvested` section in oracle-comparison.md lists SEED-002 (the single Phase-9-originated seed) plus informational deferreds.
- ⚠ `completed 2026-05-13` in ROADMAP.md — this string will be written by the orchestrator's `phase.complete` after verifying this SUMMARY. The plan-07 verify regex includes this assertion; from the executor's scope, this is deferred to the orchestrator per the parallel-executor worktree merge contract.

## Self-Check: PASSED

Verified by grep + ls:

- ✓ `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/c-diff.md` exists (167 lines).
- ✓ `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/oracle-comparison.md` exists with all 4 section headings + `## Seeds harvested` + `## Phase 9 verdict` + zero literal `<placeholder>` markers.
- ✓ `.planning/ROADMAP.md` contains `Phase 9.1: simple_physics surplus codegen defects (INSERTED)` (matched at line 41 — list line — and line 958 — detail heading).
- ✓ `.planning/seeds/SEED-002-actor-moveto-expr-overload.md` exists (Phase-9-originated, accounted for in the Phase 9.1 placeholder's Seeds-to-address bullet).
- ✓ Commit `9bdc3a38` exists in `git log` for this branch (Task 1).
- ✓ Commit `d3591332` exists in `git log` for this branch (Task 2).
- ⚠ Phase 09 `[x]` flip in ROADMAP.md is NOT present in this executor's commits — that is by design (orchestrator's `phase.complete` writes it after verifying this plan). Self-check does not assert this.

## Next Plan Readiness

- **Phase 09 closeout / verification:** Phase 9's three deliverables (c-diff.md, oracle-comparison.md, ROADMAP Phase 9.1 insert) are in place. The orchestrator can now run phase-verification → `phase.complete` → which writes the Phase 09 `[x]` + completion-date in ROADMAP.md and flips 09-07 to `[x]` in the Phase 9 detail block.
- **Phase 9.1** is a placeholder — to be scoped via `/gsd-discuss-phase 9.1` (focus on SEED-002 named fix; optionally include DEFERRED-09-01/02 codegen-hygiene as adjacencies). No work blocked on Phase 9.1; the codegen pipeline is shippable as-is for Phases 10/11/12.
- **Phase 10** (port metasprites) is unblocked by Phase 9's per-port methodology — Plan 09-01..07 templates are reusable. Consider opening with the methodology notes above (MCP wiring, INT16-read helper, decel-aware UAT trace, SEED-002 promotion check).

## Threat Flags

None — Plan 07 adds no new network/auth/file-access surface. All writes are to `.planning/` documentation files (oracle-comparison.md, c-diff.md, ROADMAP.md, this SUMMARY.md). No source code modified; no codegen changes; no DSL changes; no tests added.

---
*Phase: 09-port-simple-physics-gbdk-example-to-gbkt*
*Plan: 07*
*Completed: 2026-05-13*
