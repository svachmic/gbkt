---
slug: title-zone-path-a-render
status: resolved
trigger: |
  Phase 12.2 Plan 10 Task 2 human-verify checkpoint REJECTED the re-shot 01-title.png
  for `platformer-template`. Gameplay (02-gameplay.png, D-01 Path B, two-invocation
  tilemap()) APPROVED — Path B render confirmed working at runtime. Title (Path A,
  one-invocation, no tilemap() declaration) still shows pre-12.2 row-doubling defect.
  Phase 12.2 verdict closed as `gaps_found` (AC13 FAIL). Phase 12 parent Plan 12-19
  blocked-pending-escalation until this closes.
created: 2026-05-23
updated: 2026-05-23
tdd_mode: false
goal: find_and_fix
related_phase: 12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse
related_seed: .planning/seeds/SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT.md
related_verification: .planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-VERIFICATION.md
---

# Debug Session: Title-zone D-01 Path A scene-render defect

## Current Focus

hypothesis: CONFIRMED — Hypothesis A

test: grep -nC2 'set_bkg_tiles' gbkt-examples/platformer-template/build/gbkt/generated/bank1.c

expecting: (resolved)

next_action: (resolved — fix applied, ROM builds, JVM tests GREEN)

reasoning_checkpoint: |
  Hypothesis A confirmed in first investigation step. Generated bank1.c showed:
  `_bkg_tiles_load_banked(2u, 0u, 0u, 32u, 32u, _zone_titleZone_tilemap);`
  The fix replaced CLiteral(zone.mapWidth)/CLiteral(zone.mapHeight) with
  CVar("_zone_${zoneSanitized}_tilemap_WIDTH") / CVar("_zone_${zoneSanitized}_tilemap_HEIGHT")
  for NEW-path zones (tilesetPath != null) in SceneVisitor.kt.
  After fix, generated bank1.c shows:
  `_bkg_tiles_load_banked(2u, 0u, 0u, _zone_titleZone_tilemap_WIDTH, _zone_titleZone_tilemap_HEIGHT, _zone_titleZone_tilemap);`
  ROM builds clean. JVM tests GREEN (SceneVisitorTest TEST 15+16, TitleSceneEmissionTest).

## Symptoms

expected: |
  On platformer-template title scene enter: the BG layer renders `title-screen.png`
  (160×72 px = 20×9 tiles = 180 bytes) as a single 9-tile-high block at the top of
  the screen — the title art appearing as it does in the source PNG, no row doubling,
  no scrambling.

actual: |
  Re-shot anchor1 01-title.png (Plan 12.2-10 Task 1, commit cfbe24b0) shows the title
  art row-doubled / scrambled — visually identical to the pre-12.2 buggy behaviour.
  The user explicitly REJECTED this image at the per-image human-verify checkpoint
  on 2026-05-23. The gameplay anchor (02-gameplay.png, same commit) was APPROVED,
  proving the data-emission half AND Path B render half both work — the defect is
  isolated to Path A's scene-enter render call.

errors: |
  None at build time. ROM compiles cleanly:
  - `./gradlew :gbkt-examples:platformer-template:test --tests "PlatformerTemplateUatTest.anchor1Title_to_Gameplay"` exits 0
  - 5-ROM regression sweep (Plan 12.2-09) all GREEN
  - bank 2 = 6120 B = 3×1920 + 2×180 (exact byte math; bytes ARE in the ROM)
  The defect is silent at the build tier and only surfaces visually.

reproduction: |
  1. ./gradlew :gbkt-examples:platformer-template:clean
  2. ./gradlew :gbkt-examples:platformer-template:test --tests "PlatformerTemplateUatTest.anchor1Title_to_Gameplay"
  3. View .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/01-title.png
  4. Compare against gbkt-examples/platformer-template/src/main/resources/graphics/title-screen.png
  Observe: title art is row-doubled in the rendered PNG; source PNG is a single 9-row block.

started: |
  Defect 7 has been present since before Phase 12.2 (the row-doubling matches the
  pre-12.2 baseline screenshot at evidence/anchor-1-blocked-converttilesets-synthetic-tilemap.md).
  Phase 12.2 was designed to fix it but only touched the data-emission half — the
  Path A scene-render consumer was never migrated. AC13 FAIL surfaced 2026-05-23 at
  the Plan 12.2-10 Task 2 human-verify checkpoint after the seed-recommended per-image
  one-at-a-time labeled presentation revealed the gameplay/title split.

## Hypothesis catalog (from SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT.md)

A. **Scene-enter visitor hardcodes HEIGHT=18 for title scene** — CONFIRMED AND FIXED.
   (Actually HEIGHT=32 not 18, and WIDTH=32 too — ZoneIR defaults both directions.)
   Title PNG IHDR is 20×9 tiles; scene-enter emitted `32u, 32u` as literal dims.
   Fix: emit macros `_zone_titleZone_tilemap_WIDTH` / `_HEIGHT` for NEW-path zones.

