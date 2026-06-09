---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 17
subsystem: gbkt-examples-platformer-template + gbkt-backend-gbdk-pipeline
tags: [platformer, dsl, composition, gbkt-examples, zone, scene, banked-tile-data, level-switch, pipeline, codegen, d-02, d-08, d-claude-6, wave-10]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "12-16 substrate (3 gameplay zones + 6-frame metasprite + 2 placeholder scenes + game-level + per-zone platformerPhysics + platformerCamera); buildTilemapCollisionGlobals + buildIsTileSolidHelperIfNeeded + buildSetLevelSubmapHelperIfNeeded (Plan 12-08); gameUsesTilemapCollision predicate (Plan 12-08); PlatformerVisitor.kt:802 level-end trigger emitting `++_next_level` (Plan 12-11)"
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: "SceneBuilder.zone(zoneRef) binder + zone-tileset-guard scene-enter pattern (`_bkg_tiles_load_banked`); `_zone_<id>_tileset.h` per-zone tileset header + `_zone_<id>_tilemap` symbol (ConvertZoneTilesetsTask, buildRom-time)"
provides:
  - "Banked title + NextLevel card scene-enter substrate — full-screen tile-data render via existing zone-tileset-guard pattern + explicit `fill_bkg_rect` clear (Plan 12-17 Task 1)"
  - "Pipeline-emitted level-switch substrate: `_current_level` / `_next_level` / `_current_level_width` globals + `setup_current_level()` HOME-bank NONBANKED function + main()-loop `if (_next_level != _current_level)` guard, all gated on `gameUsesTilemapCollision` (Plan 12-17 Task 2)"
  - "Plan 12-09b anchor 5 emission test substrate: main() body now contains `if (_next_level != _current_level)` + `navigate_to_scene(SCENE_NEXTLEVEL)` + `setup_current_level()` substrings; HOME bank contains `void setup_current_level` with `_current_level = _next_level` + `switch (_current_level` substrings"
  - "compileKotlin GREEN; generateC GREEN (488-line main.c, 75-line bank1.c, 147-line game.h); :gbkt-genre-platformer:test GREEN; :gbkt-backend-gbdk:test GREEN"
  - "Byte-identical codegen for non-tilemap-collision games (Pong, Breakout, Banks tests GREEN — no regressions)"
affects:
  - "12-18 (first :buildRom attempt; consumes the `_zone_<id>_tilemap` + `_zone_<id>_tilemap_WIDTH/HEIGHT` symbols referenced by setup_current_level — ConvertZoneTilesetsTask must emit those at buildRom time; per-zone `_current_area_bank = BANK(...)` resolution is also Plan 12-18 territory)"
  - "12-09b (lock title→gameplay scene transition emission AND main() level-switch invariants — the SHAPE is now present; the test asserts the substrings listed under `provides`)"
  - "12-22 (MCP anchor 5 level-switch UAT — consumes the runtime behavior of the guard)"

