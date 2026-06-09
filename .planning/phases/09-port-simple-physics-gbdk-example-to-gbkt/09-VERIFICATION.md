---
phase: 09-port-simple-physics-gbdk-example-to-gbkt
verified: 2026-05-13T18:20:00Z
status: passed
score: 7/7 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 6/7
  gaps_closed:
    - "PLAYBOOK.md MCP scripts document expected variable values that match the actual runtime physics (CR-01)"
  gaps_remaining: []
  regressions: []
  closure_commit: 713f0821
deferred:
  - truth: "ExprVisitor signed-comparison fix covers actor PropertyAccessExpr LHS (i8Prop / u8Prop)"
    addressed_in: "Phase 9.1"
    evidence: "Phase 9.1 ROADMAP.md goal: 'Address codegen defects and DSL-ergonomics gaps surfaced during the Phase 9 simple_physics port that were not fixed under the single-named-bug doctrine (D-04 + D-05)'. WR-01 in 09-REVIEW.md explicitly states 'For SimplePhysics this is non-blocking because the port uses i16Var exclusively'. Bucket-c coverage is not in Phase 9's named-bug scope (D-04 hard cap: ONE named bug fix; surplus → seeds). The PropertyAccessExpr coverage gap is the natural Phase 9.1 follow-up alongside SEED-002."
  - truth: "Scaffolding warning hygiene (SDCC 84/85/85/126) eliminated"
    addressed_in: "Phase 9.1 (candidate, per deferred-items.md DEFERRED-09-01)"
    evidence: "deferred-items.md documents these as 'Phase 9.1 candidate for codegen-hygiene scope' — they fire on every gbkt example (verified against pong control build), pre-existing, out of scope per SCOPE BOUNDARY for the simple_physics codegen-quality oracle."
  - truth: "Single-scene games avoid the bank-1 MBC5 upgrade"
    addressed_in: "Phase 9.1 (candidate, per deferred-items.md DEFERRED-09-02)"
    evidence: "deferred-items.md DEFERRED-09-02 — 'Phase 9.1 candidate' for the BankingConfig default refinement. Not load-bearing for the D-09 ROM-size verdict (gbkt already at 1.025× reference)."
---

# Phase 9: Port simple_physics GBDK example to gbkt — Verification Report

**Phase Goal:** Re-implement the GBDK simple_physics example as an idiomatic gbkt DSL game. First reference port — validates actor/input/collision/script primitives against GBDK's reference C output (codegen oracle). Establish per-port methodology: UAT-first, idiomatic DSL port (not line-by-line), three-signal comparison against GBDK reference.

**Verified:** 2026-05-13T18:20:00Z
**Status:** passed
**Re-verification:** Yes — round 2 after CR-01 inline gap closure (commit `713f0821`)

## Round 2 — Gap closure

The round-1 verification (2026-05-13T15:54:50Z) found ONE blocking gap: **CR-01 — PLAYBOOK.md MCP-script
expected variable values contradicted the actual per-frame physics ladder**. The shipped ROM, the
`SimplePhysicsUatTest.kt` test, `uat-verdict.md`, `oracle-comparison.md`, and `09-UAT.md` were all
internally consistent at the corrected values (spdX=30@30f, spdY=−511@1f, spdX=20@20f, spdX=0 after
60 idle frames); only PLAYBOOK.md still carried the pre-Plan-06 planner-arithmetic values
(64 / −512 / ~40 / 0).

Commit `713f0821 fix(09): correct PLAYBOOK.md MCP-script expected values to match phys.c per-frame
ladder` applied a documentation-only fix:

1. **Behavior 1 (lines 78–93):** `expect: 30` and `expected:30` after 30 frames of held RIGHT (net
   +1/frame ladder). Added a continued-hold step extending to frame 64 with `expect: 63` and
   `expected:63` — the binding clamp steady-state signature (65 → clamp 64 → end-of-frame decel 63).
2. **Behavior 2 (lines 95–106):** `expect: -511` and `expected:-511` after a single edge-triggered A
   press (set −512, then same-frame decel ladder runs once → −511). Mirrors `phys.c L93`.
3. **Behavior 3 (lines 108–120):** `expect: 20` and (implicitly `expected:20` through the readback)
   after 20 frames of held RIGHT, then `expect: 0` and `expected:0` after 60 idle frames.
4. **Per-frame ladder explanatory block (lines 72–75):** prepended to the "MCP Input Scripts"
   section so an external MCP agent (or future port author) understands the ordering rule
   (accel/jump → integrate → decel) without having to cross-reference the UAT test.

### Round-2 verification — PLAYBOOK ↔ UAT artifact consistency

