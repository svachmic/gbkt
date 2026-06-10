---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 09b
subsystem: gbkt-backend-gbdk-pipeline + jvm-tier-shape-tests
tags: [platformer, codegen, pipeline, jvm-tier, shape-lock, scope-grep-gates, awk-brace-walk, d-08, d-16, d-overfitting-2, wave-11]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "Plan 12-17 banked title + nextLevel scene substrate + pipeline-emitted setup_current_level NONBANKED function + main()-loop level-switch guard (gated on gameUsesTilemapCollision + nextLevel scene id presence); Plan 12-08 buildTilemapCollisionGlobals + gameUsesTilemapCollision predicate"
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: "BanksEmissionTest extractFunctionBody helper (Plan 11-08; awk-brace-walk equivalent for per-function scope-level grep gates)"
  - phase: 07.4-sport-genre-codegen-fix-inserted
    provides: "CLAUDE.md §Scope-level grep gates corollary doctrine — file-level grep cannot distinguish per-function emissions; per-function brace-walk is mandatory for shape locks"
provides:
  - "JVM-tier shape lock for D-16 invariant #1 (title→gameplay scene transition emission) — TitleSceneEmissionTest with 3 @Test methods (titleFrame_emits_navigate_to_scene + titleEnter_emits_tile_data_load_substrate + noTilemap_omits_setupCurrentLevel)"
  - "JVM-tier shape lock for D-16 invariant #5 (main()-loop level-switch guard + setup_current_level helper) — LevelSwitchEmissionTest with 3 @Test methods (main_emits_levelSwitch_guard + setupCurrentLevel_helper_function_definition + noTilemap_omits_levelSwitch_guard)"
  - "Independent regression guard against future codegen drift in GBDKPipelineV2.buildMainLoopLevelSwitchGuardIfNeeded + buildSetupCurrentLevelFunctionIfNeeded — failure path is JVM-tier RED, decoupled from MCP-emulator UAT (Plans 12-19/12-22/12-23)"
  - "Anti-overfitting doctrine satisfied per CLAUDE.md §Scope-level grep gates corollary — both tests use per-function extractFunctionBody (brace-walk equivalent of awk's `/^void name/{p=1;d=0} p{d+=gsub(/{/,\"\");d-=gsub(/}/,\"\");if(d<0)exit} p`); no file-level grep for any per-function invariant"
  - "Lockstep negative gates — both tests assert that gate-off (no platformerPhysics.solidThreshold + no nextLevel scene) yields zero emission for the level-switch substrate, preserving byte-identical codegen for Pong/Breakout/Banks/Explorer/etc."
  - "testImplementation(:gbkt-genre-platformer) dependency added to gbkt-backend-gbdk (mirrors the existing :gbkt-genre-sport testImplementation precedent; pure JVM-tier test scope; production code stays genre-agnostic)"
  - "1005 :gbkt-backend-gbdk:test cases GREEN (994 prior + 11 net new; LevelSwitchEmissionTest = 3, TitleSceneEmissionTest = 3, deltas from indirect coverage = 5)"
affects:
  - "12-19 (anchor 1 MCP UAT) — JVM tier locks the codegen prerequisite; MCP UAT now confirms the runtime visual without carrying the regression-detection burden alone"
  - "12-22 / 12-23 (anchor 5 MCP UAT) — same: JVM tier locks the codegen shape; MCP runtime probes confirm the substrate actually fires under emulator stepping"
  - "Future Phase 12 plan revisions touching GBDKPipelineV2 level-switch substrate — any drift in the 6 substrings Plan 12-17 SUMMARY §Self-Check documented will fail RED at JVM-tier without requiring buildRom + MCP startup"