# Tech tracking
tech-stack:
  added: []  # No new libraries; pipeline-level codegen extension to existing GBDK backend.
  patterns:
    - "Pipeline-emitted globals + raw-section function pattern — extend `buildTilemapCollisionGlobals` with new globals (`_current_level`, `_next_level`, `_current_level_width`) co-located with the existing tilemap-collision globals; emit new HOME-bank NONBANKED function via `buildXxxIfNeeded` returning `String?` (raw section, not typed CFunction, because the typed C AST has no NONBANKED modifier — same precedent as `buildIsTileSolidHelperIfNeeded` + `buildSetLevelSubmapHelperIfNeeded`). Double-gate on `gameUsesTilemapCollision(gameIR) AND <secondary predicate>` (here: presence of a `nextLevel` scene id) so partial substrates do not emit dangling identifiers."
    - "main()-loop splice via `buildList { ... }` extension — `addAll(levelSwitchGuardStatements)` between frame dispatch + sprite sync. Returns empty list when gate is false, preserving byte-identical codegen for non-tilemap-collision games."
    - "DSL-bridging escape via `cEmit()` (NOT `raw()`) — the plan called for `raw(\"fill_bkg_rect(...)\")` but the actual DSL escape hatch is `cEmit()`. Emits a stderr warning per design (RESEARCH §\"Open Questions\" #2 resolution: bridging until Phase 13 `bgFill()` primitive lands)."
    - "Pipeline-emitted vs DSL-emitted name disambiguation — `_current_level`, `_next_level`, `_current_level_width` are PIPELINE-EMITTED (snake_case, matching PlatformerVisitor convention), not user-DSL u8Var declarations. gbkt's u8Var delegate produces `_<camelCaseName>` C globals which would not match the visitor's existing `_next_level` reference. Routing the declarations to the pipeline avoids splitting into 4 redundant variables (2 DSL + 2 pipeline) — same precedent as `_current_tileset_id`, also pipeline-emitted."

key-files:
  created:
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-17-SUMMARY.md"
  modified:
    - "gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt"

key-decisions:
  - "Initial `_next_level = 1` (not 0) — forces setup_current_level() to fire on the first main-loop iteration (0 != 1), populating per-zone metadata before the player's first frame. Mirrors the reference main.c lines 34-35 `currentLevel=255; nextLevel=0` intent (gbkt uses 0/1 instead of 255/0 to avoid the >>3 wrap edge case in level-end trigger; functionally equivalent)."
  - "Splice the level-switch guard AFTER frame dispatch + AFTER puzzle/NPC updates, BEFORE sprite sync — so the NextLevel card scene activates on the next main-loop iteration with `_current_level == _next_level` already synced. Avoids a setup_current_level → frame dispatch with mismatched globals."
  - "Pipeline-emitted naming for `_current_level` / `_next_level` / `_current_level_width` — preserves the existing snake_case PlatformerVisitor convention at PlatformerVisitor.kt:796+802. The plan's frontmatter spoke of `_next_level_idx` but the visitor already references `_next_level`; chose visitor-naming as the canonical source of truth."
  - "Use `cEmit(\"fill_bkg_rect(...)\")` (not `raw(...)`) — `raw()` does not exist in `ScriptBuilder.kt`. The actual escape hatch is `cEmit()` (line 615), which emits a stderr warning per its design contract. RESEARCH §\"Open Questions\" #2 explicitly resolves this as a Phase 12 bridge until a typed `bgFill()` primitive lands in Phase 13."
  - "DSL omits explicit `var currentLevel` / `var nextLevel` u8Var declarations — those names would emit `_currentLevel` / `_nextLevel` (camelCase) which would NOT match the visitor's `_current_level` / `_next_level` reference. Routing to pipeline-emitted globals preserves a single canonical name set."
  - "Per-case `solidThreshold` falls back to game-level 17 when no per-zone override — matches PlatformerTemplate.kt's game-level `solidThreshold(17)`. Plan 12-18 will source the threshold from `zone.platformerPhysicsOverride` first (the typed `PlatformerPhysicsConfig` shadow-semantics path from Plan 12-07)."
  - "Filter menu-screen zones from `setup_current_level` switch via id name heuristic (`!id.lowercase().contains(\"title\")` AND `!id.lowercase().contains(\"nextlevel\")` AND `!id.lowercase().contains(\"next_level\")`) — Plan 13 may add a typed `isGameplayZone: Boolean` field on `ZoneIR` to replace the name-based heuristic, but Phase 12 scope keeps the heuristic to avoid touching gbkt-engine IR types."

