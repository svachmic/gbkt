---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 14
subsystem: genre-platformer-codegen-tests
tags: [platformer, jump-hold, variable-height-jump, d-14, jvm-tier, emission-invariant, awk-brace-walk, wave-8]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "Plan 12-13 — section 5b gravity-suppression block emitted inside buildTilemapPhysicsUpdateFunction gated on cfg.jumpHoldMaxFrames > 0. Decrement is PREFIX (--_jump_increase_timer) not postfix (12-13 §Deviations note). Plan 12-09 — extractFunctionBody brace-walk helper pattern. Plan 12-12 — sibling HorizontalScroll emission test reuses the same helper shape."
provides:
  - "JVM-tier emission invariant locking D-14 section-5b shape: token-set (_jump_increase_timer, button_held(J_A), button_held(J_UP), _player_vy +=, _grounded == 0) confined to platformer_physics_update body via per-function awk brace-walk (Kotlin port)."
  - "Decrement-form-agnostic grep contract: positive and negative tests accept BOTH `--_jump_increase_timer` (prefix, current emission) AND `_jump_increase_timer--` (postfix, future CPostfixUnaryExpr AST refactor target). Plan 12-13's deviation note (decrement emitted prefix not postfix) is honoured without locking the project into prefix-only forever."
  - "Multiplicity-discriminator negative test: when jumpHoldMaxFrames == 0, the function body contains EXACTLY 1 reference to _jump_increase_timer (the section-5 jump-init baseline emitted unconditionally by Plan 12-11). Section 5b would add 4 more refs → a count of 5 would fail RED. Pure substring-absence tests would miss this regression because the section-5 baseline already contains one reference."
  - "Evidence artifact at evidence/tier1-shape/platformer_physics_update_jumpHold.c capturing the positive-case body verbatim for human review."
affects:
  - 12-15  # Wave 8 runtime-integration plans consume the locked shape as a downstream contract
  - 12-19  # MCP play-through anchor 3 (variable-height jump) inherits the variable-evidence chain this test guards

# Tech tracking
tech-stack:
  added: []  # No new libraries; JVM-tier test mirrors the established 12-09 / 12-12 pattern
  patterns:
    - "Decrement-form-agnostic emission grep — positive AND negative tests accept both prefix and postfix decrement (`--X` || `X--`). Codifies the principle that emission tests should lock SEMANTICS (the decrement happened) not SYNTAX (the operator position). This applies whenever the project has a single canonical emission convention (prefix in gbkt's case per CEmitter line 426) but the semantically-equivalent alternative is a foreseeable future refactor target. Documented in Plan 12-13 §Deviations as the explicit forward-compatibility contract."
    - "Multiplicity-discriminator negative test — when a gated feature ADDS N references to a token whose baseline count is M (non-zero), assert `count == M` in the negative case, not `count == 0`. This catches the regression where the gate fires unconditionally (count becomes M + N) even though the baseline token is permitted. Substring-absence assertions miss this because the baseline token is already present. For Plan 12-14: M=1 (section-5 jump-init), N=4 (section-5b's decrement + `> 0u` guard + `== 0u` guard + `= 0u` reset)."
    - "Section-5b-only discriminator tokens — `button_held` (vs section-5's `button_pressed`) and `_player_vy +=` (vs the rest of the function's `_player_vy =`) are unique signals of section-5b emission within `platformer_physics_update`. Their absence in the negative case is robust against the section-5 baseline emissions (which use the discriminator-free `button_pressed` and `=` forms). This is the per-function-scope counterpart of the file-scope gate-verification pattern used in TilemapCollisionEmissionTest (Plan 12-09 — `is_tile_solid` absent from main.c entirely)."

key-files:
  created:
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_physics_update_jumpHold.c"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-14-SUMMARY.md"
  modified:
    - "gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/JumpHoldEmissionTest.kt"