| Source                                                                                                          | Behavior 1 expected      | Behavior 2 expected | Behavior 3 expected             |
|-----------------------------------------------------------------------------------------------------------------|--------------------------|---------------------|---------------------------------|
| `SimplePhysicsUatTest.kt`                                                                                       | spdX=30 @30f; spdX=63 @64f | spdY=-511 @1f       | spdX (any) @20f; spdX=0 @80f    |
| `evidence/uat-verdict.md`                                                                                       | spdX=30 @30f; spdX=63 @64f | spdY=-511 @1f       | spdX=0 @80f                     |
| `09-UAT.md` (actual section)                                                                                    | spdX=30, posX=1519       | spdY=-511, posY=512 | spdX=0, posX=1464               |
| `evidence/oracle-comparison.md`                                                                                 | (cites uat-verdict)      | (cites uat-verdict) | (cites uat-verdict)             |
| `PLAYBOOK.md` (post-`713f0821`)                                                                                 | **expect: 30; expect: 63** | **expect: -511**    | **expect: 20; expect: 0**       |

All five artifacts now agree on the corrected values. The doc-vs-runtime contradiction is fully
resolved. An MCP agent driving the post-fix PLAYBOOK against the shipped ROM will see GREEN, which
is what D-03 ("PLAYBOOK locked BEFORE DSL with consistent UAT contract") requires for Phase
10/11/12 methodology inheritance.

**Pre-existing-failures note (carried forward from round 1, NOT a Phase 9 regression):**
`TrackSynthesizerCircuitShapeTest` in `gbkt-genre-sport` is a latent Phase 07.4 gap; it predates
Phase 9, is unrelated to the simple_physics port, and is documented in the Phase 07.4 UAT trail.
This re-verification did not run a full `:check` to re-confirm — the focus is the CR-01 closure;
the `:gbkt-examples:simple-physics:test`, `:gbkt-backend-gbdk:test`, and three UAT-tier behaviors
remain GREEN from round 1, and `git log --stat 713f0821` shows the commit touched only
`gbkt-examples/simple-physics/PLAYBOOK.md` (no codegen, no test changes), so the round-1 evidence
for SC-1 / SC-2 / SC-3 / D-01 / D-04 / D-05 / D-11 is preserved unchanged.

## Goal Achievement

### Observable Truths

The three roadmap Success Criteria are the binding must-haves. Each truth is verified against codebase evidence below.

| #  | Truth                                                                            | Status      | Evidence                                                                                                       |
|----|----------------------------------------------------------------------------------|-------------|----------------------------------------------------------------------------------------------------------------|
| 1  | **SC-1 Codegen quality** — generated C compiles via lcc with zero phase-9 warnings; ROM ≤2× reference | ✓ VERIFIED  | `evidence/buildrom-log.txt`; live rebuild on round 1: only 4 pre-existing scaffolding warnings (84/85/85/126); no warning 94 (Bug A) or 158 (Risk 3). `l__CODE` 588 vs 574 (1.025× — well inside 2× cap of 1148) per `evidence/rom-size-comparison.md`. Round 2 did not modify code — preserved. |
| 2  | **SC-2 DSL value** — gbkt DSL is dramatically shorter/clearer than equivalent GBDK C OR documented reason it isn't | ✓ VERIFIED  | `evidence/c-diff.md` provides side-by-side mapping; user-authored game-logic surface is shorter (e.g., 1-line `sprite(asset(...))` vs 6-line inline hex tile data); HOME-bank scaffolding is longer by design (framework value-add: joypad/OAM/sound/dialog/fade scaffolding emitted for free). Documented per D-09 part 3. |
| 3  | **SC-3 UAT contract** — per-example UAT passes (3 D-01 behaviors GREEN with binding visual evidence) | ✓ VERIFIED  | `SimplePhysicsUatTest.kt` round-1 rerun: 3/3 PASS (0 skipped, 0 failed). `evidence/uat-screenshots/*.png` — 3 valid 160×144 Game Boy PNGs viewed directly: behavior1 sprite displaced rightward, behavior2 sprite mid-jump above center, behavior3 sprite at rest right-of-center. CLAUDE.md Visual Evidence Rule satisfied. Round 2: no codegen/test/ROM changes — preserved. |
| 4  | **D-01 → JVM-tier emission invariants (D-11.1/2/3)** lock signed-comparison RHS contract | ✓ VERIFIED  | `SimplePhysicsEmissionTest.kt` — 3/3 GREEN (per `build/test-results/test/...SimplePhysicsEmissionTest.xml` from round 1). D-11.1 asserts `_spdX > 64` / `_spdY > 64` bare (no `u` suffix); D-11.2 asserts `_spdY = -512` + `button_pressed(J_A)`; D-11.3 asserts decel-ladder `< 0` / `> 0` bare. Phase 07.9 regression guard preserved (SignedComparisonLiteralEmissionTest 8/8 GREEN). |
| 5  | **Named codegen bug fix (D-04)** — ONE named bug fix, scoped, additive            | ✓ VERIFIED  | Bug A — ExprVisitor.visitBinaryExpr routes signed-comparison RHS through CIntLiteral when LHS is a VarRef to an I8/I16 variable. Strictly additive: `variables: List<VariableDef> = emptyList()` default preserves pre-fix CLiteral emission for callers that don't pass `variables`. Codified in `gbkt-backend-gbdk/CLAUDE.md` §"DSL-authored signed-comparison path". |
| 6  | **D-05 surplus discipline** — surplus codegen defects captured as seeds; conditional Phase 9.1 placeholder inserted | ✓ VERIFIED  | `.planning/seeds/SEED-002-actor-moveto-expr-overload.md` exists (Bug B, dormant, small scope). `.planning/ROADMAP.md` L41+ contains `Phase 9.1: simple_physics surplus codegen defects (INSERTED)` placeholder anchored on SEED-002. `deferred-items.md` documents DEFERRED-09-01/02 informational items. |
| 7  | **D-03 — PLAYBOOK.md locked BEFORE DSL with consistent UAT contract**             | ✓ VERIFIED  | Round 2 closure: commit `713f0821` corrects PLAYBOOK.md expected values to `30 / 63 / -511 / 20 / 0`, matching `SimplePhysicsUatTest.kt`, `uat-verdict.md`, `09-UAT.md` actual sections, and `oracle-comparison.md`. All five artifacts are now mutually consistent. Per-frame-ladder explanatory note (PLAYBOOK.md L72–75) makes the ordering rule (accel/jump → integrate → decel) explicit so future ports (Phase 10/11/12) inherit the correct doctrine. Behavior 1 also gains the binding clamp signature step (frame 64 → spdX=63) that proves the +64 clamp actually fires. |

