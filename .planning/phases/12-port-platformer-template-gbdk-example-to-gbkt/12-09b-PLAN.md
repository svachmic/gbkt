---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 09b
type: execute
wave: 11
depends_on:
  - 12-17
files_modified:
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt
autonomous: true
requirements:
  - D-08    # Anchor 1 (title→gameplay) + Anchor 5 (level-switch) — emission substrate
  - D-16    # JVM-tier emission invariants #1 + #5 (per-function awk brace-walk)
  - D-overfitting-2  # awk brace-walk for per-function invariants (NOT file-level grep)
threat_model:
  scope: "JVM tests asserting generated C shape; no I/O beyond test fixture files"
  threats: []
  asvs_level: 1
  block_on: high
must_haves:
  truths:
    - "TitleSceneEmissionTest builds a minimal game with a title scene + gameplay scene + tilemap-collision; invokes GBDKPipelineV2; extracts title_frame body via extractFunctionBody (awk brace-walk equivalent); asserts navigate_to_scene call appears WITHIN scope"
    - "TitleSceneEmissionTest also extracts gameplay_enter body and asserts setup_current_level call appears WITHIN scope (anchor 1 second-half: gameplay scene activates level setup)"
    - "LevelSwitchEmissionTest builds a tilemap-collision game with 2+ levels; invokes GBDKPipelineV2; extracts main() function body from main.c via extractFunctionBody; asserts the level-switch guard shape WITHIN scope: contains `_next_level_idx != _current_level` (or equivalent ordering), `navigate_to_scene(SCENE_NEXTLEVEL)` (or show_centered_NextLevel equivalent), and `setup_current_level` call reachable from the guard"
    - "Both tests have a NEGATIVE gate verification: a game WITHOUT tilemap-collision (no solidThreshold) does NOT emit the level-switch guard nor the setup_current_level function"
    - "Both tests use the same extractFunctionBody helper (copy verbatim from BanksEmissionTest.kt) — per CLAUDE.md §Scope-level grep gates corollary, file-level grep is INSUFFICIENT for per-function invariants"
  artifacts:
    - path: "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt"
      provides: "JVM-tier emission test (D-16 invariant #1) — locks title_frame navigate_to_scene shape + gameplay_enter setup_current_level shape"
      contains: "navigate_to_scene"
    - path: "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt"
      provides: "JVM-tier emission test (D-16 invariant #5) — locks main() level-switch guard shape"
      contains: "_next_level"
  key_links:
    - from: "TitleSceneEmissionTest"
      to: "GBDKPipelineV2.buildHomeFile (scene-enter codegen, navigate_to_scene emission)"
      via: "pipeline invocation in test setUp"
      pattern: "GBDKPipelineV2\\(\\)"
    - from: "LevelSwitchEmissionTest"
      to: "GBDKPipelineV2.buildMainLoopLevelSwitchGuardIfNeeded (Plan 12-17 Task 2)"
      via: "pipeline invocation in test setUp"
      pattern: "GBDKPipelineV2\\(\\)"
---

<objective>
Lock D-16 invariants #1 (title→gameplay scene transition emission) and #5 (main() level-switch
guard shape) at the JVM tier via per-function awk brace-walk emission tests, per CLAUDE.md
§"Scope-level grep gates corollary" and per the existing pattern established by Plan 12-09
TilemapCollisionEmissionTest.

Purpose: Anchors 1 + 5 currently have ONLY emulator-runtime UAT (Plans 12-19 + 12-23). Per CLAUDE.md
§"Scope-level grep gates corollary", emulator-runtime assertions cannot independently catch a future
codegen regression — they are downstream of the generated C shape. JVM-tier per-function awk
brace-walk tests must lock the shape one level below the visual outcome.

Why a dedicated plan (not extension of 12-09): 12-09 is gbkt-genre-platformer scope
(TilemapCollisionEmissionTest tests platformer-visitor output). Invariants #1 and #5 are
gbkt-backend-gbdk scope (the pipeline emits title_frame navigate_to_scene and main() level-switch
guard, NOT the platformer visitor). Splitting is the cleanest plan-affinity boundary.