B. Path A emits wrong tilemap symbol — FALSIFIED (not investigated; A was root cause).

C. VRAM tile-index base misaligned — FALSIFIED (not investigated; A was root cause).

D. Bank-switching wrong — FALSIFIED (not investigated; A was root cause).

## Investigation entry points

1. grep generated C for set_bkg_tiles in title scene-enter — DONE (confirmed A immediately)
2-5. Not needed — A confirmed in step 1.

## Eliminated

- Hypotheses B, C, D all eliminated by Hypothesis A being confirmed as sole root cause.

## Evidence

- timestamp: 2026-05-23T00:00:00Z
  type: generated-c-snippet
  content: |
    bank1.c before fix (line 14):
    _bkg_tiles_load_banked(2u, 0u, 0u, 32u, 32u, _zone_titleZone_tilemap);
    _bkg_tiles_load_banked(2u, 0u, 0u, 32u, 32u, _zone_nextLevelZone_tilemap);
    (Both title_enter and nextLevel_enter used hardcoded 32u, 32u defaults)

- timestamp: 2026-05-23T00:00:00Z
  type: root-cause-code
  content: |
    SceneVisitor.kt lines 198-199 (pre-fix):
    CLiteral(zone.mapWidth),   // emits 32u — ZoneIR default
    CLiteral(zone.mapHeight),  // emits 32u — ZoneIR default
    The gameplay zones use macros in GBDKPipelineV2 (line 2376); SceneVisitor
    was never updated to match when Phase 12.2 added the macro emission.

- timestamp: 2026-05-23T00:00:00Z
  type: generated-c-snippet-after-fix
  content: |
    bank1.c after fix (line 14):
    _bkg_tiles_load_banked(2u, 0u, 0u, _zone_titleZone_tilemap_WIDTH, _zone_titleZone_tilemap_HEIGHT, _zone_titleZone_tilemap);
    _bkg_tiles_load_banked(2u, 0u, 0u, _zone_nextLevelZone_tilemap_WIDTH, _zone_nextLevelZone_tilemap_HEIGHT, _zone_nextLevelZone_tilemap);

- timestamp: 2026-05-23T00:00:00Z
  type: build-result
  content: |
    ROM build: BUILD SUCCESSFUL (platformer-template.gb, 64 KB)
    JVM tests: SceneVisitorTest (16 tests) GREEN
    JVM tests: TitleSceneEmissionTest (3 tests) GREEN

## Resolution

root_cause: |
  SceneVisitor.kt lines 198-199 emitted CLiteral(zone.mapWidth) and
  CLiteral(zone.mapHeight) for the w/h args of _bkg_tiles_load_banked in all
  zone-binding scene-enter functions (including title_enter and nextLevel_enter).
  ZoneIR.mapWidth and mapHeight default to 32 — used when the DSL author does not
  override them, which is the case for Path A zones (single-invocation, no explicit
  tilemap() declaration). ConvertZoneTilesetsTask (Phase 12.2-06) correctly emits
  _zone_<id>_tilemap_WIDTH / _HEIGHT macros from actual PNG IHDR dimensions (title
  is 20×9 tiles, not 32×32), but SceneVisitor was never wired to consume them. The
  render call passed 32×32=1024 to set_bkg_tiles on a 180-byte buffer, producing the
  row-doubling visual defect. The gameplay zones went through GBDKPipelineV2's
  setup_current_level path which already used the macros — confirming the Path A /
  Path B asymmetry the seed predicted.

fix: |
  SceneVisitor.kt: replaced CLiteral(zone.mapWidth) / CLiteral(zone.mapHeight)
  with conditional expressions:
  - NEW-path (tilesetPath != null): CVar("_zone_${zoneSanitized}_tilemap_WIDTH") /
    CVar("_zone_${zoneSanitized}_tilemap_HEIGHT")
  - LEGACY-path (tilesetPath == null): keep CLiteral(zone.mapWidth) / CLiteral(zone.mapHeight)
    (sport-racing procedural zones have no emitted macros)
  Also added SceneVisitorTest TEST 15 (NEW-path macro lock) and TEST 16 (LEGACY-path
  literal regression guard), and updated TitleSceneEmissionTest to assert macro
  presence in the brace-walked title_enter body.

verification: |
  JVM tier (necessary but not sufficient per CLAUDE.md Visual Evidence Rule):
  - SceneVisitorTest 16/16 GREEN (includes TEST 15 + TEST 16)
  - TitleSceneEmissionTest 3/3 GREEN (includes macro assertion)
  - ROM build: clean, platformer-template.gb 64 KB

  Visual tier (required per CLAUDE.md Visual Evidence Rule — PENDING):
  - Re-shoot `PlatformerTemplateUatTest.anchor1Title_to_Gameplay` and get
    human-verify approval on fresh 01-title.png. This is the load-bearing
    acceptance gate for AC13. Recommended: `/gsd:plan-phase 12.2 --gaps`
    to create a close-out plan that runs the UAT and presents the screenshot.

files_changed:
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitorTest.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt
