# Phase 15: Full-green test suite for v0.1.0 release — Specification

**Created:** 2026-06-09
**Ambiguity score:** 0.118 (gate: ≤ 0.20)
**Requirements:** 7 locked

## Goal

The entire JVM test suite reports **zero failures** — both `./gradlew test --continue` (all library/genre/example modules) and `./gradlew pluginTest` (gradle-plugin IntegrationTest) pass — reached diagnose-first by fixing real bugs or correcting provably-stale assertions, NEVER by weakening a threshold. This is the hard release gate for tagging v0.1.0.

## Background

Phase 14 retired all broken examples (racer, explorer, Labyrinth trees, `.archive/`) and removed dead V2 code. The surviving tree compiles GREEN and every one of the 7 KEEP examples builds via `:buildRom` (EXIT 0). However, the JVM **test** suite is red: the deterministic differential sweep in `14/evidence/FINAL-REGRESSION.md` (captured 2026-06-06, reproduced byte-identically at pre-phase commit `f92efec7`) records **6 failing test classes / 19 individual tests**, all genuinely pre-existing (zero Phase-14 regressions):

| Failing class | Count | Symptom |
|---------------|-------|---------|
| `IntegrationTest` (gradle-plugin, via pluginTest) | 12 | `NoSuchMethodError: SceneIR.copy$default(...)` — TestKit/mavenLocal data-class signature skew (known since Phase 11.1-04) |
| `BanksUatTest` (`:gbkt-examples:banks`) | 2 | dominant-colour ≥95% assertion on banks' by-design near-blank codegen-demo scene |
| `PongStepAgentTest` (`:gbkt-examples:pong`) | 1 | "paddle1 OAM count mismatch expected=2 actual=1" (metadata vs runtime); distinct from the known pong ROM-hash nondeterminism (PASS\*) |
| `PlatformerTemplate128UatTest` (`:gbkt-examples:platformer-template`) | 1 | facing-right vs facing-left pixel diff 6.80% (threshold demands >10%) |
| `PlatformerTemplateUatTest` (`:gbkt-examples:platformer-template`) | 1 | failing screenshot/UAT assertion (under-counted in the ROADMAP "5 categories" summary; present in the line-84 differential sweep) |
| `PlayerMetaspriteGeometryTest` (`:gbkt-examples:platformer-template`) | 2 | greps for `sprite_player_frame_0[]`; array was renamed `player_metasprites` (likely stale assertion; ROM byte-identical) |

Phase 14's release sign-off (Plan 14-08 Task 3, a blocking human-verify gate) was WITHHELD precisely because a cleanup phase must leave a tree that works end-to-end. This phase OVERRULES Phase 14 SPEC criterion 5's `:buildRom`+byte-identity-only acceptance carve-out for release purposes. The "pre-existing / out-of-scope, route to a separate test-infra phase" disposition is no longer accepted.

## Requirements

1. **Full-suite green via aggregate commands**: The two canonical suite commands both report zero test failures.
   - Current: `./gradlew test --continue` and `./gradlew pluginTest` collectively report 19 failing tests across 6 classes
   - Target: `./gradlew test --continue` exits with zero failing tests AND `./gradlew pluginTest` exits with zero failing tests, from a clean tree
   - Acceptance: Both commands run from a clean checkout report 0 failures (auto-skipped emulator-tier tests with genuinely missing prerequisites do not count as failures, but every test that runs must pass)

2. **Re-run-first scope (fix ALL red, not just the snapshot)**: The phase begins by re-running the suite fresh; the gate is "zero failing tests today," not "the 19 snapshotted tests."
   - Current: The failing set is a 2026-06-06 snapshot; the suite has not been re-confirmed and the ROADMAP under-counts (`PlatformerTemplateUatTest` omitted)
   - Target: A fresh full-suite run is the authoritative work-list; any test red at phase start is in scope, including drift since the snapshot
   - Acceptance: A phase-start fresh-run inventory is recorded; every red test in that inventory is driven green (or removed under Req 7); no red test is declared out of scope