Why depends_on 12-17 (not 12-09): The level-switch guard is emitted by
`GBDKPipelineV2.buildMainLoopLevelSwitchGuardIfNeeded` introduced in Plan 12-17 Task 2. Title
scene `navigate_to_scene` emission lives in the existing scene-enter codegen which 12-17 also
touches when wiring the nextLevelScene navigation.

Output: 2 JVM test class files (each with positive + negative gate methods), placed in
gbkt-backend-gbdk so they share the same package as the pipeline they test.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-CONTEXT.md
@.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-RESEARCH.md
@.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-VALIDATION.md
@gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt
@gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt
@gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create TitleSceneEmissionTest (D-16 invariant #1)</name>
  <read_first>
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt (extractFunctionBody helper — copy verbatim)
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt (from Plan 12-09 — copy game-setup pattern + GBDKPipelineV2 invocation)
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-VALIDATION.md §Per-Anchor Verification Map row 1 (anchor 1 awk + grep predicates verbatim)
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-RESEARCH.md §"Validation Architecture — Anchor 1" (awk pattern verbatim)
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-17-PLAN.md Task 1 + Task 2 (which functions emit and where they live in main.c / bank1.c)
    - Any existing backend-gbdk pipeline test for the canonical test class header + package
  </read_first>
  <files>
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt (CREATE)
  </files>
  <action>
    Create new test file with MPL 2.0 header, `package io.github.gbkt.backend.gbdk.codegen.pipeline`,
    standard imports (`kotlin.test.*`, the GBDKPipelineV2 import, java.io.File).

    Class `TitleSceneEmissionTest` with:

    1. **Companion object** with `EVIDENCE_DIR` pointing at
       `../../.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape`
       (normalized) — same pattern as BanksEmissionTest.

    2. **Private helper** `extractFunctionBody(c: String, functionSignaturePrefix: String): String`
       — copy verbatim from BanksEmissionTest.kt. This is the awk brace-walk equivalent: it returns
       the substring of `c` starting at the line beginning with `functionSignaturePrefix` and ending
       at the matching closing `}`. Throws AssertionError if no match found.

    3. **Test 1 — `titleFrame_emits_navigate_to_scene` (positive case, D-16 #1 first-half):**
       - Build a minimal game with two scenes: `title` and `gameplay`. Title scene's frame body
         contains a `whenever(buttons.start.pressed) { navigate("gameplay") }` per the
         PlatformerTemplate.kt pattern landed by Plan 12-16/12-17.
       - Invoke `GBDKPipelineV2()` to produce generated C output (mirror Plan 12-09's invocation
         pattern; the pipeline construction is identical).
       - Read the file containing `title_frame` (likely `bank1.c` per RESEARCH §Anchor 1 awk pattern;
         if codegen places it elsewhere, locate by grepping all generated files).
       - Assert `Regex("^void title_frame", RegexOption.MULTILINE).containsMatchIn(fileText)`.
       - Extract function body via `extractFunctionBody(fileText, "void title_frame")`.
       - Assert body contains `navigate_to_scene` (the literal call into the scene-navigation HOME
         helper).
       - Save evidence: write extracted body to `EVIDENCE_DIR/title_frame.c` for inspection.

    4. **Test 2 — `gameplayEnter_emits_setup_current_level` (positive case, D-16 #1 second-half):**
       - Use the SAME game IR from Test 1 (or rebuild — the pipeline call is fast).
       - Read the file containing `gameplay_enter` (bank1.c).
       - Assert `Regex("^void gameplay_enter", RegexOption.MULTILINE).containsMatchIn(fileText)`.
       - Extract function body via `extractFunctionBody(fileText, "void gameplay_enter")`.
       - Assert body contains `setup_current_level` (call into the HOME-bank helper introduced by
         Plan 12-17 Task 2).
       - Save evidence: write extracted body to `EVIDENCE_DIR/gameplay_enter.c`.

    5. **Test 3 — `noTilemap_omits_setupCurrentLevel` (negative gate verification):**
       - Build a minimal game with `title` + `gameplay` scenes BUT NO `platformerPhysics { solidThreshold(...) }`
         (no tilemap-collision configured).
       - Read bank1.c.
       - Assert `gameplay_enter` exists.
       - Extract body; assert it does NOT contain `setup_current_level` (gate is OFF when tilemap
         collision absent — `buildSetupCurrentLevelFunctionIfNeeded` returns null).

    All assertions use `kotlin.test.assertTrue` / `assertFalse` / `assertContains`. No
    `kotlin.text` regex helpers outside the multiline patterns above.
  </action>
  <verify>
    <automated>./gradlew :gbkt-backend-gbdk:test --tests "TitleSceneEmissionTest" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - File `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt` exists
    - File contains `private fun extractFunctionBody(`
    - File contains 3 `@Test` methods (titleFrame_emits_navigate_to_scene, gameplayEnter_emits_setup_current_level, noTilemap_omits_setupCurrentLevel)
    - All 3 tests pass: `./gradlew :gbkt-backend-gbdk:test --tests "TitleSceneEmissionTest"` exits 0
    - Positive tests assert `navigate_to_scene` and `setup_current_level` WITHIN their respective extracted scopes
    - Negative test asserts gate behavior (no `setup_current_level` when tilemap-collision absent)
    - Evidence written to `evidence/tier1-shape/title_frame.c` + `evidence/tier1-shape/gameplay_enter.c`
  </acceptance_criteria>
  <done>D-16 invariant #1 GREEN at JVM tier — title scene navigation + gameplay enter setup_current_level shapes locked.</done>
</task>

<task type="auto">
  <name>Task 2: Create LevelSwitchEmissionTest (D-16 invariant #5)</name>
  <read_first>
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt (just created — copy extractFunctionBody helper verbatim)
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-VALIDATION.md §Per-Anchor Verification Map row 5 (anchor 5 awk + grep predicates verbatim)
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-RESEARCH.md §"Validation Architecture — Anchor 5" (awk pattern verbatim)
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-17-PLAN.md Task 2 (the exact shape emitted by buildMainLoopLevelSwitchGuardIfNeeded — `_next_level_idx != _current_level` guard + `navigate_to_scene(SCENE_NEXTLEVEL)` + the setup_current_level call from gameplay enter)
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-RESEARCH.md §"Reference Oracle Quick Map — src/main.c" lines 44-82 (reference level-switch guard shape)
  </read_first>
  <files>
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt (CREATE)
  </files>
  <action>
    Create new test file with MPL 2.0 header, same package as Task 1, same imports.

    Class `LevelSwitchEmissionTest` with:

    1. **Companion object** with `EVIDENCE_DIR` (same pattern as TitleSceneEmissionTest).

    2. **Private helper** `extractFunctionBody(...)` — copy verbatim from TitleSceneEmissionTest.kt
       (built in Task 1).

    3. **Test 1 — `main_emits_levelSwitch_guard` (positive case, D-16 #5):**
       - Build a minimal game with tilemap-collision (`platformerPhysics { solidThreshold(17) }`),
         2 zones (level 0 + level 1), a title scene, a gameplay scene, and a nextLevel scene per
         the PlatformerTemplate.kt pattern landed by Plan 12-17.
       - Invoke `GBDKPipelineV2()` to produce generated C output.
       - Read `main.c` (the HOME bank file — buildHomeFile output).
       - Assert `Regex("^void main\\(\\)", RegexOption.MULTILINE).containsMatchIn(mainC)` —
         confirms main() exists at column 0 (NOT a forward declaration; the function definition).
       - Extract function body via `extractFunctionBody(mainC, "void main()")`.
       - Assert body contains the guard:
         - `_next_level_idx` (the counter variable) AND
         - `_current_level` (the comparand) AND
         - either `!=` between them OR equivalent guard literal `_next_level_idx != _current_level`
           (whitespace-tolerant: use `body.replace(Regex("\\s+"), " ").contains("_next_level_idx != _current_level")`
           OR check both `_next_level_idx` and `_current_level` appear within 80 chars of each other
           — exact predicate per VALIDATION.md awk pattern).
       - Assert body contains `navigate_to_scene` (the guard's body navigates to the nextLevel
         scene OR equivalent show_centered_NextLevel call, per Plan 12-17 Task 2 chosen approach).
       - Assert body contains `setup_current_level` — confirms the setup call is reachable from the
         level-switch path (either directly inside the guard, or via the gameplay scene's enter the
         next time it activates; Plan 12-17 Task 2 chose the gameplay-enter route — accept either
         shape as long as `setup_current_level` appears in main() OR is unambiguously called by the
         scene-enter codegen for gameplay).
       - Save evidence: write extracted main() body to `EVIDENCE_DIR/main_levelSwitch.c`.

    4. **Test 2 — `setupCurrentLevel_function_emitted` (companion gate check):**
       - Same game IR.
       - Assert main.c contains a `void setup_current_level(void)` function definition at column 0
         (use multiline regex). This proves the helper exists; without it the level-switch guard
         body is dead code.
       - Extract setup_current_level body; assert it contains a `switch` on `_current_level` (or
         equivalent dispatch); assert it contains `_current_area_bank` (per Plan 12-17 Task 2 spec
         — the per-level dispatch must update `_current_area_bank` for each case).
       - Save evidence to `EVIDENCE_DIR/setup_current_level.c`.

    5. **Test 3 — `noTilemap_omits_levelSwitch_guard` (negative gate verification):**
       - Build a game WITHOUT tilemap-collision (no solidThreshold, no zones with tileset).
       - Read main.c.
       - Extract main() body via extractFunctionBody.
       - Assert body does NOT contain `_next_level_idx != _current_level` (guard gated off).
       - Assert main.c does NOT contain `void setup_current_level` (helper gated off).
  </action>
  <verify>
    <automated>./gradlew :gbkt-backend-gbdk:test --tests "LevelSwitchEmissionTest" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - File `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt` exists
    - File contains `private fun extractFunctionBody(`
    - File contains 3 `@Test` methods (main_emits_levelSwitch_guard, setupCurrentLevel_function_emitted, noTilemap_omits_levelSwitch_guard)
    - All 3 tests pass: `./gradlew :gbkt-backend-gbdk:test --tests "LevelSwitchEmissionTest"` exits 0
    - Positive test asserts `_next_level_idx` + `_current_level` + `navigate_to_scene` + `setup_current_level` ALL within main() extracted scope
    - Companion gate test asserts setup_current_level exists with `_current_area_bank` reference
    - Negative test asserts both the guard AND the helper are absent when tilemap-collision off
    - Evidence written to `evidence/tier1-shape/main_levelSwitch.c` + `evidence/tier1-shape/setup_current_level.c`
  </acceptance_criteria>
  <done>D-16 invariant #5 GREEN at JVM tier — main() level-switch guard shape locked; future codegen drift in Plan 12-17 Task 2 will fail this test independently of the emulator UAT (Plan 12-23).</done>
</task>

</tasks>

<verification>
  - `./gradlew :gbkt-backend-gbdk:test --tests "TitleSceneEmissionTest" --tests "LevelSwitchEmissionTest" --quiet` exits 0
  - Both test files contain `extractFunctionBody` (awk brace-walk equivalent — per CLAUDE.md §Scope-level grep gates corollary)
  - Neither test relies on file-level grep for any per-function invariant
</verification>

<success_criteria>
  - D-16 invariant #1 (title→gameplay navigation emission) locked at JVM tier
  - D-16 invariant #5 (main() level-switch guard) locked at JVM tier
  - Both invariants independently catch future codegen drift (no dependency on emulator UAT)
  - Anti-overfitting doctrine satisfied (per-function awk brace-walk, NOT file-level grep)
</success_criteria>

<output>
Create `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-09b-SUMMARY.md` when done.
</output>
</content>