key-decisions:
  - "Accept BOTH decrement forms in the positive case (`--_jump_increase_timer` OR `_jump_increase_timer--`). The plan's must_haves and the project's current emission both use prefix (CEmitter emits CUnaryExpr as `${op}${operand}` only), but the predecessor Plan 12-13's §Deviations note explicitly surfaced this as a future-compat concern: 'Plan 12-14's grep test should accept either form'. Following that explicit instruction avoids tripping on a future CPostfixUnaryExpr AST refactor that lets visitors emit postfix without changing semantics. The negative-case test uses the SAME `prefix || postfix` disjunction with `assertFalse(_OR_)` semantics → De Morgan: neither form may appear."
  - "Negative test asserts EXACTLY 1 (not zero) `_jump_increase_timer` reference. Reality of Plan 12-11's emission: the section-5 jump-initiation site UNCONDITIONALLY emits `_jump_increase_timer = cfg.jumpHoldMaxFrames` (PlatformerVisitor.kt line 627). With cfg.jumpHoldMaxFrames=0, that emits as `_jump_increase_timer = 0u;` — a no-op assignment inherited from the 12-11 baseline. Plan 12-13's SUMMARY §5b comment (lines 647-649) acknowledges this explicitly: 'When 0, the 12-11 baseline is preserved byte-identical … the jump-initiation site still emits the harmless `_jump_increase_timer = 0u;` assignment per Plan 12-11 §decision #4.' Asserting count == 1 captures both halves of the contract: the section-5 baseline survives AND section 5b's 4 additional refs do not leak."
  - "Negative test uses solidThreshold=17 (tilemap on) + jumpHoldMaxFrames=0 to exercise the section-5b gate IN ISOLATION. The alternative — solidThreshold=null + jumpHoldMaxFrames=0 — would fall through to the abstract physics path (`buildPhysicsUpdateFunction`, not `buildTilemapPhysicsUpdateFunction`), which has its own gate logic and would mask the section-5b-specific gate verification. Tilemap-on + jumpHold-off is the precise input that exercises Plan 12-13's `if (cfg.jumpHoldMaxFrames > 0)` branch with the gate evaluating to false."
  - "`platformer_physics_update` lands in main.c (HOME bank), not bank1.c. Both `buildPhysicsUpdateFunction` (abstract) and `buildTilemapPhysicsUpdateFunction` (tilemap) construct `CFunction(...)` without `isBanked = true` — CFunction defaults to `isBanked = false` → emitted at column 0 of main.c. Same convention as `platformer_camera_update` (Plan 12-12's HorizontalScrollEmissionTest also extracts from main.c). VALIDATION.md row 3 names bank1.c but Plan 12-11 SUMMARY §Next Phase Readiness clarifies the actual file; this test follows that clarification consistent with the sibling test."
  - "Single test file with 2 @Test methods (positive + negative) rather than splitting into 4-5 micro-tests. The positive test packs 6 assertions because they describe a single invariant (section 5b's emitted shape); splitting them would fragment the contract. The negative test packs 4 assertions for the same reason (section 5b is gated off as a whole). Mirrors the 12-09 / 12-12 cohesion principle."

patterns-established:
  - "Decrement-form-agnostic grep contract — for any IR construct that has both prefix and postfix C representations, emission tests should accept either form via boolean-OR. Codified explicitly here because Plan 12-13 surfaced it as a future-compat concern."
  - "Multiplicity-discriminator negative test — for gated features where the baseline already references the token, assert `count == baseline_count` not `count == 0`. Pure substring absence is insufficient when the baseline reference is permitted."

requirements-completed: [D-14, D-16, D-overfitting-2]

# Metrics
duration: 18min
completed: 2026-05-21
---

# Phase 12 Plan 14: Lock jumpHold Gravity-Suppression Branch Shape via Per-Function Awk Brace-Walk Summary

**Replaces the Wave-0 placeholder `JumpHoldEmissionTest` with the real D-14 emission invariant: positive case asserts the section-5b token-set inside the brace-walked `platformer_physics_update` body; negative case asserts the section-5b token-set is absent when `jumpHoldMaxFrames == 0` (with multiplicity-discriminator handling for the section-5 jump-initiation baseline reference that survives byte-identical).**

