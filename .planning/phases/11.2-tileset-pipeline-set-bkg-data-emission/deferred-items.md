# Phase 11.2 — Deferred Items

Items discovered during plan execution that are out of scope for the current plan.

## Pre-existing test failures in `gbkt-genre-sport`

**Discovered during:** Plan 11.2-08 (INV-8 sentinel landing)
**Test file:** `gbkt-genre-sport/src/test/kotlin/io/github/gbkt/genre/sport/codegen/TrackSynthesizerCircuitShapeTest.kt`
**Failing tests:**
- `racer_waypoints_synthesize_to_corridor_not_arena()` (line 163)
- `racer_corridor_interior_is_non_drivable()` (line 194)

**Verification that failures pre-date Plan 08:** Tests were re-run with the INV-8 file temporarily moved out of the source tree (no code changes to any existing file). Both `TrackSynthesizerCircuitShapeTest` tests still failed — confirming the failures exist on the current HEAD before Plan 08 lands and are NOT caused by INV-8.

**Scope boundary:** Plan 08 is test-only (adds a single new test file `SportLegacyTilesetPathInvariantTest.kt`). It does not touch `TrackSynthesizer` or its tests. Per the executor scope rule, pre-existing failures in unrelated files are logged here rather than fixed inline.

**Suggested follow-up:** A future sport-genre maintenance phase or a dedicated `TrackSynthesizer` fix plan should triage these two failures. They appear to relate to the track-synthesis circuit-shape contract (corridor vs arena, drivable interior tiles) — not to the Phase 11.2 NEW tileset pipeline or the LEGACY sport-racing path.

## Pre-existing Spotless / ktfmt drift in `gbkt-gradle-plugin`

**Discovered during:** Plan 11.2-03 (ConvertZoneTilesetsTask landing)
**Files with violations on spawn base `0a4f59f1`:**
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTask.kt`
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTaskMetaspriteDefaultTest.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTaskMirrorDedupOptInTest.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/OutputDirSyncTest.kt`

**Verification that violations pre-date Plan 03:** `git stash`-equivalent test (reverting all Plan 03 work via `git checkout HEAD~3 -- <files>`) was avoided per worktree rules; instead `git status` after `spotlessApply` showed these six files were reformatted independently of the new `ConvertZoneTilesetsTask.kt` / `ConvertZoneTilesetsTaskTest.kt` files. None of the six files were touched by Plan 03's two commits.

**Scope boundary:** Plan 03 added two new files; both pass `spotlessKotlinCheck` cleanly. Per the executor SCOPE BOUNDARY rule (only fix issues directly caused by current task), the pre-existing drift is logged here rather than fixed inline. Fixing them inline would expand the Plan 03 diff by ~6 files of pure formatting churn unrelated to the zone-tileset pipeline.

**Suggested follow-up:** A `chore(gradle-plugin): spotlessApply across plugin module` commit on a quiet day, or in conjunction with the next change that legitimately touches each file.

## Pre-existing RPG character codegen extern/declaration mismatch

**Discovered during:** Plan 11.2-12 (4-game cross-genre regression sweep)
**Affected games:** `gbkt-examples/dungeon` (`_char_adventurer_*`), `gbkt-examples/explorer` (`_char_hero_*`)
**Failure pattern:**
```
gbkt-examples/<game>/build/gbkt/generated/main.c: error 91: extern definition for '_char_<name>_<stat>' mismatches with declaration.
gbkt-examples/<game>/build/gbkt/generated/game.h: error 177: previously defined here
```
Affected stats: hp, sp, atk, def, matk, mdef, agl (full RPG stat set, all 7).

**Verification that failures pre-date Phase 11.2:** Tests run at the pre-Phase-11.2 base commit `dfe52566` (the `plan(phase-11.2): land 12-plan breakdown` commit, parent of all 11.2 work). Both dungeon and explorer fail identically at that base.

**Scope boundary:** Phase 11.2 touches GBDKPipelineV2, SceneVisitor, SportVisitor (KDoc only), ConvertZoneTilesetsTask (new), GbktPlugin. None of these paths emit `_char_*` symbols. The mismatch is in the RPG character codegen path (likely `gbkt-genre-rpg` or character codegen in the gbdk backend).

**Why not fixed in 11.2:** Per user-memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` ("for system-wide codegen / type-system changes, stop driving inline recommendations; route to gsd-phase → gsd-spec-phase → gsd-discuss-phase → gsd-plan-phase WITH research"), this RPG character codegen regression is system-wide territory and MUST NOT be patched inline in 11.2. Per user-memory `feedback_many_small_plans_terminal_subphase.md`, 11.2 is a TERMINAL subphase — no 11.2.1 follow-up; the proper next step is a new sibling phase via `/gsd-phase` → `/gsd-discuss-phase` → `/gsd-plan-phase` with research.

**Suggested follow-up phase:** "Fix RPG character codegen extern/declaration mismatch in main.c vs game.h" — needs:
- Triage: when did the mismatch surface? (likely a recent change to either CharacterVisitor or main.c emission)
- Decision: which side is authoritative? (the declaration in game.h or the extern in main.c)
- Fix: align the two, add a JVM-tier sentinel test locking the alignment per-game.
- Cross-genre sweep verifying dungeon + explorer + (any other RPG-genre game) builds clean.

**Impact on Phase 11.2 ship decision:** Phase 11.2 ships GREEN. 2 of 4 cross-genre games (banks + racer) build clean. The 2 failing games' failures are documented as pre-existing technical debt. INV-7 + INV-8 sentinels PASS. The NEW-path tileset pipeline is verified end-to-end on banks (the canonical example).