3. **gradle-plugin IntegrationTest fixed**: The `SceneIR.copy$default` / mavenLocal data-class skew is resolved.
   - Current: `IntegrationTest` fails ×12 with `NoSuchMethodError: SceneIR.copy$default(...)` — TestKit sandbox resolves stale mavenLocal artifacts whose `SceneIR` signature predates the `zoneRefs` addition
   - Target: IntegrationTest passes under `./gradlew pluginTest` (which republishes the 7 dependency modules to mavenLocal first), via a fixture fix and/or a hermeticity fix decided in-phase
   - Acceptance: `./gradlew pluginTest` reports 0 failures including all `IntegrationTest` cases

4. **banks BanksUatTest fixed**: The dominant-colour assertion no longer fails on the by-design near-blank scene.
   - Current: `BanksUatTest` ×2 fail a dominant-colour ≥95% assertion against banks' intentionally near-blank codegen-demo scene
   - Target: Both `BanksUatTest` cases pass — by fixing a real rendering bug if the scene is wrong, OR by correcting the assertion if it is provably testing the wrong scene/premise (NOT by lowering the 95% threshold to mask a real failure)
   - Acceptance: `./gradlew :gbkt-examples:banks:test` reports 0 failures; the diagnosis for each is recorded (real-bug-fix vs provably-stale-assertion)

5. **pong PongStepAgentTest fixed**: The paddle1 OAM count assertion matches runtime.
   - Current: `PongStepAgentTest` ×1 fails "paddle1 OAM count mismatch expected=2 actual=1" (metadata expectation vs runtime OAM)
   - Target: The test passes — by fixing the codegen/metadata bug if the runtime OAM count is wrong, OR by correcting the expectation if it is provably stale (NOT by deleting the assertion to mask a real mismatch)
   - Acceptance: `./gradlew :gbkt-examples:pong:test` reports 0 failures (the pre-existing ball.c padding / ROM-hash nondeterminism PASS\* remains acceptable as it produces no test failure)

6. **platformer-template suite fixed (3 classes)**: All platformer-template test classes pass.
   - Current: `PlatformerTemplate128UatTest` ×1 (facing diff 6.80% < 10%), `PlatformerTemplateUatTest` ×1, and `PlayerMetaspriteGeometryTest` ×2 (`sprite_player_frame_0[]` not found — array renamed `player_metasprites`) all fail
   - Target: All three classes pass — `PlayerMetaspriteGeometryTest` via updating the grep to the current `player_metasprites` symbol (provably-stale assertion, ROM byte-identical); the two UAT classes via real-bug-fix or provably-stale-assertion correction per diagnosis (NOT by lowering the pixel-diff threshold to mask a real animation/facing defect)
   - Acceptance: `./gradlew :gbkt-examples:platformer-template:test` reports 0 failures across all three classes; each fix is justified diagnose-first

7. **Diagnose-first justification per failure**: No failure is fixed blind.
   - Current: Failures are catalogued but not root-caused; the fix path (real bug vs stale assertion) is undecided per test
   - Target: Each failing test has a recorded diagnosis stating root cause and which fix path was taken (real product/codegen bug fix, provably-stale assertion correction, or — if obsolete — documented removal)
   - Acceptance: A per-failure diagnosis ledger exists; zero fixes weaken a threshold/assertion to mask a genuine failure; any removed test cites the retired capability it covered

## Boundaries

**In scope:**
- Driving `./gradlew test --continue` to zero failures (all library, genre, and example modules)
- Driving `./gradlew pluginTest` to zero failures (gradle-plugin IntegrationTest)
- A phase-start fresh-run inventory of all red tests (authoritative work-list)
- Per-failure diagnosis and a fix via: real product/codegen bug fix, provably-stale assertion correction, or documented removal of an obsolete test
- Fixing the 6 known classes: IntegrationTest, BanksUatTest, PongStepAgentTest, PlatformerTemplate128UatTest, PlatformerTemplateUatTest, PlayerMetaspriteGeometryTest — plus any additional red surfaced by the fresh run

**Out of scope:**
- Tagging / publishing v0.1.0 — that is a manual human step after this phase + Phase 14 sign-off re-presentation + `/gsd-complete-milestone`
- Re-presenting Phase 14's release sign-off — downstream of this phase, not part of it
- The pong ROM-hash / ball.c padding nondeterminism (PASS\*) — it produces no test failure, so it is naturally excluded under the "fix all red" gate
- New features, new examples, or new tests beyond what is needed to make existing tests pass or to replace a provably-obsolete one
- Weakening any threshold or assertion to coerce a pass — explicitly forbidden (`feedback_quality_over_shortcuts`)
- Re-introducing any example/code retired by Phase 14