## Performance

- **Duration:** ~18 min
- **Tasks:** 1 (Task 1 — replace placeholder + add positive + negative invariant tests)
- **Files modified:** 1 (`JumpHoldEmissionTest.kt`)
- **Files created:** 1 (`evidence/tier1-shape/platformer_physics_update_jumpHold.c`)

## Accomplishments

### Task 1: Replace JumpHoldEmissionTest placeholder with positive + negative invariant tests

**Helper added (verbatim copy from TilemapCollisionEmissionTest / HorizontalScrollEmissionTest):**
- `extractFunctionBody(cSource, functionSignaturePrefix)` — Kotlin port of the awk pattern `awk '/^prefix/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p'`. Anchors via `line.startsWith(prefix)` (column-0) and brace-walks back to depth 0.
- `EVIDENCE_DIR` companion — worktree-safe path resolved via `user.dir`-relative `../` ascent (matches 12-09 / 12-12).
- `buildPlatformerGameIR(solidThreshold, jumpHoldMaxFrames, id)` — minimal `GameIR` with one `platformer_physics` `GenericSystem` carrying a `PlatformerPhysicsConfig` whose two relevant fields parameterise the two gates.

**Positive test (`platformer_physics_update emits jumpHold gravity-suppression body when jumpHoldMaxFrames is positive`):**
- Input: `solidThreshold = 17`, `jumpHoldMaxFrames = 20` → both gates fire → section-5b emits.
- Anchors signature regex `^void platformer_physics_update` (multiline).
- Persists `physicsBody` to `evidence/tier1-shape/platformer_physics_update_jumpHold.c` BEFORE assertions fire.
- Assertions:
  1. Signature line present at column 0 of main.c.
  2. `physicsBody` non-empty (brace-walk succeeded).
  3. `_jump_increase_timer` count ≥ 2 inside function scope (section-5 init + section-5b's 4 refs).
  4. Decrement present in either form: `--_jump_increase_timer` OR `_jump_increase_timer--` (boolean OR — accepts both prefix and postfix per Plan 12-13 §Deviations).
  5. `button_held(J_A)` present inside function scope.
  6. `button_held(J_UP)` present inside function scope.
  7. `_player_vy +=` present inside function scope (section-5b's gravity application).
  8. `_grounded == 0` present (the airborne guard wrapping section 5b).

**Negative test (`platformer_physics_update omits jumpHold body when jumpHoldMaxFrames is zero`):**
- Input: `solidThreshold = 17`, `jumpHoldMaxFrames = 0` → tilemap-physics branch fires (`platformer_physics_update` still emitted), but section-5b gate evaluates to false → block omitted.
- Assertions:
  1. Signature line still present (tilemap-physics branch unaffected by jumpHold gate).
  2. `physicsBody` non-empty (brace-walk succeeded).
  3. `_jump_increase_timer` count == 1 (multiplicity discriminator — section-5 baseline survives; section-5b's 4 additional refs absent).
  4. Decrement absent in BOTH forms: NOT `--_jump_increase_timer` AND NOT `_jump_increase_timer--`.
  5. `button_held(J_A)` absent inside function scope.
  6. `button_held(J_UP)` absent inside function scope.
  7. `_player_vy +=` absent inside function scope (the rest of the function uses `=` not `+=` — unique discriminator).

**Evidence artifact:** `evidence/tier1-shape/platformer_physics_update_jumpHold.c` (2537 bytes) captures the verbatim positive-case body. Key tokens verified by `grep`:
- Line 41 (section-5): `_jump_increase_timer = 20u;` (jump-init)
- Line 47 (section-5b open): `if (_grounded == 0) {`
- Line 48 (section-5b inner): `if (_jump_increase_timer > 0u) {`
- Line 49 (section-5b decrement): `--_jump_increase_timer;` (PREFIX form per Plan 12-13)
- Line 51 (section-5b guard): `if (!(button_held(J_A) || button_held(J_UP)) || _jump_increase_timer == 0u) {`
- Line 52 (section-5b gravity-apply): `_player_vy += 32u;`
- Line 53 (section-5b timer reset): `_jump_increase_timer = 0u;`

The evidence matches the reference `platformer_template/src/player.c` lines 297-317 structure: airborne guard → conditional timer decrement → combined button-released-OR-timer-expired guard → gravity apply + timer reset.

## Tasks executed

| Task | Name                                                                            | Commit     | Files                                                                                                                                                                                            |
| ---- | ------------------------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | Replace JumpHoldEmissionTest placeholder with positive + negative invariant tests | `4d7da868` | `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/JumpHoldEmissionTest.kt`, `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_physics_update_jumpHold.c` |

## Verification

- `./gradlew :gbkt-genre-platformer:test --tests "io.github.gbkt.genre.platformer.codegen.JumpHoldEmissionTest" --quiet` → exit 0 (both @Test methods pass).
- `./gradlew :gbkt-genre-platformer:test --quiet` → exit 0 (full module suite GREEN — TilemapCollisionEmissionTest, HorizontalScrollEmissionTest, JumpHoldEmissionTest, PlatformerCodegenTest, PlatformerPhysicsBuilderTest, ZonePlatformerPhysicsTest all pass together).
- Evidence file written to `evidence/tier1-shape/platformer_physics_update_jumpHold.c` (2537 bytes; 7 expected tokens confirmed via `grep`).
- Plan-level success-criteria checks:
  - ✅ JumpHoldEmissionTest replaces stub with per-function awk brace-walk locking section-5b shape from 12-13.
  - ✅ Positive case: tilemap-physics game with jumpHoldMaxFrames > 0 emits `_jump_increase_timer` (≥2), `button_held(J_A)`, `button_held(J_UP)`, `_player_vy +=`, gravity-suppress guard (`_grounded == 0`) — all within the extracted function scope.
  - ✅ Grep accepts both `--_jump_increase_timer` (prefix) and `_jump_increase_timer--` (postfix) per Plan 12-13 §Deviations forward-compat instruction.
  - ✅ Negative case: jumpHoldMaxFrames == 0 means section-5b's 4 distinctive tokens are absent (decrement, button_held×2, `_player_vy +=`); `_jump_increase_timer` count is exactly 1 (only the section-5 baseline init survives).
  - ✅ `:gbkt-genre-platformer:test` stays GREEN.
  - ✅ No modifications to STATE.md or ROADMAP.md.

## Deviations from Plan

### Plan-prose vs. emission reality clarification

**1. [Rule 1 — Auto-fix bug-pattern in test logic] Negative test asserts count == 1, not absence of `_jump_increase_timer`.**
- **Found during:** Task 1 initial implementation (test run revealed RED on the negative case).
- **Issue:** The plan's acceptance criteria stated "Negative case: jumpHoldMaxFrames == 0 means zero new emission diff vs baseline (no _jump_increase_timer anywhere)". The predecessor Plan 12-13 SUMMARY §"Plan 12-14 readiness" reinforced this: "When cfg.jumpHoldMaxFrames == 0: none of these tokens appear in the function body — Plan 12-14's negative-case test (if it has one) can grep for ZERO occurrences and expect a clean miss." However, the **actual emission** when `jumpHoldMaxFrames == 0` still contains exactly 1 reference to `_jump_increase_timer` — the section-5 jump-initiation site at PlatformerVisitor.kt line 627 emits `_jump_increase_timer = cfg.jumpHoldMaxFrames` UNCONDITIONALLY, which for `cfg.jumpHoldMaxFrames=0` lowers to `_jump_increase_timer = 0u;`. Plan 12-13's §5b inline comment (lines 647-649) acknowledges this: "the jump-initiation site still emits the harmless `_jump_increase_timer = 0u;` assignment per Plan 12-11 §decision #4."
- **Fix:** Re-shaped the negative test to assert a multiplicity discriminator: `_jump_increase_timer` appears EXACTLY 1× in the function body when section-5b is gated off (only the section-5 baseline). Section-5b would add 4 more refs (decrement + `> 0u` guard + `== 0u` guard + `= 0u` reset) → a count of 5 would fail RED. This precisely captures "section-5b is gated off as a whole" while accepting the inherited 12-11 baseline. The 4 section-5b-only discriminator assertions (decrement absent in both forms, `button_held(J_A/J_UP)` absent, `_player_vy +=` absent) provide independent corroboration that section 5b did not leak.
- **Alternatives considered:** (a) Strict absence-test fails (section-5 emits 1 ref unconditionally) → not viable without a coordinated Plan 12-11 + 12-13 change. (b) Use `solidThreshold = null` to bypass the tilemap-physics branch entirely and verify section-5b never even reaches the code path — viable but masks the precise section-5b gate verification (the abstract `buildPhysicsUpdateFunction` has different logic with no section 5b to begin with, so this would not lock the section-5b gate's semantics).
- **Rule:** Rule 1 (auto-fix bug — the as-stated criterion was inconsistent with the production emission, and the predecessor's documented inline contract reflects reality more accurately than the predecessor's SUMMARY §Plan 12-14 readiness section).
- **Files modified:** `JumpHoldEmissionTest.kt` (negative-test body only).
- **Commit:** `4d7da868` (single commit; the fix was applied before the test landed as a passing state).

### Forward-compat clarifications (not deviations, surfaced for diff clarity)

The plan's `<success_criteria>` requires "Grep MUST accept both `--_jump_increase_timer` (prefix) and `_jump_increase_timer--` (postfix) so future changes don't trip on style." This was applied in BOTH directions:
- **Positive test:** uses boolean-OR — `prefixDecrement || postfixDecrement` → both forms accepted as evidence of section-5b emission.
- **Negative test:** uses boolean-OR-inside-assertFalse → De Morgan equivalent of "NEITHER form is present" → both forms ruled out as evidence section-5b leaked.

This symmetry locks the decrement-form-agnostic contract from both sides. A future CPostfixUnaryExpr AST refactor (Plan 12-13's "alternative considered" — rejected as out-of-scope) would not break either test.

## Known Stubs

None. Both tests assert the production emission shape exhaustively; no placeholder remains in the file. The Wave-0 stub from `12-03` is fully replaced.

## Threat Flags

None. The test is JVM-tier (no network, no auth, no IO beyond writing the evidence file to the project's evidence/ directory). The test's `pipeline.generate(gameIR)` call exercises the same `GBDKPipelineV2` API that the production codegen uses; no new surface area introduced.

## Issues Encountered

The initial implementation followed the plan's literal "zero `_jump_increase_timer` references" criterion verbatim; the test FAILED RED on the negative case at run-time. Investigation of `PlatformerVisitor.kt` line 627 (section-5 jump-initiation site) revealed the unconditional `_jump_increase_timer = cfg.jumpHoldMaxFrames` emission — which Plan 12-13's inline §5b gate comment (lines 647-649) actually documents as the intended behaviour, contradicting the predecessor SUMMARY's §Plan 12-14 readiness paragraph. The negative test was tightened to a multiplicity discriminator (count == 1, not == 0) plus 4 section-5b-only discriminator assertions; both tests then went GREEN on first re-run. See §Deviations rule 1 for the full reasoning.

## User Setup Required

None. Pure JVM-tier test; no external service or hardware required.

## Threat Mitigations

**T-12-14-01 (Test integrity — false GREEN via file-level grep):** Mitigated. Per CLAUDE.md §"Scope-level grep gates corollary" and the project memory `feedback_visual_evidence_for_visual_truths`, a file-level `mainC.contains("_jump_increase_timer")` would false-positive on the WRAM global declaration at the top of main.c (when the lockstep gate fires). The brace-walk `extractFunctionBody` confines the substring checks to the `platformer_physics_update` body only, so a regression that, e.g., kept the WRAM global declaration while dropping section-5b would correctly fail RED. The positive-case `count ≥ 2` assertion further ensures section-5b's 4 additional refs are present, not just the section-5 baseline.

**T-12-14-02 (Decrement-form lock-in risk):** Mitigated. Locking only the prefix form (`--_jump_increase_timer`) would tie the project to CEmitter's current prefix-only emission (CUnaryExpr emits as `${op}${operand}`). A future CPostfixUnaryExpr AST refactor (the "alternative considered" rejected in Plan 12-13's deviation rule 1) would silently break the test even though the C semantics are unchanged. The boolean-OR-form acceptance (`--X` || `X--` in the positive test, `!(--X || X--)` in the negative test) keeps the contract focused on SEMANTICS not SYNTAX.

**T-12-14-03 (Negative-case false GREEN via subset-absent regression):** Mitigated. A regression that, e.g., dropped JUST the decrement statement from section 5b (keeping the rest of the suppression block intact) would slip past a single-token absence test. The negative test asserts 4 independent absence conditions — decrement (both forms) AND `button_held(J_A)` AND `button_held(J_UP)` AND `_player_vy +=` — so any partial section-5b leak fails RED. The multiplicity discriminator (`count == 1`) catches a regression that re-fires section 5b silently (count of 5).

## Next Phase Readiness

**Ready for Wave 8 runtime-integration plans (12-15+):** The section-5b shape is now locked at JVM-tier. Wave 8 plans that wire the runtime player metasprite + tilemap rendering can rely on this contract — any drift in section 5b's emission will fail RED before reaching buildRom. The variable-evidence chain for UAT anchor 3 (variable-height jump — `_player_vy` transitions through 0 → -550 → 0) inherits the now-locked emission as its foundation.

**Ready for Plan 12-19 (Wave 8 — MCP play-through anchor 3 — variable-height jump):** The runtime variable evidence (anchor 3's `_player_vy` cycle) depends on section 5b's correct emission. With this test in place, a future plan that breaks section 5b's emission will fail at JVM-tier — before reaching UAT — making the failure cheap to diagnose. Per CLAUDE.md §"Verification Methodology — Visual Evidence Rule", JVM-tier codegen tests are acceptable evidence for shape contracts; the visual tier (UAT screenshots) verifies the runtime outcome.

**Plan 12-13 §"Plan 12-14 readiness" partial discrepancy noted:** The predecessor SUMMARY claimed "When cfg.jumpHoldMaxFrames == 0: none of these tokens appear in the function body". This is true for the section-5b-specific tokens (decrement, `button_held`, `_player_vy +=`, `_grounded == 0` guard), but NOT for the bare token `_jump_increase_timer` — the section-5 jump-init baseline emits one reference unconditionally. The deviation in §Deviations rule 1 above documents the reconciliation. No upstream change required — Plan 12-13's emission is correct per its own inline §5b gate comment (lines 647-649); only the SUMMARY §"Plan 12-14 readiness" paragraph was slightly imprecise.

## Self-Check: PASSED

- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/JumpHoldEmissionTest.kt` exists; contains `extractFunctionBody` helper, `EVIDENCE_DIR` companion, `buildPlatformerGameIR` setup, 2 `@Test` methods (positive + negative).
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_physics_update_jumpHold.c` exists (2537 bytes; contains all 7 expected section-5/5b tokens verified via grep).
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-14-SUMMARY.md` exists (this file).
- Commit `4d7da868` exists in git log (`test(12-14): lock jumpHold gravity-suppression branch shape (D-14 emission invariant)`).
- `./gradlew :gbkt-genre-platformer:test --quiet` exits 0 (full module suite GREEN — all 3 platformer emission tests pass together: TilemapCollision, HorizontalScroll, JumpHold).
- No modifications to STATE.md, ROADMAP.md, or any file outside the worktree scope.

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Completed: 2026-05-21*