**Score:** 7/7 truths verified.

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases or out of phase 9 scope.

| # | Item                                                                                              | Addressed In | Evidence                                                                                                                                                                          |
|---|---------------------------------------------------------------------------------------------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | ExprVisitor signed-comparison fix covers actor PropertyAccessExpr LHS (i8Prop / u8Prop) (WR-01)   | Phase 9.1    | 09-REVIEW.md WR-01: "For SimplePhysics this is non-blocking because the port uses i16Var exclusively". D-04 hard cap = ONE named bug; PropertyAccessExpr coverage is bucket-c surplus → Phase 9.1. |
| 2 | Scaffolding warning hygiene (SDCC 84/85/85/126) eliminated                                        | Phase 9.1 candidate | `deferred-items.md` DEFERRED-09-01 — pre-existing, fires on every gbkt example, out of scope per SCOPE BOUNDARY.                                                          |
| 3 | Single-scene games avoid the bank-1 MBC5 upgrade                                                  | Phase 9.1 candidate | `deferred-items.md` DEFERRED-09-02 — informational, not load-bearing for D-09 PASS.                                                                                       |

### Required Artifacts

| Artifact                                                                       | Expected                                  | Status      | Details                                                                                                              |
|--------------------------------------------------------------------------------|-------------------------------------------|-------------|----------------------------------------------------------------------------------------------------------------------|
| `gbkt-examples/simple-physics/build.gradle.kts`                                | Module scaffold + plugin wiring           | ✓ VERIFIED  | Gradle plugin configured; outputName=`simple-physics`; module registered in root `settings.gradle.kts`.              |
| `gbkt-examples/simple-physics/src/main/kotlin/.../SimplePhysics.kt`            | Idiomatic gbkt DSL port                   | ✓ VERIFIED  | 132 lines; uses `i16Var`, single `play` scene with `enter { }` and `frame { }`, `whenever`/`dpad.held`/`buttons.a.pressed`, mirrors phys.c shape per D-06/D-07/D-08. |
| `gbkt-examples/simple-physics/res/sprites/smiley.png`                          | 8x8 4-frame sprite asset                  | ✓ VERIFIED  | Single-frame 8x8 PNG (cycling deferred per D-overfitting-3); converts via the asset pipeline.                        |
| `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsIRTest.kt`      | IR validation (1 scene / 1 actor / 4 vars)| ✓ VERIFIED  | 14 tests; 0 failures. Asserts I16 type for all 4 vars, single play scene, start=play.                                |
| `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsEmissionTest.kt`| 3 JVM emission invariants (D-11.1/2/3)    | ✓ VERIFIED  | 3 tests; 0 failures. Brace-walk extraction of play_frame body per CLAUDE.md scope-level grep gate.                   |
| `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsUatTest.kt`     | 3 D-01 behaviors with screenshot evidence | ✓ VERIFIED  | 3 tests; 0 failures (after rebuilding ROM). Auto-skip when ROM is missing.                                           |
| `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsGameTest.kt`    | Simulation tests (decel ladder, etc.)     | ✓ VERIFIED  | 4 tests; 0 failures.                                                                                                 |
| `gbkt-examples/simple-physics/PLAYBOOK.md`                                     | MCP scripts matching UAT-verified values  | ✓ VERIFIED  | Round 2 (commit `713f0821`): expected values corrected to 30/63/-511/20/0; per-frame-ladder note added; binding clamp verification step added to Behavior 1. Matches `SimplePhysicsUatTest.kt`, `uat-verdict.md`, `09-UAT.md`, `oracle-comparison.md`. |
| `gbkt-examples/simple-physics/README.md`                                       | User-facing example documentation         | ✓ VERIFIED  | Present; IN-01 (stale "(once Plan 04 adds it)" suffix) is doc nit, not blocking.                                     |
| `gbkt-examples/simple-physics/CLAUDE.md`                                       | Developer notes                           | ✓ VERIFIED  | Present; IN-02 plan-history leakage is doc nit, not blocking.                                                        |
| `.planning/phases/09-.../evidence/oracle-comparison.md`                        | Three-signal verdict (D-09)               | ✓ VERIFIED  | PASS/PASS/PASS three-signal table; references buildrom-log + rom-size-comparison + c-diff + uat-verdict.            |
| `.planning/phases/09-.../evidence/c-diff.md`                                   | Informational C-diff (D-09 part 3)        | ✓ VERIFIED  | Side-by-side phys.c vs main.c+bank1.c with anti-overfitting framing.                                                 |
| `.planning/phases/09-.../evidence/buildrom-log.txt`                            | Clean-compile log (D-09 part 1)           | ✓ VERIFIED  | No warning 94 or 158 in 154KB build log.                                                                             |
| `.planning/phases/09-.../evidence/rom-size-comparison.md`                      | ROM size table (D-09 part 2)              | ✓ VERIFIED  | l__CODE 588 vs 574 → 1.025× → within 2× cap.                                                                          |
| `.planning/phases/09-.../evidence/reference/phys.c` + `BUILD.md`               | Reference oracle source + build recipe    | ✓ VERIFIED  | phys.c 99 lines captured verbatim; BUILD.md describes reproducible reference build.                                  |
| `.planning/phases/09-.../evidence/uat-screenshots/behavior{1,2,3}-*.png`       | 3 binding visual evidence PNGs (D-02)     | ✓ VERIFIED  | All 3 PNGs valid 160×144; sprite positions match documented physics.                                                 |
| `.planning/phases/09-.../evidence/uat-verdict.md`                              | Per-behavior UAT verdict (D-01)           | ✓ VERIFIED  | 3/3 PASS with visual confirmation lines per behavior.                                                                |
| `.planning/phases/09-.../deferred-items.md`                                    | Out-of-scope items documented             | ✓ VERIFIED  | DEFERRED-09-01 (scaffolding warnings) + DEFERRED-09-02 (MBC5 upgrade) documented.                                    |
| `.planning/seeds/SEED-002-actor-moveto-expr-overload.md`                       | Bug B → seed per D-05                     | ✓ VERIFIED  | Captured during Plan 04 close; small scope; dormant; tied to Phase 9.1.                                              |
| `.planning/ROADMAP.md` Phase 9.1 placeholder                                   | Conditional follow-up phase per D-05      | ✓ VERIFIED  | Phase 9.1 entry present at L41 + detail section at L958; references SEED-002.                                        |