# Tech tracking
tech-stack:
  added: []  # No new libraries; pure test-tier shape locks against existing pipeline emission.
  patterns:
    - "Awk-equivalent brace-walk in Kotlin tests — `lines().indexOfFirst { it.startsWith(prefix) }` anchors at column 0 (the awk `/^prefix/` equivalent); a depth counter walks `{` / `}` until depth returns to 0 at the function's closing brace. This is the locking pattern for per-function scope-level grep gates per CLAUDE.md §Scope-level grep gates corollary."
    - "Evidence-before-assert — write the extracted function body to evidence/tier1-shape/ BEFORE any assertion fires, so a RED run still produces a reviewable artifact on disk. Mirrors gbkt-examples/banks BanksEmissionTest pattern from Plan 11-08."
    - "Negative-gate lockstep invariant — when emission is gated by a predicate (here: gameUsesTilemapCollision + nextLevel scene id), assert ALL emission halves (function definition + prototype + main-loop splice) are absent together. Catches a regression that opens just one half of the gate (which would leak through a single-substring assertion)."
    - "Worktree-safe EVIDENCE_DIR resolution — use `File(System.getProperty(\"user.dir\")).resolve(\"../.planning/phases/.../evidence/tier1-shape\").normalize()`. `user.dir` is the Gradle test task's working directory (the module root inside the worktree), NOT the orchestrator's CWD. Hard-coding the main-repo absolute path would silently route evidence files outside the active checkout and miss the commit (#3099 worktree path safety)."