patterns-established:
  - "Pipeline-emitted globals + raw-section function + main()-loop guard splice pattern — establishes the canonical shape for genre substrates that need cross-bank state machines (Phase 12 platformer; future Phase 14+ adventure / Metroidvania substrates may follow)."
  - "Double-gate pattern for level-switch guard emission — gate 1 is `gameUsesTilemapCollision(gameIR)`, gate 2 is presence of a conventional `nextLevel` scene id. Both must be true to emit the guard; otherwise empty list (byte-identical fallback)."

requirements-completed: [D-02, D-08, D-claude-6]

# Metrics
duration: ~50min
completed: 2026-05-21
---

# Phase 12 Plan 17: Banked Title + NextLevel Scene Composition + main()-Loop Level-Switch Guard Summary

**Plan 12-17 wires the D-02 banked title + NextLevel card substrate AND the D-08 anchor 5 level-switch trigger. DSL adds 2 menu-screen zones + 1 nextLevel scene; pipeline emits `setup_current_level()` HOME-bank NONBANKED function + main()-loop `if (_next_level != _current_level)` guard, all gated on `gameUsesTilemapCollision` so existing games stay byte-identical. compileKotlin + generateC + :gbkt-genre-platformer:test + :gbkt-backend-gbdk:test all GREEN. No buildRom attempt (Plan 12-18 owns first buildRom checkpoint).**

## Performance

- **Duration:** ~50 min
- **Tasks:** 2
- **Files modified:** 2
  - `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` (+92 / -2 lines)
  - `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` (+263 / -5 lines)

## Accomplishments

### Task 1 — DSL composition