### Key Link Verification

| From                                                                            | To                                                                    | Via                                                                          | Status   | Details                                                                                                                                                              |
|---------------------------------------------------------------------------------|-----------------------------------------------------------------------|------------------------------------------------------------------------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SimplePhysics.kt` DSL                                                          | Generated `bank1.c` `play_frame()`                                    | GBDKPipelineV2 → SceneVisitor → ScriptOpVisitor → ExprVisitor                | WIRED    | bank1.c play_frame() emitted with signed-comparison RHS bare (`_spdX > 64`, `_spdY < 0`, etc.) — Bug A fix flowing through. Verified by SimplePhysicsEmissionTest.    |
| `ExprVisitor(actors, variables)`                                                | `isSignedComparisonRhs` predicate                                     | `variableTypes` lookup table built from VariableDef list                     | WIRED    | ExprVisitor.kt:78 builds `variableTypes`; line 110-114 routes Literal RHS through CIntLiteral when predicate matches.                                                |
| `GBDKPipelineV2.buildSceneFile()`                                               | `SceneVisitor.visit(scene, actors, variables)`                        | Pipeline passes gameIR.variables explicitly                                  | WIRED    | gbkt-backend-gbdk/CLAUDE.md documents the wiring: `GBDKPipelineV2.buildSceneFile() → SceneVisitor.visit(scene, gameIR.actors, gameIR.variables) → ExprVisitor(...)`. |
| `SimplePhysicsUatTest.kt`                                                       | `simple-physics.gb` ROM                                                | StepAgent (`gbkt-emulator`) + AgentSessionConfig.discoverFiles               | WIRED    | UAT auto-skip via JUnit Assumptions when ROM missing; runs 3/3 PASS after `buildRom`.                                                                                |
| Phase 9 seeds                                                                   | Phase 9.1 placeholder                                                  | ROADMAP.md cross-reference (`Seeds to address: SEED-002`)                    | WIRED    | ROADMAP.md L962-964 explicitly cites the seed file path; D-05 conditional satisfied.                                                                                 |
| PLAYBOOK.md MCP scripts                                                         | Actual runtime physics outcome                                         | (MCP agent execution path)                                                   | WIRED    | Round 2 (commit `713f0821`): expected values match UAT-verified runtime. MCP agent driving these scripts will produce GREEN against the shipped ROM.                |

### Data-Flow Trace (Level 4)

| Artifact                                  | Data Variable        | Source                                          | Produces Real Data | Status     |
|-------------------------------------------|----------------------|-------------------------------------------------|--------------------|------------|
| bank1.c `play_frame()`                    | _spdX / _spdY / _posX / _posY | Global INT16 vars assigned from BinaryExpr lowering | Yes — UAT confirms real per-frame mutation through accel/clamp/decel | ✓ FLOWING |
| evidence/uat-screenshots/*.png            | Sprite OAM position  | `move_sprite(0u, _smiley_x + 8u, _smiley_y + 16u)` driven by `_smiley_x = _posX >> 4u` | Yes — visible sprite at expected screen positions per behavior | ✓ FLOWING |
| Generated game_metadata.json              | Scene/variable list  | GBDKPipelineV2.buildMetadataFile()              | Yes — UAT loads metadata via GameMetadata.fromJsonFile()           | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior                                  | Command                                                                          | Result                                                                  | Status   |
|-------------------------------------------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------|----------|
| Module builds and produces ROM            | `./gradlew :gbkt-examples:simple-physics:buildRom --rerun-tasks` (round 1)        | ROM created at build/gbkt/output/simple-physics.gb (32 KB); 1 lcc warning | ✓ PASS   |
| Generated C contains signed-comparison literals bare (no `u`) | grep `_spdX > 64\|_spdY > 64\|_spdX < 0\|_spdX > 0` in bank1.c (round 1) | All 4 patterns present with bare integer literal                       | ✓ PASS   |
| No SDCC warning 94 in compile output      | grep `warning 94` evidence/buildrom-log.txt                                       | No matches                                                              | ✓ PASS   |
| No SDCC warning 158 in compile output     | grep `warning 158` evidence/buildrom-log.txt                                      | No matches                                                              | ✓ PASS   |
| SimplePhysicsUatTest 3/3 PASS             | `./gradlew :gbkt-examples:simple-physics:test --tests "*UatTest"` (round 1)       | 3 tests; 0 failures (rebuilt ROM)                                       | ✓ PASS   |
| SimplePhysicsEmissionTest 3/3 PASS        | XML test report `D-11_1`, `D-11_2`, `D-11_3` cases                                | 3 tests; 0 failures                                                     | ✓ PASS   |
| SignedComparisonLiteralEmissionTest 8/8 (regression guard) | gbkt-backend-gbdk XML report                                                     | 8 tests; 0 failures                                                     | ✓ PASS   |
| gbkt-backend-gbdk all tests               | Aggregated test results (round 1)                                                  | 888 tests; 0 failures                                                   | ✓ PASS   |
| **Round 2** PLAYBOOK expected values match UAT artifacts | `grep -E "expect(ed)?:.*30\|63\|-511\|20\|0" PLAYBOOK.md` cross-checked against `uat-verdict.md` actual sections | All five values (30 / 63 / -511 / 20 / 0) present and aligned | ✓ PASS   |
| **Round 2** commit `713f0821` exists and touches only PLAYBOOK.md | `git show --stat 713f0821` | One file changed: `gbkt-examples/simple-physics/PLAYBOOK.md`                                  | ✓ PASS   |

### Probe Execution

No formal `scripts/*/tests/probe-*.sh` probes are declared for this phase. The phase methodology relies on JUnit-tier emission tests (D-11) + Step-Agent-driven UAT (D-01) + binary screenshot evidence. The "probe" surface for this phase is the UAT test class — round-1 execution: 3/3 PASS. Round 2 did not modify codegen or tests, so the UAT signal is preserved.

| Probe                                              | Command                                                                                                 | Result | Status |
|----------------------------------------------------|---------------------------------------------------------------------------------------------------------|--------|--------|
| SimplePhysicsUatTest behavior 1 (clamp)            | `./gradlew :gbkt-examples:simple-physics:test --rerun-tasks --tests "*UatTest*behavior 1*"`             | PASS   | PASS   |
| SimplePhysicsUatTest behavior 2 (jump impulse)     | (same, behavior 2)                                                                                       | PASS   | PASS   |
| SimplePhysicsUatTest behavior 3 (decel-to-rest)    | (same, behavior 3)                                                                                       | PASS   | PASS   |

### Requirements Coverage

Phase 9 frontmatter declares `requirements: TBD` (no formal REQ-* IDs bound to this phase). The roadmap's binding contract is the three-signal Success Criteria (SC-1/2/3 above) plus the D-* decisions enumerated in 09-CONTEXT.md.

| Requirement                                                                       | Source        | Description                                                                                                       | Status       | Evidence                                  |
|-----------------------------------------------------------------------------------|---------------|-------------------------------------------------------------------------------------------------------------------|--------------|-------------------------------------------|
| SC-1 — codegen quality (clean compile + ≤2× ROM size)                              | ROADMAP.md L932 | Generated C compiles via lcc with zero warnings; ROM size ≤2× of GBDK reference                                    | ✓ SATISFIED  | Truth 1 evidence                          |
| SC-2 — DSL value (dramatically shorter/clearer OR documented reason)               | ROADMAP.md L933 | gbkt DSL ≪ GBDK C at user surface OR documented reason it isn't                                                    | ✓ SATISFIED  | Truth 2 evidence                          |
| SC-3 — UAT contract (per-example UAT passes)                                        | ROADMAP.md L934 | UAT (written before code work) demonstrates the specific runtime behaviors                                          | ✓ SATISFIED  | Truth 3 evidence                          |
| Hard scope cap — ONE example, ONE named codegen bug                                | ROADMAP.md L935 | Single example + single named codegen bug fix; surplus → seeds or follow-up                                        | ✓ SATISFIED  | One example (simple-physics) + one named fix (Bug A) + SEED-002 + Phase 9.1 placeholder |
| D-01 — three behaviors with screenshot per behavior                                | 09-CONTEXT.md | (1) D-pad held → accel + clamp; (2) A pressed → jump impulse; (3) D-pad released → decel-to-rest                   | ✓ SATISFIED  | 09-UAT.md + 3 PNGs                        |
| D-02 — MCP play-through + variable assertion + screenshot per behavior              | 09-CONTEXT.md | Visual evidence rule binding                                                                                       | ✓ SATISFIED  | uat-verdict.md per-behavior visual confirmation lines |
| D-03 — UAT first; PLAYBOOK locked BEFORE DSL with consistent expected values        | 09-CONTEXT.md | 09-UAT.md + PLAYBOOK.md authored before any DSL, locked through phase close                                         | ✓ SATISFIED  | Round 2: PLAYBOOK.md now matches the UAT contract (commit `713f0821`); all five artifacts agree on 30 / 63 / -511 / 20 / 0. Truth 7 evidence. |
| D-04 — exploratory mode; named bug = first concrete defect; one fix                | 09-CONTEXT.md | Name the bug post-port; fix ONE codegen defect                                                                     | ✓ SATISFIED  | Bug A — ExprVisitor signed-comparison RHS  |
| D-05 — surplus → seeds + conditional Phase 9.1 placeholder                          | 09-CONTEXT.md | Each surplus → .planning/seeds/; if ≥1 surplus → insert Phase 9.1 placeholder in ROADMAP                            | ✓ SATISFIED  | SEED-002 + ROADMAP.md Phase 9.1 entry      |
| D-06 — single play scene, no title                                                  | 09-CONTEXT.md | One `play` scene with `enter { }` + `frame { }`                                                                    | ✓ SATISFIED  | SimplePhysics.kt L65-129                  |
| D-07 — PNG asset via asset pipeline                                                 | 09-CONTEXT.md | `asset("sprites/smiley.png")` not inline tile data                                                                 | ✓ SATISFIED  | SimplePhysics.kt L55                      |
| D-08 — raw i16Var + manual `shr 4`                                                  | 09-CONTEXT.md | i16Var only; no actor FP88; explicit `>> 4` translation                                                            | ✓ SATISFIED  | SimplePhysics.kt L44-47 + L116-117        |
| D-09 — three artifacts: ROM size + C-diff + UAT verdict                             | 09-CONTEXT.md | All three artifacts committed under evidence/                                                                      | ✓ SATISFIED  | oracle-comparison.md + c-diff.md + rom-size-comparison.md + uat-verdict.md |
| D-10 — evidence layout (reference/ + BUILD.md; binaries gitignored)                 | 09-CONTEXT.md | reference/phys.c + reference/BUILD.md; .gitignore filters binaries                                                 | ✓ SATISFIED  | evidence/reference/ + evidence/.gitignore  |
| D-11 — 3 JVM emission invariants matching the 3 UAT behaviors                       | 09-CONTEXT.md | One JVM test per behavior locking C-shape: signed-comparison emission, edge-detect, decel ladder                   | ✓ SATISFIED  | SimplePhysicsEmissionTest D-11_1/2/3 (3/3 GREEN) |
| Anti-overfitting rails 1/2/3                                                        | 09-CONTEXT.md | No DSL features added for this port; no codegen tuning to GBDK style; reference ≠ DSL template                     | ✓ SATISFIED  | No new DSL primitives added; Bug A fix is additive (preserves pre-fix path); c-diff.md frames "longer" regions as framework value, not as targets for tuning |

**Orphaned requirements:** None. Phase frontmatter declared TBD; ROADMAP.md does not bind additional REQ-* IDs to this phase.

### Anti-Patterns Found

Files modified in this phase: 30+ files across `gbkt-examples/simple-physics/`, `gbkt-backend-gbdk/codegen/visitor/`, `gbkt-backend-gbdk/codegen/pipeline/`, `.planning/phases/09-.../`, `.planning/seeds/`, `.planning/ROADMAP.md`. Round 2 added one additional commit (`713f0821`) touching only `gbkt-examples/simple-physics/PLAYBOOK.md`.

| File                                                                                                | Line   | Pattern                                                                                          | Severity   | Impact                                                                                                                                                              |
|-----------------------------------------------------------------------------------------------------|--------|--------------------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `gbkt-examples/simple-physics/PLAYBOOK.md`                                                          | (round 1: 78, 80, 91, 93, 103, 107) | Doc-vs-runtime contradiction CR-01 | ~~🛑 Blocker~~ Resolved | **CLOSED round 2 by commit `713f0821`** — values corrected to 30 / 63 / -511 / 20 / 0; per-frame-ladder note added. |
| `gbkt-examples/simple-physics/README.md`                                                            | 59-60  | Stale "(once Plan 04 adds it)" suffix                                                            | ℹ️ Info    | IN-01 — doc nit; non-blocking.                                                                                                                                       |
| `gbkt-examples/simple-physics/CLAUDE.md`                                                            | 13, 40-51 | Plan-history references leak into developer docs                                              | ℹ️ Info    | IN-02 — doc nit; will become stale.                                                                                                                                  |
| `gbkt-backend-gbdk/codegen/visitor/ExprVisitor.kt`                                                  | 122-128 | `isSignedComparisonRhs` only matches `VarRef` LHS, not `PropertyAccessExpr` (i8Prop)             | ⚠️ Warning | WR-01 — known coverage gap; deferred to Phase 9.1; SimplePhysics doesn't exercise this path.                                                                        |
| `gbkt-backend-gbdk/codegen/visitor/ExprVisitor.kt`                                                  | 347, 356 | Companion-object fallback allocates fresh `ExprVisitor()` with empty `variables` map; silently bypasses the fix if reached | ⚠️ Warning | WR-02 — pre-existing pattern; widened blast radius; no callers currently route through the empty path during scene-script codegen, but invariant is fragile.        |
| `gbkt-backend-gbdk/codegen/visitor/ScriptOpVisitor.kt`                                              | 175, 282-285 | `exprVisitorContext.set(exprVisitor)` without `try/finally` cleanup                          | ⚠️ Warning | WR-03 — ThreadLocal leak across scenes; pre-existing; widened consequence; no current regression but fragile invariant.                                              |
| `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsEmissionTest.kt`                     | 83-103 | `extractFunctionBody` brace-walker naïve to braces in strings/comments                          | ⚠️ Warning | WR-04 — current generated output has no embedded brace-in-string risk; safe today; promoted-helper hazard if pattern is generalized.                                |
| `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsGameTest.kt`                         | 21-31, 53-67 | Test names imply held-input/jump verification but actually exercise decel ladder from preloaded state | ⚠️ Warning | WR-05 — pre-existing simulation harness limitation; test names should match what they verify.                                                                       |
| `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsUatTest.kt`                          | 161    | Hard-coded `1519` magic posX value                                                                | ℹ️ Info    | IN-04 — comment explains the derivation; suggested fix is to compute expected inline.                                                                                |
| `gbkt-examples/simple-physics/PLAYBOOK.md`                                                          | 14-16  | Edge-trigger behavior promised but no held-A test script                                          | ℹ️ Info    | IN-05 — PLAYBOOK coverage gap; not a defect.                                                                                                                         |
| `gbkt-examples/simple-physics/src/main/kotlin/.../SimplePhysics.kt`                                 | 31     | `@Suppress("LongMethod")` on the DSL block                                                       | ℹ️ Info    | IN-03 — consistent with every other gbkt example; established convention.                                                                                            |

**Debt markers (TBD/FIXME/XXX):** None found in phase-modified files outside the phase planning artifacts themselves. The `requirements: TBD` in PLAN frontmatter is the documented intentional state per ROADMAP.md (`Requirements: TBD (define during /gsd-discuss-phase)`).

**Pre-existing-failures note:** `TrackSynthesizerCircuitShapeTest` in `gbkt-genre-sport` is a Phase 07.4 latent gap; NOT a Phase 9 regression. The round-2 commit (`713f0821`) is documentation-only and cannot have introduced or affected any test in `gbkt-genre-sport`. Carried forward unchanged.

### Human Verification Required

None at this verification step. The three D-01 behaviors were already covered by:
1. Binding visual evidence (3 PNGs viewed directly during round 1 verification — sprite positions match documented physics)
2. Variable assertions (3/3 PASS in SimplePhysicsUatTest.kt round-1 rerun)
3. JVM-tier emission invariants (3/3 PASS in SimplePhysicsEmissionTest.kt)
4. Regression guard for Phase 07.9 (8/8 PASS in SignedComparisonLiteralEmissionTest)

Round 2 closure was a documentation-only fix to PLAYBOOK.md; no behavioral change to verify visually. The cross-artifact consistency check (PLAYBOOK ↔ test ↔ uat-verdict ↔ 09-UAT ↔ oracle-comparison) is fully programmatic.

Visual Evidence Rule (CLAUDE.md) is satisfied: variable assertions are necessary but never sufficient — the screenshots are the binding artifact for the visual outcome.

### Gaps Summary

**Round 1:** ONE blocking gap — CR-01 — PLAYBOOK.md MCP scripts contradicted the UAT-verified runtime values.

**Round 2:** Closed by commit `713f0821 fix(09): correct PLAYBOOK.md MCP-script expected values to match phys.c per-frame ladder`. PLAYBOOK.md now uses the correct values (spdX=30 @30f, spdX=63 @64f clamp signature, spdY=-511 @1f, spdX=20 @20f, spdX=0 @80f), and a per-frame-ladder explanatory block makes the ordering rule explicit for future ports. All five UAT-contract artifacts (`PLAYBOOK.md`, `SimplePhysicsUatTest.kt`, `uat-verdict.md`, `09-UAT.md`, `oracle-comparison.md`) are now mutually consistent.

The phase achieves all three Success Criteria from the roadmap (SC-1 codegen quality / SC-2 DSL value / SC-3 UAT contract) with binding evidence, satisfies the hard scope cap (one example, one named codegen bug, surplus captured as SEED-002, Phase 9.1 placeholder inserted), and now also satisfies D-03 (PLAYBOOK locked with consistent UAT contract). Per-port methodology is proven and ready for Phase 10/11/12 inheritance.

The remaining identified items are correctly deferred:
- **WR-01 (PropertyAccessExpr coverage)** — bucket-c surplus beyond Phase 9's named-bug cap; SimplePhysics doesn't exercise this path; Phase 9.1 candidate alongside SEED-002.
- **DEFERRED-09-01 (scaffolding warnings) / DEFERRED-09-02 (MBC5 upgrade)** — pre-existing, out of scope per SCOPE BOUNDARY.
- **WR-02 / WR-03 / WR-04 / WR-05 / IN-01 through IN-05** — pre-existing patterns, doc nits, or test-shape clarifications; none block the goal.

---

_Verified (round 1): 2026-05-13T15:54:50Z — gaps_found (6/7)_
_Verified (round 2): 2026-05-13T18:20:00Z — passed (7/7)_
_Verifier: Claude (gsd-verifier)_