## Constraints

- **No threshold-weakening.** Green may only be reached by fixing a real bug or correcting a *provably*-stale assertion. Lowering a pixel-diff threshold, dominant-colour percentage, or OAM-count expectation to mask a genuine failure is a phase failure condition (`feedback_quality_over_shortcuts`).
- **Diagnose-first per failure.** Every fix is preceded by a recorded root-cause diagnosis distinguishing real-bug from stale-assertion.
- **gradle-plugin tests run via `pluginTest`, never `:gbkt-gradle-plugin:test` directly** (per CLAUDE.md — pluginTest republishes the 7 dependency modules to mavenLocal so IntegrationTest fixtures compile against the current DSL).
- **No parallel `gradle clean`** against the same project root (Kotlin daemon collision — `feedback_no_parallel_gradle_clean`); chain into a single invocation or run serially.
- **Wide blast radius acknowledged** (plugin IntegrationTest + 3 example UAT/StepAgent/geometry suites) — routed through the proper phase chain per `feedback_route_to_proper_phase_when_blast_radius_is_wide`.
- Emulator-tier tests that auto-skip on genuinely missing prerequisites (e.g. missing ROM/GBDK) are not failures, but any test that actually executes must pass.

## Acceptance Criteria

- [ ] `./gradlew test --continue` from a clean tree reports **0 failing tests**
- [ ] `./gradlew pluginTest` from a clean tree reports **0 failing tests** (IntegrationTest green)
- [ ] A phase-start fresh-run inventory of all red tests is recorded and every entry is resolved
- [ ] `IntegrationTest` SceneIR/mavenLocal skew resolved (12 → 0)
- [ ] `BanksUatTest` passes (2 → 0)
- [ ] `PongStepAgentTest` passes (1 → 0)
- [ ] `PlatformerTemplate128UatTest` passes (1 → 0)
- [ ] `PlatformerTemplateUatTest` passes (1 → 0)
- [ ] `PlayerMetaspriteGeometryTest` passes (2 → 0)
- [ ] A per-failure diagnosis ledger exists; zero fixes weaken a threshold/assertion to mask a genuine failure
- [ ] All 7 KEEP examples still `:buildRom` EXIT 0 after fixes (no regression to the green build state)

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                        |
|--------------------|-------|------|--------|--------------------------------------------------------------|
| Goal Clarity       | 0.92  | 0.75 | ✓      | Zero failures on two named aggregate commands; diagnose-first |
| Boundary Clarity   | 0.88  | 0.70 | ✓      | Re-run + fix ALL red; tag/sign-off explicitly out of scope    |
| Constraint Clarity | 0.82  | 0.65 | ✓      | No-weaken hard rule; fix-bug-or-provably-stale latitude; pluginTest path |
| Acceptance Criteria| 0.88  | 0.70 | ✓      | 11 pass/fail checkboxes incl. per-class counts                |
| **Ambiguity**      | 0.118 | ≤0.20| ✓      | Gate passed round 1                                           |

Status: ✓ = met minimum, ⚠ = below minimum (planner treats as assumption)

## Interview Log

| Round | Perspective            | Question summary                                  | Decision locked                                                              |
|-------|------------------------|---------------------------------------------------|-----------------------------------------------------------------------------|
| 1     | Researcher/Boundary    | Scope — exactly the snapshot, or re-run + fix all? | Re-run the suite fresh; fix ALL red (catches under-counted PlatformerTemplateUatTest + drift) |
| 1     | Boundary/Acceptance    | What command(s) define "suite green"?             | `./gradlew test --continue` + `./gradlew pluginTest` both report zero failures |
| 1     | Constraint             | Latitude when a test's premise is provably wrong? | Fix real bug OR correct provably-stale assertion; NEVER weaken a threshold to mask a real failure |

---

*Phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin*
*Spec created: 2026-06-09*
*Next step: /gsd-discuss-phase 15 — implementation decisions (per-failure diagnosis approach, IntegrationTest fixture-vs-hermeticity choice, UAT assertion-vs-bug determinations)*