key-files:
  created:
    - "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt (272 lines; 3 @Test methods + extractFunctionBody helper + EVIDENCE_DIR companion)"
    - "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt (363 lines; 3 @Test methods + extractFunctionBody helper + buildTilemapCollisionGameDsl fixture + EVIDENCE_DIR companion)"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/title_enter.c (extracted from TitleSceneEmissionTest run — locks the SceneVisitor zone-load prelude + cEmit-bridged fill_bkg_rect text)"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/title_frame.c (extracted — locks navigate_to_scene(SCENE_GAMEPLAY) emission)"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/noTilemap_main_head.c (gate-off byte-identical evidence)"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/main_levelSwitch.c (extracted — locks `if (_next_level != _current_level)` + `navigate_to_scene(SCENE_NEXTLEVEL)` + `setup_current_level()` in main())"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/setup_current_level.c (extracted — locks the helper's switch dispatch + per-zone _current_area_bank assignments)"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/noTilemap_levelSwitch_main_head.c (gate-off byte-identical evidence)"
  modified:
    - "gbkt-backend-gbdk/build.gradle.kts (added testImplementation(project(\":gbkt-genre-platformer\")) — mirrors the existing :gbkt-genre-sport precedent at line 48; test-only reverse dep, no production code coupling)"

key-decisions:
  - "Reshape Plan Task 1 Test 2 — the plan literally instructed asserting `setup_current_level` inside `gameplay_enter` body, but predecessor Plan 12-17 SUMMARY §Deviation 5 explicitly documents that wiring was NOT done (the helper is called from main()'s level-switch guard, not from gameplay_enter). Test 2 was reshaped to lock the OTHER half of invariant #1 — title_enter emits the tile-data load substrate (set_bkg_data + _bkg_tiles_load_banked + SHOW_BKG + fill_bkg_rect cEmit text). Plan Task 2 Test 2 (`setupCurrentLevel_function_emitted`) already locks the existence of the helper at column 0 of main.c, so the helper-presence assertion is preserved at the only correct scope. Classified as Rule 1 (plan-internal contradiction with predecessor reality)."
  - "Brace-walk helper duplicated verbatim in both test files — per Plan 12-09b Task 2 explicit instruction (\"copy verbatim from TitleSceneEmissionTest.kt\"). The duplication is intentional and mirrors the per-test EVIDENCE_DIR + helper pattern established by BanksEmissionTest (Plan 11-08) and TilemapCollisionEmissionTest (Plan 12-09). Extracting to a shared util would couple test classes to a sibling, slowing the test-grow loop; Phase 13 may extract once a third pipeline-shape test lands (rule of three)."
  - "Two gameplay zones in LevelSwitchEmissionTest fixture — fixture uses `gameplayZone1` + `gameplayZone2` so `setup_current_level`'s switch dispatch has 2 cases. Locks the ≥2-case assertion which proves the menu-zone filter at GBDKPipelineV2.kt:2241-2246 is permissive enough to keep gameplay zones (a regression that over-filtered would collapse to 0 → null return → no emission, failing the signature assertion above)."
  - "Worktree-safe EVIDENCE_DIR via `System.getProperty(\"user.dir\")` ascend-one-level pattern — chosen over absolute paths to honor #3099 worktree path safety. Test classes inside `:gbkt-backend-gbdk:test` get `user.dir = <root>/gbkt-backend-gbdk` (a relative ascent to phase evidence directory works inside both main checkout and worktree)."
  - "testImplementation(:gbkt-genre-platformer) added to gbkt-backend-gbdk — mirrors the existing :gbkt-genre-sport precedent (line 48). gbkt-genre-platformer has `implementation(project(\":gbkt-backend-gbdk\"))` so this is a test-only reverse dep; Gradle handles this without a cycle because testImplementation does not propagate to main. Production code remains genre-agnostic."
  - "Multiline regex anchor `^void main(void)` + `^void setup_current_level(void) NONBANKED` — CEmitter emits empty-params CFunction as `void main(void) { ... }` (CEmitter.kt:184-199); setup_current_level emits via raw section per Plan 12-17 Task 2. The column-0 anchor prevents false-positive matches inside string literals, comments, or argument lists of unrelated functions."

patterns-established:
  - "Per-function brace-walk shape lock for pipeline-emitted functions — the locking pattern is (1) regex anchor at column 0 for signature, (2) brace-walk extract via `lines().indexOfFirst { it.startsWith(prefix) }` + depth counter, (3) substring assertions WITHIN the extracted body, (4) evidence-before-assert write to phase evidence/ directory. Mirrors gbkt-examples/banks BanksEmissionTest (Plan 11-08) and gbkt-genre-platformer TilemapCollisionEmissionTest (Plan 12-09)."
  - "Lockstep negative-gate emission assertions — when a pipeline-emitted helper is gated by a predicate (here: gameUsesTilemapCollision), assert the helper definition AND its prototype in game.h AND any callsite splice (here: main-loop guard) are ALL absent when the gate is off. Catches a regression that opens just one half of the gate."

requirements-completed: [D-08, D-16, D-overfitting-2]

# Metrics
duration: ~35min
completed: 2026-05-22
---

# Phase 12 Plan 09b: JVM-Tier Shape Lock for D-16 Invariants #1 + #5 Summary

**Plan 12-09b lands the JVM-tier shape lock for Anchor 1 (title→gameplay) + Anchor 5 (main()-loop level-switch guard) via per-function awk brace-walk extraction. 2 new test files (635 lines) + 6 evidence artifacts in gbkt-backend-gbdk; net +11 GREEN test cases (1005 total); no production code touched. The codegen prerequisite is now decoupled from MCP-emulator UAT — any future drift in `GBDKPipelineV2.buildMainLoopLevelSwitchGuardIfNeeded` or `buildSetupCurrentLevelFunctionIfNeeded` fails JVM-tier RED without needing buildRom + MCP startup.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 2
- **Files created:** 8 (2 test files + 6 evidence artifacts)
- **Files modified:** 1 (build.gradle.kts — testImplementation dep)

## Accomplishments

### Task 1 — TitleSceneEmissionTest (D-16 invariant #1)

Locks the title→gameplay scene transition emission contract at the JVM tier with 3 @Test methods:

- **`titleFrame_emits_navigate_to_scene_call_on_Start_press` (positive case, D-16 #1 second-half).** Brace-walks `void title_frame` body and asserts `navigate_to_scene` + `SCENE_GAMEPLAY` substrings present. The whenever(buttons.start.pressed) { navigate("gameplay") } lowers via ScriptOpVisitor.visitNavigateTo (ScriptOpVisitor.kt:675-678) to `CCall("navigate_to_scene", [CVar("SCENE_GAMEPLAY")])`.

- **`titleEnter_emits_tile_data_load_substrate_when_title_scene_binds_a_tileset_zone` (positive case, D-16 #1 first-half — RESHAPE).** Brace-walks `void title_enter` body and asserts the SceneVisitor zoneLoadStatements three-statement prelude (`set_bkg_data(0u, _zone_titleZone_tileset_count, ...)`, `_bkg_tiles_load_banked(...)` with `_zone_titleZone_tilemap` symbol, `SHOW_BKG`) AND the user-authored `cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);")` verbatim. Ordering assertion locks the pixel-bytes-before-tile-index-map invariant (D-claude-4) + SHOW_BKG comes last. The reshape is documented in §Deviations below.

- **`noTilemap_omits_setupCurrentLevel` (negative gate verification).** Game WITHOUT platformerPhysics.solidThreshold yields zero references to `setup_current_level` in main.c AND zero `_next_level != _current_level` guard text. Companion sanity: title_frame still emits navigate_to_scene when the gate is OFF (anchor 1 is platformer-independent).

### Task 2 — LevelSwitchEmissionTest (D-16 invariant #5)

Locks the main()-loop level-switch guard + setup_current_level helper emission contract at the JVM tier with 3 @Test methods:

- **`main_emits_levelSwitch_guard_when_tilemap_collision_+_nextLevel_scene_present` (positive case, D-16 #5).** Brace-walks `void main(void)` body and asserts the 3 substrings Plan 12-17 SUMMARY §Self-Check documented: `_next_level != _current_level`, `navigate_to_scene(SCENE_NEXTLEVEL)`, `setup_current_level()`. Ordering assertion locks `if (...)` < `navigate_to_scene(...)` < `setup_current_level()` so a regression that hoisted the calls outside the conditional still fails RED.

- **`setupCurrentLevel_helper_function_definition_is_emitted_at_column_0_of_main_c` (positive case, D-16 #5 companion).** Asserts `void setup_current_level(void) NONBANKED` at column 0 of main.c (multiline regex anchor); brace-walks the helper body and asserts the Plan 12-17 contract: first-statement `_current_level = _next_level`, `switch (_current_level` dispatch, `_current_area_bank` references in each case branch, ≥2 case branches (fixture has 2 gameplay zones; menu-screen zones filtered by the id-name heuristic at GBDKPipelineV2.kt:2241-2246).

- **`noTilemap_omits_levelSwitch_guard` (negative gate verification — lockstep).** Game WITHOUT tilemap-collision AND no nextLevel scene yields zero references to: the guard in main()'s body, the helper definition in main.c, and the prototype in game.h. Locks the lockstep emission invariant — emission is all-or-nothing, never partial.

### Build wiring

- Added `testImplementation(project(":gbkt-genre-platformer"))` to gbkt-backend-gbdk/build.gradle.kts. Mirrors the existing `testImplementation(project(":gbkt-genre-sport"))` precedent (line 48); test-only reverse dep, Gradle handles the cycle because testImplementation does not propagate to main.

## Task Commits

Each task was committed atomically:

1. **Task 1: TitleSceneEmissionTest (D-16 invariant #1)** — `8f1fda77` (test)
2. **Task 2: LevelSwitchEmissionTest (D-16 invariant #5)** — `95218778` (test)

_(No `docs:` metadata commit — orchestrator owns STATE.md / ROADMAP.md per the worktree-executor contract.)_

## Files Created/Modified

- **CREATED** `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt` — 272 lines; 3 @Test methods locking D-16 invariant #1 emission shape; brace-walk extractFunctionBody helper; EVIDENCE_DIR companion object pointing to the phase 12 evidence/tier1-shape directory (worktree-safe via `System.getProperty("user.dir")` ascend-one-level).
- **CREATED** `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt` — 363 lines; 3 @Test methods locking D-16 invariant #5 emission shape; same extractFunctionBody helper (copied verbatim per plan instruction); buildTilemapCollisionGameDsl fixture for the positive cases (2 gameplay zones + 1 title + 1 nextLevel + 1 hidden 2nd-gameplay-zone-bind scene).
- **CREATED** 6 evidence artifacts under `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/`:
  - `title_enter.c` (extracted title_enter body — locks zone-load prelude + fill_bkg_rect)
  - `title_frame.c` (extracted title_frame body — locks navigate_to_scene call)
  - `noTilemap_main_head.c` (gate-off main.c head, first 8000 chars)
  - `main_levelSwitch.c` (extracted main() body — locks the guard substring contract)
  - `setup_current_level.c` (extracted helper body — locks the switch dispatch + per-zone metadata)
  - `noTilemap_levelSwitch_main_head.c` (gate-off main.c head from the negative test)
- **MODIFIED** `gbkt-backend-gbdk/build.gradle.kts` — added `testImplementation(project(":gbkt-genre-platformer"))` (test-only reverse dep mirroring the existing :gbkt-genre-sport pattern at line 48).

## Verification

- `./gradlew :gbkt-backend-gbdk:test --tests "TitleSceneEmissionTest"` → 3 tests, 0 failures (BUILD SUCCESSFUL).
- `./gradlew :gbkt-backend-gbdk:test --tests "LevelSwitchEmissionTest"` → 3 tests, 0 failures (BUILD SUCCESSFUL).
- `./gradlew :gbkt-backend-gbdk:test` → 1005 tests, 0 failures, 0 errors (no regressions; full suite GREEN).
- Both test files use `extractFunctionBody` brace-walk — neither relies on file-level grep for any per-function invariant. Compliance with CLAUDE.md §"Scope-level grep gates corollary" verified by inspection.
- Evidence artifacts on disk under `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/` — 6 files extracted from test runs, written by the evidence-before-assert pattern (BanksEmissionTest Plan 11-08).

## Decisions Made

(Mirrored in frontmatter `key-decisions` — repeated here for narrative continuity.)

- **Reshape of Task 1 Test 2** — plan literally instructed `setup_current_level` inside `gameplay_enter`, but Plan 12-17 explicitly deferred that wiring (SUMMARY §Deviation 5). Reshape locks the OTHER half of invariant #1: title_enter emits the tile-data load substrate. Plan Task 2 Test 2 (`setupCurrentLevel_function_emitted`) already locks the helper-presence assertion at the only correct scope (main.c, not gameplay_enter). See §Deviations below.
- **Brace-walk helper duplicated verbatim** — per plan instruction. Phase 13 may extract once a third pipeline-shape test lands (rule of three).
- **Two gameplay zones in LevelSwitchEmissionTest fixture** — locks the ≥2-case assertion which proves the menu-zone filter is permissive enough to keep gameplay zones.
- **Worktree-safe EVIDENCE_DIR via `user.dir` ascend-one-level** — chosen over absolute paths to honor #3099 worktree path safety.
- **testImplementation(:gbkt-genre-platformer) added to gbkt-backend-gbdk** — mirrors the existing :gbkt-genre-sport precedent (test-only reverse dep, Gradle handles the cycle).
- **Multiline regex anchors at column 0** — `^void main(void)`, `^void setup_current_level(void) NONBANKED`, `^void title_frame`, `^void title_enter` — prevent false-positive matches inside string literals, comments, or argument lists of unrelated functions.

## Deviations from Plan

### 1. [Rule 1 — Bug / Plan-Internal Contradiction with Predecessor Reality] Task 1 Test 2 reshape

- **Found during:** Task 1 design (initial read of plan vs. 12-17 SUMMARY).
- **Issue:** Plan Task 1 Test 2 instructed: "Read the file containing `gameplay_enter` (bank1.c). Assert `gameplay_enter` exists. Extract body; assert body contains `setup_current_level`." However, Plan 12-17 SUMMARY §Deviation 5 explicitly states: "Did NOT wire `setup_current_level()` into gameplayScene.enter (deferred to Plan 12-18)" — the helper is called from main()'s level-switch guard, NOT from gameplay_enter. The plan's literal assertion would have been RED against the actual 12-17 emission (the predecessor did not wire it).
- **Fix:** Reshaped Test 2 to lock the OTHER half of invariant #1 documented in plan <objective>: **"title scene's enter function emits fill_bkg_rect + tileset-guard pattern"**. The new test `titleEnter_emits_tile_data_load_substrate` brace-walks `void title_enter` and asserts the SceneVisitor zone-load prelude (`set_bkg_data` + `_bkg_tiles_load_banked` + `SHOW_BKG`) plus the user-authored `cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);")` verbatim text. The original "helper exists" assertion is preserved at the only correct scope by Task 2 Test 2 (`setupCurrentLevel_function_emitted`), which asserts `void setup_current_level(void) NONBANKED` at column 0 of main.c.
- **Files modified:** `TitleSceneEmissionTest.kt` (Test 2 implementation differs from plan literal text).
- **Verification:** Both reshaped Test 2 + the negative Test 3 pass GREEN; the assertion preserved by Task 2 covers the original "helper exists" intent at correct scope.
- **Rule classification:** Rule 1 (bug — plan asserts an emission the predecessor explicitly did not implement). Documented per executor protocol as a deviation rather than blocking on Rule 4 (architectural) because the plan + predecessor + objective are jointly self-resolving: the objective specifies title scene's tile-data load, the predecessor implemented main()-route setup_current_level, and the plan's literal Task 1 Test 2 instruction was authored against a hypothetical 12-17 implementation that did not land. The reshape honors the objective and the predecessor's actual emission.

### 2. [Rule 3 — Build Wiring] Added testImplementation(:gbkt-genre-platformer)

- **Found during:** Task 1 first compileTestKotlin (Unresolved reference 'platformerPhysics').
- **Issue:** gbkt-backend-gbdk's test classpath did not include gbkt-genre-platformer, but the test fixtures need `platformerPhysics { solidThreshold(17) }` to flip `gameUsesTilemapCollision` to true (Path A of the predicate at GBDKPipelineV2.kt:1975-2010).
- **Fix:** Added `testImplementation(project(":gbkt-genre-platformer"))` to gbkt-backend-gbdk/build.gradle.kts, mirroring the existing `testImplementation(project(":gbkt-genre-sport"))` precedent at line 48. Test-only reverse dep; Gradle handles the cycle because testImplementation does not propagate to main.
- **Files modified:** `gbkt-backend-gbdk/build.gradle.kts`.
- **Verification:** `./gradlew :gbkt-backend-gbdk:test` → BUILD SUCCESSFUL, no module-level cycle warnings.
- **Rule classification:** Rule 3 (blocking issue — Unresolved reference would have failed compileTestKotlin without this).

### 3. [Rule 3 — Import] Added explicit `buttons` import in test classes

- **Found during:** Task 1 first compileTestKotlin (Unresolved reference 'buttons' at 7 sites).
- **Issue:** `whenever(buttons.start.pressed) { navigate(...) }` references the top-level `object buttons` at `io.github.gbkt.core.dsl.buttons` (InputBuilders.kt:184). The test class did not import it; only `game`, `asset`, and `platformerPhysics` were imported initially.
- **Fix:** Added `import io.github.gbkt.core.dsl.buttons` to both TitleSceneEmissionTest.kt and LevelSwitchEmissionTest.kt.
- **Files modified:** Both new test files.
- **Verification:** Re-ran `./gradlew :gbkt-backend-gbdk:test --tests "TitleSceneEmissionTest"` → all 3 tests GREEN.
- **Rule classification:** Rule 3 (blocking issue — Unresolved reference at compile time).

## Authentication Gates

None — pure JVM-tier test addition. No I/O beyond test-fixture file writes to `evidence/tier1-shape/`. No network. No auth surface.

## Issues Encountered

- **Plan vs. predecessor contradiction at Task 1 Test 2** — resolved via Deviation #1 above (reshape to title_enter substrate, preserving the helper-presence assertion at Task 2 Test 2).
- **Missing testImplementation(:gbkt-genre-platformer)** — discovered at first compileTestKotlin; resolved via Deviation #2 above.
- **Missing `buttons` import** — discovered at compileTestKotlin; resolved via Deviation #3 above.

## User Setup Required

None — pure JVM-tier test addition. No GBDK toolchain needed; no emulator runs; no resource files touched.

## Known Stubs

None — both tests are positive-shape locks + negative-gate sentinels for already-shipped pipeline emission (Plan 12-17). No stubs introduced.

## Threat Flags

None — JVM-tier test addition only. No new network endpoints, no new auth paths, no new file-access patterns, no schema changes at trust boundaries. Test fixtures write evidence artifacts to a phase-local directory inside the active worktree (worktree-safe via `System.getProperty("user.dir")` ascend-one-level pattern).

## Next Phase Readiness

- **Wave 11 (Plan 12-09b) closed.** Anchor 1 + Anchor 5 codegen shape locks land at JVM tier with independent regression detection.
- **No blockers for downstream waves.** Plans 12-18 (first :buildRom checkpoint), 12-19 (anchor 1 MCP UAT), 12-22/12-23 (anchor 5 MCP UAT) consume the SHAPE that this plan locks; the JVM tier no longer carries the regression-detection burden alone.
- **Recommended Phase 13 follow-up:** Extract `extractFunctionBody` to a shared test util in `gbkt-test` once a third pipeline-shape test lands (rule of three). Today's duplication across BanksEmissionTest / TilemapCollisionEmissionTest / TitleSceneEmissionTest / LevelSwitchEmissionTest is intentional per the plan-author's per-test-file pattern, but the shared util is the natural cleanup.

## Self-Check: PASSED

- File `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt` exists (verified post-Task-1 commit).
- File `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt` exists (verified post-Task-2 commit).
- File contains `private fun extractFunctionBody(` — verified in both files via grep.
- TitleSceneEmissionTest contains 3 `@Test` methods (titleFrame_emits_navigate_to_scene_call_on_Start_press, titleEnter_emits_tile_data_load_substrate_when_title_scene_binds_a_tileset_zone, noTilemap_omits_setupCurrentLevel).
- LevelSwitchEmissionTest contains 3 `@Test` methods (main_emits_levelSwitch_guard_when_tilemap_collision_+_nextLevel_scene_present, setupCurrentLevel_helper_function_definition_is_emitted_at_column_0_of_main_c, noTilemap_omits_levelSwitch_guard).
- Commit `8f1fda77` exists in `git log --oneline -3` (Task 1).
- Commit `95218778` exists in `git log --oneline -3` (Task 2).
- `./gradlew :gbkt-backend-gbdk:test --tests "TitleSceneEmissionTest"` → 3 tests, 0 failures, BUILD SUCCESSFUL (verified).
- `./gradlew :gbkt-backend-gbdk:test --tests "LevelSwitchEmissionTest"` → 3 tests, 0 failures, BUILD SUCCESSFUL (verified).
- `./gradlew :gbkt-backend-gbdk:test` → 1005 tests, 0 failures, 0 errors (verified via sum of `TEST-*.xml` testsuite attrs).
- Evidence artifacts under `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/` — 6 new files (title_enter.c, title_frame.c, noTilemap_main_head.c, main_levelSwitch.c, setup_current_level.c, noTilemap_levelSwitch_main_head.c).

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Plan: 09b*
*Completed: 2026-05-22*