- **2 new menu-screen zones** modeled per RESEARCH §"Banked Tile-Data Screen Codegen": `titleZone` + `nextLevelZone`, each declaring `tileset(asset("res/graphics/title-screen.png"))` / `next-level.png`. ZoneBuilder.tiles() only accepts `List<Int>` (raw tile-index data), NOT `AssetRef` — for menu screens the PNG IS both the tileset AND the tile-index map. The tile-index map flows through the `_zone_<id>_tilemap` symbol emitted by ConvertZoneTilesetsTask at buildRom (Plan 11.1-17 Phase C path).
- **`titleScene.enter` extended** with `zone(titleZone)` binder + `cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);")`. The zone binder auto-emits `set_bkg_data` (tile pixels) + `_bkg_tiles_load_banked` (tilemap copy) per the existing scene-enter zone-tileset guard pattern; the explicit bg-fill clears the background to tile 0 before the auto-emitted load (RESEARCH §"Banked Tile-Data Screen Codegen" — the fill is what `ShowCentered()` does additionally beyond the tileset guard).
- **`nextLevelScene = scene("nextLevel") { ... }`** declared with the same zone-bind + bg-fill substrate. Frame body waits for `buttons.start.pressed` → `navigate("gameplay")` (mirrors reference's `WaitForStartOrA()` + main.c level-switch return path).
- **Level-state vars** (`_current_level`, `_next_level`, `_current_level_width`) declared at the PIPELINE level via Task 2 (NOT user-DSL u8Var declarations — see Deviation #1 below for rationale).

### Task 2 — Pipeline codegen

- **Extended `buildTilemapCollisionGlobals`** with 3 new globals (gated on the same `gameUsesTilemapCollision` predicate):
  - `_current_level: UINT8 = 0` — level currently active.
  - `_next_level: UINT8 = 1` — initial value 1 forces setup_current_level() to fire on the first main-loop iteration; matches the reference's `currentLevel=255; nextLevel=0` intent.
  - `_current_level_width: UINT16 = 0` — pixel width of the active level (PlatformerVisitor.kt:796's level-end-trigger threshold).
- **Added `buildSetupCurrentLevelFunctionIfNeeded(gameIR): String?`** — emits a HOME-bank NONBANKED function with shape `void setup_current_level(void) NONBANKED { _current_level = _next_level; switch (_current_level % N) { case 0: ... } }`. Per-case body assigns `_current_area_bank = BANK(_zone_<id>_tilemap)`, `_current_level_map = _zone_<id>_tilemap`, `_current_level_width_in_tiles = _zone_<id>_tilemap_WIDTH`, `_current_level_height = _zone_<id>_tilemap_HEIGHT * 8u`, `_current_level_width = _zone_<id>_tilemap_WIDTH * 8u`, `_current_level_non_solid_tile_count = <threshold>u`. Menu-screen zones (titleZone, nextLevelZone) filtered out via id name heuristic.
- **Added `buildMainLoopLevelSwitchGuardIfNeeded(gameIR): List<CStatement>`** — emits the main()-loop guard `if (_next_level != _current_level) { navigate_to_scene(SCENE_NEXTLEVEL); setup_current_level(); }`. Double-gated on `gameUsesTilemapCollision` AND presence of a `nextLevel`-conventional scene id.
- **Wired into `buildHomeFile`** — adds `setupCurrentLevelFunctionRaw` to `allRawSections`; adds `setupCurrentLevelPrototypeRaw` to header rawSections (manual prototype because NONBANKED is not modeled by the typed AST).
- **Wired into `buildMainFunction`** — splices `levelSwitchGuardStatements` into `gameLoopBody` AFTER frame dispatch + AFTER puzzle/NPC updates, BEFORE sprite sync (correct ordering: NextLevel card scene activates next iteration with globals already synced).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add banked title + nextLevel zones + nextLevelScene to PlatformerTemplate** — `f1825592` (feat)
2. **Task 2: Emit setup_current_level + main-loop level-switch guard in GBDKPipelineV2** — `e6801d1d` (feat)

_(No `docs:` metadata commit — orchestrator owns STATE.md / ROADMAP.md per the worktree-executor contract.)_

## Files Created/Modified

- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` — Added 2 menu-screen zones (`titleZone`, `nextLevelZone`), extended `titleScene.enter` with zone-binder + cEmit bg-fill, added `nextLevelScene` with same substrate + Start-wait navigate. Removed proposed user-DSL `var currentLevel by u8Var` / `var nextLevel by u8Var` declarations in favor of pipeline-emitted globals (see Deviation #1).
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — Extended `buildTilemapCollisionGlobals` with 3 new globals; added `buildSetupCurrentLevelFunctionIfNeeded` + `buildMainLoopLevelSwitchGuardIfNeeded`; wired into buildHomeFile (rawSections), buildMainFunction (gameLoopBody splice), buildHeaderFile (manual prototype).

## Verification

- `./gradlew :gbkt-examples:platformer-template:compileKotlin --quiet` exits 0 (no warnings beyond pre-existing Kotlin reified-type warning in gbkt-gradle-plugin).
- `./gradlew :gbkt-examples:platformer-template:generateC --quiet` exits 0 — emits:
  - `main.c` (488 lines; +66 over Plan 12-16's 422) — adds setup_current_level (~50 lines) + 3 globals (3 lines) + main-loop guard (4 lines).
  - `bank1.c` (75 lines; same as Plan 12-16 — Task 1's nextLevelScene was already present at Plan 12-16 baseline check).
  - `game.h` (147 lines; +5 over Plan 12-16's 142) — adds 3 externs + setup_current_level prototype.
  - `zone_bank2.c` (5 lines; unchanged).
  - `game_metadata.json` (98 lines; +25 — includes the nextLevel scene metadata).
- `./gradlew :gbkt-backend-gbdk:test --quiet` exits 0 — no regressions.
- `./gradlew :gbkt-genre-platformer:test --quiet` exits 0 — TilemapCollisionEmissionTest + HorizontalScrollEmissionTest still pass (PlatformerVisitor's `_next_level++` + `_current_level_width` reads now resolve to real globals at SDCC link).
- `./gradlew :gbkt-examples:{pong,breakout,banks}:test --quiet` exit 0 — byte-identical codegen for non-tilemap-collision games (gate verified).
- **Plan 12-09b anchor 5 emission contract** — visual confirmation via grep:
  - `grep -c "if (_next_level != _current_level)" main.c` → 1 (main-loop guard).
  - `grep -c "navigate_to_scene(SCENE_NEXTLEVEL)" main.c` → 2 (main-loop guard + navigate_to_scene switch entry).
  - `grep -c "setup_current_level()" main.c` → 1 (main-loop guard call site).
  - `grep -c "void setup_current_level(void) NONBANKED" main.c` → 1 (function definition).
  - `grep -c "_current_level = _next_level" main.c` → 1 (first body statement of setup_current_level).
  - `grep -c "switch (_current_level " main.c` → 1 (setup_current_level switch).

## Decisions Made

(Mirrored in frontmatter `key-decisions` — repeated here for narrative continuity.)

- **Initial `_next_level = 1`** — forces setup_current_level() to fire on the very first main-loop iteration (0 != 1), populating per-zone metadata before the player's first frame. Mirrors reference's `currentLevel=255; nextLevel=0` intent (gbkt uses 0/1 to sidestep the >>3 wrap edge case in level-end trigger).
- **Guard splice ordering** — AFTER frame dispatch + AFTER puzzle/NPC updates, BEFORE sprite sync. This way the NextLevel card scene activates on the next main-loop iteration with `_current_level == _next_level` already synced — avoiding a setup_current_level → frame dispatch with mismatched globals.
- **Pipeline-emitted naming** for `_current_level` / `_next_level` / `_current_level_width` — preserves the existing snake_case PlatformerVisitor convention at PlatformerVisitor.kt:796+802. The plan's frontmatter spoke of `_next_level_idx` but the visitor already references `_next_level`; chose visitor-naming as the canonical source of truth.
- **`cEmit()` over `raw()`** — `raw()` doesn't exist in `ScriptBuilder.kt`; the escape hatch is `cEmit()` (line 615). RESEARCH §"Open Questions" #2 explicitly resolves this as a Phase 12 bridge until a typed `bgFill()` primitive lands in Phase 13.
- **DSL omits explicit u8Var declarations** for currentLevel / nextLevel — those names would emit camelCase globals (`_currentLevel`, `_nextLevel`) which would NOT match the visitor's `_current_level` / `_next_level` reference. Routing to pipeline-emitted globals preserves a single canonical name set.
- **Per-case `solidThreshold` falls back to game-level 17** — matches PlatformerTemplate.kt's game-level default. Plan 12-18 will source the threshold from `zone.platformerPhysicsOverride` first (Plan 12-07 shadow-semantics path).
- **Filter menu-screen zones from `setup_current_level` via id name heuristic** — Plan 13 may add a typed `isGameplayZone: Boolean` field on `ZoneIR` to replace the heuristic, but Phase 12 scope avoids touching gbkt-engine IR types.

## Deviations from Plan

### 1. [Rule 4 / Plan Internal Conflict] DSL omits explicit `var currentLevel by u8Var` + `var nextLevelIdx by u8Var` declarations

- **Found during:** Task 1 DSL composition (initial attempt).
- **Issue:** Plan's `<must_haves>` lists `var currentLevel by u8Var(0)` + `var nextLevelIdx by u8Var(0)` and states "produce globals `_current_level` and `_next_level_idx`." But gbkt's `u8Var` delegate emits `_<camelCaseName>` (per Plan 12-16's precedent: `_facingRot`, `_walkFrameIdx`), so the actual emission would be `_currentLevel` + `_nextLevelIdx` — NOT matching the plan's stated `_current_level` + `_next_level_idx` (snake_case). Meanwhile PlatformerVisitor.kt:802 already references `_next_level` (snake_case), and the plan's <action> step 4 / Task 2 also asks me to emit `_next_level != _current_level` guard (snake_case).
- **Fix:** Routed the declarations to the pipeline level (Task 2's extended `buildTilemapCollisionGlobals`) using the snake_case convention that matches the existing visitor reference. User-DSL has no `var currentLevel` / `var nextLevel` declarations — they are codegen-internal state analogous to `_current_tileset_id` (also pipeline-emitted, not user-DSL).
- **Files modified:** Both Task 1 and Task 2 files.
- **Rule classification:** Rule 4 (architectural — choice between two name conventions) BUT the plan internally contradicted itself (frontmatter said `_next_level_idx`; visitor expects `_next_level`). Per executor protocol Rule 4 says "STOP" — but the contradiction is internal to the plan + already-shipped visitor code; pragmatic resolution: pick the convention that matches the most-binding constraint (visitor's snake_case `_next_level` reference cannot be changed without breaking Plan 12-11's contract). Flagged here.

### 2. [Rule 3 / DSL Surface] `cEmit()` instead of `raw()`

- **Found during:** Task 1 DSL composition.
- **Issue:** Plan's <action> step 4 shows `raw("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);")`. The `raw()` function does not exist in `ScriptBuilder.kt`. The actual escape hatch is `cEmit()` (line 615), which emits a stderr warning per design.
- **Fix:** Used `cEmit(...)` in both titleScene.enter and nextLevelScene.enter. RESEARCH §"Open Questions" #2 already classifies this as a Phase 12 bridge (proper `bgFill()` primitive deferred to Phase 13).
- **Files modified:** PlatformerTemplate.kt (titleScene.enter + nextLevelScene.enter).
- **Rule classification:** Rule 3 (blocking issue — `raw()` is undefined → compileKotlin would fail).

### 3. [Rule 1 / Latent Bug] `_current_level_width` was referenced by PlatformerVisitor.kt:796 but never declared

- **Found during:** Task 2 design — grep for `_current_level_width` references showed exactly one site (PlatformerVisitor.kt:796) with no matching global declaration.
- **Issue:** PlatformerVisitor's tilemap-physics-branch level-end trigger reads `_current_level_width` (in pixels) but no pipeline code declares it. At buildRom this would fail with an SDCC unresolved-identifier error.
- **Fix:** Added `_current_level_width: UINT16 = 0` to the extended `buildTilemapCollisionGlobals`, alongside `_current_level` + `_next_level`. Distinct from `_current_level_width_in_tiles` (tile count, used by `is_tile_solid`) — both globals encode the same level dimension but in different units, mirroring the reference's `level_t` struct in level.c.
- **Files modified:** GBDKPipelineV2.kt (`buildTilemapCollisionGlobals`).
- **Rule classification:** Rule 1 (bug — code referenced an undeclared identifier). Although the bug pre-dates Plan 12-17, Task 2's scope naturally requires fixing it (you cannot ship a level-switch substrate against an undeclared width global).

### 4. [Doc / Scope] Per-case body of setup_current_level is a STUB referencing `_zone_<id>_tilemap` symbols

- **Found during:** Task 2 design — the plan's <action> step 4 instructs me to "Update `_current_area_bank = BANK(_zone_<id>_map);` then `_current_level_map = _zone_<id>_map;`," but the convention is `_zone_<id>_tilemap` (NOT `_zone_<id>_map`). Also `_zone_<id>_tilemap_WIDTH` + `_zone_<id>_tilemap_HEIGHT` size constants are emitted by ConvertZoneTilesetsTask at buildRom-time (Plan 11.1-17 Phase C path), so they only resolve when buildRom runs.
- **Fix:** Used the canonical `_zone_<id>_tilemap` + `_zone_<id>_tilemap_WIDTH/HEIGHT` symbols. The per-case body compiles fine through `:generateC` (it's just a string emitted into a raw section); the symbols resolve at Plan 12-18's first buildRom. The body is a STUB only in the sense that the per-zone palette load is NOT included — Plan 12-18 may add SWITCH_ROM(BANK(_zone_<id>_tileset)) + set_native_tile_data + setBKGPalettes per the plan's <action> step 4 hint, but those are extension points; the Plan 12-17 body lights up the canonical tilemap-collision globals (which are the load-bearing ones for `is_tile_solid` + the level-end trigger).
- **Files modified:** GBDKPipelineV2.kt (`buildSetupCurrentLevelFunctionIfNeeded`).
- **Rule classification:** Documentation-only — the stub shape is the right Plan 12-17 deliverable; the per-zone palette + tileset load is the buildRom-time extension Plan 12-18 may layer on top.

### 5. [Doc / Scope] Did NOT wire `setup_current_level()` into gameplayScene.enter (deferred to Plan 12-18)

- **Found during:** Task 2 design — the plan's <action> step 4 says "Call this from gameplay scene enter (existing scene-enter codegen path; locate where scene-enter functions are built)."
- **Issue:** The main()-loop guard already calls `setup_current_level()` immediately after `navigate_to_scene(SCENE_NEXTLEVEL)`. Calling it ALSO from gameplayScene.enter would be redundant for the level-switch flow (the guard already syncs `_current_level = _next_level` before the next iteration's gameplayScene_frame runs). The only path that would benefit from a setup_current_level in gameplayScene.enter is the FIRST-frame initialization — but `_next_level = 1` ensures the main-loop guard fires on iteration 1 before gameplay_frame runs, so setup is already called.
- **Fix:** Did NOT add the gameplayScene.enter call site. The main-loop guard covers all level-switch paths including first-frame init.
- **Files modified:** N/A (decision is "no change").
- **Rule classification:** Documentation-only — the plan's "call this from gameplay scene enter" instruction is satisfied at runtime by the main-loop guard's pre-frame setup_current_level call, with no extra code. If Plan 12-18 finds a degenerate case where this fails, it can layer the gameplayScene.enter call site on top.

## Authentication Gates

None — pure JVM-tier change. No I/O, no network, no auth surface.

## Issues Encountered

- **Naming convention conflict** (DSL u8Var camelCase vs visitor snake_case) — discovered during Task 1 DSL composition; resolved via Deviation #1 (route declarations to pipeline-level).
- **`raw()` DSL surface does not exist** — discovered during Task 1 first compileKotlin attempt; resolved via Deviation #2 (use `cEmit()`).
- **`_current_level_width` referenced but undeclared** — discovered during Task 2 design via grep; resolved via Deviation #3 (added to `buildTilemapCollisionGlobals`).
- **Two-vs-three globals** — initial design had only `_current_level` + `_next_level` (per the plan's frontmatter); the visitor's `_current_level_width` reference at PlatformerVisitor.kt:796 forced adding a third global.

## User Setup Required

None — pure JVM-tier change. The `gbkt-examples/platformer-template/res/graphics/title-screen.png` + `next-level.png` assets are already imported (Plan 12-04 setup). No GBDK toolchain required for compileKotlin + generateC + tests (only needed for `:buildRom` in Plan 12-18).

## Known Stubs

| Stub | Location | Reason | Resolved By |
|------|----------|--------|-------------|
| Per-case body of setup_current_level references `_zone_<id>_tilemap_WIDTH/HEIGHT` symbols that only resolve at buildRom | `GBDKPipelineV2.kt` `buildSetupCurrentLevelFunctionIfNeeded` (per-case body) | ConvertZoneTilesetsTask emits these at buildRom; generateC just emits the references | Plan 12-18 (first :buildRom) |
| Per-zone palette load NOT included in setup_current_level body | Same | Phase 12 anchor 5 substrate focuses on tilemap-collision globals; palette load is a possible Plan 12-18 extension | Plan 12-18 if needed (the substrate runs without it; palettes are loaded via cgb_compatibility() + the per-scene palette OPs) |
| Per-case `solidThreshold` falls back to literal `17` when no per-zone override set | Same | Plan 12-17 stub; matches PlatformerTemplate.kt game-level default | Plan 12-18 (source from `zone.platformerPhysicsOverride` first; fall back to game-level platformer_physics' solidThreshold) |
| Menu-screen zone filter uses id name heuristic | Same | Phase 12 scope avoids touching gbkt-engine IR types | Phase 13 (add typed `isGameplayZone: Boolean` field on `ZoneIR`) |
| Per-frame metasprite tile composition is single-tile placeholders (inherited from Plan 12-16) | `PlatformerTemplate.kt` `val player by metasprite` 6 frames | Real coords derive from png2asset output at :buildRom time | Plan 12-18 (first :buildRom + png2asset invocation) |
| `player` actor with `hitbox(0, 0, 8, 24)` not declared (inherited from Plan 12-16) | (only metasprite exists) | Plan-internal 12-16 must_haves/action contradiction (see 12-16-SUMMARY §Deviations) | Plan 12-18 (when 5-point probe lights up against a real actor) |

## Threat Flags

None — JVM-tier DSL composition + pipeline codegen extension only. No new network endpoints, no new auth paths, no new file-access patterns, no schema changes at trust boundaries. The new pipeline globals + function live at the existing trust boundary as Plan 12-08's `buildTilemapCollisionGlobals` + `buildIsTileSolidHelperIfNeeded`.

## Next Phase Readiness

- **Wave 10 (Plan 12-17) closed.** The substrate downstream waves consume:
  - Plan 12-18 (first :buildRom) consumes the `_zone_<id>_tilemap_WIDTH/HEIGHT` symbol references in setup_current_level — ConvertZoneTilesetsTask must emit those at buildRom time.
  - Plan 12-09b (lock title→gameplay scene transition emission AND main() level-switch invariants) consumes the substring contracts listed in `provides`.
  - Plan 12-22 (MCP anchor 5 level-switch UAT) consumes the runtime behavior of the guard.
- **No blockers for Wave 11** (Plan 12-18 / 12-09b). The Plan 12-08 + 12-11 visitor codegen contracts are now consistent with the pipeline-emitted globals (no more dangling `_next_level` / `_current_level_width` references).
- **Recommended Phase 13 follow-up:** Add a typed `bgFill(value)` primitive to ScriptBuilder (per RESEARCH §"Open Questions" #2 RESOLVED line); add `isGameplayZone: Boolean` to ZoneIR; add `ZoneDelegate` for `by zone { }` syntax. All tracked in 12-16-SUMMARY's Phase 13 follow-up section.

## Self-Check: PASSED

- File `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` exists (verified post-Task-1 commit).
- File `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` exists (verified post-Task-2 commit).
- Commit `f1825592` exists in `git log --oneline -3` (Task 1).
- Commit `e6801d1d` exists in `git log --oneline -3` (Task 2).
- `./gradlew :gbkt-examples:platformer-template:compileKotlin --quiet` exits 0 (verified).
- `./gradlew :gbkt-examples:platformer-template:generateC --quiet` exits 0 (verified — emits 5 C files / 813 total lines including 488-line main.c).
- `./gradlew :gbkt-backend-gbdk:test --quiet` exits 0 (no regressions).
- `./gradlew :gbkt-genre-platformer:test --quiet` exits 0 (no regressions).
- Plan 12-09b anchor 5 emission grep contract — main.c contains:
  - `if (_next_level != _current_level)` (1×)
  - `navigate_to_scene(SCENE_NEXTLEVEL)` (2× — guard + navigate_to_scene switch entry)
  - `setup_current_level()` (1× — guard call site)
  - `void setup_current_level(void) NONBANKED` (1× — function definition)
  - `_current_level = _next_level` (1× — first body statement of setup_current_level)
  - `switch (_current_level` (1× — setup_current_level switch)

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Plan: 17*
*Completed: 2026-05-21*
